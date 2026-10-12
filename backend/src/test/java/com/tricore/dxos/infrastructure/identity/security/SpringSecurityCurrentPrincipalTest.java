package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityCurrentPrincipalTest {

    private final SpringSecurityCurrentPrincipal currentPrincipal =
            new SpringSecurityCurrentPrincipal();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(
                IllegalStateException.class,
                currentPrincipal::requireVerified
        );
    }

    @Test
    void rejectsNonJwtAuthenticationEvenWhenMarkedAuthenticated() {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("caller", null);

        authentication.setAuthenticated(true);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThrows(
                IllegalStateException.class,
                currentPrincipal::requireVerified
        );
    }

    @Test
    void rejectsJwtWithoutSubject() {
        Jwt jwt = jwt(
                Map.of("iss", "https://issuer.example.test")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        assertThrows(
                IllegalStateException.class,
                currentPrincipal::requireVerified
        );
    }

    @Test
    void resolvesPrincipalFromVerifiedJwtAuthentication() {
        Jwt jwt = jwt(
                Map.of("sub", "user-123")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        AuthenticatedPrincipal principal =
                currentPrincipal.requireVerified();

        assertEquals("user-123", principal.subjectId());
        assertTrue(principal.authenticated());
        assertTrue(principal.authorities().isEmpty());
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant issuedAt = Instant.parse("2026-10-11T00:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(300);

        return new Jwt(
                "test-token",
                issuedAt,
                expiresAt,
                Map.of("alg", "RS256"),
                claims
        );
    }
}