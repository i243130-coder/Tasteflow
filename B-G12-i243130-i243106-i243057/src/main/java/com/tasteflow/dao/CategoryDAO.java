package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.MenuCategory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the menu_category table.
 */
public class CategoryDAO {

    public List<MenuCategory> findAllActive() throws SQLException {
        String sql = "SELECT * FROM menu_category WHERE is_active = TRUE ORDER BY display_order, category_name";
        List<MenuCategory> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                MenuCategory c = new MenuCategory();
                c.setCategoryId(rs.getInt("category_id"));
                c.setCategoryName(rs.getString("category_name"));
                c.setDisplayOrder(rs.getInt("display_order"));
                c.setActive(rs.getBoolean("is_active"));
                list.add(c);
            }
        }
        return list;
    }

    public int insert(MenuCategory cat) throws SQLException {
        String sql = "INSERT INTO menu_category (category_name, display_order) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, cat.getCategoryName());
            ps.setInt(2, cat.getDisplayOrder());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    cat.setCategoryId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated category ID");
    }
}
