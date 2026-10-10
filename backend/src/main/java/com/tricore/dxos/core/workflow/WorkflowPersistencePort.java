package com.tricore.dxos.core.workflow;

import java.util.List;

/**
 * Workflow-owned persistence semantics; implemented later by Data/infrastructure.
 * All provider failures are translated to WorkflowException, without provider causes,
 * SQL or credentials. Structural invalid arguments use IllegalArgumentException/NullPointerException.
 * Snapshots and history preserve exact Instant values; adapters must not silently round them.
 *
 * For all three writes, validation/business/concurrency rejection makes no changes.
 * OPERATION_FAILURE requires confirmed no commit or confirmed rollback; it makes no changes.
 * Successful return requires confirmed durable commit. COMMIT_OUTCOME_UNKNOWN means the
 * adapter cannot establish whether the write committed, including a lost connection/response
 * after COMMIT: the whole write may already be durable, or may not have committed.
 * Atomicity still holds in every outcome; partial instance/history mutation is never permitted.
 * A provider exception alone does not establish rollback. Never blindly automatically retry;
 * reconcile unknown outcomes through authoritative reads before deciding on an explicit retry.
 * Separate instance/history reads do not share a snapshot, and an old/absent result while
 * a transaction is unresolved does not establish rollback or identify the winning writer.
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
     * Returns the snapshot only after confirmed durable commit. Never overwrites an existing instance:
     * duplicate id throws INSTANCE_ALREADY_EXISTS, even if the supplied content is identical.
     * Missing definition throws DEFINITION_NOT_FOUND; noninitial snapshot throws
     * TRANSITION_NOT_ALLOWED. Rejection or confirmed no commit leaves data unchanged.
     * Provider write errors use OPERATION_FAILURE only for confirmed no commit/rollback;
     * otherwise COMMIT_OUTCOME_UNKNOWN, which may mean creation already committed.
     */
    WorkflowInstance createInstance(WorkflowInstance initial);

    /**
     * Atomically compares the full expected snapshot with authoritative stored data and
     * commits exactly expected.activate(authoritativeDefinition, activated.updatedAt()).
     * Compare instance id, definition id/version, resource, state, runtime version,
     * lifecycle, createdAt and updatedAt by value, never object identity or version alone.
     * Success changes NOT_STARTED to ACTIVE, preserves version zero/binding/resource/createdAt,
     * permits equal timestamps and changes no history. Returns only after confirmed durable commit.
     *
     * Check missing id (INSTANCE_NOT_FOUND), then runtime version/lifecycle mismatch
     * (VERSION_CONFLICT), then completed state (INSTANCE_COMPLETED). Other source/proposal
     * mismatches or reactivation of an unchanged ACTIVE snapshot throw TRANSITION_NOT_ALLOWED.
     * Missing exact definition throws DEFINITION_NOT_FOUND. Concurrent activations have one
     * winner; replay of the original NOT_STARTED proposal conflicts despite version still zero.
     * Rejection or confirmed no commit/rollback leaves instance/history unchanged (OPERATION_FAILURE
     * for provider errors). Otherwise throw COMMIT_OUTCOME_UNKNOWN; activation may already be durable.
     * Both outcomes preserve atomicity and add no history; see the interface's reconciliation policy.
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
     * and append exactly one matching history record. Returns only after confirmed durable commit.
     *
     * The adapter must compare the previous snapshot's definition binding, resource, source state,
     * lifecycle and timestamps with persisted data, not trust a consumer-restored snapshot.
     * The proposal must preserve definition binding/resource/createdAt and agree with its history.
     * After origin checks, the adapter must load the authoritative exact definition and rederive
     * the transition using the record's transitionId/actor/occurredAt and the expected version.
     * Missing exact definition throws DEFINITION_NOT_FOUND.
     * Do not trust a consumer's same-ID/version definition. An absent authoritative transition
     * throws INVALID_TRANSITION; a differing target snapshot/history throws TRANSITION_NOT_ALLOWED.
     * A missing instance throws INSTANCE_NOT_FOUND; a stale version throws VERSION_CONFLICT,
     * including replay of an already committed proposal. Completed instances reject transitions.
     * Rejection or confirmed no commit/rollback leaves both unchanged (OPERATION_FAILURE for provider
     * errors). Otherwise throw COMMIT_OUTCOME_UNKNOWN: the instance and its matching history record
     * may already be committed together. Neither outcome permits a partial write or blind retry.
     * Reads are individually consistent, but two separate read calls are not a shared snapshot.
     *
     * This contract specifies atomicity; it does not implement or prove database transactions.
     * Definition provisioning and retry/idempotency policy are outside this runtime port.
     */
    TransitionResult commitTransition(TransitionResult transition);
}
