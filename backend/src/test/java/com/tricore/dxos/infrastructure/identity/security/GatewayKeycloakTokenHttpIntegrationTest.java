package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.gateway.workflow.security.WorkflowGatewayPrincipalGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Optional end-to-end test of a *real* locally issued Keycloak JWT through:
 * HTTP -> Spring Security filter chain -> real OIDC/JWKS decoder ->
 * CurrentPrincipal.requireVerified() -> Gateway operation gate.
 *
 * No production endpoint, fake principal, mocked JWT decoder, database or Workflow write.
 * Run only with a fresh OIDC_TEST_ACCESS_TOKEN issued by the configured local Keycloak.
 */
@EnabledIfEnvironmentVariable(named = "OIDC_TEST_ACCESS_TOKEN", matches = ".+")
@WebMvcTest(controllers = GatewayKeycloakTokenHttpIntegrationTest.ProbeController.class, properties = {
        "OIDC_ISSUER_URI=http://localhost:8180/realms/tricore",
        "OIDC_AUDIENCE=tricore-api",
        "OIDC_RESOURCE_CLIENT_ID=tricore-api"
})
@Import({GatewayKeycloakTokenHttpIntegrationTest.ProbeController.class, SecurityConfig.class, SpringSecurityCurrentPrincipal.class, WorkflowGatewayPrincipalGate.class})
class GatewayKeycloakTokenHttpIntegrationTest {
    private static final String PATH = "/__test_only/gateway/identity-handoff-live";
    private static final String ISSUER = "http://localhost:8180/realms/tricore";

    @Autowired private MockMvc mvc;
    @Autowired private ProbeController probe;

    @AfterEach
    void clearProbe() {
        probe.caller = null;
    }

    @Test
    void realKeycloakBearerTokenReachesVerifiedGatewayOperationGate() throws Exception {
        mvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .header("X-User", "spoofed")
                        .header("X-Role", "administrator"))
                .andExpect(status().isNoContent());

        assertThat(probe.caller).isNotNull();
        assertThat(probe.caller.authenticated()).isTrue();
        assertThat(probe.caller.subjectId()).startsWith("oidc:").doesNotContain("=");

        String expectedSubject = expectedSubject();
        if (expectedSubject != null) {
            assertThat(probe.caller.subjectId())
                    .isEqualTo(expectedCanonicalSubjectId(expectedSubject));
        }

        assertThat(probe.caller.authorities())
                .extracting(a -> a.value())
                .contains("workflow:read");
    }

    @Test
    void modifiedSignatureIsRejectedBeforeGateway() throws Exception {
        String[] jwt = token().split("\\.", -1);
        assertThat(jwt).hasSize(3);
        assertThat(jwt[2]).isNotBlank();
        String signature = jwt[2];
        char first = signature.charAt(0) == 'A' ? 'B' : 'A';
        String forged = jwt[0] + "." + jwt[1] + "." + first + signature.substring(1);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
        assertThat(probe.caller).isNull();
    }

    @Test
    void anonymousForgedHeadersCannotReachGateway() throws Exception {
        mvc.perform(get(PATH)
                        .header("X-User", "admin")
                        .header("X-Role", "workflow:read"))
                .andExpect(status().isUnauthorized());
        assertThat(probe.caller).isNull();
    }

    private static String expectedSubject() {
        String subject = System.getenv("OIDC_TEST_EXPECTED_SUBJECT");
        if (subject == null || subject.isBlank()) {
            return null;
        }
        return subject;
    }

    private static String expectedCanonicalSubjectId(String subject) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "oidc:"
                + encoder.encodeToString(ISSUER.getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(subject.getBytes(StandardCharsets.UTF_8));
    }

    private static String token() {
        String token = System.getenv("OIDC_TEST_ACCESS_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("A fresh local Keycloak access token is required");
        }
        return token;
    }

    @RestController
    static class ProbeController {
        private final WorkflowGatewayPrincipalGate gatewayGate;
        volatile AuthenticatedPrincipal caller;

        ProbeController(WorkflowGatewayPrincipalGate gatewayGate) {
            this.gatewayGate = gatewayGate;
        }

        @GetMapping(PATH)
        ResponseEntity<Void> inspect() {
            caller = gatewayGate.requireOperation(WorkflowOperation.READ);
            return ResponseEntity.noContent().build();
        }
    }
}
