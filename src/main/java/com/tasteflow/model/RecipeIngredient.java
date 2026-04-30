package com.tasteflow.model;

import java.math.BigDecimal;

/**
 * Bridge entity linking a MenuItem to an Ingredient with a required quantity.
 * Maps to the recipe_ingredient table.
 */
public class RecipeIngredient {
    private int recipeId;
    private int itemId;
    private int ingredientId;
    private String ingredientName;   // denormalised for display
    private String unit;             // denormalised for display
    private BigDecimal quantityRequired;

    public RecipeIngredient() {}

    public RecipeIngredient(int ingredientId, String ingredientName, String unit, BigDecimal quantityRequired) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.unit = unit;
        this.quantityRequired = quantityRequired;
    }

    // --- Getters & Setters ---

    public int getRecipeId() { return recipeId; }
    public void setRecipeId(int recipeId) { this.recipeId = recipeId; }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getIngredientId() { return ingredientId; }
    public void setIngredientId(int ingredientId) { this.ingredientId = ingredientId; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getQuantityRequired() { return quantityRequired; }
    public void setQuantityRequired(BigDecimal quantityRequired) { this.quantityRequired = quantityRequired; }
}
