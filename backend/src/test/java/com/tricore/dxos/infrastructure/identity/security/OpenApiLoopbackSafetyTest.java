package com.tricore.dxos.infrastructure.identity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiLoopbackSafetyTest {
    @Test
    void failsClosedWhenDocsEnabledWithoutLoopbackBind() {
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.requireLoopbackOpenApi(true, ""));
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.requireLoopbackOpenApi(true, "0.0.0.0"));
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.requireLoopbackOpenApi(true, "::1"));
        assertDoesNotThrow(() -> SecurityConfig.requireLoopbackOpenApi(true, "127.0.0.1"));
    }

    @Test
    void docsDisabledDoesNotChangeExistingListenerPolicy() {
        assertDoesNotThrow(() -> SecurityConfig.requireLoopbackOpenApi(false, ""));
        assertDoesNotThrow(() -> SecurityConfig.requireLoopbackOpenApi(false, "0.0.0.0"));
    }
}
