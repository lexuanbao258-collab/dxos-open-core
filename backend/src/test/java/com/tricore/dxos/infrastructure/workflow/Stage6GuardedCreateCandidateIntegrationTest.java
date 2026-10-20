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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Disposable PG17 evidence for a SINGLE candidate CREATE SQL function.
 * Uses the same explicit opt-in envelope as the Stage 6.2 physical tests.
 * Every test creates V3+V5+function and synthetic data in one transaction,
 * then rolls back; NO live staging, Flyway, new Identity provider or HTTP route.
 * Not evidence of effective runtime-role grants or concurrent revoke races.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6GuardedCreateCandidateIntegrationTest {
    private Connection connection;
    private String schema;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        String url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI target = URI.create(url.substring(5));
        assumeTrue(("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()))
                && target.getPath().matches("/stage6_disposable_[a-zA-Z0-9_]+"));
        String user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        String password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null);
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(false);
        try (var s = connection.createStatement();
             var r = s.executeQuery("SELECT pg_catalog.current_setting('server_version_num')::integer,"
                     + " pg_catalog.current_database()")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getInt(1)).isBetween(170000, 179999);
            assertThat(r.getString(2)).matches("stage6_disposable_[a-zA-Z0-9_]+");
        }
        schema = "s6_create_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        for (String name : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql"
        }) {
            execute(Files.readString(Path.of(name)).replace("${flyway:defaultSchema}", schema));
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) {
            try { connection.rollback(); }
            finally { connection.close(); }
        }
    }

    @Test
    void authorizedCreateInsertsInitialInstanceWithoutHistory() throws Exception {
        fixture();
        String id = newId();
        Reply reply = create(id, "member-a", true, "res-a");
        assertThat(reply.outcome()).isEqualTo("SNAPSHOT");
        assertThat(reply.body()).contains(id, "NOT_STARTED");
        assertThat(rows("workflow_instances")).isEqualTo(1);
        assertThat(rows("workflow_transition_history")).isZero();
        assertThat(count("SELECT count(*) FROM " + schema
                + ".workflow_instances WHERE instance_id='" + id
                + "' AND runtime_version=0 AND current_state='draft'")).isEqualTo(1);
    }

    @Test
    void hiddenOrNonmemberResourceCannotCreateOrExposeBinding() throws Exception {
        fixture();
        assertThat(create(newId(), "unknown", true, "res-a").outcome()).isEqualTo("HIDDEN");
        assertThat(create(newId(), "member-a", false, "unmapped").outcome()).isEqualTo("HIDDEN");
        execute("UPDATE " + schema + ".tenant_memberships SET active=false WHERE actor_id='member-a'");
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("HIDDEN");
        assertThat(rows("workflow_instances")).isZero();
    }

    @Test
    void ownerWithoutExactCreateGrantIsForbidden() throws Exception {
        fixture();
        execute("UPDATE " + schema
                + ".resource_acl SET granted=false WHERE operation='workflow:create'");
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("FORBIDDEN");
        assertThat(rows("workflow_instances")).isZero();
    }

    @Test
    void visibleMemberWithOtherGrantButNoCreateGrantIsForbidden() throws Exception {
        fixture();
        execute("UPDATE " + schema
                + ".resource_registry SET owner_actor_id='other-member' WHERE resource_id='res-a'");
        execute("UPDATE " + schema
                + ".resource_acl SET granted=false WHERE operation='workflow:create'");
        execute("INSERT INTO " + schema + ".resource_acl "
                + "(tenant_id,resource_type,resource_id,actor_id,operation,granted) "
                + "VALUES('tenant-a','item','res-a','member-a','workflow:read',true)");
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("FORBIDDEN");
        assertThat(rows("workflow_instances")).isZero();
    }

    @Test
    void missingAuthorityIsForbiddenBeforeDefinitionOrBinding() throws Exception {
        fixture();
        assertThat(create(newId(), "member-a", false, "res-a").outcome()).isEqualTo("FORBIDDEN");
        assertThat(rows("workflow_instances")).isZero();
    }

    @Test
    void absentOrInactiveExactBindingIsHiddenAfterPositiveCreateGrant() throws Exception {
        fixture();
        execute("UPDATE " + schema + ".workflow_definition_bindings SET active=false");
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("HIDDEN");
        assertThat(rows("workflow_instances")).isZero();
    }

    @Test
    void revokedCreateGrantBeforeFreshCallDeniesWithoutNewWrite() throws Exception {
        fixture();
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("SNAPSHOT");
        execute("UPDATE " + schema + ".resource_acl SET granted=false WHERE operation='workflow:create'");
        assertThat(create(newId(), "member-a", true, "res-a").outcome()).isEqualTo("FORBIDDEN");
        assertThat(rows("workflow_instances")).isEqualTo(1);
    }

    @Test
    void duplicateIdAndMalformedProposalNeverCreateAnotherInstance() throws Exception {
        fixture();
        String id = newId();
        assertThat(create(id, "member-a", true, "res-a").outcome()).isEqualTo("SNAPSHOT");
        assertThat(create(id, "member-a", true, "res-a").outcome()).isEqualTo("UNAVAILABLE");
        String valid = proposal(newId(), "res-a");
        Reply extraField = call("member-a", true,
                valid.substring(0, valid.length() - 1) + "," + (char) 34 + "unexpected" + (char) 34 + ":true}");
        assertThat(extraField.outcome()).isEqualTo("UNAVAILABLE");
        assertThat(rows("workflow_instances")).isEqualTo(1);
    }

    private void fixture() throws SQLException {
        execute("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        execute("INSERT INTO " + schema + ".tenant_memberships(tenant_id,actor_id,active) "
                + "VALUES('tenant-a','member-a',true),('tenant-a','other-member',true)");
        execute("INSERT INTO " + schema + ".resource_registry "
                + "(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) "
                + "VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
        execute("INSERT INTO " + schema + ".resource_acl "
                + "(tenant_id,resource_type,resource_id,actor_id,operation,granted) "
                + "VALUES('tenant-a','item','res-a','member-a','workflow:create',true)");
        execute("INSERT INTO " + schema + ".workflow_definitions "
                + "(definition_id,definition_version,initial_state) VALUES('publication',7,'draft')");
        execute("INSERT INTO " + schema + ".workflow_definition_states "
                + "(definition_id,definition_version,state_id) VALUES('publication',7,'draft')");
        execute("INSERT INTO " + schema + ".workflow_definition_bindings "
                + "(tenant_id,resource_type,definition_id,definition_version,active) "
                + "VALUES('tenant-a','item','publication',7,true)");
    }

    private String newId() {
        return "wf_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String proposal(String id, String resourceId) {
        return "{\"instance_id\":\"" + id
                + "\",\"definition_id\":\"publication\",\"definition_version\":7,"
                + "\"resource_type\":\"item\",\"resource_id\":\"" + resourceId
                + "\",\"current_state\":\"draft\",\"runtime_version\":0,"
                + "\"lifecycle\":\"NOT_STARTED\",\"created_at_seconds\":0,"
                + "\"created_at_nanos\":0,\"updated_at_seconds\":0,\"updated_at_nanos\":0}";
    }

    private Reply create(String id, String actor, boolean authority, String resourceId) throws SQLException {
        return call(actor, authority, proposal(id, resourceId));
    }

    private Reply call(String actor, boolean authority, String proposal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT outcome,body FROM " + schema + ".wf_port_create_instance(?,?,?::jsonb)")) {
            statement.setString(1, actor);
            statement.setBoolean(2, authority);
            statement.setString(3, proposal);
            try (ResultSet r = statement.executeQuery()) {
                assertThat(r.next()).isTrue();
                Reply reply = new Reply(r.getString(1), r.getString(2));
                assertThat(r.next()).isFalse();
                return reply;
            }
        }
    }

    private record Reply(String outcome, String body) {}

    private int rows(String relation) throws SQLException {
        return count("SELECT count(*) FROM " + schema + "." + relation);
    }

    private int count(String sql) throws SQLException {
        try (var stmt = connection.createStatement(); var r = stmt.executeQuery(sql)) {
            assertThat(r.next()).isTrue();
            return r.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var stmt = connection.createStatement()) { stmt.execute(sql); }
    }
}
