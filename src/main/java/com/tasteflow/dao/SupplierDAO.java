package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.Supplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the supplier table using raw JDBC.
 */
public class SupplierDAO {

    public List<Supplier> findAllActive() throws SQLException {
        String sql = "SELECT * FROM supplier WHERE is_active = TRUE ORDER BY supplier_name";
        List<Supplier> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int insert(Supplier s) throws SQLException {
        String sql = "INSERT INTO supplier (supplier_name, contact_person, phone, email, address) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, s.getSupplierName());
            ps.setString(2, s.getContactPerson());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    s.setSupplierId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated supplier ID");
    }

    public void update(Supplier s) throws SQLException {
        String sql = "UPDATE supplier SET supplier_name=?, contact_person=?, phone=?, email=?, address=? " +
                     "WHERE supplier_id=?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, s.getSupplierName());
            ps.setString(2, s.getContactPerson());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setInt(6, s.getSupplierId());
            ps.executeUpdate();
        }
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setSupplierName(rs.getString("supplier_name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setActive(rs.getBoolean("is_active"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        return s;
    }
}
