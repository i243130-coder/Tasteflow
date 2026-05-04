package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a customer order.
 * An Order has 1..* OrderItems (composition).
 */
public class Order {
    private int orderId;
    private int branchId;
    private Integer tableId;
    private Integer customerId;
    private Integer reservationId;
    private Integer waiterId;
    private String orderType;        // DINE_IN, TAKEAWAY, DELIVERY, PRE_ORDER
    private String status;           // PENDING, IN_PROGRESS, READY, SERVED, COMPLETED, CANCELLED
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal discount;
    private int loyaltyPointsRedeemed;
    private BigDecimal total;
    private String specialInstructions;
    private boolean isHeld;          // true = pre-order held until guest is seated
    private Timestamp createdAt;
    private Timestamp completedAt;

    private List<OrderItem> items = new ArrayList<>();

    // Denormalised for display
    private int tableNumber;

    public Order() {
        this.branchId = 1;
        this.orderType = "DINE_IN";
        this.status = "PENDING";
        this.subtotal = BigDecimal.ZERO;
        this.tax = BigDecimal.ZERO;
        this.discount = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
        this.loyaltyPointsRedeemed = 0;
    }

    /** Recalculates subtotal and total from line items. Tax = 10%. */
    public void recalculate() {
        this.subtotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            this.subtotal = this.subtotal.add(item.getSubtotal());
        }
        this.tax = this.subtotal.multiply(new BigDecimal("0.10"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.total = this.subtotal.add(this.tax).subtract(this.discount != null ? this.discount : BigDecimal.ZERO);
    }

    // --- Getters & Setters ---

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public Integer getTableId() { return tableId; }
    public void setTableId(Integer tableId) { this.tableId = tableId; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public Integer getReservationId() { return reservationId; }
    public void setReservationId(Integer reservationId) { this.reservationId = reservationId; }

    public Integer getWaiterId() { return waiterId; }
    public void setWaiterId(Integer waiterId) { this.waiterId = waiterId; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public int getLoyaltyPointsRedeemed() { return loyaltyPointsRedeemed; }
    public void setLoyaltyPointsRedeemed(int loyaltyPointsRedeemed) { this.loyaltyPointsRedeemed = loyaltyPointsRedeemed; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }

    public boolean isHeld() { return isHeld; }
    public void setHeld(boolean held) { isHeld = held; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }
}
