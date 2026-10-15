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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityCurrentPrincipalTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String USER_123_ID =
            "oidc:aHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZS50ZXN0.dXNlci0xMjM";

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
                Map.of("iss", ISSUER, "sub", "user-123")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        AuthenticatedPrincipal principal =
                currentPrincipal.requireVerified();

        assertEquals(USER_123_ID, principal.subjectId());
        assertTrue(principal.authenticated());
        assertTrue(principal.authorities().isEmpty());
    }

    @Test
    void mapsCanonicalWorkflowAuthoritiesFromResourceAccess() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                "iss", ISSUER, "sub", "user-123",
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
                "iss", ISSUER, "sub", "user-123",
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
        AuthenticatedPrincipal principal = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123"));

        assertEquals(USER_123_ID, principal.subjectId());
        assertTrue(principal.authorities().isEmpty());
    }

    @Test
    void returnsEmptyAuthoritiesWhenTricoreApiRolesAreMissing() {
        for (Map<String, Object> resourceAccess : List.<Map<String, Object>>of(
                Map.of("other-client", Map.of("roles", List.of("workflow:create"))),
                Map.of("tricore-api", Map.of("other-claim", List.of("workflow:create")))
        )) {
            AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                    "iss", ISSUER, "sub", "user-123", "resource_access", resourceAccess));

            assertEquals(USER_123_ID, principal.subjectId());
            assertTrue(principal.authorities().isEmpty());
        }
    }

    @Test
    void authorityMatchingRemainsExact() {
        AuthenticatedPrincipal principal = principalFromClaims(Map.of(
                "iss", ISSUER, "sub", "user-123",
                "resource_access", Map.of("tricore-api", Map.of("roles", List.of(
                        "Workflow:create", " workflow:create", "workflow:create ", "workflow:*")))
        ));

        assertTrue(principal.authorities().isEmpty());
    }

    @Test
    void sameIssuerAndSubjectProduceSameCanonicalSubjectId() {
        String first = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123")).subjectId();
        String second = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123")).subjectId();

        assertEquals(USER_123_ID, first);
        assertEquals(first, second);
    }

    @Test
    void differentIssuerWithSameSubjectProducesDifferentSubjectId() {
        String first = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123")).subjectId();
        String second = principalFromClaims(Map.of(
                "iss", "https://id.example/realms/Test", "sub", "user-123")).subjectId();

        assertNotEquals(first, second);
    }

    @Test
    void differentSubjectWithSameIssuerProducesDifferentSubjectId() {
        String first = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123")).subjectId();
        String second = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-456")).subjectId();

        assertNotEquals(first, second);
        assertTrue(second.startsWith("oidc:"));
    }

    @Test
    void usesExactBase64UrlEncodingWithoutPadding() {
        String subjectId = principalFromClaims(Map.of(
                "iss", "https://id.example/realms/Test", "sub", "user-123")).subjectId();

        assertEquals("oidc:aHR0cHM6Ly9pZC5leGFtcGxlL3JlYWxtcy9UZXN0.dXNlci0xMjM", subjectId);
    }

    @Test
    void encodesUnicodeIssuerAndSubjectAsUtf8() {
        String subjectId = principalFromClaims(Map.of(
                "iss", "https://id.example/é", "sub", "用户-😀")).subjectId();

        assertEquals("oidc:aHR0cHM6Ly9pZC5leGFtcGxlL8Op.55So5oi3LfCfmIA", subjectId);
    }

    @Test
    void encodedComponentsContainNoPadding() {
        String subjectId = principalFromClaims(Map.of("iss", ISSUER, "sub", "user-123")).subjectId();

        assertTrue(subjectId.startsWith("oidc:"));
        String[] components = subjectId.substring("oidc:".length()).split("\\.", -1);
        assertEquals(2, components.length);
        assertFalse(components[0].contains("="));
        assertFalse(components[1].contains("="));
    }

    @Test
    void rejectsJwtWithoutIssuer() {
        assertThrows(IllegalStateException.class,
                () -> principalFromClaims(Map.of("sub", "user-123")));
    }

    @Test
    void rejectsJwtWithBlankIssuer() {
        assertThrows(IllegalStateException.class,
                () -> principalFromClaims(Map.of("iss", " ", "sub", "user-123")));
    }

    @Test
    void rejectsJwtWithBlankSubject() {
        assertThrows(IllegalStateException.class,
                () -> principalFromClaims(Map.of("iss", ISSUER, "sub", " ")));
    }

    @Test
    void preservesCaseWhitespaceAndUnicodeFormInIdentity() {
        String subjectId = principalFromClaims(Map.of(
                "iss", "https://ISSUER.example/Realm", "sub", " User-A ")).subjectId();
        String composed = principalFromClaims(Map.of("iss", ISSUER, "sub", "é")).subjectId();
        String decomposed = principalFromClaims(Map.of("iss", ISSUER, "sub", "e\u0301")).subjectId();

        assertEquals("oidc:aHR0cHM6Ly9JU1NVRVIuZXhhbXBsZS9SZWFsbQ.IFVzZXItQSA", subjectId);
        assertNotEquals(composed, decomposed);
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
