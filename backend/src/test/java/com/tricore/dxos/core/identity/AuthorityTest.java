package com.tricore.dxos.core.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void shouldSatisfyRequiredAuthorityWithExactlyEqualValue() {
        Authority authority = new Authority("WORKFLOW_APPROVE");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertTrue(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotSatisfyRequiredAuthorityWithDifferentCase() {
        Authority authority = new Authority("workflow_approve");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertFalse(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotSatisfyRequiredAuthorityWithLeadingWhitespace() {
        Authority authority = new Authority(" WORKFLOW_APPROVE");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertFalse(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotSatisfyRequiredAuthorityWithTrailingWhitespace() {
        Authority authority = new Authority("WORKFLOW_APPROVE ");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertFalse(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotTreatWildcardLikeAuthorityAsMatch() {
        Authority authority = new Authority("WORKFLOW_*");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertFalse(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotSatisfyLongerRequiredAuthorityValue() {
        Authority authority = new Authority("WORKFLOW_APPROVE");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE_EXTRA");

        assertFalse(authority.satisfies(requiredAuthority));
    }

    @Test
    void shouldNotSatisfyRequiredAuthorityWithLongerAuthorityValue() {
        Authority authority = new Authority("WORKFLOW_APPROVE_EXTRA");
        RequiredAuthority requiredAuthority =
                new RequiredAuthority("WORKFLOW_APPROVE");

        assertFalse(authority.satisfies(requiredAuthority));
    }
}
