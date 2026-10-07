package com.tricore.dxos.core.workflow;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable definition. A terminal state has no outgoing transition. */
public record WorkflowDefinition(String definitionId, long definitionVersion, String initialState,
                                 Set<String> states, Set<WorkflowTransition> transitions) {
    public WorkflowDefinition {
        definitionId = WorkflowValues.text(definitionId, "definitionId");
        WorkflowValues.nonNegative(definitionVersion, "definitionVersion");
        initialState = WorkflowValues.text(initialState, "initialState");
        states = Set.copyOf(Objects.requireNonNull(states, "states"));
        transitions = Set.copyOf(Objects.requireNonNull(transitions, "transitions"));
        states.forEach(state -> WorkflowValues.text(state, "state"));
        if (!states.contains(initialState)) {
            throw new IllegalArgumentException("initialState must belong to states");
        }
        Set<String> ids = new HashSet<>();
        for (WorkflowTransition transition : transitions) {
            if (!ids.add(transition.transitionId())) {
                throw new IllegalArgumentException("transitionId must be unique");
            }
            if (!states.contains(transition.sourceState()) || !states.contains(transition.targetState())) {
                throw new IllegalArgumentException("transition endpoints must belong to states");
            }
        }
    }

    public WorkflowTransition transition(String transitionId) {
        WorkflowValues.text(transitionId, "transitionId");
        return transitions.stream().filter(t -> t.transitionId().equals(transitionId)).findFirst()
                .orElseThrow(() -> new WorkflowException(WorkflowErrorCode.INVALID_TRANSITION));
    }

    public boolean isTerminal(String state) {
        if (!states.contains(state)) {
            throw new IllegalArgumentException("state must belong to states");
        }
        return transitions.stream().noneMatch(t -> t.sourceState().equals(state));
    }
}
