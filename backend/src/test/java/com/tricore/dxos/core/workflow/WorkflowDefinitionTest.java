package com.tricore.dxos.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {
    @Test
    void mapsTransitionByIdAndDerivesTerminalState() {
        WorkflowDefinition definition = approval();
        assertThat(definition.transition("submit").targetState()).isEqualTo("review");
        assertThat(definition.isTerminal("draft")).isFalse();
        assertThat(definition.isTerminal("published")).isTrue();
        assertThatThrownBy(() -> definition.transition("missing"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        error -> assertThat(error.code()).isEqualTo(WorkflowErrorCode.INVALID_TRANSITION));
    }

    @Test
    void rejectsMissingInitialStateAndUnknownEndpoints() {
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "absent", Set.of("a"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "a", Set.of("a"),
                Set.of(new WorkflowTransition("t", "a", "absent"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "a", Set.of("a"),
                Set.of(new WorkflowTransition("t", "absent", "a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateTransitionIdsEvenWithDifferentEndpoints() {
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "a", Set.of("a", "b"),
                Set.of(new WorkflowTransition("t", "a", "b"), new WorkflowTransition("t", "b", "a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidStructuralValues() {
        assertThatThrownBy(() -> new WorkflowDefinition(" ", 0, "a", Set.of("a"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition("d", -1, "a", Set.of("a"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "a", Set.of("a", " "), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition("d", 0, "a", null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new WorkflowTransition("", "a", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesCollections() {
        Set<String> states = new HashSet<>(Set.of("a", "b"));
        Set<WorkflowTransition> transitions = new HashSet<>(Set.of(new WorkflowTransition("t", "a", "b")));
        WorkflowDefinition definition = new WorkflowDefinition("d", 0, "a", states, transitions);
        states.clear();
        transitions.clear();
        assertThat(definition.states()).containsExactlyInAnyOrder("a", "b");
        assertThat(definition.transitions()).hasSize(1);
        assertThatThrownBy(() -> definition.states().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.transitions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    static WorkflowDefinition approval() {
        return new WorkflowDefinition("publication", 7, "draft", Set.of("draft", "review", "published"),
                Set.of(new WorkflowTransition("submit", "draft", "review"),
                        new WorkflowTransition("publish", "review", "published")));
    }

    static WorkflowDefinition delivery() {
        return new WorkflowDefinition("delivery", 2, "queued", Set.of("queued", "dispatched"),
                Set.of(new WorkflowTransition("dispatch", "queued", "dispatched")));
    }
}
