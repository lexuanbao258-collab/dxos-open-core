package com.tricore.dxos.gateway.helpdesk;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI is opt-in, only for the loopback-bound local demo.
 * Do not attach a public API host or bypass the existing Keycloak/JWT/ACL gates.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dxos.openapi.enabled", havingValue = "true")
public class HelpdeskOpenApiConfiguration {
    @Bean
    OpenAPI dxosOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DX-OS Open-Core — API Reference")
                        .version("MVP local")
                        .description("Tài liệu API theo mã nguồn. Không chứng minh đã triển khai production."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")
                                .description("Keycloak access token; Backend tiếp tục kiểm tra tenant, ACL, vai trò và trạng thái.")));
    }

    @Bean
    @ConditionalOnProperty(name = "dxos.helpdesk.enabled", havingValue = "true")
    GroupedOpenApi helpdeskApi() {
        return GroupedOpenApi.builder()
                .group("it-helpdesk-mvp")
                .pathsToMatch("/api/v1/mvp/requests", "/api/v1/mvp/requests/**")
                .build();
    }
}
