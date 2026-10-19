package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.workflow.ResourceReference;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Stage 5 authoritative ACL facts, not a client DTO or a JWT verification mechanism.
 *
 * A future PostgreSQL adapter must derive these facts from the global resource registry,
 * active tenant membership, current explicit grants and exact definition bindings. Every
 * answer is scoped to ONE resource and ONE verified canonical actor. A null Optional,
 * partial facts or a lookup exception are unavailable, not permission to continue.
 *
 * This is only a contract: NO production adapter or Spring bean exists. Even a complete
 * Stage 5 implementation is an application precheck, NOT a transaction/revoke guard.
 */
public interface WorkflowAclSnapshotReader {
    /** Optional.empty() means the authoritative resource is missing; never infer tenant from input. */
    Optional<ResourceAclSnapshot> read(ResourceReference resource, String verifiedActorId);

    record DefinitionBinding(String definitionId, long definitionVersion) {
        public DefinitionBinding {
            if (definitionId == null || definitionId.isBlank() || definitionVersion < 0) {
                throw new IllegalArgumentException("Invalid exact workflow definition binding");
            }
        }
    }

    /**
     * Must be built ONLY from authoritative, actor-scoped storage. Grants contain *positive,
     * currently valid* resource ACLs, not requested scopes, roles, owner flags or denials.
     * The resource/actor echo is verified to reject accidental cross-key cache mixing.
     */
    record ResourceAclSnapshot(
            ResourceReference resource,
            String verifiedActorId,
            String tenantId,
            boolean resourceActive,
            boolean memberActive,
            boolean owner,
            Set<WorkflowOperation> grants,
            Set<DefinitionBinding> activeDefinitionBindings
    ) {
        public ResourceAclSnapshot {
            Objects.requireNonNull(resource, "resource");
            if (verifiedActorId == null || verifiedActorId.isBlank()
                    || tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("Incomplete authoritative ACL identity");
            }
            grants = Set.copyOf(Objects.requireNonNull(grants, "grants"));
            activeDefinitionBindings = Set.copyOf(
                    Objects.requireNonNull(activeDefinitionBindings, "activeDefinitionBindings"));
        }
    }
}
