package com.tricore.dxos.core.workflow;

import java.util.List;

/**
 * Workflow-owned persistence semantics; implemented later by Data/infrastructure.
 * All provider failures are translated to WorkflowException, without provider causes.
 * Structural invalid arguments use IllegalArgumentException/NullPointerException.
 * Snapshots and history preserve exact Instant values; adapters must not silently round them.
 */
public interface WorkflowPersistencePort {
    /**
     * Returns the authoritative exact definition version; absence throws DEFINITION_NOT_FOUND.
     * A (definitionId, definitionVersion) key identifies immutable content. Provisioning is
     * owned by Data/infrastructure, outside this runtime port; there is no latest-version fallback.
     */
    WorkflowDefinition loadDefinition(String definitionId, long definitionVersion);

    /**
     * Atomically inserts a NOT_STARTED, version-zero snapshot matching the authoritative
     * definition's initial state, with equal createdAt/updatedAt and empty history.
     * Returns the durably stored snapshot. Never overwrites an existing instance:
     * duplicate id throws INSTANCE_ALREADY_EXISTS, even if the supplied content is identical.
     * Missing definition throws DEFINITION_NOT_FOUND; noninitial snapshot throws
     * TRANSITION_NOT_ALLOWED. A failure leaves all existing instances/history unchanged.
     */
    WorkflowInstance createInstance(WorkflowInstance initial);

    /**
     * Atomically compares the full expected snapshot with authoritative stored data and
     * commits exactly expected.activate(authoritativeDefinition, activated.updatedAt()).
     * Compare instance id, definition id/version, resource, state, runtime version,
     * lifecycle, createdAt and updatedAt by value, never object identity or version alone.
     * Success changes NOT_STARTED to ACTIVE, preserves version zero/binding/resource/createdAt,
     * permits equal timestamps and changes no history. Returns the durably stored snapshot.
     *
     * Check missing id (INSTANCE_NOT_FOUND), then runtime version/lifecycle mismatch
     * (VERSION_CONFLICT), then completed state (INSTANCE_COMPLETED). Other source/proposal
     * mismatches or reactivation of an unchanged ACTIVE snapshot throw TRANSITION_NOT_ALLOWED.
     * Missing exact definition throws DEFINITION_NOT_FOUND. Concurrent activations have one
     * winner; replay of the original NOT_STARTED proposal conflicts despite version still zero.
     * Provider failure throws OPERATION_FAILURE; any failure leaves instance/history unchanged.
     */
    WorkflowInstance commitActivation(WorkflowInstance expected, WorkflowInstance activated);

    /** Returns the authoritative immutable snapshot; missing id throws INSTANCE_NOT_FOUND. */
    WorkflowInstance loadInstance(String instanceId);

    /**
     * Returns an immutable per-instance history, ordered by resultingRuntimeVersion ascending.
     * Versions are unique and contiguous from 1; equal timestamps are permitted.
     * An existing instance with no transitions returns an empty list; missing id throws INSTANCE_NOT_FOUND.
     */
    List<TransitionRecord> loadHistory(String instanceId);

    /**
     * Commits a validated proposal as one logical operation: compare expectedVersion with the
     * persisted runtimeVersion, update state/lifecycle/timestamp, increment version exactly once,
     * and append exactly one matching history record. Returns the durably committed result.
     *
     * The adapter must compare the previous snapshot's definition binding, resource, source state,
     * lifecycle and timestamps with persisted data, not trust a consumer-restored snapshot.
     * The proposal must preserve definition binding/resource/createdAt and agree with its history.
     * A missing instance throws INSTANCE_NOT_FOUND; a stale version throws VERSION_CONFLICT,
     * including replay of an already committed proposal. Completed instances reject transitions.
     * Any failure leaves both instance and history unchanged; provider failure maps to OPERATION_FAILURE.
     * Reads are individually consistent, but two separate read calls are not a shared snapshot.
     *
     * This contract specifies atomicity; it does not implement or prove database transactions.
     * Definition provisioning and retry/idempotency policy are outside this runtime port.
     */
    TransitionResult commitTransition(TransitionResult transition);
}
