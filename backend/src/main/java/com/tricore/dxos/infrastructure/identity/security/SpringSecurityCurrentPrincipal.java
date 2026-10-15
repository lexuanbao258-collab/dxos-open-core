package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class SpringSecurityCurrentPrincipal implements CurrentPrincipal {
    private static final Set<String> SUPPORTED_AUTHORITIES = Set.of(
            "workflow:create", "workflow:activate", "workflow:transition", "workflow:read"
    );


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

        return new AuthenticatedPrincipal(
                subjectId,
                jwtAuthentication.getName(),
                workflowAuthorities(jwtAuthentication.getToken()),
                true
        );
    }

    private static Set<Authority> workflowAuthorities(Jwt jwt) {
        Object resourceAccess = jwt.getClaim("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> clients)
                || !(clients.get("tricore-api") instanceof Map<?, ?> client)
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
