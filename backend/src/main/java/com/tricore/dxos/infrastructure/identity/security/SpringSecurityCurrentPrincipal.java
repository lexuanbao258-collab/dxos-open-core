package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class SpringSecurityCurrentPrincipal implements CurrentPrincipal {
    private static final Set<String> SUPPORTED_AUTHORITIES = Set.of(
            "workflow:create", "workflow:activate", "workflow:transition", "workflow:read"
    );
    private final String resourceClientId;

    SpringSecurityCurrentPrincipal(@Value("${tricore.security.oidc.resource-client-id}") String resourceClientId) {
        if (resourceClientId == null || resourceClientId.isBlank()) {
            throw new IllegalArgumentException("Resource client ID must not be blank");
        }
        this.resourceClientId = resourceClientId;
    }

    @Override
    public AuthenticatedPrincipal requireVerified() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new IllegalStateException(
                    "Verified authentication is required"
            );
        }

        String subjectId = jwtAuthentication.getToken().getSubject();

        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalStateException(
                    "Verified JWT subject is required"
            );
        }

        Object issuerClaim = jwtAuthentication.getToken().getClaim("iss");
        if (!(issuerClaim instanceof String issuer) || issuer.isBlank()) {
            throw new IllegalStateException(
                    "Verified JWT issuer is required"
            );
        }

        return new AuthenticatedPrincipal(
                canonicalSubjectId(issuer, subjectId),
                jwtAuthentication.getName(),
                workflowAuthorities(jwtAuthentication.getToken()),
                true
        );
    }

    private static String canonicalSubjectId(String issuer, String subject) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "oidc:" + encoder.encodeToString(issuer.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(subject.getBytes(StandardCharsets.UTF_8));
    }

    private Set<Authority> workflowAuthorities(Jwt jwt) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> clients)
                || !(clients.get(resourceClientId) instanceof Map<?, ?> client)
                || !(client.get("roles") instanceof List<?> roles)
                || roles.stream().anyMatch(role -> !(role instanceof String))) {
            return Set.of();
        }

        return roles.stream()
                .map(String.class::cast)
                .filter(SUPPORTED_AUTHORITIES::contains)
                .map(Authority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
