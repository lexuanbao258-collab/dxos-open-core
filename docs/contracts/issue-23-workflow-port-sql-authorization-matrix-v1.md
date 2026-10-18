# Issue #23 — Six-port-method SQL / authorization / transaction mapping, v1

> **REVIEW DESIGN — NOT EXECUTABLE SQL OR IMPLEMENTATION APPROVAL.**
> Scope: exactly the six public methods in `WorkflowPersistencePort`, their `WorkflowRuntime` callers, trusted principal provenance, PostgreSQL permissions, transaction boundaries, error propagation and negative evidence. This document is **not** a V5 Flyway migration, completed SQL grant policy, implemented proof verifier, or production approval. Do not execute the SQL signatures as a migration or access shared staging/Supabase. Keep #23 OPEN, #36 DRAFT and #22 Workflow HTTP OFF.
>
> **Code baseline:** `main` `2115de355e8df4f0567e8e8421dae8465da9cea8`; [Workflow port](../../backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowPersistencePort.java), [Runtime](../../backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java), [current JDBC adapter](../../backend/src/main/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistence.java), [V3 migration](../../backend/src/main/resources/db/migration/V3__create_workflow_persistence.sql), [ADR-0023 Option A](../adr/ADR-0023-workflow-acl-transaction-boundary.md), [SQL/roles v2](issue-23-v5-ddl-guarded-tx-review-v2.md), [independent v2 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36#issuecomment-6249635681).
>
> **Review goal:** supply a traceable candidate mapping, not silently assert the unimplemented SQL APIs meet the existing Core port. The former CREATE collision-probe conflict is resolved at **design-choice level by [ADR-0024 C3](../adr/ADR-0024-workflow-create-and-sql-principal-attestation.md)**; code implementation and independent review remain blocked.

## 1. Fixed rules and exact Trust/transaction boundaries

1. A trusted `AuthorizedWorkflowService` obtains `CurrentPrincipal.requireVerified()` only from the actual Identity JWT/JWKS security boundary. Exact case-sensitive Workflow authority: `workflow:create`, `workflow:activate`, `workflow:transition`, or `workflow:read`. Canonical actor is `oidc:<base64url(UTF8(issuer))>.<base64url(UTF8(subject))>`, preserving original issuer/subject byte identity. No caller-supplied actor, tenant, owner, `authenticated=true`, or header proves anything. The current `AuthenticatedPrincipal` record constructor alone is **not** a verification mechanism.
2. An application-private `OperationScope` binds one **action, target, actor, exact authority, and immutable definition/transition context**. Production HTTP must not receive `WorkflowRuntime`, `WorkflowPersistencePort`, a generic callback or a JDBC `Connection`. The private adapter rejects a mismatching method, target, actor, definition/version, target lifecycle or a request after scope closure.
3. **Each port invocation has its own connection and transaction** under proposed ADR Option A. Preliminary reads and final write may use *different* physical JDBC connections. Each transaction requires fresh proof and post-lock ACL assessment. A successful **write port method** must **not return until it itself confirms durable COMMIT**. No outer deferred-COMMIT port, no assumption that the full Runtime call is atomic.
4. The SQL protection objective is **no direct protected table SELECT or DML by `dxos_wf_runtime`**. Grant only explicitly scoped `EXECUTE` on vetted `SECURITY DEFINER` APIs. All protected SQL APIs must verify actual actor/operation/target provenance, tenant guard and current ACL **inside the SQL trust boundary**, not just in Java. A function that accepts a free-form SQL `actor_id` or a GUC cannot resist stolen runtime SQL credentials. The [v2 cryptographic proof proposal](issue-23-v5-ddl-guarded-tx-review-v2.md) remains unapproved; its exact algorithm/key storage and transaction binding are a **P0 dependency**.
5. Authoritative resource->tenant mapping comes from V5 registry, not HTTP. Existing V3 instances store only global `instance_id` and `(resource_type,resource_id)`. No registry row, changed key, tombstone, missing guard, inactive membership or foreign tenant fails closed. Active owner membership alone gives visibility, not operation rights; exact Workflow authority **and** explicit resource grant required for every operation. CREATE additionally needs exact active tenant/resource-type/definition-ID/version binding.
6. Guarded Workflow SQL begins checked `READ COMMITTED`, `autoCommit=false`, uses lock-capable transaction configuration (`setReadOnly(false)` even for guarded READ/HISTORY), locks **permanent tenant guard FOR SHARE**, then re-reads resource, membership, ACL, definition binding where applicable **after guard acquisition**. No protected result is materialized before that decision. All policy mutators lock same row `FOR UPDATE` before child rows and bump `acl_revision`. No savepoint rollback may release the held guard and then continue without a new guard lock+full recheck. PostgreSQL `FOR SHARE` only excludes policy `FOR UPDATE` writers, **not** another `FOR SHARE` Workflow action.
7. **Admin/provisioner SQL authentication and guard protocol are separate:** runtime functions must not double as grant/revoke/provisioning entrypoints. The migration role and owner are offline/non-login where appropriate; runtime cannot `SET ROLE` into either. Public/default EXECUTE and inherited roles need catalog evidence.

## 2. Proposed wire representation (NOT actual schema DDL)

Avoid passing a mutable Java object or lossy/misparsed timestamp. Define **reviewed versioned SQL composite types** before creating any V5 functions; all are merely *symbolic signatures* below, not types that currently exist.

- `wf_instance_wire`: `instance_id TEXT`, `definition_id TEXT`, `definition_version BIGINT`, `resource_type TEXT`, `resource_id TEXT`, `current_state TEXT`, `runtime_version BIGINT`, `lifecycle TEXT`, `created_at_seconds BIGINT`, `created_at_nanos INTEGER`, `updated_at_seconds BIGINT`, `updated_at_nanos INTEGER`. Exact parity with V3 instance columns, no timestamp rounding.
- `wf_definition_wire`: `definition_id TEXT`, `definition_version BIGINT`, `initial_state TEXT`, `state_ids TEXT[]` and `transitions wf_definition_transition_wire[]` (`transition_id`, `source_state`, `target_state`). Reject duplicate transition IDs/invalid endpoints; order collections deterministically for transport but preserve exact Java `Set` semantics. This is not a public definition-listing API.
- `wf_history_wire`: `instance_id TEXT`, `transition_id TEXT`, `source_state TEXT`, `target_state TEXT`, `actor_id TEXT`, `resulting_runtime_version BIGINT`, `occurred_at_seconds BIGINT`, `occurred_at_nanos INTEGER`. Results always sorted by `resulting_runtime_version`, not timestamp.
- `wf_transition_result_wire`: exact `previous_instance` + resulting `instance` + `record`, or adapter reconstitutes `TransitionResult` from the trusted proposal after DB confirmation. The latter needs **full equality recheck** and must not return a proposal if the DB commits something different.
- `verified_operation_proof BYTEA`: abstract signed/MACed, versioned canonical UTF-8/length-prefixed bytes, issued from real verified Identity context with exact target+operation+definition/transition binding and minted **per physical transaction**. Not a JWT passed to an unreviewed SQL helper. SQL verifier/key rotation/replay prevention is NOT specified/approved; do not build or assume this mechanism works until a separate security ADR signs off.

## 3. Six SQL function signatures and effective GRANT intent

**Single proposed namespace:** existing reviewed private Workflow schema `"${flyway:defaultSchema}"`. Identifiers below name the six **port** APIs, deliberately separate from the *five HTTP* endpoints. SQL implementations are envisioned as `SECURITY DEFINER` under a reviewed NOLOGIN function owner; `SET search_path=pg_catalog,pg_temp` and schema-qualified internal dependencies. Every function must independently authorize the **bound** operation and target; **no generic read-any-instance, grant, or SQL executor**.

```sql
-- SIGNATURE PROPOSALS ONLY, DO NOT EXECUTE (types/functions not implemented).
-- All types/functions are located in the existing private Workflow schema.
"${flyway:defaultSchema}".wf_port_load_definition(proof BYTEA, id TEXT, version BIGINT)
    RETURNS "${flyway:defaultSchema}".wf_definition_wire;

"${flyway:defaultSchema}".wf_port_load_instance(proof BYTEA, id TEXT)
    RETURNS "${flyway:defaultSchema}".wf_instance_wire;

"${flyway:defaultSchema}".wf_port_load_history(proof BYTEA, id TEXT)
    RETURNS SETOF "${flyway:defaultSchema}".wf_history_wire;

"${flyway:defaultSchema}".wf_port_create_instance(proof BYTEA, initial "${flyway:defaultSchema}".wf_instance_wire)
    RETURNS "${flyway:defaultSchema}".wf_instance_wire;

"${flyway:defaultSchema}".wf_port_commit_activation(
    proof BYTEA, expected "${flyway:defaultSchema}".wf_instance_wire, activated "${flyway:defaultSchema}".wf_instance_wire)
    RETURNS "${flyway:defaultSchema}".wf_instance_wire;

"${flyway:defaultSchema}".wf_port_commit_transition(
    proof BYTEA, expected "${flyway:defaultSchema}".wf_instance_wire,
    proposed "${flyway:defaultSchema}".wf_instance_wire, record "${flyway:defaultSchema}".wf_history_wire)
    RETURNS "${flyway:defaultSchema}".wf_transition_result_wire;
```

**Permissions:** for each of those **six exact signatures**, owner `dxos_wf_owner` owns function + protected tables; `dxos_wf_runtime` gets **EXECUTE only**; `PUBLIC`, Supabase `anon/authenticated/service_role`, policy admin and provisioner do **not** get EXECUTE. This per-function EXECUTE is a *conditional design*, **not sufficient by itself**: proof validator must reject invalid/mismatched actor/operation/target and all private helper functions must be uncallable by runtime. Do not grant direct SELECT on any V3/V5 Workflow/ACL table, schema CREATE, TRIGGER, TRUNCATE, or owner membership to runtime. New-function PUBLIC EXECUTE must be revoked in the **same transaction** as creation; review default privileges for actual creator role and full inherited grants.

**DB role/API allocation:**

| Database principal | Allowed direct table access | Allowed function family | Proof and lock obligations |
| --- | --- | --- | --- |
| `dxos_wf_runtime` | NONE on protected Workflow/ACL tables | Exact six `wf_port_*` methods ONLY | Every call verifies per-transaction bound verified caller proof and post-guard operation ACL |
| `dxos_wf_policy_admin` | NONE | Audited `wf_policy_*` only, never `wf_port_*` | Authenticated admin provenance, tenant `FOR UPDATE` guard, revision + audit before commit |
| `dxos_wf_provisioner` | NONE | Audited `wf_provision_*` only | Separate provenance, immutable definition/tenant/resource, guard + audit |
| `dxos_wf_migrator` | Deployment-time controlled DDL only | Explicit migration pathway only | No runtime pool, V3/V4 manifests/checksums unchanged |
| `dxos_wf_owner` | Table owner / function owner | Internal SQL under definer | NOLOGIN, not inherited by online roles, SQL search_path/function hijack defense |
| `PUBLIC` and Supabase API roles | NONE | NONE | No default/inherited grants |

**Source compatibility:** current `PostgresWorkflowPersistence` directly queries/updates tables and `WorkflowApprovedMigration`/`WorkflowEventTriggerGuard` pin the reviewed V3/V4 paths. A privilege switch without new SQL functions, reviewed roles and legacy-provisioning separation **breaks the adapter**. No such switch is part of this design task.

## 4. Exact Java port → SQL function → operation/target/transaction matrix

| # / Java port method | Proposed SQL entrypoint and permitted trusted call scope | Authoritative SQL check / data permitted | JDBC commit / errors |
| --- | --- | --- | --- |
| **1. `loadDefinition(id, version)`** | `wf_port_load_definition`; **internal** CREATE scope for exactly signed resource+definition+version, or ACTIVATE/TRANSITION scope for the definition pinned to exactly signed persisted instance. **Not an unauthenticated/list-all definitions API.** | Resolve signed resource or instance+resource privately; `FOR SHARE` its permanent tenant guard; re-read membership, resource and exact operation grant/authority; CREATE binding active; for existing instance verify its immutable exact definition/version; only then read V3 definitions/states/transitions and construct exact `WorkflowDefinition`. | Guarded `READ COMMITTED` operation-local JDBC transaction, no write, ended before returning; missing authorized exact definition -> `DEFINITION_NOT_FOUND`, unpermitted target -> typed HIDDEN/FORBIDDEN; policy failure -> UNAVAILABLE. No public definition oracle. |
| **2. `loadInstance(id)`** | `wf_port_load_instance`; public SNAPSHOT requires signed READ, **internal** ACTIVATE/TRANSITION may fetch only their signed exact instance under corresponding operation grant (not separately demanding READ authority). **Secured Gateway CREATE uses ADR-0024 C3 and NEVER invokes this method**, even on a generated-ID collision. | Minimal instance→resource→tenant locator only; guard then authoritative recheck of active registry/membership/resource ACL/authority; load full snapshot and exact pinned definition *after authorization*. No cross-instance or resource switching. | Guarded `READ COMMITTED` transaction; rollback/error is fail closed. Missing/hidden indistinguishable 404 externally, visible denied 403; authorized absent instance -> domain `INSTANCE_NOT_FOUND` after safe classification; no write. |
| **3. `loadHistory(id)`** | `wf_port_load_history`; **public HISTORY requires READ** for the exact signed instance. No history under ACTIVATE/TRANSITION or CREATE scope. | Resolve minimal binding then guard, recheck READ policy; verify instance existence **before** returning 0 rows; materialize only scoped history ordered by resulting version on same connection. Reconstruct exact actor/time records. | Guarded `READ COMMITTED` transaction ends before result released; missing and hidden 404 externally; existing with zero transitions returns empty immutable list, not a missing-instance result. |
| **4. `createInstance(initial)`** | `wf_port_create_instance`; CREATE scope for a **server-generated** opaque ID + exact signed `resourceType/resourceId/definitionId/version` and proposed full initial snapshot/time. No caller-selected ID or alternate target. | Lock authoritative resource tenant guard first; post-lock verify membership, exact CREATE authority/resource grant, active exact definition binding, non-tombstone; load immutable definition and rederive `NOT_STARTED` version 0 initial state and equal epoch+nanos times; insert globally unique ID with DB conflict handling. Never expose colliding existing object. | **One write-port JDBC transaction** with one confirmed COMMIT *before* returning; duplicate/collision is internal sanitized; only known rollback allows typed domain/policy errors; uncertain COMMIT/failed write rollback -> `COMMIT_OUTCOME_UNKNOWN`, never blind retry. |
| **5. `commitActivation(expected, activated)`** | `wf_port_commit_activation`; ACTIVATE scope for that **exact signed instance**, pinned definition and proposed timestamps; deny target/version/resource change. | Guard lock, post-lock current ACTIVATE policy; lock instance `FOR UPDATE` afterward; verify full expected snapshot, existing NOT_STARTED lifecycle, version/lifecycle conflict priority and pinned definition; rederive activation from current state/immutable definition with exact epoch+nanos; no history append. | **One write-port JDBC transaction** and confirmed COMMIT before return. Preserve domain `INSTANCE_NOT_FOUND / VERSION_CONFLICT / INSTANCE_COMPLETED / TRANSITION_NOT_ALLOWED / DEFINITION_NOT_FOUND` on authorized paths; typed ACL only after known rollback; uncertain outcome wins. |
| **6. `commitTransition(proposal)`** | `wf_port_commit_transition`; TRANSITION scope for exact signed instance + canonical verified actor + transition ID + expected runtime version and proposed epoch+nanos. **No actor/body override.** | Guard+post-lock TRANSITION grant/authority and registry check; then instance `FOR UPDATE`; compare **full previous snapshot** and expected version/lifecycle; load exact definition and **rederive canonical transition** with actor, transition ID, timestamp and expected version (as current JDBC adapter does), compare *entire* resulting snapshot/record, update one instance state and append **exactly one** matching history row. | **One write-port JDBC transaction**; history and state atomic with one COMMIT before success. Preserve version/conflict/invalid-transition/current Core precedence after authorization; failed/uncertain write classification and no retries as above. |

**Important encoding/equivalence requirement:** the proposed `wf_*_wire` transport must be lossless (Java `Instant` epoch seconds+nanos, immutable Core snapshot fields, definition state/transition sets and actor identity). If implementing domain re-derivation partly in SQL, compare against the exact Java semantics including lifecycle, terminal-state rules, validation/error precedence; otherwise the Java adapter must enforce them *and* the SQL API must reject any DB-credential-only attempt to submit a different untrusted proposal. Binding a canonical **hash of the complete proposal** into the independent proof is a candidate but **not approved cryptographic design**. A signed operation name alone is insufficient to authorize arbitrary next-state SQL payloads.

## 5. CREATE collision: design choice C3 recorded in ADR-0024

**Resolved at design-choice level, not yet implemented or production approved.** See [ADR-0024](../adr/ADR-0024-workflow-create-and-sql-principal-attestation.md) for source evidence, threat model and mandatory acceptance tests.

**Chosen C3 for secured Gateway CREATE**: Never call `WorkflowRuntime.createInstance` or its preliminary `persistence.loadInstance(serverGeneratedId)` probe on the HTTP CREATE path. Instead, after true JWT verification, generate an opaque server-owned ID; call **guarded `loadDefinition(exactId, exactVersion)`** only for the currently signed CREATE resource/definition binding; call `WorkflowInstance.notStarted(newId, exactDefinition, resource, clock.instant())`; and call **guarded `createInstance(initial)`**. The latter owns the final lock + current ACL check + INSERT + durable COMMIT, with a global primary key for collision resolution. On a known-rollback collision, do not SELECT the existing instance or expose the colliding ID; fail safely. Unknown commit never auto-retries.

This reuses the existing Core domain and public persistence method **without editing Core**, but changes which public Runtime orchestration method the *trusted Gateway* calls. **Do not claim identical error precedence**: the original Runtime performs duplicate-ID probe before definition lookup, while C3 performs definition lookup before the insert; with server-only IDs this difference is internal and needs explicit regression/approval. Production HTTP may not expose a controller or Spring bean calling the unguarded Runtime CREATE path.

**Six-method matrix correction:** `wf_port_load_instance` under **CREATE** has **no permitted invocation**. All other LOAD_INSTANCE cases continue to require exact target proof and per-operation authorization; no dummy snapshots or fabricated `INSTANCE_NOT_FOUND`. The SQL proof protocol chosen as a design candidate is [ADR-0024 transaction-bound HMAC-SHA-256](../adr/ADR-0024-workflow-create-and-sql-principal-attestation.md); verifier code, secrets/roles, expiration, replay limits and real database negative tests remain P0 open.

## 6. Error classification, precedence and HTTP bridge

**Never leak raw SQLSTATE/messages, actor IDs, tenant identity, registry existence, schema privileges or hidden instance snapshots to HTTP.** Caller with no/invalid/wrong issuer/audience JWT gets 401 in actual Spring security filter *before* the port. No Java fake principal or request header may override that.

| Condition | Internal handling at boundary | External handling (subject to SEC/HTTP approval) |
| --- | --- | --- |
| Missing/foreign/inactive/tombstone/hidden target | No materialization; typed `HIDDEN` on known rollback/ended read | Identical safe 404 for missing and hidden |
| Visible target but no exact verified operation authority/resource grant | Typed `FORBIDDEN` after known rollback | Sanitized 403; owner gets no implicit operation |
| Provenance/ACL verifier null/throws, lock timeout, deadlock, DB unavailable | Typed `UNAVAILABLE` or fail-closed provider error after known rollback | Sanitized non-success (candidate 503; **not approved**) |
| Authorized exact definition/instance missing, invalid transition, stale version, completed lifecycle | Preserve existing port `WorkflowErrorCode` and precedence after confirmed rollback/no-write; **re-authorize before releasing errors from preliminary Runtime reads** | Domain errors without hidden existence leakage; mapping TBD |
| Known write rollback / known no COMMIT | Typed policy result or appropriate domain `WorkflowException` | Do not replace every error with generic `OPERATION_FAILURE` |
| Provider failure after COMMIT is attempted, or failed write rollback with unknown state | Override any prior ACL/domain error with `COMMIT_OUTCOME_UNKNOWN` | Safe distinct nonretryable outcome; HTTP code/idempotency **OPEN** |
| CREATE generated ID collision with known no COMMIT | `INSTANCE_ALREADY_EXISTS` internal, no other instance materialization | Safe internal collision path, no existence oracle; never auto retry after uncertain COMMIT |

**Proposed SQL error channel:** narrowly allowlisted SQLSTATE codes for *sanitized internal* HIDDEN, FORBIDDEN, UNAVAILABLE, INSTANCE_NOT_FOUND, DEFINITION_NOT_FOUND and provider failure, translated by **infrastructure-only typed bridge after rollback**; exact SQLSTATE values/error precedence require DB review. A privileged function must not raise raw permission/SQL details externally or conflate an unexpected SQL error with a policy denial. Only Java JDBC coordinator knows whether COMMIT was attempted and unknown; no SQL function can truthfully assert that the caller's JDBC COMMIT succeeded.

**Potential timing leakage:** HTTP hidden-vs-nonexistent responses must match status, body, identifiers/headers and cache handling; timing still needs threat-focused tests. Never return a preliminary validation error after permissions are revoked without a fresh guarded recheck. A fresh check **does not retry** the Workflow write and may not overwrite `COMMIT_OUTCOME_UNKNOWN`.

## 7. Test matrix and review gates

| Test id | Deterministic disposable-PostgreSQL 17 evidence required | Must fail if |
| --- | --- | --- |
| M1 | One JDK21 integration test per **six** port methods verifies exact SQL function signature/role, correct actor/operation/target/definition and protected results | SQL function can operate on instance B with proof for A; READ can write |
| M2 | Actor proof created **for each new physical transaction**, expired/wrong-operation/target/swapped-proposal/forged actor or reused proof denied with SQL-only runtime credentials | Free-form actor/GUC yields authorization or proof from earlier transaction succeeds |
| M3 | Effective permission catalog and runtime-role negative tests: no direct V3/V5 SELECT/DML/ALTER/TRUNCATE, no PRIVATE helper/PUBLIC EXECUTE, no owner role inheritance | Online runtime accesses rows or helper directly |
| M4 | Core-exact create/activation/transition snapshots, epoch nanos, version/lifecycle/error precedence, terminal-state rules and state+history atomicity | Java-vs-SQL output/proposal differs or partial write commits |
| M5 | Two physical JDBC connections, revoke-first/write-first and revoke **between preliminary read and final write**, including membership, resource tombstone, owner/ACL change and CREATE binding | Revoked writer still commits, hidden read/history leaks |
| M6 | Guarded READ/HISTORY, zero-history vs absent, post-lock `READ COMMITTED` fresh snapshot, lock-capable reads; no savepoint guard release | Unauthorized materialization or pre-revoke stale snapshot |
| M7 | Exception prior to JDBC COMMIT vs ambiguous COMMIT/failed rollback, recoverable transient SQL error without blind retry, failure after visible preliminary read | Wrong `COMMIT_OUTCOME_UNKNOWN` precedence or protected domain error leaks |
| M8 | Force generated-ID collision on the ADR-0024 C3 Gateway CREATE path (without hidden `loadInstance`); blocked client-supplied `instanceId`; lost-response behavior | Hidden instance snapshot or fake Core port result |
| M9 | Runtime HTTP role, provisioner/admin role split, V3/V4 approved manifest path and forward-only V5 proposal | Old adapter bypass or unsafe staging role switch |
| M10 | After real `CurrentPrincipal.requireVerified()` wiring: genuine JWKS JWT, wrong issuer/audience/forged headers, all five routes, Request regressions | Unauthorized Workflow HTTP reachable |

**Approval still needed:** ADR-0024 C3 Gateway CREATE implementation/equivalence review; concrete wire composite DDL + SQL function exact implementations; SQL-A/SQL-B authenticated proof + role/key custody; PostgreSQL DDL FK/UNIQUE/collation/guard mutation enforcement; transaction savepoints and SQLSTATE bridge; domain parity tests; original V3/V4 pinned manifest compatibility; visibility/403/404 and uncertain write HTTP/idempotency contract. Independent PostgreSQL/DB-security reviewer must sign off before any executable migration; Yến reviews real Identity integration later.

**Status: SIX-METHOD MAPPING UPDATED WITH ADR-0024 C3/HMAC DESIGN CHOICES; IMPLEMENTATION STILL BLOCKED BY INDEPENDENT SQL/SECURITY SIGN-OFF AND TESTS.** This documentation does not approve a Core change, SQL privileges/migration, protected HTTP exposure or merge. No SQL/JDK21 tests are claimed at this new HEAD.
