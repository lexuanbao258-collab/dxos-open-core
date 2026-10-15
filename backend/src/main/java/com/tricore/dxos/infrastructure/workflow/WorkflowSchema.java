package com.tricore.dxos.infrastructure.workflow;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/** Validated PostgreSQL namespace and fail-closed connection checks, shared by JDBC and Flyway. */
final class WorkflowSchema {
    private final String name;

    WorkflowSchema(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}") || name.startsWith("pg_")
                || Set.of("public", "extensions", "information_schema").contains(name)) {
            throw new IllegalArgumentException("Workflow requires an explicit private schema identifier");
        }
        this.name = name;
    }

    String name() {
        return name;
    }

    String quoted() {
        return "\"" + name + "\"";
    }

    void validateUrl(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("Workflow requires a PostgreSQL JDBC URL");
        }
        if (url.substring("jdbc:postgresql:".length()).contains("@")) {
            throw new IllegalArgumentException("Credentials must be supplied separately");
        }
        String query = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        boolean foundSchema = false;
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (key.equalsIgnoreCase("user") || key.equalsIgnoreCase("password")) {
                throw new IllegalArgumentException("Credentials must be supplied separately");
            }
            if (key.equals("currentSchema")) {
                if (foundSchema || !safePath(value)) {
                    throw new IllegalArgumentException("JDBC schema override rejected");
                }
                foundSchema = true;
            }
            if (key.equals("options") && value.toLowerCase(java.util.Locale.ROOT).contains("search_path")) {
                throw new IllegalArgumentException("JDBC search_path override rejected");
            }
        }
    }

    private boolean safePath(String path) {
        String[] entries = path.split(",", -1);
        if (entries.length == 0 || !name.equals(entries[0].trim().replace("\"", ""))) {
            return false;
        }
        for (String entry : entries) {
            String token = entry.trim().replace("\"", "");
            if (!Set.of(name, "pg_catalog", "pg_temp").contains(token)) {
                return false;
            }
        }
        return true;
    }

    void verify(Connection connection) throws SQLException {
        validateUrl(connection.getMetaData().getURL());
        if (!name.equals(connection.getSchema())) {
            throw new SQLException("Workflow schema mismatch");
        }
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT pg_catalog.current_setting('search_path')")) {
            if (!rows.next() || !safePath(rows.getString(1))) {
                throw new SQLException("Workflow search_path mismatch");
            }
        }
    }

    DataSource guard(DataSource source) {
        return guard(source, connection -> {});
    }

    DataSource guard(DataSource source, java.util.function.Consumer<Connection> configurationCheck) {
        return new DelegatingDataSource(java.util.Objects.requireNonNull(source, "dataSource")) {
            private Connection checked(Connection connection) throws SQLException {
                try {
                    configurationCheck.accept(connection);
                    WorkflowEventTriggerGuard.REVIEWED.verify(connection, name);
                    return connection;
                } catch (SQLException | RuntimeException failure) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // Reject before handing the connection to any writer.
                    }
                    throw new SQLException("Workflow connection rejected");
                }
            }

            @Override
            public Connection getConnection() throws SQLException {
                return checked(super.getConnection());
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return checked(super.getConnection(username, password));
            }
        };
    }
}
