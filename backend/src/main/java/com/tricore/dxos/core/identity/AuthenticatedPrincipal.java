package com.tricore.dxos.core.identity;

import java.util.Set;

public record AuthenticatedPrincipal(
        String subjectId,
        String displayName,
        Set<Authority> authorities,
        boolean authenticated
) {

    public AuthenticatedPrincipal {
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Subject ID must not be blank");
        }

        if (authorities == null) {
            throw new IllegalArgumentException("Authorities must not be null");
        }

        if (!authenticated) {
            throw new IllegalArgumentException(
                    "Authenticated principal must be authenticated"
            );
        }

        authorities = Set.copyOf(authorities);
    }
}