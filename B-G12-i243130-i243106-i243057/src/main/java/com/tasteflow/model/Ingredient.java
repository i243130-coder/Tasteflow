package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Entity representing a raw ingredient in inventory.
 */
public class Ingredient {
    private int ingredientId;
    private String ingredientName;
    private String unit;                // kg, litre, piece, etc.
    private BigDecimal currentStock;
    private BigDecimal reorderLevel;
    private BigDecimal costPerUnit;
    private boolean active;
    private Timestamp lastUpdated;

    public Ingredient() {
        this.active = true;
    }

    // --- Getters & Setters ---

    public int getIngredientId() { return ingredientId; }
    public void setIngredientId(int ingredientId) { this.ingredientId = ingredientId; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }

    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }

    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Timestamp getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }

    @Override
    public String toString() {
        return ingredientName + " (" + unit + ")";
    }
}
