package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.KdsTicket;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the kds_ticket table (Kitchen Display System).
 *
 * Responsibilities:
 *  - Auto-generate KDS tickets when new order items appear
 *  - Fetch active tickets (PENDING, IN_PROGRESS, READY within correction window)
 *  - Status transitions with business rule enforcement
 *  - Allergen acknowledgement
 *  - Correction window (30s after marking READY)
 *
 * Pattern: Facade — encapsulates all KDS persistence logic.
 */
public class KdsDAO {

    /** Correction window duration in seconds. */
    private static final int CORRECTION_WINDOW_SECONDS = 30;

    // ------------------------------------------------------------------ POLLING

    /**
     * Generates KDS tickets for any order_items that don't have one yet.
     * This is called by the polling mechanism.
     * Also looks up allergen warnings from menu_item_allergen.
     */
    public int generateMissingTickets() throws SQLException {
        // Find order_items without a kds_ticket (excluding held pre-orders)
        String findSql =
            "SELECT oi.order_item_id, oi.item_id " +
            "FROM order_item oi " +
            "JOIN `order` o ON oi.order_id = o.order_id " +
            "WHERE oi.order_item_id NOT IN (SELECT order_item_id FROM kds_ticket) " +
            "  AND oi.status IN ('QUEUED', 'PREPARING') " +
            "  AND o.is_held = FALSE";

        String insertSql =
            "INSERT INTO kds_ticket (order_item_id, station, status, has_allergen_warning) " +
            "VALUES (?, 'GENERAL', 'PENDING', ?)";

        String allergenCheckSql =
            "SELECT COUNT(*) FROM menu_item_allergen WHERE item_id = ?";

        int count = 0;

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<int[]> missing = new ArrayList<>(); // [orderItemId, itemId]

            try (PreparedStatement ps = conn.prepareStatement(findSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    missing.add(new int[]{ rs.getInt("order_item_id"), rs.getInt("item_id") });
                }
            }

            for (int[] pair : missing) {
                // Check allergens
                boolean hasAllergen = false;
                try (PreparedStatement ps = conn.prepareStatement(allergenCheckSql)) {
                    ps.setInt(1, pair[1]); // item_id
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) hasAllergen = rs.getInt(1) > 0;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, pair[0]); // order_item_id
                    ps.setBoolean(2, hasAllergen);
                    ps.executeUpdate();
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Fetches all active KDS tickets:
     *   - PENDING and IN_PROGRESS tickets
     *   - READY tickets still within correction window
     * Joined with order_item, menu_item, and order for display data.
     * Also fetches allergen names for items that have allergen warnings.
     */
    public List<KdsTicket> findActiveTickets() throws SQLException {
        String sql =
            "SELECT kt.*, oi.quantity, oi.special_requests, oi.priority, " +
            "       mi.item_name, COALESCE(dt.table_number, 0) AS table_number, " +
            "       oi.order_id " +
            "FROM kds_ticket kt " +
            "JOIN order_item oi ON kt.order_item_id = oi.order_item_id " +
            "JOIN menu_item mi ON oi.item_id = mi.item_id " +
            "JOIN `order` o ON oi.order_id = o.order_id " +
            "LEFT JOIN dining_table dt ON o.table_id = dt.table_id " +
            "WHERE kt.status IN ('PENDING', 'IN_PROGRESS') " +
            "   OR (kt.status = 'READY' AND kt.correction_window_end > NOW()) " +
            "ORDER BY " +
            "  CASE oi.priority WHEN 'VIP' THEN 0 WHEN 'RUSH' THEN 1 ELSE 2 END, " +
            "  kt.created_at ASC";

        String allergenSql =
            "SELECT GROUP_CONCAT(a.allergen_name SEPARATOR ', ') AS flags " +
            "FROM menu_item_allergen mia " +
            "JOIN allergen a ON mia.allergen_id = a.allergen_id " +
            "WHERE mia.item_id = ?";

        List<KdsTicket> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                KdsTicket t = mapRow(rs);

                // Load allergen flags if has warning
                if (t.isHasAllergenWarning()) {
                    // get item_id from order_item
                    int orderItemId = t.getOrderItemId();
                    String itemSql = "SELECT item_id FROM order_item WHERE order_item_id = ?";
                    try (PreparedStatement ps2 = conn.prepareStatement(itemSql)) {
                        ps2.setInt(1, orderItemId);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (rs2.next()) {
                                int itemId = rs2.getInt("item_id");
                                try (PreparedStatement ps3 = conn.prepareStatement(allergenSql)) {
                                    ps3.setInt(1, itemId);
                                    try (ResultSet rs3 = ps3.executeQuery()) {
                                        if (rs3.next()) {
                                            t.setAllergenFlags(rs3.getString("flags"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                list.add(t);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ STATUS TRANSITIONS

    /**
     * Mark ticket as IN_PROGRESS (chef starts working on it).
     * If the item has allergen warnings, they MUST be acknowledged first.
     */
    public void startPreparing(int ticketId) throws SQLException {
        // Check allergen acknowledgement requirement
        KdsTicket ticket = findById(ticketId);
        if (ticket != null && ticket.isHasAllergenWarning() && !ticket.isAllergenAcknowledged()) {
            throw new SQLException(
                "⚠ ALLERGEN WARNING: This item contains allergens. " +
                "You must acknowledge the allergen warning before starting preparation.");
        }

        String sql = "UPDATE kds_ticket SET status = 'IN_PROGRESS' WHERE ticket_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Ticket #" + ticketId + " is not in PENDING status.");
        }

        // Also update the order_item status
        updateOrderItemStatus(ticketId, "PREPARING");
    }

    /**
     * Mark ticket as READY and start the correction window (30 seconds).
     */
    public void markReady(int ticketId) throws SQLException {
        String sql =
            "UPDATE kds_ticket SET status = 'READY', " +
            "completed_at = NOW(), " +
            "correction_window_end = DATE_ADD(NOW(), INTERVAL " + CORRECTION_WINDOW_SECONDS + " SECOND) " +
            "WHERE ticket_id = ? AND status = 'IN_PROGRESS'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Ticket #" + ticketId + " is not IN_PROGRESS.");
        }

        updateOrderItemStatus(ticketId, "READY");
    }

    /**
     * RECALL a ticket back to IN_PROGRESS (correction window).
     * Only works if the correction window hasn't expired.
     */
    public void recallTicket(int ticketId) throws SQLException {
        String sql =
            "UPDATE kds_ticket SET status = 'IN_PROGRESS', " +
            "completed_at = NULL, correction_window_end = NULL " +
            "WHERE ticket_id = ? AND status = 'READY' " +
            "AND correction_window_end > NOW()";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException(
                    "⚠ Cannot recall: correction window has expired for ticket #" + ticketId + ".");
            }
        }

        updateOrderItemStatus(ticketId, "PREPARING");
    }

    // ------------------------------------------------------------------ ALLERGEN ACKNOWLEDGEMENT

    /**
     * Acknowledge allergen warnings for a ticket (chef explicitly confirms).
     */
    public void acknowledgeAllergens(int ticketId, int chefId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // Mark the ticket as acknowledged
            String updateSql =
                "UPDATE kds_ticket SET allergen_acknowledged = TRUE, " +
                "acknowledged_by = ?, acknowledged_at = NOW() WHERE ticket_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, chefId);
                ps.setInt(2, ticketId);
                ps.executeUpdate();
            }

            // Log each allergen acknowledgement
            String logSql =
                "INSERT INTO kds_allergen_ack (ticket_id, allergen_id, chef_id) " +
                "SELECT ?, mia.allergen_id, ? " +
                "FROM order_item oi " +
                "JOIN menu_item_allergen mia ON oi.item_id = mia.item_id " +
                "JOIN kds_ticket kt ON kt.order_item_id = oi.order_item_id " +
                "WHERE kt.ticket_id = ?";

            try (PreparedStatement ps = conn.prepareStatement(logSql)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, chefId);
                ps.setInt(3, ticketId);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ====================================================================
    //  PRIVATE helpers
    // ====================================================================

    private KdsTicket findById(int ticketId) throws SQLException {
        String sql =
            "SELECT kt.*, oi.quantity, oi.special_requests, oi.priority, " +
            "       mi.item_name, 0 AS table_number, oi.order_id " +
            "FROM kds_ticket kt " +
            "JOIN order_item oi ON kt.order_item_id = oi.order_item_id " +
            "JOIN menu_item mi ON oi.item_id = mi.item_id " +
            "WHERE kt.ticket_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    private void updateOrderItemStatus(int ticketId, String status) throws SQLException {
        String sql =
            "UPDATE order_item oi " +
            "JOIN kds_ticket kt ON kt.order_item_id = oi.order_item_id " +
            "SET oi.status = ? WHERE kt.ticket_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
    }

    private KdsTicket mapRow(ResultSet rs) throws SQLException {
        KdsTicket t = new KdsTicket();
        t.setTicketId(rs.getInt("ticket_id"));
        t.setOrderItemId(rs.getInt("order_item_id"));
        t.setOrderId(rs.getInt("order_id"));
        t.setStation(rs.getString("station"));
        t.setStatus(rs.getString("status"));
        t.setHasAllergenWarning(rs.getBoolean("has_allergen_warning"));
        t.setAllergenAcknowledged(rs.getBoolean("allergen_acknowledged"));
        int ackBy = rs.getInt("acknowledged_by");
        t.setAcknowledgedBy(rs.wasNull() ? null : ackBy);
        t.setAcknowledgedAt(rs.getTimestamp("acknowledged_at"));
        t.setCorrectionWindowEnd(rs.getTimestamp("correction_window_end"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setCompletedAt(rs.getTimestamp("completed_at"));
        t.setItemName(rs.getString("item_name"));
        t.setQuantity(rs.getInt("quantity"));
        t.setSpecialRequests(rs.getString("special_requests"));
        t.setPriority(rs.getString("priority"));
        t.setTableNumber(rs.getInt("table_number"));
        return t;
    }
}
