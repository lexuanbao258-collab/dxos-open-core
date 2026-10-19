package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.TransitionRecord;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Synthetic identities and mocked ACLs prove orchestration, not production authentication or ACLs. */
class AuthorizedWorkflowServiceTest {
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition(
            "publication", 7, "draft", Set.of("draft"), Set.of());
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "instance-1", DEFINITION, RESOURCE, Instant.parse("2026-10-19T00:00:00Z"));
    private static final String SUBJECT = "oidc:verified-issuer.verified-subject";
    private final CurrentPrincipal currentPrincipal = mock(CurrentPrincipal.class);
    private final WorkflowAccessPolicy policy = mock(WorkflowAccessPolicy.class);
    private final WorkflowPersistencePort persistence = mock(WorkflowPersistencePort.class);
    private final WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    private final AuthorizedWorkflowService service = new AuthorizedWorkflowService(
            currentPrincipal, new WorkflowAuthorization(policy), persistence, runtime);

    @BeforeEach
    void setUp() {
        when(persistence.loadDefinition("publication", 7)).thenReturn(DEFINITION);
        when(persistence.loadInstance("instance-1")).thenReturn(INSTANCE);
        when(policy.forCreation(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
        when(policy.forInstance(any(), any(), any())).thenReturn(WorkflowAccessDecision.ALLOWED);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void identityFailurePreventsEvenAuthorizationLookups(Action action) {
        IllegalStateException failure = new IllegalStateException("Verified authentication is required");
        when(currentPrincipal.requireVerified()).thenThrow(failure);

        assertThatThrownBy(() -> action.invoke(service)).isSameAs(failure);
        verify(currentPrincipal).requireVerified();
        verifyNoInteractions(persistence, policy, runtime);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void nullIdentityFailsClosedBeforeAnyLookup(Action action) {
        assertThatThrownBy(() -> action.invoke(service)).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(persistence, policy, runtime);
    }

    @ParameterizedTest
    @MethodSource("denials")
    void authorityNeverOverridesResourceDenial(Action action, WorkflowAccessDecision decision) {
        verifiedCaller(action.authority());
        decision(decision);

        assertDenied(action, decision);
        verifyLookupOnly(action);
    }

    static Stream<Arguments> denials() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(
                WorkflowAccessDecision.FORBIDDEN, WorkflowAccessDecision.HIDDEN,
                WorkflowAccessDecision.UNAVAILABLE).map(decision -> Arguments.of(action, decision)));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void nullPolicyDecisionCannotDelegate(Action action) {
        verifiedCaller(action.authority());
        decision(null);

        assertDenied(action, WorkflowAccessDecision.UNAVAILABLE);
        verifyLookupOnly(action);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void policyFailureIsSanitizedAndCannotDelegate(Action action) {
        verifiedCaller(action.authority());
        when(policy.forCreation(any(), any(), any())).thenThrow(new IllegalStateException("private ACL details"));
        when(policy.forInstance(any(), any(), any())).thenThrow(new IllegalStateException("private ACL details"));

        assertDenied(action, WorkflowAccessDecision.UNAVAILABLE);
        verifyLookupOnly(action);
    }

    @ParameterizedTest
    @MethodSource("wrongAuthorities")
    void onlyExactOperationAuthorityCanDelegate(Action action, String authority) {
        verifiedCaller(authority);

        assertDenied(action, WorkflowAccessDecision.FORBIDDEN);
        verifyLookupOnly(action);
    }

    static Stream<Arguments> wrongAuthorities() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(
                "workflow:*", action.authority().toUpperCase(java.util.Locale.ROOT),
                " " + action.authority(), action.authority() + " ",
                action == Action.CREATE ? "workflow:read" : "workflow:create")
                .map(authority -> Arguments.of(action, authority)));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void missingAuthorityCannotDelegateEvenWithResourcePermission(Action action) {
        when(currentPrincipal.requireVerified()).thenReturn(new AuthenticatedPrincipal(SUBJECT, null, Set.of(), true));

        assertDenied(action, WorkflowAccessDecision.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void exactAuthorityDelegatesOnlyAfterVerifiedCallerAndAuthoritativePolicyInput(Action action) {
        AuthenticatedPrincipal caller = verifiedCaller(action.authority());
        Object expected = switch (action) {
            case CREATE, ACTIVATE, SNAPSHOT -> INSTANCE;
            case TRANSITION -> mock(TransitionResult.class);
            case HISTORY -> List.<TransitionRecord>of();
        };
        when(action.invoke(runtime)).thenAnswer(invocation -> expected);

        assertThat(action.invoke(service)).isSameAs(expected);

        InOrder order = inOrder(currentPrincipal, persistence, policy, runtime);
        order.verify(currentPrincipal).requireVerified();
        if (action == Action.CREATE) {
            order.verify(persistence).loadDefinition("publication", 7);
            order.verify(policy).forCreation(caller, DEFINITION, RESOURCE);
        } else {
            order.verify(persistence).loadInstance("instance-1");
            order.verify(policy).forInstance(caller, INSTANCE, action.operation);
        }
        action.invoke(order.verify(runtime));
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void missingAndHiddenResourcesHaveTheSameFailureWithoutRuntimeDelegation(Action action) {
        verifiedCaller(action.authority());
        if (action == Action.CREATE) {
            when(persistence.loadDefinition("publication", 7))
                    .thenThrow(new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND));
        } else {
            when(persistence.loadInstance("instance-1"))
                    .thenThrow(new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND));
        }

        assertDenied(action, WorkflowAccessDecision.HIDDEN);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void lookupFailureCannotExposeProviderDetailsOrReachRuntime(Action action) {
        verifiedCaller(action.authority());
        if (action == Action.CREATE) {
            when(persistence.loadDefinition("publication", 7)).thenThrow(new IllegalStateException("private DB details"));
        } else {
            when(persistence.loadInstance("instance-1")).thenThrow(new IllegalStateException("private DB details"));
        }

        assertDenied(action, WorkflowAccessDecision.UNAVAILABLE);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void normalizedLookupFailureRemainsUnavailable(Action action) {
        verifiedCaller(action.authority());
        WorkflowException failure = new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        if (action == Action.CREATE) {
            when(persistence.loadDefinition("publication", 7)).thenThrow(failure);
        } else {
            when(persistence.loadInstance("instance-1")).thenThrow(failure);
        }

        assertDenied(action, WorkflowAccessDecision.UNAVAILABLE);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void nullLookupCannotReachPolicyOrRuntime(Action action) {
        verifiedCaller(action.authority());
        when(persistence.loadDefinition("publication", 7)).thenReturn(null);
        when(persistence.loadInstance("instance-1")).thenReturn(null);

        assertDenied(action, WorkflowAccessDecision.UNAVAILABLE);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @MethodSource("runtimeFailures")
    void runtimeFailuresArePreservedWithoutRetries(Action action, WorkflowErrorCode code) {
        verifiedCaller(action.authority());
        WorkflowException failure = new WorkflowException(code);
        when(action.invoke(runtime)).thenThrow(failure);

        assertThatThrownBy(() -> action.invoke(service)).isSameAs(failure);
        action.invoke(verify(runtime));
        verifyNoMoreInteractions(runtime);
    }

    static Stream<Arguments> runtimeFailures() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(
                WorkflowErrorCode.VERSION_CONFLICT, WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN,
                WorkflowErrorCode.OPERATION_FAILURE).map(code -> Arguments.of(action, code)));
    }

    private AuthenticatedPrincipal verifiedCaller(String authority) {
        AuthenticatedPrincipal caller = new AuthenticatedPrincipal(SUBJECT, "display-name-is-not-actor",
                Set.of(new Authority(authority)), true);
        when(currentPrincipal.requireVerified()).thenReturn(caller);
        return caller;
    }

    private void decision(WorkflowAccessDecision decision) {
        when(policy.forCreation(any(), any(), any())).thenReturn(decision);
        when(policy.forInstance(any(), any(), any())).thenReturn(decision);
    }

    private void assertDenied(Action action, WorkflowAccessDecision decision) {
        assertThatThrownBy(() -> action.invoke(service))
                .isInstanceOf(WorkflowAccessException.class).hasMessage(decision.name()).hasNoCause();
        verifyNoInteractions(runtime);
    }

    private void verifyLookupOnly(Action action) {
        if (action == Action.CREATE) {
            verify(persistence).loadDefinition("publication", 7);
        } else {
            verify(persistence).loadInstance("instance-1");
        }
        verifyNoMoreInteractions(persistence);
    }

    enum Action {
        CREATE(WorkflowOperation.CREATE), ACTIVATE(WorkflowOperation.ACTIVATE),
        TRANSITION(WorkflowOperation.TRANSITION), SNAPSHOT(WorkflowOperation.READ), HISTORY(WorkflowOperation.READ);

        private final WorkflowOperation operation;

        Action(WorkflowOperation operation) {
            this.operation = operation;
        }

        String authority() {
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

        Object invoke(WorkflowRuntime runtime) {
            return switch (this) {
                case CREATE -> runtime.createInstance("instance-1", "publication", 7, RESOURCE);
                case ACTIVATE -> runtime.activateInstance("instance-1");
                case TRANSITION -> runtime.executeTransition(new TransitionCommand(
                        "instance-1", "publish", new ActorReference(SUBJECT), 12));
                case SNAPSHOT -> runtime.loadInstance("instance-1");
                case HISTORY -> runtime.loadHistory("instance-1");
            };
        }
    }
}
