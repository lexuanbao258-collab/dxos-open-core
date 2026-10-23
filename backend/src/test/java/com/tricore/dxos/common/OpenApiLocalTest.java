package com.tricore.dxos.common;

import com.tricore.dxos.request.service.RequestService;
import com.tricore.dxos.request.service.RequestWorkflowService;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import com.tricore.dxos.infrastructure.workflow.PostgresWorkflowPersistence;
import com.tricore.dxos.gateway.workflow.controller.WorkflowController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "dxos.workflow.schema=docs_test"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OpenApiLocalTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;
    @MockitoBean RequestService requestService;
    @MockitoBean RequestWorkflowService requestWorkflowService;
    @MockitoBean WorkflowRuntime workflowRuntime;
    @MockitoBean PostgresWorkflowPersistence workflowPersistence;

    @Test
    void documentsOnlyRegisteredRoutesAndSafeSchemas() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode spec = mapper.readTree(json);
        assertThat(spec.path("openapi").asText()).startsWith("3.");
        Set<String> paths = new HashSet<>();
        spec.path("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).isEqualTo(Set.of(
                "/api/v1/health", "/api/v1/requests", "/api/v1/requests/{id}",
                "/api/v1/requests/{id}/assign", "/api/v1/requests/{id}/start",
                "/api/v1/requests/{id}/resolve", "/api/v1/requests/{id}/confirm",
                "/api/v1/requests/{id}/close", "/api/v1/requests/{id}/history"));
        int operations = 0;
        for (JsonNode path : spec.path("paths")) {
            operations += path.size();
        }
        assertThat(operations).isEqualTo(10);
        assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText())
                .isEqualTo("bearer");
        assertThat(spec.path("security").isMissingNode()).isTrue();
        assertThat(spec.path("paths").path("/api/v1/requests").path("post").path("responses").has("201")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/requests").path("post").path("responses")
                .path("201").toString())
                .contains("RequestResponse");
        assertThat(spec.path("paths").path("/api/v1/requests").path("post").path("responses")
                .path("400").path("content").path("application/json").path("schema").toString())
                .contains("ApiError");
        assertThat(spec.path("paths").path("/api/v1/requests/{id}/assign").path("post").path("responses").has("409")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/requests/{id}/assign").path("post").path("responses").has("200")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/health").has("get")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/requests/{id}/history").has("get")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/requests/{id}/resolve").has("post")).isTrue();
        assertThat(spec.path("components").path("schemas").path("CreateRequestDto").path("required").toString())
                .contains("title", "description", "requestType");
        assertThat(spec.path("components").path("schemas").path("CreateRequestDto")
                .path("properties").path("title").path("maxLength").asInt()).isEqualTo(200);
        assertThat(spec.path("components").path("schemas").path("AssignRequestDto")
                .path("properties").path("assigneeId").path("maxLength").asInt()).isEqualTo(100);
        assertThat(spec.path("components").path("schemas").path("ResolveRequestDto")
                .path("properties").path("resolution").path("maxLength").asInt()).isEqualTo(4000);
        assertThat(spec.path("components").path("schemas").path("ApiError").path("properties").has("errors")).isTrue();
        assertThat(json).doesNotContain("DB_PASSWORD", "DB_HOST", "jdbc:postgresql:", "X-User", "X-Role", "client_secret");
    }

    @Test
    void servesSwaggerUiAndKeepsWorkflowHttpAbsent() throws Exception {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(context.getBeansOfType(WorkflowController.class)).isEmpty();
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/workflow-instances/test")).andExpect(status().isNotFound());
    }
}
