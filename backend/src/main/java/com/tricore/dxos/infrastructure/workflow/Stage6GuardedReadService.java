package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.workflow.TransitionRecord;
import com.tricore.dxos.core.workflow.WorkflowInstance;

import java.util.List;
import java.util.Objects;

/** Opt-in protected READ composition; deliberately not a Spring bean or HTTP adapter. */
public final class Stage6GuardedReadService {
    private final PostgresWorkflowPersistence persistence;
    private final CurrentPrincipal currentPrincipal;

    public Stage6GuardedReadService(PostgresWorkflowPersistence persistence, CurrentPrincipal currentPrincipal) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal, "currentPrincipal");
    }

    public WorkflowInstance loadInstance(String instanceId) {
        return persistence.forRead(currentPrincipal, instanceId).loadInstance(instanceId);
    }

    public List<TransitionRecord> loadHistory(String instanceId) {
        return persistence.forRead(currentPrincipal, instanceId).loadHistory(instanceId);
    }
}
