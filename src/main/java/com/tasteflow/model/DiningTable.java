package com.tasteflow.model;

/**
 * Entity representing a dining table in a branch.
 */
public class DiningTable {
    private int tableId;
    private int branchId;
    private int tableNumber;
    private int capacity;
    private String status;  // AVAILABLE, OCCUPIED, RESERVED, OUT_OF_SERVICE

    public DiningTable() {
        this.status = "AVAILABLE";
    }

    // --- Getters & Setters ---

    public int getTableId() { return tableId; }
    public void setTableId(int tableId) { this.tableId = tableId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "Table " + tableNumber + " (seats " + capacity + ")";
    }
}
