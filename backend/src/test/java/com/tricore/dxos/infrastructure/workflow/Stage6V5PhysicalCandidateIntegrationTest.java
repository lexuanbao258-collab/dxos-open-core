package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
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

    private void fixture() throws SQLException {
        execute("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        execute("INSERT INTO " + schema + ".tenant_memberships(tenant_id,actor_id,active) "
                + "VALUES('tenant-a','member-a',true)");
        execute("INSERT INTO " + schema + ".resource_registry "
                + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                + "VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
    }

    private void rejects(String sql, String state) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            execute(sql);
            fail("Expected database rejection");
        } catch (SQLException rejected) {
            assertThat(rejected.getSQLState()).isEqualTo(state);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
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
