package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a pool-definitions properties file (see db-pools.properties for the
 * format) into a Map<poolName, DbConfig>.
 *
 * THIS is the piece that makes onboarding a new DB a config-only change:
 * every line is just "<poolName>.<field>=value" - add a new poolName prefix
 * and this loader builds a fully-formed DbConfig for it automatically, no
 * Java changes required.
 */
public final class DbConfigLoader {

    // Matches ${ENV_VAR} or ${ENV_VAR:defaultValue}
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)(:([^}]*))?}");

    private DbConfigLoader() {}

    public static Map<String, DbConfig> loadFromClasspath(String resourceName) {
        try (InputStream in = DbConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resourceName);
            }
            Properties props = new Properties();
            props.load(in);
            return parse(props);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load pool config from classpath: " + resourceName, e);
        }
    }

    public static Map<String, DbConfig> loadFromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            return parse(props);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load pool config from file: " + path, e);
        }
    }

    /**
     * Groups flat "<poolName>.<field>=value" properties into one DbConfig
     * per distinct poolName prefix, then hands each group's fields to
     * DbConfig.Builder.
     */
    static Map<String, DbConfig> parse(Properties props) {
        // pool name -> (field name -> raw value)
        Map<String, Map<String, String>> grouped = new TreeMap<>();

        for (String key : props.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot < 0) continue; // ignore malformed/unrelated keys rather than failing the whole file
            String poolName = key.substring(0, dot);
            String field = key.substring(dot + 1);
            grouped.computeIfAbsent(poolName, n -> new LinkedHashMap<>())
                    .put(field, resolvePlaceholders(props.getProperty(key)));
        }

        Map<String, DbConfig> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), buildConfig(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static DbConfig buildConfig(String poolName, Map<String, String> fields) {
        DbConfig.Builder builder = DbConfig.builder().poolName(poolName);

        String vendorName = require(fields, poolName, "vendor");
        builder.vendor(DbVendor.valueOf(vendorName.trim().toUpperCase()));

        String jdbcUrl = fields.get("jdbcUrl");
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            builder.jdbcUrl(jdbcUrl);
        } else {
            builder.host(require(fields, poolName, "host"));
            builder.port(Integer.parseInt(require(fields, poolName, "port")));
            builder.dbName(require(fields, poolName, "dbName"));
        }

        builder.username(fields.getOrDefault("username", ""));
        builder.password(fields.getOrDefault("password", ""));

        if (fields.containsKey("maxPoolSize")) builder.maxPoolSize(Integer.parseInt(fields.get("maxPoolSize")));
        if (fields.containsKey("minIdle")) builder.minIdle(Integer.parseInt(fields.get("minIdle")));
        if (fields.containsKey("connectionTimeoutMs")) builder.connectionTimeoutMs(Long.parseLong(fields.get("connectionTimeoutMs")));
        if (fields.containsKey("idleTimeoutMs")) builder.idleTimeoutMs(Long.parseLong(fields.get("idleTimeoutMs")));
        if (fields.containsKey("maxLifetimeMs")) builder.maxLifetimeMs(Long.parseLong(fields.get("maxLifetimeMs")));

        return builder.build();
    }

    private static String require(Map<String, String> fields, String poolName, String field) {
        String value = fields.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Pool '" + poolName + "' is missing required property '" + poolName + "." + field + "'");
        }
        return value;
    }

    /**
     * Resolves ${ENV_VAR} / ${ENV_VAR:default} in a raw property value against
     * actual environment variables. Lets secrets (passwords, etc.) live in
     * env vars / a secrets manager instead of in this file.
     */
    private static String resolvePlaceholders(String raw) {
        if (raw == null || !raw.contains("${")) return raw;

        Matcher matcher = ENV_PLACEHOLDER.matcher(raw);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultValue = matcher.group(3); // null if no ":default" was given
            String resolved = System.getenv(envVar);
            if (resolved == null) {
                if (defaultValue != null) {
                    resolved = defaultValue;
                } else {
                    throw new IllegalStateException(
                            "Environment variable '" + envVar + "' is referenced in config but not set, "
                                    + "and no default was provided (use ${" + envVar + ":default} to allow one)");
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}