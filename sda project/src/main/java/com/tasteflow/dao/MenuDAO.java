package com.tasteflow.dao;

import com.tasteflow.DatabaseConnection;
import com.tasteflow.model.MenuItem;
import com.tasteflow.model.RecipeIngredient;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-Access Object for menu_item and recipe_ingredient tables.
 * Uses raw JDBC with manual transaction control for atomicity.
 *
 * Pattern: Facade — this DAO encapsulates all menu + recipe persistence
 * behind a single interface so controllers never touch JDBC directly.
 */
public class MenuDAO {

    // ------------------------------------------------------------------ QUERIES

    /**
     * Returns all menu items joined with their category name.
     */
    public List<MenuItem> findAll() throws SQLException {
        String sql =
            "SELECT mi.*, mc.category_name " +
            "FROM menu_item mi " +
            "JOIN menu_category mc ON mi.category_id = mc.category_id " +
            "ORDER BY mc.display_order, mi.item_name";

        List<MenuItem> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapMenuItem(rs));
            }
        }
        return list;
    }

    /**
     * Returns only available (is_available = true) menu items.
     * Used by POS to enforce domain rule: OrderItem cannot reference unavailable item.
     */
    public List<MenuItem> findAllAvailable() throws SQLException {
        String sql =
            "SELECT mi.*, mc.category_name " +
            "FROM menu_item mi " +
            "JOIN menu_category mc ON mi.category_id = mc.category_id " +
            "WHERE mi.is_available = TRUE " +
            "ORDER BY mc.display_order, mi.item_name";

        List<MenuItem> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapMenuItem(rs));
            }
        }
        return list;
    }

    /**
     * Loads the recipe (list of RecipeIngredients) for a given menu item.
     */
    public List<RecipeIngredient> findRecipeByItemId(int itemId) throws SQLException {
        String sql =
            "SELECT ri.*, i.ingredient_name, i.unit " +
            "FROM recipe_ingredient ri " +
            "JOIN ingredient i ON ri.ingredient_id = i.ingredient_id " +
            "WHERE ri.item_id = ? " +
            "ORDER BY i.ingredient_name";

        List<RecipeIngredient> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecipeIngredient ri = new RecipeIngredient();
                    ri.setRecipeId(rs.getInt("recipe_id"));
                    ri.setItemId(rs.getInt("item_id"));
                    ri.setIngredientId(rs.getInt("ingredient_id"));
                    ri.setIngredientName(rs.getString("ingredient_name"));
                    ri.setUnit(rs.getString("unit"));
                    ri.setQuantityRequired(rs.getBigDecimal("quantity_required"));
                    list.add(ri);
                }
            }
        }
        return list;
    }

    // -------------------------------------------------------------- INSERT (Atomic)

    /**
     * Inserts a MenuItem AND its recipe ingredients in a SINGLE transaction.
     * If any part fails the entire operation rolls back.
     */
    public int insertWithRecipe(MenuItem item) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);    // BEGIN TRANSACTION

            // 1. Insert the menu_item
            int itemId = insertMenuItem(conn, item);
            item.setItemId(itemId);

            // 2. Insert each recipe_ingredient row
            for (RecipeIngredient ri : item.getRecipeIngredients()) {
                ri.setItemId(itemId);
                insertRecipeIngredient(conn, ri);
            }

            conn.commit();               // COMMIT
            return itemId;

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();   // ROLLBACK on any failure
            throw ex;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // -------------------------------------------------------------- UPDATE (Atomic)

    /**
     * Updates a MenuItem's basic fields AND replaces its recipe atomically.
     * Strategy: DELETE old recipe rows → INSERT new ones (inside same tx).
     */
    public void updateWithRecipe(MenuItem item) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Update menu_item row
            updateMenuItem(conn, item);

            // 2. Delete old recipe rows for this item
            deleteRecipeByItemId(conn, item.getItemId());

            // 3. Re-insert fresh recipe rows
            for (RecipeIngredient ri : item.getRecipeIngredients()) {
                ri.setItemId(item.getItemId());
                insertRecipeIngredient(conn, ri);
            }

            conn.commit();

        } catch (SQLException ex) {
            if (conn != null) conn.rollback();
            throw ex;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // -------------------------------------------------------------- DELETE

    /**
     * Soft-delete: sets is_available = false so historical orders stay valid.
     */
    public void softDelete(int itemId) throws SQLException {
        String sql = "UPDATE menu_item SET is_available = FALSE WHERE item_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        }
    }

    // ====================================================================
    //  PRIVATE helpers — single-connection, called inside a transaction
    // ====================================================================

    private int insertMenuItem(Connection conn, MenuItem item) throws SQLException {
        String sql =
            "INSERT INTO menu_item (category_id, item_name, description, price, " +
            "image_url, is_available, preparation_time_min) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getItemName());
            ps.setString(3, item.getDescription());
            ps.setBigDecimal(4, item.getPrice());
            ps.setString(5, item.getImageUrl());
            ps.setBoolean(6, item.isAvailable());
            ps.setInt(7, item.getPreparationTimeMin());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to retrieve generated menu_item ID");
    }

    private void updateMenuItem(Connection conn, MenuItem item) throws SQLException {
        String sql =
            "UPDATE menu_item SET category_id = ?, item_name = ?, description = ?, " +
            "price = ?, image_url = ?, is_available = ?, preparation_time_min = ? " +
            "WHERE item_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getCategoryId());
            ps.setString(2, item.getItemName());
            ps.setString(3, item.getDescription());
            ps.setBigDecimal(4, item.getPrice());
            ps.setString(5, item.getImageUrl());
            ps.setBoolean(6, item.isAvailable());
            ps.setInt(7, item.getPreparationTimeMin());
            ps.setInt(8, item.getItemId());
            ps.executeUpdate();
        }
    }

    private void insertRecipeIngredient(Connection conn, RecipeIngredient ri) throws SQLException {
        String sql =
            "INSERT INTO recipe_ingredient (item_id, ingredient_id, quantity_required) " +
            "VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ri.getItemId());
            ps.setInt(2, ri.getIngredientId());
            ps.setBigDecimal(3, ri.getQuantityRequired());
            ps.executeUpdate();
        }
    }

    private void deleteRecipeByItemId(Connection conn, int itemId) throws SQLException {
        String sql = "DELETE FROM recipe_ingredient WHERE item_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        }
    }

    // ====================================================================
    //  Row mapper
    // ====================================================================

    private MenuItem mapMenuItem(ResultSet rs) throws SQLException {
        MenuItem m = new MenuItem();
        m.setItemId(rs.getInt("item_id"));
        m.setCategoryId(rs.getInt("category_id"));
        m.setCategoryName(rs.getString("category_name"));
        m.setItemName(rs.getString("item_name"));
        m.setDescription(rs.getString("description"));
        m.setPrice(rs.getBigDecimal("price"));
        m.setImageUrl(rs.getString("image_url"));
        m.setAvailable(rs.getBoolean("is_available"));
        m.setPreparationTimeMin(rs.getInt("preparation_time_min"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        return m;
    }
}
