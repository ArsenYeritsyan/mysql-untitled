package org.example;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
        // - MYSQL_CONNECT_TIMEOUT_MS (default: 5000)
        // - MYSQL_SOCKET_TIMEOUT_MS (default: 10000)
        // - MYSQL_RETRIES (default: 5)
        // - MYSQL_RETRY_DELAY_MS (default: 1000)
        // - MYSQL_WAIT_FOR_HOST (default: false)
        // - MYSQL_WAIT_TIMEOUT_MS (default: 30000)

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

        // Optional: timeouts via env (ms)
        int connectTimeoutMs = parseIntOrDefault(System.getenv("MYSQL_CONNECT_TIMEOUT_MS"), 5000);
        int socketTimeoutMs = parseIntOrDefault(System.getenv("MYSQL_SOCKET_TIMEOUT_MS"), 10000);

        // Append timeouts if not present in URL
        if (!jdbcUrl.toLowerCase().contains("connecttimeout=")) {
            String sep = jdbcUrl.contains("?") ? "&" : "?";
            jdbcUrl = jdbcUrl + sep + "connectTimeout=" + connectTimeoutMs;
        }
        if (!jdbcUrl.toLowerCase().contains("sockettimeout=")) {
            String sep2 = jdbcUrl.contains("?") ? "&" : "?";
            jdbcUrl = jdbcUrl + sep2 + "socketTimeout=" + socketTimeoutMs;
        }

        int retries = parseIntOrDefault(System.getenv("MYSQL_RETRIES"), 5);
        int delayMs = parseIntOrDefault(System.getenv("MYSQL_RETRY_DELAY_MS"), 1000);
        boolean waitForHost = "true".equalsIgnoreCase(getEnvOrDefault("MYSQL_WAIT_FOR_HOST", "false"));
        int waitTimeoutMs = parseIntOrDefault(System.getenv("MYSQL_WAIT_TIMEOUT_MS"), 30000);

        System.out.println("[INFO] Attempting MySQL connection...");
        System.out.println("[INFO] JDBC URL: " + redactPassword(jdbcUrl));
        System.out.println("[INFO] User: " + user);
        System.out.println("[INFO] Host: " + host + ":" + port);
        System.out.println("[INFO] connectTimeoutMs=" + connectTimeoutMs + ", socketTimeoutMs=" + socketTimeoutMs);

        // Load driver explicitly (often optional, but safe across environments)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] MySQL JDBC Driver not found. Did you add mysql-connector-j dependency?");
            e.printStackTrace();
            System.exit(2);
        }

        // Optional preflight: wait for host:port to accept TCP
        if (waitForHost) {
            System.out.println("[INFO] Waiting for MySQL host:port to become available (timeout " + waitTimeoutMs + " ms)...");
            long deadline = System.currentTimeMillis() + waitTimeoutMs;
            boolean ok = false;
            while (System.currentTimeMillis() < deadline) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(host, Integer.parseInt(port)), Math.min(2000, connectTimeoutMs));
                    ok = true;
                    break;
                } catch (Exception ignored) {
                    try { Thread.sleep(250); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            if (!ok) {
                System.err.println("[WARN] MySQL host:port didn't become available within wait timeout; continuing to JDBC attempts.");
            } else {
                System.out.println("[INFO] Host:port is reachable, proceeding with JDBC.");
            }
        }

        // Retry loop for connection and health check
        SQLException lastSqlException = null;
        for (int attempt = 1; attempt <= Math.max(1, retries); attempt++) {
            if (attempt > 1) {
                System.out.println("[INFO] Retry attempt " + attempt + " of " + retries + "...");
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
                lastSqlException = e;
                System.err.println("[ERROR] Attempt " + attempt + " failed to connect or query MySQL.");
                printSqlException(e);
                if (attempt < Math.max(1, retries)) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
            }
        }

        System.err.println("[ERROR] All attempts to connect/query MySQL have failed.");
        System.err.println("[HINT] Check that the MySQL server is running and reachable at " + host + ":" + port + ", credentials are correct, and that firewalls or Docker networking allow access. If needed, provide a full MYSQL_URL.");
        if (lastSqlException != null) {
            printSqlException(lastSqlException);
        }
        System.exit(1);
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