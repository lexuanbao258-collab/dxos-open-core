package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.tricore.dxos.core.workflow.WorkflowInstanceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowRuntimeTest {
    private static final WorkflowDefinition DEFINITION = WorkflowDefinitionTest.approval();
    private final InMemoryWorkflowPersistence persistence = spy(new InMemoryWorkflowPersistence());
    private final WorkflowRuntime runtime = new WorkflowRuntime(persistence, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void provisionDefinition() {
        persistence.provision(DEFINITION);
    }

    @Test
    void createsActivatesAndCompletesAWorkflowWithTrustedActorsAndOrderedHistory() {
        WorkflowInstance initial = create();
        assertThat(initial.lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
        assertThat(initial.runtimeVersion()).isZero();
        assertThat(initial.createdAt()).isEqualTo(NOW);
        assertThat(initial.updatedAt()).isEqualTo(NOW);
        assertThat(runtime.loadHistory("instance-1")).isEmpty();

        WorkflowInstance active = runtime.activateInstance("instance-1");
        assertThat(active.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
        assertThat(active.runtimeVersion()).isZero();
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        TransitionResult submitted = runtime.executeTransition(command("submit", 0));
        ActorReference publisher = new ActorReference("trusted-publisher");
        TransitionResult published = runtime.executeTransition(
                new TransitionCommand("instance-1", "publish", publisher, 1));

        assertThat(submitted.previousInstance()).isSameAs(active);
        assertThat(published.previousInstance()).isSameAs(submitted.instance());
        assertThat(runtime.loadInstance("instance-1")).isSameAs(published.instance());
        assertThat(published.instance().currentState()).isEqualTo("published");
        assertThat(published.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        assertThat(published.instance().runtimeVersion()).isEqualTo(2);
        assertThat(published.instance().definitionVersion()).isEqualTo(DEFINITION.definitionVersion());
        assertThat(published.instance().resourceReference()).isEqualTo(RESOURCE);
        assertThat(published.instance().createdAt()).isEqualTo(NOW);
        assertThat(runtime.loadHistory("instance-1")).containsExactly(submitted.record(), published.record());
        assertThat(submitted.record().actorReference()).isEqualTo(ACTOR);
        assertThat(published.record().actorReference()).isEqualTo(publisher);
    }

    @Test
    void usesExactClockInstantsForAllWritesIndependentlyOfClockTimeZone() {
        var createdAt = NOW.plusNanos(123);
        WorkflowRuntime creation = new WorkflowRuntime(persistence, Clock.fixed(createdAt, ZoneId.of("Asia/Bangkok")));
        WorkflowInstance initial = creation.createInstance("instance-1", DEFINITION.definitionId(), 7, RESOURCE);
        var startedAt = createdAt.plusNanos(321);
        WorkflowRuntime activation = new WorkflowRuntime(persistence, Clock.fixed(startedAt, ZoneOffset.UTC));
        WorkflowInstance active = activation.activateInstance("instance-1");
        var occurredAt = startedAt.plusNanos(456);
        WorkflowRuntime execution = new WorkflowRuntime(persistence, Clock.fixed(occurredAt, ZoneOffset.UTC));
        TransitionResult result = execution.executeTransition(command("submit", 0));

        assertThat(initial.createdAt()).isEqualTo(createdAt);
        assertThat(initial.updatedAt()).isEqualTo(createdAt);
        assertThat(active.createdAt()).isEqualTo(createdAt);
        assertThat(active.updatedAt()).isEqualTo(startedAt);
        assertThat(result.instance().createdAt()).isEqualTo(createdAt);
        assertThat(result.instance().updatedAt()).isEqualTo(occurredAt);
        assertThat(result.record().occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void pinsDefinitionVersionsEvenWhenANewerVersionChangesTheInitialState() {
        WorkflowDefinition newer = new WorkflowDefinition(DEFINITION.definitionId(), 8, "review",
                DEFINITION.states(), DEFINITION.transitions());
        persistence.provision(newer);
        WorkflowInstance original = create();
        WorkflowInstance second = runtime.createInstance("instance-2", newer.definitionId(), 8, RESOURCE);
        assertThat(original.currentState()).isEqualTo("draft");
        assertThat(original.definitionVersion()).isEqualTo(7);
        assertThat(second.currentState()).isEqualTo("review");
        runtime.activateInstance("instance-1");
        runtime.activateInstance("instance-2");
        assertThat(runtime.executeTransition(command("submit", 0)).instance().currentState()).isEqualTo("review");
        TransitionResult result = runtime.executeTransition(new TransitionCommand("instance-2", "publish", ACTOR, 0));
        assertThat(result.instance().definitionVersion()).isEqualTo(8);
        assertThat(result.instance().runtimeVersion()).isEqualTo(1);
        assertThat(result.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
    }

    @Test
    void executesAnIndependentDefinitionWithoutBusinessSpecificStates() {
        WorkflowDefinition delivery = WorkflowDefinitionTest.delivery();
        persistence.provision(delivery);
        runtime.createInstance("instance-1", delivery.definitionId(), delivery.definitionVersion(), RESOURCE);
        runtime.activateInstance("instance-1");
        TransitionResult result = runtime.executeTransition(command("dispatch", 0));
        assertThat(result.instance().currentState()).isEqualTo("dispatched");
        assertThat(result.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        assertThat(runtime.loadHistory("instance-1")).containsExactly(result.record());
    }

    @Test
    void readsAuthoritativeDataWithoutCachingAndReturnsImmutableHistorySnapshots() {
        WorkflowInstance active = activate();
        List<TransitionRecord> earlierHistory = runtime.loadHistory("instance-1");
        TransitionResult committed = persistence.commitTransition(active.transition(DEFINITION, command("submit", 0), NOW));
        assertThat(runtime.loadInstance("instance-1")).isSameAs(committed.instance());
        assertThat(runtime.loadHistory("instance-1")).containsExactly(committed.record());
        assertThat(earlierHistory).isEmpty();
        assertThatThrownBy(() -> runtime.loadHistory("instance-1").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidStructuralInputsAndRequiresAnActorInTheCommand() {
        assertThatThrownBy(() -> new WorkflowRuntime(null, Clock.systemUTC())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkflowRuntime(persistence, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> runtime.createInstance(" ", "publication", 7, RESOURCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.createInstance("instance-1", " ", 7, RESOURCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.createInstance("instance-1", "publication", -1, RESOURCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.createInstance("instance-1", "publication", 7, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> runtime.activateInstance(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.loadInstance(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.loadHistory(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.executeTransition(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransitionCommand("instance-1", "submit", null, 0))
                .isInstanceOf(NullPointerException.class);
        verify(persistence, never()).createInstance(any());
    }

    @Test
    void rejectsMissingExactDefinitionsWithoutCreatingAnInstance() {
        assertError(() -> runtime.createInstance("instance-1", DEFINITION.definitionId(), 8, RESOURCE),
                WorkflowErrorCode.DEFINITION_NOT_FOUND);
        assertError(() -> runtime.createInstance("instance-1", "missing", 7, RESOURCE),
                WorkflowErrorCode.DEFINITION_NOT_FOUND);
        assertError(() -> runtime.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        verify(persistence, never()).createInstance(any());
    }

    @Test
    void rejectsDuplicateCreationAndReplayWithoutOverwritingExistingHistory() {
        activate();
        TransitionResult result = runtime.executeTransition(command("submit", 0));
        assertError(this::create, WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        assertError(() -> runtime.createInstance("instance-1", DEFINITION.definitionId(), 7,
                new ResourceReference("other", "other")), WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(result.instance());
        assertThat(runtime.loadHistory("instance-1")).containsExactly(result.record());
    }

    @Test
    void propagatesMissingInstancesForReadsActivationAndTransition() {
        assertError(() -> runtime.loadInstance("missing"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> runtime.loadHistory("missing"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> runtime.activateInstance("missing"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> runtime.executeTransition(new TransitionCommand("missing", "submit", ACTOR, 0)),
                WorkflowErrorCode.INSTANCE_NOT_FOUND);
        verify(persistence, never()).commitActivation(any(), any());
        verify(persistence, never()).commitTransition(any());
    }

    @Test
    void propagatesDefinitionLoadingFailuresBeforeActivationOrTransitionWrites() {
        WorkflowInstance initial = create();
        WorkflowException missing = new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND);
        doThrow(missing).when(persistence).loadDefinition(DEFINITION.definitionId(), 7);
        assertThatThrownBy(() -> runtime.activateInstance("instance-1")).isSameAs(missing);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(initial);
        verify(persistence, never()).commitActivation(any(), any());

        doCallRealMethod().when(persistence).loadDefinition(DEFINITION.definitionId(), 7);
        WorkflowInstance active = runtime.activateInstance("instance-1");
        doThrow(missing).when(persistence).loadDefinition(DEFINITION.definitionId(), 7);
        assertThatThrownBy(() -> runtime.executeTransition(command("submit", 0))).isSameAs(missing);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(active);
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        verify(persistence, never()).commitTransition(any());
    }

    @Test
    void propagatesAuthoritativeReadFailuresWithoutAttemptingWrites() {
        WorkflowException failure = new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        doThrow(failure).when(persistence).loadInstance("instance-1");
        doThrow(failure).when(persistence).loadHistory("instance-1");
        assertThatThrownBy(() -> runtime.loadInstance("instance-1")).isSameAs(failure);
        assertThatThrownBy(() -> runtime.loadHistory("instance-1")).isSameAs(failure);
        assertThatThrownBy(() -> runtime.activateInstance("instance-1")).isSameAs(failure);
        assertThatThrownBy(() -> runtime.executeTransition(command("submit", 0))).isSameAs(failure);
        verify(persistence, never()).commitActivation(any(), any());
        verify(persistence, never()).commitTransition(any());
    }

    @Test
    void rejectsTransitionsBeforeActivationUnknownTransitionsAndWrongSources() {
        create();
        assertError(() -> runtime.executeTransition(command("submit", 0)), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        runtime.activateInstance("instance-1");
        assertError(() -> runtime.executeTransition(command("missing", 0)), WorkflowErrorCode.INVALID_TRANSITION);
        assertError(() -> runtime.executeTransition(command("publish", 0)), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        assertThat(runtime.loadInstance("instance-1").runtimeVersion()).isZero();
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        verify(persistence, never()).commitTransition(any());
    }

    @Test
    void rejectsStaleVersionsAndTransitionReplayWithoutAppendingHistory() {
        activate();
        assertError(() -> runtime.executeTransition(command("submit", 1)), WorkflowErrorCode.VERSION_CONFLICT);
        TransitionResult committed = runtime.executeTransition(command("submit", 0));
        assertError(() -> runtime.executeTransition(command("submit", 0)), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(committed.instance());
        assertThat(runtime.loadHistory("instance-1")).containsExactly(committed.record());
        verify(persistence, times(1)).commitTransition(any());
    }

    @Test
    void rejectsReactivationAndCompletedOperationsWithExistingErrorPrecedence() {
        activate();
        assertError(() -> runtime.activateInstance("instance-1"), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        runtime.executeTransition(command("submit", 0));
        TransitionResult completed = runtime.executeTransition(command("publish", 1));
        assertError(() -> runtime.executeTransition(command("submit", 1)), WorkflowErrorCode.VERSION_CONFLICT);
        assertError(() -> runtime.executeTransition(command("submit", 2)), WorkflowErrorCode.INSTANCE_COMPLETED);
        assertError(() -> runtime.activateInstance("instance-1"), WorkflowErrorCode.INSTANCE_COMPLETED);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(completed.instance());
        assertThat(runtime.loadHistory("instance-1")).hasSize(2);
    }

    @Test
    void preservesTheDomainRejectionOfAnInitialTerminalState() {
        WorkflowDefinition terminal = new WorkflowDefinition("terminal", 0, "done", Set.of("done"), Set.of());
        persistence.provision(terminal);
        WorkflowInstance initial = runtime.createInstance("instance-1", "terminal", 0, RESOURCE);
        assertError(() -> runtime.activateInstance("instance-1"), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(initial);
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void rejectsABackwardsClockWithoutClampingTimestampsOrWriting() {
        WorkflowInstance initial = create();
        WorkflowRuntime earlier = new WorkflowRuntime(persistence, Clock.fixed(NOW.minusNanos(1), ZoneOffset.UTC));
        assertThatThrownBy(() -> earlier.activateInstance("instance-1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(initial);
        WorkflowRuntime later = new WorkflowRuntime(persistence, Clock.fixed(NOW.plusNanos(1), ZoneOffset.UTC));
        WorkflowInstance active = later.activateInstance("instance-1");
        assertThatThrownBy(() -> runtime.executeTransition(command("submit", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(active);
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        verify(persistence, never()).commitTransition(any());
    }

    @Test
    void preservesRuntimeVersionOverflowRejectionBeforePersistence() {
        WorkflowInstance exhausted = WorkflowInstance.restore("instance-1", DEFINITION, RESOURCE, "draft", Long.MAX_VALUE,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        doReturn(exhausted).when(persistence).loadInstance("instance-1");
        assertError(() -> runtime.executeTransition(command("submit", Long.MAX_VALUE)), WorkflowErrorCode.OPERATION_FAILURE);
        verify(persistence, never()).commitTransition(any());
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void propagatesCreationWriteOutcomesWithoutRetry(WorkflowErrorCode error) {
        failWrite(error);
        assertError(this::create, error);
        verify(persistence, times(1)).createInstance(any());
        if (error == WorkflowErrorCode.OPERATION_FAILURE) {
            assertError(() -> runtime.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
            assertError(() -> runtime.loadHistory("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        } else {
            assertThat(runtime.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
            assertThat(runtime.loadInstance("instance-1").runtimeVersion()).isZero();
            assertThat(runtime.loadHistory("instance-1")).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void propagatesActivationWriteOutcomesWithoutRetryOrTransitionHistory(WorkflowErrorCode error) {
        WorkflowInstance initial = create();
        failWrite(error);
        assertError(() -> runtime.activateInstance("instance-1"), error);
        verify(persistence, times(1)).commitActivation(any(), any());
        WorkflowInstance stored = runtime.loadInstance("instance-1");
        assertThat(stored.runtimeVersion()).isZero();
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        if (error == WorkflowErrorCode.OPERATION_FAILURE) assertThat(stored).isSameAs(initial);
        else assertThat(stored.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowErrorCode.class, names = {"OPERATION_FAILURE", "COMMIT_OUTCOME_UNKNOWN"})
    void propagatesTransitionWriteOutcomesWithoutRetryAndKeepsInstanceHistoryAtomic(WorkflowErrorCode error) {
        WorkflowInstance active = activate();
        failWrite(error);
        assertError(() -> runtime.executeTransition(command("submit", 0)), error);
        verify(persistence, times(1)).commitTransition(any());
        if (error == WorkflowErrorCode.OPERATION_FAILURE) {
            assertThat(runtime.loadInstance("instance-1")).isSameAs(active);
            assertThat(runtime.loadHistory("instance-1")).isEmpty();
        } else {
            WorkflowInstance stored = runtime.loadInstance("instance-1");
            assertThat(stored.currentState()).isEqualTo("review");
            assertThat(stored.runtimeVersion()).isEqualTo(1);
            assertThat(runtime.loadHistory("instance-1")).containsExactly(
                    new TransitionRecord("instance-1", "submit", "draft", "review", ACTOR, 1, stored.updatedAt()));
        }
    }

    @Test
    void rejectsAnActivationThatLosesTheRaceAfterLoadingAVersionZeroSource() {
        WorkflowInstance initial = create();
        Clock competingClock = mock(Clock.class);
        WorkflowInstance winner = initial.activate(DEFINITION, NOW.plusNanos(1));
        when(competingClock.instant()).thenAnswer(ignored -> {
            persistence.commitActivation(initial, winner);
            return NOW;
        });
        WorkflowRuntime competing = new WorkflowRuntime(persistence, competingClock);
        assertError(() -> competing.activateInstance("instance-1"), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(winner);
        assertThat(runtime.loadHistory("instance-1")).isEmpty();
        verify(persistence, times(2)).commitActivation(any(), any());
    }

    @Test
    void rejectsATransitionThatLosesTheRaceAfterLoadingItsSource() {
        WorkflowInstance active = activate();
        TransitionResult winner = active.transition(DEFINITION,
                new TransitionCommand("instance-1", "submit", new ActorReference("other-trusted-actor"), 0), NOW.plusNanos(1));
        Clock competingClock = mock(Clock.class);
        when(competingClock.instant()).thenAnswer(ignored -> {
            persistence.commitTransition(winner);
            return NOW;
        });
        WorkflowRuntime competing = new WorkflowRuntime(persistence, competingClock);
        assertError(() -> competing.executeTransition(command("submit", 0)), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(runtime.loadInstance("instance-1")).isSameAs(winner.instance());
        assertThat(runtime.loadHistory("instance-1")).containsExactly(winner.record());
        verify(persistence, times(2)).commitTransition(any());
    }

    private WorkflowInstance create() {
        return runtime.createInstance("instance-1", DEFINITION.definitionId(), DEFINITION.definitionVersion(), RESOURCE);
    }

    private WorkflowInstance activate() {
        create();
        return runtime.activateInstance("instance-1");
    }

    private void failWrite(WorkflowErrorCode error) {
        if (error == WorkflowErrorCode.OPERATION_FAILURE) persistence.failNextCommit();
        else persistence.loseNextCommitResponse();
    }
}
