package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedPrincipalTest {

    @Test
    void shouldCreatePrincipalWithValidValues() {
        Set<Authority> authorities =
                Set.of(new Authority("test.read"));

        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(
                        "subject-123",
                        "Test User",
                        authorities,
                        true
                );

        assertEquals("subject-123", principal.subjectId());
        assertEquals("Test User", principal.displayName());
        assertEquals(authorities, principal.authorities());
        assertTrue(principal.authenticated());
    }

    @Test
    void shouldAllowNullDisplayName() {
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(
                        "subject-123",
                        null,
                        Set.of(),
                        true
                );

        assertNull(principal.displayName());
    }

    @Test
    void shouldRejectNullSubjectId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(
                        null,
                        "Test User",
                        Set.of(),
                        true
                )
        );
    }

    @Test
    void shouldRejectBlankSubjectId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(
                        "   ",
                        "Test User",
                        Set.of(),
                        true
                )
        );
    }

    @Test
    void shouldRejectNullAuthorities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(
                        "subject-123",
                        "Test User",
                        null,
                        true
                )
        );
    }

    @Test
    void shouldDefensivelyCopyAuthorities() {
        Set<Authority> source = new HashSet<>();
        source.add(new Authority("test.read"));

        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(
                        "subject-123",
                        "Test User",
                        source,
                        true
                );

        source.add(new Authority("test.write"));

        assertEquals(1, principal.authorities().size());
        assertFalse(
                principal.authorities()
                        .contains(new Authority("test.write"))
        );
    }

    @Test
    void shouldExposeUnmodifiableAuthorities() {
        AuthenticatedPrincipal principal =
                new AuthenticatedPrincipal(
                        "subject-123",
                        "Test User",
                        Set.of(new Authority("test.read")),
                        true
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> principal.authorities()
                        .add(new Authority("test.write"))
        );
    }

    @Test
    void shouldRejectUnauthenticatedPrincipal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(
                        "subject-123",
                        "Test User",
                        Set.of(),
                        false
                )
        );
    }
}