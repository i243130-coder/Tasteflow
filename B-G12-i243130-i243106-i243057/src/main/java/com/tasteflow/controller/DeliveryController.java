package com.tasteflow.controller;

import com.tasteflow.bridge.UniversalOrderBridge;
import com.tasteflow.dao.DeliveryDAO;
import com.tasteflow.dao.OrderDAO;
import com.tasteflow.model.DeliveryOrder;
import com.tasteflow.model.Order;
import com.tasteflow.model.OrderItem;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/**
 * Controller for delivery_tracker.fxml.
 * GRASP Controller — mediates between the Delivery UI and DAOs.
 *
 * Uses the GoF Adapter (UniversalOrderBridge) to simulate external
 * platform orders being ingested into our native delivery system.
 */
public class DeliveryController {

    private final DeliveryDAO deliveryDAO = new DeliveryDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @FXML private TableView<DeliveryOrder> deliveryTable;
    @FXML private TableColumn<DeliveryOrder, Number> colId;
    @FXML private TableColumn<DeliveryOrder, Number> colOrderId;
    @FXML private TableColumn<DeliveryOrder, String> colPlatform;
    @FXML private TableColumn<DeliveryOrder, String> colCustomer;
    @FXML private TableColumn<DeliveryOrder, String> colAddress;
    @FXML private TableColumn<DeliveryOrder, String> colPhone;
    @FXML private TableColumn<DeliveryOrder, String> colRider;
    @FXML private TableColumn<DeliveryOrder, String> colStatus;
    @FXML private TableColumn<DeliveryOrder, String> colFee;
    @FXML private TableColumn<DeliveryOrder, String> colTotal;

    @FXML private ComboBox<String> riderCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label statusLabel;

    private final ObservableList<DeliveryOrder> deliveries = FXCollections.observableArrayList();
    private List<String[]> riders = new ArrayList<>();

    @FXML
    public void initialize() {
        // Column bindings
        colId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getDeliveryId()));
        colOrderId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getOrderId()));
        colPlatform.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPlatformSource()));
        colCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCustomerName()));
        colAddress.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDeliveryAddress()));
        colPhone.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDeliveryPhone()));
        colRider.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getRiderName() != null ? cd.getValue().getRiderName() : "—"));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));
        colFee.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getDeliveryFee() != null ? "$" + cd.getValue().getDeliveryFee().toPlainString() : "$0"));
        colTotal.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getOrderTotal() != null ? "$" + cd.getValue().getOrderTotal().toPlainString() : "—"));

        // Color-code status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("DELIVERED".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if ("IN_TRANSIT".equals(item)) {
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                    } else if ("ASSIGNED".equals(item) || "PICKED_UP".equals(item)) {
                        setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
                    } else if ("FAILED".equals(item) || "RETURNED".equals(item)) {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
                    }
                }
            }
        });

        deliveryTable.setItems(deliveries);

        // Status combo
        statusCombo.setItems(FXCollections.observableArrayList(
            "ASSIGNED", "PICKED_UP", "IN_TRANSIT", "DELIVERED", "FAILED", "RETURNED"
        ));

        // Load riders
        loadRiders();

        // Load active deliveries
        handleShowActive();
    }

    // ====================================================================
    //  EVENT HANDLERS
    // ====================================================================

    @FXML
    private void handleShowActive() {
        try {
            deliveries.setAll(deliveryDAO.findActiveDeliveries());
            setStatus("Showing " + deliveries.size() + " active deliveries.", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleShowAll() {
        try {
            deliveries.setAll(deliveryDAO.findTodayDeliveries());
            setStatus("Showing " + deliveries.size() + " deliveries today.", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /**
     * Creates a brand-new delivery order.
     * Opens a multi-field dialog to collect customer details, then
     * automatically creates both the parent Order (DELIVERY type) and the
     * linked delivery_order record.  The delivery appears in the active
     * list with PENDING_ASSIGNMENT status, waiting for a rider to be assigned.
     */
    @FXML
    private void handleNewDelivery() {
        // ---- Build a custom dialog with multiple fields ----
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Delivery Order");
        dialog.setHeaderText("Enter delivery details");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField customerField = new TextField();
        customerField.setPromptText("Customer name");
        TextField addressField = new TextField();
        addressField.setPromptText("Full delivery address");
        TextField phoneField = new TextField();
        phoneField.setPromptText("03001234567");
        TextField feeField = new TextField("5.00");
        feeField.setPromptText("Delivery fee");
        TextField totalField = new TextField();
        totalField.setPromptText("Order total amount");

        grid.add(new Label("Customer:"), 0, 0);  grid.add(customerField, 1, 0);
        grid.add(new Label("Address:"),  0, 1);  grid.add(addressField,  1, 1);
        grid.add(new Label("Phone:"),    0, 2);  grid.add(phoneField,    1, 2);
        grid.add(new Label("Delivery Fee:"), 0, 3); grid.add(feeField, 1, 3);
        grid.add(new Label("Order Total:"), 0, 4); grid.add(totalField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Focus the customer name field
        javafx.application.Platform.runLater(customerField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) return;

        // ---- Validate inputs ----
        String customer = customerField.getText().trim();
        String address  = addressField.getText().trim();
        String phone    = phoneField.getText().trim();
        String feeText  = feeField.getText().trim();
        String totalText = totalField.getText().trim();

        if (address.isEmpty() || phone.isEmpty()) {
            setStatus("⚠ Address and Phone are required.", true);
            return;
        }

        BigDecimal fee;
        BigDecimal orderTotal;
        try {
            fee = feeText.isEmpty() ? new BigDecimal("5.00") : new BigDecimal(feeText);
            orderTotal = totalText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(totalText);
        } catch (NumberFormatException e) {
            setStatus("⚠ Invalid number for fee or total.", true);
            return;
        }

        try {
            // 1. Create a DELIVERY order in the `order` table
            Order order = new Order();
            order.setBranchId(1);
            order.setOrderType("DELIVERY");
            order.setStatus("PENDING");
            order.setSubtotal(orderTotal);
            order.setTax(BigDecimal.ZERO);
            order.setDiscount(BigDecimal.ZERO);
            order.setTotal(orderTotal);
            order.setSpecialInstructions("Delivery order for " +
                    (customer.isEmpty() ? "Walk-in" : customer));
            order.setItems(new ArrayList<>());

            int orderId = orderDAO.placeDeliveryOrder(order);

            // 2. Create the delivery record (PENDING_ASSIGNMENT)
            DeliveryOrder d = new DeliveryOrder();
            d.setOrderId(orderId);
            d.setDeliveryAddress(address);
            d.setDeliveryPhone(phone);
            d.setPlatformSource("TASTEFLOW");
            d.setDeliveryFee(fee);
            d.setCustomerName(customer.isEmpty() ? "Walk-in" : customer);
            d.setOrderTotal(orderTotal);

            int delivId = deliveryDAO.createDelivery(d);

            setStatus("✅ Delivery #" + delivId + " created (Order #" + orderId +
                       ") — Waiting for rider assignment.", false);
            handleShowActive();

        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    /**
     * Simulates an external platform order using the UniversalOrderBridge (GoF Adapter).
     * Creates a dummy order, then passes a simulated payload through the bridge.
     */
    @FXML
    private void handleSimulateExternal() {
        // Let user pick a platform
        ChoiceDialog<String> platformDialog = new ChoiceDialog<>("FOODPANDA",
            "FOODPANDA", "UBER_EATS", "TASTEFLOW");
        platformDialog.setTitle("Simulate External Order");
        platformDialog.setHeaderText("UniversalOrderBridge — GoF Adapter Pattern");
        platformDialog.setContentText("Platform:");
        Optional<String> platformResult = platformDialog.showAndWait();
        if (!platformResult.isPresent()) return;

        String platform = platformResult.get();

        // Build simulated JSON-like payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("platform", platform);

        if ("FOODPANDA".equals(platform)) {
            payload.put("customer_name", "FP Customer " + (int)(Math.random() * 1000));
            payload.put("customer_address", "Block A, DHA Phase 5, Lahore");
            payload.put("customer_phone", "0300" + (int)(Math.random() * 10000000));
            payload.put("total_price", "25.50");
            payload.put("delivery_charge", "3.99");
            payload.put("fp_order_id", "FP-" + System.currentTimeMillis());
        } else if ("UBER_EATS".equals(platform)) {
            payload.put("eater_name", "UE Eater " + (int)(Math.random() * 1000));
            payload.put("dropoff_address", "Model Town, Block C, Lahore");
            payload.put("contact_number", "0321" + (int)(Math.random() * 10000000));
            payload.put("order_total", "32.00");
            payload.put("uber_delivery_fee", "4.50");
            payload.put("uber_order_ref", "UE-" + System.currentTimeMillis());
        } else {
            payload.put("customer_name", "TF Customer " + (int)(Math.random() * 1000));
            payload.put("address", "Gulberg III, Lahore");
            payload.put("phone", "0333" + (int)(Math.random() * 10000000));
            payload.put("total", "18.75");
            payload.put("delivery_fee", "2.50");
            payload.put("notes", "Ring doorbell twice");
        }

        try {
            // 1. Use the ADAPTER to convert external payload → native DeliveryOrder
            DeliveryOrder adapted = UniversalOrderBridge.convertExternalOrder(payload);

            // 2. Create a stub order in our DB (DELIVERY type)
            Order order = new Order();
            order.setBranchId(1);
            order.setOrderType("DELIVERY");
            order.setStatus("PENDING");
            order.setSubtotal(adapted.getOrderTotal() != null ? adapted.getOrderTotal() : BigDecimal.ZERO);
            order.setTax(BigDecimal.ZERO);
            order.setDiscount(BigDecimal.ZERO);
            order.setTotal(adapted.getOrderTotal() != null ? adapted.getOrderTotal() : BigDecimal.ZERO);
            order.setSpecialInstructions("External: " + platform);
            order.setItems(new ArrayList<>());  // No items for external orders

            int orderId = orderDAO.placeDeliveryOrder(order);

            // 3. Create delivery record
            adapted.setOrderId(orderId);
            int delivId = deliveryDAO.createDelivery(adapted);

            setStatus("✅ External " + platform + " order adapted via UniversalOrderBridge → " +
                       "Delivery #" + delivId + " (Order #" + orderId + ") | " +
                       adapted.getCustomerName() + " | " + adapted.getDeliveryAddress(), false);
            handleShowActive();

        } catch (Exception e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleAssignRider() {
        DeliveryOrder sel = deliveryTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a delivery first.", true);
            return;
        }

        String riderSelection = riderCombo.getValue();
        if (riderSelection == null) {
            setStatus("⚠ Select a rider.", true);
            return;
        }

        // Parse rider ID from selection
        String[] parts = riderSelection.split(" — ");
        int riderId = Integer.parseInt(parts[0].replace("#", "").trim());
        String riderName = parts.length > 1 ? parts[1] : riderSelection;

        try {
            deliveryDAO.assignRider(sel.getDeliveryId(), riderId, riderName);
            setStatus("✅ Rider '" + riderName + "' assigned to Delivery #" + sel.getDeliveryId(), false);
            handleShowActive();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleUpdateStatus() {
        DeliveryOrder sel = deliveryTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a delivery first.", true);
            return;
        }

        String newStatus = statusCombo.getValue();
        if (newStatus == null) {
            setStatus("⚠ Select a status.", true);
            return;
        }

        try {
            deliveryDAO.updateStatus(sel.getDeliveryId(), newStatus,
                    "Status updated to " + newStatus + " by dispatcher");
            setStatus("✅ Delivery #" + sel.getDeliveryId() + " → " + newStatus, false);
            handleShowActive();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleViewHistory() {
        DeliveryOrder sel = deliveryTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select a delivery first.", true);
            return;
        }

        try {
            List<String> history = deliveryDAO.findStatusHistory(sel.getDeliveryId());

            StringBuilder sb = new StringBuilder();
            sb.append("Delivery #").append(sel.getDeliveryId())
              .append(" | Order #").append(sel.getOrderId())
              .append(" | ").append(sel.getPlatformSource()).append("\n");
            sb.append("Rider: ").append(sel.getRiderName() != null ? sel.getRiderName() : "Unassigned").append("\n");
            sb.append("Address: ").append(sel.getDeliveryAddress()).append("\n\n");
            sb.append("─── STATUS HISTORY ───\n");

            if (history.isEmpty()) {
                sb.append("No status changes recorded.\n");
            } else {
                for (String line : history) {
                    sb.append(line).append("\n");
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Delivery History");
            alert.setHeaderText("Tracking: Delivery #" + sel.getDeliveryId());
            TextArea ta = new TextArea(sb.toString());
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setPrefHeight(300);
            alert.getDialogPane().setContent(ta);
            alert.showAndWait();

        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    private void loadRiders() {
        try {
            riders = deliveryDAO.findAvailableRiders();
            ObservableList<String> items = FXCollections.observableArrayList();
            for (String[] r : riders) {
                items.add("#" + r[0] + " — " + r[1]);
            }
            riderCombo.setItems(items);
        } catch (SQLException e) {
            setStatus("❌ Failed to load riders.", true);
        }
    }

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }
}
