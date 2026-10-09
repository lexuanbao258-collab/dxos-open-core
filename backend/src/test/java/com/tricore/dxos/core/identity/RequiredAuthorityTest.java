package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredAuthorityTest {

    @Test
    void validRequiredAuthority_shouldBeAccepted() {
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("test.read");

        assertEquals("test.read", requiredAuthority.value());
    }

    @Test
    void nullValue_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RequiredAuthority(null)
        );
    }

    @Test
    void emptyValue_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RequiredAuthority("")
        );
    }

    @Test
    void blankValue_shouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RequiredAuthority("   ")
        );
    }
}