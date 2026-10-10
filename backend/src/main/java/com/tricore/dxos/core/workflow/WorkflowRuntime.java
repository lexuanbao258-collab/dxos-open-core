package com.tricore.dxos.core.workflow;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Provider-independent orchestration over authoritative persistence and the existing domain.
 * Returns writes only after persistence confirms them; propagates failures without retry or
 * automatic reconciliation. The injected clock supplies exact Instants without rounding;
 * its precision must be configured upstream to match the adapter's accepted representation.
 */
public final class WorkflowRuntime {
    private final WorkflowPersistencePort persistence;
    private final Clock clock;

    public WorkflowRuntime(WorkflowPersistencePort persistence, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkflowInstance createInstance(String instanceId, String definitionId, long definitionVersion,
                                           ResourceReference resourceReference) {
        WorkflowValues.text(instanceId, "instanceId");
        WorkflowValues.text(definitionId, "definitionId");
        WorkflowValues.nonNegative(definitionVersion, "definitionVersion");
        Objects.requireNonNull(resourceReference, "resourceReference");
        WorkflowDefinition definition = persistence.loadDefinition(definitionId, definitionVersion);
        WorkflowInstance initial = WorkflowInstance.notStarted(instanceId, definition, resourceReference, clock.instant());
        return persistence.createInstance(initial);
    }

    public WorkflowInstance activateInstance(String instanceId) {
        WorkflowInstance expected = loadInstance(instanceId);
        WorkflowDefinition definition = persistence.loadDefinition(expected.definitionId(), expected.definitionVersion());
        WorkflowInstance activated = expected.activate(definition, clock.instant());
        return persistence.commitActivation(expected, activated);
    }

    /** The command's actor must come from a trusted upstream boundary; this runtime performs no authentication. */
    public TransitionResult executeTransition(TransitionCommand command) {
        Objects.requireNonNull(command, "command");
        WorkflowInstance expected = loadInstance(command.instanceId());
        WorkflowDefinition definition = persistence.loadDefinition(expected.definitionId(), expected.definitionVersion());
        TransitionResult proposal = expected.transition(definition, command, clock.instant());
        return persistence.commitTransition(proposal);
    }

    public WorkflowInstance loadInstance(String instanceId) {
        WorkflowValues.text(instanceId, "instanceId");
        return persistence.loadInstance(instanceId);
    }

    /** An authoritative read, individually consistent with no shared snapshot across separate calls. */
    public List<TransitionRecord> loadHistory(String instanceId) {
        WorkflowValues.text(instanceId, "instanceId");
        return persistence.loadHistory(instanceId);
    }
}
