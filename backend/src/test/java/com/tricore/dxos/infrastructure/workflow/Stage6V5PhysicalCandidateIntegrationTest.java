package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in D2 physical candidate verification. Runs ONLY on a disposable PG17 database.
 * One never-committed JDBC transaction creates a unique schema; finally rolls it back.
 * Does not approve V5 Flyway, SQL privileges, Policy APIs, Workflow HTTP or Identity.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6V5PhysicalCandidateIntegrationTest {
    private Connection connection;
    private String schema;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")),
                "This test requires explicit disposable DB approval");
        String url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI uri = URI.create(url.substring(5));
        assumeTrue(("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                        && uri.getPath().matches("/stage6_disposable_[a-zA-Z0-9_]+"),
                "Only a localhost disposable database is accepted");
        String user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        String password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null,
                "Test account must be supplied expressly");
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        try (var s = connection.createStatement();
             ResultSet r = s.executeQuery("SHOW server_version_num")) {
            assertThat(r.next()).isTrue();
            assertThat(Integer.parseInt(r.getString(1))).isBetween(170000, 179999);
        }
        try (var s = connection.createStatement();
             ResultSet r = s.executeQuery("SELECT pg_catalog.current_database()")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).matches("stage6_disposable_[a-zA-Z0-9_]+");
        }

        schema = "s6_v5_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        String v3 = Files.readString(Path.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql"));
        String v5 = Files.readString(Path.of("src/test/resources/stage6/V5_acl_physical_candidate.sql"));
        execute(v3.replace("${flyway:defaultSchema}", schema));
        execute(v5.replace("${flyway:defaultSchema}", schema));
    }

    @AfterEach
    void rollback() throws Exception {
        if (connection != null) {
            try { connection.rollback(); }
            finally { connection.close(); }
        }
    }

    @Test
    void candidateCreatesExactlySixPolicyTablesAndTwelveProtectionTriggers() throws Exception {
        assertThat(count("SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname='"
                + schema + "' AND tablename IN ('tenant_guards','tenant_memberships',"
                + "'resource_registry','resource_acl','workflow_definition_bindings','workflow_policy_audit')"))
                .isEqualTo(6);
        assertThat(count("SELECT count(*) FROM pg_catalog.pg_trigger t "
                + "JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace "
                + "WHERE n.nspname='" + schema + "' AND t.tgname LIKE 's6_%' AND NOT t.tgisinternal"))
                .isEqualTo(12);
    }

    @Test
    void durableKeysCannotBeRewrittenDeletedOrRevived() throws Exception {
        fixture();
        rejects("UPDATE " + schema + ".tenant_guards SET tenant_id='tenant-other' WHERE tenant_id='tenant-a'", "23514");
        rejects("DELETE FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a'", "23514");
        rejects("UPDATE " + schema + ".resource_registry SET resource_id='res-other' WHERE resource_id='res-a'", "23514");
        rejects("DELETE FROM " + schema + ".resource_registry WHERE resource_id='res-a'", "23514");
        execute("UPDATE " + schema + ".resource_registry "
                + "SET lifecycle='TOMBSTONED',tombstoned_at=now() WHERE resource_id='res-a'");
        rejects("UPDATE " + schema + ".resource_registry "
                + "SET lifecycle='ACTIVE',tombstoned_at=NULL WHERE resource_id='res-a'", "23514");
        rejects("TRUNCATE " + schema + ".workflow_policy_audit", "23514");
    }

    @Test
    void aclIdentityAndTenantMembershipForeignKeyStayBound() throws Exception {
        fixture();
        rejects("INSERT INTO " + schema + ".resource_acl "
                + "(tenant_id,resource_type,resource_id,actor_id,operation,granted) "
                + "VALUES('tenant-a','item','res-a','other-actor','workflow:create',true)", "23503");
        execute("INSERT INTO " + schema + ".resource_acl "
                + "(tenant_id,resource_type,resource_id,actor_id,operation,granted) "
                + "VALUES('tenant-a','item','res-a','member-a','workflow:create',true)");
        rejects("UPDATE " + schema + ".resource_acl SET actor_id='other-actor'", "23514");
        rejects("INSERT INTO " + schema + ".workflow_definition_bindings "
                + "(tenant_id,resource_type,definition_id,definition_version,active) "
                + "VALUES('tenant-a','item','missing-definition',7,true)", "23503");
    }

    @Test
    void policyAuditCannotBeUpdatedDeletedOrContainEmptyChangeSet() throws Exception {
        fixture();
        String audit = "INSERT INTO " + schema + ".workflow_policy_audit "
                + "(audit_event_id,tenant_id,previous_acl_revision,resulting_acl_revision,"
                + "actor_id,actor_source_ref,action,change_set) VALUES "
                + "('00000000-0000-0000-0000-000000000001','tenant-a',NULL,0,"
                + "'member-a','operator-confirmed','tenant-bootstrap','[{\"tenant\":\"tenant-a\"}]'::jsonb)";
        execute(audit);
        rejects("UPDATE " + schema + ".workflow_policy_audit SET action='tampered'", "23514");
        rejects("DELETE FROM " + schema + ".workflow_policy_audit", "23514");
        rejects("UPDATE " + schema + ".tenant_guards SET acl_revision=-1", "23514");
        rejects("INSERT INTO " + schema + ".workflow_policy_audit "
                + "(audit_event_id,tenant_id,previous_acl_revision,resulting_acl_revision,"
                + "actor_id,actor_source_ref,action,change_set) VALUES "
                + "('00000000-0000-0000-0000-000000000002','tenant-a',0,1,"
                + "'member-a','operator-confirmed','no-op','[]'::jsonb)", "23514");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void globalResourceKeyCannotBeRegisteredInAnotherTenant(boolean tombstoned) throws Exception {
        fixture();
        if (tombstoned) tombstoneResource();
        rejects("INSERT INTO " + schema + ".resource_registry "
                + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                + "VALUES('item','res-a','tenant-b','member-b','ACTIVE')",
                "23505", "unique constraint \"resource_registry_pkey\"");
        assertThat(count("SELECT count(*) FROM " + schema + ".resource_registry "
                + "WHERE resource_type='item' AND resource_id='res-a' "
                + "AND tenant_id='tenant-a' AND owner_actor_id='member-a'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM " + schema + ".resource_registry WHERE tenant_id='tenant-b'")).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deleteThenRecreateCannotReuseActiveOrTombstonedIdentity(boolean tombstoned) throws Exception {
        fixture();
        if (tombstoned) tombstoneResource();
        String recreate = "INSERT INTO " + schema + ".resource_registry "
                + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                + "VALUES('item','res-a','tenant-b','member-b','ACTIVE')";
        rejects("DELETE FROM " + schema + ".resource_registry WHERE resource_type='item' AND resource_id='res-a'; "
                + recreate, "23514", "protected Stage 6 policy identities cannot be deleted");
        rejects(recreate, "23505", "unique constraint \"resource_registry_pkey\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "upsert"})
    void crossTenantRebindingCannotChangeResourceAclOrMembership(String operation) throws Exception {
        fixture();
        execute("INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','res-a','member-a','workflow:read',true)");
        String mutation = operation.equals("update")
                ? "UPDATE " + schema + ".resource_registry SET tenant_id='tenant-b',owner_actor_id='member-b' "
                    + "WHERE resource_type='item' AND resource_id='res-a'"
                : "INSERT INTO " + schema + ".resource_registry "
                    + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                    + "VALUES('item','res-a','tenant-b','member-b','ACTIVE') "
                    + "ON CONFLICT(resource_type,resource_id) DO UPDATE "
                    + "SET tenant_id=EXCLUDED.tenant_id,owner_actor_id=EXCLUDED.owner_actor_id";
        rejects(mutation, "23514", "immutable resource key, tenant or tombstone");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            resource_type | 'renamed-item'
            resource_id   | 'res-other'
            tenant_id     | 'tenant-b'
            created_at    | created_at + interval '1 second'
            """)
    void registryIdentityFieldsAreImmutableWithoutAclChildren(String field, String value) throws Exception {
        fixture();
        assertThat(count("SELECT count(*) FROM " + schema + ".resource_acl")).isZero();
        assertThat(count("SELECT count(*) FROM " + schema + ".workflow_instances")).isZero();
        rejects("UPDATE " + schema + ".resource_registry SET " + field + "=" + value
                + " WHERE resource_type='item' AND resource_id='res-a'",
                "23514", "immutable resource key, tenant or tombstone");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            tenant_guards                | tenant_id='tenant-other'      | immutable tenant key or decreasing ACL revision
            tenant_memberships           | tenant_id='tenant-b'          | membership identity is immutable
            tenant_memberships           | actor_id='member-b'           | membership identity is immutable
            resource_acl                 | tenant_id='tenant-b'          | ACL identity is immutable
            resource_acl                 | resource_type='renamed-item'  | ACL identity is immutable
            resource_acl                 | resource_id='res-other'       | ACL identity is immutable
            resource_acl                 | actor_id='member-b'           | ACL identity is immutable
            resource_acl                 | operation='workflow:create'   | ACL identity is immutable
            workflow_definition_bindings | tenant_id='tenant-b'          | definition binding identity is immutable
            workflow_definition_bindings | resource_type='renamed-item'  | definition binding identity is immutable
            workflow_definition_bindings | definition_id='other'         | definition binding identity is immutable
            workflow_definition_bindings | definition_version=8          | definition binding identity is immutable
            """)
    void guardMembershipAclAndBindingIdentityKeysAreImmutable(String table, String assignment, String reason) throws Exception {
        fixture();
        execute("INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','res-a','member-a','workflow:read',true)");
        execute("INSERT INTO " + schema + ".workflow_definitions(definition_id,definition_version,initial_state) "
                + "VALUES('publication',7,'draft'),('publication',8,'draft'),('other',7,'draft')");
        execute("INSERT INTO " + schema + ".workflow_definition_states "
                + "VALUES('publication',7,'draft'),('publication',8,'draft'),('other',7,'draft')");
        execute("INSERT INTO " + schema + ".workflow_definition_bindings "
                + "VALUES('tenant-a','item','publication',7,true)");
        String target = table.equals("tenant_memberships")
                ? "tenant_id='tenant-a' AND actor_id='member-a'" : "tenant_id='tenant-a'";
        assertThat(count("SELECT count(*) FROM " + schema + "." + table + " WHERE " + target)).isEqualTo(1);
        rejects("UPDATE " + schema + "." + table + " SET " + assignment + " WHERE " + target, "23514", reason);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            lifecycle='ACTIVE',tombstoned_at=NULL
            tombstoned_at=tombstoned_at + interval '1 second'
            tombstoned_at=NULL
            """)
    void tombstoneCannotBeReversedClearedOrRetimestamped(String assignment) throws Exception {
        fixture();
        tombstoneResource();
        rejects("UPDATE " + schema + ".resource_registry SET " + assignment
                + " WHERE resource_type='item' AND resource_id='res-a'",
                "23514", "immutable resource key, tenant or tombstone");
    }

    private void tombstoneResource() throws SQLException {
        String identity = "SELECT jsonb_build_array(resource_type,resource_id,tenant_id,owner_actor_id,created_at)::text "
                + "FROM " + schema + ".resource_registry WHERE resource_type='item' AND resource_id='res-a'";
        String before = text(identity);
        execute("UPDATE " + schema + ".resource_registry SET lifecycle='TOMBSTONED',tombstoned_at=clock_timestamp() "
                + "WHERE resource_type='item' AND resource_id='res-a'");
        assertThat(text(identity)).as("tombstoning preserves the original identity and owner").isEqualTo(before);
        assertThat(count("SELECT count(*) FROM " + schema + ".resource_registry WHERE resource_type='item' "
                + "AND resource_id='res-a' AND lifecycle='TOMBSTONED' AND tombstoned_at>=created_at")).isEqualTo(1);
    }

    private void fixture() throws SQLException {
        execute("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a'),('tenant-b')");
        execute("INSERT INTO " + schema + ".tenant_memberships(tenant_id,actor_id,active) "
                + "VALUES('tenant-a','member-a',true),('tenant-a','member-b',true),"
                + "('tenant-b','member-a',true),('tenant-b','member-b',true)");
        execute("INSERT INTO " + schema + ".resource_registry "
                + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                + "VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
    }

    private void rejects(String sql, String state, String... details) throws SQLException {
        Map<String, String> before = snapshot();
        Savepoint savepoint = connection.setSavepoint();
        try {
            execute(sql);
            fail("Expected database rejection");
        } catch (SQLException rejected) {
            assertThat(rejected.getSQLState()).isEqualTo(state);
            if (details.length > 0) assertThat(rejected.getMessage()).contains(details);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
        assertThat(snapshot()).as("persisted policy rows unchanged after rejected SQL: %s", sql).isEqualTo(before);
    }

    private Map<String, String> snapshot() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String table : List.of("tenant_guards", "tenant_memberships", "resource_registry", "resource_acl",
                "workflow_definition_bindings", "workflow_policy_audit")) {
            rows.put(table, text("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text "
                    + "FROM " + schema + "." + table + " t"));
        }
        return rows;
    }

    private String text(String sql) throws SQLException {
        try (var statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private int count(String sql) throws SQLException {
        try (var s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).isTrue();
            return r.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var s = connection.createStatement()) { s.execute(sql); }
    }
}
