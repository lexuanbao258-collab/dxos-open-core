package com.tricore.dxos.core.identity;

import java.util.Objects;

/**
 * Provider-independent result of an authorization check.
 */
public record AuthorizationDecision(
        boolean granted,
        RequiredAuthority requiredAuthority,
        AuthorizationReason reason
) {

    public AuthorizationDecision {
        Objects.requireNonNull(
                requiredAuthority,
                "requiredAuthority must not be null"
        );

        Objects.requireNonNull(
                reason,
                "reason must not be null"
        );

        if (granted && reason != AuthorizationReason.AUTHORITY_GRANTED) {
            throw new IllegalArgumentException(
                    "Granted decision must use AUTHORITY_GRANTED reason"
            );
        }

        if (!granted && reason != AuthorizationReason.MISSING_AUTHORITY) {
            throw new IllegalArgumentException(
                    "Denied decision must use MISSING_AUTHORITY reason"
            );
        }
    }
}