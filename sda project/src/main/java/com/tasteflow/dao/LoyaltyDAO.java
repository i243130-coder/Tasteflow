package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.Customer;

import java.math.BigDecimal;
import java.sql.*;

/**
 * DAO for customer loyalty operations.
 *
 * KEY DOMAIN RULE: pointsRedeemed MUST NOT exceed customer.loyaltyPoints
 * at the time of the transaction. Enforced via:
 *   1. Application-level check (SELECT ... FOR UPDATE)
 *   2. Database-level CHECK constraint (loyalty_points >= 0)
 *
 * Pattern: Facade — encapsulates customer lookup, point earn/redeem behind one call.
 */
public class LoyaltyDAO {

    /** Points earned per dollar spent. */
    private static final int POINTS_PER_DOLLAR = 1;

    // ------------------------------------------------------------------ LOOKUP

    /**
     * Find customer by phone number.
     */
    public Customer findByPhone(String phone) throws SQLException {
        String sql = "SELECT * FROM customer WHERE phone = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCustomer(rs);
            }
        }
        return null;
    }

    /**
     * Find customer by ID.
     */
    public Customer findById(int customerId) throws SQLException {
        String sql = "SELECT * FROM customer WHERE customer_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCustomer(rs);
            }
        }
        return null;
    }

    /**
     * Returns all customers ordered by name.
     */
    public java.util.List<Customer> findAll() throws SQLException {
        String sql = "SELECT * FROM customer ORDER BY full_name";

        java.util.List<Customer> list = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapCustomer(rs));
            }
        }
        return list;
    }

    /**
     * Search customers by name or phone (LIKE match).
     */
    public java.util.List<Customer> searchCustomers(String query) throws SQLException {
        String sql = "SELECT * FROM customer WHERE full_name LIKE ? OR phone LIKE ? ORDER BY full_name";
        String pattern = "%" + query.trim() + "%";

        java.util.List<Customer> list = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCustomer(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns loyalty transaction history for a customer.
     */
    public java.util.List<String> findTransactionHistory(int customerId) throws SQLException {
        String sql =
            "SELECT transaction_type, points, balance_after, description, created_at " +
            "FROM loyalty_transaction WHERE customer_id = ? ORDER BY created_at DESC LIMIT 50";

        java.util.List<String> lines = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String line = rs.getTimestamp("created_at") + " | " +
                                  rs.getString("transaction_type") + " | " +
                                  (rs.getInt("points") >= 0 ? "+" : "") + rs.getInt("points") + " pts | " +
                                  "Balance: " + rs.getInt("balance_after") + " | " +
                                  rs.getString("description");
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Manually award/adjust points for a customer (admin action).
     */
    public void awardManualPoints(int customerId, int points, String reason) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            int current = lockCustomerPoints(conn, customerId);
            int newBalance = current + points;
            if (newBalance < 0) newBalance = 0;

            updateCustomerPoints(conn, customerId, newBalance);
            updateTier(conn, customerId, newBalance);

            // Use 0 as orderId placeholder for manual adjustments
            String sql =
                "INSERT INTO loyalty_transaction (customer_id, transaction_type, " +
                "points, balance_after, description) VALUES (?, 'ADJUSTED', ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, customerId);
                ps.setInt(2, points);
                ps.setInt(3, newBalance);
                ps.setString(4, reason);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ------------------------------------------------------------------ REGISTER

    /**
     * Register a new loyalty customer.
     */
    public int registerCustomer(String fullName, String phone, String email) throws SQLException {
        String sql = "INSERT INTO customer (full_name, phone, email) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to register customer");
    }

    // ------------------------------------------------------------------ REDEEM POINTS

    /**
     * Redeem loyalty points as an order discount.
     * Runs in a JDBC transaction with row locking to enforce:
     *   pointsRedeemed <= customer.loyaltyPoints
     *
     * @param customerId   the customer
     * @param pointsToRedeem how many points to redeem
     * @param orderId      the order receiving the discount
     * @return the dollar discount amount (points * $0.10)
     * @throws SQLException if insufficient points or DB error
     */
    public BigDecimal redeemPoints(int customerId, int pointsToRedeem, int orderId) throws SQLException {
        if (pointsToRedeem <= 0) {
            throw new SQLException("Points to redeem must be positive.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Lock customer row and read current points
            int currentPoints = lockCustomerPoints(conn, customerId);

            // 2. DOMAIN RULE: pointsRedeemed MUST NOT exceed loyaltyPoints
            if (pointsToRedeem > currentPoints) {
                throw new SQLException(
                    "⚠ INSUFFICIENT POINTS: Customer has " + currentPoints +
                    " points but tried to redeem " + pointsToRedeem + ".");
            }

            int newBalance = currentPoints - pointsToRedeem;

            // 3. Deduct points from customer
            updateCustomerPoints(conn, customerId, newBalance);

            // 4. Log the transaction
            insertLoyaltyTransaction(conn, customerId, orderId, "REDEEMED",
                    -pointsToRedeem, newBalance,
                    "Redeemed " + pointsToRedeem + " pts on order #" + orderId);

            // 5. Update the order's loyalty_points_redeemed and discount
            BigDecimal discount = Customer.POINT_VALUE.multiply(BigDecimal.valueOf(pointsToRedeem));
            updateOrderDiscount(conn, orderId, pointsToRedeem, discount);

            conn.commit();
            return discount;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ------------------------------------------------------------------ EARN POINTS

    /**
     * Award loyalty points after an order is completed.
     * Rule: 1 point per $1 spent (rounded down).
     */
    public int earnPoints(int customerId, int orderId, BigDecimal orderTotal) throws SQLException {
        int pointsEarned = orderTotal.intValue() * POINTS_PER_DOLLAR;
        if (pointsEarned <= 0) return 0;

        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            int currentPoints = lockCustomerPoints(conn, customerId);
            int newBalance = currentPoints + pointsEarned;

            updateCustomerPoints(conn, customerId, newBalance);

            // Update tier based on total points
            updateTier(conn, customerId, newBalance);

            insertLoyaltyTransaction(conn, customerId, orderId, "EARNED",
                    pointsEarned, newBalance,
                    "Earned " + pointsEarned + " pts from order #" + orderId);

            conn.commit();
            return pointsEarned;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ====================================================================
    //  PRIVATE helpers
    // ====================================================================

    private int lockCustomerPoints(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT loyalty_points FROM customer WHERE customer_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("loyalty_points");
            }
        }
        throw new SQLException("Customer #" + customerId + " not found");
    }

    private void updateCustomerPoints(Connection conn, int customerId, int newPoints) throws SQLException {
        String sql = "UPDATE customer SET loyalty_points = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newPoints);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private void updateTier(Connection conn, int customerId, int totalPoints) throws SQLException {
        String tier;
        if (totalPoints >= 5000) {
            tier = "PLATINUM";
        } else if (totalPoints >= 2000) {
            tier = "GOLD";
        } else if (totalPoints >= 500) {
            tier = "SILVER";
        } else {
            tier = "BRONZE";
        }

        String sql = "UPDATE customer SET loyalty_tier = ? WHERE customer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tier);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private void insertLoyaltyTransaction(Connection conn, int customerId, int orderId,
                                          String type, int points, int balanceAfter,
                                          String description) throws SQLException {
        String sql =
            "INSERT INTO loyalty_transaction (customer_id, order_id, transaction_type, " +
            "points, balance_after, description) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, orderId);
            ps.setString(3, type);
            ps.setInt(4, points);
            ps.setInt(5, balanceAfter);
            ps.setString(6, description);
            ps.executeUpdate();
        }
    }

    private void updateOrderDiscount(Connection conn, int orderId,
                                     int pointsRedeemed, BigDecimal discount) throws SQLException {
        String sql =
            "UPDATE `order` SET loyalty_points_redeemed = ?, " +
            "discount = discount + ?, total = total - ? WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pointsRedeemed);
            ps.setBigDecimal(2, discount);
            ps.setBigDecimal(3, discount);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        }
    }

    private Customer mapCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getInt("customer_id"));
        c.setFullName(rs.getString("full_name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setLoyaltyPoints(rs.getInt("loyalty_points"));
        c.setLoyaltyTier(rs.getString("loyalty_tier"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        return c;
    }
}
