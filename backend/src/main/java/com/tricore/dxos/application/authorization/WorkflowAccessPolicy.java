package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowInstance;

/**
 * Application-owned resource policy, not an authenticator or a Workflow persistence adapter.
 * The caller must originate from verified Identity; constructing a principal is not verification.
 * Implementations resolve authoritative resource identity, visibility, tenant membership and
 * action permission. References or token scopes alone do not establish resource access.
 * ALLOWED means visible and permitted; FORBIDDEN is only for visible resources. Missing and
 * hidden resources both use HIDDEN. An uncertain decision uses UNAVAILABLE, never ALLOWED.
 * No production implementation or allow-all fallback is supplied by this contract.
 */
public interface WorkflowAccessPolicy {
    /**
     * Definition must be loaded by exact ID/version from authoritative persistence, not HTTP input.
     * Resolve the requested resource reference authoritatively and permit its exact binding to this
     * definition only with tenant membership and binding permission; do not remap an alias silently.
     */
    WorkflowAccessDecision forCreation(AuthenticatedPrincipal caller, WorkflowDefinition definition,
                                       ResourceReference resource);

    /**
     * Instance must be an authoritative persisted snapshot; use its binding, never client ownership.
     * Resolve current visibility, tenant membership and resource permission for ACTIVATE, TRANSITION
     * or READ. READ covers both the snapshot and history. CREATE is not an instance operation.
     * shortcut: operation-level resource permissions only, add transition context before wiring
     * if the application's ACL distinguishes individual transitions.
     * A decision does not lock ACLs or make authorization and subsequent writes transactional.
     */
    WorkflowAccessDecision forInstance(AuthenticatedPrincipal caller, WorkflowInstance instance,
                                       WorkflowOperation operation);
}
