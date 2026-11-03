package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(
        named = "OIDC_TEST_ACCESS_TOKEN",
        matches = ".+"
)
class OidcKeycloakIntegrationTest {

    private static final String ISSUER =
            "http://localhost:8180/realms/tricore";

    private static final String AUDIENCE = "tricore-api";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void realKeycloakTokenIsVerifiedThroughDiscoveryAndJwks() {
        SecurityConfig securityConfig = new SecurityConfig();

        JwtDecoder decoder =
                securityConfig.jwtDecoder(ISSUER, AUDIENCE);

        Jwt jwt = decoder.decode(required("OIDC_TEST_ACCESS_TOKEN"));

        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).contains(AUDIENCE);
        assertThat(jwt.getSubject()).isNotBlank();
    }

    @Test
    void forgedSignatureIsRejectedByRealKeycloakJwksDecoder() {
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(ISSUER, AUDIENCE);
        String token = required("OIDC_TEST_ACCESS_TOKEN");

        decoder.decode(token);

        String[] segments = token.split("\\.", -1);
        assertEquals(3, segments.length);
        String signature = segments[2];
        assertFalse(signature.isEmpty());
        char changed = signature.charAt(0) == 'A' ? 'B' : 'A';
        String forged = segments[0] + "." + segments[1] + "." + changed + signature.substring(1);

        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void verifiedKeycloakTokenMapsToCanonicalPrincipalAndWorkflowAuthorities() {
        SecurityConfig securityConfig = new SecurityConfig();

        JwtDecoder decoder =
                securityConfig.jwtDecoder(ISSUER, AUDIENCE);

        Jwt jwt = decoder.decode(required("OIDC_TEST_ACCESS_TOKEN"));

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, java.util.List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        SpringSecurityCurrentPrincipal currentPrincipal =
                new SpringSecurityCurrentPrincipal(AUDIENCE);

        AuthenticatedPrincipal principal =
                currentPrincipal.requireVerified();

        assertThat(principal.authenticated()).isTrue();

        assertThat(principal.subjectId())
                .startsWith("oidc:")
                .doesNotContain("=");

        assertThat(principal.authorities())
                .extracting(authority -> authority.value())
                .containsExactlyInAnyOrder(
                        "workflow:create",
                        "workflow:activate",
                        "workflow:transition",
                        "workflow:read"
                );
    }

    private static String required(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing test environment variable: " + name
            );
        }

        return value;
    }
}
