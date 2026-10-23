package com.tricore.dxos.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI dxosOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DX-OS HTTP API").version("1.0")
                        .description("Active Health and Request routes on the Issue #37 baseline main. The Request prototype on that baseline does not enforce JWT or object-level ACL. Separate Identity/Gateway Keycloak branches do not establish those controls for Request on main. Use only with a disposable local database."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Documentation for future Keycloak OIDC access tokens; bearerAuth is not an enforced security requirement on the Issue #37 baseline main.")));
    }
}
