package com.tasteflow.controller;

import com.tasteflow.dao.LoyaltyDAO;
import com.tasteflow.model.Customer;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for customer_management.fxml.
 * Displays all customers with loyalty data, supports search,
 * registration, manual point awards, and transaction history.
 */
public class CustomerController {

    private final LoyaltyDAO loyaltyDAO = new LoyaltyDAO();

    @FXML private TextField searchField;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Number> colId;
    @FXML private TableColumn<Customer, String> colName;
    @FXML private TableColumn<Customer, String> colPhone;
    @FXML private TableColumn<Customer, String> colEmail;
    @FXML private TableColumn<Customer, Number> colPoints;
    @FXML private TableColumn<Customer, String> colTier;
    @FXML private TableColumn<Customer, String> colDate;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;

    private final ObservableList<Customer> customers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Column bindings
        colId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getCustomerId()));
        colName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFullName()));
        colPhone.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPhone()));
        colEmail.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getEmail() != null ? cd.getValue().getEmail() : "—"));
        colPoints.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getLoyaltyPoints()));
        colTier.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getLoyaltyTier()));
        colDate.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getCreatedAt() != null ? cd.getValue().getCreatedAt().toString().substring(0, 10) : "—"));

        // Color-code the tier column
        colTier.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("PLATINUM".equals(item)) {
                        setStyle("-fx-text-fill: #9b59b6; -fx-font-weight: bold;");
                    } else if ("GOLD".equals(item)) {
                        setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                    } else if ("SILVER".equals(item)) {
                        setStyle("-fx-text-fill: #95a5a6; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #cd7f32; -fx-font-weight: bold;");
                    }
                }
            }
        });

        customerTable.setItems(customers);

        // Load all customers
        handleShowAll();
    }

    // ====================================================================
    //  EVENT HANDLERS
    // ====================================================================

    @FXML
    private void handleShowAll() {
        try {
            customers.setAll(loyaltyDAO.findAll());
            summaryLabel.setText(customers.size() + " customers");
            setStatus("Showing all customers.", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            handleShowAll();
            return;
        }

        try {
            customers.setAll(loyaltyDAO.searchCustomers(query));
            summaryLabel.setText(customers.size() + " result(s) for \"" + query + "\"");
            setStatus("Search complete.", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleRegister() {
        // Name dialog
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Register Customer");
        nameDialog.setHeaderText("New Loyalty Member");
        nameDialog.setContentText("Full Name:");
        Optional<String> nameResult = nameDialog.showAndWait();
        if (!nameResult.isPresent() || nameResult.get().trim().isEmpty()) return;
        String fullName = nameResult.get().trim();

        // Phone dialog
        TextInputDialog phoneDialog = new TextInputDialog();
        phoneDialog.setTitle("Register Customer");
        phoneDialog.setHeaderText("Phone Number");
        phoneDialog.setContentText("Phone:");
        Optional<String> phoneResult = phoneDialog.showAndWait();
        if (!phoneResult.isPresent() || phoneResult.get().trim().isEmpty()) return;
        String phone = phoneResult.get().trim();

        // Email dialog (optional)
        TextInputDialog emailDialog = new TextInputDialog();
        emailDialog.setTitle("Register Customer");
        emailDialog.setHeaderText("Email (optional)");
        emailDialog.setContentText("Email:");
        Optional<String> emailResult = emailDialog.showAndWait();
        String email = emailResult.isPresent() && !emailResult.get().trim().isEmpty()
                ? emailResult.get().trim() : null;

        try {
            int id = loyaltyDAO.registerCustomer(fullName, phone, email);
            setStatus("✅ Registered: " + fullName + " (" + phone + ") — ID #" + id, false);
            handleShowAll();
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate")) {
                setStatus("❌ Phone number already registered.", true);
            } else {
                setStatus("❌ " + e.getMessage(), true);
            }
        }
    }

    @FXML
    private void handleAwardPoints() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a customer first.", true);
            return;
        }

        TextInputDialog ptsDialog = new TextInputDialog("100");
        ptsDialog.setTitle("Award Points");
        ptsDialog.setHeaderText("Award points to " + sel.getFullName());
        ptsDialog.setContentText("Points (negative to deduct):");
        Optional<String> result = ptsDialog.showAndWait();
        if (!result.isPresent()) return;

        int points;
        try {
            points = Integer.parseInt(result.get().trim());
        } catch (NumberFormatException e) {
            setStatus("⚠ Enter a valid number.", true);
            return;
        }

        TextInputDialog reasonDialog = new TextInputDialog("Manual adjustment");
        reasonDialog.setTitle("Reason");
        reasonDialog.setHeaderText("Why?");
        reasonDialog.setContentText("Reason:");
        Optional<String> reasonResult = reasonDialog.showAndWait();
        String reason = reasonResult.isPresent() ? reasonResult.get().trim() : "Manual adjustment";

        try {
            loyaltyDAO.awardManualPoints(sel.getCustomerId(), points, reason);
            setStatus("✅ " + (points >= 0 ? "+" : "") + points + " pts awarded to " + sel.getFullName(), false);
            handleShowAll();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleViewHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a customer first.", true);
            return;
        }

        try {
            List<String> history = loyaltyDAO.findTransactionHistory(sel.getCustomerId());

            if (history.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "No loyalty transactions found for " + sel.getFullName() + ".",
                        ButtonType.OK);
                alert.setHeaderText("Transaction History");
                alert.showAndWait();
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (String line : history) {
                sb.append(line).append("\n");
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Loyalty History");
            alert.setHeaderText(sel.getFullName() + " — " + sel.getLoyaltyPoints() +
                                " pts (" + sel.getLoyaltyTier() + ")");

            TextArea textArea = new TextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(300);
            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();

        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }
}
