package com.tasteflow.controller;

import com.tasteflow.App;
import com.tasteflow.util.AnimationUtil;
import com.tasteflow.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.util.List;

/**
 * Dashboard controller — GoF Mediator pattern navigator.
 * Each sidebar button gets a crisp SVG vector icon + animations.
 */
public class DashboardController {

    @FXML private StackPane contentArea;
    @FXML private Label navStatusLabel;

    @FXML private Button btnPOS;
    @FXML private Button btnKDS;
    @FXML private Button btnMenu;
    @FXML private Button btnInventory;
    @FXML private Button btnReservation;
    @FXML private Button btnCustomers;
    @FXML private Button btnDelivery;
    @FXML private Button btnSchedule;
    @FXML private Button btnInvDashboard;
    @FXML private Button btnDiagnostics;
    @FXML private Button btnThemeToggle;

    private static final String NAV_BUTTON_CLASS = "nav-button";
    private static final String NAV_ACTIVE_CLASS = "nav-button-active";

    /* ── SVG icon paths (16×16 viewBox) ─────────────────── */

    // Receipt / cash register
    private static final String ICON_POS =
            "M4 0h8a2 2 0 0 1 2 2v14l-3-2-2 2-2-2-2 2-2-2-3 2V2a2 2 0 0 1 2-2z"
          + "M5 4h6v1H5zM5 7h6v1H5zM5 10h4v1H5z";

    // Chef hat
    private static final String ICON_KDS =
            "M8 0C5.8 0 4 1.5 4 3.5c0 .3 0 .5.1.8C2.3 4.8 1 6.3 1 8c0 2 1.5 3.5 3.3 3.9"
          + "V13a1 1 0 0 0 1 1h5.4a1 1 0 0 0 1-1v-1.1C13.5 11.5 15 10 15 8c0-1.7-1.3-3.2-3.1-3.7"
          + ".1-.3.1-.5.1-.8C12 1.5 10.2 0 8 0z";

    // Hamburger / food
    private static final String ICON_MENU =
            "M1 7h14a1 1 0 0 1 0 2H1a1 1 0 0 1 0-2zM2 5c0-2.2 2.7-4 6-4s6 1.8 6 4H2z"
          + "M2 11h12l-1 3H3l-1-3z";

    // Box / package
    private static final String ICON_INVENTORY =
            "M1 4l7-4 7 4v1H1V4zM1 6h14v8a1 1 0 0 1-1 1H2a1 1 0 0 1-1-1V6z"
          + "M6 8h4v3H6V8z";

    // Calendar
    private static final String ICON_RESERVATION =
            "M4 0v2h8V0h2v2h1a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H1a1 1 0 0 1-1-1V3"
          + "a1 1 0 0 1 1-1h1V0h2zM0 6h16v9H0V6zM2 8v2h3V8H2zM7 8v2h3V8H7z"
          + "M12 8v2h2V8h-2z";

    // People / users
    private static final String ICON_CUSTOMERS =
            "M5.5 0a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM0 12c0-2.2 2.5-4 5.5-4s5.5 1.8 5.5 4v1H0v-1z"
          + "M12 2a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM12 9c1.8 0 3.4.8 4 2v1h-4";

    // Delivery truck
    private static final String ICON_DELIVERY =
            "M0 2h10v9H0V2zM10 5h3l3 3v3h-2.1a2 2 0 0 1-3.8 0H5.9a2 2 0 0 1-3.8 0H0v-1h10V5z"
          + "M4 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM12 12a1 1 0 1 0 0-2 1 1 0 0 0 0 2z";

    // Clock / schedule
    private static final String ICON_SCHEDULE =
            "M8 0a8 8 0 1 0 0 16A8 8 0 0 0 8 0zM7 3h2v5l3.5 2-.9 1.6L7 9V3z";

    // Chart / dashboard
    private static final String ICON_INV_DASHBOARD =
            "M1 14h14v1H0V0h1v14zM3 10h2v4H3v-4zM7 6h2v8H7V6zM11 8h2v6h-2V8zM15 3h-2v11h2V3z";

    // Gear / settings
    private static final String ICON_DIAGNOSTICS =
            "M7 0h2l.4 2.2c.5.2.9.4 1.3.7l2.1-.8 1 1.7-1.7 1.4c.1.5.1 1 0 1.6l1.7 1.4-1 1.7"
          + "-2.1-.8c-.4.3-.8.5-1.3.7L9 12H7l-.4-2.2c-.5-.2-.9-.4-1.3-.7l-2.1.8-1-1.7 1.7-1.4"
          + "c-.1-.5-.1-1 0-1.6L2.2 3.8l1-1.7 2.1.8c.4-.3.8-.5 1.3-.7L7 0z"
          + "M8 4.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z";

    // Sun
    private static final String ICON_SUN =
            "M8 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM8 0v2M8 14v2M0 8h2M14 8h2"
          + "M2.3 2.3l1.4 1.4M12.3 12.3l1.4 1.4M2.3 13.7l1.4-1.4M12.3 3.7l1.4-1.4";

    // Moon
    private static final String ICON_MOON =
            "M6 0a8 8 0 1 0 8 10A6 6 0 0 1 6 0z";

    /* ──────────────────────────────────────────────────────
     *  INITIALIZATION
     * ────────────────────────────────────────────────────── */
    @FXML
    public void initialize() {

        // 1. Set SVG icons on each button
        setIcon(btnPOS,          ICON_POS);
        setIcon(btnKDS,          ICON_KDS);
        setIcon(btnMenu,         ICON_MENU);
        setIcon(btnInventory,    ICON_INVENTORY);
        setIcon(btnReservation,  ICON_RESERVATION);
        setIcon(btnCustomers,    ICON_CUSTOMERS);
        setIcon(btnDelivery,     ICON_DELIVERY);
        setIcon(btnSchedule,     ICON_SCHEDULE);
        setIcon(btnInvDashboard, ICON_INV_DASHBOARD);
        setIcon(btnDiagnostics,  ICON_DIAGNOSTICS);
        updateThemeButtonIcon();

        // 2. Collect all nav buttons
        List<Button> navButtons = List.of(
                btnPOS, btnKDS, btnMenu, btnInventory,
                btnReservation, btnCustomers, btnDelivery,
                btnSchedule, btnInvDashboard, btnDiagnostics
        );

        // 3. Apply CSS classes + animations
        double stagger = 0.0;
        for (Button btn : navButtons) {
            btn.setStyle(null);
            btn.getStyleClass().add(NAV_BUTTON_CLASS);
            AnimationUtil.applyHoverScale(btn);
            AnimationUtil.applyClickPulse(btn);
            AnimationUtil.fadeInAndSlideRight(btn, stagger);
            stagger += 0.07;
        }

        // 4. Theme toggle button
        if (btnThemeToggle != null) {
            AnimationUtil.applyHoverScale(btnThemeToggle);
            AnimationUtil.applyClickPulse(btnThemeToggle);
            AnimationUtil.fadeInAndSlideRight(btnThemeToggle, stagger);
        }

        // 5. Content area + status
        AnimationUtil.fadeInAndSlideUp(contentArea, 0.15);
        if (navStatusLabel != null) {
            AnimationUtil.fadeInAndSlideUp(navStatusLabel, stagger + 0.1);
        }
    }

    /* ──────────────────────────────────────────────────────
     *  SVG ICON HELPER
     * ────────────────────────────────────────────────────── */
    private void setIcon(Button btn, String svgContent) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgContent);
        icon.setFill(Color.web("#A0A0A0"));
        icon.setScaleX(0.9);
        icon.setScaleY(0.9);
        btn.setGraphic(icon);
    }

    private void updateThemeButtonIcon() {
        if (btnThemeToggle == null) return;
        boolean dark = ThemeManager.getInstance().isDark();
        setIcon(btnThemeToggle, dark ? ICON_SUN : ICON_MOON);
        btnThemeToggle.setText(dark ? "Light Mode" : "Dark Mode");
    }

    /* ──────────────────────────────────────────────────────
     *  THEME TOGGLE
     * ────────────────────────────────────────────────────── */
    @FXML
    public void handleThemeToggle() {
        AnimationUtil.themeFlash(contentArea);
        ThemeManager.getInstance().toggle();
        updateThemeButtonIcon();
        navStatusLabel.setText(ThemeManager.getInstance().isDark()
                ? "🌙 Dark mode enabled" : "☀ Light mode enabled");
    }

    /* ──────────────────────────────────────────────────────
     *  SIDEBAR CLICK HANDLERS
     * ────────────────────────────────────────────────────── */
    @FXML public void handlePOSClick()          { loadModule("pos_dashboard", "POS (Dine-In)"); setActiveButton(btnPOS); }
    @FXML public void handleKDSClick()          { loadModule("kds_screen", "Kitchen Display"); setActiveButton(btnKDS); }
    @FXML public void handleMenuClick()         { loadModule("menu_management", "Menu & Recipes"); setActiveButton(btnMenu); }
    @FXML public void handleInventoryClick()    { loadModule("inventory_management", "Inventory & POs"); setActiveButton(btnInventory); }
    @FXML public void handleReservationClick()  { loadModule("reservation", "Reservations"); setActiveButton(btnReservation); }
    @FXML public void handleCustomersClick()    { loadModule("customer_management", "Customers"); setActiveButton(btnCustomers); }
    @FXML public void handleDeliveryClick()     { loadModule("delivery_tracker", "Delivery Tracker"); setActiveButton(btnDelivery); }
    @FXML public void handleScheduleClick()     { loadModule("staff_schedule", "Staff Schedule"); setActiveButton(btnSchedule); }
    @FXML public void handleInvDashboardClick() { loadModule("inventory_dashboard", "Inventory Dashboard"); setActiveButton(btnInvDashboard); }
    @FXML public void handleDiagnosticsClick()  { loadModule("primary", "Diagnostics"); setActiveButton(btnDiagnostics); }

    /* ──────────────────────────────────────────────────────
     *  MODULE LOADING
     * ────────────────────────────────────────────────────── */
    private void loadModule(String fxmlName, String label) {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource(fxmlName + ".fxml"));
            Node moduleRoot = loader.load();
            AnimationUtil.transitionContent(contentArea, moduleRoot);
            navStatusLabel.setText("📍 " + label);
        } catch (IOException e) {
            navStatusLabel.setText("❌ Failed to load " + fxmlName);
            e.printStackTrace();
        }
    }

    /* ──────────────────────────────────────────────────────
     *  ACTIVE BUTTON STATE — updates icon color too
     * ────────────────────────────────────────────────────── */
    private void setActiveButton(Button active) {
        List<Button> all = List.of(
                btnPOS, btnKDS, btnMenu, btnInventory,
                btnReservation, btnCustomers, btnDelivery,
                btnSchedule, btnInvDashboard, btnDiagnostics
        );

        for (Button btn : all) {
            btn.getStyleClass().remove(NAV_ACTIVE_CLASS);
            if (!btn.getStyleClass().contains(NAV_BUTTON_CLASS)) {
                btn.getStyleClass().add(NAV_BUTTON_CLASS);
            }
            // Reset icon to grey
            if (btn.getGraphic() instanceof SVGPath svg) {
                svg.setFill(Color.web("#A0A0A0"));
            }
        }

        active.getStyleClass().remove(NAV_BUTTON_CLASS);
        if (!active.getStyleClass().contains(NAV_ACTIVE_CLASS)) {
            active.getStyleClass().add(NAV_ACTIVE_CLASS);
        }
        // Highlight active icon to accent blue
        if (active.getGraphic() instanceof SVGPath svg) {
            svg.setFill(Color.web("#3B82F6"));
        }
    }
}
