package com.tasteflow.controller;

import com.tasteflow.dao.ShiftDAO;
import com.tasteflow.model.StaffShift;
import com.tasteflow.util.AnimationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Controller for the Staff Schedule module.
 *
 * Displays a weekly view of shifts and provides a form to
 * assign a user to a time slot with overlap prevention.
 *
 * Pattern: GoF Mediator — coordinates between the form inputs,
 * the table view, and the ShiftDAO.
 */
public class ScheduleController {

    // ── FXML bindings ─────────────────────────────────────────
    @FXML private TableView<StaffShift> shiftTable;
    @FXML private TableColumn<StaffShift, String> colDay;
    @FXML private TableColumn<StaffShift, String> colDate;
    @FXML private TableColumn<StaffShift, String> colStaff;
    @FXML private TableColumn<StaffShift, String> colRole;
    @FXML private TableColumn<StaffShift, String> colTime;
    @FXML private TableColumn<StaffShift, String> colBranch;
    @FXML private TableColumn<StaffShift, String> colStatus;
    @FXML private TableColumn<StaffShift, Void>   colActions;

    @FXML private ComboBox<String> cmbStaff;
    @FXML private ComboBox<String> cmbBranch;
    @FXML private DatePicker dpShiftDate;
    @FXML private ComboBox<String> cmbStartTime;
    @FXML private ComboBox<String> cmbEndTime;
    @FXML private ComboBox<String> cmbRole;

    @FXML private Button btnPrevWeek;
    @FXML private Button btnNextWeek;
    @FXML private Button btnAssign;
    @FXML private Label lblWeekRange;
    @FXML private Label lblStatus;
    @FXML private Label lblFormStatus;

    // ── State ─────────────────────────────────────────────────
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private LocalDate currentWeekStart;
    private final ObservableList<StaffShift> shiftData = FXCollections.observableArrayList();

    // Staff/branch combo data: "id | displayName"
    private List<String[]> staffList;
    private List<String[]> branchList;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DAY_FMT  = DateTimeFormatter.ofPattern("EEE");

    // ──────────────────────────────────────────────────────────
    //  INITIALIZATION
    // ──────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        // Set current week to Monday of this week
        currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        setupTableColumns();
        setupFormInputs();
        loadWeekShifts();

        // Animations
        AnimationUtil.fadeInAndSlideUp(shiftTable, 0.1);
        AnimationUtil.applyHoverScale(btnAssign);
        AnimationUtil.applyClickPulse(btnAssign);
        AnimationUtil.applyHoverScale(btnPrevWeek);
        AnimationUtil.applyHoverScale(btnNextWeek);
        AnimationUtil.applyClickPulse(btnPrevWeek);
        AnimationUtil.applyClickPulse(btnNextWeek);
    }

    // ──────────────────────────────────────────────────────────
    //  TABLE SETUP
    // ──────────────────────────────────────────────────────────

    private void setupTableColumns() {
        colDay.setCellValueFactory(cd -> {
            StaffShift s = cd.getValue();
            if (s.getShiftDate() == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(
                s.getShiftDate().toLocalDate().format(DAY_FMT));
        });

        colDate.setCellValueFactory(cd -> {
            StaffShift s = cd.getValue();
            if (s.getShiftDate() == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(
                s.getShiftDate().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM")));
        });

        colStaff.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getUserName()));

        colRole.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getRoleOnShift()));

        colTime.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getTimeRange()));

        colBranch.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getBranchName()));

        colStatus.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getStatus()));

        // Status column with color coding
        colStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "CHECKED_IN"  -> setStyle("-fx-text-fill: #22C55E; -fx-font-weight: bold;");
                        case "CHECKED_OUT" -> setStyle("-fx-text-fill: #3B82F6;");
                        case "ABSENT"      -> setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                        default            -> setStyle("-fx-text-fill: #A0A0A0;");
                    }
                }
            }
        });

        // Role column with color
        colRole.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(role);
                    switch (role) {
                        case "MANAGER" -> setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: bold;");
                        case "CHEF"    -> setStyle("-fx-text-fill: #EF4444;");
                        case "WAITER"  -> setStyle("-fx-text-fill: #22C55E;");
                        case "CASHIER" -> setStyle("-fx-text-fill: #3B82F6;");
                        default        -> setStyle("-fx-text-fill: #A0A0A0;");
                    }
                }
            }
        });

        // Actions column with delete button
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button btnDelete = new Button("✕");
            {
                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setStyle("-fx-padding: 4 10; -fx-font-size: 11;");
                AnimationUtil.applyHoverScale(btnDelete);
                btnDelete.setOnAction(e -> {
                    StaffShift shift = getTableView().getItems().get(getIndex());
                    deleteShift(shift);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });

        shiftTable.setItems(shiftData);
    }

    // ──────────────────────────────────────────────────────────
    //  FORM SETUP
    // ──────────────────────────────────────────────────────────

    private void setupFormInputs() {
        // Populate time combo boxes (30-min intervals)
        ObservableList<String> timeSlots = FXCollections.observableArrayList();
        for (int h = 6; h <= 23; h++) {
            timeSlots.add(String.format("%02d:00", h));
            timeSlots.add(String.format("%02d:30", h));
        }
        cmbStartTime.setItems(timeSlots);
        cmbEndTime.setItems(FXCollections.observableArrayList(timeSlots));

        // Roles
        cmbRole.setItems(FXCollections.observableArrayList(
            "CHEF", "WAITER", "CASHIER", "MANAGER", "DELIVERY_DRIVER"
        ));

        // Default date
        dpShiftDate.setValue(LocalDate.now());

        // Load staff and branches from DB
        try {
            staffList = shiftDAO.findActiveStaff();
            ObservableList<String> staffNames = FXCollections.observableArrayList();
            for (String[] s : staffList) {
                staffNames.add(s[1] + "  (" + s[2] + ")");
            }
            cmbStaff.setItems(staffNames);

            branchList = shiftDAO.findActiveBranches();
            ObservableList<String> branchNames = FXCollections.observableArrayList();
            for (String[] b : branchList) {
                branchNames.add(b[1]);
            }
            cmbBranch.setItems(branchNames);

        } catch (SQLException e) {
            showFormError("❌ Failed to load staff/branches: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  WEEK NAVIGATION
    // ──────────────────────────────────────────────────────────

    @FXML
    public void handlePrevWeek() {
        currentWeekStart = currentWeekStart.minusWeeks(1);
        loadWeekShifts();
        AnimationUtil.slideInFromLeft(shiftTable, 0);
    }

    @FXML
    public void handleNextWeek() {
        currentWeekStart = currentWeekStart.plusWeeks(1);
        loadWeekShifts();
        AnimationUtil.slideInFromLeft(shiftTable, 0);
    }

    // ──────────────────────────────────────────────────────────
    //  LOAD SHIFTS
    // ──────────────────────────────────────────────────────────

    private void loadWeekShifts() {
        LocalDate weekEnd = currentWeekStart.plusDays(6);
        lblWeekRange.setText(
            currentWeekStart.format(DATE_FMT) + "  →  " + weekEnd.format(DATE_FMT));

        try {
            List<StaffShift> shifts = shiftDAO.findWeekShifts(
                Date.valueOf(currentWeekStart));
            shiftData.setAll(shifts);
            lblStatus.setText(shifts.size() + " shift(s) this week");
        } catch (SQLException e) {
            lblStatus.setText("❌ " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  ASSIGN SHIFT
    // ──────────────────────────────────────────────────────────

    @FXML
    public void handleAssignShift() {
        // Validate inputs
        if (cmbStaff.getSelectionModel().getSelectedIndex() < 0) {
            showFormError("Please select a staff member.");
            return;
        }
        if (cmbBranch.getSelectionModel().getSelectedIndex() < 0) {
            showFormError("Please select a branch.");
            return;
        }
        if (dpShiftDate.getValue() == null) {
            showFormError("Please select a date.");
            return;
        }
        if (cmbStartTime.getValue() == null || cmbEndTime.getValue() == null) {
            showFormError("Please select start and end times.");
            return;
        }
        if (cmbRole.getValue() == null) {
            showFormError("Please select a role.");
            return;
        }

        // Parse selections
        int staffIdx  = cmbStaff.getSelectionModel().getSelectedIndex();
        int branchIdx = cmbBranch.getSelectionModel().getSelectedIndex();

        int userId   = Integer.parseInt(staffList.get(staffIdx)[0]);
        int branchId = Integer.parseInt(branchList.get(branchIdx)[0]);
        String branchName = branchList.get(branchIdx)[1];

        Time startTime = Time.valueOf(cmbStartTime.getValue() + ":00");
        Time endTime   = Time.valueOf(cmbEndTime.getValue() + ":00");

        if (!endTime.after(startTime)) {
            showFormError("End time must be after start time.");
            return;
        }

        // Build shift
        StaffShift shift = new StaffShift();
        shift.setUserId(userId);
        shift.setBranchId(branchId);
        shift.setBranchName(branchName);
        shift.setShiftDate(Date.valueOf(dpShiftDate.getValue()));
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setRoleOnShift(cmbRole.getValue());
        shift.setStatus("SCHEDULED");

        try {
            shiftDAO.insertShift(shift);
            showFormSuccess("✅ Shift assigned successfully! (ID: " + shift.getShiftId() + ")");
            AnimationUtil.celebrationPulse(btnAssign);
            loadWeekShifts();
            clearForm();
        } catch (SQLException e) {
            showFormError(e.getMessage());
            AnimationUtil.shake(btnAssign);
        }
    }

    // ──────────────────────────────────────────────────────────
    //  DELETE SHIFT
    // ──────────────────────────────────────────────────────────

    private void deleteShift(StaffShift shift) {
        try {
            shiftDAO.deleteShift(shift.getShiftId());
            loadWeekShifts();
            showFormSuccess("🗑 Shift deleted.");
        } catch (SQLException e) {
            showFormError("❌ Delete failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────────────────

    private void clearForm() {
        cmbStaff.getSelectionModel().clearSelection();
        cmbBranch.getSelectionModel().clearSelection();
        cmbStartTime.getSelectionModel().clearSelection();
        cmbEndTime.getSelectionModel().clearSelection();
        cmbRole.getSelectionModel().clearSelection();
        dpShiftDate.setValue(LocalDate.now());
    }

    private void showFormError(String msg) {
        lblFormStatus.setStyle("-fx-text-fill: #EF4444;");
        AnimationUtil.flashMessage(lblFormStatus, msg, 5);
        AnimationUtil.shake(lblFormStatus);
    }

    private void showFormSuccess(String msg) {
        lblFormStatus.setStyle("-fx-text-fill: #22C55E;");
        AnimationUtil.flashMessage(lblFormStatus, msg, 4);
    }
}
