package org.example;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class Demo {

    public static void main(String[] args) throws Exception {
        ConnectionPoolRegistry registry = ConnectionPoolRegistry.getInstance();

        // Everything about orders-db and reporting-db - vendor, host, pool
        // sizing - comes from src/main/resources/db-pools.properties.
        // Onboarding a THIRD db means adding a block to that file only;
        // this line of code doesn't change.
        registry.registerAllFromClasspath("db-pools.properties");

        System.out.println("Registered pools from config file: orders-db, reporting-db");

        seedAndQuery("orders-db", "orders", "order_id");
        seedAndQuery("reporting-db", "daily_totals", "report_id");

        printPoolStats("orders-db");
        printPoolStats("reporting-db");

        registry.shutdownAll();
        System.out.println("All pools shut down.");
    }

    private static void seedAndQuery(String poolName, String table, String idColumn) throws Exception {
        ConnectionPoolRegistry registry = ConnectionPoolRegistry.getInstance();

        try (Connection conn = registry.getConnection(poolName);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + table + "(" + idColumn + " INT PRIMARY KEY, note VARCHAR(100))");
            st.execute("MERGE INTO " + table + " KEY(" + idColumn + ") VALUES (1, 'from pool " + poolName + "')");
        }

        try (Connection conn = registry.getConnection(poolName);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT note FROM " + table + " WHERE " + idColumn + " = 1")) {
            if (rs.next()) {
                System.out.printf("[%s] query result: %s%n", poolName, rs.getString("note"));
            }
        }
    }

    private static void printPoolStats(String poolName) {
        HikariDataSource ds = ConnectionPoolRegistry.getInstance().getPool(poolName);
        var mx = ds.getHikariPoolMXBean();
        System.out.printf("[%s] active=%d idle=%d total=%d%n",
                poolName, mx.getActiveConnections(), mx.getIdleConnections(), mx.getTotalConnections());
    }
}