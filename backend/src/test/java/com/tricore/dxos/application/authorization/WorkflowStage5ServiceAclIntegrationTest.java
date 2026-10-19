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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Stage 5 application integration only: real policy + Stage-3 service, mocked DB and
 * Identity. This is NOT a JDBC ACL adapter, live OIDC test or transaction guarantee.
 */
class WorkflowStage5ServiceAclIntegrationTest {
    private static final String ACTOR = "oidc:verified.issuer-subject";
    private static final ResourceReference RESOURCE = new ResourceReference("document", "document-a");
    private static final WorkflowDefinition DEFINITION =
            new WorkflowDefinition("publication", 7, "draft", Set.of("draft"), Set.of());
    private static final WorkflowInstance INSTANCE = WorkflowInstance.notStarted(
            "instance-a", DEFINITION, RESOURCE, Instant.parse("2026-10-19T00:00:00Z"));
    private static final WorkflowAclSnapshotReader.DefinitionBinding BINDING =
            new WorkflowAclSnapshotReader.DefinitionBinding("publication", 7);

    private final CurrentPrincipal principal = mock(CurrentPrincipal.class);
    private final WorkflowAclSnapshotReader reader = mock(WorkflowAclSnapshotReader.class);
    private final WorkflowPersistencePort persistence = mock(WorkflowPersistencePort.class);
    private final WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    private final WorkflowAuthorization authorization = new WorkflowAuthorization(
            new TenantResourceWorkflowAccessPolicy(reader));
    private final AuthorizedWorkflowService service =
            new AuthorizedWorkflowService(principal, authorization, persistence, runtime);

    @BeforeEach
    void sourceSnapshots() {
        when(persistence.loadDefinition("publication", 7)).thenReturn(DEFINITION);
        when(persistence.loadInstance("instance-a")).thenReturn(INSTANCE);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void exactGrantMembershipAndAuthorityPermitDelegation(Action action) {
        caller(action.operation.requiredAuthority().value());
        respond(true, true, false, Set.of(action.operation), Set.of(BINDING));
        assertThatCode(() -> action.call(service)).doesNotThrowAnyException();

        verify(principal).requireVerified();
        verify(reader, times(1)).read(RESOURCE, ACTOR);
        action.verifyRuntime(runtime);
        verifyNoMoreInteractions(runtime);
    }

    @ParameterizedTest
    @MethodSource("resourceDenials")
    void missingTenantMembershipOwnerGrantOrTombstoneCannotReachRuntime(
            Action action, boolean activeResource, boolean member,
            boolean owner, Set<WorkflowOperation> grants, WorkflowAccessDecision expected) {
        caller(action.operation.requiredAuthority().value());
        respond(activeResource, member, owner, grants, Set.of(BINDING));
        denied(action, expected);
        verify(reader, times(1)).read(RESOURCE, ACTOR);
    }

    static Stream<Arguments> resourceDenials() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(
                Arguments.of(action, true, false, true, Set.of(action.operation),
                        WorkflowAccessDecision.HIDDEN), // other tenant or membership revoked
                Arguments.of(action, false, true, true, Set.of(action.operation),
                        WorkflowAccessDecision.HIDDEN), // tombstoned
                Arguments.of(action, true, true, false, Set.of(),
                        WorkflowAccessDecision.HIDDEN), // no visibility
                Arguments.of(action, true, true, true, Set.of(),
                        WorkflowAccessDecision.FORBIDDEN) // owner alone is not a grant
        ));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void missingUnmappedResourceIsHiddenForEveryOperation(Action action) {
        caller(action.operation.requiredAuthority().value());
        when(reader.read(RESOURCE, ACTOR)).thenReturn(Optional.empty());
        denied(action, WorkflowAccessDecision.HIDDEN);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void authorityAloneCannotBypassMissingResourceAcl(Action action) {
        caller("workflow:create", "workflow:activate", "workflow:transition", "workflow:read");
        // Active member is visible via a *different* operation grant; requested grant absent.
        Set<WorkflowOperation> unrelated = action.operation == WorkflowOperation.READ
                ? Set.of(WorkflowOperation.CREATE) : Set.of(WorkflowOperation.READ);
        respond(true, true, false, unrelated, Set.of(BINDING));
        denied(action, WorkflowAccessDecision.FORBIDDEN);
    }

    @Test
    void activeGrantWithoutExactDefinitionBindingNeverReachesCreateRuntime() {
        caller("workflow:create");
        respond(true, true, false, Set.of(WorkflowOperation.CREATE), Set.of());
        denied(Action.CREATE, WorkflowAccessDecision.HIDDEN);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void failingAclSourceCannotReachRuntime(Action action) {
        caller(action.operation.requiredAuthority().value());
        when(reader.read(RESOURCE, ACTOR)).thenThrow(new IllegalStateException("SQL provider detail"));
        denied(action, WorkflowAccessDecision.UNAVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void unverifiedCallerCannotEvenReadProtectedAclOrWorkflow(Action action) {
        when(principal.requireVerified()).thenThrow(new IllegalStateException("Verified authentication is required"));
        assertThatThrownBy(() -> action.call(service))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Verified authentication is required");
        verifyNoInteractions(persistence, reader, runtime);
    }

    @Test
    void instanceBindingDoesNotDependOnActiveCreateBinding() {
        caller("workflow:read");
        respond(true, true, false, Set.of(WorkflowOperation.READ), Set.of());
        assertThatCode(() -> service.loadHistory("instance-a")).doesNotThrowAnyException();
        verify(runtime).loadHistory("instance-a");
    }

    private void caller(String... roles) {
        Set<Authority> authorities = java.util.Arrays.stream(roles)
                .map(Authority::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal(
                ACTOR, "not-an-actor", authorities, true));
    }

    private void respond(boolean resourceActive, boolean memberActive, boolean owner,
                         Set<WorkflowOperation> grants,
                         Set<WorkflowAclSnapshotReader.DefinitionBinding> definitions) {
        when(reader.read(RESOURCE, ACTOR)).thenReturn(Optional.of(
                new WorkflowAclSnapshotReader.ResourceAclSnapshot(RESOURCE, ACTOR, "tenant-a",
                        resourceActive, memberActive, owner, grants, definitions)));
    }

    private void denied(Action action, WorkflowAccessDecision expected) {
        assertThatThrownBy(() -> action.call(service))
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

        Object call(AuthorizedWorkflowService service) {
            return switch (this) {
                case CREATE -> service.createInstance("instance-a", "publication", 7, RESOURCE);
                case ACTIVATE -> service.activateInstance("instance-a");
                case TRANSITION -> service.executeTransition("instance-a", "publish", 12);
                case SNAPSHOT -> service.loadInstance("instance-a");
                case HISTORY -> service.loadHistory("instance-a");
            };
        }

        void verifyRuntime(WorkflowRuntime runtime) {
            switch (this) {
                case CREATE -> verify(runtime).createInstance("instance-a", "publication", 7, RESOURCE);
                case ACTIVATE -> verify(runtime).activateInstance("instance-a");
                case TRANSITION -> verify(runtime).executeTransition(
                        new TransitionCommand("instance-a", "publish", new ActorReference(ACTOR), 12));
                case SNAPSHOT -> verify(runtime).loadInstance("instance-a");
                case HISTORY -> verify(runtime).loadHistory("instance-a");
            }
        }
    }
}
