package com.tricore.dxos.core.identity;

/**
 * Provider-independent reference to authentication context.
 * Possession of this reference alone does not prove authentication.
 */
public record AuthenticationContext(String authenticationReference) {

    public AuthenticationContext {
        if (authenticationReference == null || authenticationReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Authentication reference must not be blank"
            );
        }
    }
}