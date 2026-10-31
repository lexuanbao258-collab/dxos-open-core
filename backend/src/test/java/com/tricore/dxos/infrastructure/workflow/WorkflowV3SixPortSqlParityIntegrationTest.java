package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.TransitionRecord;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Read/write SQL-vs-Core exact V3 state/definition/history comparison on disposable PG17.
 * SIX TEST-ONLY SECURITY-INVOKER SQL functions. NO V5, NO actor attestation or permission grant.
 * Do not promote these methods to production without the ADR-0023/0024 security review.
 */
@EnabledIfEnvironmentVariable(named="WORKFLOW_SQL_PROOF_DISPOSABLE_ACK",matches="ONLY_DISPOSABLE_PG17")
class WorkflowV3SixPortSqlParityIntegrationTest {
    private static final Pattern SAFE_URL=Pattern.compile(
            "^jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):([0-9]{4,5})/(wfproof_test_[a-z0-9_]{8,48})$");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Instant T0=Instant.parse("2026-10-18T01:02:03.123456789Z");
    private static final Instant T1=T0.plusNanos(11);
    private static final Instant T2=T1.plusNanos(13);
    private static final Instant T3=T2.plusNanos(17);
    private static final ActorReference ACTOR=new ActorReference("oidc:verified-parity-actor");

    @Test
    void sixFunctionsReconstructAndMutateExactCoreV3WireWithoutProductionGrant() throws Exception {
        String url=System.getenv("WORKFLOW_SQL_PROOF_DISPOSABLE_URL");
        Matcher match=SAFE_URL.matcher(url==null?"":url);
        if(!match.matches())throw new IllegalArgumentException("Dedicated disposable PG17 URL required");
        int port=Integer.parseInt(match.group(1));
        if(port==5432||port==55426||port<1024||port>65535)
            throw new IllegalArgumentException("Default/staging port rejected");
        if(!"true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")))
            throw new IllegalArgumentException("Pinned migration guard disposable mode required");
        String admin=required("WORKFLOW_SQL_PROOF_ADMIN_USER");
        String password=required("WORKFLOW_SQL_PROOF_ADMIN_PASSWORD");
        String schema="wf_v3_parity_"+UUID.randomUUID().toString().replace("-","");
        if(!schema.matches("[a-z][a-z0-9_]{0,62}"))throw new AssertionError("Unsafe schema");
        try(Connection first=DriverManager.getConnection(url,admin,password)){
            try(Statement q=first.createStatement();
                ResultSet rows=q.executeQuery("SELECT current_database(),current_setting('server_version_num'),"
                     +" current_setting('dxos.wfproof_disposable',true)")){
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(match.group(2));
                assertThat(Integer.parseInt(rows.getString(2))/10000).isEqualTo(17);
                assertThat(rows.getString(3)).isEqualTo("on");
            }
            try(Statement q=first.createStatement()){
                q.execute("CREATE SCHEMA "+schema);
            }
            try {
                DriverManagerDataSource ds=new DriverManagerDataSource();
                ds.setUrl(url);
                ds.setUsername(admin);
                ds.setPassword(password);
                Properties properties=new Properties();
                properties.setProperty("currentSchema",schema);
                ds.setConnectionProperties(properties);
                WorkflowMigrations.configure(ds,schema,"classpath:db/migration")
                        .locations("classpath:db/migration").loggers("slf4j").load().migrate();
                try(InputStream stream=WorkflowV3SixPortSqlParityIntegrationTest.class.getResourceAsStream(
                        "/workflow-sql-parity/v3-six-port-exact-wire.sql")){
                    assertThat(stream).isNotNull();
                    String raw=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                    String text=raw.replace("__WORKFLOW_SCHEMA__",schema);
                    assertThat(text).doesNotContain("__WORKFLOW_SCHEMA__");
                    try(Connection connection=ds.getConnection();Statement q=connection.createStatement()){
                        connection.setAutoCommit(false);
                        try{q.execute(text);connection.commit();}
                        catch(SQLException error){connection.rollback();throw error;}
                    }
                }

                // A real V3/V4 Java reference; domain transformations are those of Core.
                PostgresWorkflowPersistence portRef=new PostgresWorkflowPersistence(ds,schema);
                WorkflowDefinition def=portRef.loadDefinition("document-review",1);
                JsonNode defSql=call(ds,schema,"wf_port_load_definition(?,?)","document-review",1);
                assertThat(defSql.path("definition_id").asText()).isEqualTo(def.definitionId());
                assertThat(defSql.path("definition_version").asLong()).isEqualTo(def.definitionVersion());
                assertThat(defSql.path("initial_state").asText()).isEqualTo(def.initialState());
                assertThat(defSql.path("states").size()).isEqualTo(def.states().size());
                assertThat(defSql.path("transitions").size()).isEqualTo(def.transitions().size());
                for(var state:def.states())assertThat(defSql.path("states").toString()).contains("\""+state+"\"");

                String id="wf_parity_"+UUID.randomUUID().toString().replace("-","");
                var resource=new ResourceReference("document","doc-parity");
                WorkflowInstance initial=WorkflowInstance.notStarted(id,def,resource,T0);
                JsonNode inserted=call(ds,schema,"wf_port_create_instance(?,?,?,?,?,?,?)",
                        id,def.definitionId(),def.definitionVersion(),resource.resourceType(),resource.resourceId(),
                        T0.getEpochSecond(),T0.getNano());
                assertSnapshot(initial,inserted);
                assertSnapshot(initial,call(ds,schema,"wf_port_load_instance(?)",id));
                assertThat(call(ds,schema,"wf_port_load_history(?)",id).size()).isZero();
                assertSqlState(ds,schema,"wf_port_create_instance(?,?,?,?,?,?,?)","23505",
                        id,def.definitionId(),def.definitionVersion(),resource.resourceType(),resource.resourceId(),
                        T0.getEpochSecond(),T0.getNano());

                WorkflowInstance activated=initial.activate(def,T1);
                JsonNode activatedSql=call(ds,schema,"wf_port_commit_activation(?::jsonb,?::jsonb)",
                        wire(initial).toString(),wire(activated).toString());
                assertSnapshot(activated,activatedSql);
                assertSqlState(ds,schema,"wf_port_commit_activation(?::jsonb,?::jsonb)","P0001",
                        wire(initial).toString(),wire(activated).toString());

                TransitionResult firstTransition=activated.transition(def,
                        new TransitionCommand(id,"submit",ACTOR,0),T2);
                JsonNode result=call(ds,schema,"wf_port_commit_transition(?::jsonb,?::jsonb,?::jsonb)",
                        wire(activated).toString(),wire(firstTransition.instance()).toString(),
                        wire(firstTransition.record()).toString());
                assertSnapshot(firstTransition.instance(),result.path("instance"));
                assertRecord(firstTransition.record(),result.path("record"));
                assertThat(call(ds,schema,"wf_port_load_history(?)",id).size()).isEqualTo(1);

                TransitionResult second=firstTransition.instance().transition(def,
                        new TransitionCommand(id,"approve",ACTOR,1),T3);
                JsonNode secondSql=call(ds,schema,"wf_port_commit_transition(?::jsonb,?::jsonb,?::jsonb)",
                        wire(firstTransition.instance()).toString(),wire(second.instance()).toString(),
                        wire(second.record()).toString());
                assertSnapshot(second.instance(),secondSql.path("instance"));
                assertRecord(second.record(),secondSql.path("record"));
                JsonNode history=call(ds,schema,"wf_port_load_history(?)",id);
                assertThat(history.size()).isEqualTo(2);
                assertRecord(firstTransition.record(),history.get(0));
                assertRecord(second.record(),history.get(1));

                // Verified DB atomicity/error: stale proposal cannot append a third record.
                assertSqlState(ds,schema,"wf_port_commit_transition(?::jsonb,?::jsonb,?::jsonb)","P0001",
                        wire(activated).toString(),wire(firstTransition.instance()).toString(),
                        wire(firstTransition.record()).toString());
                assertThat(call(ds,schema,"wf_port_load_history(?)",id).size()).isEqualTo(2);
                assertSnapshot(second.instance(),call(ds,schema,"wf_port_load_instance(?)",id));
                assertSqlState(ds,schema,"wf_port_load_instance(?)","P0002","missing-private-id");

                // These bare experimental function signatures must NOT be invocable by a runtime role.
                assertThat(canExecuteAsRuntime(url,schema)).isFalse();
            } finally {
                try(Statement cleanup=first.createStatement()){
                    cleanup.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
                }
            }
        }
    }

    private static boolean canExecuteAsRuntime(String url,String schema) throws SQLException {
        try(Connection runtime=DriverManager.getConnection(url,
                    required("WORKFLOW_TEST_DB_USER"),required("WORKFLOW_TEST_DB_PASSWORD"));
            PreparedStatement q=runtime.prepareStatement("SELECT "+schema+".wf_port_load_instance(?)")){
            q.setString(1,"private");
            try(ResultSet result=q.executeQuery()){return result.next();}
        } catch(SQLException denied){
            if(!"42501".equals(denied.getSQLState()))
                throw new AssertionError("Wrong SQL runtime privilege denial",denied);
            return false;
        }
    }

    private static JsonNode call(DriverManagerDataSource ds,String schema,String sql,Object... args) throws Exception {
        try(Connection c=ds.getConnection();
            PreparedStatement q=c.prepareStatement("SELECT "+schema+"."+sql)){
            for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);
            try(ResultSet rs=q.executeQuery()){
                assertThat(rs.next()).isTrue();
                return JSON.readTree(rs.getString(1));
            }
        }
    }

    private static void assertSqlState(DriverManagerDataSource ds,String schema,String sql,
                                       String state,Object... args) throws Exception {
        assertThatThrownBy(()->call(ds,schema,sql,args)).isInstanceOf(SQLException.class)
                .satisfies(err->assertThat(((SQLException)err).getSQLState()).isEqualTo(state));
    }

    private static ObjectNode wire(WorkflowInstance i) {
        return JSON.createObjectNode()
            .put("instance_id",i.instanceId()).put("definition_id",i.definitionId())
            .put("definition_version",i.definitionVersion())
            .put("resource_type",i.resourceReference().resourceType())
            .put("resource_id",i.resourceReference().resourceId())
            .put("current_state",i.currentState()).put("runtime_version",i.runtimeVersion())
            .put("lifecycle",i.lifecycle().name())
            .put("created_at_seconds",i.createdAt().getEpochSecond())
            .put("created_at_nanos",i.createdAt().getNano())
            .put("updated_at_seconds",i.updatedAt().getEpochSecond())
            .put("updated_at_nanos",i.updatedAt().getNano());
    }

    private static ObjectNode wire(TransitionRecord r) {
        return JSON.createObjectNode()
            .put("instance_id",r.instanceId()).put("transition_id",r.transitionId())
            .put("source_state",r.sourceState()).put("target_state",r.targetState())
            .put("actor_id",r.actorReference().actorId())
            .put("resulting_runtime_version",r.resultingRuntimeVersion())
            .put("occurred_at_seconds",r.occurredAt().getEpochSecond())
            .put("occurred_at_nanos",r.occurredAt().getNano());
    }

    private static void assertSnapshot(WorkflowInstance expected,JsonNode actual) {
        assertThat(actual.path("instance_id").asText()).isEqualTo(expected.instanceId());
        assertThat(actual.path("definition_id").asText()).isEqualTo(expected.definitionId());
        assertThat(actual.path("definition_version").asLong()).isEqualTo(expected.definitionVersion());
        assertThat(actual.path("resource_type").asText()).isEqualTo(expected.resourceReference().resourceType());
        assertThat(actual.path("resource_id").asText()).isEqualTo(expected.resourceReference().resourceId());
        assertThat(actual.path("current_state").asText()).isEqualTo(expected.currentState());
        assertThat(actual.path("runtime_version").asLong()).isEqualTo(expected.runtimeVersion());
        assertThat(actual.path("lifecycle").asText()).isEqualTo(expected.lifecycle().name());
        assertThat(actual.path("created_at_seconds").asLong()).isEqualTo(expected.createdAt().getEpochSecond());
        assertThat(actual.path("created_at_nanos").asInt()).isEqualTo(expected.createdAt().getNano());
        assertThat(actual.path("updated_at_seconds").asLong()).isEqualTo(expected.updatedAt().getEpochSecond());
        assertThat(actual.path("updated_at_nanos").asInt()).isEqualTo(expected.updatedAt().getNano());
    }
    private static void assertRecord(TransitionRecord expected,JsonNode actual) {
        assertThat(actual.path("instance_id").asText()).isEqualTo(expected.instanceId());
        assertThat(actual.path("transition_id").asText()).isEqualTo(expected.transitionId());
        assertThat(actual.path("source_state").asText()).isEqualTo(expected.sourceState());
        assertThat(actual.path("target_state").asText()).isEqualTo(expected.targetState());
        assertThat(actual.path("actor_id").asText()).isEqualTo(expected.actorReference().actorId());
        assertThat(actual.path("resulting_runtime_version").asLong()).isEqualTo(expected.resultingRuntimeVersion());
        assertThat(actual.path("occurred_at_seconds").asLong()).isEqualTo(expected.occurredAt().getEpochSecond());
        assertThat(actual.path("occurred_at_nanos").asInt()).isEqualTo(expected.occurredAt().getNano());
    }
    private static String required(String name) {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+name);
        return value;
    }
}
