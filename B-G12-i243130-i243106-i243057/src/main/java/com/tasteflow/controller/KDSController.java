package com.tasteflow.controller;

import com.tasteflow.dao.KdsDAO;
import com.tasteflow.model.KdsTicket;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Kitchen Display System (kds_screen.fxml).
 *
 * Features:
 *  - Polls the database every 3 seconds via javafx.animation.Timeline
 *  - Auto-generates KDS tickets for new order items
 *  - Displays ticket cards colour-coded by status and priority
 *  - Red-alert allergen warnings requiring explicit acknowledgement
 *  - 30-second correction window to recall tickets marked READY
 *  - Live clock display
 *
 * Pattern: GRASP Controller + Observer (polling loop)
 */
public class KDSController {

    private final KdsDAO kdsDAO = new KdsDAO();

    @FXML private FlowPane ticketPane;
    @FXML private Label ticketCountLabel;
    @FXML private Label clockLabel;
    @FXML private Label pollingLabel;
    @FXML private Label statusLabel;

    private Timeline pollingTimeline;
    private Timeline clockTimeline;

    private static final int CHEF_ID = 1; // default chef for acknowledgements

    // ====================================================================
    //  INITIALIZE
    // ====================================================================

    @FXML
    public void initialize() {
        // Initial load
        refreshTickets();

        // Polling every 3 seconds
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            try {
                int generated = kdsDAO.generateMissingTickets();
                if (generated > 0) {
                    setStatus("+" + generated + " new ticket(s) detected.", false);
                }
                refreshTickets();
            } catch (SQLException ex) {
                setStatus("Polling error: " + ex.getMessage(), true);
            }
        }));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();

        // Live clock every second
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }));
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    // ====================================================================
    //  REFRESH & RENDER
    // ====================================================================

    private void refreshTickets() {
        try {
            List<KdsTicket> tickets = kdsDAO.findActiveTickets();
            ticketCountLabel.setText(tickets.size() + " ticket(s)");
            renderTickets(tickets);
        } catch (SQLException e) {
            setStatus("Error: " + e.getMessage(), true);
        }
    }

    private void renderTickets(List<KdsTicket> tickets) {
        ticketPane.getChildren().clear();

        for (KdsTicket ticket : tickets) {
            VBox card = buildTicketCard(ticket);
            ticketPane.getChildren().add(card);
        }

        if (tickets.isEmpty()) {
            Label empty = new Label("No active tickets — waiting for orders...");
            empty.setStyle("-fx-font-size: 16; -fx-text-fill: #555;");
            ticketPane.getChildren().add(empty);
        }
    }

    // ====================================================================
    //  TICKET CARD BUILDER
    // ====================================================================

    private VBox buildTicketCard(KdsTicket ticket) {
        VBox card = new VBox(6);
        card.setPrefWidth(220);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.TOP_LEFT);

        // --- Card border colour based on status + priority ---
        String borderColor;
        String bgColor;

        boolean isAllergenAlert = ticket.isHasAllergenWarning() && !ticket.isAllergenAcknowledged();

        if (isAllergenAlert) {
            borderColor = "#e74c3c";
            bgColor = "#3d1010";
        } else if ("VIP".equals(ticket.getPriority())) {
            borderColor = "#f1c40f";
            bgColor = "#2c2c1a";
        } else if ("RUSH".equals(ticket.getPriority())) {
            borderColor = "#e67e22";
            bgColor = "#2c2010";
        } else if ("READY".equals(ticket.getStatus())) {
            borderColor = "#27ae60";
            bgColor = "#102c1a";
        } else if ("IN_PROGRESS".equals(ticket.getStatus())) {
            borderColor = "#2980b9";
            bgColor = "#10182c";
        } else {
            borderColor = "#555";
            bgColor = "#16213e";
        }

        card.setStyle("-fx-background-color: " + bgColor + "; " +
                       "-fx-border-color: " + borderColor + "; " +
                       "-fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8;");

        // --- Header: Order# + Table ---
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label orderLabel = new Label("Order #" + ticket.getOrderId());
        orderLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #a0a0a0;");
        Label tableLabel = new Label("T" + ticket.getTableNumber());
        tableLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #ecf0f1; " +
                            "-fx-background-color: #444; -fx-padding: 2 6; -fx-background-radius: 4;");
        header.getChildren().addAll(orderLabel, tableLabel);

        // Priority badge
        if (!"NORMAL".equals(ticket.getPriority())) {
            Label badge = new Label(ticket.getPriority());
            String badgeColor = "VIP".equals(ticket.getPriority()) ? "#f1c40f" : "#e67e22";
            badge.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: " + badgeColor + "; " +
                           "-fx-border-color: " + badgeColor + "; -fx-border-width: 1; " +
                           "-fx-padding: 1 5; -fx-border-radius: 3; -fx-background-radius: 3;");
            header.getChildren().add(badge);
        }

        card.getChildren().add(header);

        // --- Item name + quantity ---
        Label itemLabel = new Label(ticket.getQuantity() + "x " + ticket.getItemName());
        itemLabel.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #ecf0f1;");
        itemLabel.setWrapText(true);
        card.getChildren().add(itemLabel);

        // --- Special requests ---
        if (ticket.getSpecialRequests() != null && !ticket.getSpecialRequests().isEmpty()) {
            Label reqLabel = new Label("NOTE: " + ticket.getSpecialRequests());
            reqLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #f39c12; -fx-font-style: italic;");
            reqLabel.setWrapText(true);
            card.getChildren().add(reqLabel);
        }

        // --- ALLERGEN WARNING ---
        if (ticket.isHasAllergenWarning()) {
            String allergenText = ticket.getAllergenFlags() != null ? ticket.getAllergenFlags() : "Allergens present";
            Label allergenLabel = new Label("⚠ ALLERGEN: " + allergenText);
            allergenLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #e74c3c; " +
                                   "-fx-background-color: rgba(231,76,60,0.15); -fx-padding: 4 8; " +
                                   "-fx-background-radius: 4;");
            allergenLabel.setWrapText(true);
            card.getChildren().add(allergenLabel);

            if (!ticket.isAllergenAcknowledged()) {
                Button ackBtn = new Button("Acknowledge Allergens");
                ackBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                "-fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand;");
                ackBtn.setOnAction(e -> handleAcknowledgeAllergen(ticket));
                card.getChildren().add(ackBtn);
            } else {
                Label acked = new Label("✓ Allergens Acknowledged");
                acked.setStyle("-fx-font-size: 10; -fx-text-fill: #27ae60;");
                card.getChildren().add(acked);
            }
        }

        // --- Status label ---
        Label statusLbl = new Label(ticket.getStatus());
        String statusColor;
        if ("PENDING".equals(ticket.getStatus())) {
            statusColor = "#e67e22";
        } else if ("IN_PROGRESS".equals(ticket.getStatus())) {
            statusColor = "#2980b9";
        } else {
            statusColor = "#27ae60";
        }
        statusLbl.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + statusColor + ";");
        card.getChildren().add(statusLbl);

        // --- Correction window countdown ---
        if ("READY".equals(ticket.getStatus())) {
            long secs = ticket.getCorrectionSecondsRemaining();
            if (secs > 0) {
                Label countdownLabel = new Label("Recall window: " + secs + "s");
                countdownLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #f39c12;");
                card.getChildren().add(countdownLabel);
            }
        }

        // --- Action buttons ---
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_LEFT);

        if ("PENDING".equals(ticket.getStatus())) {
            Button startBtn = new Button("Start");
            startBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                              "-fx-font-size: 11; -fx-cursor: hand;");
            startBtn.setOnAction(e -> handleStart(ticket));
            actions.getChildren().add(startBtn);
        }

        if ("IN_PROGRESS".equals(ticket.getStatus())) {
            Button readyBtn = new Button("Mark Ready");
            readyBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                              "-fx-font-size: 11; -fx-cursor: hand;");
            readyBtn.setOnAction(e -> handleMarkReady(ticket));
            actions.getChildren().add(readyBtn);
        }

        if ("READY".equals(ticket.getStatus()) && ticket.getCorrectionSecondsRemaining() > 0) {
            Button recallBtn = new Button("Recall");
            recallBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; " +
                               "-fx-font-size: 11; -fx-cursor: hand;");
            recallBtn.setOnAction(e -> handleRecall(ticket));
            actions.getChildren().add(recallBtn);
        }

        if (!actions.getChildren().isEmpty()) {
            card.getChildren().add(actions);
        }

        // --- Time elapsed ---
        if (ticket.getCreatedAt() != null) {
            long elapsedSec = (System.currentTimeMillis() - ticket.getCreatedAt().getTime()) / 1000;
            long mins = elapsedSec / 60;
            long secs = elapsedSec % 60;
            Label timeLabel = new Label(mins + "m " + secs + "s ago");
            timeLabel.setStyle("-fx-font-size: 10; -fx-text-fill: " +
                               (mins >= 10 ? "#e74c3c" : "#7f8c8d") + ";");
            card.getChildren().add(timeLabel);
        }

        return card;
    }

    // ====================================================================
    //  EVENT HANDLERS
    // ====================================================================

    private void handleStart(KdsTicket ticket) {
        try {
            kdsDAO.startPreparing(ticket.getTicketId());
            setStatus("Ticket #" + ticket.getTicketId() + " — preparing.", false);
            refreshTickets();
        } catch (SQLException e) {
            // Show allergen warning as a dialog if that's the issue
            if (e.getMessage().contains("ALLERGEN")) {
                showAllergenDialog(ticket, e.getMessage());
            } else {
                setStatus("Error: " + e.getMessage(), true);
            }
        }
    }

    private void handleMarkReady(KdsTicket ticket) {
        try {
            kdsDAO.markReady(ticket.getTicketId());
            setStatus("Ticket #" + ticket.getTicketId() + " — READY! (30s correction window)", false);
            refreshTickets();
        } catch (SQLException e) {
            setStatus("Error: " + e.getMessage(), true);
        }
    }

    private void handleRecall(KdsTicket ticket) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Recall ticket #" + ticket.getTicketId() + " back to IN_PROGRESS?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Recall Ticket");
        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                kdsDAO.recallTicket(ticket.getTicketId());
                setStatus("Ticket #" + ticket.getTicketId() + " recalled back to preparation.", false);
                refreshTickets();
            } catch (SQLException e) {
                setStatus("Error: " + e.getMessage(), true);
            }
        }
    }

    private void handleAcknowledgeAllergen(KdsTicket ticket) {
        String allergens = ticket.getAllergenFlags() != null ? ticket.getAllergenFlags() : "allergens";

        Alert alert = new Alert(Alert.AlertType.WARNING,
                "This item contains: " + allergens + "\n\n" +
                "By clicking OK, you confirm you have taken the necessary precautions " +
                "to prevent cross-contamination.",
                ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText("⚠ ALLERGEN ACKNOWLEDGEMENT REQUIRED");
        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                kdsDAO.acknowledgeAllergens(ticket.getTicketId(), CHEF_ID);
                setStatus("Allergens acknowledged for ticket #" + ticket.getTicketId(), false);
                refreshTickets();
            } catch (SQLException e) {
                setStatus("Error: " + e.getMessage(), true);
            }
        }
    }

    private void showAllergenDialog(KdsTicket ticket, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText("⚠ Allergen Acknowledgement Required");
        alert.showAndWait();
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================

    private void setStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(isError ? Color.RED : Color.web("#27ae60"));
    }
}
