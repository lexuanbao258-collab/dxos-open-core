package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import com.tricore.dxos.gateway.workflow.dto.WorkflowInstanceResponse;
import com.tricore.dxos.infrastructure.identity.security.SecurityConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real local OIDC discovery/JWKS and six-port LOGIN; never starts the production application. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Stage6WorkflowReadHttpIntegrationTest {
    private static final String PATH = "/__test_only/stage6/workflow-instances/";
    private static final String INSTANCE = "wf_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String FOREIGN_INSTANCE = "wf_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String READER = "người-đọc";
    private static final List<String> TABLES = List.of("tenant_guards", "tenant_memberships", "resource_registry",
            "resource_acl", "workflow_definition_bindings", "workflow_policy_audit", "workflow_definitions",
            "workflow_definition_states", "workflow_definition_transitions", "workflow_instances", "workflow_transition_history");
    private final Map<String, String> actors = new HashMap<>();
    private final AtomicInteger jwksRequests = new AtomicInteger();
    private HttpServer oidc;
    private RSAKey key;
    private String issuer, url, adminUser, adminPassword, schema, ownerRole, runtimeRole;
    private boolean fixtureCommitted;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ReadController controller;
    private DriverManagerDataSource runtime;
    private Map<String, String> before;

    @BeforeAll void prepare() throws Exception {
        url = required("STAGE6_DISPOSABLE_PG17_URL");
        assertThat(url).as("BLOCKED: only the approved disposable endpoint is permitted")
                .matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test");
        for (String flag : List.of("STAGE6_DISPOSABLE_PG17_APPROVAL", "STAGE6_LOGIN_ROLE_TEST_APPROVAL"))
            assertThat(required(flag)).as("BLOCKED: disposable fixture/LOGIN approval").isEqualTo("YES");
        assertThat(required("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")).isEqualTo("true");
        adminUser = required("STAGE6_DISPOSABLE_PG17_USER");
        assertThat(adminUser).isEqualTo("stage6_test");
        adminPassword = required("STAGE6_DISPOSABLE_PG17_PASSWORD");
        try (Connection connection = administrator()) {
            assertThat(query(connection, "SELECT current_database() ")).isEqualTo("stage6_disposable_test");
            assertThat(Integer.parseInt(query(connection, "SHOW server_version_num"))).isBetween(170000, 179999);
        }

        key = new RSAKeyGenerator(2048).keyID("stage6-read").generate();
        oidc = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + oidc.getAddress().getPort();
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        oidc.createContext("/.well-known/openid-configuration", exchange -> {
            byte[] body = json.writeValueAsBytes(Map.of("issuer", issuer, "jwks_uri", issuer + "/jwks"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        oidc.createContext("/jwks", exchange -> {
            jwksRequests.incrementAndGet();
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        oidc.start();

        String suffix = UUID.randomUUID().toString().replace("-", "");
        schema = "s6_http_" + suffix;
        ownerRole = "s6_http_owner_" + suffix;
        runtimeRole = "s6_http_rt_" + suffix;
        String password = UUID.randomUUID().toString();
        runtime = new DriverManagerDataSource(url, runtimeRole, password);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        runtime.setConnectionProperties(properties);
        PostgresWorkflowPersistence persistence = new PostgresWorkflowPersistence(runtime, schema);
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-oidc", Map.of(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer,
                "tricore.security.oidc.audience", "tricore-api",
                "tricore.security.oidc.resource-client-id", "tricore-api")));
        context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("persistence", persistence));
        context.register(SecurityConfig.class,
                Class.forName("com.tricore.dxos.infrastructure.identity.security.SpringSecurityCurrentPrincipal"),
                WebConfiguration.class, ReadController.class, ReadAdvice.class);
        context.refresh();
        controller = context.getBean(ReadController.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();

        // Resolve fixture identities through the real verified boundary, never from usernames/aliases.
        for (String subject : List.of(READER, "owner", "visible", "hidden", "foreign")) {
            var jwt = context.getBean(JwtDecoder.class).decode(token(subject, List.of("workflow:read")));
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationConverter().convert(jwt));
            try { actors.put(subject, context.getBean(CurrentPrincipal.class).requireVerified().subjectId()); }
            finally { SecurityContextHolder.clearContext(); }
        }
        try (Connection connection = administrator()) {
            connection.setAutoCommit(false);
            execute(connection, "CREATE ROLE " + ownerRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
            execute(connection, "CREATE SCHEMA " + schema);
            for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                    "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                    "src/test/resources/stage6/Stage6_guarded_read_candidate.sql",
                    "src/test/resources/stage6/Stage6_guarded_create_candidate.sql",
                    "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql"))
                execute(connection, Files.readString(Path.of(file)).replace("${flyway:defaultSchema}", schema));
            fixture(connection);
            execute(connection, "CREATE ROLE " + runtimeRole + " LOGIN PASSWORD '" + password
                    + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
            execute(connection, "REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
            execute(connection, "REVOKE ALL ON ALL TABLES IN SCHEMA " + schema + " FROM PUBLIC");
            execute(connection, "REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + schema + " FROM PUBLIC");
            execute(connection, "SET CONSTRAINTS ALL IMMEDIATE");
            for (String table : TABLES) execute(connection, "ALTER TABLE " + schema + "." + table + " OWNER TO " + ownerRole);
            List<String> functions = new ArrayList<>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT quote_ident(proname)||'('||pg_get_function_identity_arguments(oid)||')' FROM pg_proc WHERE pronamespace='" + schema + "'::regnamespace")) {
                while (rows.next()) functions.add(rows.getString(1));
            }
            assertThat(functions.stream().filter(f -> f.startsWith("wf_port_")).count()).isEqualTo(6);
            for (String function : functions) {
                execute(connection, "ALTER FUNCTION " + schema + "." + function + " OWNER TO " + ownerRole);
                if (function.startsWith("wf_port_")) execute(connection, "GRANT EXECUTE ON FUNCTION " + schema + "." + function + " TO " + runtimeRole);
            }
            execute(connection, "ALTER SCHEMA " + schema + " OWNER TO " + ownerRole);
            execute(connection, "GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
            connection.commit();
            fixtureCommitted = true;
        }
    }

    private void fixture(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO " + schema + ".tenant_guards VALUES('tenant-a',0),('tenant-b',0)");
        for (String subject : List.of(READER, "owner", "visible", "hidden"))
            execute(connection, "INSERT INTO " + schema + ".tenant_memberships VALUES('tenant-a','" + actors.get(subject) + "',true)");
        execute(connection, "INSERT INTO " + schema + ".tenant_memberships VALUES('tenant-b','" + actors.get("foreign") + "',true)");
        for (String side : List.of("a", "b")) {
            String owner = actors.get(side.equals("a") ? "owner" : "foreign");
            execute(connection, "INSERT INTO " + schema + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) VALUES('item','res-" + side + "','tenant-" + side + "','" + owner + "','ACTIVE')");
            execute(connection, "INSERT INTO " + schema + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,resulting_acl_revision,actor_id,actor_source_ref,action,change_set) VALUES('" + UUID.randomUUID() + "','tenant-" + side + "',NULL,0,'" + owner + "','test-oidc','bootstrap','[{\"synthetic\":true}]')");
        }
        execute(connection, "INSERT INTO " + schema + ".resource_acl VALUES('tenant-a','item','res-a','" + actors.get(READER) + "','workflow:read',true),('tenant-a','item','res-a','" + actors.get("visible") + "','workflow:transition',true)");
        execute(connection, "INSERT INTO " + schema + ".workflow_definitions(definition_id,definition_version,initial_state) VALUES('publication',7,'draft')");
        execute(connection, "INSERT INTO " + schema + ".workflow_definition_states VALUES('publication',7,'draft'),('publication',7,'done')");
        execute(connection, "INSERT INTO " + schema + ".workflow_definition_transitions VALUES('publication',7,'finish','draft','done')");
        execute(connection, "INSERT INTO " + schema + ".workflow_definition_bindings VALUES('tenant-a','item','publication',7,true)");
        for (String id : List.of(INSTANCE, FOREIGN_INSTANCE)) {
            String side = id.equals(INSTANCE) ? "a" : "b";
            execute(connection, "INSERT INTO " + schema + ".workflow_instances VALUES('" + id + "','publication',7,'item','res-" + side + "','done',1,'COMPLETED',0,123,1,456)");
            execute(connection, "INSERT INTO " + schema + ".workflow_transition_history VALUES('" + id + "',1,'finish','draft','done','" + actors.get(side.equals("a") ? READER : "foreign") + "',1,456)");
        }
    }

    @BeforeEach void capture() throws Exception {
        controller.calls = 0;
        controller.observedActor = null;
        before = snapshot();
    }

    @AfterEach void unchanged() throws Exception {
        SecurityContextHolder.clearContext();
        if (before != null) assertThat(snapshot()).as("all protected rows/revisions unchanged on a fresh connection").isEqualTo(before);
    }

    @Test void validSignedReadUsesCanonicalActorAndRealJwks() throws Exception {
        mvc.perform(request(token(READER, List.of("workflow:read")), INSTANCE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.instanceId").value(INSTANCE))
                .andExpect(jsonPath("$.resourceId").value("res-a"))
                .andExpect(jsonPath("$.lifecycle").value("COMPLETED"))
                .andExpect(jsonPath("$.runtimeVersion").value(1));
        String expected = "oidc:" + Base64.getUrlEncoder().withoutPadding().encodeToString(issuer.getBytes(StandardCharsets.UTF_8))
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(READER.getBytes(StandardCharsets.UTF_8));
        assertThat(controller.observedActor).isEqualTo(expected).isEqualTo(actors.get(READER));
        assertThat(controller.calls).isEqualTo(1);
        assertThat(jwksRequests.get()).isPositive();
    }

    @Test void missingTokenIsUnauthorizedBeforeController() throws Exception {
        mvc.perform(get(PATH + INSTANCE)).andExpect(status().isUnauthorized());
        assertThat(controller.calls).isZero();
    }

    @Test void wrongSignatureIsUnauthorizedBeforeController() throws Exception {
        RSAKey wrong = new RSAKeyGenerator(2048).keyID(key.getKeyID()).generate();
        String token = signed(READER, List.of("workflow:read"), issuer, "tricore-api", Instant.now().plusSeconds(300), wrong);
        assertThat(SignedJWT.parse(token).verify(new RSASSAVerifier(key.toRSAPublicKey()))).isFalse();
        mvc.perform(request(token, INSTANCE)).andExpect(status().isUnauthorized());
        assertThat(controller.calls).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"expiry", "issuer", "audience"})
    void independentlyInvalidClaimWithValidSignatureIsUnauthorized(String invalid) throws Exception {
        String token = signed(READER, List.of("workflow:read"), invalid.equals("issuer") ? issuer + "/wrong" : issuer,
                invalid.equals("audience") ? "wrong-api" : "tricore-api",
                invalid.equals("expiry") ? Instant.now().minusSeconds(300) : Instant.now().plusSeconds(300), key);
        assertThat(SignedJWT.parse(token).verify(new RSASSAVerifier(key.toRSAPublicKey()))).isTrue();
        String description = switch (invalid) { case "expiry" -> "expired"; case "issuer" -> "iss claim"; default -> "required audience"; };
        assertThatThrownBy(() -> context.getBean(JwtDecoder.class).decode(token))
                .isInstanceOfSatisfying(JwtValidationException.class, error -> {
                    assertThat(error.getErrors()).hasSize(1);
                    assertThat(error.getErrors().iterator().next().getDescription()).containsIgnoringCase(description);
                });
        mvc.perform(request(token, INSTANCE)).andExpect(status().isUnauthorized());
        assertThat(controller.calls).isZero();
    }

    @Test void visibleReaderWithoutExactAuthorityIsForbidden() throws Exception {
        forbidden(READER, List.of("workflow:create"));
    }

    @Test void visibleMemberWithoutReadGrantIsForbidden() throws Exception {
        forbidden("visible", List.of("workflow:read"));
    }

    @Test void ownerWithoutReadGrantIsForbidden() throws Exception {
        forbidden("owner", List.of("workflow:read"));
    }

    @Test void foreignTenantIsHidden() throws Exception {
        mvc.perform(request(token(READER, List.of("workflow:read")), FOREIGN_INSTANCE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(controller.calls).isEqualTo(1);
    }

    @Test void nonvisibleMemberIsHidden() throws Exception {
        mvc.perform(request(token("hidden", List.of("workflow:read")), INSTANCE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(controller.calls).isEqualTo(1);
    }

    @Test void spoofedIdentityHeadersCannotGrantOwnerReadAccess() throws Exception {
        mvc.perform(request(token("owner", List.of("workflow:read")), INSTANCE)
                        .header("X-User", actors.get(READER)).header("X-Role", "workflow:read").header("X-Tenant", "tenant-a"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(controller.observedActor).isEqualTo(actors.get("owner"));
    }

    @Test void runtimeLoginHasOnlySixDefinerEntrypointsAndNoDirectTableOrRoleAccess() throws Exception {
        try (Connection connection = runtime.getConnection()) {
            assertThat(query(connection, "SELECT session_user")).isEqualTo(runtimeRole);
            assertThat(query(connection, "SELECT (rolsuper OR rolcreaterole OR rolcreatedb OR rolbypassrls OR rolinherit)::text FROM pg_roles WHERE rolname=current_user")).isEqualTo("false");
            assertThat(query(connection, "SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=current_user)")).isEqualTo("0");
            assertThat(query(connection, "SELECT has_schema_privilege(current_user,'" + schema + "','CREATE')::text")).isEqualTo("false");
            assertThat(query(connection, "SELECT count(*) FROM pg_proc WHERE pronamespace='" + schema + "'::regnamespace AND has_function_privilege(current_user,oid,'EXECUTE')")).isEqualTo("6");
            assertThat(query(connection, "SELECT count(*) FROM pg_proc p JOIN pg_roles r ON r.oid=p.proowner WHERE p.pronamespace='" + schema + "'::regnamespace AND p.proname LIKE 'wf_port_%' AND p.prosecdef AND r.rolname='" + ownerRole + "' AND NOT r.rolcanlogin AND p.proconfig @> ARRAY['search_path=pg_catalog, pg_temp']")).isEqualTo("6");
            for (String table : TABLES) {
                for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER", "MAINTAIN"))
                    assertThat(query(connection, "SELECT has_table_privilege(current_user,'" + schema + "." + table + "','" + privilege + "')::text")).as(table + ":" + privilege).isEqualTo("false");
                deniedSql(connection, "SELECT * FROM " + schema + "." + table);
                deniedSql(connection, "DELETE FROM " + schema + "." + table);
            }
            deniedSql(connection, "SET ROLE " + ownerRole);
            deniedSql(connection, "SET ROLE " + adminUser);
        }
    }

    private void forbidden(String subject, List<String> roles) throws Exception {
        mvc.perform(request(token(subject, roles), INSTANCE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(controller.calls).isEqualTo(1);
        assertThat(controller.observedActor).isEqualTo(actors.get(subject));
    }

    private MockHttpServletRequestBuilder request(String token, String instance) {
        return get(PATH + instance).header("Authorization", "Bearer " + token);
    }

    private String token(String subject, List<String> roles) throws Exception {
        return signed(subject, roles, issuer, "tricore-api", Instant.now().plusSeconds(300), key);
    }

    private String signed(String subject, List<String> roles, String tokenIssuer, String audience, Instant expiry, RSAKey signer) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(tokenIssuer).subject(subject).audience(audience)
                        .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expiry))
                        .claim("typ", "Bearer").claim("resource_access", Map.of("tricore-api", Map.of("roles", roles))).build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }

    private Map<String, String> snapshot() throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection observer = administrator()) {
            for (String table : TABLES) result.put(table, query(observer,
                    "SELECT COALESCE(jsonb_agg(row ORDER BY row::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) AS row FROM " + schema + "." + table + " t) rows"));
        }
        return result;
    }

    private Connection administrator() throws SQLException { return DriverManager.getConnection(url, adminUser, adminPassword); }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("BLOCKED: missing " + name);
        return value;
    }

    private static void deniedSql(Connection connection, String sql) {
        assertThatThrownBy(() -> execute(connection, sql)).isInstanceOfSatisfying(SQLException.class,
                failure -> assertThat(failure.getSQLState()).isEqualTo("42501"));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static String query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    @AfterAll void cleanup() throws Exception {
        if (context != null) context.close();
        if (oidc != null) oidc.stop(0);
        if (fixtureCommitted) {
            assertThat(schema).matches("s6_http_[a-f0-9]{32}");
            assertThat(ownerRole).matches("s6_http_owner_[a-f0-9]{32}");
            assertThat(runtimeRole).matches("s6_http_rt_[a-f0-9]{32}");
            try (Connection connection = administrator()) {
                connection.setAutoCommit(false);
                execute(connection, "DROP SCHEMA " + schema + " CASCADE");
                execute(connection, "DROP ROLE " + runtimeRole);
                execute(connection, "DROP ROLE " + ownerRole);
                connection.commit();
            }
        }
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    static class WebConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @RestController
    static class ReadController {
        private final CurrentPrincipal principal;
        private final PostgresWorkflowPersistence persistence;
        int calls;
        String observedActor;
        ReadController(CurrentPrincipal principal, PostgresWorkflowPersistence persistence) {
            this.principal = principal;
            this.persistence = persistence;
        }
        @GetMapping(PATH + "{instanceId}")
        WorkflowInstanceResponse read(@PathVariable String instanceId) {
            calls++;
            observedActor = principal.requireVerified().subjectId();
            var port = persistence.forStage6(principal, WorkflowOperation.READ, instanceId, null, null, null, null, null);
            return WorkflowInstanceResponse.from(new WorkflowRuntime(port, Clock.systemUTC()).loadInstance(instanceId));
        }
    }

    // Apply the production exception handlers to the test-only controller without copying status logic.
    @RestControllerAdvice(assignableTypes = ReadController.class)
    static class ReadAdvice extends WorkflowExceptionHandler { }
}
