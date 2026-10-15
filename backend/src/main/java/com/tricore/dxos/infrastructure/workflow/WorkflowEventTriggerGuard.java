package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.configuration.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Live drift checks against reviewed catalog evidence; native binaries remain a platform trust assumption. */
final class WorkflowEventTriggerGuard {
    private static final ObjectMapper JSON = new ObjectMapper();
    static final JsonNode MANIFEST = loadManifest();
    static final WorkflowEventTriggerGuard REVIEWED = new WorkflowEventTriggerGuard(MANIFEST);
    private final JsonNode manifest;

    WorkflowEventTriggerGuard(JsonNode manifest) {
        if (manifest.path("formatVersion").asInt() != 1
                || manifest.path("inventory").path("triggers").size() != 6
                || manifest.path("inventory").path("functions").size() != 8
                || manifest.path("helperEvidence").size() != 2) {
            throw new IllegalArgumentException("Workflow event manifest rejected");
        }
        this.manifest = manifest.deepCopy();
    }

    void verify(Connection connection, String schema) throws SQLException {
        verify(connection, schema, true);
    }

    /** Cloud catalog acceptance before provisioning: never permits the disposable-local bypass. */
    void verifyReadOnly(Connection connection, String schema) throws SQLException {
        verify(connection, schema, false);
    }

    private void verify(Connection connection, String schema, boolean runtime) throws SQLException {
        try {
            if (runtime) {
                new WorkflowSchema(schema).verify(connection);
            } else {
                if (connection.getAutoCommit()) throw new IllegalArgumentException();
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(15);
                    try (var rows = statement.executeQuery("""
                            SELECT pg_catalog.current_setting('transaction_read_only'),
                                   pg_catalog.current_setting('search_path'),
                                   pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NOT NULL,
                                   pg_catalog.current_schema()
                            """)) {
                        if (!rows.next() || !"on".equals(rows.getString(1))
                                || !(schema + ",pg_catalog,pg_temp").equals(rows.getString(2).replace(" ", ""))
                                || (rows.getBoolean(3) && !schema.equals(rows.getString(4)))) {
                            throw new IllegalArgumentException();
                        }
                    }
                }
            }
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("""
                        SELECT pg_catalog.current_setting('session_replication_role') = 'origin',
                               current_user = session_user,
                               pg_catalog.current_setting('event_triggers', true) = 'on'
                        """)) {
                    if (!rows.next() || !rows.getBoolean(1) || !rows.getBoolean(2) || !rows.getBoolean(3)) {
                        throw new IllegalArgumentException();
                    }
                }
            }
            if (runtime && emptyLocal(connection)) return;
            List<String> signatures = new ArrayList<>();
            manifest.path("helperEvidence").forEach(helper -> signatures.add(helper.path("signature").asText()));
            JsonNode live = JSON.valueToTree(WorkflowEventTriggerReview.liveMetadata(connection, signatures));
            if (!schema.equals(manifest.path("schema").asText())
                    || !manifest.path("serverVersion").asText().equals(live.path("session").path("version").asText())
                    || !inventory(live).equals(manifest.path("inventory"))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            // Do not include SQL, function definitions, configuration values, or driver causes in errors.
            throw new SQLException("Workflow event-trigger guard rejected", "42501");
        }
    }

    static boolean emptyLocal(Connection connection) throws SQLException {
        if (!"true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"))) return false;
        URI endpoint = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));
        if (!Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(endpoint.getHost())) return false;
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("SELECT count(*) FROM pg_catalog.pg_event_trigger")) {
                return rows.next() && rows.getLong(1) == 0;
            }
        }
    }

    static ObjectNode inventory(JsonNode metadata) {
        ObjectNode result = JSON.createObjectNode();
        List<JsonNode> triggers = new ArrayList<>(), functions = new ArrayList<>();
        metadata.path("triggers").forEach(item -> {
            ObjectNode trigger = item.deepCopy();
            trigger.remove(List.of("functionOid", "matchesConfirmedMetadata"));
            triggers.add(trigger);
        });
        metadata.path("functions").forEach(item -> {
            ObjectNode function = item.deepCopy();
            function.remove("oid");
            List<JsonNode> dependencies = new ArrayList<>();
            function.path("catalogDependencies").forEach(itemDependency -> {
                ObjectNode dependency = itemDependency.deepCopy();
                dependency.remove("oid");
                dependencies.add(dependency);
            });
            function.set("catalogDependencies", JSON.valueToTree(dependencies.stream()
                    .sorted(Comparator.comparing(node -> node.path("catalog").asText() + "/" + node.path("identity").asText()
                            + "/" + node.path("kind").asText() + "/" + node.path("subId").asText())).toList()));
            List<JsonNode> acl = new ArrayList<>();
            function.path("effectiveAcl").forEach(acl::add);
            function.set("effectiveAcl", JSON.valueToTree(acl.stream().sorted(Comparator.comparing(node ->
                    node.path("grantee").asText() + "/" + node.path("grantor").asText() + "/"
                            + node.path("privilege").asText() + "/" + node.path("grantable").asText())).toList()));
            functions.add(function);
        });
        result.set("triggers", JSON.valueToTree(triggers.stream().sorted(
                Comparator.comparing(node -> node.path("name").asText())).toList()));
        result.set("functions", JSON.valueToTree(functions.stream().sorted(Comparator.comparing(node ->
                node.path("schema").asText() + "." + node.path("name").asText()
                        + "(" + node.path("identityArguments").asText() + ")")).toList()));
        return result;
    }

    static void verifyMigrations(Configuration configuration, Connection connection) throws SQLException {
        var locations = configuration.getLocations();
        if (locations.length != 1) throw new IllegalArgumentException("Workflow migration locations rejected");
        String location = locations[0].getDescriptor();
        if (location.startsWith("filesystem:")) {
            verifyBundle(Path.of(location.substring("filesystem:".length())));
        } else if (location.equals("classpath:db/migration") && emptyLocal(connection)) {
            for (var names = MANIFEST.path("migrations").fieldNames(); names.hasNext();) {
                String name = names.next();
                try (var resource = WorkflowEventTriggerGuard.class.getResourceAsStream("/db/migration/" + name)) {
                    if (resource == null) throw new IllegalArgumentException();
                    verifyDigest(name, new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                } catch (Exception failure) {
                    throw new IllegalArgumentException("Workflow migration resource rejected");
                }
            }
        } else {
            throw new IllegalArgumentException("Workflow migration locations rejected");
        }
    }

    static void verifyBundle(Path bundle) {
        try (var entries = Files.list(bundle)) {
            Set<String> names = entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> expected = new java.util.HashSet<>();
            MANIFEST.path("migrations").fieldNames().forEachRemaining(expected::add);
            if (!Files.isDirectory(bundle, LinkOption.NOFOLLOW_LINKS) || !names.equals(expected)) {
                throw new IllegalArgumentException();
            }
            for (String name : expected) {
                Path file = bundle.resolve(name);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
                verifyDigest(name, Files.readString(file));
            }
        } catch (Exception failure) {
            throw new IllegalArgumentException("Workflow migration bundle rejected");
        }
    }

    private static void verifyDigest(String name, String sql) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(sql.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
        if (!digest.equals(MANIFEST.path("migrations").path(name).asText())) throw new IllegalArgumentException();
    }

    private static JsonNode loadManifest() {
        try (var input = WorkflowEventTriggerGuard.class.getResourceAsStream("/workflow-event-trigger-manifest.json")) {
            if (input == null) throw new IllegalArgumentException();
            return JSON.readTree(input);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError("Workflow event manifest unavailable");
        }
    }
}
