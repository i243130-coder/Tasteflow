package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.Reservation;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the reservation table.
 *
 * Domain Rule: UNIQUE(table_id, reservation_date, start_time) at DB level
 * prevents double-booking. This DAO additionally checks for TIME OVERLAP
 * before insert, and gracefully catches SQLIntegrityConstraintViolationException
 * returning a clean error message to the UI.
 *
 * Pattern: Facade — encapsulates all reservation persistence logic.
 */
public class ReservationDAO {

    // MySQL error code for duplicate-key violation
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    // ------------------------------------------------------------------ QUERIES

    /**
     * Returns all reservations for a given date, joined with table info.
     */
    public List<Reservation> findByDate(LocalDate date) throws SQLException {
        String sql =
            "SELECT r.*, dt.table_number, dt.capacity " +
            "FROM reservation r " +
            "JOIN dining_table dt ON r.table_id = dt.table_id " +
            "WHERE r.reservation_date = ? " +
            "ORDER BY r.start_time, dt.table_number";

        List<Reservation> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all upcoming reservations (today onward), active statuses only.
     */
    public List<Reservation> findUpcoming() throws SQLException {
        String sql =
            "SELECT r.*, dt.table_number, dt.capacity " +
            "FROM reservation r " +
            "JOIN dining_table dt ON r.table_id = dt.table_id " +
            "WHERE r.reservation_date >= CURDATE() " +
            "  AND r.status IN ('CONFIRMED', 'SEATED') " +
            "ORDER BY r.reservation_date, r.start_time, dt.table_number";

        List<Reservation> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ INSERT

    /**
     * Creates a new reservation.
     *
     * Before inserting, checks for TIME OVERLAP with existing reservations
     * on the same table and date. If overlap is found, throws SQLException
     * with a clean message. Also catches MySQL duplicate-key violations
     * (UNIQUE constraint) as a safety net.
     *
     * @return the generated reservation_id
     * @throws SQLException with user-friendly message on double-booking
     */
    public int insert(Reservation res) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Check for overlapping reservations on same table + date
            if (hasOverlap(conn, res.getTableId(), res.getReservationDate(),
                           res.getStartTime(), res.getEndTime(), -1)) {
                throw new SQLException(
                    "⚠ DOUBLE-BOOKING: Table " + res.getTableId() +
                    " already has a reservation that overlaps with " +
                    res.getStartTime() + " – " + res.getEndTime() +
                    " on " + res.getReservationDate() + ".");
            }

            // 2. Insert the reservation
            String sql =
                "INSERT INTO reservation (table_id, customer_id, guest_name, guest_phone, " +
                "guest_count, reservation_date, start_time, end_time, status, special_requests) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, res.getTableId());
                if (res.getCustomerId() != null) {
                    ps.setInt(2, res.getCustomerId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, res.getGuestName());
                ps.setString(4, res.getGuestPhone());
                ps.setInt(5, res.getGuestCount());
                ps.setDate(6, Date.valueOf(res.getReservationDate()));
                ps.setTime(7, Time.valueOf(res.getStartTime()));
                ps.setTime(8, Time.valueOf(res.getEndTime()));
                ps.setString(9, res.getStatus());
                ps.setString(10, res.getSpecialRequests());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int id = keys.getInt(1);
                        res.setReservationId(id);
                        conn.commit();
                        return id;
                    }
                }
            }

            conn.commit();
            throw new SQLException("Failed to retrieve generated reservation ID");

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();

            // Catch MySQL duplicate-key violation as safety net
            if (ex.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                throw new SQLException(
                    "⚠ DOUBLE-BOOKING: This exact time slot is already reserved for this table.");
            }
            throw ex;

        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ------------------------------------------------------------------ UPDATE STATUS

    /**
     * Updates a reservation's status (e.g. CONFIRMED → SEATED → COMPLETED / CANCELLED).
     */
    public void updateStatus(int reservationId, String newStatus) throws SQLException {
        String sql = "UPDATE reservation SET status = ? WHERE reservation_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, reservationId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Reservation #" + reservationId + " not found.");
            }
        }
    }

    // ====================================================================
    //  PRIVATE helpers
    // ====================================================================

    /**
     * Checks if any ACTIVE reservation overlaps the given time window
     * on the same table and date. Uses the overlap formula:
     *   existing.start_time < newEndTime AND existing.end_time > newStartTime
     *
     * @param excludeId  reservation_id to exclude (for updates), or -1 for new inserts
     */
    private boolean hasOverlap(Connection conn, int tableId, LocalDate date,
                               LocalTime startTime, LocalTime endTime, int excludeId) throws SQLException {
        String sql =
            "SELECT COUNT(*) FROM reservation " +
            "WHERE table_id = ? AND reservation_date = ? " +
            "  AND start_time < ? AND end_time > ? " +
            "  AND status NOT IN ('CANCELLED', 'NO_SHOW') " +
            "  AND reservation_id != ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setDate(2, Date.valueOf(date));
            ps.setTime(3, Time.valueOf(endTime));
            ps.setTime(4, Time.valueOf(startTime));
            ps.setInt(5, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ====================================================================
    //  Row mapper
    // ====================================================================

    private Reservation mapRow(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setReservationId(rs.getInt("reservation_id"));
        r.setTableId(rs.getInt("table_id"));
        r.setTableNumber(rs.getInt("table_number"));
        r.setTableCapacity(rs.getInt("capacity"));
        int custId = rs.getInt("customer_id");
        r.setCustomerId(rs.wasNull() ? null : custId);
        r.setGuestName(rs.getString("guest_name"));
        r.setGuestPhone(rs.getString("guest_phone"));
        r.setGuestCount(rs.getInt("guest_count"));
        r.setReservationDate(rs.getDate("reservation_date").toLocalDate());
        r.setStartTime(rs.getTime("start_time").toLocalTime());
        r.setEndTime(rs.getTime("end_time").toLocalTime());
        r.setStatus(rs.getString("status"));
        r.setSpecialRequests(rs.getString("special_requests"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }
}
