package com.tasteflow.model;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entity representing a table reservation.
 * Domain Rule: UNIQUE(table_id, reservation_date, start_time) at DB level
 * prevents double-booking.
 */
public class Reservation {
    private int reservationId;
    private int tableId;
    private int tableNumber;       // denormalised for display
    private int tableCapacity;     // denormalised for display
    private Integer customerId;
    private String guestName;
    private String guestPhone;
    private int guestCount;
    private LocalDate reservationDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;         // CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW
    private String specialRequests;
    private Timestamp createdAt;

    public Reservation() {
        this.status = "CONFIRMED";
    }

    // --- Getters & Setters ---

    public int getReservationId() { return reservationId; }
    public void setReservationId(int reservationId) { this.reservationId = reservationId; }

    public int getTableId() { return tableId; }
    public void setTableId(int tableId) { this.tableId = tableId; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }

    public int getTableCapacity() { return tableCapacity; }
    public void setTableCapacity(int tableCapacity) { this.tableCapacity = tableCapacity; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }

    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }

    public int getGuestCount() { return guestCount; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }

    public LocalDate getReservationDate() { return reservationDate; }
    public void setReservationDate(LocalDate reservationDate) { this.reservationDate = reservationDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Res#" + reservationId + " - " + guestName + " (Table " + tableNumber + ")";
    }
}
