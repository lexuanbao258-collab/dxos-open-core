package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationContextTest {

    @Test
    void validAuthenticationReference_shouldBeAccepted() {
        AuthenticationContext context =
                new AuthenticationContext("auth-context-123");

        assertEquals(
                "auth-context-123",
                context.authenticationReference()
        );
    }

    @Test
    void nullAuthenticationReference_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationContext(null)
        );
    }

    @Test
    void emptyAuthenticationReference_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationContext("")
        );
    }

    @Test
    void blankAuthenticationReference_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticationContext("   ")
        );
    }
}