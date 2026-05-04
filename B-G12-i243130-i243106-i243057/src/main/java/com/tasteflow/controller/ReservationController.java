package com.tasteflow.controller;

import com.tasteflow.dao.DiningTableDAO;
import com.tasteflow.dao.OrderDAO;
import com.tasteflow.dao.ReservationDAO;
import com.tasteflow.model.DiningTable;
import com.tasteflow.model.Reservation;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Controller for reservation.fxml.
 * GRASP Controller — mediates between the reservation UI and DAOs.
 */
public class ReservationController {

    // ---- DAOs ----
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final DiningTableDAO tableDAO = new DiningTableDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    // ---- Booking form ----
    @FXML private ComboBox<DiningTable> tableCombo;
    @FXML private DatePicker datePicker;
    @FXML private Spinner<Integer> startHourSpinner;
    @FXML private Spinner<Integer> startMinSpinner;
    @FXML private Spinner<Integer> endHourSpinner;
    @FXML private Spinner<Integer> endMinSpinner;
    @FXML private TextField guestNameField;
    @FXML private TextField guestPhoneField;
    @FXML private Spinner<Integer> guestCountSpinner;
    @FXML private TextArea requestsArea;
    @FXML private Label statusLabel;

    // ---- Right side ----
    @FXML private DatePicker filterDatePicker;
    @FXML private TableView<Reservation> reservationTable;
    @FXML private TableColumn<Reservation, Number> resIdCol;
    @FXML private TableColumn<Reservation, String> resTableCol;
    @FXML private TableColumn<Reservation, String> resDateCol;
    @FXML private TableColumn<Reservation, String> resTimeCol;
    @FXML private TableColumn<Reservation, String> resGuestCol;
    @FXML private TableColumn<Reservation, String> resPhoneCol;
    @FXML private TableColumn<Reservation, Number> resSizeCol;
    @FXML private TableColumn<Reservation, String> resStatusCol;

    private final ObservableList<Reservation> reservations = FXCollections.observableArrayList();

    // ====================================================================
    //  INITIALIZE
    // ====================================================================

    @FXML
    public void initialize() {
        // Time spinners
        startHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 18));
        startMinSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0, 15));
        endHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 20));
        endMinSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0, 15));
        guestCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2));

        // Default date = today
        datePicker.setValue(LocalDate.now());
        filterDatePicker.setValue(LocalDate.now());

        // --- Table columns ---
        resIdCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getReservationId()));
        resTableCol.setCellValueFactory(cd -> new SimpleStringProperty(
                "T" + cd.getValue().getTableNumber() + " (" + cd.getValue().getTableCapacity() + ")"));
        resDateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getReservationDate().toString()));
        resTimeCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getStartTime().toString() + " – " + cd.getValue().getEndTime().toString()));
        resGuestCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getGuestName()));
        resPhoneCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getGuestPhone()));
        resSizeCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getGuestCount()));
        resStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));

        // Color the status column
        resStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("CONFIRMED".equals(item)) {
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                    } else if ("SEATED".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if ("CANCELLED".equals(item) || "NO_SHOW".equals(item)) {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    } else if ("COMPLETED".equals(item)) {
                        setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        reservationTable.setItems(reservations);

        // Load data
        loadTables();
        handleShowUpcoming();
    }

    // ====================================================================
    //  EVENT HANDLERS
    // ====================================================================

    /** Book a new reservation. */
    @FXML
    private void handleBook() {
        // Validate
        DiningTable table = tableCombo.getSelectionModel().getSelectedItem();
        if (table == null) { setStatus("⚠ Select a table.", true); return; }

        LocalDate date = datePicker.getValue();
        if (date == null) { setStatus("⚠ Select a date.", true); return; }

        String guestName = guestNameField.getText().trim();
        if (guestName.isEmpty()) { setStatus("⚠ Guest name is required.", true); return; }

        String guestPhone = guestPhoneField.getText().trim();
        if (guestPhone.isEmpty()) { setStatus("⚠ Guest phone is required.", true); return; }

        LocalTime startTime = LocalTime.of(startHourSpinner.getValue(), startMinSpinner.getValue());
        LocalTime endTime = LocalTime.of(endHourSpinner.getValue(), endMinSpinner.getValue());

        if (!endTime.isAfter(startTime)) {
            setStatus("⚠ End time must be after start time.", true);
            return;
        }

        int guestCount = guestCountSpinner.getValue();
        if (guestCount > table.getCapacity()) {
            setStatus("⚠ Party size (" + guestCount + ") exceeds table capacity (" + table.getCapacity() + ").", true);
            return;
        }

        // Build reservation
        Reservation res = new Reservation();
        res.setTableId(table.getTableId());
        res.setGuestName(guestName);
        res.setGuestPhone(guestPhone);
        res.setGuestCount(guestCount);
        res.setReservationDate(date);
        res.setStartTime(startTime);
        res.setEndTime(endTime);
        res.setSpecialRequests(requestsArea.getText().trim());
        res.setStatus("CONFIRMED");

        try {
            int id = reservationDAO.insert(res);
            setStatus("✅ Reservation #" + id + " booked — " + guestName +
                       " at Table " + table.getTableNumber() +
                       " on " + date + " " + startTime + "–" + endTime, false);
            handleClear();
            handleShowUpcoming();
        } catch (SQLException e) {
            // This catches both our overlap check AND MySQL constraint violations
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /**
     * Mark selected reservation as SEATED.
     * DOMAIN RULE: When seated, all held pre-orders for this reservation
     * are released — stock is deducted atomically and items become visible
     * to the KDS polling system.
     */
    @FXML
    private void handleMarkSeated() {
        Reservation sel = reservationTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a reservation from the table.", true);
            return;
        }

        try {
            // 1. Update reservation status to SEATED
            reservationDAO.updateStatus(sel.getReservationId(), "SEATED");

            // 2. Release any held pre-orders → deduct stock + fire to KDS
            int released = orderDAO.releaseHeldOrders(
                    sel.getReservationId(), sel.getTableId());

            String msg = "✅ Guest is now seated.";
            if (released > 0) {
                msg += " " + released + " pre-order(s) released to kitchen!";
            }
            setStatus(msg, false);
            handleShowUpcoming();

        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /** Mark selected reservation as COMPLETED. */
    @FXML
    private void handleMarkCompleted() {
        updateSelectedStatus("COMPLETED", "✔ Reservation completed.");
    }

    /** Cancel selected reservation. */
    @FXML
    private void handleCancel() {
        updateSelectedStatus("CANCELLED", "❌ Reservation cancelled.");
    }

    /** Mark selected reservation as NO_SHOW. */
    @FXML
    private void handleNoShow() {
        updateSelectedStatus("NO_SHOW", "🚫 Guest marked as no-show.");
    }

    /** Filter reservations by the selected date. */
    @FXML
    private void handleFilter() {
        LocalDate date = filterDatePicker.getValue();
        if (date == null) { setStatus("⚠ Select a filter date.", true); return; }

        try {
            reservations.setAll(reservationDAO.findByDate(date));
            setStatus("Showing " + reservations.size() + " reservation(s) for " + date, false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /** Show all upcoming reservations. */
    @FXML
    private void handleShowUpcoming() {
        try {
            reservations.setAll(reservationDAO.findUpcoming());
            setStatus("Showing " + reservations.size() + " upcoming reservation(s).", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /** Clear the booking form. */
    @FXML
    private void handleClear() {
        tableCombo.getSelectionModel().clearSelection();
        datePicker.setValue(LocalDate.now());
        startHourSpinner.getValueFactory().setValue(18);
        startMinSpinner.getValueFactory().setValue(0);
        endHourSpinner.getValueFactory().setValue(20);
        endMinSpinner.getValueFactory().setValue(0);
        guestNameField.clear();
        guestPhoneField.clear();
        guestCountSpinner.getValueFactory().setValue(2);
        requestsArea.clear();
        setStatus("", false);
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    private void updateSelectedStatus(String newStatus, String successMsg) {
        Reservation sel = reservationTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a reservation from the table.", true);
            return;
        }

        try {
            reservationDAO.updateStatus(sel.getReservationId(), newStatus);
            setStatus(successMsg, false);
            handleShowUpcoming();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    private void loadTables() {
        try {
            tableCombo.setItems(FXCollections.observableArrayList(tableDAO.findAllByBranch(1)));
        } catch (SQLException e) {
            setStatus("❌ Failed to load tables: " + e.getMessage(), true);
        }
    }

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }
}
