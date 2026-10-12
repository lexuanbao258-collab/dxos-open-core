package com.tricore.dxos.infrastructure.identity.security;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
final class SpringSecurityCurrentPrincipal implements CurrentPrincipal {

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
                Set.of(),
                true
        );
    }
}