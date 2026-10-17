package com.tricore.dxos.common.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Exposes documentation only in the explicitly selected docs-local profile.
 * The profile binds the HTTP server to 127.0.0.1; production defaults disable
 * both OpenAPI JSON and Swagger UI. Not an authentication or authorization layer.
 */
@Configuration(proxyBeanMethods = false)
@Profile("docs-local")
public class LocalApiDocumentationConfiguration {

    @Bean
    OpenAPI dxosLocalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TRICORE DX-OS — ACTIVE HTTP APIs (local only)")
                .version("0.1.0-draft")
                .description("Only endpoints currently registered on this backend are documented. "
                        + "Request is a standalone business prototype and not the generic Workflow Core. "
                        + "The generic Workflow HTTP API, secure Gateway and OIDC integration are BLOCKED "
                        + "until Issues #22/#23/#24 complete. Documentation does not grant permissions. "
                        + "Use an isolated disposable local database; never submit requests to shared Supabase."));
    }

    @Bean
    GroupedOpenApi dxosActiveLocalApis() {
        return GroupedOpenApi.builder()
                .group("active")
                // Explicit allowlist: no tentative/unregistered Workflow HTTP or internal routes.
                .pathsToMatch("/api/v1/health", "/api/v1/requests", "/api/v1/requests/**")
                .build();
    }
}
