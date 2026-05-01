package com.tasteflow.model;

import java.math.BigDecimal;

/**
 * Entity representing a single line item in a purchase order.
 * Each line links to an Ingredient.
 */
public class PurchaseOrderItem {
    private int poItemId;
    private int poId;
    private int ingredientId;
    private String ingredientName;   // denormalised for display
    private String unit;             // denormalised for display
    private BigDecimal quantityOrdered;
    private BigDecimal quantityReceived;
    private BigDecimal unitPrice;

    public PurchaseOrderItem() {
        this.quantityReceived = BigDecimal.ZERO;
    }

    public PurchaseOrderItem(int ingredientId, String ingredientName, String unit,
                             BigDecimal quantityOrdered, BigDecimal unitPrice) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.unit = unit;
        this.quantityOrdered = quantityOrdered;
        this.unitPrice = unitPrice;
        this.quantityReceived = BigDecimal.ZERO;
    }

    /** Line total = quantity × unit price */
    public BigDecimal getLineTotal() {
        if (quantityOrdered != null && unitPrice != null) {
            return quantityOrdered.multiply(unitPrice);
        }
        return BigDecimal.ZERO;
    }

    // --- Getters & Setters ---

    public int getPoItemId() { return poItemId; }
    public void setPoItemId(int poItemId) { this.poItemId = poItemId; }

    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }

    public int getIngredientId() { return ingredientId; }
    public void setIngredientId(int ingredientId) { this.ingredientId = ingredientId; }

    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getQuantityOrdered() { return quantityOrdered; }
    public void setQuantityOrdered(BigDecimal quantityOrdered) { this.quantityOrdered = quantityOrdered; }

    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal quantityReceived) { this.quantityReceived = quantityReceived; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
