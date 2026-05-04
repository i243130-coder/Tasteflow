package com.tasteflow.service;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.InventoryAlert;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * InventoryManager — GRASP Pure Fabrication.
 *
 * Encapsulates the automated recipe-level inventory deduction
 * that runs when an order is confirmed. This class exists purely
 * to maintain high cohesion; the Order domain object shouldn't
 * know how to deduct stock.
 *
 * KEY CONTRACT:
 * <pre>
 *   boolean deductInventoryForOrder(String orderId)
 * </pre>
 * Executes an ACID-compliant JDBC transaction:
 *   1. Read order items for the given order
 *   2. For each item → load recipe ingredients
 *   3. For each recipe ingredient:
 *      a. SELECT current_stock FOR UPDATE  (row lock)
 *      b. new_stock = current − (recipe_qty × order_qty)
 *      c. If new_stock < 0  →  ROLLBACK (no partial deductions)
 *      d. UPDATE ingredient.current_stock
 *      e. INSERT inventory_transaction audit log
 *      f. If new_stock ≤ reorder_level  →  flag alert
 *   4. COMMIT
 *
 * Concurrent orders are safe because the FOR UPDATE lock serialises
 * access to each ingredient row within the transaction.
 *
 * GoF Pattern: Strategy-compatible — the deduction logic can be
 * swapped out or extended without touching OrderDAO.
 */
public class InventoryManager {

    /**
     * Deducts raw-ingredient stock for every item in the specified order.
     * Returns true if the deduction succeeded, false if it was rolled back.
     *
     * @param orderId the order whose items should be deducted
     * @return true on successful deduction; false on rollback
     */
    public boolean deductInventoryForOrder(String orderId) {
        Connection conn = null;
        List<InventoryAlert> triggeredAlerts = new ArrayList<>();

        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);   // ── BEGIN TRANSACTION ──

            int oid = Integer.parseInt(orderId);

            // 1. Load order items
            String itemsSql =
                "SELECT oi.item_id, oi.quantity " +
                "FROM order_item oi " +
                "WHERE oi.order_id = ?";

            List<int[]> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setInt(1, oid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new int[]{
                            rs.getInt("item_id"),
                            rs.getInt("quantity")
                        });
                    }
                }
            }

            if (items.isEmpty()) {
                conn.rollback();
                return false;
            }

            // 2. For each order item → load recipe → deduct
            for (int[] item : items) {
                int itemId   = item[0];
                int orderQty = item[1];

                // Load recipe ingredients for this menu item
                String recipeSql =
                    "SELECT ri.ingredient_id, ri.quantity_required, " +
                    "       i.ingredient_name, i.unit, i.reorder_level " +
                    "FROM recipe_ingredient ri " +
                    "JOIN ingredient i ON ri.ingredient_id = i.ingredient_id " +
                    "WHERE ri.item_id = ?";

                try (PreparedStatement ps = conn.prepareStatement(recipeSql)) {
                    ps.setInt(1, itemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int ingredientId         = rs.getInt("ingredient_id");
                            BigDecimal qtyRequired   = rs.getBigDecimal("quantity_required");
                            String ingredientName    = rs.getString("ingredient_name");
                            String unit              = rs.getString("unit");
                            BigDecimal reorderLevel  = rs.getBigDecimal("reorder_level");

                            BigDecimal totalNeeded   = qtyRequired.multiply(BigDecimal.valueOf(orderQty));

                            // 3a. Lock ingredient row
                            BigDecimal currentStock = lockIngredientStock(conn, ingredientId);

                            // 3b. Calculate new stock
                            BigDecimal newStock = currentStock.subtract(totalNeeded);

                            // 3c. CRITICAL CHECK: reject if stock would go negative
                            if (newStock.compareTo(BigDecimal.ZERO) < 0) {
                                conn.rollback();
                                System.err.println("⚠ OUT OF STOCK: \"" + ingredientName + "\" — " +
                                    "need " + totalNeeded.toPlainString() + " " + unit +
                                    " but only " + currentStock.toPlainString() + " available.");
                                return false;
                            }

                            // 3d. Update stock
                            updateIngredientStock(conn, ingredientId, newStock);

                            // 3e. Audit log
                            insertInventoryTransaction(conn, ingredientId, oid,
                                    totalNeeded.negate(), newStock);

                            // 3f. Threshold check — flag alert if stock ≤ reorder level
                            if (newStock.compareTo(reorderLevel) <= 0) {
                                InventoryAlert alert = new InventoryAlert();
                                alert.setIngredientId(ingredientId);
                                alert.setIngredientName(ingredientName);
                                alert.setUnit(unit);
                                alert.setCurrentStock(newStock);
                                alert.setReorderLevel(reorderLevel);
                                alert.setDeficit(reorderLevel.subtract(newStock));
                                alert.setSeverity(
                                    newStock.compareTo(BigDecimal.ZERO) <= 0
                                        ? "CRITICAL" : "WARNING");
                                triggeredAlerts.add(alert);
                            }
                        }
                    }
                }
            }

            // 4. COMMIT
            conn.commit();

            // Log any triggered alerts
            if (!triggeredAlerts.isEmpty()) {
                System.out.println("⚠ LOW STOCK ALERTS triggered for order #" + orderId + ":");
                for (InventoryAlert a : triggeredAlerts) {
                    System.out.println("  • " + a);
                }
            }

            return true;

        } catch (SQLException | NumberFormatException ex) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
            System.err.println("❌ Inventory deduction failed for order #" + orderId + ": " + ex.getMessage());
            return false;

        } finally {
            try {
                if (conn != null) { conn.setAutoCommit(true); conn.close(); }
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Returns all currently triggered low-stock alerts.
     * Delegates to a live query (not cached).
     */
    public List<InventoryAlert> getActiveAlerts() throws SQLException {
        String sql =
            "SELECT ingredient_id, ingredient_name, unit, " +
            "       current_stock, reorder_level, " +
            "       (reorder_level - current_stock) AS deficit, " +
            "       CASE WHEN current_stock <= 0 THEN 'CRITICAL' ELSE 'WARNING' END AS severity " +
            "FROM ingredient " +
            "WHERE is_active = TRUE AND current_stock <= reorder_level " +
            "ORDER BY CASE WHEN current_stock <= 0 THEN 0 ELSE 1 END, " +
            "         (reorder_level - current_stock) DESC";

        List<InventoryAlert> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                InventoryAlert a = new InventoryAlert();
                a.setIngredientId(rs.getInt("ingredient_id"));
                a.setIngredientName(rs.getString("ingredient_name"));
                a.setUnit(rs.getString("unit"));
                a.setCurrentStock(rs.getBigDecimal("current_stock"));
                a.setReorderLevel(rs.getBigDecimal("reorder_level"));
                a.setDeficit(rs.getBigDecimal("deficit"));
                a.setSeverity(rs.getString("severity"));
                list.add(a);
            }
        }
        return list;
    }

    // ====================================================================
    //  PRIVATE helpers (single-connection, used inside the transaction)
    // ====================================================================

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
}
