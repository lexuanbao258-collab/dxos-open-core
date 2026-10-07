package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowInstanceTest {
    static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    static final ActorReference ACTOR = new ActorReference("actor-opaque");
    static final ResourceReference RESOURCE = new ResourceReference("document", "document-1");

    @Test
    void followsTwoIndependentLifecyclesWithoutBusinessSpecificStates() {
        WorkflowDefinition approval = WorkflowDefinitionTest.approval();
        WorkflowInstance draft = active(approval);
        TransitionResult submitted = draft.transition(approval, command("submit", 0), NOW);
        assertThat(submitted.instance().currentState()).isEqualTo("review");
        assertThat(submitted.instance().lifecycle()).isEqualTo(WorkflowLifecycle.ACTIVE);
        TransitionResult published = submitted.instance().transition(approval, command("publish", 1), NOW);
        assertThat(published.instance().currentState()).isEqualTo("published");
        assertThat(published.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        WorkflowDefinition delivery = WorkflowDefinitionTest.delivery();
        TransitionResult dispatched = active(delivery).transition(delivery, command("dispatch", 0), NOW);
        assertThat(dispatched.instance().currentState()).isEqualTo("dispatched");
        assertThat(dispatched.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
    }

    @Test
    void pinsDefinitionVersionSeparatelyFromIncrementedRuntimeVersion() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance before = active(definition);
        TransitionResult result = before.transition(definition, command("submit", 0), NOW.plusSeconds(1));
        assertThat(before.currentState()).isEqualTo("draft");
        assertThat(before.runtimeVersion()).isZero();
        assertThat(result.expectedVersion()).isZero();
        assertThat(result.previousInstance()).isSameAs(before);
        assertThat(result.instance().definitionId()).isEqualTo("publication");
        assertThat(result.instance().definitionVersion()).isEqualTo(7);
        assertThat(result.instance().runtimeVersion()).isEqualTo(1);
        assertThat(result.instance().resourceReference()).isEqualTo(RESOURCE);
        assertThat(result.instance().createdAt()).isEqualTo(NOW);
        assertThat(result.instance().updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(result.record()).isEqualTo(new TransitionRecord("instance-1", "submit", "draft", "review",
                ACTOR, 1, NOW.plusSeconds(1)));
    }

    @Test
    void rejectsAnotherDefinitionIdOrVersion() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance instance = active(definition);
        for (WorkflowDefinition other : Set.of(
                new WorkflowDefinition("other", 7, definition.initialState(), definition.states(), definition.transitions()),
                new WorkflowDefinition("publication", 8, definition.initialState(), definition.states(), definition.transitions()))) {
            assertError(() -> instance.transition(other, command("submit", 0), NOW), WorkflowErrorCode.DEFINITION_NOT_FOUND);
        }
        assertThat(instance.currentState()).isEqualTo("draft");
    }

    @Test
    void rejectsUnknownTransitionAndSourceMismatchWithoutChangingSnapshot() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance instance = active(definition);
        assertError(() -> instance.transition(definition, command("missing", 0), NOW), WorkflowErrorCode.INVALID_TRANSITION);
        assertError(() -> instance.transition(definition, command("publish", 0), NOW), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        assertThat(instance.currentState()).isEqualTo("draft");
        assertThat(instance.runtimeVersion()).isZero();
        assertThat(instance.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsVersionConflictAndWrongInstance() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance instance = active(definition);
        assertError(() -> instance.transition(definition, command("submit", 1), NOW), WorkflowErrorCode.VERSION_CONFLICT);
        assertError(() -> instance.transition(definition, new TransitionCommand("another", "submit", ACTOR, 0), NOW),
                WorkflowErrorCode.INSTANCE_NOT_FOUND);
        assertThat(instance.runtimeVersion()).isZero();
    }

    @Test
    void preventsTransitionBeforeActivationAndReactivation() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance pending = WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW);
        assertThat(pending.lifecycle()).isEqualTo(WorkflowLifecycle.NOT_STARTED);
        assertError(() -> pending.transition(definition, command("submit", 0), NOW), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        WorkflowInstance active = pending.activate(definition, NOW);
        assertThat(active.runtimeVersion()).isZero();
        assertError(() -> active.activate(definition, NOW), WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
    }

    @Test
    void protectsCompletedInstanceAgainstTransitionsAndReopen() {
        WorkflowDefinition definition = WorkflowDefinitionTest.delivery();
        WorkflowInstance completed = active(definition).transition(definition, command("dispatch", 0), NOW).instance();
        assertError(() -> completed.transition(definition, command("dispatch", 1), NOW), WorkflowErrorCode.INSTANCE_COMPLETED);
        assertError(() -> completed.activate(definition, NOW), WorkflowErrorCode.INSTANCE_COMPLETED);
        assertThat(completed.runtimeVersion()).isEqualTo(1);
    }

    @Test
    void validatesRestoredLifecycleAndTimestamps() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        assertThatThrownBy(() -> WorkflowInstance.restore("i", definition, RESOURCE, "published", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInstance.restore("i", definition, RESOURCE, "draft", 1,
                WorkflowLifecycle.COMPLETED, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInstance.restore("i", definition, RESOURCE, "review", 0,
                WorkflowLifecycle.ACTIVE, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowInstance.restore("i", definition, RESOURCE, "draft", 0,
                WorkflowLifecycle.NOT_STARTED, NOW, NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active(definition).transition(definition, command("submit", 0), NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preventsRuntimeVersionOverflow() {
        WorkflowDefinition definition = WorkflowDefinitionTest.approval();
        WorkflowInstance instance = WorkflowInstance.restore("instance-1", definition, RESOURCE, "draft", Long.MAX_VALUE,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        assertError(() -> instance.transition(definition, command("submit", Long.MAX_VALUE), NOW), WorkflowErrorCode.OPERATION_FAILURE);
    }

    static WorkflowInstance active(WorkflowDefinition definition) {
        return WorkflowInstance.notStarted("instance-1", definition, RESOURCE, NOW).activate(definition, NOW);
    }

    static TransitionCommand command(String transitionId, long version) {
        return new TransitionCommand("instance-1", transitionId, ACTOR, version);
    }

    static void assertError(Runnable action, WorkflowErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WorkflowException.class,
                error -> assertThat(error.code()).isEqualTo(code));
    }
}
