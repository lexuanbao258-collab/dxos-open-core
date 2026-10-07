package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.tricore.dxos.core.workflow.WorkflowInstanceTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPersistencePortTest {
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

    private static InMemoryWorkflowPersistence seeded(WorkflowDefinition definition) {
        InMemoryWorkflowPersistence port = new InMemoryWorkflowPersistence();
        port.seed(active(definition));
        return port;
    }
}
