package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.InventoryAlert;
import com.tasteflow.model.Ingredient;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for inventory alert queries.
 * Checks ingredient stock against reorder thresholds.
 *
 * Pattern: Table Data Gateway — exposes queries used by the
 * Inventory Dashboard and InventoryManager.
 */
public class InventoryAlertDAO {

    /**
     * Returns all ingredients whose current_stock ≤ reorder_level.
     * These are the "low stock" alerts for the manager dashboard.
     *
     * Results are sorted: CRITICAL (stock = 0) first, then by deficit descending.
     */
    public List<InventoryAlert> findActiveAlerts() throws SQLException {
        String sql =
            "SELECT ingredient_id, ingredient_name, unit, " +
            "       current_stock, reorder_level, " +
            "       (reorder_level - current_stock) AS deficit, " +
            "       CASE " +
            "           WHEN current_stock <= 0 THEN 'CRITICAL' " +
            "           ELSE 'WARNING' " +
            "       END AS severity " +
            "FROM ingredient " +
            "WHERE is_active = TRUE " +
            "  AND current_stock <= reorder_level " +
            "ORDER BY " +
            "  CASE WHEN current_stock <= 0 THEN 0 ELSE 1 END, " +
            "  (reorder_level - current_stock) DESC";

        List<InventoryAlert> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                InventoryAlert alert = new InventoryAlert();
                alert.setIngredientId(rs.getInt("ingredient_id"));
                alert.setIngredientName(rs.getString("ingredient_name"));
                alert.setUnit(rs.getString("unit"));
                alert.setCurrentStock(rs.getBigDecimal("current_stock"));
                alert.setReorderLevel(rs.getBigDecimal("reorder_level"));
                alert.setDeficit(rs.getBigDecimal("deficit"));
                alert.setSeverity(rs.getString("severity"));
                list.add(alert);
            }
        }
        return list;
    }

    /**
     * Returns all active ingredients with their stock info for the
     * full inventory table view.
     */
    public List<Ingredient> findAllIngredientsWithStatus() throws SQLException {
        String sql =
            "SELECT * FROM ingredient " +
            "WHERE is_active = TRUE " +
            "ORDER BY ingredient_name";

        List<Ingredient> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Ingredient i = new Ingredient();
                i.setIngredientId(rs.getInt("ingredient_id"));
                i.setIngredientName(rs.getString("ingredient_name"));
                i.setUnit(rs.getString("unit"));
                i.setCurrentStock(rs.getBigDecimal("current_stock"));
                i.setReorderLevel(rs.getBigDecimal("reorder_level"));
                i.setCostPerUnit(rs.getBigDecimal("cost_per_unit"));
                i.setActive(rs.getBoolean("is_active"));
                i.setLastUpdated(rs.getTimestamp("last_updated"));
                list.add(i);
            }
        }
        return list;
    }

    /**
     * Returns the count of critical and warning alerts.
     * [0] = critical count, [1] = warning count
     */
    public int[] getAlertCounts() throws SQLException {
        String sql =
            "SELECT " +
            "  SUM(CASE WHEN current_stock <= 0 THEN 1 ELSE 0 END) AS critical_count, " +
            "  SUM(CASE WHEN current_stock > 0 AND current_stock <= reorder_level THEN 1 ELSE 0 END) AS warning_count " +
            "FROM ingredient " +
            "WHERE is_active = TRUE AND current_stock <= reorder_level";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new int[]{
                    rs.getInt("critical_count"),
                    rs.getInt("warning_count")
                };
            }
        }
        return new int[]{0, 0};
    }
}
