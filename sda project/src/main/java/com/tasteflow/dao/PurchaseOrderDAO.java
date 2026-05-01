package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.PurchaseOrder;
import com.tasteflow.model.PurchaseOrderItem;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for purchase_order and purchase_order_item tables.
 *
 * Key domain rule: When a PO is marked "RECEIVED", the ingredient stock
 * must increase atomically within the same transaction.
 *
 * Pattern: Facade — encapsulates PO + line items + stock update behind one interface.
 */
public class PurchaseOrderDAO {

    // ------------------------------------------------------------------ QUERIES

    /**
     * Returns all purchase orders with supplier name, newest first.
     */
    public List<PurchaseOrder> findAll() throws SQLException {
        String sql =
            "SELECT po.*, s.supplier_name " +
            "FROM purchase_order po " +
            "JOIN supplier s ON po.supplier_id = s.supplier_id " +
            "ORDER BY po.order_date DESC";

        List<PurchaseOrder> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapPO(rs));
            }
        }
        return list;
    }

    /**
     * Loads line items for a specific PO.
     */
    public List<PurchaseOrderItem> findItemsByPoId(int poId) throws SQLException {
        String sql =
            "SELECT poi.*, i.ingredient_name, i.unit " +
            "FROM purchase_order_item poi " +
            "JOIN ingredient i ON poi.ingredient_id = i.ingredient_id " +
            "WHERE poi.po_id = ? " +
            "ORDER BY i.ingredient_name";

        List<PurchaseOrderItem> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPOItem(rs));
                }
            }
        }
        return list;
    }

    // ----------------------------------------------------------- CREATE PO (Atomic)

    /**
     * Inserts a PurchaseOrder and ALL its line items in a single transaction.
     */
    public int createPurchaseOrder(PurchaseOrder po) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Insert PO header
            int poId = insertPOHeader(conn, po);
            po.setPoId(poId);

            // 2. Insert each line item
            for (PurchaseOrderItem item : po.getItems()) {
                item.setPoId(poId);
                insertPOItem(conn, item);
            }

            conn.commit();
            return poId;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ----------------------------------------------------------- RECEIVE PO (Atomic)

    /**
     * Marks a PO as "RECEIVED" and increases ingredient stock for every line item.
     * All updates happen inside a single JDBC transaction — if any step fails,
     * stock and PO status both roll back.
     *
     * Uses SELECT … FOR UPDATE on ingredient rows to prevent concurrent races.
     */
    public void receivePurchaseOrder(int poId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Load line items for this PO
            List<PurchaseOrderItem> items = findItemsByPoId(conn, poId);
            if (items.isEmpty()) {
                throw new SQLException("PO-" + poId + " has no line items.");
            }

            // 2. For each line: lock ingredient row → increase stock → log transaction
            for (PurchaseOrderItem item : items) {
                // Lock the ingredient row
                BigDecimal currentStock = lockIngredientStock(conn, item.getIngredientId());

                BigDecimal received = item.getQuantityOrdered(); // receive full qty
                BigDecimal newStock = currentStock.add(received);

                // Update ingredient stock
                updateIngredientStock(conn, item.getIngredientId(), newStock);

                // Update quantity_received on the PO line
                updatePOItemReceived(conn, item.getPoItemId(), received);

                // Log inventory transaction
                insertInventoryTransaction(conn, item.getIngredientId(), poId,
                        received, newStock);
            }

            // 3. Update PO status to RECEIVED and set total
            updatePOStatus(conn, poId, "RECEIVED");

            conn.commit();

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ----------------------------------------------------------- CANCEL PO

    public void cancelPurchaseOrder(int poId) throws SQLException {
        String sql = "UPDATE purchase_order SET status = 'CANCELLED' WHERE po_id = ? AND status IN ('DRAFT','SUBMITTED')";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, poId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Cannot cancel PO-" + poId + " (already received or cancelled).");
            }
        }
    }

    // ====================================================================
    //  PRIVATE helpers (single-connection, used inside transactions)
    // ====================================================================

    private int insertPOHeader(Connection conn, PurchaseOrder po) throws SQLException {
        String sql =
            "INSERT INTO purchase_order (supplier_id, branch_id, ordered_by, expected_delivery, " +
            "status, total_amount, notes) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, po.getSupplierId());
            ps.setInt(2, po.getBranchId());
            ps.setInt(3, po.getOrderedBy());
            ps.setDate(4, po.getExpectedDelivery() != null ?
                    Date.valueOf(po.getExpectedDelivery()) : null);
            ps.setString(5, po.getStatus());
            ps.setBigDecimal(6, po.getTotalAmount());
            ps.setString(7, po.getNotes());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to retrieve generated PO ID");
    }

    private void insertPOItem(Connection conn, PurchaseOrderItem item) throws SQLException {
        String sql =
            "INSERT INTO purchase_order_item (po_id, ingredient_id, quantity_ordered, unit_price) " +
            "VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getPoId());
            ps.setInt(2, item.getIngredientId());
            ps.setBigDecimal(3, item.getQuantityOrdered());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) item.setPoItemId(keys.getInt(1));
            }
        }
    }

    /** Loads PO items using an existing connection (for transactional use). */
    private List<PurchaseOrderItem> findItemsByPoId(Connection conn, int poId) throws SQLException {
        String sql =
            "SELECT poi.*, i.ingredient_name, i.unit " +
            "FROM purchase_order_item poi " +
            "JOIN ingredient i ON poi.ingredient_id = i.ingredient_id " +
            "WHERE poi.po_id = ?";

        List<PurchaseOrderItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPOItem(rs));
                }
            }
        }
        return list;
    }

    /** Locks the ingredient row and returns current stock. */
    private BigDecimal lockIngredientStock(Connection conn, int ingredientId) throws SQLException {
        String sql = "SELECT current_stock FROM ingredient WHERE ingredient_id = ? FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("current_stock");
            }
        }
        throw new SQLException("Ingredient #" + ingredientId + " not found.");
    }

    private void updateIngredientStock(Connection conn, int ingredientId, BigDecimal newStock) throws SQLException {
        String sql = "UPDATE ingredient SET current_stock = ? WHERE ingredient_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newStock);
            ps.setInt(2, ingredientId);
            ps.executeUpdate();
        }
    }

    private void updatePOItemReceived(Connection conn, int poItemId, BigDecimal qtyReceived) throws SQLException {
        String sql = "UPDATE purchase_order_item SET quantity_received = ? WHERE po_item_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, qtyReceived);
            ps.setInt(2, poItemId);
            ps.executeUpdate();
        }
    }

    private void updatePOStatus(Connection conn, int poId, String status) throws SQLException {
        String sql = "UPDATE purchase_order SET status = ? WHERE po_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, poId);
            ps.executeUpdate();
        }
    }

    /** Logs a PO_RECEIPT entry in the inventory_transaction audit table. */
    private void insertInventoryTransaction(Connection conn, int ingredientId, int poId,
                                            BigDecimal qtyChange, BigDecimal stockAfter) throws SQLException {
        String sql =
            "INSERT INTO inventory_transaction (ingredient_id, po_id, transaction_type, " +
            "quantity_change, stock_after) VALUES (?, ?, 'PO_RECEIPT', ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ingredientId);
            ps.setInt(2, poId);
            ps.setBigDecimal(3, qtyChange);
            ps.setBigDecimal(4, stockAfter);
            ps.executeUpdate();
        }
    }

    // ====================================================================
    //  Row mappers
    // ====================================================================

    private PurchaseOrder mapPO(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoId(rs.getInt("po_id"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setBranchId(rs.getInt("branch_id"));
        po.setOrderedBy(rs.getInt("ordered_by"));
        po.setOrderDate(rs.getTimestamp("order_date"));
        Date ed = rs.getDate("expected_delivery");
        if (ed != null) po.setExpectedDelivery(ed.toLocalDate());
        po.setStatus(rs.getString("status"));
        po.setTotalAmount(rs.getBigDecimal("total_amount"));
        po.setNotes(rs.getString("notes"));
        return po;
    }

    private PurchaseOrderItem mapPOItem(ResultSet rs) throws SQLException {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPoItemId(rs.getInt("po_item_id"));
        item.setPoId(rs.getInt("po_id"));
        item.setIngredientId(rs.getInt("ingredient_id"));
        item.setIngredientName(rs.getString("ingredient_name"));
        item.setUnit(rs.getString("unit"));
        item.setQuantityOrdered(rs.getBigDecimal("quantity_ordered"));
        item.setQuantityReceived(rs.getBigDecimal("quantity_received"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        return item;
    }
}
