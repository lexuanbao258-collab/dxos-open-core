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
        context.addBeanFactoryPostProcessor(f -> f.registerSingleton("helpdeskPersistence",new PostgresWorkflowPersistence(runtime,schema)));
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

    @Test void visibleWithoutExactTechnicalAuthorityIsForbidden() throws Exception {
        String id=create("employee").path("id").asText(); var before=snapshot();
        mvc.perform(get(API+"/"+id).header("Authorization","Bearer "+token("employee",List.of("workflow:read-all"),key)))
                .andExpect(status().isForbidden());
        assertThat(snapshot()).isEqualTo(before);
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
