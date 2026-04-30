package com.tasteflow;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class PrimaryController {

    @FXML
    private Label statusLabel;

    @FXML
    private void runDiagnostics() {
        String result = DatabaseConnection.getInstance().checkStatus();
        statusLabel.setText(result);
        statusLabel.setTextFill(result.startsWith("✅") ? Color.GREEN : Color.RED);
    }
}
