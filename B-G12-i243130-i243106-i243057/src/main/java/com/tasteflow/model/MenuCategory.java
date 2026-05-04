package com.tasteflow.model;

/**
 * Entity representing a menu category (e.g. Appetizers, Mains, Desserts).
 */
public class MenuCategory {
    private int categoryId;
    private String categoryName;
    private int displayOrder;
    private boolean active;

    public MenuCategory() {}

    public MenuCategory(int categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    // --- Getters & Setters ---

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return categoryName;
    }
}
