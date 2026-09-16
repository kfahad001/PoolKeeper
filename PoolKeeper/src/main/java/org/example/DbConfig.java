package org.example;

/**
 * Everything needed to stand up ONE pool for ONE database, built fluently:
 *
 *   DbConfig cfg = DbConfig.builder()
 *       .poolName("orders-db")
 *       .vendor(DbVendor.POSTGRES)
 *       .host("orders.internal").port(5432).dbName("orders")
 *       .username("app_user").password("secret")
 *       .maxPoolSize(15)
 *       .build();
 *
 * Kept separate from the registry/pool classes so config can come from
 * anywhere - hardcoded (demo), a properties file, environment variables,
 * Spring @ConfigurationProperties, Vault, etc. - without touching pooling
 * logic at all.
 */
public final class DbConfig {

    private final String poolName;
    private final DbVendor vendor;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;

    private DbConfig(Builder b) {
        this.poolName = b.poolName;
        this.vendor = b.vendor;
        this.jdbcUrl = (b.explicitJdbcUrl != null)
                ? b.explicitJdbcUrl
                : b.vendor.buildUrl(b.host, b.port, b.dbName);
        this.username = b.username;
        this.password = b.password;
        this.maxPoolSize = b.maxPoolSize;
        this.minIdle = b.minIdle;
        this.connectionTimeoutMs = b.connectionTimeoutMs;
        this.idleTimeoutMs = b.idleTimeoutMs;
        this.maxLifetimeMs = b.maxLifetimeMs;
    }

    public String getPoolName() { return poolName; }
    public DbVendor getVendor() { return vendor; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getMinIdle() { return minIdle; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public long getMaxLifetimeMs() { return maxLifetimeMs; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String poolName;
        private DbVendor vendor;
        private String host;
        private int port;
        private String dbName;
        private String explicitJdbcUrl; // escape hatch: skip host/port/dbName and set the full URL directly
        private String username;
        private String password;

        // Sensible defaults - override only what your workload actually needs
        private int maxPoolSize = 10;
        private int minIdle = 2;
        private long connectionTimeoutMs = 30_000;
        private long idleTimeoutMs = 600_000;
        private long maxLifetimeMs = 1_800_000;

        public Builder poolName(String poolName) { this.poolName = poolName; return this; }
        public Builder vendor(DbVendor vendor) { this.vendor = vendor; return this; }
        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder dbName(String dbName) { this.dbName = dbName; return this; }
        public Builder jdbcUrl(String url) { this.explicitJdbcUrl = url; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder maxPoolSize(int n) { this.maxPoolSize = n; return this; }
        public Builder minIdle(int n) { this.minIdle = n; return this; }
        public Builder connectionTimeoutMs(long ms) { this.connectionTimeoutMs = ms; return this; }
        public Builder idleTimeoutMs(long ms) { this.idleTimeoutMs = ms; return this; }
        public Builder maxLifetimeMs(long ms) { this.maxLifetimeMs = ms; return this; }

        public DbConfig build() {
            if (poolName == null || poolName.isBlank()) {
                throw new IllegalStateException("poolName is required");
            }
            if (vendor == null) {
                throw new IllegalStateException("vendor is required");
            }
            if (explicitJdbcUrl == null && (host == null || dbName == null)) {
                throw new IllegalStateException("either jdbcUrl(...) or host()+dbName() must be set");
            }
            return new DbConfig(this);
        }
    }
}