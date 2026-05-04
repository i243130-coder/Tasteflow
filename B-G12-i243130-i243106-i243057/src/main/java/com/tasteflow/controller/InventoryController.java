package com.tasteflow.controller;

import com.tasteflow.dao.IngredientDAO;
import com.tasteflow.dao.PurchaseOrderDAO;
import com.tasteflow.dao.SupplierDAO;
import com.tasteflow.model.Ingredient;
import com.tasteflow.model.PurchaseOrder;
import com.tasteflow.model.PurchaseOrderItem;
import com.tasteflow.model.Supplier;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.geometry.Insets;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for inventory_management.fxml (3 tabs).
 * Follows GRASP Controller pattern.
 */
public class InventoryController {

    // ---- DAOs ----
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final PurchaseOrderDAO poDAO = new PurchaseOrderDAO();

    // ======================== TAB 1: STOCK ========================
    @FXML private TableView<Ingredient> stockTable;
    @FXML private TableColumn<Ingredient, Number> stkIdCol;
    @FXML private TableColumn<Ingredient, String> stkNameCol;
    @FXML private TableColumn<Ingredient, String> stkUnitCol;
    @FXML private TableColumn<Ingredient, String> stkQtyCol;
    @FXML private TableColumn<Ingredient, String> stkReorderCol;
    @FXML private TableColumn<Ingredient, String> stkCostCol;
    @FXML private TableColumn<Ingredient, String> stkStatusCol;

    private final ObservableList<Ingredient> stockItems = FXCollections.observableArrayList();

    // ======================== TAB 2: SUPPLIERS ========================
    @FXML private TextField supplierNameField;
    @FXML private TextField supplierContactField;
    @FXML private TextField supplierPhoneField;
    @FXML private TextField supplierEmailField;
    @FXML private TextField supplierAddressField;
    @FXML private TableView<Supplier> supplierTable;
    @FXML private TableColumn<Supplier, Number> supIdCol;
    @FXML private TableColumn<Supplier, String> supNameCol;
    @FXML private TableColumn<Supplier, String> supContactCol;
    @FXML private TableColumn<Supplier, String> supPhoneCol;
    @FXML private TableColumn<Supplier, String> supEmailCol;
    @FXML private Label supplierStatusLabel;

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();

    // ======================== TAB 3: PURCHASE ORDERS ========================
    @FXML private ComboBox<Supplier> poSupplierCombo;
    @FXML private DatePicker poDatePicker;
    @FXML private ComboBox<Ingredient> poIngredientCombo;
    @FXML private TextField poQtyField;
    @FXML private TextField poPriceField;
    @FXML private Label poTotalLabel;
    @FXML private Label poStatusLabel;

    // PO line items builder
    @FXML private TableView<PurchaseOrderItem> poLineTable;
    @FXML private TableColumn<PurchaseOrderItem, String> poLineIngCol;
    @FXML private TableColumn<PurchaseOrderItem, String> poLineQtyCol;
    @FXML private TableColumn<PurchaseOrderItem, String> poLineUnitCol;
    @FXML private TableColumn<PurchaseOrderItem, String> poLinePrcCol;
    @FXML private TableColumn<PurchaseOrderItem, String> poLineTotCol;

    private final ObservableList<PurchaseOrderItem> poLineItems = FXCollections.observableArrayList();

    // Existing POs table
    @FXML private TableView<PurchaseOrder> poTable;
    @FXML private TableColumn<PurchaseOrder, Number> poIdCol;
    @FXML private TableColumn<PurchaseOrder, String> poSupCol;
    @FXML private TableColumn<PurchaseOrder, String> poDateCol;
    @FXML private TableColumn<PurchaseOrder, String> poExpCol;
    @FXML private TableColumn<PurchaseOrder, String> poAmtCol;
    @FXML private TableColumn<PurchaseOrder, String> poStatusCol;

    private final ObservableList<PurchaseOrder> purchaseOrders = FXCollections.observableArrayList();

    // ====================================================================
    //  INITIALIZE
    // ====================================================================

    @FXML
    public void initialize() {
        // --- Tab 1: Stock table columns ---
        stkIdCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getIngredientId()));
        stkNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getIngredientName()));
        stkUnitCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getUnit()));
        stkQtyCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getCurrentStock() != null ? cd.getValue().getCurrentStock().toPlainString() : "0"));
        stkReorderCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getReorderLevel() != null ? cd.getValue().getReorderLevel().toPlainString() : "0"));
        stkCostCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getCostPerUnit() != null ? cd.getValue().getCostPerUnit().toPlainString() : "0"));
        stkStatusCol.setCellValueFactory(cd -> {
            Ingredient i = cd.getValue();
            if (i.getCurrentStock() != null && i.getReorderLevel() != null
                    && i.getCurrentStock().compareTo(i.getReorderLevel()) <= 0) {
                return new SimpleStringProperty("⚠ LOW");
            }
            return new SimpleStringProperty("OK");
        });

        // Color the status column: red if low
        stkStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("LOW")) {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    }
                }
            }
        });

        stockTable.setItems(stockItems);

        // --- Tab 2: Supplier table columns ---
        supIdCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getSupplierId()));
        supNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSupplierName()));
        supContactCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getContactPerson()));
        supPhoneCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPhone()));
        supEmailCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getEmail()));
        supplierTable.setItems(suppliers);

        // --- Tab 3: PO line items table ---
        poLineIngCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getIngredientName()));
        poLineQtyCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getQuantityOrdered() != null ? cd.getValue().getQuantityOrdered().toPlainString() : ""));
        poLineUnitCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getUnit()));
        poLinePrcCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getUnitPrice() != null ? cd.getValue().getUnitPrice().toPlainString() : ""));
        poLineTotCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getLineTotal().toPlainString()));
        poLineTable.setItems(poLineItems);

        // --- Tab 3: Existing POs table ---
        poIdCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getPoId()));
        poSupCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSupplierName()));
        poDateCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getOrderDate() != null ? cd.getValue().getOrderDate().toString().substring(0, 16) : ""));
        poExpCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getExpectedDelivery() != null ? cd.getValue().getExpectedDelivery().toString() : ""));
        poAmtCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getTotalAmount() != null ? cd.getValue().getTotalAmount().toPlainString() : "0"));
        poStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getStatus()));

        // Color the PO status column
        poStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    if ("RECEIVED".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else if ("CANCELLED".equals(item)) {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    } else if ("SUBMITTED".equals(item)) {
                        setStyle("-fx-text-fill: #2980b9; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #7f8c8d;");
                    }
                }
            }
        });

        poTable.setItems(purchaseOrders);

        // --- Load all data ---
        handleRefreshStock();
        loadSuppliers();
        loadIngredientCombos();
        handleRefreshPOs();
    }

    // ====================================================================
    //  TAB 1: STOCK HANDLERS
    // ====================================================================

    @FXML
    private void handleRefreshStock() {
        try {
            stockItems.setAll(ingredientDAO.findAllActive());
        } catch (SQLException e) {
            showError("Stock Error", e.getMessage());
        }
    }

    // ====================================================================
    //  TAB 2: SUPPLIER HANDLERS
    // ====================================================================

    @FXML
    private void handleSaveSupplier() {
        String name = supplierNameField.getText().trim();
        if (name.isEmpty()) {
            setSupplierStatus("⚠ Supplier name is required.", true);
            return;
        }

        Supplier s = new Supplier();
        s.setSupplierName(name);
        s.setContactPerson(supplierContactField.getText().trim());
        s.setPhone(supplierPhoneField.getText().trim());
        s.setEmail(supplierEmailField.getText().trim());
        s.setAddress(supplierAddressField.getText().trim());

        try {
            supplierDAO.insert(s);
            setSupplierStatus("✅ Supplier saved: " + name, false);
            handleClearSupplier();
            loadSuppliers();
            loadSupplierCombo();
        } catch (SQLException e) {
            setSupplierStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleClearSupplier() {
        supplierNameField.clear();
        supplierContactField.clear();
        supplierPhoneField.clear();
        supplierEmailField.clear();
        supplierAddressField.clear();
    }

    // ====================================================================
    //  INLINE ADD: NEW SUPPLIER (from PO tab)
    // ====================================================================

    @FXML
    private void handleAddNewSupplierInline() {
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle("Add New Supplier");
        dialog.setHeaderText("Enter supplier details");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        ColumnConstraints labelCol = new ColumnConstraints(100);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        TextField nameF    = new TextField();
        nameF.setPromptText("Supplier name (required)");
        TextField contactF = new TextField();
        contactF.setPromptText("Contact person");
        TextField phoneF   = new TextField();
        phoneF.setPromptText("Phone");
        TextField emailF   = new TextField();
        emailF.setPromptText("Email");
        TextField addressF = new TextField();
        addressF.setPromptText("Address");

        grid.add(new Label("Name:"),    0, 0); grid.add(nameF,    1, 0);
        grid.add(new Label("Contact:"), 0, 1); grid.add(contactF, 1, 1);
        grid.add(new Label("Phone:"),   0, 2); grid.add(phoneF,   1, 2);
        grid.add(new Label("Email:"),   0, 3); grid.add(emailF,   1, 3);
        grid.add(new Label("Address:"), 0, 4); grid.add(addressF, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Disable Save button until name is provided
        dialog.getDialogPane().lookupButton(saveBtn).setDisable(true);
        nameF.textProperty().addListener((obs, o, n) ->
                dialog.getDialogPane().lookupButton(saveBtn).setDisable(n.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                Supplier s = new Supplier();
                s.setSupplierName(nameF.getText().trim());
                s.setContactPerson(contactF.getText().trim());
                s.setPhone(phoneF.getText().trim());
                s.setEmail(emailF.getText().trim());
                s.setAddress(addressF.getText().trim());
                return s;
            }
            return null;
        });

        Optional<Supplier> result = dialog.showAndWait();
        result.ifPresent(s -> {
            try {
                supplierDAO.insert(s);
                setPOStatus("✅ Supplier '" + s.getSupplierName() + "' created.", false);
                loadSuppliers();        // refresh Tab 2 table
                loadSupplierCombo();    // refresh PO combo
                // Auto-select the newly added supplier
                for (Supplier item : poSupplierCombo.getItems()) {
                    if (item.getSupplierId() == s.getSupplierId()) {
                        poSupplierCombo.getSelectionModel().select(item);
                        break;
                    }
                }
            } catch (SQLException ex) {
                setPOStatus("❌ " + ex.getMessage(), true);
            }
        });
    }

    // ====================================================================
    //  INLINE ADD: NEW INGREDIENT (from PO tab)
    // ====================================================================

    @FXML
    private void handleAddNewIngredientInline() {
        Dialog<Ingredient> dialog = new Dialog<>();
        dialog.setTitle("Add New Ingredient");
        dialog.setHeaderText("Enter ingredient details");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        ColumnConstraints labelCol = new ColumnConstraints(120);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        TextField nameF   = new TextField();
        nameF.setPromptText("Ingredient name (required)");
        ComboBox<String> unitCombo = new ComboBox<>();
        unitCombo.getItems().addAll("kg", "g", "litre", "ml", "piece", "dozen", "bottle", "packet");
        unitCombo.setPromptText("Select unit");
        unitCombo.setEditable(true);
        TextField stockF   = new TextField("0");
        stockF.setPromptText("Initial stock");
        TextField reorderF = new TextField("0");
        reorderF.setPromptText("Reorder level");
        TextField costF    = new TextField("0");
        costF.setPromptText("Cost per unit ($)");

        grid.add(new Label("Name:"),         0, 0); grid.add(nameF,     1, 0);
        grid.add(new Label("Unit:"),         0, 1); grid.add(unitCombo, 1, 1);
        grid.add(new Label("Initial Stock:"),0, 2); grid.add(stockF,    1, 2);
        grid.add(new Label("Reorder Level:"),0, 3); grid.add(reorderF,  1, 3);
        grid.add(new Label("Cost/Unit ($):"),0, 4); grid.add(costF,     1, 4);

        dialog.getDialogPane().setContent(grid);

        // Disable Save button until name is provided
        dialog.getDialogPane().lookupButton(saveBtn).setDisable(true);
        nameF.textProperty().addListener((obs, o, n) ->
                dialog.getDialogPane().lookupButton(saveBtn).setDisable(n.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                try {
                    Ingredient ing = new Ingredient();
                    ing.setIngredientName(nameF.getText().trim());
                    ing.setUnit(unitCombo.getValue() != null ? unitCombo.getValue().trim() : "piece");
                    ing.setCurrentStock(new BigDecimal(stockF.getText().trim()));
                    ing.setReorderLevel(new BigDecimal(reorderF.getText().trim()));
                    ing.setCostPerUnit(new BigDecimal(costF.getText().trim()));
                    return ing;
                } catch (NumberFormatException e) {
                    return null; // invalid number → treat as cancel
                }
            }
            return null;
        });

        Optional<Ingredient> result = dialog.showAndWait();
        result.ifPresent(ing -> {
            try {
                ingredientDAO.insert(ing);
                setPOStatus("✅ Ingredient '" + ing.getIngredientName() + "' created.", false);
                handleRefreshStock();       // refresh Tab 1 table
                loadIngredientCombos();     // refresh PO ingredient combo
                // Auto-select the newly added ingredient
                for (Ingredient item : poIngredientCombo.getItems()) {
                    if (item.getIngredientId() == ing.getIngredientId()) {
                        poIngredientCombo.getSelectionModel().select(item);
                        break;
                    }
                }
            } catch (SQLException ex) {
                setPOStatus("❌ " + ex.getMessage(), true);
            }
        });
    }

    // ====================================================================
    //  TAB 3: PURCHASE ORDER HANDLERS
    // ====================================================================

    @FXML
    private void handleAddPOLine() {
        Ingredient sel = poIngredientCombo.getSelectionModel().getSelectedItem();
        if (sel == null) { setPOStatus("⚠ Select an ingredient.", true); return; }

        BigDecimal qty;
        try {
            qty = new BigDecimal(poQtyField.getText().trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setPOStatus("⚠ Enter a valid positive quantity.", true);
            return;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(poPriceField.getText().trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setPOStatus("⚠ Enter a valid positive unit price.", true);
            return;
        }

        // Prevent duplicate ingredient
        for (PurchaseOrderItem existing : poLineItems) {
            if (existing.getIngredientId() == sel.getIngredientId()) {
                setPOStatus("⚠ Ingredient already added — remove it first.", true);
                return;
            }
        }

        poLineItems.add(new PurchaseOrderItem(
                sel.getIngredientId(), sel.getIngredientName(), sel.getUnit(), qty, price));

        poQtyField.clear();
        poPriceField.clear();
        poIngredientCombo.getSelectionModel().clearSelection();
        recalcPOTotal();
        setPOStatus("", false);
    }

    @FXML
    private void handleRemovePOLine() {
        PurchaseOrderItem sel = poLineTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            poLineItems.remove(sel);
            recalcPOTotal();
        }
    }

    @FXML
    private void handleSubmitPO() {
        Supplier sup = poSupplierCombo.getSelectionModel().getSelectedItem();
        if (sup == null) { setPOStatus("⚠ Select a supplier.", true); return; }

        if (poLineItems.isEmpty()) { setPOStatus("⚠ Add at least one line item.", true); return; }

        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(sup.getSupplierId());
        po.setBranchId(1);       // default branch for now
        po.setOrderedBy(1);      // default user for now
        po.setStatus("SUBMITTED");

        LocalDate exp = poDatePicker.getValue();
        if (exp != null) po.setExpectedDelivery(exp);

        po.setItems(new ArrayList<>(poLineItems));
        po.recalculateTotal();

        try {
            int id = poDAO.createPurchaseOrder(po);
            setPOStatus("✅ PO-" + id + " submitted ($" + po.getTotalAmount().toPlainString() + ")", false);
            handleClearPO();
            handleRefreshPOs();
        } catch (SQLException e) {
            setPOStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleReceivePO() {
        PurchaseOrder sel = poTable.getSelectionModel().getSelectedItem();
        if (sel == null) { setPOStatus("⚠ Select a PO from the table.", true); return; }

        if ("RECEIVED".equals(sel.getStatus()) || "CANCELLED".equals(sel.getStatus())) {
            setPOStatus("⚠ PO-" + sel.getPoId() + " is already " + sel.getStatus() + ".", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Mark PO-" + sel.getPoId() + " as received?\nThis will increase ingredient stock.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Receive");
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                poDAO.receivePurchaseOrder(sel.getPoId());
                setPOStatus("✅ PO-" + sel.getPoId() + " received — stock updated!", false);
                handleRefreshPOs();
                handleRefreshStock(); // refresh stock tab too
            } catch (SQLException e) {
                setPOStatus("❌ " + e.getMessage(), true);
            }
        }
    }

    @FXML
    private void handleCancelPO() {
        PurchaseOrder sel = poTable.getSelectionModel().getSelectedItem();
        if (sel == null) { setPOStatus("⚠ Select a PO from the table.", true); return; }

        try {
            poDAO.cancelPurchaseOrder(sel.getPoId());
            setPOStatus("🗑 PO-" + sel.getPoId() + " cancelled.", false);
            handleRefreshPOs();
        } catch (SQLException e) {
            setPOStatus("❌ " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleClearPO() {
        poSupplierCombo.getSelectionModel().clearSelection();
        poDatePicker.setValue(null);
        poLineItems.clear();
        poQtyField.clear();
        poPriceField.clear();
        poIngredientCombo.getSelectionModel().clearSelection();
        poTotalLabel.setText("Total: $0.00");
    }

    @FXML
    private void handleRefreshPOs() {
        try {
            purchaseOrders.setAll(poDAO.findAll());
        } catch (SQLException e) {
            setPOStatus("❌ " + e.getMessage(), true);
        }
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    private void loadSuppliers() {
        try {
            suppliers.setAll(supplierDAO.findAllActive());
        } catch (SQLException e) {
            showError("Supplier Error", e.getMessage());
        }
    }

    private void loadSupplierCombo() {
        try {
            poSupplierCombo.setItems(FXCollections.observableArrayList(supplierDAO.findAllActive()));
        } catch (SQLException e) {
            setPOStatus("❌ " + e.getMessage(), true);
        }
    }

    private void loadIngredientCombos() {
        try {
            poIngredientCombo.setItems(FXCollections.observableArrayList(ingredientDAO.findAllActive()));
        } catch (SQLException e) {
            showError("Ingredient Error", e.getMessage());
        }
        loadSupplierCombo();
    }

    private void recalcPOTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : poLineItems) {
            total = total.add(item.getLineTotal());
        }
        poTotalLabel.setText("Total: $" + total.toPlainString());
    }

    private void setSupplierStatus(String msg, boolean isError) {
        supplierStatusLabel.setText(msg);
        supplierStatusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }

    private void setPOStatus(String msg, boolean isError) {
        poStatusLabel.setText(msg);
        poStatusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
