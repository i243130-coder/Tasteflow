package com.tasteflow.model;

import java.sql.Timestamp;

/**
 * Entity representing a loyalty programme customer.
 */
public class Customer {
    private int customerId;
    private String fullName;
    private String phone;
    private String email;
    private int loyaltyPoints;
    private String loyaltyTier;     // BRONZE, SILVER, GOLD, PLATINUM
    private Timestamp createdAt;

    public Customer() {
        this.loyaltyPoints = 0;
        this.loyaltyTier = "BRONZE";
    }

    /** 1 point = $0.10 discount */
    public static final java.math.BigDecimal POINT_VALUE =
            new java.math.BigDecimal("0.10");

    // --- Getters & Setters ---

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public String getLoyaltyTier() { return loyaltyTier; }
    public void setLoyaltyTier(String loyaltyTier) { this.loyaltyTier = loyaltyTier; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return fullName + " (" + phone + ") — " + loyaltyPoints + " pts [" + loyaltyTier + "]";
    }
}
