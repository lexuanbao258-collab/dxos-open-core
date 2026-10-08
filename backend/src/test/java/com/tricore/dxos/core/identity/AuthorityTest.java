package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorityTest {

    @Test
    void shouldCreateAuthorityWithValidValue() {
        Authority authority = new Authority("request.read");

        assertEquals("request.read", authority.value());
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Authority(null)
        );
    }

    @Test
    void shouldRejectEmptyValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Authority("")
        );
    }

    @Test
    void shouldRejectBlankValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Authority("   ")
        );
    }
}