module com.tasteflow {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.tasteflow to javafx.fxml;
    opens com.tasteflow.controller to javafx.fxml;
    opens com.tasteflow.model to javafx.base;
    opens com.tasteflow.util to javafx.fxml;

    exports com.tasteflow;
    exports com.tasteflow.controller;
    exports com.tasteflow.model;
    exports com.tasteflow.dao;
    exports com.tasteflow.bridge;
    exports com.tasteflow.util;
}
