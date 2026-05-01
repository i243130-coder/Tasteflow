package com.tasteflow.model;

import java.sql.Timestamp;

/**
 * Entity representing a loyalty points transaction (earn/redeem/adjust/expire).
 */
public class LoyaltyTransaction {
    private int transactionId;
    private int customerId;
    private Integer orderId;
    private String transactionType;  // EARNED, REDEEMED, ADJUSTED, EXPIRED
    private int points;
    private int balanceAfter;
    private String description;
    private Timestamp createdAt;

    public LoyaltyTransaction() {}

    // --- Getters & Setters ---

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
