package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentityExceptionTest {

    @Test
    void shouldExposeNormalizedErrorCode() {
        IdentityException exception =
                new IdentityException(
                        IdentityErrorCode.AUTHENTICATION_FAILED
                );

        assertEquals(
                IdentityErrorCode.AUTHENTICATION_FAILED,
                exception.code()
        );
    }

    @Test
    void shouldUseOnlyNormalizedCodeAsMessage() {
        IdentityException exception =
                new IdentityException(
                        IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE
                );

        assertEquals(
                "IDENTITY_PROVIDER_UNAVAILABLE",
                exception.getMessage()
        );
    }

    @Test
    void shouldNotExposeCause() {
        IdentityException exception =
                new IdentityException(
                        IdentityErrorCode.INVALID_AUTHENTICATION_CONTEXT
                );

        assertNull(exception.getCause());
    }

    @Test
    void nullErrorCode_shouldBeRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new IdentityException(null)
        );
    }

    @Test
    void allBaselineErrorCodes_shouldBeAvailable() {
        assertArrayEquals(
                new IdentityErrorCode[]{
                        IdentityErrorCode.AUTHENTICATION_FAILED,
                        IdentityErrorCode.INVALID_AUTHENTICATION_CONTEXT,
                        IdentityErrorCode.AUTHENTICATION_EXPIRED,
                        IdentityErrorCode.ACCESS_DENIED,
                        IdentityErrorCode.IDENTITY_NOT_FOUND,
                        IdentityErrorCode.IDENTITY_PROVIDER_UNAVAILABLE
                },
                IdentityErrorCode.values()
        );
    }
}