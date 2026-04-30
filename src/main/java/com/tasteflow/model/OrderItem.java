package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Entity representing a single line item within an order.
 */
public class OrderItem {
    private int orderItemId;
    private int orderId;
    private int itemId;
    private String itemName;        // denormalised for display
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String specialRequests;
    private String status;          // QUEUED, PREPARING, READY, SERVED, CANCELLED
    private String priority;        // NORMAL, RUSH, VIP
    private Timestamp sentToKdsAt;
    private Timestamp readyAt;

    public OrderItem() {
        this.quantity = 1;
        this.status = "QUEUED";
        this.priority = "NORMAL";
    }

    public OrderItem(int itemId, String itemName, int quantity, BigDecimal unitPrice) {
        this();
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // --- Getters & Setters ---

    public int getOrderItemId() { return orderItemId; }
    public void setOrderItemId(int orderItemId) { this.orderItemId = orderItemId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Timestamp getSentToKdsAt() { return sentToKdsAt; }
    public void setSentToKdsAt(Timestamp sentToKdsAt) { this.sentToKdsAt = sentToKdsAt; }

    public Timestamp getReadyAt() { return readyAt; }
    public void setReadyAt(Timestamp readyAt) { this.readyAt = readyAt; }
}
