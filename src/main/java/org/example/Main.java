package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;

public class Main {
    public static void main(String[] args) {
        // Simple MySQL connection and health check (SELECT 1)
        // Configuration via environment variables:
        // - MYSQL_URL (optional, full JDBC URL)
        // - MYSQL_HOST (default: localhost)
        // - MYSQL_PORT (default: 3306)
        // - MYSQL_DB (default: mysql)
        // - MYSQL_USER (default: root)
        // - MYSQL_PASSWORD (default: empty)

        String jdbcUrl = System.getenv("MYSQL_URL");
        String host = getEnvOrDefault("MYSQL_HOST", "localhost");
        String port = getEnvOrDefault("MYSQL_PORT", "3306");
        String database = getEnvOrDefault("MYSQL_DB", "mysql");
        String user = getEnvOrDefault("MYSQL_USER", "root");
        String password = getEnvOrDefault("MYSQL_PASSWORD", "");

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            // Build URL with recommended MySQL parameters
            jdbcUrl = String.format(
                    "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port, database);
        }

        // Optional: allow overriding connect timeout via env (ms)
        int connectTimeoutMs = parseIntOrDefault(System.getenv("MYSQL_CONNECT_TIMEOUT_MS"), 5000);

        // The MySQL driver will pick up connectTimeout from the URL; append if not present
        if (!jdbcUrl.contains("connectTimeout=")) {
            String sep = jdbcUrl.contains("?") ? "&" : "?";
            jdbcUrl = jdbcUrl + sep + "connectTimeout=" + connectTimeoutMs;
        }

        System.out.println("[INFO] Attempting MySQL connection...");
        System.out.println("[INFO] JDBC URL: " + redactPassword(jdbcUrl));
        System.out.println("[INFO] User: " + user);

        // Load driver explicitly (often optional, but safe across environments)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] MySQL JDBC Driver not found. Did you add mysql-connector-j dependency?");
            e.printStackTrace();
            System.exit(2);
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            System.out.println("[INFO] Connection established successfully.");

            // Health check query (configurable via env MYSQL_HEALTHCHECK_QUERY; default SELECT 1)
            String healthQuery = getEnvOrDefault("MYSQL_HEALTHCHECK_QUERY", "SELECT 1");
            String expectedRaw = System.getenv("MYSQL_HEALTHCHECK_EXPECT");
            System.out.println("[INFO] Running health check query: " + healthQuery);
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery(healthQuery)) {
                    if (rs.next()) {
                        String firstCol = null;
                        try {
                            firstCol = rs.getString(1);
                        } catch (Exception ignore) {}
                        System.out.println("[INFO] Health check first column: " + firstCol);
                        boolean pass;
                        if (expectedRaw != null && !expectedRaw.isBlank()) {
                            pass = valuesMatch(firstCol, expectedRaw);
                        } else if ("select 1".equalsIgnoreCase(healthQuery.trim())) {
                            // Backward-compatible default
                            int result = rs.getInt(1);
                            pass = (result == 1);
                            if (!pass) {
                                System.err.println("[WARN] Unexpected health check result: " + result);
                            }
                        } else {
                            // If no expected provided, any row means success
                            pass = true;
                        }
                        if (pass) {
                            System.out.println("[INFO] MySQL health check passed.");
                            System.exit(0);
                        } else {
                            System.err.println("[WARN] MySQL health check failed based on expectation.");
                            System.exit(3);
                        }
                    } else {
                        System.err.println("[WARN] Health check returned no rows.");
                        System.exit(3);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to connect or query MySQL.");
            printSqlException(e);
            System.exit(1);
        }
    }

    private static String getEnvOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int parseIntOrDefault(String v, int def) {
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String redactPassword(String url) {
        if (url == null) return null;
        // Very basic redaction if password is present as URL param (password=...)
        String marker = "password=";
        int idx = url.toLowerCase().indexOf(marker);
        if (idx < 0) return url;
        int end = url.indexOf('&', idx);
        if (end < 0) end = url.length();
        return url.substring(0, idx + marker.length()) + "******" + url.substring(end);
    }

    private static boolean valuesMatch(String actual, String expected) {
        if (actual == null) return false;
        String a = actual.trim();
        String eStr = expected == null ? "" : expected.trim();
        if (a.equals(eStr)) return true;
        try {
            double da = Double.parseDouble(a);
            double de = Double.parseDouble(eStr);
            return Double.compare(da, de) == 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static void printSqlException(SQLException e) {
        System.err.println("Message: " + e.getMessage());
        System.err.println("SQLState: " + e.getSQLState());
        System.err.println("ErrorCode: " + e.getErrorCode());
        SQLException next = e.getNextException();
        int depth = 0;
        while (next != null && depth < 5) {
            System.err.println("Caused by -> Message: " + next.getMessage() + ", SQLState: " + next.getSQLState() + ", ErrorCode: " + next.getErrorCode());
            next = next.getNextException();
            depth++;
        }
        e.printStackTrace();
    }
}