package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable, thread-safe registry of named connection pools.
 *
 * This is the piece you drop into ANY project as-is. It doesn't know or
 * care whether "orders-db" is MySQL and "reporting-db" is Postgres and
 * "legacy-db" is Oracle - each pool is just a DbConfig away.
 *
 *   registry.register(DbConfig.builder()
 *       .poolName("orders-db").vendor(DbVendor.POSTGRES)
 *       .host("orders.internal").port(5432).dbName("orders")
 *       .username("app").password("secret").build());
 *
 *   try (Connection c = registry.getConnection("orders-db")) {
 *       ...
 *   }
 *
 * WHY A REGISTRY (vs. one pool per app): real systems routinely talk to
 * more than one database - a primary OLTP DB, a reporting/read-replica DB,
 * maybe a legacy system during a migration. Each needs its OWN pool with
 * its own sizing, because they have independent load profiles and DB-side
 * connection limits. A registry keyed by pool name is the standard way to
 * manage that without every caller wiring up HikariConfig by hand.
 */
public final class ConnectionPoolRegistry {

    private static final ConnectionPoolRegistry INSTANCE = new ConnectionPoolRegistry();

    // ConcurrentHashMap - safe for concurrent register()/getConnection() calls
    // from multiple threads without external synchronization.
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    private ConnectionPoolRegistry() {}

    public static ConnectionPoolRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Loads every pool block from a classpath properties file (see
     * db-pools.properties for the format) and registers all of them in one
     * call. This is the intended app-startup entry point - onboarding a new
     * DB from here on is purely a config file edit.
     */
    public void registerAllFromClasspath(String resourceName) {
        DbConfigLoader.loadFromClasspath(resourceName).values().forEach(this::register);
    }

    /**
     * Registers (creates) a pool under config.getPoolName(). Idempotent-safe
     * to call once per pool name at startup; calling it twice for the same
     * name replaces the old pool (closing it first) rather than leaking it.
     */
    public void register(DbConfig config) {
        pools.compute(config.getPoolName(), (name, existing) -> {
            if (existing != null && !existing.isClosed()) {
                existing.close();
            }
            return buildDataSource(config);
        });
    }

    /**
     * Lazily registers using the given config if the pool doesn't exist yet.
     * Handy when multiple parts of an app might be first to need a pool and
     * you don't want a separate explicit startup step.
     */
    public HikariDataSource getOrRegister(DbConfig config) {
        return pools.computeIfAbsent(config.getPoolName(), name -> buildDataSource(config));
    }

    private HikariDataSource buildDataSource(DbConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(config.getPoolName());
        hikariConfig.setDriverClassName(config.getVendor().getDriverClassName());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Borrow a connection from the named pool.
     * Caller must close() it (try-with-resources) to return it to the pool.
     */
    public Connection getConnection(String poolName) throws SQLException {
        HikariDataSource ds = pools.get(poolName);
        if (ds == null) {
            throw new IllegalStateException(
                    "No pool registered under name '" + poolName + "'. Call register(DbConfig) first.");
        }
        return ds.getConnection();
    }

    public HikariDataSource getPool(String poolName) {
        HikariDataSource ds = pools.get(poolName);
        if (ds == null) {
            throw new IllegalStateException("No pool registered under name '" + poolName + "'.");
        }
        return ds;
    }

    /** Shuts down one named pool (e.g. a service being decommissioned at runtime). */
    public void shutdown(String poolName) {
        HikariDataSource ds = pools.remove(poolName);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    /** Shuts down every pool - call this from your app's shutdown hook. */
    public void shutdownAll() {
        pools.forEach((name, ds) -> {
            if (!ds.isClosed()) ds.close();
        });
        pools.clear();
    }
}
