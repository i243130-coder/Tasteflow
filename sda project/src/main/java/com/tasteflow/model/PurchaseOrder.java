package com.tasteflow.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a purchase order placed with a supplier.
 * A PurchaseOrder has 1..* PurchaseOrderItems (composition).
 */
public class PurchaseOrder {
    private int poId;
    private int supplierId;
    private String supplierName;       // denormalised for display
    private int branchId;
    private int orderedBy;
    private Timestamp orderDate;
    private LocalDate expectedDelivery;
    private String status;             // DRAFT, SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    private BigDecimal totalAmount;
    private String notes;

    /** Composition: the line items for this PO */
    private List<PurchaseOrderItem> items = new ArrayList<>();

    public PurchaseOrder() {
        this.status = "DRAFT";
        this.totalAmount = BigDecimal.ZERO;
    }

    // --- Getters & Setters ---

    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public int getOrderedBy() { return orderedBy; }
    public void setOrderedBy(int orderedBy) { this.orderedBy = orderedBy; }

    public Timestamp getOrderDate() { return orderDate; }
    public void setOrderDate(Timestamp orderDate) { this.orderDate = orderDate; }

    public LocalDate getExpectedDelivery() { return expectedDelivery; }
    public void setExpectedDelivery(LocalDate expectedDelivery) { this.expectedDelivery = expectedDelivery; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<PurchaseOrderItem> getItems() { return items; }
    public void setItems(List<PurchaseOrderItem> items) { this.items = items; }

    /** Recalculates totalAmount from line items. */
    public void recalculateTotal() {
        this.totalAmount = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (item.getUnitPrice() != null && item.getQuantityOrdered() != null) {
                this.totalAmount = this.totalAmount.add(
                    item.getUnitPrice().multiply(item.getQuantityOrdered()));
            }
        }
    }

    @Override
    public String toString() {
        return "PO-" + poId + " (" + status + ")";
    }
}
