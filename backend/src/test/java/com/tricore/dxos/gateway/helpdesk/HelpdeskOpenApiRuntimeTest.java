package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.gateway.workflow.WorkflowJsonConfiguration;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import com.tricore.dxos.infrastructure.identity.security.SecurityConfig;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Springdoc + production filter chain; real local issuer; no JDBC or business invocation. */
@EnabledIfEnvironmentVariable(named = "DXOS_OPENAPI_LOCAL_REVIEW", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HelpdeskOpenApiRuntimeTest {
    private ServletWebServerApplicationContext context;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HttpClient http = HttpClient.newHttpClient();
    private String base;
    private JsonNode spec;

    @BeforeAll void startDocsOnlyContext() throws Exception {
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(DocsTestApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=0", "--dxos.openapi.enabled=true", "--dxos.helpdesk.enabled=true",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:8182/realms/tricore",
                "--tricore.security.oidc.audience=tricore-api", "--tricore.security.oidc.resource-client-id=tricore-api",
                "--springdoc.api-docs.version=OPENAPI_3_1", "--spring.main.banner-mode=off");
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/v3/api-docs/it-helpdesk-mvp")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        spec = json.readTree(response.body());
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec).remove("servers");
        var output = Path.of("build/openapi-review/it-helpdesk.p1.springdoc-test.local.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n", StandardCharsets.UTF_8);
    }

    @AfterAll void closeContext() {
        if (context != null) { verifyNoInteractions(context.getBean(HelpdeskService.class)); context.close(); }
    }

    @Test void generatedRequestSchemasHaveAllP1ConstraintsAndKeepNineSecuredOperations() {
        assertThat(spec.path("openapi").asText()).startsWith("3.1.");
        int count = 0;
        for (var paths = spec.path("paths").fields(); paths.hasNext();) {
            for (var methods = paths.next().getValue().fields(); methods.hasNext();) {
                var op = methods.next();
                if (!Set.of("get", "post").contains(op.getKey())) continue;
                count++;
                assertThat(op.getValue().path("security").toString()).contains("bearerAuth");
                if (op.getKey().equals("post")) assertThat(op.getValue().path("requestBody").path("required").asBoolean()).isTrue();
            }
        }
        assertThat(count).isEqualTo(9);
        for (String name : List.of("Create", "Version", "Assign", "Resolve"))
            assertThat(schema(name).path("additionalProperties").isBoolean() && !schema(name).path("additionalProperties").asBoolean()).isTrue();
        for (String[] field : List.of(new String[]{"Create","title"}, new String[]{"Create","description"}, new String[]{"Assign","assigneeId"}, new String[]{"Resolve","resolution"})) {
            var value = schema(field[0]).path("properties").path(field[1]);
            assertThat(value.path("minLength").asInt()).isEqualTo(1);
            assertThat(value.path("pattern").asText()).isNotBlank();
        }
    }

    @Test void oas31NullableFieldsAcceptNullWhileNonNullIncludedFieldsStayOptional() throws Exception {
        for (String[] field : List.of(new String[]{"RequestView","assigneeId"}, new String[]{"RequestView","resolution"}, new String[]{"Page","nextCursor"}, new String[]{"AssigneeOption","displayName"})) {
            JsonNode property = schema(field[0]).path("properties").path(field[1]);
            assertThat(types(property)).containsExactlyInAnyOrder("string", "null");
            assertThat(property.has("nullable")).isFalse();
        }
        assertThat(schema("Page").path("properties").path("nextCursor").path("format").asText()).isEqualTo("uuid");
        for (String field : List.of("requesterDisplayName","assigneeDisplayName","eligibleAssignees")) {
            assertThat(types(schema("RequestView").path("properties").path(field))).doesNotContain("null");
            assertThat(schema("RequestView").path("required").toString()).doesNotContain('"' + field + '"');
        }
        var view = new HelpdeskService.RequestView(UUID.randomUUID(), "title", "description", "IT_HELPDESK", "requester", null, null, 0L, "wf_test", "SUBMITTED", 0L, List.of(), null, null, null);
        JsonNode value = json.valueToTree(view);
        assertThat(value.path("assigneeId").isNull()).isTrue();
        assertThat(value.path("resolution").isNull()).isTrue();
        assertThat(value.has("requesterDisplayName") || value.has("assigneeDisplayName") || value.has("eligibleAssignees")).isFalse();
        assertThat(json.valueToTree(new HelpdeskService.Page(List.of(), null)).path("nextCursor").isNull()).isTrue();
        assertThat(json.valueToTree(new HelpdeskService.AssigneeOption("worker", null)).path("displayName").isNull()).isTrue();
    }

    @Test void errorSchemasArePresentOnlyOnSourceSupportedRouteStatuses() {
        for (var paths = spec.path("paths").fields(); paths.hasNext();) {
            var path = paths.next();
            for (var methods = path.getValue().fields(); methods.hasNext();) {
                var method = methods.next();
                if (!Set.of("get","post").contains(method.getKey())) continue;
                Set<String> errors = new HashSet<>(Set.of("400","401","403","404","500","503"));
                if (method.getKey().equals("post")) {
                    errors.addAll(Set.of("409","415"));
                    if (List.of("/triage","/start","/resolve","/confirm").stream().anyMatch(path.getKey()::endsWith)) errors.add("422");
                }
                var responses = method.getValue().path("responses");
                for (String code : errors)
                    assertThat(responses.path(code).path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiError");
                if (!errors.contains("422")) assertThat(responses.has("422")).isFalse();
                if (method.getKey().equals("get")) assertThat(responses.has("409") || responses.has("415")).isFalse();
            }
        }
        assertThat(schema("ApiError").path("properties").size()).isEqualTo(4);
        assertThat(schema("ApiError").path("properties").path("errors").path("additionalProperties").path("type").asText()).isEqualTo("string");
    }

    @Test void realProductionFilterChainReturnsApiErrorForMissingAndMalformedTokensWithoutCallingBusiness() throws Exception {
        for (var paths = spec.path("paths").fields(); paths.hasNext();) {
            var path = paths.next();
            for (var methods = path.getValue().fieldNames(); methods.hasNext();) {
                String method = methods.next();
                if (!Set.of("get","post").contains(method)) continue;
                String endpoint = path.getKey().replace("{id}", "00000000-0000-0000-0000-000000000001");
                var request = HttpRequest.newBuilder(URI.create(base + endpoint)).method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody()).build();
                assert401(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
            }
        }
        var invalid = HttpRequest.newBuilder(URI.create(base + "/api/v1/mvp/requests")).header("Authorization","Bearer deliberately.invalid.token").GET().build();
        assert401(http.send(invalid, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private void assert401(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(401);
        assertThat(body.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.path("message").isTextual() && body.path("errors").isObject()).isTrue();
    }

    private JsonNode schema(String name) { return spec.path("components").path("schemas").path(name); }
    private static List<String> types(JsonNode value) {
        JsonNode type = value.path("type");
        if (type.isTextual()) return List.of(type.asText());
        List<String> values = new ArrayList<>(); type.forEach(item -> values.add(item.asText())); return values;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({HelpdeskController.class, HelpdeskOpenApiConfiguration.class, WorkflowJsonConfiguration.class, WorkflowExceptionHandler.class, SecurityConfig.class})
    static class DocsTestApplication {
        @Bean HelpdeskService helpdeskService() { return mock(HelpdeskService.class); }
    }
}
