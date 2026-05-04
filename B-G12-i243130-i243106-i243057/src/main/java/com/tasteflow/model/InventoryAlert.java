package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Entity representing a low-stock inventory alert.
 *
 * Generated when an ingredient's current_stock drops to or below
 * its reorder_level after an order deduction. Displayed on the
 * Inventory Dashboard for the General Manager.
 *
 * This is a GRASP Information Expert — it knows whether it is
 * critical (stock = 0) vs. warning (stock > 0 but ≤ reorder_level).
 */
public class InventoryAlert {
    private int alertId;
    private int ingredientId;
    private String ingredientName;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal reorderLevel;
    private BigDecimal deficit;         // reorderLevel − currentStock
    private String severity;            // CRITICAL, WARNING
    private boolean resolved;
    private Timestamp triggeredAt;
    private Integer resolvedBy;         // user_id who acknowledged
    private Timestamp resolvedAt;

    public InventoryAlert() {}

    // --- Computed helpers ---

    /** Is stock at zero? */
    public boolean isCritical() {
        return currentStock != null && currentStock.compareTo(BigDecimal.ZERO) <= 0;
    }

    /** Percentage of stock remaining relative to reorder level. */
    public double getStockPercent() {
        if (reorderLevel == null || reorderLevel.compareTo(BigDecimal.ZERO) == 0) return 100.0;
        if (currentStock == null) return 0.0;
        return currentStock.doubleValue() / reorderLevel.doubleValue() * 100.0;
    }

    // --- Getters & Setters ---

    public int getAlertId() { return alertId; }
    public void setAlertId(int alertId) { this.alertId = alertId; }

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

    public BigDecimal getDeficit() { return deficit; }
    public void setDeficit(BigDecimal deficit) { this.deficit = deficit; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public Timestamp getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Timestamp triggeredAt) { this.triggeredAt = triggeredAt; }

    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    @Override
    public String toString() {
        return ingredientName + " [" + severity + "] — " +
               currentStock + "/" + reorderLevel + " " + unit;
    }
}
