package com.tasteflow.controller;

import com.tasteflow.dao.InventoryAlertDAO;
import com.tasteflow.model.Ingredient;
import com.tasteflow.model.InventoryAlert;
import com.tasteflow.util.AnimationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for the Inventory Dashboard module.
 *
 * Displays low-stock alerts (critical in red, warnings in amber)
 * and a full ingredient inventory table for the General Manager.
 *
 * Pattern: GoF Observer — the table views observe data changes
 * via ObservableList; the controller mediates between DAO and UI.
 */
public class InventoryDashboardController {

    // ── FXML bindings ─────────────────────────────────────────
    @FXML private TableView<InventoryAlert> alertsTable;
    @FXML private TableColumn<InventoryAlert, String> colAlertSeverity;
    @FXML private TableColumn<InventoryAlert, String> colAlertIngredient;
    @FXML private TableColumn<InventoryAlert, String> colAlertStock;
    @FXML private TableColumn<InventoryAlert, String> colAlertReorder;
    @FXML private TableColumn<InventoryAlert, String> colAlertDeficit;
    @FXML private TableColumn<InventoryAlert, String> colAlertUnit;

    @FXML private TableView<Ingredient> inventoryTable;
    @FXML private TableColumn<Ingredient, String> colInvName;
    @FXML private TableColumn<Ingredient, String> colInvStock;
    @FXML private TableColumn<Ingredient, String> colInvReorder;
    @FXML private TableColumn<Ingredient, String> colInvUnit;
    @FXML private TableColumn<Ingredient, String> colInvCost;
    @FXML private TableColumn<Ingredient, String> colInvStatus;

    @FXML private Label lblCriticalCount;
    @FXML private Label lblWarningCount;
    @FXML private Label lblTotalIngredients;
    @FXML private Label lblAlertCount;
    @FXML private Label lblInventoryCount;
    @FXML private Label lblLastRefresh;
    @FXML private Button btnRefresh;

    // ── State ─────────────────────────────────────────────────
    private final InventoryAlertDAO alertDAO = new InventoryAlertDAO();
    private final ObservableList<InventoryAlert> alertData = FXCollections.observableArrayList();
    private final ObservableList<Ingredient> inventoryData = FXCollections.observableArrayList();

    // ──────────────────────────────────────────────────────────
    //  INITIALIZATION
    // ──────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupAlertsTable();
        setupInventoryTable();
        loadData();

        // Animations
        AnimationUtil.fadeInAndSlideUp(alertsTable, 0.1);
        AnimationUtil.fadeInAndSlideUp(inventoryTable, 0.2);
        AnimationUtil.applyHoverScale(btnRefresh);
        AnimationUtil.applyClickPulse(btnRefresh);

        // Animate stat cards
        if (lblCriticalCount != null) AnimationUtil.bounceIn(lblCriticalCount.getParent(), 0.15);
        if (lblWarningCount != null) AnimationUtil.bounceIn(lblWarningCount.getParent(), 0.25);
        if (lblTotalIngredients != null) AnimationUtil.bounceIn(lblTotalIngredients.getParent(), 0.35);
    }

    // ──────────────────────────────────────────────────────────
    //  ALERTS TABLE
    // ──────────────────────────────────────────────────────────

    private void setupAlertsTable() {
        colAlertSeverity.setCellValueFactory(cd -> {
            InventoryAlert a = cd.getValue();
            return new SimpleStringProperty(a.isCritical() ? "🔴" : "🟡");
        });

        colAlertIngredient.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getIngredientName()));

        colAlertStock.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getCurrentStock().toPlainString()));

        colAlertReorder.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getReorderLevel().toPlainString()));

        colAlertDeficit.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getDeficit().toPlainString()));

        colAlertUnit.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getUnit()));

        // RED highlight for critical rows
        alertsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(InventoryAlert alert, boolean empty) {
                super.updateItem(alert, empty);
                if (empty || alert == null) {
                    setStyle("");
                } else if (alert.isCritical()) {
                    setStyle("-fx-background-color: rgba(239,68,68,0.15); " +
                             "-fx-border-color: transparent transparent rgba(239,68,68,0.30) transparent;");
                } else {
                    setStyle("-fx-background-color: rgba(245,158,11,0.10); " +
                             "-fx-border-color: transparent transparent rgba(245,158,11,0.20) transparent;");
                }
            }
        });

        // Color coding for stock column
        colAlertStock.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(stock);
                    try {
                        BigDecimal val = new BigDecimal(stock);
                        if (val.compareTo(BigDecimal.ZERO) <= 0) {
                            setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: bold;");
                        }
                    } catch (NumberFormatException e) {
                        setStyle("");
                    }
                }
            }
        });

        // Color coding for deficit column
        colAlertDeficit.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String deficit, boolean empty) {
                super.updateItem(deficit, empty);
                if (empty || deficit == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("−" + deficit);
                    setStyle("-fx-text-fill: #EF4444;");
                }
            }
        });

        alertsTable.setItems(alertData);
    }

    // ──────────────────────────────────────────────────────────
    //  INVENTORY TABLE
    // ──────────────────────────────────────────────────────────

    private void setupInventoryTable() {
        colInvName.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getIngredientName()));

        colInvStock.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getCurrentStock().toPlainString()));

        colInvReorder.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getReorderLevel().toPlainString()));

        colInvUnit.setCellValueFactory(cd ->
            new SimpleStringProperty(cd.getValue().getUnit()));

        colInvCost.setCellValueFactory(cd ->
            new SimpleStringProperty("$" + cd.getValue().getCostPerUnit().toPlainString()));

        // Status column — OK / LOW / OUT
        colInvStatus.setCellValueFactory(cd -> {
            Ingredient i = cd.getValue();
            BigDecimal stock = i.getCurrentStock();
            BigDecimal reorder = i.getReorderLevel();

            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                return new SimpleStringProperty("OUT OF STOCK");
            } else if (stock.compareTo(reorder) <= 0) {
                return new SimpleStringProperty("LOW STOCK");
            } else {
                return new SimpleStringProperty("OK");
            }
        });

        colInvStatus.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "OUT OF STOCK" -> setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                        case "LOW STOCK"    -> setStyle("-fx-text-fill: #F59E0B; -fx-font-weight: bold;");
                        default             -> setStyle("-fx-text-fill: #22C55E;");
                    }
                }
            }
        });

        // Row highlighting for low stock in main inventory table too
        inventoryTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Ingredient ing, boolean empty) {
                super.updateItem(ing, empty);
                if (empty || ing == null) {
                    setStyle("");
                } else if (ing.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0) {
                    setStyle("-fx-background-color: rgba(239,68,68,0.08);");
                } else if (ing.getCurrentStock().compareTo(ing.getReorderLevel()) <= 0) {
                    setStyle("-fx-background-color: rgba(245,158,11,0.06);");
                } else {
                    setStyle("");
                }
            }
        });

        inventoryTable.setItems(inventoryData);
    }

    // ──────────────────────────────────────────────────────────
    //  DATA LOADING
    // ──────────────────────────────────────────────────────────

    private void loadData() {
        try {
            // Load alerts
            List<InventoryAlert> alerts = alertDAO.findActiveAlerts();
            alertData.setAll(alerts);
            lblAlertCount.setText(alerts.size() + " alert(s)");

            // Load full inventory
            List<Ingredient> ingredients = alertDAO.findAllIngredientsWithStatus();
            inventoryData.setAll(ingredients);
            lblInventoryCount.setText(ingredients.size() + " item(s)");
            lblTotalIngredients.setText(String.valueOf(ingredients.size()));

            // Load counts
            int[] counts = alertDAO.getAlertCounts();
            AnimationUtil.animateCounter(lblCriticalCount, counts[0], "", "", 600);
            AnimationUtil.animateCounter(lblWarningCount, counts[1], "", "", 600);

            // Timestamp
            lblLastRefresh.setText("Last refresh: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        } catch (SQLException e) {
            lblAlertCount.setText("❌ " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  ACTIONS
    // ──────────────────────────────────────────────────────────

    @FXML
    public void handleRefresh() {
        AnimationUtil.spinOnce(btnRefresh);
        loadData();
        AnimationUtil.zoomFadeIn(alertsTable, 0);
        AnimationUtil.zoomFadeIn(inventoryTable, 0.1);
    }
}
