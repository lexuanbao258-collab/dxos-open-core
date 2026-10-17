package com.tricore.dxos.common.docs;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP requests against the test-only, database-free MVC preview *without* docs-local.
 * This does not claim a production DxosApplication startup or OIDC verification.
 * It checks that default config leaves documentation endpoints unregistered.
 */
@SpringBootTest(classes = DocsPreviewApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=docs-disabled-test"})
class DocsDisabledByDefaultHttpTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate http;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;

    @Test
    void baseConfigurationDisablesSwaggerJsonUiAndConfigurationOverHttp() {
        assertThat(environment.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(GroupedOpenApi.class)).isEmpty();

        for (String endpoint : new String[]{
                "/v3/api-docs",
                "/v3/api-docs/active",
                "/v3/api-docs/swagger-config",
                "/swagger-ui.html",
                "/swagger-ui/index.html"
        }) {
            assertThat(http.getForEntity(url(endpoint), String.class).getStatusCode())
                    .as("Docs disabled by default at %s", endpoint)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
