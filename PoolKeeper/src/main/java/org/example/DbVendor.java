package org.example;


/**
 * Centralizes the two vendor-specific things every DB connection needs:
 * the driver class name, and how to build a JDBC URL.
 *
 * Adding support for a new DB vendor = adding one enum constant here.
 * Nothing else in the pooling code needs to change - that's the whole
 * point of factoring this out.
 */
public enum DbVendor {

    MYSQL("com.mysql.cj.jdbc.Driver") {
        public String buildUrl(String host, int port, String dbName) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, dbName);
        }
    },
    POSTGRES("org.postgresql.Driver") {
        public String buildUrl(String host, int port, String dbName) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
        }
    },
    ORACLE("oracle.jdbc.OracleDriver") {
        public String buildUrl(String host, int port, String dbName) {
            // dbName here is the SID or service name, e.g. "ORCLPDB1"
            return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, dbName);
        }
    },
    SQL_SERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver") {
        public String buildUrl(String host, int port, String dbName) {
            return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false", host, port, dbName);
        }
    },
    H2_MEM("org.h2.Driver") {
        public String buildUrl(String host, int port, String dbName) {
            // in-memory H2 ignores host/port - kept here purely so demos/tests
            // don't need a real external DB
            return String.format("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1", dbName);
        }
    };

    private final String driverClassName;

    DbVendor(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public abstract String buildUrl(String host, int port, String dbName);
}
