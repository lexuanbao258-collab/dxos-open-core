package com.tricore.dxos.common.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the checked-in profile defaults without starting the backend or contacting any database.
 * These are configuration assertions, not proof of externally exposed network behavior.
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
        assertThat(value("application-docs-local.yml", "springdoc.swagger-ui.supported-submit-methods"))
                .isEqualTo(java.util.List.of());
    }

    private static Object value(String resource, String key) throws IOException {
        var sources = new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
        assertThat(sources).isNotEmpty();
        return sources.get(0).getProperty(key);
    }
}
