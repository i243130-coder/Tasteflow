package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.DiningTable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for dining_table table.
 */
public class DiningTableDAO {

    public List<DiningTable> findAllByBranch(int branchId) throws SQLException {
        String sql = "SELECT * FROM dining_table WHERE branch_id = ? ORDER BY table_number";
        List<DiningTable> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public void updateStatus(int tableId, String status) throws SQLException {
        String sql = "UPDATE dining_table SET status = ? WHERE table_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, tableId);
            ps.executeUpdate();
        }
    }

    private DiningTable mapRow(ResultSet rs) throws SQLException {
        DiningTable t = new DiningTable();
        t.setTableId(rs.getInt("table_id"));
        t.setBranchId(rs.getInt("branch_id"));
        t.setTableNumber(rs.getInt("table_number"));
        t.setCapacity(rs.getInt("capacity"));
        t.setStatus(rs.getString("status"));
        return t;
    }
}
