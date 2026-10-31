package com.tricore.dxos.gateway.helpdesk;

import com.tricore.dxos.common.error.ApiError;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

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
                .addOpenApiCustomizer(HelpdeskOpenApiConfiguration::documentContract)
                .build();
    }

    static void documentContract(OpenAPI api) {
        // Bean Validation's @Size(min=0) otherwise overwrites @Schema(minLength=1).
        property(api, "Create", "title").setMinLength(1);
        property(api, "Create", "description").setMinLength(1);
        property(api, "Assign", "assigneeId").setMinLength(1);
        property(api, "Resolve", "resolution").setMinLength(1);
        // OAS 3.1 uses a null type union. NON_NULL fields remain optional, not nullable.
        nullableString(api, "RequestView", "assigneeId");
        nullableString(api, "RequestView", "resolution");
        nullableString(api, "Page", "nextCursor");
        nullableString(api, "AssigneeOption", "displayName");

        var error = ModelConverters.getInstance(true).resolveAsResolvedSchema(new AnnotatedType(ApiError.class).resolveAsRef(true));
        error.referencedSchemas.forEach(api.getComponents()::addSchemas);
        api.getComponents().getSchemas().get("ApiError").setRequired(List.of("status", "code", "message", "errors"));

        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            var responses = operation.getResponses();
            responses.addApiResponse("400", error("Invalid body, request parameter or Bean Validation failure"));
            responses.addApiResponse("401", error("Missing/invalid bearer token or missing trusted actor")
                    .addHeaderObject("WWW-Authenticate", new Header().required(false)
                            .description("Bearer challenge from the authentication entry point; not guaranteed for advice-generated 401")
                            .schema(new StringSchema().example("Bearer"))));
            responses.addApiResponse("403", error("Visible resource, but the Workflow operation is forbidden"));
            responses.addApiResponse("404", error("Hidden, cross-tenant or nonexistent resource; indistinguishable NOT_FOUND"));
            responses.addApiResponse("500", error("Workflow/internal failure. COMMIT_OUTCOME_UNKNOWN requires reconciliation; do not automatically retry a write"));
            responses.addApiResponse("503", error("Workflow authorization is unavailable; access fails closed"));
            if (method == PathItem.HttpMethod.POST) {
                responses.addApiResponse("409", error("Instance/version conflict, disallowed state or completed workflow"));
                responses.addApiResponse("415", error("Unsupported request media type"));
                // These four methods call WorkflowDefinition.transition(); assign/create do not.
                if (List.of("/triage", "/start", "/resolve", "/confirm").stream().anyMatch(path::endsWith)) {
                    responses.addApiResponse("422", error("Transition is absent from the configured Workflow definition"));
                }
            }
        }));
    }

    private static void nullableString(OpenAPI api, String schema, String field) {
        Schema<?> value = property(api, schema, field);
        value.setType(null);
        value.setTypes(new LinkedHashSet<>(List.of("string", "null")));
        value.setNullable(null);
    }

    private static Schema<?> property(OpenAPI api, String schema, String field) {
        Schema<?> owner = Objects.requireNonNull(api.getComponents().getSchemas().get(schema));
        return Objects.requireNonNull(owner.getProperties().get(field));
    }

    private static ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
