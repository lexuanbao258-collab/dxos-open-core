package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationDecisionTest {

    @Test
    void grantedDecision_shouldBeAccepted() {
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("test.read");

        AuthorizationDecision decision =
                new AuthorizationDecision(
                        true,
                        requiredAuthority,
                        AuthorizationReason.AUTHORITY_GRANTED
                );

        assertTrue(decision.granted());
        assertEquals(requiredAuthority, decision.requiredAuthority());
        assertEquals(
                AuthorizationReason.AUTHORITY_GRANTED,
                decision.reason()
        );
    }

    @Test
    void deniedDecision_shouldBeAccepted() {
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("test.write");

        AuthorizationDecision decision =
                new AuthorizationDecision(
                        false,
                        requiredAuthority,
                        AuthorizationReason.MISSING_AUTHORITY
                );

        assertFalse(decision.granted());
        assertEquals(requiredAuthority, decision.requiredAuthority());
        assertEquals(
                AuthorizationReason.MISSING_AUTHORITY,
                decision.reason()
        );
    }

    @Test
    void grantedDecisionWithMissingAuthorityReason_shouldBeRejected() {
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("test.read");

        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizationDecision(
                        true,
                        requiredAuthority,
                        AuthorizationReason.MISSING_AUTHORITY
                )
        );
    }

    @Test
    void deniedDecisionWithGrantedReason_shouldBeRejected() {
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("test.read");

        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizationDecision(
                        false,
                        requiredAuthority,
                        AuthorizationReason.AUTHORITY_GRANTED
                )
        );
    }

    @Test
    void nullRequiredAuthority_shouldBeRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new AuthorizationDecision(
                        true,
                        null,
                        AuthorizationReason.AUTHORITY_GRANTED
                )
        );
    }

    @Test
    void nullReason_shouldBeRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new AuthorizationDecision(
                        true,
                        new RequiredAuthority("test.read"),
                        null
                )
        );
    }
}