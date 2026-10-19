package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.gateway.workflow.security.WorkflowGatewayPrincipalGate;

import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.infrastructure.identity.security.SecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test-only HTTP probe: the JwtDecoder is mocked. This proves filter-chain -> verified
 * CurrentPrincipal -> Gateway operation gate wiring, NOT a real JWT signature/JWKS check.
 * There is no corresponding production route.
 */
@WebMvcTest(controllers = GatewayIdentityHandoffHttpTest.ProbeController.class, properties = {
        "OIDC_ISSUER_URI=https://issuer.example.test",
        "OIDC_AUDIENCE=tricore-api",
        "OIDC_RESOURCE_CLIENT_ID=tricore-api"
})
@Import({SecurityConfig.class,
        SpringSecurityCurrentPrincipal.class,
        WorkflowGatewayPrincipalGate.class})
class GatewayIdentityHandoffHttpTest {
    private static final String PATH = "/__test_only/gateway/identity-handoff";
    private static final String ISSUER = "https://issuer.example.test";

    @Autowired private MockMvc mvc;
    @Autowired private ProbeController probe;
    @MockitoBean private JwtDecoder jwtDecoder;

    @AfterEach
    void resetProbe() {
        probe.caller = null;
    }

    @Test
    void validBearerTokenReachesGatewayWithCanonicalVerifiedActorAndExactReadAuthority() throws Exception {
        when(jwtDecoder.decode("signed-read-token")).thenReturn(jwt(List.of("workflow:read")));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer signed-read-token"))
                .andExpect(status().isNoContent());

        AuthenticatedPrincipal caller = probe.caller;
        assertThat(caller).isNotNull();
        assertThat(caller.authenticated()).isTrue();
        String issuerPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ISSUER.getBytes(StandardCharsets.UTF_8));
        String subjectPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("user-123".getBytes(StandardCharsets.UTF_8));
        assertThat(caller.subjectId()).isEqualTo("oidc:" + issuerPart + "." + subjectPart);
        assertThat(caller.authorities())
                .extracting(authority -> authority.value())
                .containsExactly("workflow:read");
    }

    @Test
    void validJwtWithoutRequiredWorkflowReadAuthorityReturns403() throws Exception {
        when(jwtDecoder.decode("signed-create-only-token"))
                .thenReturn(jwt(List.of("workflow:create")));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer signed-create-only-token"))
                .andExpect(status().isForbidden());
        assertThat(probe.caller).isNull();
    }

    @Test
    void unknownOrWildcardAuthorityDoesNotGrantRead() throws Exception {
        when(jwtDecoder.decode("signed-wildcard-token"))
                .thenReturn(jwt(List.of("workflow:*", "Workflow:read", " workflow:read ")));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer signed-wildcard-token"))
                .andExpect(status().isForbidden());
        assertThat(probe.caller).isNull();
    }

    @Test
    void anonymousAndSpoofedHeadersCannotReachGateway() throws Exception {
        mvc.perform(get(PATH)
                        .header("X-User", "admin")
                        .header("X-Role", "workflow:read"))
                .andExpect(status().isUnauthorized());
        assertThat(probe.caller).isNull();
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeGateway() throws Exception {
        when(jwtDecoder.decode("bad-signature-token"))
                .thenThrow(new BadJwtException("Signature rejected"));

        mvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-signature-token")
                        .header("X-User", "admin"))
                .andExpect(status().isUnauthorized());
        assertThat(probe.caller).isNull();
    }

    private static Jwt jwt(List<String> roles) {
        Instant now = Instant.now();
        return new Jwt(
                "mock-decoder-result-only",
                now,
                now.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", ISSUER,
                        "sub", "user-123",
                        "resource_access", Map.of("tricore-api", Map.of("roles", roles))
                )
        );
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
