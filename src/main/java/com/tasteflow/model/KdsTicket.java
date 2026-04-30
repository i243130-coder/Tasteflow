package com.tasteflow.model;

import java.sql.Timestamp;

/**
 * Entity representing a KDS (Kitchen Display System) ticket.
 * Each ticket corresponds to one OrderItem.
 */
public class KdsTicket {
    private int ticketId;
    private int orderItemId;
    private int orderId;
    private String station;          // GRILL, FRY, SALAD, DESSERT, BEVERAGE, GENERAL
    private String status;           // PENDING, IN_PROGRESS, READY, RECALLED
    private boolean hasAllergenWarning;
    private boolean allergenAcknowledged;
    private Integer acknowledgedBy;
    private Timestamp acknowledgedAt;
    private Timestamp correctionWindowEnd;
    private Timestamp createdAt;
    private Timestamp completedAt;

    // Denormalised fields for display
    private String itemName;
    private int quantity;
    private String specialRequests;
    private String priority;
    private int tableNumber;
    private String allergenFlags;    // comma-separated allergen names (e.g. "Gluten, Dairy")

    public KdsTicket() {
        this.status = "PENDING";
        this.station = "GENERAL";
    }

    /** How many seconds remain in the correction window, or -1 if expired. */
    public long getCorrectionSecondsRemaining() {
        if (correctionWindowEnd == null) return -1;
        long remaining = (correctionWindowEnd.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, -1);
    }

    // --- Getters & Setters ---

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public int getOrderItemId() { return orderItemId; }
    public void setOrderItemId(int orderItemId) { this.orderItemId = orderItemId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isHasAllergenWarning() { return hasAllergenWarning; }
    public void setHasAllergenWarning(boolean hasAllergenWarning) { this.hasAllergenWarning = hasAllergenWarning; }

    public boolean isAllergenAcknowledged() { return allergenAcknowledged; }
    public void setAllergenAcknowledged(boolean allergenAcknowledged) { this.allergenAcknowledged = allergenAcknowledged; }

    public Integer getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(Integer acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public Timestamp getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Timestamp acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public Timestamp getCorrectionWindowEnd() { return correctionWindowEnd; }
    public void setCorrectionWindowEnd(Timestamp correctionWindowEnd) { this.correctionWindowEnd = correctionWindowEnd; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }

    public String getAllergenFlags() { return allergenFlags; }
    public void setAllergenFlags(String allergenFlags) { this.allergenFlags = allergenFlags; }
}
