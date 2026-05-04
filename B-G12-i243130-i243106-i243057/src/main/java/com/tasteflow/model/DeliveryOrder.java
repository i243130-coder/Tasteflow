package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Entity representing a delivery order — specialized from Order.
 * Maps to the delivery_order table which extends the base order.
 */
public class DeliveryOrder {
    private int deliveryId;
    private int orderId;
    private Integer driverId;
    private String riderName;
    private String deliveryAddress;
    private String deliveryPhone;
    private Timestamp estimatedDeliveryTime;
    private Timestamp actualDeliveryTime;
    private String status;           // PENDING_ASSIGNMENT, ASSIGNED, PICKED_UP, IN_TRANSIT, DELIVERED, FAILED, RETURNED
    private BigDecimal currentLat;
    private BigDecimal currentLng;
    private BigDecimal distanceKm;
    private BigDecimal deliveryFee;
    private String notes;
    private String platformSource;   // TASTEFLOW, FOODPANDA, UBER_EATS, etc.
    private Timestamp createdAt;

    // Denormalised from order
    private BigDecimal orderTotal;
    private String orderStatus;
    private String customerName;

    public DeliveryOrder() {
        this.status = "PENDING_ASSIGNMENT";
        this.platformSource = "TASTEFLOW";
        this.deliveryFee = BigDecimal.ZERO;
    }

    // --- Getters & Setters ---
    public int getDeliveryId() { return deliveryId; }
    public void setDeliveryId(int deliveryId) { this.deliveryId = deliveryId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public Integer getDriverId() { return driverId; }
    public void setDriverId(Integer driverId) { this.driverId = driverId; }

    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getDeliveryPhone() { return deliveryPhone; }
    public void setDeliveryPhone(String deliveryPhone) { this.deliveryPhone = deliveryPhone; }

    public Timestamp getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(Timestamp estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }

    public Timestamp getActualDeliveryTime() { return actualDeliveryTime; }
    public void setActualDeliveryTime(Timestamp actualDeliveryTime) { this.actualDeliveryTime = actualDeliveryTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getCurrentLat() { return currentLat; }
    public void setCurrentLat(BigDecimal currentLat) { this.currentLat = currentLat; }

    public BigDecimal getCurrentLng() { return currentLng; }
    public void setCurrentLng(BigDecimal currentLng) { this.currentLng = currentLng; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPlatformSource() { return platformSource; }
    public void setPlatformSource(String platformSource) { this.platformSource = platformSource; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
