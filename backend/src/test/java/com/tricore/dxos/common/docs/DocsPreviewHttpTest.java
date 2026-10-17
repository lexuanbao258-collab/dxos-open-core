package com.tricore.dxos.common.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actual embedded HTTP preview with the real MVC controllers, no datasource, no Flyway and no
 * executable business API. This is documentation-only and is NOT production DxosApplication E2E.
 */
@SpringBootTest(classes = DocsPreviewApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=docs-local"})
class DocsPreviewHttpTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate http;
    @Autowired private ApplicationContext context;
    @Autowired private ObjectMapper json;

    @Test
    void activeOpenApiJsonOnlyDescribesCurrentlyRegisteredHealthAndRequestRoutes() throws Exception {
        ResponseEntity<String> response = http.getForEntity(url("/v3/api-docs/active"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode document = json.readTree(response.getBody());

        assertThat(document.path("openapi").asText()).startsWith("3.");
        Set<String> actualPaths = java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(document.path("paths").fieldNames(), 0), false)
                .collect(Collectors.toSet());

        assertThat(actualPaths).containsExactlyInAnyOrder(
                "/api/v1/health",
                "/api/v1/requests",
                "/api/v1/requests/{id}",
                "/api/v1/requests/{id}/assign",
                "/api/v1/requests/{id}/start",
                "/api/v1/requests/{id}/resolve",
                "/api/v1/requests/{id}/confirm",
                "/api/v1/requests/{id}/close",
                "/api/v1/requests/{id}/history");
        assertThat(actualPaths).noneMatch(path -> path.startsWith("/api/v1/workflow-instances"));
        assertThat(document.path("paths").path("/api/v1/requests").has("post")).isTrue();
    }

    @Test
    void previewHasNoDatasourceAndCannotExecuteAnyBusinessEndpoint() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(http.getForEntity(url("/api/v1/health"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity(url("/api/v1/requests"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.postForEntity(url("/api/v1/requests"), "{}", String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void swaggerUiConfigurationIsAvailableOnlyInDocsPreview() {
        ResponseEntity<String> config = http.getForEntity(url("/v3/api-docs/swagger-config"), String.class);
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config.getBody()).contains("/v3/api-docs/active");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
