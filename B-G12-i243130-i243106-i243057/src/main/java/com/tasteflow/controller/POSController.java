package com.tasteflow.controller;

import com.tasteflow.dao.*;
import com.tasteflow.model.DiningTable;
import com.tasteflow.model.MenuCategory;
import com.tasteflow.model.MenuItem;
import com.tasteflow.model.Order;
import com.tasteflow.model.OrderItem;
import com.tasteflow.model.Reservation;
import com.tasteflow.model.Customer;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * GRASP Facade Controller for the POS (Point of Sale) screen.
 *
 * Responsibilities:
 *  - Displays table grid and menu item grid
 *  - Manages the order cart (add/remove/qty)
 *  - Real-time stock checking as items are added
 *  - Delegates atomic order placement to OrderDAO
 */
public class POSController {

    // ---- DAOs (Information Expert) ----
    private final OrderDAO orderDAO = new OrderDAO();
    private final MenuDAO menuDAO = new MenuDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final DiningTableDAO tableDAO = new DiningTableDAO();
    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final LoyaltyDAO loyaltyDAO = new LoyaltyDAO();

    // ---- FXML Controls ----
    @FXML private FlowPane tableGrid;
    @FXML private FlowPane menuGrid;
    @FXML private ComboBox<MenuCategory> categoryFilter;

    @FXML private Label orderTitleLabel;
    @FXML private Label tableInfoLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<OrderItem> cartTable;
    @FXML private TableColumn<OrderItem, String> cartItemCol;
    @FXML private TableColumn<OrderItem, Number> cartQtyCol;
    @FXML private TableColumn<OrderItem, String> cartPriceCol;
    @FXML private TableColumn<OrderItem, String> cartSubCol;

    @FXML private Spinner<Integer> qtySpinner;

    // Loyalty controls
    @FXML private TextField customerPhoneField;
    @FXML private Label customerInfoLabel;
    @FXML private Label discountLabel;
    @FXML private Spinner<Integer> redeemSpinner;

    @FXML private TableView<Order> ordersTable;
    @FXML private TableColumn<Order, Number> ordIdCol;
    @FXML private TableColumn<Order, String> ordTableCol;
    @FXML private TableColumn<Order, String> ordTotalCol;
    @FXML private TableColumn<Order, String> ordStatusCol;
    @FXML private TableColumn<Order, String> ordTimeCol;

    // ---- State ----
    private final ObservableList<OrderItem> cartItems = FXCollections.observableArrayList();
    private final ObservableList<Order> todayOrders = FXCollections.observableArrayList();
    private DiningTable selectedTable = null;
    private List<MenuItem> allMenuItems = new ArrayList<>();
    private Customer linkedCustomer = null;
    private int pointsToRedeem = 0;
    private BigDecimal loyaltyDiscount = BigDecimal.ZERO;

    // ====================================================================
    //  INITIALIZE
    // ====================================================================

    @FXML
    public void initialize() {
        // Qty spinner
        qtySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 1));

        // Redeem spinner (0 = no redemption)
        redeemSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));

        // Cart columns
        cartItemCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getItemName()));
        cartQtyCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getQuantity()));
        cartPriceCol.setCellValueFactory(cd -> new SimpleStringProperty(
                "$" + cd.getValue().getUnitPrice().toPlainString()));
        cartSubCol.setCellValueFactory(cd -> new SimpleStringProperty(
                "$" + cd.getValue().getSubtotal().toPlainString()));
        cartTable.setItems(cartItems);

        // Orders columns
        ordIdCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getOrderId()));
        ordTableCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getTableNumber() > 0 ? "T" + cd.getValue().getTableNumber() : "-"));
        ordTotalCol.setCellValueFactory(cd -> new SimpleStringProperty(
                "$" + cd.getValue().getTotal().toPlainString()));
        ordStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));
        ordTimeCol.setCellValueFactory(cd -> {
            String time = cd.getValue().getCreatedAt() != null
                    ? cd.getValue().getCreatedAt().toString().substring(11, 16) : "";
            return new SimpleStringProperty(time);
        });

        // Color the status column
        ordStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    if ("PENDING".equals(item)) {
                        setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
                    } else if ("IN_PROGRESS".equals(item)) {
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                    } else if ("COMPLETED".equals(item) || "SERVED".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if ("CANCELLED".equals(item)) {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        ordersTable.setItems(todayOrders);

        // Load data
        loadTableGrid();
        loadCategoryFilter();
        loadMenuItems(null);
        refreshOrders();
    }

    // ====================================================================
    //  TABLE GRID
    // ====================================================================

    private void loadTableGrid() {
        tableGrid.getChildren().clear();
        try {
            List<DiningTable> tables = tableDAO.findAllByBranch(1);
            for (DiningTable t : tables) {
                Button btn = new Button("T" + t.getTableNumber() + "\n(" + t.getCapacity() + " seats)");
                btn.setPrefSize(90, 55);
                btn.setPadding(new Insets(4));
                btn.setWrapText(true);

                // Color by status
                String bgColor;
                if ("OCCUPIED".equals(t.getStatus())) {
                    bgColor = "#e74c3c";
                } else if ("RESERVED".equals(t.getStatus())) {
                    bgColor = "#f39c12";
                } else if ("OUT_OF_SERVICE".equals(t.getStatus())) {
                    bgColor = "#95a5a6";
                } else {
                    bgColor = "#27ae60";
                }

                btn.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: white; " +
                             "-fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

                btn.setOnAction(e -> selectTable(t));
                tableGrid.getChildren().add(btn);
            }
        } catch (SQLException e) {
            setStatus("❌ Failed to load tables: " + e.getMessage(), true);
        }
    }

    private void selectTable(DiningTable table) {
        selectedTable = table;
        tableInfoLabel.setText("Table " + table.getTableNumber() +
                " (" + table.getCapacity() + " seats) — " + table.getStatus());
        orderTitleLabel.setText("Order for Table " + table.getTableNumber());
        setStatus("Table " + table.getTableNumber() + " selected.", false);
    }

    // ====================================================================
    //  MENU GRID
    // ====================================================================

    private void loadCategoryFilter() {
        try {
            List<MenuCategory> cats = categoryDAO.findAllActive();
            MenuCategory allCat = new MenuCategory(0, "All Categories");
            ObservableList<MenuCategory> list = FXCollections.observableArrayList();
            list.add(allCat);
            list.addAll(cats);
            categoryFilter.setItems(list);
            categoryFilter.getSelectionModel().selectFirst();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleCategoryFilter() {
        MenuCategory sel = categoryFilter.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getCategoryId() > 0) {
            loadMenuItems(sel.getCategoryId());
        } else {
            loadMenuItems(null);
        }
    }

    private void loadMenuItems(Integer categoryId) {
        menuGrid.getChildren().clear();
        try {
            allMenuItems = menuDAO.findAllAvailable();

            for (MenuItem mi : allMenuItems) {
                if (categoryId != null && mi.getCategoryId() != categoryId) continue;

                // Check stock in real-time
                String stockIssue = orderDAO.checkStockForItem(mi.getItemId(), 1);
                boolean outOfStock = (stockIssue != null);

                Button btn = new Button(mi.getItemName() + "\n$" + mi.getPrice().toPlainString());
                btn.setPrefSize(120, 60);
                btn.setPadding(new Insets(6));
                btn.setWrapText(true);

                if (outOfStock) {
                    btn.setStyle("-fx-background-color: #bdc3c7; -fx-text-fill: #7f8c8d; " +
                                 "-fx-font-size: 11; -fx-background-radius: 8;");
                    btn.setDisable(true);
                    btn.setText(mi.getItemName() + "\n⛔ OUT OF STOCK");
                } else {
                    btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                                 "-fx-font-size: 11; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
                    btn.setOnAction(e -> addToCart(mi));
                }

                menuGrid.getChildren().add(btn);
            }
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  CART MANAGEMENT
    // ====================================================================

    private void addToCart(MenuItem mi) {
        // Check if already in cart — increase qty
        for (OrderItem oi : cartItems) {
            if (oi.getItemId() == mi.getItemId()) {
                oi.setQuantity(oi.getQuantity() + 1);
                oi.setSubtotal(oi.getUnitPrice().multiply(BigDecimal.valueOf(oi.getQuantity())));
                cartTable.refresh();
                updateTotals();
                return;
            }
        }

        // New cart item
        OrderItem oi = new OrderItem(mi.getItemId(), mi.getItemName(), 1, mi.getPrice());
        cartItems.add(oi);
        updateTotals();
    }

    @FXML
    private void handleIncQty() {
        OrderItem sel = cartTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        sel.setQuantity(sel.getQuantity() + qtySpinner.getValue());
        sel.setSubtotal(sel.getUnitPrice().multiply(BigDecimal.valueOf(sel.getQuantity())));
        cartTable.refresh();
        updateTotals();
    }

    @FXML
    private void handleDecQty() {
        OrderItem sel = cartTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        int newQty = sel.getQuantity() - qtySpinner.getValue();
        if (newQty <= 0) {
            cartItems.remove(sel);
        } else {
            sel.setQuantity(newQty);
            sel.setSubtotal(sel.getUnitPrice().multiply(BigDecimal.valueOf(sel.getQuantity())));
            cartTable.refresh();
        }
        updateTotals();
    }

    @FXML
    private void handleRemoveItem() {
        OrderItem sel = cartTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            cartItems.remove(sel);
            updateTotals();
        }
    }

    @FXML
    private void handleClearCart() {
        cartItems.clear();
        selectedTable = null;
        linkedCustomer = null;
        pointsToRedeem = 0;
        loyaltyDiscount = BigDecimal.ZERO;
        tableInfoLabel.setText("No table selected");
        orderTitleLabel.setText("New Order");
        customerInfoLabel.setText("No customer linked");
        customerPhoneField.clear();
        redeemSpinner.getValueFactory().setValue(0);
        discountLabel.setText("0.00");
        updateTotals();
        setStatus("", false);
    }

    private void updateTotals() {
        BigDecimal sub = BigDecimal.ZERO;
        for (OrderItem oi : cartItems) {
            sub = sub.add(oi.getSubtotal());
        }
        BigDecimal tax = sub.multiply(new BigDecimal("0.10"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal total = sub.add(tax).subtract(loyaltyDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        subtotalLabel.setText("$" + sub.toPlainString());
        taxLabel.setText("$" + tax.toPlainString());
        discountLabel.setText("-$" + loyaltyDiscount.toPlainString());
        totalLabel.setText("$" + total.toPlainString());
    }

    // ====================================================================
    //  PLACE ORDER
    // ====================================================================

    @FXML
    private void handlePlaceOrder() {
        if (selectedTable == null) {
            setStatus("⚠ Select a table first.", true);
            return;
        }
        if (cartItems.isEmpty()) {
            setStatus("⚠ Add items to the cart.", true);
            return;
        }

        // Build the Order
        Order order = new Order();
        order.setTableId(selectedTable.getTableId());
        order.setBranchId(1);
        order.setOrderType("DINE_IN");
        order.setStatus("PENDING");
        order.setDiscount(loyaltyDiscount);
        order.setLoyaltyPointsRedeemed(pointsToRedeem);
        if (linkedCustomer != null) {
            order.setCustomerId(linkedCustomer.getCustomerId());
        }
        order.setItems(new ArrayList<>(cartItems));
        order.recalculate();
        // Apply loyalty discount on top of recalculated total
        if (loyaltyDiscount.compareTo(BigDecimal.ZERO) > 0) {
            order.setDiscount(loyaltyDiscount);
            order.setTotal(order.getTotal().subtract(loyaltyDiscount));
            if (order.getTotal().compareTo(BigDecimal.ZERO) < 0) {
                order.setTotal(BigDecimal.ZERO);
            }
        }

        try {
            int orderId = orderDAO.placeOrder(order);

            // Redeem points if applied
            String loyaltyMsg = "";
            if (linkedCustomer != null && pointsToRedeem > 0) {
                loyaltyDAO.redeemPoints(linkedCustomer.getCustomerId(), pointsToRedeem, orderId);
                loyaltyMsg += " | " + pointsToRedeem + " pts redeemed (-$" + loyaltyDiscount.toPlainString() + ")";
            }

            // Earn points (1 pt per $1 of subtotal)
            if (linkedCustomer != null) {
                int earned = loyaltyDAO.earnPoints(linkedCustomer.getCustomerId(), orderId, order.getSubtotal());
                if (earned > 0) {
                    loyaltyMsg += " | +" + earned + " pts earned";
                }
            }

            setStatus("✅ Order #" + orderId + " placed! Total: $" + order.getTotal().toPlainString() +
                       loyaltyMsg, false);
            handleClearCart();
            loadTableGrid();
            loadMenuItems(null);
            refreshOrders();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  LOYALTY HANDLERS
    // ====================================================================

    @FXML
    private void handleLookupCustomer() {
        String phone = customerPhoneField.getText().trim();
        if (phone.isEmpty()) {
            setStatus("⚠ Enter a phone number.", true);
            return;
        }

        try {
            Customer c = loyaltyDAO.findByPhone(phone);
            if (c == null) {
                customerInfoLabel.setText("❌ No customer found for " + phone);
                linkedCustomer = null;
                redeemSpinner.getValueFactory().setValue(0);
                setStatus("Customer not found. Click 'Register' to create.", true);
            } else {
                linkedCustomer = c;
                customerInfoLabel.setText("✅ " + c.getFullName() + " | " +
                        c.getLoyaltyPoints() + " pts | " + c.getLoyaltyTier());
                // Set max redeem to customer's points
                redeemSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, c.getLoyaltyPoints(), 0));
                setStatus("Customer linked: " + c.getFullName(), false);
            }
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleRegisterCustomer() {
        String phone = customerPhoneField.getText().trim();
        if (phone.isEmpty()) {
            setStatus("⚠ Enter a phone number first.", true);
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Register Customer");
        nameDialog.setHeaderText("New Loyalty Member");
        nameDialog.setContentText("Full Name:");

        java.util.Optional<String> result = nameDialog.showAndWait();
        if (!result.isPresent() || result.get().trim().isEmpty()) return;

        String fullName = result.get().trim();

        try {
            int id = loyaltyDAO.registerCustomer(fullName, phone, null);
            Customer c = loyaltyDAO.findById(id);
            linkedCustomer = c;
            customerInfoLabel.setText("✅ " + c.getFullName() + " | " +
                    c.getLoyaltyPoints() + " pts | " + c.getLoyaltyTier());
            redeemSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 0, 0));
            setStatus("✅ Registered: " + fullName + " (" + phone + ") — 0 pts (BRONZE)", false);
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleApplyPoints() {
        if (linkedCustomer == null) {
            setStatus("⚠ Lookup a customer first.", true);
            return;
        }
        if (cartItems.isEmpty()) {
            setStatus("⚠ Add items to cart first.", true);
            return;
        }

        pointsToRedeem = redeemSpinner.getValue();
        if (pointsToRedeem <= 0) {
            loyaltyDiscount = BigDecimal.ZERO;
            pointsToRedeem = 0;
            updateTotals();
            setStatus("No points applied.", false);
            return;
        }

        if (pointsToRedeem > linkedCustomer.getLoyaltyPoints()) {
            setStatus("⚠ Customer only has " + linkedCustomer.getLoyaltyPoints() + " points.", true);
            return;
        }

        // Calculate discount: 1 point = $0.10
        loyaltyDiscount = Customer.POINT_VALUE.multiply(BigDecimal.valueOf(pointsToRedeem))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        updateTotals();
        setStatus("💎 " + pointsToRedeem + " points applied = -$" + loyaltyDiscount.toPlainString() +
                  " discount", false);
    }

    // ------------------------------------------------ PRE-ORDER

    /**
     * Places a HELD pre-order linked to a confirmed reservation.
     * Items are saved but NOT sent to KDS and stock is NOT deducted
     * until the manager clicks "Seat Customer" in the Reservations module.
     */
    @FXML
    private void handlePreOrder() {
        if (cartItems.isEmpty()) {
            setStatus("⚠ Add items to the cart first.", true);
            return;
        }

        // Load confirmed reservations for today
        List<Reservation> confirmed;
        try {
            confirmed = reservationDAO.findUpcoming();
            // Filter to only CONFIRMED
            confirmed.removeIf(r -> !"CONFIRMED".equals(r.getStatus()));
        } catch (SQLException e) {
            setStatus("❌ Failed to load reservations: " + e.getMessage(), true);
            return;
        }

        if (confirmed.isEmpty()) {
            setStatus("⚠ No confirmed reservations found. Book a reservation first.", true);
            return;
        }

        // Build display strings for choice dialog
        List<String> choices = new ArrayList<>();
        for (Reservation r : confirmed) {
            choices.add("#" + r.getReservationId() + " — " + r.getGuestName() +
                        " (T" + r.getTableNumber() + ", " + r.getReservationDate() +
                        " " + r.getStartTime() + ")");
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Pre-Order");
        dialog.setHeaderText("Select a reservation to attach this pre-order to:");
        dialog.setContentText("Reservation:");

        java.util.Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return;

        // Find the selected reservation
        int selectedIndex = choices.indexOf(result.get());
        Reservation selectedRes = confirmed.get(selectedIndex);

        // Build the held order
        Order order = new Order();
        order.setReservationId(selectedRes.getReservationId());
        order.setTableId(selectedRes.getTableId());
        order.setBranchId(1);
        order.setItems(new ArrayList<>(cartItems));
        order.recalculate();

        try {
            int orderId = orderDAO.placeHeldOrder(order);
            setStatus("📋 Pre-Order #" + orderId + " saved for " + selectedRes.getGuestName() +
                       " (Reservation #" + selectedRes.getReservationId() + "). " +
                       "Will fire to kitchen when guest is seated.", false);
            handleClearCart();
            refreshOrders();
        } catch (SQLException e) {
            setStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  RECENT ORDERS
    // ====================================================================

    private void refreshOrders() {
        try {
            todayOrders.setAll(orderDAO.findTodayOrders());
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
