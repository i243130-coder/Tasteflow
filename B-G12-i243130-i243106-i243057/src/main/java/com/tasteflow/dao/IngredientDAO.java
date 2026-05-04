package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.Ingredient;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-Access Object for the ingredient table.
 * Uses raw JDBC PreparedStatements — no ORM.
 */
public class IngredientDAO {

    /**
     * Returns all active ingredients, ordered by name.
     */
    public List<Ingredient> findAllActive() throws SQLException {
        String sql = "SELECT * FROM ingredient WHERE is_active = TRUE ORDER BY ingredient_name";
        List<Ingredient> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Inserts a new ingredient and returns the generated key.
     */
    public int insert(Ingredient ing) throws SQLException {
        String sql = "INSERT INTO ingredient (ingredient_name, unit, current_stock, reorder_level, cost_per_unit) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, ing.getIngredientName());
            ps.setString(2, ing.getUnit());
            ps.setBigDecimal(3, ing.getCurrentStock() != null ? ing.getCurrentStock() : BigDecimal.ZERO);
            ps.setBigDecimal(4, ing.getReorderLevel() != null ? ing.getReorderLevel() : BigDecimal.ZERO);
            ps.setBigDecimal(5, ing.getCostPerUnit() != null ? ing.getCostPerUnit() : BigDecimal.ZERO);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    ing.setIngredientId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated ingredient ID");
    }

    // --- helper ---
    private Ingredient mapRow(ResultSet rs) throws SQLException {
        Ingredient i = new Ingredient();
        i.setIngredientId(rs.getInt("ingredient_id"));
        i.setIngredientName(rs.getString("ingredient_name"));
        i.setUnit(rs.getString("unit"));
        i.setCurrentStock(rs.getBigDecimal("current_stock"));
        i.setReorderLevel(rs.getBigDecimal("reorder_level"));
        i.setCostPerUnit(rs.getBigDecimal("cost_per_unit"));
        i.setActive(rs.getBoolean("is_active"));
        i.setLastUpdated(rs.getTimestamp("last_updated"));
        return i;
    }
}
