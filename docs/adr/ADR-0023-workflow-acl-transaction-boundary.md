# ADR-0023: Authorization at the durable persistence boundary for Workflow

**Status:** TRANSACTION BOUNDARY DECISION RECORDED — Option A selected for design planning; **not physical PostgreSQL/SQL-role/production implementation approval**  
**Date:** 2026-10-18  
**Issue:** [#23 Gateway ACL](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23)  
**Related:** [#22 Workflow HTTP](https://github.com/lexuanbao258-collab/dxos-open-core/issues/22), [#24 Identity / PR #33](https://github.com/lexuanbao258-collab/dxos-open-core/pull/33), [Draft PR #36](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36)  
**Review baseline:** `main` at `2115de355e8df4f0567e8e8421dae8465da9cea8`; draft V5 design v2 at `6e8ca01d2a264da6c5bdbd846b083bdc596b68ef`.  
**Issue owner:** Bảo. **Decision authority:** this document records a technical architecture recommendation for Issue #23; it must not be mistaken for a separate human DBA/security sign-off or approval of SQL deployment. Independent PostgreSQL/concurrency sign-off is still required, and Yến re-reviews the Identity integration **after implementation**.

> **DESIGN ONLY.** Do not implement this ADR as a V5 Flyway migration or change privileges on staging/shared Supabase. Do not edit V1–V4, register Workflow HTTP, merge PR #36, or represent this as passing PG17/JWKS tests. This ADR addresses the *durable port contract* contradiction found in the [v2 independent review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36#issuecomment-6249635681), not all remaining DB/Identity blockers.

## 1. Problem and non-negotiable invariants

### Source facts

1. `WorkflowPersistencePort.createInstance`, `commitActivation` and `commitTransition` contractually **return success only after confirmed durable COMMIT**. `OPERATION_FAILURE` requires confirmed no-commit/rollback; an ambiguous write COMMIT or failed rollback must yield `COMMIT_OUTCOME_UNKNOWN` and **must not be blindly retried**. Transition snapshot/history write is atomic. See [port source](../../backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowPersistencePort.java).
2. `WorkflowRuntime` performs multiple port calls per operation (CREATE: collision probe, exact definition, then create; ACTIVATE: load, definition, then commit activation; TRANSITION: load, definition, then commit transition). It has *no* outer transaction guarantee. See [Runtime source](../../backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java).
3. `PostgresWorkflowPersistence` currently opens/commits/closes **its own JDBC connection per method**; writes use `READ COMMITTED` and a single JDBC commit; unguarded reads use `REPEATABLE READ`. The adapter's generic transaction catch erases non-Workflow authorization failures into `OPERATION_FAILURE`. See [JDBC source](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistence.java).
4. V3 Workflow instances store `instance_id` and `(resource_type, resource_id)`, not `tenant_id`. No existing production tenant/ACL source guarantees visibility. [V3 migration](../../backend/src/main/resources/db/migration/V3__create_workflow_persistence.sql).
5. Yến approved keeping **public Core APIs and Workflow port semantics** provider-neutral (D1–D5); that approval does **not** approve a change to the durable-return semantics. [Yến's comment](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6249235587).

### Required properties

- A verified actor/authority originates **only** at `CurrentPrincipal.requireVerified()` (real JWT/JWKS) and is carried via a private, target+operation-bound application scope. No unverified `AuthenticatedPrincipal` constructor, client-supplied actor, tenant, or `instanceId` as authorization proof.
- **Before and during each durable write**, resolve authoritative tenant/resource from persisted state; lock that tenant's permanent guard row `FOR SHARE`; *after acquiring the lock*, check current membership, resource visibility/active state, operation ACL, exact token authority, and CREATE's exact active definition binding. Perform **Workflow instance/history mutation and durable COMMIT on that same JDBC connection**, keeping the guard until commit/rollback.
- Every policy/ACL/membership/owner/binding/tombstone mutator locks **that same** guard `FOR UPDATE` before other protected rows, updates the revision and commits. Enforce this by actual SQL roles/functions, not just Java convention.
- READ and HISTORY must be authorized **before protected materialization** with guard acquired inside their own coherent transaction. Revocation after a preliminary read but before a later write must not allow the later write.
- Preserve exact `WorkflowRuntime` domain results, snapshot/version checks, transition actor and history atomicity; preserve public port write return contract and sanitized errors. Never expose hidden resource existence or intermediate snapshots in API responses.
- SQL role isolation and DB-verifiable principal provenance described by [v2](../contracts/issue-23-v5-ddl-guarded-tx-review-v2.md) **remain separate unresolved review gates**. This ADR does not claim compromised runtime SQL credentials are safe.

## 2. Considered options

| Option | Strategy | Evaluation |
| --- | --- | --- |
| **A — Proposed choice** | Preserve `WorkflowRuntime` and public `WorkflowPersistencePort`. Wrap each *individual protected port operation* with target/operation-bound authorization; in **each write port method**, acquire guard + final ACL check + instance/history write + durable commit on **one physical JDBC connection**. Preliminary port reads are independently guarded; no outer transaction spans the whole Runtime call. | **Preserves port return semantics.** Changes internal JDBC adapter/security orchestration and SQL privilege APIs. Requires careful masking of pre-write errors and lock-race tests. |
| **B — New authorized application orchestrator, no Runtime for production write flows** | Reimplement the Core orchestration flow as an authorized coordinator calling lower-level domain methods/SQL, commit at the coordinator; do not pretend a deferred-commit port implements the Core port. | Can make a whole operation atomic but duplicates Core sequencing/error precedence; costly regression and invariant-equivalence proofs. Separate review needed. |
| **C — Change Core-port transactional semantics** | Change public port so write methods can return before durable commit, add a higher commit owner and change semantics across callers. | **Not acceptable under D1 and existing port contract** absent concrete failing contract and explicit Core architecture re-approval. |
| **Rejected shortcut** | An outer Spring `@Transactional`, standalone Java `WorkflowAuthorization` check, or v2 `Function<WorkflowRuntime,T>` callback wrapping an inner-committing persistence adapter. | Still permits revoke-to-write TOCTOU; callback risks authorizing A and acting on B; deferred commit contradicts the Core write port contract. |

**Selected direction: Option A for transaction-boundary design and implementation planning, conditional on DB/security sign-off and executable tests.** This is not a claim that a complete request/`WorkflowRuntime` call is one transaction. The **final write port call** is the atomic authorization + durability boundary; preliminary reads are separately guarded and may become stale. Existing final write methods already revalidate authoritative snapshots/proposals and must also revalidate **current** ACL within their transaction.

## 3. Concrete application and persistence contracts

### 3.1 Public Gateway surface (operation-specific, no user-controlled identity)

Design-only API:

```java
interface AuthorizedWorkflowService {
    WorkflowInstance create(CreateWorkflowInput input);     // no client instanceId
    WorkflowInstance activate(String instanceId);
    TransitionResult transition(String instanceId, String transitionId, long expectedVersion);
    WorkflowInstance snapshot(String instanceId);
    List<TransitionRecord> history(String instanceId);
}
```

- One trusted `CurrentPrincipal.requireVerified()` call establishes verified actor + exact authorities per request. No arbitrary caller `Function<WorkflowRuntime, T>`, JDBC connection or `WorkflowPersistencePort` is exposed to controller/plugin code.
- Application privately constructs an **immutable target+operation scope**, with exact instance ID, resource reference, definition/transition context where applicable, verified actor, and required authority. Create new instance IDs using the existing PR #36 generator *server side*. Each scoped persistence method enforces target, action, actor and lifecycle, and denies use after request closure.
- Scope restricts *which method/row* may be invoked; **it does not itself grant ACL**. Trusted DB SQL APIs must also enforce authoritative ACL and caller-proof constraints independently when deployed. SQL-A/SQL-B remain blockers.

### 3.2 Method-by-method mapping; each write owns its COMMIT

| HTTP operation | Runtime/port sequence | Final authorization and durability boundary |
| --- | --- | --- |
| CREATE | `loadInstance(newGeneratedId)` collision probe; `loadDefinition(exactId/version)`; `createInstance(initial)` | `createInstance` begins its own guarded **write** transaction; resolve requested resource to authoritative tenant, take guard `FOR SHARE`, recheck membership + CREATE grant + exact definition binding and authority, validate immutable definition and new ID, INSERT snapshot, **COMMIT before returning**. Collision/missing/hidden cases must never expose existing IDs to HTTP. |
| ACTIVATE | `loadInstance(target)`; `loadDefinition(persisted exact)`; `commitActivation(expected, activated)` | Preliminary `loadInstance` is a guarded read permitted by **ACTIVATE** scope (does not imply independent READ authority). `commitActivation` re-resolves target/binding, takes guard, repeats current ACL/authority, locks instance `FOR UPDATE`, checks full expected snapshot/lifecycle, persists and commits before return. |
| TRANSITION | `loadInstance(target)`; `loadDefinition(persisted exact)`; `commitTransition(proposal)` | Preliminary authorized internal read allowed by TRANSITION scope. `commitTransition` locks guard, revalidates ACL/actor/target, then version/definition/transition, commits state+one matching history row **atomically**. Actor must equal verified canonical actor, not request body. |
| SNAPSHOT | `loadInstance(target)` | One separately committed/ended **guarded read transaction** under READ scope; authorize after guard lock then materialize; no mutation. |
| HISTORY | `loadHistory(target)` | One separately ended **guarded read transaction** under READ scope; existence/permission and history materialization use same guarded connection/snapshot; no unauthorized history. |

**Exact port method checks:** `loadDefinition` is limited to the bound definition/operation scope, not an arbitrary definition oracle. `loadInstance` may only resolve the scoped target and must not leak snapshots to an unauthorized external caller; CREATE's collision probe is an *internal generated-ID check*, sanitized on unexpected match. `commitActivation`/`commitTransition` reject any changed target/resource/actor/definition, and final persistence re-derives canonical transitions exactly as V3/Core requires. `loadHistory` is permitted only under externally authorized READ/HISTORY scope. No arbitrary nested operation or method mixing.

**Mapping to SQL remains a separate required artifact:** The v2 list of five public SQL operations does **not** yet prove coverage for the six individual port methods. For option A, each port method needs an operation-scoped, narrowly granted SQL entrypoint (or a precise mapping to vetted helper functions) that checks target+actor+scope; write functions may not commit independently of their enclosing JDBC method. Any translation from port method to SQL function must be reviewed with exact parameters, error/result shape and execution privilege. Do not implement a free-form `get_instance(actorId, instanceId)` helper callable with forged actor/authority.

### 3.3 Write transaction: one guarded port invocation, one physical JDBC connection

Conceptual steps for `createInstance`, `commitActivation` or `commitTransition`:

1. Read validated private operation scope/verified caller. Acquire a **new checked JDBC connection dedicated to this port method** (may be different from earlier Runtime read calls). Ensure no implicit outer Spring transaction. Choose `READ COMMITTED`, `autoCommit=false`, explicit finite lock/statement deadlines, keep existing `synchronous_commit=on` plus `fsync/full_page_writes` checks.
2. Obtain only a **minimal private target->resource->tenant projection** needed to find the candidate guard. No protected object/history is returned to the caller. Missing/changed resource/tenant resolution fails closed; client input cannot supply authoritative tenant.
3. Lock that tenant's permanent guard `FOR SHARE`. **After** acquiring it, re-read *current* authoritative registry/instance binding, active membership, visibility, exact resource ACL, verified Workflow authority, and for CREATE exact active definition/version binding. If a changed target points to a different tenant, **fail closed and restart nothing automatically**; never continue on the old guard.
4. Check bound scope/actor/operation again and only then lock the target instance `FOR UPDATE` (CREATE checks uniqueness and insert atomically). Revalidate expected version, full persisted snapshot, definition and transition semantics; write complete Workflow state/history in this transaction. Global lock order: tenant guard -> resource -> instance -> history (including implicit FK/trigger locks).
5. Attempt **exactly one COMMIT** from this same port method; return successful Core result **only when COMMIT success is confirmed**. Do not let a nested SQL function or transaction-scoped helper commit/close the connection. Hold guard until COMMIT/ROLLBACK. Only then return the detached result to `WorkflowRuntime`.
6. On *pre-commit* failure: attempt rollback and require confirmed rollback/no-commit before returning typed HIDDEN/FORBIDDEN/UNAVAILABLE or recognized Core error. A write COMMIT attempt with unknown outcome, or failed write rollback, raises `COMMIT_OUTCOME_UNKNOWN` even if an earlier check suggested access denial. **No automatic retry, especially after uncertain COMMIT.**

**Concurrency semantics:** If policy revoke holds `FOR UPDATE` and commits first, writer waits for guard, checks revised ACL and writes nothing. If writer acquires `FOR SHARE` first, revoke waits until writer commits/rolls back. If revoke completes **between preliminary Runtime read and the final guarded write**, the final write **must fail**. The full Runtime operation is *not* atomic across those preliminary reads, so they must never be returned unguarded or used as sufficient authorization. PostgreSQL 17 documents the conflicting lock modes and transaction release behavior: https://www.postgresql.org/docs/17/explicit-locking.html.

### 3.4 Guarded read and error-before-write paths

- Every protected `loadInstance` / `loadHistory` opens its **own READ COMMITTED transaction**, uses the same guard `FOR SHARE` and post-lock ACL check, and only then materializes requested snapshot/history. **Do not use the current unguarded `REPEATABLE READ` path** as a secure substitute. In particular, a transaction executing `SELECT ... FOR SHARE` cannot be a PostgreSQL `READ ONLY` transaction; configure guarded read with write-capable JDBC transaction settings while *application/DB function privileges and the scoped API disallow writes*. Review and test this setting explicitly.
- Preliminary ACTIVATE/TRANSITION `loadInstance` is an internal step authorized by its own operation scope, **not** the public SNAPSHOT endpoint and not an implicit `workflow:read` grant. Bound definition loads cannot become a hidden data oracle.
- **Error-before-final-write leakage:** A preliminary domain validation may throw (e.g. INVALID_TRANSITION, VERSION_CONFLICT) *before* the final write port method checks the current ACL. If access may have been revoked meanwhile, returning that domain detail may reveal protected state. The public application failure bridge must perform a **fresh guarded authorization/visibility check** for the same target/operation *before releasing a state-dependent business error*; if no longer visible/allowed, return a safe 404/403/UNAVAILABLE instead. A fresh check is not a license to retry the business operation; it only classifies a known no-write error. Make timing/existence side-channel tests explicit. If uncertain prior write COMMIT exists, `COMMIT_OUTCOME_UNKNOWN` always dominates and must not be hidden by that check.
- READ/HISTORY results are only released after successful authorization and transaction completion; snapshots and history are not guaranteed to share a cross-request snapshot (consistent with existing port contract).

### 3.5 JDBC catch bridge / public HTTP classification

The current `PostgresWorkflowPersistence.transaction()` retains only `WorkflowException` and maps unrelated Java runtime failures to `OPERATION_FAILURE`. **Do not pass bare `WorkflowAccessException` through that catch.** Have the guarded method carry an internal tagged, sanitized policy outcome; after confirmed rollback, translate it to HIDDEN (404), FORBIDDEN (403), or UNAVAILABLE (sanitized non-success, candidate 503). Missing/hidden/other-tenant/tombstoned must have same externally safe 404 body; visible but missing exact operation grant/authority gets 403. No SQL text, actor/tenant ID, token, or internal exception leaked.

Maintain existing Core error precedence for authorized callers *as proven by equivalence tests*, never let a domain error leak a hidden target. `COMMIT_OUTCOME_UNKNOWN` supersedes other errors when write commitment is uncertain; HTTP uncertain-write mapping/idempotency and reconciliation stay a separate explicit approval gate.

### 3.6 Security/concurrency refinements from independent architectural review

These are **mandatory design invariants**, not assertions of passed tests:

1. **One verified HTTP caller, multiple independent JDBC transactions.** A request may perform an internal collision probe, definition lookup, a preliminary instance read and then a final guarded write on different JDBC connections. The application's private request scope must remain immutable across those calls. **Each physical transaction** must independently establish and validate its operation/target/caller provenance and current ACL. If v2's transaction-bound cryptographic SQL proof is retained, mint/verify a **fresh proof bound to each actual transaction**; reusing a preliminary read's proof in the final write is prohibited. Do not derive trust from an SQL actor argument or PostgreSQL GUC. Define what occurs if a proof expires between calls: deny without retrying an unknown write.
2. **Guard lock lifetime cannot be reduced by savepoints.** PostgreSQL [releases locks taken after a savepoint on rollback to that savepoint, including in PL/pgSQL exception blocks](https://www.postgresql.org/docs/17/explicit-locking.html). Therefore a trusted function must not acquire tenant guard `FOR SHARE`, catch/rollback to a prior savepoint, then continue a protected read or write on the assumption it still holds the guard. Require no such flow, or a provable guard reacquisition and complete policy recheck. Test a function exception/savepoint scenario and both revoke interleavings.
3. **Guarded reads must use a lock-capable transaction.** Configure a distinct guarded `READ COMMITTED` path: `setReadOnly(false)` (the PostgreSQL `READ ONLY` transaction mode rejects locking `SELECT ... FOR SHARE`), `autoCommit=false`, and **function/operation scope restricts writes**. Existing unguarded `transaction(false)` still uses `setReadOnly(true)` with `REPEATABLE READ` and cannot substitute. Keep the single materialization within its guarded lifetime. Do not assume the `FOR SHARE` tenant guard serializes *other Workflow writes*: it conflicts with policy mutator `FOR UPDATE`, not another Workflow `FOR SHARE`. Preserve complete-state/history invariants through atomic write transactions and per-result coherent queries.
4. **No trusted internal read as independent public READ entitlement.** An ACTIVATE or TRANSITION scope may internally fetch **only its bound target** and pinned definition to compute a proposal, without granting the caller a separate snapshot/history endpoint. The result of that internal read cannot escape externally. A business error raised before the final write must pass the **fresh, operation-specific guarded error-classification check** described in `3.4. Keep that classification narrowly scoped and protected from source/SQL error leakage.
5. **Production dependency graph and SQL-function mapping.** The current production controller calls plain `WorkflowRuntime` and the existing JDBC port is also available as a Spring component. Before wiring HTTP, prove there is **no alternate unguarded bean/endpoint/job path** into protected instances/history. Provide for each port method a concrete SQL function signature, the approved role, proof target+operation, read/write authority, transaction owner, error/result mapping, and no scope escape. Keep legacy infrastructure smoke/provisioning separately gated and do not broadly revoke role rights before replacing their JDBC path.
6. **No inference of database release approval.** Core contract preservation is supported by the observed port/runtime source and Option A's separate final-commit boundary, **conditional on implementation matching this ADR**. SQL credential threat resistance, ACL guard immutability, the V5 migration gate/manifest, physical role GRANT/REVOKE, real PostgreSQL races and JWT/JWKS tests remain blocking. Do not treat this technical record as PostgreSQL conformance evidence or independent human reviewer approval.

## 4. Dependencies, hard blockers and tests before implementation approval

Option A intentionally **does not** solve every v2 blocker. Explicit gates:

1. **SQL-A/SQL-B — DB roles and caller provenance:** runtime must not directly read/write protected tables. Choose/review exact `SECURITY DEFINER` function API and principal attestation/key custody (or an explicitly narrower accepted threat model); verify proof and rights on **each new port transaction**. Credential-only attackers must not impersonate another actor if that stronger guarantee is claimed. Admin/provisioning paths must not bypass guards. See [v2 proposal](../contracts/issue-23-v5-ddl-guarded-tx-review-v2.md).
2. **DB-A — DDL and V3 migration:** immutable global resource->tenant registry, permanent guard, one-way tombstones, membership and binding FKs/UNIQUE/collation, V3 unmapped instance fail-closed; forward-only reviewed V5 approval path and compatibility with pinned V3/V4 `WorkflowApprovedMigration` / event-trigger manifest. **Never update V1–V4.**
3. **Security/product acceptance:** exact resource visibility policy (visible denied 403 vs hidden 404), CREATE binding error visibility, server-owned generated IDs, internal collision/lost response, and unknown-write HTTP/no-auto-retry policy. Yến must review actual Identity integration when wired.
4. **Existing Workflow writes:** source-equivalence tests for create/activate/transition error precedence, immutable timestamps, exact definition, transition re-derivation, state/history atomicity, completed lifecycle, duplicate version conflict, failed COMMIT and rollback; tests must prove success returns only after durable COMMIT.
5. **Concurrency:** two **independent physical PostgreSQL 17 JDBC connections**, bounded latches/lock barriers, revoke-first and write-first for ACL, tenant membership, owner/visibility, definition binding (CREATE only) and tombstones; **also revoke after pre-read and before final write**. Assert actual instance/version/history after fresh committed reads. No timing-only sleeps and no actual staging/shared Supabase.
6. **Negative security tests:** authorize A then try B; READ tries write, ACTIVATE tries TRANSITION, actor/definition swap, unverified caller, SQL-only impersonation/direct SELECT/DML, pre-write error leak after revoke, independent protected history/snapshot checks, key/privilege bypass, generated-ID collision; genuine OIDC/JWKS + all five routes 401/403/404 + Request regression **before** production route enablement.

### Minimum implementation ordering after independent ADR sign-off

1. Record security/product decisions `SQL-A/SQL-B/DB-A` and actual per-port SQL function map + privilege matrix. No migration while those are open.
2. Implement a new **guarded persistence adapter** (or carefully isolated internal refactor) that preserves *each write port method's commit ownership*, with typed denial bridge. Do not register `WorkflowController` yet.
3. Review additive V5 migration separately and run only on disposable PostgreSQL 17, prove SQL grants/mutation guard and two-connection lock races.
4. Integrate verified Identity and typed `AuthorizedWorkflowService`, run real JWT/JWKS 401/403/404 and Request/full backend regressions. Ask Yến to re-review the real boundary, then decide PR merge/security gate. 

## 5. Decision / sign-off request

**Recorded architectural choice:** **Option A** — *transaction and durable COMMIT remain owned by the **final guarded write persistence method**; no deferred-commit port, and no whole-`WorkflowRuntime` transaction illusion.* Protected reads are guarded separately and final write re-authorizes after tenant lock. This preserves `WorkflowPersistencePort` observable durability semantics, but requires a new precisely scoped adapter, secure SQL functions/role design, safe pre-write error mapping and concurrency tests.

**Status: OPTION A CHOSEN FOR TRANSACTION-BOUNDARY PLANNING; INDEPENDENT DB/SECURITY SIGN-OFF AND IMPLEMENTATION AUTHORIZATION REMAIN PENDING.** Please review explicitly:
- **TX-ADR-1:** Does a one-write-method transaction satisfy #23's revoke/TOCTOU and durable port requirements under all provided counterexamples?
- **TX-ADR-2:** Is the pre-write guarded read + final reauthorization + error-before-write masking contract sufficient? Supply one concrete failing scenario otherwise.
- **TX-ADR-3:** Can the SQL-A/B caller-proof and restricted function API be enforced independently for *every port method* without compromising Core semantics, including admin/provisioners?
- **TX-ADR-4:** Are error ordering, unknown-commit precedence and guarded READ/HISTORY ready to be implemented and tested as stated?
- **TX-ADR-5:** Does the pinned V3/V4 migration/event-trigger approval path permit a forward-only isolated V5 rollout without weakening previously reviewed gates?

No migration, Java edit, database action, GitHub approval, production API enablement, or merge is authorized by this ADR.
