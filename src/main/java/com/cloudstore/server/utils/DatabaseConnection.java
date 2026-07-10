package com.cloudstore.server.utils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseConnection {
    private static DatabaseConnection instance; // Singleton instance
    private static String connectionUrl; // Database connection URL
    private static String username; // Database username
    private static String password; // Database password
    
    /**
        * Private constructor for the DatabaseConnection class.
    **/
    private DatabaseConnection() {
        String host = getRequiredEnv("DB_HOST");
        String port = getRequiredEnv("DB_PORT");
        String database = getRequiredEnv("DB_NAME");
        
        username = getRequiredEnv("DB_USER");
        password = getRequiredEnv("DB_PASSWORD");
        
        connectionUrl = String.format("jdbc:mysql://%s:%s/%s?%s", host, port, database, buildConnectionOptions());
    }

    private String buildConnectionOptions() {
        List<String> options = new ArrayList<>();
        options.add("sslMode=" + getOptionalEnv("DB_SSL_MODE", "PREFERRED"));
        options.add("serverTimezone=" + getOptionalEnv("DB_SERVER_TIMEZONE", "UTC"));
        options.add("allowPublicKeyRetrieval=" + getOptionalEnv("DB_ALLOW_PUBLIC_KEY_RETRIEVAL", "true"));

        String trustCertPath = getOptionalEnv("DB_TRUST_CERT_PATH", "");
        if (!trustCertPath.isBlank()) {
            options.add("trustCertificateKeyStoreUrl=file:" + trustCertPath);
        }

        String trustCertPassword = getOptionalEnv("DB_TRUST_CERT_PASSWORD", "");
        if (!trustCertPassword.isBlank()) {
            options.add("trustCertificateKeyStorePassword=" + trustCertPassword);
        }

        return String.join("&", options);
    }
    
    /**
        * Retrieves the value of a required environment variable.
        * @param key The environment variable name.
        * @return The environment variable value.
        * @throws IllegalStateException If the environment variable is missing or empty.
    **/
    private String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private String getOptionalEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
    
    /**
        * Retrieves the singleton instance of the DatabaseConnection class.
        * @return The singleton instance of the DatabaseConnection class.
        * @throws SQLException If an error occurs while initializing the database connection.
    **/
    public static synchronized DatabaseConnection getInstance() throws SQLException {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }
    
    /**
        * Establishes and returns a connection to the database.
        * @return A Connection object representing the connection to the database.
        * @throws SQLException If an error occurs while connecting to the database.
    **/
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(connectionUrl, username, password);
            return conn;
        } catch (SQLException e) {
            throw e;
        }
    }
}
