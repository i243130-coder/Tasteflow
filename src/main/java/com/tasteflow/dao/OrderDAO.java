package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.Order;
import com.tasteflow.model.OrderItem;
import com.tasteflow.model.RecipeIngredient;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the `order` and `order_item` tables.
 *
 * KEY DOMAIN RULE: On order confirmation, recipe-level deduction must
 * execute atomically. For each OrderItem we read its Recipe, then for
 * each RecipeIngredient we lock the ingredient row (SELECT ... FOR UPDATE),
 * verify sufficient stock, and deduct. If ANY ingredient would go negative,
 * the entire transaction rolls back — no partial deductions.
 *
 * Pattern: Facade — encapsulates order + items + stock deduction behind one call.
 */
public class OrderDAO {

    // ------------------------------------------------------------------ QUERIES

    /**
     * Returns all orders for today, newest first, with table number.
     */
    public List<Order> findTodayOrders() throws SQLException {
        String sql =
            "SELECT o.*, COALESCE(dt.table_number, 0) AS table_number " +
            "FROM `order` o " +
            "LEFT JOIN dining_table dt ON o.table_id = dt.table_id " +
            "WHERE DATE(o.created_at) = CURDATE() " +
            "ORDER BY o.created_at DESC";

        List<Order> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapOrder(rs));
            }
        }
        return list;
    }

    /**
     * Returns items for a given order.
     */
    public List<OrderItem> findItemsByOrderId(int orderId) throws SQLException {
        String sql =
            "SELECT oi.*, mi.item_name " +
            "FROM order_item oi " +
            "JOIN menu_item mi ON oi.item_id = mi.item_id " +
            "WHERE oi.order_id = ?";

        List<OrderItem> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapOrderItem(rs));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------ PLACE ORDER (Atomic)

    /**
     * Places an order: inserts Order + OrderItems, then deducts ingredient
     * stock based on each item's recipe. All inside one JDBC transaction.
     *
     * Steps inside the transaction:
     *   1. INSERT order header
     *   2. For each OrderItem:
     *      a. INSERT order_item row
     *      b. Load recipe for that menu item
     *      c. For each RecipeIngredient:
     *         - SELECT current_stock FOR UPDATE  (row lock)
     *         - Calculate new_stock = current - (recipe_qty * order_qty)
     *         - If new_stock < 0 → ROLLBACK with clear error
     *         - UPDATE ingredient.current_stock
     *         - INSERT inventory_transaction audit log
     *   3. UPDATE dining_table status to OCCUPIED
     *   4. COMMIT
     *
     * @throws SQLException with a clean message if stock is insufficient
     */
    public int placeOrder(Order order) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Insert order header
            int orderId = insertOrderHeader(conn, order);
            order.setOrderId(orderId);

            // 2. Process each item
            for (OrderItem item : order.getItems()) {
                item.setOrderId(orderId);

                // 2a. Insert order_item
                insertOrderItem(conn, item);

                // 2b. Load recipe for this menu item
                List<RecipeIngredient> recipe = loadRecipe(conn, item.getItemId());

                // 2c. Deduct stock for each ingredient in the recipe
                for (RecipeIngredient ri : recipe) {
                    BigDecimal totalNeeded = ri.getQuantityRequired()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

                    // Lock ingredient row
                    BigDecimal currentStock = lockIngredientStock(conn, ri.getIngredientId());

                    BigDecimal newStock = currentStock.subtract(totalNeeded);

                    // CRITICAL CHECK: reject if stock would go negative
                    if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                        throw new SQLException(
                            "⚠ OUT OF STOCK: \"" + ri.getIngredientName() + "\" — " +
                            "need " + totalNeeded.toPlainString() + " " + ri.getUnit() +
                            " but only " + currentStock.toPlainString() + " available. " +
                            "Order rolled back.");
                    }

                    // Update stock
                    updateIngredientStock(conn, ri.getIngredientId(), newStock);

                    // Audit log
                    insertInventoryTransaction(conn, ri.getIngredientId(), orderId,
                            totalNeeded.negate(), newStock);
                }
            }

            // 3. Mark table as occupied
            if (order.getTableId() != null) {
                updateTableStatus(conn, order.getTableId(), "OCCUPIED");
            }

            conn.commit();
            return orderId;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ------------------------------------------------ UPDATE STATUS

    public void updateOrderStatus(int orderId, String status) throws SQLException {
        String sql = "UPDATE `order` SET status = ? WHERE order_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    /**
     * Places a delivery order (header only, no stock deduction).
     * Used for external platform orders that don't have recipe-level items.
     */
    public int placeDeliveryOrder(Order order) throws SQLException {
        order.setOrderType("DELIVERY");
        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            return insertOrderHeader(conn, order);
        }
    }

    // ------------------------------------------------ PRE-ORDER (Held)

    /**
     * Places a HELD pre-order: saves Order + OrderItems but does NOT deduct
     * stock and does NOT mark the table as OCCUPIED. The order is invisible
     * to the KDS until released.
     *
     * Domain Rule: A customer with a pending Reservation can place an Order.
     * This order is saved with is_held = true and order_type = 'PRE_ORDER'.
     *
     * @return the generated order_id
     */
    public int placeHeldOrder(Order order) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            order.setHeld(true);
            order.setOrderType("PRE_ORDER");

            // Insert order header (with is_held = true)
            int orderId = insertOrderHeader(conn, order);
            order.setOrderId(orderId);

            // Insert each order item (status = QUEUED but won't generate KDS tickets)
            for (OrderItem item : order.getItems()) {
                item.setOrderId(orderId);
                insertOrderItem(conn, item);
            }

            conn.commit();
            return orderId;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    /**
     * Releases all held orders for a given reservation.
     * Called when the reservation is marked SEATED.
     *
     * For each held order:
     *  1. Set is_held = false (makes items visible to KDS polling)
     *  2. Atomically deduct ingredient stock (same logic as placeOrder)
     *  3. Update table status to OCCUPIED
     *
     * @return number of orders released
     * @throws SQLException with clean error if stock is insufficient
     */
    public int releaseHeldOrders(int reservationId, int tableId) throws SQLException {
        // Find all held orders for this reservation
        String findSql = "SELECT order_id FROM `order` WHERE reservation_id = ? AND is_held = TRUE";

        List<Integer> heldOrderIds = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    heldOrderIds.add(rs.getInt("order_id"));
                }
            }
        }

        if (heldOrderIds.isEmpty()) return 0;

        // Process each held order atomically
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            for (int orderId : heldOrderIds) {
                // Load items for this order
                List<OrderItem> items = findItemsByOrderIdInternal(conn, orderId);

                // Deduct stock for each item's recipe
                for (OrderItem item : items) {
                    List<RecipeIngredient> recipe = loadRecipe(conn, item.getItemId());

                    for (RecipeIngredient ri : recipe) {
                        BigDecimal totalNeeded = ri.getQuantityRequired()
                                .multiply(BigDecimal.valueOf(item.getQuantity()));

                        BigDecimal currentStock = lockIngredientStock(conn, ri.getIngredientId());
                        BigDecimal newStock = currentStock.subtract(totalNeeded);

                        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                            throw new SQLException(
                                "⚠ OUT OF STOCK for pre-order #" + orderId + ": \"" +
                                ri.getIngredientName() + "\" — need " +
                                totalNeeded.toPlainString() + " " + ri.getUnit() +
                                " but only " + currentStock.toPlainString() + " available.");
                        }

                        updateIngredientStock(conn, ri.getIngredientId(), newStock);
                        insertInventoryTransaction(conn, ri.getIngredientId(), orderId,
                                totalNeeded.negate(), newStock);
                    }
                }

                // Release the order: set is_held = false
                String releaseSql = "UPDATE `order` SET is_held = FALSE, table_id = ? WHERE order_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(releaseSql)) {
                    ps.setInt(1, tableId);
                    ps.setInt(2, orderId);
                    ps.executeUpdate();
                }
            }

            // Mark table as occupied
            updateTableStatus(conn, tableId, "OCCUPIED");

            conn.commit();
            return heldOrderIds.size();

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    /**
     * Finds held pre-orders for a specific reservation.
     */
    public List<Order> findHeldOrdersByReservation(int reservationId) throws SQLException {
        String sql =
            "SELECT o.*, COALESCE(dt.table_number, 0) AS table_number " +
            "FROM `order` o " +
            "LEFT JOIN dining_table dt ON o.table_id = dt.table_id " +
            "WHERE o.reservation_id = ? AND o.is_held = TRUE";

        List<Order> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapOrder(rs));
                }
            }
        }
        return list;
    }

    // ------------------------------------------------ STOCK CHECK (for POS real-time)

    /**
     * Checks if a menu item can be ordered (sufficient stock for its recipe).
     * Used by POS to flag "Out of Stock" items in real-time.
     *
     * @return null if OK, or an error message describing the shortage
     */
    public String checkStockForItem(int itemId, int quantity) throws SQLException {
        String sql =
            "SELECT ri.quantity_required, i.ingredient_name, i.unit, i.current_stock " +
            "FROM recipe_ingredient ri " +
            "JOIN ingredient i ON ri.ingredient_id = i.ingredient_id " +
            "WHERE ri.item_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal needed = rs.getBigDecimal("quantity_required")
                            .multiply(BigDecimal.valueOf(quantity));
                    BigDecimal available = rs.getBigDecimal("current_stock");

                    if (available.compareTo(needed) < 0) {
                        return "Insufficient \"" + rs.getString("ingredient_name") + "\" — " +
                               "need " + needed.toPlainString() + " " + rs.getString("unit") +
                               ", have " + available.toPlainString();
                    }
                }
            }
        }
        return null; // all ingredients are in stock
    }

    // ====================================================================
    //  PRIVATE helpers (single-connection, used inside transactions)
    // ====================================================================

    private int insertOrderHeader(Connection conn, Order order) throws SQLException {
        String sql =
            "INSERT INTO `order` (branch_id, table_id, customer_id, reservation_id, " +
            "waiter_id, order_type, status, subtotal, tax, discount, " +
            "loyalty_points_redeemed, total, special_instructions, is_held) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getBranchId());
            setNullableInt(ps, 2, order.getTableId());
            setNullableInt(ps, 3, order.getCustomerId());
            setNullableInt(ps, 4, order.getReservationId());
            setNullableInt(ps, 5, order.getWaiterId());
            ps.setString(6, order.getOrderType());
            ps.setString(7, order.getStatus());
            ps.setBigDecimal(8, order.getSubtotal());
            ps.setBigDecimal(9, order.getTax());
            ps.setBigDecimal(10, order.getDiscount());
            ps.setInt(11, order.getLoyaltyPointsRedeemed());
            ps.setBigDecimal(12, order.getTotal());
            ps.setString(13, order.getSpecialInstructions());
            ps.setBoolean(14, order.isHeld());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to retrieve generated order ID");
    }

    private void insertOrderItem(Connection conn, OrderItem item) throws SQLException {
        String sql =
            "INSERT INTO order_item (order_id, item_id, quantity, unit_price, subtotal, " +
            "special_requests, status, priority) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getItemId());
            ps.setInt(3, item.getQuantity());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.setBigDecimal(5, item.getSubtotal());
            ps.setString(6, item.getSpecialRequests());
            ps.setString(7, item.getStatus());
            ps.setString(8, item.getPriority());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setOrderItemId(keys.getInt(1));
            }
        }
    }

    private List<RecipeIngredient> loadRecipe(Connection conn, int itemId) throws SQLException {
        String sql =
            "SELECT ri.*, i.ingredient_name, i.unit " +
            "FROM recipe_ingredient ri " +
            "JOIN ingredient i ON ri.ingredient_id = i.ingredient_id " +
            "WHERE ri.item_id = ?";

        List<RecipeIngredient> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecipeIngredient ri = new RecipeIngredient();
                    ri.setRecipeId(rs.getInt("recipe_id"));
                    ri.setItemId(rs.getInt("item_id"));
                    ri.setIngredientId(rs.getInt("ingredient_id"));
                    ri.setIngredientName(rs.getString("ingredient_name"));
                    ri.setUnit(rs.getString("unit"));
                    ri.setQuantityRequired(rs.getBigDecimal("quantity_required"));
                    list.add(ri);
                }
            }
        }
        return list;
    }

    private BigDecimal lockIngredientStock(Connection conn, int ingredientId) throws SQLException {
        String sql = "SELECT current_stock FROM ingredient WHERE ingredient_id = ? FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("current_stock");
            }
        }
        throw new SQLException("Ingredient #" + ingredientId + " not found");
    }

    private void updateIngredientStock(Connection conn, int ingredientId, BigDecimal newStock) throws SQLException {
        String sql = "UPDATE ingredient SET current_stock = ? WHERE ingredient_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newStock);
            ps.setInt(2, ingredientId);
            ps.executeUpdate();
        }
    }

    private void updateTableStatus(Connection conn, int tableId, String status) throws SQLException {
        String sql = "UPDATE dining_table SET status = ? WHERE table_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, tableId);
            ps.executeUpdate();
        }
    }

    private void insertInventoryTransaction(Connection conn, int ingredientId, int orderId,
                                            BigDecimal qtyChange, BigDecimal stockAfter) throws SQLException {
        String sql =
            "INSERT INTO inventory_transaction (ingredient_id, order_id, transaction_type, " +
            "quantity_change, stock_after) VALUES (?, ?, 'ORDER_DEDUCTION', ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            ps.setInt(2, orderId);
            ps.setBigDecimal(3, qtyChange);
            ps.setBigDecimal(4, stockAfter);
            ps.executeUpdate();
        }
    }

    /** Internal version using an existing connection (for transactions). */
    private List<OrderItem> findItemsByOrderIdInternal(Connection conn, int orderId) throws SQLException {
        String sql =
            "SELECT oi.*, mi.item_name " +
            "FROM order_item oi " +
            "JOIN menu_item mi ON oi.item_id = mi.item_id " +
            "WHERE oi.order_id = ?";

        List<OrderItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapOrderItem(rs));
                }
            }
        }
        return list;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    // ====================================================================
    //  Row mappers
    // ====================================================================

    private Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setBranchId(rs.getInt("branch_id"));
        int tid = rs.getInt("table_id");
        o.setTableId(rs.wasNull() ? null : tid);
        int cid = rs.getInt("customer_id");
        o.setCustomerId(rs.wasNull() ? null : cid);
        o.setOrderType(rs.getString("order_type"));
        o.setStatus(rs.getString("status"));
        o.setSubtotal(rs.getBigDecimal("subtotal"));
        o.setTax(rs.getBigDecimal("tax"));
        o.setDiscount(rs.getBigDecimal("discount"));
        o.setLoyaltyPointsRedeemed(rs.getInt("loyalty_points_redeemed"));
        o.setTotal(rs.getBigDecimal("total"));
        o.setSpecialInstructions(rs.getString("special_instructions"));
        o.setHeld(rs.getBoolean("is_held"));
        o.setCreatedAt(rs.getTimestamp("created_at"));
        o.setCompletedAt(rs.getTimestamp("completed_at"));
        o.setTableNumber(rs.getInt("table_number"));
        return o;
    }

    private OrderItem mapOrderItem(ResultSet rs) throws SQLException {
        OrderItem oi = new OrderItem();
        oi.setOrderItemId(rs.getInt("order_item_id"));
        oi.setOrderId(rs.getInt("order_id"));
        oi.setItemId(rs.getInt("item_id"));
        oi.setItemName(rs.getString("item_name"));
        oi.setQuantity(rs.getInt("quantity"));
        oi.setUnitPrice(rs.getBigDecimal("unit_price"));
        oi.setSubtotal(rs.getBigDecimal("subtotal"));
        oi.setSpecialRequests(rs.getString("special_requests"));
        oi.setStatus(rs.getString("status"));
        oi.setPriority(rs.getString("priority"));
        return oi;
    }
}
