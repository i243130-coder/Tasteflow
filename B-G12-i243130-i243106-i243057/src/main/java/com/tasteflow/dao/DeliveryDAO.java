package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.DeliveryOrder;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for delivery_order and delivery_status_log tables.
 *
 * Pattern: Facade — encapsulates all delivery persistence logic.
 */
public class DeliveryDAO {

    // ------------------------------------------------------------------ CREATE

    /**
     * Creates a delivery order linked to an existing order.
     */
    public int createDelivery(DeliveryOrder d) throws SQLException {
        String sql =
            "INSERT INTO delivery_order (order_id, driver_id, rider_name, delivery_address, " +
            "delivery_phone, status, delivery_fee, notes, platform_source) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, d.getOrderId());
            if (d.getDriverId() != null) {
                ps.setInt(2, d.getDriverId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, d.getRiderName());
            ps.setString(4, d.getDeliveryAddress());
            ps.setString(5, d.getDeliveryPhone());
            ps.setString(6, d.getStatus());
            ps.setBigDecimal(7, d.getDeliveryFee());
            ps.setString(8, d.getNotes());
            ps.setString(9, d.getPlatformSource());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to create delivery order");
    }

    // ------------------------------------------------------------------ QUERIES

    /**
     * Returns all active deliveries (not DELIVERED/FAILED/RETURNED).
     */
    public List<DeliveryOrder> findActiveDeliveries() throws SQLException {
        String sql =
            "SELECT d.*, o.total AS order_total, o.status AS order_status, " +
            "       COALESCE(c.full_name, 'N/A') AS customer_name " +
            "FROM delivery_order d " +
            "JOIN `order` o ON d.order_id = o.order_id " +
            "LEFT JOIN customer c ON o.customer_id = c.customer_id " +
            "WHERE d.status NOT IN ('DELIVERED', 'FAILED', 'RETURNED') " +
            "ORDER BY d.created_at DESC";

        return queryDeliveries(sql);
    }

    /**
     * Returns all deliveries for today.
     */
    public List<DeliveryOrder> findTodayDeliveries() throws SQLException {
        String sql =
            "SELECT d.*, o.total AS order_total, o.status AS order_status, " +
            "       COALESCE(c.full_name, 'N/A') AS customer_name " +
            "FROM delivery_order d " +
            "JOIN `order` o ON d.order_id = o.order_id " +
            "LEFT JOIN customer c ON o.customer_id = c.customer_id " +
            "WHERE DATE(d.created_at) = CURDATE() " +
            "ORDER BY d.created_at DESC";

        return queryDeliveries(sql);
    }

    /**
     * Returns available riders (DELIVERY_DRIVER role).
     */
    public List<String[]> findAvailableRiders() throws SQLException {
        String sql = "SELECT user_id, full_name FROM `user` WHERE role = 'DELIVERY_DRIVER' ORDER BY full_name";
        List<String[]> riders = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                riders.add(new String[]{ String.valueOf(rs.getInt("user_id")), rs.getString("full_name") });
            }
        }
        return riders;
    }

    // ------------------------------------------------------------------ STATUS TRANSITIONS

    /**
     * Assign a rider to a delivery.
     */
    public void assignRider(int deliveryId, int riderId, String riderName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            String sql = "UPDATE delivery_order SET driver_id = ?, rider_name = ?, status = 'ASSIGNED' " +
                         "WHERE delivery_id = ? AND status = 'PENDING_ASSIGNMENT'";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, riderId);
                ps.setString(2, riderName);
                ps.setInt(3, deliveryId);
                int rows = ps.executeUpdate();
                if (rows == 0) throw new SQLException("Delivery #" + deliveryId + " is not in PENDING_ASSIGNMENT status.");
            }

            logStatusChange(conn, deliveryId, "ASSIGNED", "Rider assigned: " + riderName);
            conn.commit();
        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    /**
     * Update delivery status with audit logging.
     */
    public void updateStatus(int deliveryId, String newStatus, String note) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            String sql;
            if ("DELIVERED".equals(newStatus)) {
                sql = "UPDATE delivery_order SET status = ?, actual_delivery_time = NOW() WHERE delivery_id = ?";
            } else {
                sql = "UPDATE delivery_order SET status = ? WHERE delivery_id = ?";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newStatus);
                ps.setInt(2, deliveryId);
                ps.executeUpdate();
            }

            logStatusChange(conn, deliveryId, newStatus, note);
            conn.commit();
        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    /**
     * Returns the status history for a delivery.
     */
    public List<String> findStatusHistory(int deliveryId) throws SQLException {
        String sql = "SELECT status, note, logged_at FROM delivery_status_log " +
                     "WHERE delivery_id = ? ORDER BY logged_at ASC";

        List<String> lines = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(rs.getTimestamp("logged_at") + " | " +
                              rs.getString("status") + " | " +
                              (rs.getString("note") != null ? rs.getString("note") : ""));
                }
            }
        }
        return lines;
    }

    // ====================================================================
    //  PRIVATE helpers
    // ====================================================================

    private void logStatusChange(Connection conn, int deliveryId, String status, String note) throws SQLException {
        String sql = "INSERT INTO delivery_status_log (delivery_id, status, note) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, deliveryId);
            ps.setString(2, status);
            ps.setString(3, note);
            ps.executeUpdate();
        }
    }

    private List<DeliveryOrder> queryDeliveries(String sql) throws SQLException {
        List<DeliveryOrder> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapDelivery(rs));
            }
        }
        return list;
    }

    private DeliveryOrder mapDelivery(ResultSet rs) throws SQLException {
        DeliveryOrder d = new DeliveryOrder();
        d.setDeliveryId(rs.getInt("delivery_id"));
        d.setOrderId(rs.getInt("order_id"));
        int dId = rs.getInt("driver_id");
        d.setDriverId(rs.wasNull() ? null : dId);
        d.setRiderName(rs.getString("rider_name"));
        d.setDeliveryAddress(rs.getString("delivery_address"));
        d.setDeliveryPhone(rs.getString("delivery_phone"));
        d.setEstimatedDeliveryTime(rs.getTimestamp("estimated_delivery_time"));
        d.setActualDeliveryTime(rs.getTimestamp("actual_delivery_time"));
        d.setStatus(rs.getString("status"));
        d.setCurrentLat(rs.getBigDecimal("current_lat"));
        d.setCurrentLng(rs.getBigDecimal("current_lng"));
        d.setDistanceKm(rs.getBigDecimal("distance_km"));
        d.setDeliveryFee(rs.getBigDecimal("delivery_fee"));
        d.setNotes(rs.getString("notes"));
        d.setPlatformSource(rs.getString("platform_source"));
        d.setCreatedAt(rs.getTimestamp("created_at"));
        d.setOrderTotal(rs.getBigDecimal("order_total"));
        d.setOrderStatus(rs.getString("order_status"));
        d.setCustomerName(rs.getString("customer_name"));
        return d;
    }
}
