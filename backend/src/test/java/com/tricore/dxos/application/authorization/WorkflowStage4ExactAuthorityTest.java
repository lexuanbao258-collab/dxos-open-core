package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stage 4 regression matrix. All principals, resource decisions and runtime outputs are
 * test doubles: this proves the exact authority boundary, not real ACL or HTTP security.
 */
class WorkflowStage4ExactAuthorityTest {
    private static final String SUBJECT = "oidc:verified-issuer.verified-subject";
    private static final String[] ROLES = {
            "workflow:create", "workflow:activate", "workflow:transition", "workflow:read"
    };
    private static final WorkflowDefinition DEFINITION =
            new WorkflowDefinition("publication", 7, "draft", Set.of("draft"), Set.of());
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "instance-1", DEFINITION, RESOURCE, Instant.parse("2026-10-19T00:00:00Z"));

    private final CurrentPrincipal identity = mock(CurrentPrincipal.class);
    private final WorkflowAccessPolicy policy = mock(WorkflowAccessPolicy.class);
    private final WorkflowPersistencePort persistence = mock(WorkflowPersistencePort.class);
    private final WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    private final AuthorizedWorkflowService service =
            new AuthorizedWorkflowService(identity, new WorkflowAuthorization(policy), persistence, runtime);

    @BeforeEach
    void allowResourceOnlyForAuthorityMatrix() {
        when(persistence.loadDefinition("publication", 7)).thenReturn(DEFINITION);
        when(persistence.loadInstance("instance-1")).thenReturn(INSTANCE);
        when(policy.forCreation(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
        when(policy.forInstance(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
    }

    @Test
    void operationToAuthorityContractIsPinned() {
        assertThat(WorkflowOperation.values()).containsExactly(
                WorkflowOperation.CREATE, WorkflowOperation.ACTIVATE,
                WorkflowOperation.TRANSITION, WorkflowOperation.READ);
        assertThat(WorkflowOperation.CREATE.requiredAuthority().value()).isEqualTo("workflow:create");
        assertThat(WorkflowOperation.ACTIVATE.requiredAuthority().value()).isEqualTo("workflow:activate");
        assertThat(WorkflowOperation.TRANSITION.requiredAuthority().value()).isEqualTo("workflow:transition");
        assertThat(WorkflowOperation.READ.requiredAuthority().value()).isEqualTo("workflow:read");
        assertThat(Action.SNAPSHOT.requiredRole()).isEqualTo(Action.HISTORY.requiredRole());
    }

    @ParameterizedTest(name = "{0} with {1}")
    @MethodSource("singleCanonicalRoleMatrix")
    void onlyTheMatchingCanonicalRoleReachesRuntime(Action action, String role) {
        AuthenticatedPrincipal caller = authenticate(Set.of(role));

        if (!action.requiredRole().equals(role)) {
            assertDenied(action, WorkflowAccessDecision.FORBIDDEN);
            return;
        }

        assertThatCode(() -> action.invoke(service)).doesNotThrowAnyException();

        InOrder order = inOrder(identity, persistence, policy, runtime);
        order.verify(identity).requireVerified();
        if (action == Action.CREATE) {
            order.verify(persistence).loadDefinition("publication", 7);
            order.verify(policy).forCreation(caller, DEFINITION, RESOURCE);
        } else {
            order.verify(persistence).loadInstance("instance-1");
            order.verify(policy).forInstance(caller, INSTANCE, action.operation);
        }
        action.verifyRuntime(order, runtime);
        order.verifyNoMoreInteractions();
    }

    static Stream<Arguments> singleCanonicalRoleMatrix() {
        return Arrays.stream(Action.values())
                .flatMap(action -> Arrays.stream(ROLES).map(role -> Arguments.of(action, role)));
    }

    @ParameterizedTest(name = "{0} with malformed authority {1}")
    @MethodSource("malformedAuthorities")
    void variantsAndWildcardNeverMatch(Action action, String role) {
        authenticate(Set.of(role));
        assertDenied(action, WorkflowAccessDecision.FORBIDDEN);
    }

    static Stream<Arguments> malformedAuthorities() {
        return Arrays.stream(Action.values()).flatMap(action -> Stream.of(
                "workflow:*",
                action.requiredRole().toUpperCase(Locale.ROOT),
                " " + action.requiredRole(),
                action.requiredRole() + " ",
                action.requiredRole() + ":extra"
        ).map(role -> Arguments.of(action, role)));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void aSetOfCanonicalRolesPermitsOnlyWhenItContainsTheMatchingRole(Action action) {
        authenticate(Set.of(ROLES));
        assertThatCode(() -> action.invoke(service)).doesNotThrowAnyException();
        action.verifyRuntime(inOrder(runtime), runtime);
    }

    @ParameterizedTest(name = "{0} + resource decision {1}")
    @MethodSource("resourcePolicyDenialMatrix")
    void evenAllOperationRolesCannotOverrideResourcePolicy(Action action, WorkflowAccessDecision decision) {
        authenticate(Set.of(ROLES));
        when(policy.forCreation(any(), any(), any())).thenReturn(decision);
        when(policy.forInstance(any(), any(), any())).thenReturn(decision);
        assertDenied(action, decision);
    }

    static Stream<Arguments> resourcePolicyDenialMatrix() {
        return Arrays.stream(Action.values()).flatMap(action -> Stream.of(
                WorkflowAccessDecision.FORBIDDEN,
                WorkflowAccessDecision.HIDDEN,
                WorkflowAccessDecision.UNAVAILABLE
        ).map(decision -> Arguments.of(action, decision)));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void hiddenResourceRemainsHiddenWithoutAnyOperationRole(Action action) {
        authenticate(Set.of());
        when(policy.forCreation(any(), any(), any())).thenReturn(WorkflowAccessDecision.HIDDEN);
        when(policy.forInstance(any(), any(), any())).thenReturn(WorkflowAccessDecision.HIDDEN);
        assertDenied(action, WorkflowAccessDecision.HIDDEN);
    }

    private AuthenticatedPrincipal authenticate(Set<String> roles) {
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal(
                SUBJECT, "untrusted-display-name", roles.stream().map(Authority::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()), true);
        when(identity.requireVerified()).thenReturn(caller);
        return caller;
    }

    private void assertDenied(Action action, WorkflowAccessDecision expected) {
        assertThatThrownBy(() -> action.invoke(service))
                .isInstanceOf(WorkflowAccessException.class)
                .hasMessage(expected.name()).hasNoCause();
        verifyNoInteractions(runtime);
    }

    enum Action {
        CREATE(WorkflowOperation.CREATE),
        ACTIVATE(WorkflowOperation.ACTIVATE),
        TRANSITION(WorkflowOperation.TRANSITION),
        SNAPSHOT(WorkflowOperation.READ),
        HISTORY(WorkflowOperation.READ);

        final WorkflowOperation operation;

        Action(WorkflowOperation operation) {
            this.operation = operation;
        }

        String requiredRole() {
            return operation.requiredAuthority().value();
        }

        Object invoke(AuthorizedWorkflowService service) {
            return switch (this) {
                case CREATE -> service.createInstance("instance-1", "publication", 7, RESOURCE);
                case ACTIVATE -> service.activateInstance("instance-1");
                case TRANSITION -> service.executeTransition("instance-1", "publish", 12);
                case SNAPSHOT -> service.loadInstance("instance-1");
                case HISTORY -> service.loadHistory("instance-1");
            };
        }

        void verifyRuntime(InOrder order, WorkflowRuntime runtime) {
            switch (this) {
                case CREATE -> order.verify(runtime).createInstance("instance-1", "publication", 7, RESOURCE);
                case ACTIVATE -> order.verify(runtime).activateInstance("instance-1");
                case TRANSITION -> order.verify(runtime).executeTransition(
                        new TransitionCommand("instance-1", "publish", new ActorReference(SUBJECT), 12));
                case SNAPSHOT -> order.verify(runtime).loadInstance("instance-1");
                case HISTORY -> order.verify(runtime).loadHistory("instance-1");
            }
        }
    }
}
