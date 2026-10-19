package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;

import java.util.Objects;
import java.util.Optional;

/**
 * Stage-5-only application ACL evaluator. This deliberately has NO @Component or
 * database implementation: do not register it on production HTTP until trusted
 * PostgreSQL facts, SQL role/provenance and Stage 6 guarded transactions exist.
 *
 * Visibility rule (proposed for owner/security review): after active membership,
 * owner OR at least one current explicit grant can see existence; neither grants
 * permission for the requested operation. Even the owner needs an explicit grant.
 * Missing/inactive CREATE binding is HIDDEN pending approval of public error policy.
 */
public final class TenantResourceWorkflowAccessPolicy implements WorkflowAccessPolicy {
    private final WorkflowAclSnapshotReader reader;

    public TenantResourceWorkflowAccessPolicy(WorkflowAclSnapshotReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public WorkflowAccessDecision forCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                              ResourceReference resource) {
        Objects.requireNonNull(definition, "definition");
        WorkflowAccessDecision decision = evaluate(caller, resource, WorkflowOperation.CREATE);
        if (decision != WorkflowAccessDecision.ALLOWED) {
            return decision;
        }
        // Use the same actor/resource-scoped facts for both CREATE ACL and exact binding.
        WorkflowAclSnapshotReader.ResourceAclSnapshot facts = facts(caller, resource);
        if (facts == null) {
            return WorkflowAccessDecision.HIDDEN;
        }
        if (!facts.activeDefinitionBindings().contains(
                new WorkflowAclSnapshotReader.DefinitionBinding(
                        definition.definitionId(), definition.definitionVersion()))) {
            return WorkflowAccessDecision.HIDDEN;
        }
        return WorkflowAccessDecision.ALLOWED;
    }

    @Override
    public WorkflowAccessDecision forInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                              WorkflowOperation operation) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(operation, "operation");
        if (operation == WorkflowOperation.CREATE) {
            throw new IllegalArgumentException("CREATE requires creation authorization");
        }
        // The instance's resource reference must come from authoritative persistence,
        // not from a request-provided owner/tenant or client-supplied resource.
        return evaluate(caller, instance.resourceReference(), operation);
    }

    private WorkflowAccessDecision evaluate(AuthenticatedPrincipal caller, ResourceReference resource,
                                            WorkflowOperation operation) {
        WorkflowAclSnapshotReader.ResourceAclSnapshot facts = facts(caller, resource);
        if (facts == null || !facts.resourceActive() || !facts.memberActive()) {
            return WorkflowAccessDecision.HIDDEN;
        }
        if (!facts.owner() && facts.grants().isEmpty()) {
            return WorkflowAccessDecision.HIDDEN;
        }
        return facts.grants().contains(operation)
                ? WorkflowAccessDecision.ALLOWED : WorkflowAccessDecision.FORBIDDEN;
    }

    private WorkflowAclSnapshotReader.ResourceAclSnapshot facts(AuthenticatedPrincipal caller,
                                                                ResourceReference resource) {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(resource, "resource");
        // No trust is derived from a principal being constructible: trusted caller
        // provenance belongs to CurrentPrincipal.requireVerified() in the orchestrator.
        Optional<WorkflowAclSnapshotReader.ResourceAclSnapshot> found =
                Objects.requireNonNull(reader.read(resource, caller.subjectId()),
                        "Authoritative ACL lookup must not return null");
        if (found.isEmpty()) {
            return null;
        }
        WorkflowAclSnapshotReader.ResourceAclSnapshot record =
                Objects.requireNonNull(found.get(), "ACL record");
        if (!record.resource().equals(resource)
                || !record.verifiedActorId().equals(caller.subjectId())) {
            throw new IllegalStateException("Authoritative ACL target/caller mismatch");
        }
        return record;
    }
}
