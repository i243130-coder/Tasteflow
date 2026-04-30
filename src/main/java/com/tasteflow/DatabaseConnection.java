package com.tasteflow;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static volatile DatabaseConnection instance;

    private static final String URL = "jdbc:mysql://localhost:3306/tasteflow";
    private static final String USER = "root";
    private static final String PASS = "!@#$%^&*(";

    private DatabaseConnection() {
    }

    public static DatabaseConnection getInstance() {
        DatabaseConnection local = instance;
        if (local == null) {
            synchronized (DatabaseConnection.class) {
                local = instance;
                if (local == null) {
                    local = new DatabaseConnection();
                    instance = local;
                }
            }
        }
        return local;
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found", e);
        }
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public String checkStatus() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return "❌ Database Error: MySQL JDBC driver not found (" + e.getMessage() + ")";
        }

        try (Connection ignored = DriverManager.getConnection(URL, USER, PASS)) {
            return "✅ Database Connected!";
        } catch (SQLException e) {
            return "❌ Database Error: " + e.getMessage();
        }
    }
}

