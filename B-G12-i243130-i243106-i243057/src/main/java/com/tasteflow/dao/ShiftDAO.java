package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.StaffShift;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-Access Object for the staff_shift table.
 * Uses raw JDBC PreparedStatements — no ORM.
 *
 * DOMAIN RULE: A StaffShift cannot overlap with another shift
 * for the same User at the same Branch. Overlap is checked using
 * a SELECT … FOR UPDATE query with time-range intersection logic
 * inside a transaction before every INSERT.
 *
 * Overlap condition for two time ranges [A_start, A_end) and [B_start, B_end):
 *   A_start < B_end  AND  A_end > B_start
 *
 * Pattern: Table Data Gateway (GoF) — one class per table.
 */
public class ShiftDAO {

    // ──────────────────────────────────────────────────────────
    //  OVERLAP CHECK (the core domain-rule query)
    // ──────────────────────────────────────────────────────────

    /**
     * Overlap-detection SQL.
     *
     * Two shifts overlap on the same (user, branch, date) when:
     *   existing.start_time < new.end_time
     *   AND existing.end_time > new.start_time
     *
     * We exclude the shift being edited (if shiftId > 0) to allow
     * updates without false-positive self-overlap.
     *
     * Uses FOR UPDATE to lock candidate rows inside a transaction
     * so concurrent inserts cannot create overlapping shifts.
     */
    private static final String SQL_CHECK_OVERLAP =
        "SELECT COUNT(*) AS cnt " +
        "FROM staff_shift " +
        "WHERE user_id   = ? " +
        "  AND branch_id = ? " +
        "  AND shift_date = ? " +
        "  AND start_time < ? " +   // existing.start < new.end
        "  AND end_time   > ? " +   // existing.end   > new.start
        "  AND shift_id  <> ? " +   // exclude self when updating
        "FOR UPDATE";

    // ──────────────────────────────────────────────────────────
    //  INSERT (with overlap guard)
    // ──────────────────────────────────────────────────────────

    /**
     * Inserts a new shift after verifying no overlapping shift exists.
     * Runs inside a single JDBC transaction.
     *
     * @return generated shift_id
     * @throws SQLException with a clear message if overlap detected
     */
    public int insertShift(StaffShift shift) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Lock + check overlap
            if (hasOverlap(conn, shift, 0)) {
                throw new SQLException(
                    "⚠ SHIFT OVERLAP: This user already has a shift at " +
                    shift.getBranchName() + " on " + shift.getShiftDate() +
                    " that overlaps with " + shift.getStartTime() + "–" + shift.getEndTime() +
                    ". Please choose a different time slot.");
            }

            // 2. Insert the shift
            String sql =
                "INSERT INTO staff_shift (user_id, branch_id, shift_date, start_time, " +
                "end_time, role_on_shift, status, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            int shiftId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, shift.getUserId());
                ps.setInt(2, shift.getBranchId());
                ps.setDate(3, shift.getShiftDate());
                ps.setTime(4, shift.getStartTime());
                ps.setTime(5, shift.getEndTime());
                ps.setString(6, shift.getRoleOnShift());
                ps.setString(7, shift.getStatus());
                ps.setString(8, shift.getNotes());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        shiftId = keys.getInt(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated shift ID");
                    }
                }
            }

            conn.commit();
            shift.setShiftId(shiftId);
            return shiftId;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  DELETE
    // ──────────────────────────────────────────────────────────

    /**
     * Deletes a shift by its ID.
     */
    public void deleteShift(int shiftId) throws SQLException {
        String sql = "DELETE FROM staff_shift WHERE shift_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            ps.executeUpdate();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  QUERIES
    // ──────────────────────────────────────────────────────────

    /**
     * Returns shifts for a specific branch within a date range (inclusive).
     * Joins user table for display name.
     */
    public List<StaffShift> findByBranchAndDateRange(int branchId, Date from, Date to) throws SQLException {
        String sql =
            "SELECT s.*, u.full_name AS user_name, b.branch_name " +
            "FROM staff_shift s " +
            "JOIN `user` u ON s.user_id = u.user_id " +
            "JOIN branch b ON s.branch_id = b.branch_id " +
            "WHERE s.branch_id = ? " +
            "  AND s.shift_date BETWEEN ? AND ? " +
            "ORDER BY s.shift_date, s.start_time";

        List<StaffShift> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, branchId);
            ps.setDate(2, from);
            ps.setDate(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all shifts for a given week start date across all branches.
     */
    public List<StaffShift> findWeekShifts(Date weekStart) throws SQLException {
        // weekStart is Monday; weekEnd = weekStart + 6 days
        Date weekEnd = Date.valueOf(weekStart.toLocalDate().plusDays(6));

        String sql =
            "SELECT s.*, u.full_name AS user_name, b.branch_name " +
            "FROM staff_shift s " +
            "JOIN `user` u ON s.user_id = u.user_id " +
            "JOIN branch b ON s.branch_id = b.branch_id " +
            "WHERE s.shift_date BETWEEN ? AND ? " +
            "ORDER BY s.shift_date, s.start_time, u.full_name";

        List<StaffShift> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, weekStart);
            ps.setDate(2, weekEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Returns all active staff (users) for the combo box.
     * Returns a simple String list: "userId|fullName|role"
     */
    public List<String[]> findActiveStaff() throws SQLException {
        String sql =
            "SELECT user_id, full_name, role FROM `user` " +
            "WHERE is_active = TRUE ORDER BY full_name";

        List<String[]> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("user_id")),
                    rs.getString("full_name"),
                    rs.getString("role")
                });
            }
        }
        return list;
    }

    /**
     * Returns all active branches for the combo box.
     */
    public List<String[]> findActiveBranches() throws SQLException {
        String sql =
            "SELECT branch_id, branch_name FROM branch " +
            "WHERE is_active = TRUE ORDER BY branch_name";

        List<String[]> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new String[]{
                    String.valueOf(rs.getInt("branch_id")),
                    rs.getString("branch_name")
                });
            }
        }
        return list;
    }

    /**
     * Updates shift status (CHECKED_IN, CHECKED_OUT, ABSENT).
     */
    public void updateStatus(int shiftId, String status) throws SQLException {
        String sql = "UPDATE staff_shift SET status = ? WHERE shift_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, shiftId);
            ps.executeUpdate();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  PRIVATE helpers
    // ──────────────────────────────────────────────────────────

    /**
     * Checks for overlap using FOR UPDATE row locking.
     * Must run inside an active transaction.
     */
    private boolean hasOverlap(Connection conn, StaffShift shift, int excludeShiftId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_OVERLAP)) {
            ps.setInt(1, shift.getUserId());
            ps.setInt(2, shift.getBranchId());
            ps.setDate(3, shift.getShiftDate());
            ps.setTime(4, shift.getEndTime());      // existing.start < new.end
            ps.setTime(5, shift.getStartTime());     // existing.end   > new.start
            ps.setInt(6, excludeShiftId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("cnt") > 0;
            }
        }
    }

    private StaffShift mapRow(ResultSet rs) throws SQLException {
        StaffShift s = new StaffShift();
        s.setShiftId(rs.getInt("shift_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setBranchId(rs.getInt("branch_id"));
        s.setShiftDate(rs.getDate("shift_date"));
        s.setStartTime(rs.getTime("start_time"));
        s.setEndTime(rs.getTime("end_time"));
        s.setRoleOnShift(rs.getString("role_on_shift"));
        s.setStatus(rs.getString("status"));
        s.setNotes(rs.getString("notes"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        s.setUserName(rs.getString("user_name"));
        s.setBranchName(rs.getString("branch_name"));
        return s;
    }
}
