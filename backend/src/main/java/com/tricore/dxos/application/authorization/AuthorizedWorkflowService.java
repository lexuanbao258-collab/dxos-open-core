package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.TransitionRecord;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowRuntime;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Verified-identity orchestration; deliberately unregistered until production ACL and guarded
 * persistence are ready. Persistence lookups are private authorization inputs, never returned
 * on denial. No caller identity or actor is accepted as an operation argument.
 * shortcut: authorization and execution are separate, coordinate ACL revocation before HTTP wiring.
 */
public final class AuthorizedWorkflowService {
    private final CurrentPrincipal currentPrincipal;
    private final WorkflowAuthorization authorization;
    private final WorkflowPersistencePort persistence;
    private final WorkflowRuntime runtime;

    public AuthorizedWorkflowService(CurrentPrincipal currentPrincipal, WorkflowAuthorization authorization,
                                     WorkflowPersistencePort persistence, WorkflowRuntime runtime) {
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal, "currentPrincipal");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public WorkflowInstance createInstance(String instanceId, String definitionId, long definitionVersion,
                                           ResourceReference resource) {
        AuthenticatedPrincipal caller = requireCaller();
        WorkflowDefinition definition = lookup(() -> persistence.loadDefinition(definitionId, definitionVersion),
                WorkflowErrorCode.DEFINITION_NOT_FOUND);
        authorization.authorizeCreation(caller, definition, resource);
        return runtime.createInstance(instanceId, definitionId, definitionVersion, resource);
    }

    public WorkflowInstance activateInstance(String instanceId) {
        authorizeInstance(instanceId, WorkflowOperation.ACTIVATE);
        return runtime.activateInstance(instanceId);
    }

    public TransitionResult executeTransition(String instanceId, String transitionId, long expectedVersion) {
        AuthenticatedPrincipal caller = authorizeInstance(instanceId, WorkflowOperation.TRANSITION);
        return runtime.executeTransition(new TransitionCommand(instanceId, transitionId,
                new ActorReference(caller.subjectId()), expectedVersion));
    }

    public WorkflowInstance loadInstance(String instanceId) {
        authorizeInstance(instanceId, WorkflowOperation.READ);
        return runtime.loadInstance(instanceId);
    }

    public List<TransitionRecord> loadHistory(String instanceId) {
        authorizeInstance(instanceId, WorkflowOperation.READ);
        return runtime.loadHistory(instanceId);
    }

    private AuthenticatedPrincipal requireCaller() {
        return Objects.requireNonNull(currentPrincipal.requireVerified(), "verified caller");
    }

    private AuthenticatedPrincipal authorizeInstance(String instanceId, WorkflowOperation operation) {
        AuthenticatedPrincipal caller = requireCaller();
        WorkflowInstance instance = lookup(() -> persistence.loadInstance(instanceId),
                WorkflowErrorCode.INSTANCE_NOT_FOUND);
        authorization.authorizeInstance(caller, instance, operation);
        return caller;
    }

    private static <T> T lookup(Supplier<T> load, WorkflowErrorCode missing) {
        try {
            return Objects.requireNonNull(load.get());
        } catch (WorkflowException failure) {
            throw new WorkflowAccessException(failure.code() == missing
                    ? WorkflowAccessDecision.HIDDEN : WorkflowAccessDecision.UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE);
        }
    }
}
