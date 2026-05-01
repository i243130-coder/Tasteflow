package com.tasteflow.model;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Entity representing a staff shift assignment.
 * Maps to the staff_shift table.
 *
 * Domain Rule: A StaffShift cannot overlap with another shift
 * for the same User at the same Branch. Overlap prevention is
 * enforced at the DAO layer using SELECT … FOR UPDATE.
 */
public class StaffShift {
    private int shiftId;
    private int userId;
    private int branchId;
    private Date shiftDate;
    private Time startTime;
    private Time endTime;
    private String roleOnShift;   // CHEF, WAITER, CASHIER, MANAGER, DELIVERY_DRIVER
    private String status;        // SCHEDULED, CHECKED_IN, CHECKED_OUT, ABSENT
    private String notes;
    private Timestamp createdAt;

    // Denormalised display fields (joined from user / branch)
    private String userName;
    private String branchName;

    public StaffShift() {
        this.status = "SCHEDULED";
    }

    // --- Getters & Setters ---

    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public Date getShiftDate() { return shiftDate; }
    public void setShiftDate(Date shiftDate) { this.shiftDate = shiftDate; }

    public Time getStartTime() { return startTime; }
    public void setStartTime(Time startTime) { this.startTime = startTime; }

    public Time getEndTime() { return endTime; }
    public void setEndTime(Time endTime) { this.endTime = endTime; }

    public String getRoleOnShift() { return roleOnShift; }
    public void setRoleOnShift(String roleOnShift) { this.roleOnShift = roleOnShift; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    /** Formatted time range for display (e.g. "09:00 – 17:00") */
    public String getTimeRange() {
        if (startTime == null || endTime == null) return "";
        return startTime.toString().substring(0, 5) + " – " + endTime.toString().substring(0, 5);
    }

    @Override
    public String toString() {
        return (userName != null ? userName : "User#" + userId)
                + " [" + roleOnShift + "] " + getTimeRange();
    }
}
