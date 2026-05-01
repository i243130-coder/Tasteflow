package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a menu item.
 * A MenuItem has many RecipeIngredients defining its recipe.
 * Domain Rule: An OrderItem cannot reference a MenuItem where isAvailable = false.
 */
public class MenuItem {
    private int itemId;
    private int categoryId;
    private String categoryName;      // denormalised for display
    private String itemName;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private boolean available;
    private int preparationTimeMin;
    private Timestamp createdAt;

    /** Composition: the recipe for this item */
    private List<RecipeIngredient> recipeIngredients = new ArrayList<>();

    public MenuItem() {
        this.available = true;
        this.preparationTimeMin = 15;
    }

    // --- Getters & Setters ---

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public int getPreparationTimeMin() { return preparationTimeMin; }
    public void setPreparationTimeMin(int preparationTimeMin) { this.preparationTimeMin = preparationTimeMin; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public List<RecipeIngredient> getRecipeIngredients() { return recipeIngredients; }
    public void setRecipeIngredients(List<RecipeIngredient> recipeIngredients) {
        this.recipeIngredients = recipeIngredients;
    }

    @Override
    public String toString() {
        return itemName + " ($" + price + ")";
    }
}
