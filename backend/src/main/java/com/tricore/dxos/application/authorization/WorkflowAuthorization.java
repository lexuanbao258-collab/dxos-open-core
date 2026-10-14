package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Pure authorization collaborator; not a Spring bean, HTTP entry point or authentication proof.
 * Inputs require the verified-caller and authoritative-data provenance defined by the policy.
 * It cannot load protected data or invoke WorkflowRuntime. Production orchestration is deferred.
 */
public final class WorkflowAuthorization {
    private final WorkflowAccessPolicy policy;

    public WorkflowAuthorization(WorkflowAccessPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void authorizeCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                  ResourceReference resource) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(resource, "resource");
        authorize(caller, WorkflowOperation.CREATE, () -> policy.forCreation(caller, definition, resource));
    }

    public void authorizeInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                  WorkflowOperation operation) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(operation, "operation");
        if (operation == WorkflowOperation.CREATE) {
            throw new IllegalArgumentException("CREATE requires creation authorization");
        }
        authorize(caller, operation, () -> policy.forInstance(caller, instance, operation));
    }

    private void authorize(AuthenticatedPrincipal caller, WorkflowOperation operation,
                           Supplier<WorkflowAccessDecision> resourcePolicy) {
        WorkflowAccessDecision decision;
        try {
            decision = resourcePolicy.get();
        } catch (RuntimeException failure) {
            throw new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE);
        }
        if (decision == null) {
            decision = WorkflowAccessDecision.UNAVAILABLE;
        }
        // Visibility precedes authority denial so hidden resources never produce FORBIDDEN.
        if (decision != WorkflowAccessDecision.ALLOWED) {
            throw new WorkflowAccessException(decision);
        }
        if (caller.authorities().stream().noneMatch(authority -> authority.satisfies(operation.requiredAuthority()))) {
            throw new WorkflowAccessException(WorkflowAccessDecision.FORBIDDEN);
        }
    }
}
