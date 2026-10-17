package com.tricore.dxos.common.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks checked-in profile defaults without starting the backend or contacting any database.
 * Config shape tests do not prove that Swagger UI runtime honored each setting: that needs HTTP smoke.
 */
class OpenApiProfileDefaultsTest {
    @Test
    void defaultConfigurationDisablesBothDocumentationEndpoints() throws IOException {
        assertThat(value("application.yml", "springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(value("application.yml", "springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Test
    void explicitLocalProfileBindsOnlyLoopbackAndDisablesSwaggerWrites() throws IOException {
        assertThat(value("application-docs-local.yml", "server.address")).isEqualTo("127.0.0.1");
        assertThat(value("application-docs-local.yml", "springdoc.api-docs.enabled")).isEqualTo(true);
        assertThat(value("application-docs-local.yml", "springdoc.swagger-ui.enabled")).isEqualTo(true);
        assertThat(value("application-docs-local.yml", "springdoc.swagger-ui.disable-swagger-default-url"))
                .isEqualTo(true);

        // Spring Boot's flattened PropertySource may omit an empty YAML collection entirely,
        // so getProperty("...supported-submit-methods") is NOT a reliable way to verify [].
        // Parse the checked-in YAML structure directly. Actual Swagger UI behavior needs an HTTP test.
        assertThat(rawYamlValue("application-docs-local.yml",
                "springdoc", "swagger-ui", "supported-submit-methods"))
                .isEqualTo(List.of());
        // Springdoc already lists GroupedOpenApi("active") automatically in Swagger UI.
        // A second manual urls entry previously produced duplicate choices.
        assertThat(rawYamlValue("application-docs-local.yml",
                "springdoc", "swagger-ui", "urls")).isNull();
    }

    private static Object value(String resource, String key) throws IOException {
        var sources = new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
        assertThat(sources).isNotEmpty();
        return sources.get(0).getProperty(key);
    }

    private static Object rawYamlValue(String resource, String... keys) throws IOException {
        try (var input = new ClassPathResource(resource).getInputStream()) {
            Object cursor = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            for (String key : keys) {
                assertThat(cursor).as("YAML mapping for " + key).isInstanceOf(Map.class);
                cursor = ((Map<?, ?>) cursor).get(key);
            }
            return cursor;
        }
    }
}
