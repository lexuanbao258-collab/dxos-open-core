package com.tricore.dxos.application.authorization;

import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.ResourceReference;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Stage 6 CREATE precheck only. Not a Spring bean and not wired to Workflow HTTP.
 * Identity is supplied exclusively by the existing verified CurrentPrincipal boundary.
 *
 * The source must implement classify using registry/membership/visibility/grants only,
 * without querying/cache-loading definition bindings, definitions, instances or history.
 * This preliminary check never authorizes a write: the future guarded persistence port
 * must independently recheck live ACL and exact binding under its own tenant guard.
 */
final class Stage6CreateGate {
    interface Source {
        Optional<Facts> classify(ResourceReference resource, String verifiedActorId);
        boolean hasExactActiveBinding(ResourceReference resource, String authoritativeTenantId,
                                      String definitionId, long version);
    }

    record Facts(ResourceReference resource, String verifiedActorId, String tenantId, boolean resourceActive,
                 boolean memberActive, boolean owner, Set<WorkflowOperation> grants) {
        Facts {
            Objects.requireNonNull(resource, "resource");
            if (verifiedActorId == null || verifiedActorId.isBlank()
                    || tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("Unbound actor in ACL facts");
            }
            grants = Set.copyOf(Objects.requireNonNull(grants, "grants"));
        }
    }

    private final CurrentPrincipal currentPrincipal;
    private final Source source;

    Stage6CreateGate(CurrentPrincipal currentPrincipal, Source source) {
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal, "currentPrincipal");
        this.source = Objects.requireNonNull(source, "source");
    }

    AuthenticatedPrincipal check(ResourceReference resource, String definitionId, long definitionVersion) {
        Objects.requireNonNull(resource, "resource");
        if (definitionId == null || definitionId.isBlank() || definitionVersion < 0) {
            throw new IllegalArgumentException("Invalid exact definition key");
        }
        // Keep Yến's Identity/JWT code untouched. A client-supplied principal is never accepted.
        AuthenticatedPrincipal caller = Objects.requireNonNull(
                currentPrincipal.requireVerified(), "verified principal");

        Optional<Facts> found;
        try {
            found = Objects.requireNonNull(source.classify(resource, caller.subjectId()), "ACL classification");
        } catch (RuntimeException failure) {
            throw denied(WorkflowAccessDecision.UNAVAILABLE);
        }
        if (found.isEmpty()) {
            throw denied(WorkflowAccessDecision.HIDDEN);
        }
        Facts facts = found.get();
        if (facts == null || !facts.resource().equals(resource)
                || !facts.verifiedActorId().equals(caller.subjectId())) {
            throw denied(WorkflowAccessDecision.UNAVAILABLE);
        }
        // Visibility MUST precede missing CREATE authority, even for owner.
        if (!facts.resourceActive() || !facts.memberActive()
                || (!facts.owner() && facts.grants().isEmpty())) {
            throw denied(WorkflowAccessDecision.HIDDEN);
        }
        if (!facts.grants().contains(WorkflowOperation.CREATE)
                || caller.authorities().stream().noneMatch(
                        authority -> authority.satisfies(WorkflowOperation.CREATE.requiredAuthority()))) {
            throw denied(WorkflowAccessDecision.FORBIDDEN);
        }
        // No binding/definition access was needed for any of the earlier denials.
        boolean active;
        try {
            active = source.hasExactActiveBinding(resource, facts.tenantId(), definitionId, definitionVersion);
        } catch (RuntimeException failure) {
            throw denied(WorkflowAccessDecision.UNAVAILABLE);
        }
        if (!active) {
            throw denied(WorkflowAccessDecision.HIDDEN);
        }
        // This is NOT final authorization; a future scoped port must recheck in its own tx.
        return caller;
    }

    private static WorkflowAccessException denied(WorkflowAccessDecision decision) {
        return new WorkflowAccessException(decision);
    }
}
