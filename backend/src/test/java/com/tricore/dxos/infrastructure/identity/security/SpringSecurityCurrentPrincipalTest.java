package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void mapsCanonicalWorkflowAuthoritiesFromResourceAccess() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                "sub", "user-123",
                "resource_access", Map.of("tricore-api", Map.of("roles", List.of(
                        "workflow:create", "workflow:activate", "workflow:transition", "workflow:read")))
        ));

        assertEquals(Set.of(
                new Authority("workflow:create"),
                new Authority("workflow:activate"),
                new Authority("workflow:transition"),
                new Authority("workflow:read")
        ), principal.authorities());
    }

    @Test
    void ignoresUnknownClientRoles() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                "sub", "user-123",
                "resource_access", Map.of("tricore-api", Map.of("roles", List.of(
                        "workflow:create", "workflow:activate", "workflow:transition", "workflow:read",
                        "offline_access", "uma_authorization", "some-other-role")))
        ));

        assertEquals(Set.of(
                new Authority("workflow:create"),
                new Authority("workflow:activate"),
                new Authority("workflow:transition"),
                new Authority("workflow:read")
        ), principal.authorities());
    }

    @Test
    void returnsEmptyAuthoritiesWhenResourceAccessIsMissing() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of("sub", "user-123"));

        assertEquals("user-123", principal.subjectId());
        assertTrue(principal.authorities().isEmpty());
    }

    @Test
    void returnsEmptyAuthoritiesWhenTricoreApiRolesAreMissing() {
        for (Map<String, Object> resourceAccess : List.<Map<String, Object>>of(
                Map.of("other-client", Map.of("roles", List.of("workflow:create"))),
                Map.of("tricore-api", Map.of("other-claim", List.of("workflow:create")))
        )) {
            AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                    "sub", "user-123", "resource_access", resourceAccess));

            assertEquals("user-123", principal.subjectId());
            assertTrue(principal.authorities().isEmpty());
        }
    }

    @Test
    void authorityMatchingRemainsExact() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                "sub", "user-123",
                "resource_access", Map.of("tricore-api", Map.of("roles", List.of(
                        "Workflow:create", " workflow:create", "workflow:create ", "workflow:*")))
        ));

        assertTrue(principal.authorities().isEmpty());
    }

    private AuthenticatedPrincipal principalFromClaims(Map<String, Object> claims) {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt(claims), List.of()));
        return currentPrincipal.requireVerified();
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
