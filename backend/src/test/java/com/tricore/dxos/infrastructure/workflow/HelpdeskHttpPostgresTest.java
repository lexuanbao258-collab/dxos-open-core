package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.*;
import com.tricore.dxos.core.workflow.*;
import com.tricore.dxos.gateway.helpdesk.HelpdeskController;
import com.tricore.dxos.gateway.workflow.WorkflowJsonConfiguration;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import com.tricore.dxos.infrastructure.identity.security.SecurityConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real signed OIDC/JWKS -> production security/controller -> EXECUTE-only PostgreSQL LOGIN. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "HELPDESK_DISPOSABLE_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:55439/helpdesk_disposable_test")
class HelpdeskHttpPostgresTest {
    private static final String API = "/api/v1/mvp/requests";
    private static final List<String> AUTHORITIES = List.of("workflow:create", "workflow:activate", "workflow:read", "workflow:transition");
    private static final List<String> TABLES = List.of("tenant_guards", "tenant_memberships", "resource_registry", "resource_acl",
            "workflow_definition_bindings", "workflow_policy_audit", "workflow_definitions", "workflow_definition_states",
            "workflow_definition_transitions", "workflow_instances", "workflow_transition_history", "helpdesk_actors", "helpdesk_requests");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Map<String,String> actors = new HashMap<>();
    private final ThreadLocal<PidCapture> httpPidCapture = new ThreadLocal<>();
    private final AtomicBoolean unfinishedRaceWorker = new AtomicBoolean();
    private String url, schema, ownerRole, runtimeRole, issuer, runtimePassword;
    private DriverManagerDataSource runtime;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private HttpServer oidc;
    private RSAKey key;

    @BeforeAll void setup() throws Exception {
        url = System.getenv("HELPDESK_DISPOSABLE_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test");
        assertThat(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")).isEqualTo("true");
        key = new RSAKeyGenerator(2048).keyID("helpdesk-test").generate();
        oidc = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + oidc.getAddress().getPort();
        oidc.createContext("/.well-known/openid-configuration", exchange -> {
            byte[] body = json.writeValueAsBytes(Map.of("issuer",issuer,"jwks_uri",issuer+"/jwks"));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try (var output=exchange.getResponseBody()) { output.write(body); }
        });
        oidc.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try (var output=exchange.getResponseBody()) { output.write(body); }
        });
        oidc.start();
        for (String subject : List.of("employee","employee2","coordinator","worker","worker2","outsider","foreign"))
            actors.put(subject, "oidc:" + encode(issuer) + "." + encode(subject));
        String suffix = UUID.randomUUID().toString().replace("-", "");
        schema = "helpdesk_" + suffix;
        ownerRole = "helpdesk_owner_" + suffix;
        runtimeRole = "helpdesk_runtime_" + suffix;
        String password = UUID.randomUUID().toString();
        runtimePassword = password;
        try (var c = admin()) {
            assertThat(number(c,"SHOW server_version_num")).isBetween(180000L,189999L);
            c.setAutoCommit(false);
            sql(c,"CREATE ROLE " + ownerRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
            sql(c,"CREATE ROLE " + runtimeRole + " LOGIN PASSWORD '" + password + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
            sql(c,"CREATE SCHEMA " + schema);
            for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                    "src/test/resources/stage6/V5_acl_physical_candidate.sql", "src/test/resources/stage6/Stage6_guarded_read_candidate.sql",
                    "src/test/resources/stage6/Stage6_guarded_create_candidate.sql", "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql",
                    "src/main/resources/db/helpdesk/application.sql", "src/main/resources/db/helpdesk/definition.sql"))
                sql(c, Files.readString(Path.of(file)).replace("${flyway:defaultSchema}",schema));
            sql(c,"INSERT INTO " + schema + ".tenant_guards VALUES('tenant-a',0),('tenant-b',0)");
            for (var actor : actors.entrySet()) {
                String tenant = actor.getKey().equals("foreign") ? "tenant-b" : "tenant-a";
                sql(c,"INSERT INTO " + schema + ".tenant_memberships VALUES('" + tenant + "','" + actor.getValue() + "',true)");
                sql(c,"INSERT INTO " + schema + ".helpdesk_actors "
                        + "(tenant_id,actor_id,submitter,coordinator,worker,display_name) VALUES('" + tenant + "','" + actor.getValue() + "',"
                        + actor.getKey().startsWith("employee") + "," + actor.getKey().equals("coordinator") + ","
                        + actor.getKey().startsWith("worker") + ",'Demo " + actor.getKey() + "')");
            }
            sql(c,"INSERT INTO " + schema + ".workflow_definition_bindings VALUES('tenant-a','mvp_request','it-helpdesk',1,true)");
            sql(c,"REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
            sql(c,"REVOKE ALL ON ALL TABLES IN SCHEMA " + schema + " FROM PUBLIC");
            sql(c,"REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + schema + " FROM PUBLIC");
            sql(c,"SET CONSTRAINTS ALL IMMEDIATE");
            for (String table : TABLES) sql(c,"ALTER TABLE " + schema + "." + table + " OWNER TO " + ownerRole);
            List<String> functions = new ArrayList<>();
            try (var s=c.createStatement();var r=s.executeQuery("SELECT quote_ident(proname)||'('||pg_get_function_identity_arguments(oid)||')' FROM pg_proc WHERE pronamespace='" + schema + "'::regnamespace")) {
                while(r.next()) functions.add(r.getString(1));
            }
            for(String function:functions) {
                sql(c,"ALTER FUNCTION " + schema + "." + function + " OWNER TO " + ownerRole);
                if(function.startsWith("wf_port_") || function.startsWith("helpdesk_open(")
                        || function.startsWith("helpdesk_request(") || function.startsWith("helpdesk_list("))
                    sql(c,"GRANT EXECUTE ON FUNCTION " + schema + "." + function + " TO " + runtimeRole);
            }
            sql(c,"ALTER SCHEMA " + schema + " OWNER TO " + ownerRole);
            sql(c,"GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
            c.commit();
        }
        runtime = new DriverManagerDataSource(url,runtimeRole,password);
        var properties=new Properties(); properties.setProperty("currentSchema",schema+",pg_catalog,pg_temp");
        runtime.setConnectionProperties(properties);
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("helpdesk-test",Map.of(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",issuer,"tricore.security.oidc.audience","tricore-api",
                "tricore.security.oidc.resource-client-id","tricore-api","dxos.helpdesk.enabled","true","test.schema",schema)));
        context.addBeanFactoryPostProcessor(f -> f.registerSingleton("helpdeskPersistence",new PostgresWorkflowPersistence(captureHttpBackend(runtime),schema)));
        context.register(SecurityConfig.class,Class.forName("com.tricore.dxos.infrastructure.identity.security.SpringSecurityCurrentPrincipal"),
                Web.class,HelpdeskController.class,WorkflowExceptionHandler.class,WorkflowJsonConfiguration.class,
                com.tricore.dxos.request.controller.RequestController.class,com.tricore.dxos.request.controller.RequestWorkflowController.class);
        context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
    }

    @Test void goldenPathUsesAllNineRoutesAndAuthoritativeVersions() throws Exception {
        var created=create("employee"); String id=created.path("id").asText();
        assertThat(created.path("requesterId").asText()).isEqualTo(actors.get("employee"));
        assertThat(created.path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(created.path("allowedActions")).isEmpty();
        expect(get(API+"/"+id),"coordinator",200).andExpect(jsonPath("$.allowedActions[0]").value("triage"));
        expect(post(API+"/"+id+"/triage").content("{\"expectedVersion\":0}"),"coordinator",200)
                .andExpect(jsonPath("$.state").value("TRIAGED")).andExpect(jsonPath("$.version").value(1));
        assign(id,"worker",0,200);
        expect(get(API+"/"+id),"worker",200).andExpect(jsonPath("$.allowedActions[0]").value("start"))
                .andExpect(jsonPath("$.assignmentVersion").value(1)).andExpect(jsonPath("$.version").value(1));
        expect(post(API+"/"+id+"/start").content("{\"expectedVersion\":1}"),"worker",200);
        expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":2,\"resolution\":\"Software repaired\"}"),"worker",200)
                .andExpect(jsonPath("$.state").value("RESOLVED"));
        expect(get(API+"/"+id),"employee",200).andExpect(jsonPath("$.allowedActions[0]").value("confirm"));
        expect(post(API+"/"+id+"/confirm").content("{\"expectedVersion\":3}"),"employee",200)
                .andExpect(jsonPath("$.state").value("CONFIRMED")).andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
        expect(get(API+"/"+id+"/history"),"employee",200).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].transitionId").value("start_work")).andExpect(jsonPath("$[3].version").value(4))
                .andExpect(jsonPath("$[0].actorId").doesNotExist());
        expect(get(API),"employee",200).andExpect(jsonPath("$.items[?(@.id=='"+id+"')]").isNotEmpty());
    }

    @Test void detailProjectsTrustedDisplayNamesAndLimitsCandidateDirectoryToAuthorizedAssign() throws Exception {
        String id=triaged();
        var coordinator=read(expect(get(API+"/"+id),"coordinator",200));
        assertThat(coordinator.path("requesterDisplayName").asText()).isEqualTo("Demo employee");
        assertThat(coordinator.path("assigneeDisplayName").isMissingNode()).isTrue();
        assertThat(coordinator.path("eligibleAssignees").isArray()).isTrue();
        assertThat(coordinator.path("eligibleAssignees").findValuesAsText("id"))
                .containsExactlyInAnyOrder(actors.get("worker"),actors.get("worker2"));
        assertThat(coordinator.path("eligibleAssignees").findValuesAsText("displayName"))
                .containsExactlyInAnyOrder("Demo worker","Demo worker2");

        // Read permission alone does not disclose a worker directory.
        expect(get(API+"/"+id),"employee",200)
                .andExpect(jsonPath("$.eligibleAssignees").doesNotExist());
        expect(get(API),"coordinator",200)
                .andExpect(jsonPath("$.items[0].eligibleAssignees").doesNotExist())
                .andExpect(jsonPath("$.items[0].requesterDisplayName").doesNotExist())
                .andExpect(jsonPath("$.items[0].assigneeDisplayName").doesNotExist());

        assign(id,"worker",0,200);
        expect(get(API+"/"+id),"coordinator",200)
                .andExpect(jsonPath("$.assigneeDisplayName").value("Demo worker"));
        expect(get(API+"/"+id),"worker",200)
                .andExpect(jsonPath("$.eligibleAssignees").doesNotExist());
        expect(post(API+"/"+id+"/start").content("{\"expectedVersion\":1}"),"worker",200);
        expect(get(API+"/"+id),"coordinator",200)
                .andExpect(jsonPath("$.eligibleAssignees").doesNotExist());
    }

    @Test void noDirectoryNameMeansNullRatherThanInventedLabel() throws Exception {
        String id=create("employee").path("id").asText();
        try (var c=admin()) {
            sql(c,"UPDATE "+schema+".helpdesk_actors SET display_name=NULL WHERE actor_id='"+actors.get("employee")+"'");
        }
        try {
            expect(get(API+"/"+id),"employee",200)
                    .andExpect(jsonPath("$.requesterDisplayName").doesNotExist());
        } finally {
            try (var c=admin()) {
                sql(c,"UPDATE "+schema+".helpdesk_actors SET display_name='Demo employee' WHERE actor_id='"+actors.get("employee")+"'");
            }
        }
    }

    @Test void unauthenticatedAndForgedSignatureNeverWrite() throws Exception {
        var before=snapshot();
        mvc.perform(post(API).contentType("application/json").content(createBody())).andExpect(status().isUnauthorized());
        String token=token("employee",AUTHORITIES,new RSAKeyGenerator(2048).generate());
        mvc.perform(post(API).header("Authorization","Bearer "+token).contentType("application/json").content(createBody()))
                .andExpect(status().isUnauthorized());
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"requesterId","actorId","tenantId","roles","authorities"})
    void identityFieldsAreRejected(String field) throws Exception {
        var before=snapshot();
        expect(post(API).content(createBody().replace("}",",\""+field+"\":\"forged\"}")),"employee",400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void crossRequestAndCrossTenantAreHiddenAndListIsScoped() throws Exception {
        String mine=create("employee").path("id").asText();
        String other=create("employee2").path("id").asText();
        var before=snapshot();
        for(String actor:List.of("employee2","outsider","foreign")) {
            expect(get(API+"/"+mine),actor,404);
            expect(get(API+"/"+mine+"/history"),actor,404);
            expect(post(API+"/"+mine+"/confirm").content("{\"expectedVersion\":0}"),actor,404);
        }
        expect(get(API),"employee",200).andExpect(jsonPath("$.items[?(@.id=='"+other+"')]").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void hiddenAndNonexistentIdsHaveIndistinguishable404Responses() throws Exception {
        var created=create("employee");
        String hidden=created.path("id").asText();
        UUID nonexistent=UUID.randomUUID();
        assertThat(nonexistent).isNotEqualTo(UUID.fromString(hidden));
        try(var c=admin();var s=c.prepareStatement("SELECT 1 FROM "+schema+".helpdesk_requests WHERE request_id=?")) {
            s.setObject(1,nonexistent);
            try(var rows=s.executeQuery()) { assertThat(rows.next()).isFalse(); }
        }
        var before=snapshot();
        JsonNode expected=json.readTree("""
                {"status":404,"code":"NOT_FOUND","message":"Workflow resource not found","errors":{}}
                """);
        for(String suffix:List.of("","/history")) {
            var hiddenResponse=expect(get(API+"/"+hidden+suffix),"employee2",404).andReturn().getResponse();
            var missingResponse=expect(get(API+"/"+nonexistent+suffix),"employee2",404).andReturn().getResponse();
            JsonNode hiddenBody=json.readTree(hiddenResponse.getContentAsString());
            JsonNode missingBody=json.readTree(missingResponse.getContentAsString());
            assertThat(hiddenBody).isEqualTo(missingBody).isEqualTo(expected);
            assertThat(hiddenResponse.getContentType()).isEqualTo(missingResponse.getContentType());
            assertThat(hiddenResponse.getContentAsString()).doesNotContain(hidden,actors.get("employee"),
                    created.path("workflowInstanceId").asText(),"Software issue","Please repair");
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void visibleWithoutExactTechnicalAuthorityIsForbidden() throws Exception {
        String id=create("employee").path("id").asText(); var before=snapshot();
        mvc.perform(get(API+"/"+id).header("Authorization","Bearer "+token("employee",List.of("workflow:read-all"),key)))
                .andExpect(status().isForbidden());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void coordinatorWithoutExactTransitionAuthorityCannotTriage() throws Exception {
        String id=create("employee").path("id").asText();
        String coordinator=actors.get("coordinator");
        try(var c=admin()) {
            assertThat(number(c,"SELECT COUNT(*) FROM "+schema+".tenant_memberships m JOIN "+schema
                    +".helpdesk_actors a USING(tenant_id,actor_id) WHERE m.tenant_id='tenant-a'"
                    +" AND m.actor_id='"+coordinator+"' AND m.active AND a.coordinator")).isEqualTo(1);
            assertThat(number(c,"SELECT COUNT(*) FROM "+schema+".resource_acl WHERE tenant_id='tenant-a'"
                    +" AND resource_type='mvp_request' AND resource_id='"+id+"' AND actor_id='"+coordinator+"'"
                    +" AND operation IN ('workflow:read','workflow:transition') AND granted")).isEqualTo(2);
        }
        String readOnly=token("coordinator",List.of("workflow:read"),key);
        String canTransition=token("coordinator",List.of("workflow:read","workflow:transition"),key);
        mvc.perform(get(API+"/"+id).header("Authorization","Bearer "+readOnly))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("SUBMITTED"))
                .andExpect(jsonPath("$.version").value(0));
        var before=snapshot();
        mvc.perform(post(API+"/"+id+"/triage").header("Authorization","Bearer "+readOnly)
                .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(snapshot()).isEqualTo(before);
        expect(get(API+"/"+id),"employee",200).andExpect(jsonPath("$.state").value("SUBMITTED"))
                .andExpect(jsonPath("$.version").value(0));
        expect(get(API+"/"+id+"/history"),"employee",200).andExpect(jsonPath("$.length()").value(0));
        assertThat(snapshot()).isEqualTo(before);
        mvc.perform(post(API+"/"+id+"/triage").header("Authorization","Bearer "+canTransition)
                .contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("TRIAGED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test void unauthorizedTriageAssignmentAndConfirmationDoNotWrite() throws Exception {
        String id=triaged(); var before=snapshot();
        expect(post(API+"/"+id+"/triage").content("{\"expectedVersion\":1}"),"employee",403);
        expect(post(API+"/"+id+"/assign").content(assignBody("worker",0)),"employee",403);
        assign(id,"outsider",0,403);
        expect(post(API+"/"+id+"/confirm").content("{\"expectedVersion\":1}"),"coordinator",403);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void staleAssignmentAndReassignmentRevokePreviousWorker() throws Exception {
        String id=triaged(); assign(id,"worker",0,200); var before=snapshot();
        assign(id,"worker2",0,409); assertThat(snapshot()).isEqualTo(before);
        assign(id,"worker2",1,200);
        expect(get(API+"/"+id),"worker",404);
        expect(post(API+"/"+id+"/start").content("{\"expectedVersion\":1}"),"worker",404);
        expect(get(API+"/"+id),"worker2",200).andExpect(jsonPath("$.assignmentVersion").value(2));
    }

    @Test void twoConcurrentAssignmentsHaveExactlyOneWinner() throws Exception {
        String id=triaged(); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<String>> attempts=new ArrayList<>();
            for(String worker:List.of("worker","worker2")) attempts.add(executor.submit(() -> {
                start.await();
                try { serviceAs("coordinator",runtime).assign(UUID.fromString(id),actors.get(worker),0); return "OK"; }
                catch(WorkflowException conflict) { return conflict.code().name(); }
            }));
            start.countDown();
            assertThat(List.of(attempts.get(0).get(20,TimeUnit.SECONDS),attempts.get(1).get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK","VERSION_CONFLICT");
        }
        expect(get(API+"/"+id),"coordinator",200).andExpect(jsonPath("$.assignmentVersion").value(1))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test void reassignmentCommitPrecedesWaitingPreviousAssigneeStart() throws Exception {
        String id=triaged(); assign(id,"worker",0,200);
        List<HistoryRecord> before=historyRecords(id);
        assertThat(before).containsExactly(new HistoryRecord(1,"triage","SUBMITTED","TRIAGED",actors.get("coordinator")));
        var gate=new CommitGate();
        var capture=new PidCapture();
        try(var inspector=inspector()) {
            ExecutorService executor=Executors.newFixedThreadPool(2);
            Future<?> reassignment=null,waiting=null;
            try {
                reassignment=executor.submit(() -> serviceAs("coordinator",gateCommit(runtime,gate))
                        .assign(UUID.fromString(id),actors.get("worker2"),1));
                assertThat(gate.entered.await(5,TimeUnit.SECONDS)).isTrue();
                waiting=executor.submit(() -> {
                    httpPidCapture.set(capture);
                    try {
                        expect(post(API+"/"+id+"/start").content("{\"expectedVersion\":1}"),"worker",404)
                                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
                        return null;
                    } finally { httpPidCapture.remove(); }
                });
                assertThat(capture.ready.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(waitForBlockedBy(inspector,capture.pid.get(),gate.pid.get())).isTrue();
                gate.release.countDown();
                reassignment.get(10,TimeUnit.SECONDS);
                waiting.get(10,TimeUnit.SECONDS);
            } finally {
                gate.release.countDown();
                finishRace(executor,reassignment,waiting);
            }
        }
        assertThat(requestState(id)).isEqualTo(new RequestState(actors.get("worker2"),2,null,"TRIAGED",1,1,0,0));
        assertThat(historyRecords(id)).containsExactlyElementsOf(before);
    }

    @Test void previousAssigneeStartCommitPrecedesWaitingReassignment() throws Exception {
        String id=triaged(); assign(id,"worker",0,200);
        var gate=new CommitGate();
        var capture=new PidCapture();
        try(var inspector=inspector()) {
            ExecutorService executor=Executors.newFixedThreadPool(2);
            Future<?> start=null,waiting=null;
            try {
                start=executor.submit(() -> serviceAs("worker",gateCommit(runtime,gate))
                        .transition(UUID.fromString(id),"start_work",1,null));
                assertThat(gate.entered.await(5,TimeUnit.SECONDS)).isTrue();
                waiting=executor.submit(() -> {
                    httpPidCapture.set(capture);
                    try {
                        expect(post(API+"/"+id+"/assign").content(assignBody("worker2",1)),"coordinator",409)
                                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
                        return null;
                    } finally { httpPidCapture.remove(); }
                });
                assertThat(capture.ready.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(waitForBlockedBy(inspector,capture.pid.get(),gate.pid.get())).isTrue();
                gate.release.countDown();
                start.get(10,TimeUnit.SECONDS);
                waiting.get(10,TimeUnit.SECONDS);
            } finally {
                gate.release.countDown();
                finishRace(executor,start,waiting);
            }
        }
        assertThat(requestState(id)).isEqualTo(new RequestState(actors.get("worker"),1,null,"IN_PROGRESS",2,2,1,0));
        assertThat(historyRecords(id)).containsExactly(
                new HistoryRecord(1,"triage","SUBMITTED","TRIAGED",actors.get("coordinator")),
                new HistoryRecord(2,"start_work","TRIAGED","IN_PROGRESS",actors.get("worker")));
    }

    @Test void staleResolutionRollsBackBusinessFactAndHistory() throws Exception {
        String id=inProgress(); var before=snapshot();
        expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":1,\"resolution\":\"must roll back\"}"),"worker",409);
        assertThat(snapshot()).isEqualTo(before);
        expect(get(API+"/"+id),"worker",200).andExpect(jsonPath("$.resolution").isEmpty()).andExpect(jsonPath("$.state").value("IN_PROGRESS"));
    }

    @Test void databaseHistoryFailureRollsBackResolutionAndWorkflowState() throws Exception {
        String id=inProgress();
        String instance=read(expect(get(API+"/"+id),"employee",200)).path("workflowInstanceId").asText();
        try(var c=admin()) {
            sql(c,"CREATE FUNCTION "+schema+".fail_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected history failure'; END $$");
            sql(c,"CREATE TRIGGER injected_history_failure BEFORE INSERT ON "+schema+".workflow_transition_history FOR EACH ROW "
                    +"WHEN (NEW.instance_id='"+instance+"' AND NEW.transition_id='resolve') EXECUTE FUNCTION "+schema+".fail_history()");
        }
        try {
            var before=snapshot();
            expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":2,\"resolution\":\"must roll back\"}"),"worker",503)
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("injected history"))));
            assertThat(snapshot()).isEqualTo(before);
        } finally { try(var c=admin()) {
            sql(c,"DROP TRIGGER injected_history_failure ON "+schema+".workflow_transition_history");
            sql(c,"DROP FUNCTION "+schema+".fail_history()");
        } }
    }

    @Test void failedActivationLeavesNoBusinessBindingResourceOrWorkflowOrphan() throws Exception {
        try(var c=admin()) {
            sql(c,"CREATE FUNCTION "+schema+".fail_activation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected activation failure'; END $$");
            sql(c,"CREATE TRIGGER injected_activation_failure BEFORE UPDATE ON "+schema+".workflow_instances FOR EACH ROW "
                    +"WHEN (OLD.lifecycle='NOT_STARTED' AND NEW.lifecycle='ACTIVE') EXECUTE FUNCTION "+schema+".fail_activation()");
        }
        try {
            var before=snapshot();
            expect(post(API).content(createBody()),"employee",503);
            assertThat(snapshot()).isEqualTo(before);
        } finally { try(var c=admin()) {
            sql(c,"DROP TRIGGER injected_activation_failure ON "+schema+".workflow_instances");
            sql(c,"DROP FUNCTION "+schema+".fail_activation()");
        } }
    }

    @Test void ownerStillNeedsReadAndTransitionGrants() throws Exception {
        String id=create("employee").path("id").asText();
        try(var c=admin()) { sql(c,"UPDATE "+schema+".resource_acl SET granted=false WHERE resource_id='"+id+"' AND actor_id='"
                +actors.get("employee")+"' AND operation='workflow:read'"); }
        var before=snapshot();
        expect(get(API+"/"+id),"employee",403);
        expect(get(API+"/"+id+"/history"),"employee",403);
        expect(get(API),"employee",200).andExpect(jsonPath("$.items[?(@.id=='"+id+"')]").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void businessEligibilitySurvivesBroadResourceGrant() throws Exception {
        String id=inProgress(); grant(id,"worker2"); var before=snapshot();
        expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":2,\"resolution\":\"forged result\"}"),"worker2",403);
        expect(post(API+"/"+id+"/confirm").content("{\"expectedVersion\":2}"),"worker2",403);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void revokedWorkerEligibilityIsRecheckedAtWriteBoundary() throws Exception {
        String id=inProgress();
        try(var c=admin()) { sql(c,"UPDATE "+schema+".helpdesk_actors SET worker=false WHERE actor_id='"+actors.get("worker")+"'"); }
        try {
            var before=snapshot();
            expect(get(API+"/"+id),"worker",200).andExpect(jsonPath("$.allowedActions").isEmpty());
            expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":2,\"resolution\":\"denied\"}"),"worker",403);
            assertThat(snapshot()).isEqualTo(before);
        } finally { try(var c=admin()) { sql(c,"UPDATE "+schema+".helpdesk_actors SET worker=true WHERE actor_id='"+actors.get("worker")+"'"); } }
    }

    @Test void earlierRevokeWinsAgainstWaitingResolution() throws Exception {
        String id=inProgress();
        try(var administrator=admin();var executor=Executors.newSingleThreadExecutor()) {
            administrator.setAutoCommit(false);
            sql(administrator,"UPDATE "+schema+".helpdesk_actors SET worker=false WHERE actor_id='"+actors.get("worker")+"'");
            var started=new CountDownLatch(1);
            var attempt=executor.submit(() -> {
                started.countDown();
                return catchThrowable(() -> serviceAs("worker",runtime).transition(UUID.fromString(id),"resolve",2,"must not persist"));
            });
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> attempt.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            administrator.commit();
            assertThat(attempt.get(20,TimeUnit.SECONDS)).isInstanceOfSatisfying(
                    com.tricore.dxos.application.authorization.WorkflowAccessException.class,
                    e -> assertThat(e.decision()).isEqualTo(com.tricore.dxos.application.authorization.WorkflowAccessDecision.FORBIDDEN));
            expect(get(API+"/"+id),"employee",200).andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.resolution").isEmpty()).andExpect(jsonPath("$.version").value(2));
            expect(get(API+"/"+id+"/history"),"employee",200).andExpect(jsonPath("$.length()").value(2));
        } finally { try(var c=admin()) { sql(c,"UPDATE "+schema+".helpdesk_actors SET worker=true WHERE actor_id='"+actors.get("worker")+"'"); } }
    }

    @Test void committedTransitionGrantRevocationDeniesWaitingResolve() throws Exception {
        String id=inProgress();
        List<HistoryRecord> before=historyRecords(id);
        assertThat(before).containsExactly(
                new HistoryRecord(1,"triage","SUBMITTED","TRIAGED",actors.get("coordinator")),
                new HistoryRecord(2,"start_work","TRIAGED","IN_PROGRESS",actors.get("worker")));
        var capture=new PidCapture();
        try(var inspector=inspector();var administrator=revocationHolder()) {
            administrator.setAutoCommit(false);
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> waiting=null;
            Throwable primaryFailure=null;
            boolean committed=false;
            try {
                int administratorPid=(int)boundedNumber(administrator,"SELECT pg_backend_pid()");
                boundedSql(administrator,"SELECT tenant_id FROM "+schema+".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
                boundedSql(administrator,"UPDATE "+schema+".resource_acl SET granted=false WHERE tenant_id='tenant-a'"
                        +" AND resource_type='mvp_request' AND resource_id='"+id+"' AND actor_id='"+actors.get("worker")+"'"
                        +" AND operation='workflow:transition'");
                waiting=executor.submit(() -> {
                    httpPidCapture.set(capture);
                    try {
                        expect(post(API+"/"+id+"/resolve").content("{\"expectedVersion\":2,\"resolution\":\"must not persist\"}"),"worker",403)
                                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
                        return null;
                    } finally { httpPidCapture.remove(); }
                });
                assertThat(capture.ready.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(waitForBlockedBy(inspector,capture.pid.get(),administratorPid)).isTrue();
                administrator.commit();
                committed=true;
                waiting.get(10,TimeUnit.SECONDS);
            } catch(Exception | Error failure) {
                primaryFailure=failure;
                throw failure;
            } finally {
                finishRevocationRace(administrator,!committed,executor,waiting,primaryFailure);
            }
        }
        assertThat(requestState(id)).isEqualTo(new RequestState(actors.get("worker"),1,null,"IN_PROGRESS",2,2,1,0));
        assertThat(historyRecords(id)).containsExactlyElementsOf(before);
        try(var observer=admin()) {
            assertThat(number(observer,"SELECT count(*) FROM "+schema+".resource_acl WHERE resource_id='"+id+"'"
                    +" AND actor_id='"+actors.get("worker")+"' AND operation='workflow:transition' AND NOT granted")).isEqualTo(1);
            assertThat(number(observer,"SELECT count(*) FROM "+schema+".resource_acl WHERE resource_id='"+id+"'"
                    +" AND actor_id='"+actors.get("worker")+"' AND operation='workflow:read' AND granted")).isEqualTo(1);
            assertThat(number(observer,"SELECT count(*) FROM "+schema+".helpdesk_actors a JOIN "+schema
                    +".tenant_memberships m USING(tenant_id,actor_id) WHERE a.actor_id='"+actors.get("worker")
                    +"' AND a.worker AND m.active")).isEqualTo(1);
        }
    }


    /** B06: real Keycloak bearer tokens through actual loopback Spring Boot HTTP and disposable PG.
     *  All identities are provisioned only in this test's random PostgreSQL schema.
     *  No mocked principal or local fake issuer is used for these requests. */
    @Test
    @EnabledIfEnvironmentVariable(named="B06_EMPLOYEE_ACCESS_TOKEN",matches=".+")
    void b06RealKeycloakTokensAndPostgres() throws Exception {
        String keycloakIssuer="http://localhost:8180/realms/tricore";
        var decoder=org.springframework.security.oauth2.jwt.JwtDecoders.fromIssuerLocation(keycloakIssuer);
        Map<String,String> tokens=new LinkedHashMap<>();
        Map<String,String> ids=new LinkedHashMap<>();
        for(String role:List.of("employee","coordinator","worker","foreign")) {
            String secret=System.getenv("B06_"+role.toUpperCase(Locale.ROOT)+"_ACCESS_TOKEN");
            assertThat(secret).as("Missing fresh real Keycloak token for "+role).isNotNull().isNotBlank();
            var verified=decoder.decode(secret); // signature, issuer, aud, typ and lifetime validated via real JWKS
            assertThat(verified.getIssuer().toString()).isEqualTo(keycloakIssuer);
            assertThat(verified.getAudience()).contains("tricore-api");
            assertThat(verified.getExpiresAt()).isAfter(Instant.now().plusSeconds(90));
            tokens.put(role,secret);
            ids.put(role,"oidc:"+encode(keycloakIssuer)+"."+encode(verified.getSubject()));
        }
        assertThat(new HashSet<>(ids.values())).as("Distinct authenticated subjects").hasSize(4);
        try(var c=admin()) {
            for(String role:ids.keySet()) {
                String tenant=role.equals("foreign")?"tenant-b":"tenant-a";
                try(var membership=c.prepareStatement("INSERT INTO "+schema+".tenant_memberships (tenant_id,actor_id,active) VALUES (?,?,true)");
                    var actor=c.prepareStatement("INSERT INTO "+schema+".helpdesk_actors "
                        +"(tenant_id,actor_id,submitter,coordinator,worker,display_name) VALUES (?,?,?,?,?,?)")) {
                    membership.setString(1,tenant);membership.setString(2,ids.get(role));membership.executeUpdate();
                    actor.setString(1,tenant);actor.setString(2,ids.get(role));
                    actor.setBoolean(3,role.equals("employee"));
                    actor.setBoolean(4,role.equals("coordinator"));
                    actor.setBoolean(5,role.equals("worker"));
                    actor.setString(6,"B06 "+role);actor.executeUpdate();
                }
            }
        }
        try(var app=new SpringApplicationBuilder(com.tricore.dxos.DxosApplication.class).run(
                "--server.address=127.0.0.1","--server.port=0","--spring.main.banner-mode=off",
                "--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=none",
                "--spring.datasource.url="+url+"?currentSchema="+schema+",pg_catalog,pg_temp",
                "--spring.datasource.username="+runtimeRole,"--spring.datasource.password="+runtimePassword,
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri="+keycloakIssuer,
                "--tricore.security.oidc.audience=tricore-api","--tricore.security.oidc.resource-client-id=tricore-api",
                "--dxos.workflow.schema="+schema,"--dxos.helpdesk.enabled=true",
                "--dxos.helpdesk.jdbc-url="+url,"--dxos.helpdesk.username="+runtimeRole,
                "--dxos.helpdesk.password="+runtimePassword,"--dxos.helpdesk.schema="+schema,
                "--dxos.helpdesk.tenant=tenant-a");
            var http=HttpClient.newHttpClient()) {
            String base="http://127.0.0.1:"+((ServletWebServerApplicationContext)app).getWebServer().getPort();
            b06Call(http,base,null,"POST","",createBody(),401);
            String[] jwt=tokens.get("employee").split("\\.",-1);
            assertThat(jwt).hasSize(3);assertThat(jwt[2]).isNotEmpty();
            String modified=jwt[0]+"."+jwt[1]+"."+(jwt[2].charAt(0)=='A'?'B':'A')+jwt[2].substring(1);
            b06Call(http,base,modified,"POST","",createBody(),401);
            JsonNode created=b06Call(http,base,tokens.get("employee"),"POST","",createBody(),201);
            String id=created.path("id").asText();
            assertThat(UUID.fromString(id)).isNotNull();
            assertThat(created.path("requesterId").asText()).isEqualTo(ids.get("employee"));
            assertThat(created.path("state").asText()).isEqualTo("SUBMITTED");
            assertThat(b06Call(http,base,tokens.get("employee"),"GET","?limit=50",null,200)
                    .path("items").findValuesAsText("id")).contains(id);
            b06Call(http,base,tokens.get("foreign"),"GET","/"+id,null,404);
            b06Call(http,base,tokens.get("worker"),"GET","/"+id,null,404);
            assertThat(b06Call(http,base,tokens.get("coordinator"),"GET","/"+id,null,200)
                    .path("allowedActions").toString()).contains("triage");
            b06Call(http,base,tokens.get("employee"),"POST","/"+id+"/triage",
                    json.writeValueAsString(Map.of("expectedVersion",0)),403);
            assertThat(b06Call(http,base,tokens.get("coordinator"),"POST","/"+id+"/triage",
                    json.writeValueAsString(Map.of("expectedVersion",0)),200).path("state").asText()).isEqualTo("TRIAGED");
            assertThat(b06Call(http,base,tokens.get("coordinator"),"GET","/"+id,null,200)
                    .path("eligibleAssignees").findValuesAsText("id")).contains(ids.get("worker"));
            String badAssign=json.writeValueAsString(Map.of("assigneeId",ids.get("worker"),"expectedAssignmentVersion",1));
            String goodAssign=json.writeValueAsString(Map.of("assigneeId",ids.get("worker"),"expectedAssignmentVersion",0));
            b06Call(http,base,tokens.get("employee"),"POST","/"+id+"/assign",goodAssign,403);
            b06Call(http,base,tokens.get("coordinator"),"POST","/"+id+"/assign",badAssign,409);
            JsonNode assigned=b06Call(http,base,tokens.get("coordinator"),"POST","/"+id+"/assign",goodAssign,200);
            assertThat(assigned.path("assigneeId").asText()).isEqualTo(ids.get("worker"));
            assertThat(assigned.path("assignmentVersion").asLong()).isEqualTo(1);
            b06Call(http,base,tokens.get("coordinator"),"POST","/"+id+"/start",
                    json.writeValueAsString(Map.of("expectedVersion",1)),403);
            assertThat(b06Call(http,base,tokens.get("worker"),"POST","/"+id+"/start",
                    json.writeValueAsString(Map.of("expectedVersion",1)),200).path("state").asText()).isEqualTo("IN_PROGRESS");
            b06Call(http,base,tokens.get("worker"),"POST","/"+id+"/resolve",
                    json.writeValueAsString(Map.of("expectedVersion",1,"resolution","stale")),409);
            assertThat(b06Call(http,base,tokens.get("worker"),"POST","/"+id+"/resolve",
                    json.writeValueAsString(Map.of("expectedVersion",2,"resolution","B06 fixed")),200)
                    .path("state").asText()).isEqualTo("RESOLVED");
            b06Call(http,base,tokens.get("worker"),"POST","/"+id+"/confirm",
                    json.writeValueAsString(Map.of("expectedVersion",3)),403);
            JsonNode confirmed=b06Call(http,base,tokens.get("employee"),"POST","/"+id+"/confirm",
                    json.writeValueAsString(Map.of("expectedVersion",3)),200);
            assertThat(confirmed.path("state").asText()).isEqualTo("CONFIRMED");
            assertThat(confirmed.path("version").asLong()).isEqualTo(4);
            JsonNode history=b06Call(http,base,tokens.get("employee"),"GET","/"+id+"/history",null,200);
            assertThat(history.size()).isEqualTo(4);
            assertThat(history.get(3).path("version").asLong()).isEqualTo(4);
            b06Call(http,base,tokens.get("foreign"),"GET","/"+id+"/history",null,404);
            try(var c=admin();var s=c.prepareStatement(
                    "SELECT requester_id,assignee_id FROM "+schema+".helpdesk_requests WHERE request_id=?")) {
                s.setObject(1,UUID.fromString(id));
                try(var row=s.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo(ids.get("employee"));
                    assertThat(row.getString(2)).isEqualTo(ids.get("worker"));
                }
            }
            // A formerly assigned worker loses access immediately after active membership is revoked.
            try(var c=admin();var s=c.prepareStatement(
                    "UPDATE "+schema+".tenant_memberships SET active=false WHERE tenant_id='tenant-a' AND actor_id=?")) {
                s.setString(1,ids.get("worker"));assertThat(s.executeUpdate()).isEqualTo(1);
            }
            b06Call(http,base,tokens.get("worker"),"GET","/"+id,null,404);
            assertThat(b06Call(http,base,tokens.get("employee"),"GET","/"+id,null,200)
                    .path("state").asText()).isEqualTo("CONFIRMED");
        }
    }

    private JsonNode b06Call(HttpClient http,String base,String bearer,String method,
            String suffix,String body,int expectedStatus) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+API+suffix));
        if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        // Fail without logging raw access tokens or possibly private response payloads.
        assertThat(response.statusCode()).as("B06 %s %s",method,suffix).isEqualTo(expectedStatus);
        return json.readTree(response.body().isBlank()?"null":response.body());
    }

    @Test void fullBootApplicationStartsAndServesGuardedRoutesOverHttp() throws Exception {
        try(var app=new SpringApplicationBuilder(com.tricore.dxos.DxosApplication.class).run(
                "--server.address=127.0.0.1","--server.port=0","--spring.main.banner-mode=off",
                "--spring.flyway.enabled=false","--spring.jpa.hibernate.ddl-auto=none",
                "--spring.datasource.url="+url+"?currentSchema="+schema+",pg_catalog,pg_temp",
                "--spring.datasource.username="+runtimeRole,"--spring.datasource.password="+runtimePassword,
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri="+issuer,
                "--tricore.security.oidc.audience=tricore-api","--tricore.security.oidc.resource-client-id=tricore-api",
                "--dxos.workflow.schema="+schema,"--dxos.helpdesk.enabled=true",
                "--dxos.helpdesk.jdbc-url="+url,"--dxos.helpdesk.username="+runtimeRole,
                "--dxos.helpdesk.password="+runtimePassword,"--dxos.helpdesk.schema="+schema,"--dxos.helpdesk.tenant=tenant-a")) {
            int port=((ServletWebServerApplicationContext)app).getWebServer().getPort();
            try(var client=HttpClient.newHttpClient()) {
                String endpoint="http://127.0.0.1:"+port;
                var unauthorized=client.send(HttpRequest.newBuilder(URI.create(endpoint+API)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(unauthorized.statusCode()).isEqualTo(401);
                String bearer="Bearer "+token("employee",AUTHORITIES,key);
                var create=client.send(HttpRequest.newBuilder(URI.create(endpoint+API)).header("Authorization",bearer)
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(createBody())).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(create.statusCode()).as(create.body()).isEqualTo(201);
                String id=json.readTree(create.body()).path("id").asText();
                var detail=client.send(HttpRequest.newBuilder(URI.create(endpoint+API+"/"+id)).header("Authorization",bearer).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
                assertThat(json.readTree(detail.body()).path("state").asText()).isEqualTo("SUBMITTED");
                var prototype=client.send(HttpRequest.newBuilder(URI.create(endpoint+"/api/v1/requests")).header("Authorization",bearer).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(prototype.statusCode()).isEqualTo(404);
            }
        }
    }

    @Test void unknownCommitIsNotRetriedAndCanBeReconciled() throws Exception {
        String id=inProgress(); var commits=new AtomicInteger();
        DataSource uncertain=new DelegatingDataSource(runtime) {
            @Override public Connection getConnection() throws SQLException {
                Connection actual=super.getConnection();
                return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
                    try {
                        Object result=method.invoke(actual,args);
                        if(method.getName().equals("commit")) { commits.incrementAndGet(); throw new SQLException("lost acknowledgement"); }
                        return result;
                    } catch(InvocationTargetException exception) { throw exception.getCause(); }
                });
            }
        };
        assertThatThrownBy(() -> serviceAs("worker",uncertain).transition(UUID.fromString(id),"resolve",2,"Persisted once"))
                .isInstanceOfSatisfying(WorkflowException.class,e -> assertThat(e.code()).isEqualTo(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN));
        assertThat(commits).hasValue(1);
        expect(get(API+"/"+id),"employee",200).andExpect(jsonPath("$.state").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Persisted once")).andExpect(jsonPath("$.version").value(3));
        expect(get(API+"/"+id+"/history"),"employee",200).andExpect(jsonPath("$.length()").value(3));
    }

    @Test void runtimeCannotReadOrWriteProtectedTablesDirectly() throws Exception {
        try(var c=runtime.getConnection()) {
            for(String table:TABLES) assertThatThrownBy(() -> sql(c,"SELECT * FROM "+schema+"."+table))
                    .isInstanceOfSatisfying(SQLException.class,e -> assertThat(e.getSQLState()).isEqualTo("42501"));
            assertThatThrownBy(() -> sql(c,"UPDATE "+schema+".helpdesk_requests SET resolution='bypass'"))
                    .isInstanceOfSatisfying(SQLException.class,e -> assertThat(e.getSQLState()).isEqualTo("42501"));
        }
    }

    @ParameterizedTest @ValueSource(strings={"\"0\"","0.0","-1","9223372036854775808","null"})
    void invalidVersionsAreRejectedWithoutWrite(String version) throws Exception {
        String id=create("employee").path("id").asText(); var before=snapshot();
        expect(post(API+"/"+id+"/triage").content("{\"expectedVersion\":"+version+"}"),"coordinator",400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void paginationAndPrototypeIsolation() throws Exception {
        create("employee");create("employee");
        var first=read(expect(get(API+"?limit=1"),"employee",200));
        assertThat(first.path("items")).hasSize(1);
        String cursor=first.path("nextCursor").asText(); assertThat(cursor).isNotEmpty();
        var next=read(expect(get(API+"?limit=1&after="+cursor),"employee",200));
        assertThat(next.path("items").get(0).path("id").asText()).isNotEqualTo(first.path("items").get(0).path("id").asText());
        expect(get("/api/v1/requests"),"employee",404);
        expect(get("/api/v1/workflow-instances/anything"),"employee",404);
        expect(get(API+"?limit=101"),"employee",400);
        expect(get(API+"/invalid-id"),"employee",400);
    }

    private JsonNode create(String actor) throws Exception { return read(expect(post(API).content(createBody()),actor,201)); }
    private String triaged() throws Exception {
        String id=create("employee").path("id").asText();
        expect(post(API+"/"+id+"/triage").content("{\"expectedVersion\":0}"),"coordinator",200); return id;
    }
    private String inProgress() throws Exception {
        String id=triaged(); assign(id,"worker",0,200);
        expect(post(API+"/"+id+"/start").content("{\"expectedVersion\":1}"),"worker",200); return id;
    }
    private void assign(String id,String worker,long version,int status) throws Exception {
        expect(post(API+"/"+id+"/assign").content(assignBody(worker,version)),"coordinator",status);
    }
    private String assignBody(String worker,long version) throws Exception {
        return json.writeValueAsString(Map.of("assigneeId",actors.get(worker),"expectedAssignmentVersion",version));
    }
    private String createBody() { return "{\"title\":\"Software issue\",\"description\":\"Please repair\",\"requestType\":\"IT_HELPDESK\"}"; }
    private ResultActions expect(MockHttpServletRequestBuilder request,String actor,int code) throws Exception {
        return mvc.perform(request.header("Authorization","Bearer "+token(actor,AUTHORITIES,key)).contentType("application/json"))
                .andExpect(status().is(code));
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private String token(String subject,List<String> roles,RSAKey signer) throws Exception {
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience("tricore-api")
                        .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("typ","Bearer").claim("resource_access",Map.of("tricore-api",Map.of("roles",roles))).build());
        jwt.sign(new RSASSASigner(signer));return jwt.serialize();
    }
    private HelpdeskService serviceAs(String subject,DataSource database) {
        CurrentPrincipal principal=() -> new AuthenticatedPrincipal(actors.get(subject),subject,
                Set.of(new Authority("workflow:read"),new Authority("workflow:transition")),true);
        return new HelpdeskService(new PostgresWorkflowPersistence(database,schema),principal,schema,"tenant-a",Clock.systemUTC());
    }
    private static final class CommitGate {
        final CountDownLatch entered=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
        final AtomicInteger pid=new AtomicInteger();
    }
    private static final class PidCapture {
        final CountDownLatch ready=new CountDownLatch(1);
        final AtomicInteger pid=new AtomicInteger();
    }
    private DataSource captureHttpBackend(DataSource source) {
        return new DelegatingDataSource(source) {
            @Override public Connection getConnection() throws SQLException {
                Connection actual=super.getConnection();
                PidCapture capture=httpPidCapture.get();
                if(capture==null) return actual;
                try {
                    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
                        if(method.getName().equals("prepareStatement") && args!=null && args.length>0
                                && args[0] instanceof String sql && sql.contains(".helpdesk_request(")
                                && capture.pid.get()==0) {
                            int pid=(int)number(actual,"SELECT pg_backend_pid()");
                            if(capture.pid.compareAndSet(0,pid)) capture.ready.countDown();
                        }
                        try { return method.invoke(actual,args); }
                        catch(InvocationTargetException exception) { throw exception.getCause(); }
                    });
                } catch(RuntimeException | Error failure) {
                    try { actual.close(); } catch(Throwable closeFailure) { failure.addSuppressed(closeFailure); }
                    throw failure;
                }
            }
        };
    }
    private DataSource gateCommit(DataSource source,CommitGate gate) {
        return new DelegatingDataSource(source) {
            @Override public Connection getConnection() throws SQLException {
                Connection actual=super.getConnection();
                try {
                    gate.pid.set((int)number(actual,"SELECT pg_backend_pid()"));
                    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
                        if(method.getName().equals("commit")) {
                            gate.entered.countDown();
                            try {
                                if(!gate.release.await(20,TimeUnit.SECONDS)) throw new SQLException("commit gate timed out");
                            } catch(InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new SQLException("commit gate interrupted",interrupted);
                            }
                        }
                        try { return method.invoke(actual,args); }
                        catch(InvocationTargetException exception) { throw exception.getCause(); }
                    });
                } catch(SQLException | RuntimeException | Error failure) {
                    try { actual.close(); } catch(Throwable closeFailure) { failure.addSuppressed(closeFailure); }
                    throw failure;
                }
            }
        };
    }
    // Client timeouts bound JDBC waits; they do not prove PostgreSQL ended a failed transaction.
    // Failure cleanup still rolls back and closes A before fixture teardown.
    private Connection revocationHolder() throws SQLException {
        Properties properties=new Properties();
        properties.setProperty("user","helpdesk_test");
        String password=System.getenv("HELPDESK_DISPOSABLE_PASSWORD");
        properties.setProperty("password",password==null?"":password);
        properties.setProperty("loginTimeout","2");
        properties.setProperty("connectTimeout","2");
        properties.setProperty("socketTimeout","5");
        properties.setProperty("cancelSignalTimeout","1");
        Connection connection=DriverManager.getConnection(url,properties);
        try {
            connection.setNetworkTimeout(Runnable::run,5_000);
            if(connection.getNetworkTimeout()!=5_000) throw new SQLException("revocation holder network timeout unavailable");
            return connection;
        } catch(SQLException | RuntimeException | Error failure) {
            try { connection.close(); } catch(Throwable closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    private static long boundedNumber(Connection connection,String query) throws SQLException {
        try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(3);
            try(var rows=statement.executeQuery(query)) { rows.next();return rows.getLong(1); }
        }
    }
    private static void boundedSql(Connection connection,String query) throws SQLException {
        try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(3);
            statement.execute(query);
        }
    }
    // pgJDBC socket/network and statement timeouts bound normal I/O. JDBC close has no separately
    // proven wall-clock bound; an abort worker could outlive this disposable fixture's cleanup.
    private Connection inspector() throws SQLException {
        Properties properties=new Properties();
        properties.setProperty("user","helpdesk_test");
        String password=System.getenv("HELPDESK_DISPOSABLE_PASSWORD");
        properties.setProperty("password",password==null?"":password);
        properties.setProperty("loginTimeout","2");
        properties.setProperty("connectTimeout","2");
        properties.setProperty("socketTimeout","2");
        properties.setProperty("cancelSignalTimeout","1");
        Connection connection=DriverManager.getConnection(url,properties);
        try {
            connection.setNetworkTimeout(Runnable::run,2_000);
            if(connection.getNetworkTimeout()!=2_000) throw new SQLException("inspector network timeout unavailable");
            return connection;
        } catch(SQLException | RuntimeException failure) {
            try { connection.close(); } catch(Throwable closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    private boolean waitForBlockedBy(Connection observer,int blockedPid,int blockerPid) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(System.nanoTime()<deadline) {
            try(var statement=observer.prepareStatement("SELECT ?=ANY(pg_catalog.pg_blocking_pids(?))")) {
                statement.setQueryTimeout(1);
                statement.setInt(1,blockerPid);
                statement.setInt(2,blockedPid);
                try(var rows=statement.executeQuery()) { rows.next(); if(rows.getBoolean(1)) return true; }
            }
            Thread.sleep(40);
        }
        return false;
    }
    private void finishRace(ExecutorService executor,Future<?>... futures) {
        for(Future<?> future:futures) if(future!=null && !future.isDone()) future.cancel(true);
        executor.shutdownNow();
        try {
            if(!executor.awaitTermination(25,TimeUnit.SECONDS)) {
                unfinishedRaceWorker.set(true);
                throw new AssertionError("race worker did not terminate within 25 seconds; fixture cleanup is unsafe");
            }
        } catch(InterruptedException interrupted) {
            unfinishedRaceWorker.set(true);
            Thread.currentThread().interrupt();
            throw new AssertionError("race worker termination interrupted; fixture cleanup is unsafe",interrupted);
        }
    }
    private void finishRevocationRace(Connection administrator,boolean rollback,ExecutorService executor,
                                      Future<?> waiting,Throwable primaryFailure) throws SQLException {
        Throwable cleanupFailure=null;
        if(rollback) try { administrator.rollback(); }
        catch(SQLException | RuntimeException | Error failure) { cleanupFailure=failure; }
        try { finishRace(executor,waiting); }
        catch(RuntimeException | Error failure) {
            if(cleanupFailure==null) cleanupFailure=failure;
            else cleanupFailure.addSuppressed(failure);
        }
        if(cleanupFailure==null) return;
        if(primaryFailure!=null) { primaryFailure.addSuppressed(cleanupFailure);return; }
        if(cleanupFailure instanceof SQLException sqlFailure) throw sqlFailure;
        if(cleanupFailure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        throw (Error)cleanupFailure;
    }
    private record HistoryRecord(long version,String transition,String source,String target,String actor) {}
    private List<HistoryRecord> historyRecords(String id) throws Exception {
        try(var c=admin();var statement=c.prepareStatement("SELECT x.resulting_runtime_version,x.transition_id,"
                +"x.source_state,x.target_state,x.actor_id FROM "+schema+".workflow_transition_history x"
                +" JOIN "+schema+".helpdesk_requests h ON h.instance_id=x.instance_id"
                +" WHERE h.request_id=? ORDER BY x.resulting_runtime_version")) {
            statement.setObject(1,UUID.fromString(id));
            try(var rows=statement.executeQuery()) {
                List<HistoryRecord> records=new ArrayList<>();
                while(rows.next()) records.add(new HistoryRecord(rows.getLong(1),rows.getString(2),
                        rows.getString(3),rows.getString(4),rows.getString(5)));
                return List.copyOf(records);
            }
        }
    }
    private record RequestState(String assignee,long assignmentVersion,String resolution,String state,
                                long workflowVersion,long historyCount,long startCount,long resolveCount) {}
    private RequestState requestState(String id) throws Exception {
        try(var c=admin();var statement=c.prepareStatement("SELECT h.assignee_id,h.assignment_version,h.resolution,"
                +"i.current_state,i.runtime_version,"
                +"(SELECT count(*) FROM "+schema+".workflow_transition_history x WHERE x.instance_id=h.instance_id),"
                +"(SELECT count(*) FROM "+schema+".workflow_transition_history x WHERE x.instance_id=h.instance_id AND x.transition_id='start_work'),"
                +"(SELECT count(*) FROM "+schema+".workflow_transition_history x WHERE x.instance_id=h.instance_id AND x.transition_id='resolve')"
                +" FROM "+schema+".helpdesk_requests h JOIN "+schema+".workflow_instances i ON i.instance_id=h.instance_id"
                +" WHERE h.request_id=?")) {
            statement.setObject(1,UUID.fromString(id));
            try(var rows=statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                var state=new RequestState(rows.getString(1),rows.getLong(2),rows.getString(3),rows.getString(4),
                        rows.getLong(5),rows.getLong(6),rows.getLong(7),rows.getLong(8));
                assertThat(rows.next()).isFalse();
                return state;
            }
        }
    }
    private void grant(String id,String actor) throws Exception {
        try(var c=admin()) { for(String operation:List.of("read","transition"))
            sql(c,"INSERT INTO "+schema+".resource_acl VALUES('tenant-a','mvp_request','"+id+"','"+actors.get(actor)+"','workflow:"+operation+"',true)"); }
    }
    private Map<String,String> snapshot() throws Exception {
        Map<String,String> result=new LinkedHashMap<>();
        try(var c=admin()) { for(String table:TABLES) try(var s=c.createStatement();var r=s.executeQuery(
                "SELECT COALESCE(jsonb_agg(row ORDER BY row::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) AS row FROM "+schema+"."+table+" t) rows")) {
            r.next();result.put(table,r.getString(1));
        } } return result;
    }
    private Connection admin() throws SQLException { String secret=System.getenv("HELPDESK_DISPOSABLE_PASSWORD"); return DriverManager.getConnection(url,"helpdesk_test",secret==null?"":secret); }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static void sql(Connection c,String sql) throws SQLException { try(var s=c.createStatement()) { s.execute(sql); } }
    private static long number(Connection c,String sql) throws SQLException { try(var s=c.createStatement();var r=s.executeQuery(sql)) { r.next();return r.getLong(1); } }

    @AfterAll void cleanup() throws Exception {
        if(unfinishedRaceWorker.get()) throw new AssertionError("race worker remains active; refusing fixture schema cleanup");
        if(context!=null) context.close();if(oidc!=null) oidc.stop(0);
        if(schema!=null) { assertThat(schema).matches("helpdesk_[a-f0-9]{32}");
            try(var c=admin()) { sql(c,"DROP SCHEMA IF EXISTS "+schema+" CASCADE");sql(c,"DROP ROLE IF EXISTS "+runtimeRole);sql(c,"DROP ROLE IF EXISTS "+ownerRole); }
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    static class Web {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean HelpdeskService helpdeskService(PostgresWorkflowPersistence persistence,CurrentPrincipal principal,@Value("${test.schema}") String schema) {
            return new HelpdeskService(persistence,principal,schema,"tenant-a",Clock.systemUTC());
        }
    }
}
