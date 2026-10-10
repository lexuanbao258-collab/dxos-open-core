package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tricore.dxos.core.workflow.WorkflowInstanceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPersistencePortTest {
    @Test
    void unknownCreationOutcomeCanAlreadyHaveCommittedAndReplayIsStillRejected() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW.plusNanos(123));
        port.loseNextCommitResponse();

        assertError(() -> port.createInstance(initial), WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(port.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isZero();
        assertThat(port.loadHistory("instance-1")).isEmpty();
        assertError(() -> port.createInstance(initial), WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
    }

    @Test
    void unknownActivationOutcomeCanAlreadyHaveCommittedAtVersionZeroWithoutHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        WorkflowInstance activated = initial.activate(definition, NOW);
        port.loseNextCommitResponse();

        assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        assertThat(port.loadInstance("instance-1")).isSameAs(activated);
        assertThat(port.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isZero();
        assertThat(port.loadHistory("instance-1")).isEmpty();
        assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.VERSION_CONFLICT);
        TransitionResult next = port.loadInstance("instance-1").transition(definition, command("submit", 0), NOW);
        port.commitTransition(next);
        assertThat(port.loadHistory("instance-1")).containsExactly(next.record());
    }

    @Test
    void unknownTransitionOutcomeCommitsInstanceAndHistoryTogetherAndRejectsReplay() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        TransitionResult first = port.commitTransition(
                port.loadInstance("instance-1").transition(definition, command("submit", 0), NOW));
        List<TransitionRecord> earlierHistory = port.loadHistory("instance-1");
        TransitionResult last = first.instance().transition(definition, command("publish", 1), NOW.plusNanos(321));
        port.loseNextCommitResponse();

        assertError(() -> port.commitTransition(last), WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        assertThat(port.loadInstance("instance-1")).isSameAs(last.instance());
        assertThat(port.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isEqualTo(2);
        assertThat(port.loadHistory("instance-1")).containsExactly(first.record(), last.record());
        assertThat(port.loadHistory("instance-1").getLast().occurredAt())
                .isEqualTo(port.loadInstance("instance-1").updatedAt());
        assertThat(earlierHistory).containsExactly(first.record());
        assertError(() -> port.commitTransition(last), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(port.loadInstance("instance-1")).isSameAs(last.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(first.record(), last.record());
    }

    @Test
    void rejectsSameKeyForgedTransitionTargetsAndLifecyclesAgainstAuthoritativeDefinition() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        List<WorkflowDefinition> altered = List.of(
                new WorkflowDefinition(definition.definitionId(), definition.definitionVersion(),
                        definition.initialState(), definition.states(),
                        Set.of(new WorkflowTransition("submit", "draft", "published"))),
                new WorkflowDefinition(definition.definitionId(), definition.definitionVersion(),
                        definition.initialState(), definition.states(),
                        Set.of(new WorkflowTransition("submit", "draft", "review"))));

        for (WorkflowDefinition forged : altered) {
            TransitionResult proposal = original.transition(forged, command("submit", 0), NOW);
            assertError(() -> port.commitTransition(proposal), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            assertThat(port.loadInstance("instance-1")).isSameAs(original);
            assertThat(port.loadHistory("instance-1")).isEmpty();
        }
        TransitionResult valid = original.transition(definition, command("submit", 0), NOW);
        port.commitTransition(valid);
        assertThat(port.loadInstance("instance-1")).isSameAs(valid.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(valid.record());
    }

    @Test
    void rejectsTransitionIdsMissingFromTheAuthoritativeSameKeyDefinition() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        WorkflowDefinition forged = new WorkflowDefinition(definition.definitionId(), definition.definitionVersion(),
                definition.initialState(), definition.states(), Set.of(new WorkflowTransition("skip", "draft", "published")));
        TransitionResult proposal = original.transition(forged, command("skip", 0), NOW);

        assertError(() -> port.commitTransition(proposal), WorkflowErrorCode.INVALID_TRANSITION);
        assertThat(port.loadInstance("instance-1")).isSameAs(original);
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void commitsStateVersionAndExactlyOneMatchingHistoryRecord() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        TransitionResult proposal = port.loadInstance("instance-1").transition(definition, command("submit", 0), NOW);
        assertThat(port.loadInstance("instance-1").currentState()).isEqualTo("draft");
        assertThat(port.loadHistory("instance-1")).isEmpty();
        TransitionResult committed = port.commitTransition(proposal);
        assertThat(port.loadInstance("instance-1")).isSameAs(committed.instance());
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isEqualTo(1);
        assertThat(port.loadHistory("instance-1")).containsExactly(committed.record());
    }

    @Test
    void rejectsStaleConcurrentProposalAndReplayWithoutChangingStateOrHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        TransitionResult first = original.transition(definition, command("submit", 0), NOW);
        TransitionResult competing = original.transition(definition,
                new TransitionCommand("instance-1", "submit", new ActorReference("other-actor"), 0), NOW.plusSeconds(1));
        port.commitTransition(first);
        assertError(() -> port.commitTransition(competing), WorkflowErrorCode.VERSION_CONFLICT);
        assertError(() -> port.commitTransition(first), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(port.loadInstance("instance-1")).isSameAs(first.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(first.record());
    }

    @Test
    void operationFailureLeavesBothStateAndHistoryUnchangedAndAllowsExplicitRetry() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        TransitionResult proposal = original.transition(definition, command("submit", 0), NOW);
        port.failNextCommit();
        assertError(() -> port.commitTransition(proposal), WorkflowErrorCode.OPERATION_FAILURE);
        assertThat(port.loadInstance("instance-1")).isSameAs(original);
        assertThat(port.loadHistory("instance-1")).isEmpty();
        port.commitTransition(proposal);
        assertThat(port.loadHistory("instance-1")).containsExactly(proposal.record());
    }

    @Test
    void ordersHistoryByUniqueRuntimeVersionEvenWhenTimestampsTie() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        TransitionResult first = port.commitTransition(port.loadInstance("instance-1")
                .transition(definition, command("submit", 0), NOW));
        List<TransitionRecord> snapshot = port.loadHistory("instance-1");
        TransitionResult second = port.commitTransition(port.loadInstance("instance-1")
                .transition(definition, command("publish", 1), NOW));
        assertThat(port.loadHistory("instance-1")).containsExactly(first.record(), second.record());
        assertThat(port.loadHistory("instance-1")).extracting(TransitionRecord::resultingRuntimeVersion).containsExactly(1L, 2L);
        assertThat(snapshot).containsExactly(first.record());
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(List.of(second.record(), first.record()).stream().sorted(TransitionRecord.BY_RUNTIME_VERSION).toList())
                .containsExactly(first.record(), second.record());
    }

    @Test
    void rejectsUnknownInstancesForBothReadsAndCommit() {
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        assertError(() -> port.loadInstance("missing"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> port.loadHistory("missing"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> port.commitTransition(active(definition).transition(definition, command("submit", 0), NOW)),
                WorkflowErrorCode.INSTANCE_NOT_FOUND);
    }

    @Test
    void rejectsProposalFromConsumerRestoredSourceOrDefinitionBinding() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        WorkflowInstance restored = WorkflowInstance.restore("instance-1", definition, RESOURCE, "review", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        // Commit a legitimate first transition, then try changing the pinned binding at the same runtime version.
        port.commitTransition(original.transition(definition, command("submit", 0), NOW));
        WorkflowDefinition newer = new WorkflowDefinition(definition.definitionId(), 8, definition.initialState(),
                definition.states(), definition.transitions());
        WorkflowInstance changedBinding = WorkflowInstance.restore("instance-1", newer, RESOURCE, "review", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> port.commitTransition(changedBinding.transition(newer, command("publish", 1), NOW)),
                WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        WorkflowInstance changedResource = WorkflowInstance.restore("instance-1", definition,
                new ResourceReference("other", "other"), "review", 1, WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> port.commitTransition(changedResource.transition(definition, command("publish", 1), NOW)),
                WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        WorkflowInstance changedSource = WorkflowInstance.restore("instance-1", definition, RESOURCE, "draft", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> port.commitTransition(changedSource.transition(definition, command("submit", 1), NOW)),
                WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        assertThat(port.loadHistory("instance-1")).hasSize(1);
        // A structurally reconstituted, unchanged snapshot is usable by a real adapter.
        port.commitTransition(restored.transition(definition, command("publish", 1), NOW));
        assertThat(port.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
    }

    @Test
    void invalidAndTerminalAttemptsDoNotAppendHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.delivery();
        InMemoryWorkflowPersistence port = seeded(definition);
        assertError(() -> port.loadInstance("instance-1").transition(definition, command("missing", 0), NOW),
                WorkflowErrorCode.INVALID_TRANSITION);
        assertThat(port.loadHistory("instance-1")).isEmpty();
        TransitionResult last = port.commitTransition(port.loadInstance("instance-1")
                .transition(definition, command("dispatch", 0), NOW));
        assertError(() -> port.loadInstance("instance-1").transition(definition, command("dispatch", 1), NOW),
                WorkflowErrorCode.INSTANCE_COMPLETED);
        assertThat(port.loadInstance("instance-1")).isSameAs(last.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(last.record());
    }

    @Test
    void normalizedErrorsCarryNoProviderCauseOrStackTrace() {
        for (WorkflowErrorCode code : WorkflowErrorCode.values()) {
            WorkflowException error = new WorkflowException(code);
            assertThat(error.code()).isEqualTo(code);
            assertThat(error.getMessage()).isEqualTo(code.name());
            assertThat(error.getCause()).isNull();
            assertThat(error.getStackTrace()).isEmpty();
        }
    }

    @Test
    void loadsOnlyAuthoritativeExactDefinitionVersionsAndProtectsProvisionedContent() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowDefinition newer = new WorkflowDefinition(definition.definitionId(), 8,
                definition.initialState(), definition.states(), definition.transitions());
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        port.provision(newer);
        WorkflowDefinition zero = new WorkflowDefinition(definition.definitionId(), 0,
                definition.initialState(), definition.states(), definition.transitions());
        port.provision(zero);
        port.provision(new WorkflowDefinition(definition.definitionId(), definition.definitionVersion(),
                definition.initialState(), definition.states(), definition.transitions()));

        assertThat(port.loadDefinition(definition.definitionId(), definition.definitionVersion())).isSameAs(definition);
        assertThat(port.loadDefinition(newer.definitionId(), newer.definitionVersion())).isSameAs(newer);
        assertThat(port.loadDefinition(zero.definitionId(), 0)).isSameAs(zero);
        assertError(() -> port.loadDefinition(definition.definitionId(), 9), WorkflowErrorCode.DEFINITION_NOT_FOUND);
        assertError(() -> port.loadDefinition("missing", definition.definitionVersion()), WorkflowErrorCode.DEFINITION_NOT_FOUND);
        WorkflowDefinition changed = new WorkflowDefinition(definition.definitionId(), definition.definitionVersion(),
                definition.initialState(), definition.states(), Set.of());
        assertThatThrownBy(() -> port.provision(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(port.loadDefinition(definition.definitionId(), definition.definitionVersion())).isSameAs(definition);
        assertThatThrownBy(() -> port.loadDefinition(" ", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.loadDefinition(definition.definitionId(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsOnlyInitialSnapshotsWithEmptyHistoryAndPreservesNanosecondTimestamps() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        var createdAt = NOW.plusNanos(123);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, createdAt);

        assertThat(port.createInstance(initial)).isSameAs(initial);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(initial.lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
        assertThat(initial.runtimeVersion()).isZero();
        assertThat(initial.currentState()).isEqualTo(definition.initialState());
        assertThat(initial.createdAt()).isEqualTo(createdAt);
        assertThat(initial.updatedAt()).isEqualTo(createdAt);
        assertThat(port.loadHistory("instance-1")).isEmpty();
        assertThatThrownBy(() -> port.loadHistory("instance-1").clear())
                .isInstanceOf(UnsupportedOperationException.class);
        WorkflowInstance second = WorkflowInstance.notStarted("instance-2", definition, RESOURCE, createdAt);
        assertThat(port.createInstance(second)).isSameAs(second);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(port.loadHistory("instance-2")).isEmpty();
    }

    @Test
    void duplicateCreationNeverOverwritesAnExistingInstanceOrItsHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        TransitionResult committed = port.commitTransition(
                port.loadInstance("instance-1").transition(definition, command("submit", 0), NOW));
        WorkflowInstance duplicate = WorkflowInstance.notStarted("instance-1", definition,
                new ResourceReference("other", "other"), NOW.plusSeconds(1));

        assertError(() -> port.createInstance(duplicate), WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        WorkflowDefinition missing = new WorkflowDefinition("missing", 0, definition.initialState(),
                definition.states(), definition.transitions());
        assertError(() -> port.createInstance(WorkflowInstance.notStarted("instance-1", missing, RESOURCE, NOW)),
                WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        assertThat(port.loadInstance("instance-1")).isSameAs(committed.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(committed.record());
    }

    @Test
    void identicalCreationReplayStillConflicts() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        assertError(() -> port.createInstance(initial), WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void creationRequiresProvisioningAndDoesNotLeavePartialDataOnFailure() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        assertError(() -> port.createInstance(initial), WorkflowErrorCode.DEFINITION_NOT_FOUND);
        assertError(() -> port.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> port.loadHistory("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);

        port.provision(definition);
        port.failNextCommit();
        assertError(() -> port.createInstance(initial), WorkflowErrorCode.OPERATION_FAILURE);
        assertError(() -> port.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertError(() -> port.loadHistory("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertThat(port.createInstance(initial)).isSameAs(initial);
    }

    @Test
    void creationRejectsNoninitialOrForgedDefinitionSnapshots() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowDefinition changedInitial = new WorkflowDefinition(definition.definitionId(),
                definition.definitionVersion(), "review", definition.states(), definition.transitions());
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        List<WorkflowInstance> invalid = List.of(
                active(definition),
                WorkflowInstance.restore("instance-1", definition, RESOURCE, "published", 2,
                        WorkflowLifecycle.COMPLETED, NOW, NOW),
                WorkflowInstance.notStarted("instance-1", changedInitial, RESOURCE, NOW));
        for (WorkflowInstance snapshot : invalid) {
            assertError(() -> port.createInstance(snapshot), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            assertError(() -> port.loadInstance("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
            assertError(() -> port.loadHistory("instance-1"), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        }
    }

    @Test
    void concurrentCreationsOfTheSameIdHaveExactlyOneWinner() throws Exception {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance first = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        WorkflowInstance second = WorkflowInstance.notStarted("instance-1", definition,
                new ResourceReference("other", "other"), NOW);

        assertThat(race(() -> port.createInstance(first), () -> port.createInstance(second)))
                .containsExactlyInAnyOrder("COMMITTED", "INSTANCE_ALREADY_EXISTS");
        assertThat(port.loadInstance("instance-1")).isIn(first, second);
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void activationAcceptsAnEqualRestoredSourceWithoutIncrementingVersionOrHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        WorkflowInstance restored = WorkflowInstance.restore("instance-1", definition, RESOURCE,
                definition.initialState(), 0, WorkflowLifecycle.NOT_STARTED, NOW, NOW);
        WorkflowInstance activated = restored.activate(definition, NOW.plusNanos(321));

        assertThat(restored).isNotSameAs(initial);
        assertThat(port.commitActivation(restored, activated)).isSameAs(activated);
        assertThat(port.loadInstance("instance-1")).isSameAs(activated);
        assertThat(activated.lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
        assertThat(activated.runtimeVersion()).isZero();
        assertThat(activated.definitionId()).isEqualTo(initial.definitionId());
        assertThat(activated.definitionVersion()).isEqualTo(initial.definitionVersion());
        assertThat(activated.resourceReference()).isEqualTo(initial.resourceReference());
        assertThat(activated.currentState()).isEqualTo(initial.currentState());
        assertThat(activated.createdAt()).isEqualTo(initial.createdAt());
        assertThat(activated.updatedAt()).isEqualTo(NOW.plusNanos(321));
        assertThat(port.loadHistory("instance-1")).isEmpty();
        assertThat(port.commitTransition(activated.transition(definition, command("submit", 0), activated.updatedAt()))
                .instance().runtimeVersion()).isEqualTo(1);
    }

    @Test
    void activationComparesDefinitionResourceStateAndExactSourceTimestamps() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowDefinition otherId = new WorkflowDefinition("other", definition.definitionVersion(),
                definition.initialState(), definition.states(), definition.transitions());
        WorkflowDefinition otherVersion = new WorkflowDefinition(definition.definitionId(), 8,
                definition.initialState(), definition.states(), definition.transitions());
        WorkflowDefinition otherInitial = new WorkflowDefinition(definition.definitionId(),
                definition.definitionVersion(), "review", definition.states(), definition.transitions());
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        WorkflowInstance activated = initial.activate(definition, NOW.plusSeconds(1));
        List<WorkflowInstance> forgedSources = List.of(
                WorkflowInstance.notStarted("instance-1", otherId, RESOURCE, NOW),
                WorkflowInstance.notStarted("instance-1", otherVersion, RESOURCE, NOW),
                WorkflowInstance.notStarted("instance-1", definition, new ResourceReference("other", "other"), NOW),
                WorkflowInstance.notStarted("instance-1", otherInitial, RESOURCE, NOW),
                WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW.plusNanos(1)));
        for (WorkflowInstance expected : forgedSources) {
            assertError(() -> port.commitActivation(expected, activated), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            assertThat(port.loadInstance("instance-1")).isSameAs(initial);
            assertThat(port.loadHistory("instance-1")).isEmpty();
        }
    }

    @Test
    void activationRejectsProposalsThatDoNotMatchDomainActivation() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowDefinition otherId = new WorkflowDefinition("other", definition.definitionVersion(),
                definition.initialState(), definition.states(), definition.transitions());
        WorkflowDefinition otherVersion = new WorkflowDefinition(definition.definitionId(), 8,
                definition.initialState(), definition.states(), definition.transitions());
        WorkflowDefinition otherInitial = new WorkflowDefinition(definition.definitionId(),
                definition.definitionVersion(), "review", definition.states(), definition.transitions());
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        List<WorkflowInstance> forgedTargets = List.of(
                WorkflowInstance.notStarted("other", definition, RESOURCE, NOW).activate(definition, NOW),
                active(otherId),
                active(otherVersion),
                WorkflowInstance.notStarted("instance-1", definition, new ResourceReference("other", "other"), NOW)
                        .activate(definition, NOW),
                active(otherInitial),
                WorkflowInstance.restore("instance-1", definition, RESOURCE, definition.initialState(), 1,
                        WorkflowLifecycle.ACTIVE, NOW, NOW),
                WorkflowInstance.restore("instance-1", definition, RESOURCE, definition.initialState(), 0,
                        WorkflowLifecycle.ACTIVE, NOW.minusSeconds(1), NOW),
                WorkflowInstance.restore("instance-1", definition, RESOURCE, definition.initialState(), 0,
                        WorkflowLifecycle.ACTIVE, NOW.minusSeconds(1), NOW.minusNanos(1)),
                initial);
        for (WorkflowInstance activated : forgedTargets) {
            assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            assertThat(port.loadInstance("instance-1")).isSameAs(initial);
            assertThat(port.loadHistory("instance-1")).isEmpty();
        }
    }

    @Test
    void activationUsesTheAuthoritativeDefinitionEvenForAnInitialTerminalState() {
        WorkflowDefinition supplied = WorkflowDefinitionTest.approval();
        WorkflowDefinition terminal = new WorkflowDefinition(supplied.definitionId(), supplied.definitionVersion(),
                supplied.initialState(), supplied.states(), Set.of());
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(terminal);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", terminal, RESOURCE, NOW);
        port.createInstance(initial);

        assertError(() -> port.commitActivation(initial, active(supplied)), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void activationChecksVersionAndLifecycleAndNeverReactivatesCompletedInstances() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance current = port.loadInstance("instance-1");
        WorkflowInstance stale = WorkflowInstance.restore("instance-1", definition, RESOURCE,
                definition.initialState(), 1, WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> port.commitActivation(stale, stale), WorkflowErrorCode.VERSION_CONFLICT);
        assertError(() -> port.commitActivation(current, current), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);

        port.commitTransition(current.transition(definition, command("submit", 0), NOW));
        WorkflowInstance completed = port.commitTransition(
                port.loadInstance("instance-1").transition(definition, command("publish", 1), NOW)).instance();
        assertError(() -> port.commitActivation(completed, completed), WorkflowErrorCode.INSTANCE_COMPLETED);
        assertThat(port.loadInstance("instance-1")).isSameAs(completed);
        assertThat(port.loadHistory("instance-1")).hasSize(2);
    }

    @Test
    void activationFailureChangesNothingAndAllowsExplicitRetryButNotReplay() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        WorkflowInstance activated = initial.activate(definition, NOW);
        assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.INSTANCE_NOT_FOUND);
        port.createInstance(initial);
        port.failNextCommit();
        assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.OPERATION_FAILURE);
        assertThat(port.loadInstance("instance-1")).isSameAs(initial);
        assertThat(port.loadHistory("instance-1")).isEmpty();

        port.commitActivation(initial, activated);
        assertError(() -> port.commitActivation(initial, activated), WorkflowErrorCode.VERSION_CONFLICT);
        assertThat(port.loadInstance("instance-1")).isSameAs(activated);
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isZero();
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    @Test
    void concurrentActivationsHaveOneWinnerDespiteBothExpectingVersionZero() throws Exception {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        WorkflowInstance first = initial.activate(definition, NOW);
        WorkflowInstance second = initial.activate(definition, NOW.plusSeconds(1));

        assertThat(race(() -> port.commitActivation(initial, first), () -> port.commitActivation(initial, second)))
                .containsExactlyInAnyOrder("COMMITTED", "VERSION_CONFLICT");
        assertThat(port.loadInstance("instance-1")).isIn(first, second);
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isZero();
        assertThat(port.loadHistory("instance-1")).isEmpty();
    }

    private List<String> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> writeAfterSignal(first, start));
            var two = executor.submit(() -> writeAfterSignal(second, start));
            start.countDown();
            return List.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCommitsHaveExactlyOneWinner() throws Exception {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        WorkflowInstance original = port.loadInstance("instance-1");
        TransitionResult first = original.transition(definition, command("submit", 0), NOW);
        TransitionResult second = original.transition(definition,
                new TransitionCommand("instance-1", "submit", new ActorReference("second"), 0), NOW);

        assertThat(race(() -> port.commitTransition(first), () -> port.commitTransition(second)))
                .containsExactlyInAnyOrder("COMMITTED", "VERSION_CONFLICT");
        assertThat(port.loadInstance("instance-1").runtimeVersion()).isEqualTo(1);
        assertThat(port.loadHistory("instance-1")).hasSize(1);
        assertThat(port.loadHistory("instance-1").getFirst().targetState())
                .isEqualTo(port.loadInstance("instance-1").currentState());
    }

    @Test
    void failureOnLaterTransitionPreservesAlreadyCommittedHistory() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        InMemoryWorkflowPersistence port = seeded(definition);
        TransitionResult first = port.commitTransition(port.loadInstance("instance-1")
                .transition(definition, command("submit", 0), NOW));
        TransitionResult second = first.instance().transition(definition, command("publish", 1), NOW);
        port.failNextCommit();
        assertError(() -> port.commitTransition(second), WorkflowErrorCode.OPERATION_FAILURE);
        assertThat(port.loadInstance("instance-1")).isSameAs(first.instance());
        assertThat(port.loadHistory("instance-1")).containsExactly(first.record());
    }

    @Test
    void portProtectsTerminalSnapshotEvenAgainstReconstitutedActiveProposal() {
        WorkflowDefinition definition = WorkflowDefinitionTest.delivery();
        InMemoryWorkflowPersistence port = seeded(definition);
        port.commitTransition(port.loadInstance("instance-1").transition(definition, command("dispatch", 0), NOW));
        // Deliberately break the immutable-definition-key assumption to exercise the persistence guard.
        WorkflowDefinition altered = new WorkflowDefinition("delivery", 2, "queued", definition.states(),
                Set.of(new WorkflowTransition("dispatch", "queued", "dispatched"),
                        new WorkflowTransition("reopen", "dispatched", "queued")));
        WorkflowInstance forged = WorkflowInstance.restore("instance-1", altered, RESOURCE, "dispatched", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> port.commitTransition(forged.transition(altered, command("reopen", 1), NOW)),
                WorkflowErrorCode.INSTANCE_COMPLETED);
        assertThat(port.loadInstance("instance-1").lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        assertThat(port.loadHistory("instance-1")).hasSize(1);
    }

    private String writeAfterSignal(Runnable operation, CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start signal timed out");
        try {
            operation.run();
            return "COMMITTED";
        } catch (WorkflowException error) {
            return error.code().name();
        }
    }

    private InMemoryWorkflowPersistence seeded(WorkflowDefinition definition) {
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.provision(definition);
        WorkflowInstance initial = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        port.createInstance(initial);
        port.commitActivation(initial, initial.activate(definition, NOW));
        return port;
    }
}
