package com.tasteflow.controller;

import com.tasteflow.dao.CategoryDAO;
import com.tasteflow.dao.IngredientDAO;
import com.tasteflow.dao.MenuDAO;
import com.tasteflow.model.Ingredient;
import com.tasteflow.model.MenuCategory;
import com.tasteflow.model.MenuItem;
import com.tasteflow.model.RecipeIngredient;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller for menu_management.fxml.
 * Follows GRASP Controller pattern — mediates between UI events and DAOs.
 */
public class MenuManagementController {

    // ---- DAOs (Information Expert — they know how to persist their entities) ----
    private final MenuDAO menuDAO = new MenuDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();

    // ---- FXML-bound controls ----
    @FXML private ComboBox<MenuCategory> categoryCombo;
    @FXML private TextField itemNameField;
    @FXML private TextField priceField;
    @FXML private Spinner<Integer> prepTimeSpinner;
    @FXML private TextArea descriptionArea;
    @FXML private CheckBox availableCheck;
    @FXML private Label statusLabel;

    // Recipe sub-form
    @FXML private ComboBox<Ingredient> ingredientCombo;
    @FXML private TextField qtyField;
    @FXML private TableView<RecipeIngredient> recipeTable;
    @FXML private TableColumn<RecipeIngredient, String> riNameCol;
    @FXML private TableColumn<RecipeIngredient, String> riQtyCol;
    @FXML private TableColumn<RecipeIngredient, String> riUnitCol;

    // Right-side menu list
    @FXML private TableView<MenuItem> menuTable;
    @FXML private TableColumn<MenuItem, Number> idCol;
    @FXML private TableColumn<MenuItem, String> nameCol;
    @FXML private TableColumn<MenuItem, String> catCol;
    @FXML private TableColumn<MenuItem, String> priceCol;
    @FXML private TableColumn<MenuItem, Number> prepCol;
    @FXML private TableColumn<MenuItem, Boolean> availCol;

    // Observable lists
    private final ObservableList<RecipeIngredient> recipeRows = FXCollections.observableArrayList();
    private final ObservableList<MenuItem> menuItems = FXCollections.observableArrayList();

    /** Tracks the currently-selected item for update/delete. */
    private MenuItem selectedItem = null;

    // ====================================================================
    //  INITIALIZE
    // ====================================================================

    @FXML
    public void initialize() {
        // Prep-time spinner: 1 – 120 min, default 15
        prepTimeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 120, 15));

        // --- Wire menu table columns ---
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getItemId()));
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getItemName()));
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCategoryName()));
        priceCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPrice() != null ? cd.getValue().getPrice().toPlainString() : ""));
        prepCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getPreparationTimeMin()));
        availCol.setCellValueFactory(cd -> new SimpleBooleanProperty(cd.getValue().isAvailable()));
        menuTable.setItems(menuItems);

        // --- Wire recipe sub-table columns ---
        riNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getIngredientName()));
        riQtyCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getQuantityRequired() != null ? cd.getValue().getQuantityRequired().toPlainString() : ""));
        riUnitCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getUnit()));
        recipeTable.setItems(recipeRows);

        // --- Menu table row click → populate form for editing ---
        menuTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateForm(newVal);
            }
        });

        // --- Load initial data ---
        loadCategories();
        loadIngredients();
        handleRefresh();
    }

    // ====================================================================
    //  EVENT HANDLERS
    // ====================================================================

    /** Refresh the right-hand menu table. */
    @FXML
    private void handleRefresh() {
        try {
            menuItems.setAll(menuDAO.findAll());
            setStatus("Loaded " + menuItems.size() + " menu items.", false);
        } catch (SQLException e) {
            setStatus("Error loading menu: " + e.getMessage(), true);
        }
    }

    /** Save a NEW menu item (with recipe). */
    @FXML
    private void handleSave() {
        MenuItem item = buildItemFromForm();
        if (item == null) return;   // validation failed

        try {
            int id = menuDAO.insertWithRecipe(item);
            setStatus("✅ Saved new item #" + id + ": " + item.getItemName(), false);
            handleClear();
            handleRefresh();
        } catch (SQLException e) {
            setStatus("❌ Save failed: " + e.getMessage(), true);
        }
    }

    /** Update an EXISTING menu item (with recipe). */
    @FXML
    private void handleUpdate() {
        if (selectedItem == null) {
            setStatus("⚠ Select an item from the table first.", true);
            return;
        }
        MenuItem item = buildItemFromForm();
        if (item == null) return;
        item.setItemId(selectedItem.getItemId());

        try {
            menuDAO.updateWithRecipe(item);
            setStatus("✅ Updated item #" + item.getItemId(), false);
            handleClear();
            handleRefresh();
        } catch (SQLException e) {
            setStatus("❌ Update failed: " + e.getMessage(), true);
        }
    }

    /** Soft-delete (mark unavailable). */
    @FXML
    private void handleDelete() {
        if (selectedItem == null) {
            setStatus("⚠ Select an item from the table first.", true);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Mark \"" + selectedItem.getItemName() + "\" as unavailable?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                menuDAO.softDelete(selectedItem.getItemId());
                setStatus("🗑 Item marked unavailable.", false);
                handleClear();
                handleRefresh();
            } catch (SQLException e) {
                setStatus("❌ Delete failed: " + e.getMessage(), true);
            }
        }
    }

    /** Clear the form. */
    @FXML
    private void handleClear() {
        selectedItem = null;
        categoryCombo.getSelectionModel().clearSelection();
        itemNameField.clear();
        priceField.clear();
        prepTimeSpinner.getValueFactory().setValue(15);
        descriptionArea.clear();
        availableCheck.setSelected(true);
        recipeRows.clear();
        menuTable.getSelectionModel().clearSelection();
        setStatus("", false);
    }

    /** Add a recipe row from the ingredient combo + qty field. */
    @FXML
    private void handleAddRecipeRow() {
        Ingredient sel = ingredientCombo.getSelectionModel().getSelectedItem();
        if (sel == null) {
            setStatus("⚠ Select an ingredient first.", true);
            return;
        }

        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyField.getText().trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setStatus("⚠ Enter a valid positive quantity.", true);
            return;
        }

        // Prevent duplicate ingredient in the same recipe
        for (RecipeIngredient existing : recipeRows) {
            if (existing.getIngredientId() == sel.getIngredientId()) {
                setStatus("⚠ Ingredient already in recipe — remove it first.", true);
                return;
            }
        }

        recipeRows.add(new RecipeIngredient(
                sel.getIngredientId(), sel.getIngredientName(), sel.getUnit(), qty));
        qtyField.clear();
        ingredientCombo.getSelectionModel().clearSelection();
    }

    /** Remove selected recipe row. */
    @FXML
    private void handleRemoveRecipeRow() {
        RecipeIngredient sel = recipeTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            recipeRows.remove(sel);
        }
    }

    /** Prompt for a new category name, insert it, and refresh combo. */
    @FXML
    private void handleNewCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Category");
        dialog.setHeaderText("Enter category name:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            MenuCategory cat = new MenuCategory();
            cat.setCategoryName(name.trim());
            try {
                categoryDAO.insert(cat);
                loadCategories();
                // Auto-select the new category
                categoryCombo.getSelectionModel().select(cat);
                setStatus("✅ Category created: " + name.trim(), false);
            } catch (SQLException e) {
                setStatus("❌ " + e.getMessage(), true);
            }
        });
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    /**
     * Reads form fields, validates, and builds a MenuItem with recipe list.
     * Returns null if validation fails (status label is set).
     */
    private MenuItem buildItemFromForm() {
        MenuCategory cat = categoryCombo.getSelectionModel().getSelectedItem();
        if (cat == null) { setStatus("⚠ Select a category.", true); return null; }

        String name = itemNameField.getText().trim();
        if (name.isEmpty()) { setStatus("⚠ Item name is required.", true); return null; }

        BigDecimal price;
        try {
            price = new BigDecimal(priceField.getText().trim());
            if (price.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setStatus("⚠ Enter a valid positive price.", true);
            return null;
        }

        if (recipeRows.isEmpty()) {
            setStatus("⚠ Add at least one recipe ingredient.", true);
            return null;
        }

        MenuItem item = new MenuItem();
        item.setCategoryId(cat.getCategoryId());
        item.setItemName(name);
        item.setDescription(descriptionArea.getText().trim());
        item.setPrice(price);
        item.setAvailable(availableCheck.isSelected());
        item.setPreparationTimeMin(prepTimeSpinner.getValue());
        item.setRecipeIngredients(new java.util.ArrayList<>(recipeRows));
        return item;
    }

    /** Populates the form from a selected MenuItem (for editing). */
    private void populateForm(MenuItem item) {
        selectedItem = item;

        // Find and select the matching category
        for (MenuCategory c : categoryCombo.getItems()) {
            if (c.getCategoryId() == item.getCategoryId()) {
                categoryCombo.getSelectionModel().select(c);
                break;
            }
        }

        itemNameField.setText(item.getItemName());
        priceField.setText(item.getPrice() != null ? item.getPrice().toPlainString() : "");
        prepTimeSpinner.getValueFactory().setValue(item.getPreparationTimeMin());
        descriptionArea.setText(item.getDescription() != null ? item.getDescription() : "");
        availableCheck.setSelected(item.isAvailable());

        // Load recipe rows from DB
        try {
            recipeRows.setAll(menuDAO.findRecipeByItemId(item.getItemId()));
        } catch (SQLException e) {
            setStatus("❌ Error loading recipe: " + e.getMessage(), true);
        }
    }

    private void loadCategories() {
        try {
            categoryCombo.setItems(FXCollections.observableArrayList(categoryDAO.findAllActive()));
        } catch (SQLException e) {
            setStatus("❌ Error loading categories: " + e.getMessage(), true);
        }
    }

    private void loadIngredients() {
        try {
            ingredientCombo.setItems(FXCollections.observableArrayList(ingredientDAO.findAllActive()));
        } catch (SQLException e) {
            setStatus("❌ Error loading ingredients: " + e.getMessage(), true);
        }
    }

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }
}
