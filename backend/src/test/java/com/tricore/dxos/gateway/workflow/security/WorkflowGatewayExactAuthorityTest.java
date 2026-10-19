package com.tricore.dxos.gateway.workflow.security;

import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Stage 4: verifies the Gateway's exact role gate only. This is not a resource
 * ACL check, JWT signature validation, or production Workflow HTTP registration.
 */
class WorkflowGatewayExactAuthorityTest {
    private static final String SUBJECT = "oidc:verified-issuer.verified-subject";
    private static final String[] ROLES = {
            "workflow:create", "workflow:activate", "workflow:transition", "workflow:read"
    };
    private final CurrentPrincipal identity = mock(CurrentPrincipal.class);
    private final WorkflowGatewayPrincipalGate gate = new WorkflowGatewayPrincipalGate(identity);

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("canonicalRoleMatrix")
    void onlyMatchingCanonicalAuthorityPassesGateway(WorkflowOperation operation, String role) {
        AuthenticatedPrincipal caller = caller(Set.of(role));
        when(identity.requireVerified()).thenReturn(caller);

        if (role.equals(operation.requiredAuthority().value())) {
            assertThat(gate.requireOperation(operation)).isSameAs(caller);
        } else {
            assertThatThrownBy(() -> gate.requireOperation(operation))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verify(identity).requireVerified();
        verifyNoMoreInteractions(identity);
    }

    static Stream<Arguments> canonicalRoleMatrix() {
        return Arrays.stream(WorkflowOperation.values())
                .flatMap(operation -> Arrays.stream(ROLES).map(role -> Arguments.of(operation, role)));
    }

    @ParameterizedTest
    @MethodSource("invalidRoles")
    void wildcardCaseWhitespaceAndSuffixNeverReplaceExactRole(WorkflowOperation operation, String role) {
        when(identity.requireVerified()).thenReturn(caller(Set.of(role)));
        assertThatThrownBy(() -> gate.requireOperation(operation))
                .isInstanceOf(AccessDeniedException.class);
        verify(identity).requireVerified();
    }

    static Stream<Arguments> invalidRoles() {
        return Arrays.stream(WorkflowOperation.values()).flatMap(operation -> Stream.of(
                "workflow:*",
                operation.requiredAuthority().value().toUpperCase(Locale.ROOT),
                " " + operation.requiredAuthority().value(),
                operation.requiredAuthority().value() + " ",
                operation.requiredAuthority().value() + ":extra"
        ).map(role -> Arguments.of(operation, role)));
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void allCanonicalRolesStillRequireVerifiedIdentity(WorkflowOperation operation) {
        when(identity.requireVerified()).thenThrow(
                new IllegalStateException("Verified authentication is required"));

        assertThatThrownBy(() -> gate.requireOperation(operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Verified authentication is required");
        verify(identity).requireVerified();
    }

    @ParameterizedTest
    @EnumSource(WorkflowOperation.class)
    void emptyAuthoritiesNeverGrantAnOperation(WorkflowOperation operation) {
        when(identity.requireVerified()).thenReturn(caller(Set.of()));
        assertThatThrownBy(() -> gate.requireOperation(operation))
                .isInstanceOf(AccessDeniedException.class);
        verify(identity).requireVerified();
    }

    @Test
    void allCanonicalRolesPermitEachOperationButAreNotAResourceGrant() {
        AuthenticatedPrincipal caller = caller(Set.of(ROLES));
        when(identity.requireVerified()).thenReturn(caller);

        for (WorkflowOperation operation : WorkflowOperation.values()) {
            assertThat(gate.requireOperation(operation)).isSameAs(caller);
        }
        verify(identity, times(WorkflowOperation.values().length)).requireVerified();
    }

    private static AuthenticatedPrincipal caller(Set<String> roles) {
        return new AuthenticatedPrincipal(SUBJECT, "display-name", roles.stream()
                .map(Authority::new).collect(java.util.stream.Collectors.toUnmodifiableSet()), true);
    }
}
