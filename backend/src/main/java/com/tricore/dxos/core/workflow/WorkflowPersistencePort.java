package com.tricore.dxos.core.workflow;

import java.util.List;

/**
 * Workflow-owned persistence semantics; implemented later by Data/infrastructure.
 * All provider failures are translated to WorkflowException, without provider causes.
 */
public interface WorkflowPersistencePort {
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
     * Instance creation/activation, definition storage and retry/idempotency policy are deferred.
     */
    TransitionResult commitTransition(TransitionResult transition);
}
