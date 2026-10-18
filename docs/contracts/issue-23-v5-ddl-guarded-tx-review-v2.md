# Issue #23 — ACL V5 and guarded JDBC contract v2: SQL privilege boundary and target-scoped operations

> **REVIEW-ONLY DESIGN; NOT APPROVED SQL, NOT A FLYWAY MIGRATION.** No production source edit, no PostgreSQL/staging/Supabase changes, no Workflow HTTP exposure or PR merge is authorized.
>
> Baseline: main `2115de355e8df4f0567e8e8421dae8465da9cea8`, v1 review document in Draft PR #36 `06a0a95c18a3e3ab422eb72c801c226f99e9de0b`. This v2 addresses P0-1 (SQL privilege bypass) and P0-2 (confused-deputy callback) from [independent review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36#issuecomment-6249492270). The [v1 proposed five-table DDL](issue-23-v5-ddl-guarded-tx-review-v1.md) remains provisional, not approved or replaced by an executable migration. Yến's D1–D5 approval applies to **Identity/Core only**.

## 1. Security goals, threats and an explicit limitation

- Trust **only** `CurrentPrincipal.requireVerified()` after Yến's genuine Spring Security JWT/JWKS validation. Canonical actor = `oidc:<base64url(UTF8(issuer))>.<base64url(UTF8(subject))>`. No client-selected tenant, actor, authorities, owner, or `instanceId`.
- An HTTP caller must have active tenant membership, exact verified Workflow authority, and an explicit *resource operation* ACL. CREATE additionally requires exact active tenant/resource-type/definition/version binding. Ownership grants potential visibility only, **not** an operation. Tombstoned and cross-tenant resources deny normal access.
- An adversary able to issue SQL as the **online runtime database role** (stolen DB password or SQL injection) must be denied **all direct reads/writes** on protected Workflow and ACL data. Its available SQL functions must not blindly trust actor and authority strings supplied as parameters.
- **Limitation:** separate DB-user credentials from a stronger backend authorization signing credential. A stolen *DB password alone* should not allow forging a new principal; full compromise of the trusted backend, signing key, PostgreSQL superuser, or schema owner is **out of scope** and must be called out. A separate DB-verifiable caller proof is therefore a P0 design dependency, **not an implemented security feature**.

## 2. Proposed PostgreSQL SQL-role architecture (DB privilege gate)

Keep ACL tables under the already validated private Workflow Flyway namespace `"${flyway:defaultSchema}"`, subject to v1 collation and FK review. All five V5 candidate relations remain: `tenant_guards`, `tenant_memberships`, `resource_registry`, `resource_acl`, `workflow_definition_bindings`. All existing V3 Workflow instances/history and definitions remain protected. Never modify V1–V4.

| SQL role (candidate) | Permitted | Explicitly prohibited |
| --- | --- | --- |
| `dxos_wf_owner` — NOLOGIN, owner and audited `SECURITY DEFINER` | Own protected tables/functions; SQL inside reviewed functions only | Application login or inherited/settable role of runtime/admin |
| `dxos_wf_migrator` — deploy-only | Additive migrations, controlled ownership/permissions | Used in app connection pool or normal HTTP |
| `dxos_wf_runtime` — LOGIN | Schema USAGE; EXECUTE **only** allowlisted guarded Workflow entry functions; harmless DB transaction/diagnostic operations | Direct SELECT/INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER/MAINTAIN on protected tables; schema CREATE/ALTER; owner/migrator membership; BYPASSRLS or superuser |
| `dxos_wf_policy_admin` — separate identity | EXECUTE only guarded policy mutation functions | Direct table DML; unguarded policy mutation, owner role access |
| `dxos_wf_provisioner` — separate batch identity | EXECUTE only reviewed immutable-definition and resource/tenant provisioning APIs | General table DML or open-ended admin SQL |
| `PUBLIC` and Supabase API roles | No Workflow ACL/instance/history access | Function EXECUTE, schema CREATE, inherited access |

**Review-only permission sketch (NOT a migration):**
```sql
-- DO NOT RUN. Exact deployment identity, object list and sequencing require DB approval.
REVOKE ALL ON SCHEMA "${flyway:defaultSchema}" FROM PUBLIC;
GRANT USAGE ON SCHEMA "${flyway:defaultSchema}" TO dxos_wf_runtime, dxos_wf_policy_admin;

-- Target only enumerated Workflow/V5 tables; never grant any direct DML to runtime.
REVOKE ALL ON TABLE
  "${flyway:defaultSchema}".workflow_instances,
  "${flyway:defaultSchema}".workflow_transition_history,
  "${flyway:defaultSchema}".workflow_definitions,
  "${flyway:defaultSchema}".workflow_definition_states,
  "${flyway:defaultSchema}".workflow_definition_transitions,
  "${flyway:defaultSchema}".tenant_guards,
  "${flyway:defaultSchema}".tenant_memberships,
  "${flyway:defaultSchema}".resource_registry,
  "${flyway:defaultSchema}".resource_acl,
  "${flyway:defaultSchema}".workflow_definition_bindings
  FROM PUBLIC, dxos_wf_runtime, dxos_wf_policy_admin;

-- Same deployment TRANSACTION as function creation:
-- REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_read_instance(<exact types>) FROM PUBLIC;
-- GRANT EXECUTE ON FUNCTION "${flyway:defaultSchema}".wf_read_instance(<exact types>)
--   TO dxos_wf_runtime;
--
-- Future function default privileges are set PER CREATING ROLE.
-- Global EXECUTE default to PUBLIC cannot be undone by per-schema REVOKE.
-- ALTER DEFAULT PRIVILEGES FOR ROLE dxos_wf_owner
--   REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC; -- global to this owner; review impact.
```

**Every privileged SQL function MUST:** be owned by non-login owner; be narrow and `SECURITY DEFINER` with fixed `SET search_path = pg_catalog, pg_temp`, fully schema-qualified table/helper references and no untrusted dynamic SQL; explicitly revoke PUBLIC EXECUTE *in its creation transaction*; restrict role-specific EXECUTE; verify authentication proof inside SQL; return only sanitized results after authorization. Audit inherited role membership, existing/default privileges, temporary shadowing, search-path poisoning and function owner. PostgreSQL 17 documents: [safe SECURITY DEFINER and PUBLIC EXECUTE](https://www.postgresql.org/docs/17/sql-createfunction.html), [default privileges](https://www.postgresql.org/docs/17/sql-alterdefaultprivileges.html).

**Migration compatibility BLOCKER:** Existing `PostgresWorkflowPersistence` uses direct JDBC SELECT/INSERT/UPDATE on Workflow tables. An immediate GRANT revocation would break existing runtime/provisioning/tests. Implement the guarded SQL adapter and explicit service/connection-role split, test it with disposable PG17, and then atomically switch the application runtime role. No staging privilege changes until approved rollout. A function named `execute_sql`, `read_any` or impersonation API must never exist.

### 2.1 Allowlisted protected SQL APIs (conceptual, not implemented)

- Runtime only: `wf_create_instance` (new server-generated ID, resource, exact definition/version, CREATE), `wf_activate_instance` (specific instance, ACTIVATE), `wf_transition_instance` (specific instance, verified actor, transition and expected version, TRANSITION), `wf_read_instance` (specific instance, READ), `wf_read_history` (specific instance, READ).
- Policy admin only: `wf_grant_permission`, `wf_revoke_permission`, `wf_set_membership`, `wf_tombstone_resource`, `wf_set_definition_binding`, `wf_change_owner`. Each must lock the permanent tenant guard `FOR UPDATE` first, mutate only the bound tenant/resource, increment `acl_revision` and audit. No generic `update_acl_table`.
- Provisioner only: `wf_register_resource`, `wf_create_tenant`, `wf_provision_definition`. Tenant identity cannot be deleted/reused; immutable definition imports maintain V3 invariants. Admin jobs/backfills must obey same guard.
- Internal (not GRANTed): private tenant resolution, guard, proof verification, authorization, SQL domain helpers, audit functions. Need an exact SQL signature and dependency graph review **before** SQL coding.

**Java Core mapping:** These five public SQL endpoints define the protected surface but do not automatically preserve existing `WorkflowRuntime` calls. Prefer a private connection-scoped `WorkflowPersistencePort` backed by narrowly scoped SQL primitives, with **proof/operation/target validation in every protected call**, so existing Java Core state machine remains authoritative. If instead domain execution moves into SQL, it requires a separate reviewed design and Core-equivalence tests. This trade-off remains open.

### 2.2 DB-verifiable principal provenance: P0, not a claim of existing protection

An SQL function accepting `actor_id`, `workflow:create` or `authenticated=true` as free arguments is **not a trusted authorization boundary**: SQL clients can invent them. Custom PostgreSQL GUCs (`SET LOCAL app.actor`) can also be set by a user with arbitrary SQL and are insufficient alone.

**Proposed stronger path requiring security sign-off:** issue a *short-lived cryptographic, operation-scoped attestation* at the trusted Java Identity-to-Gateway boundary, signed with material **separate from the DB credential**. SQL verifier uses a protected verification key (`pgcrypto`-backed MAC only if independently approved/tested) and verifies, before disclosing/writing protected data:
- Canonical actor/issuer+subject, exact verified operation authority, operation type, immutable bound instance OR new generated instance ID + resource pair/definition ID/version, transition actor/ID/version where applicable.
- A cryptographically sound deterministic **length-prefixed UTF-8** encoding; version/key ID, nonce, expiration, and binding to the **actual JDBC transaction** established after BEGIN. No string concatenation ambiguity or Unicode normalization.
- Verify signature/MAC, expiry, target, operation and transaction binding; then independently look up tenant and **current** membership/resource ACL/binding *after tenant-guard lock*. A valid proof alone never grants ACL.
- Never log proof/key/raw JWT; key custody/rotation/revocation and replay handling require explicit review; SQL function owners alone may read verification key. Test with only stolen runtime DB credentials: forged actor, missing/wrong/expired proof, operation/target swap, cross-transaction replay, revoked ACL all deny with no data.
- **Still unresolved P0:** actual algorithm, PostgreSQL 17 `pgcrypto` deployment, key rotation, transaction identity binding, replay/timing assurance and verified caller encoding. Until approved, do **not** claim resistance to leaked runtime DB credentials merely because table grants are revoked.

**Alternative:** if DB verifier is rejected, explicitly **weaken and approve** the threat model to a trusted-backend boundary, and document that the runtime-only SQL caller could impersonate another actor through functions; no SQL-credential compromise claims.

## 3. Replace generic callback with target+operation-scoped Gateway APIs

**Remove v1 conceptual `write(caller,target,operation,Function<WorkflowRuntime,T>)` and `read(caller,target,Function<WorkflowRuntime,T>)`.** That interface could authorize instance A and let the callback act on B; a "read" callback could write. It is a *design-level confused deputy risk*, not an existing production route exploit.

**New proposed interfaces (DESIGN ONLY):**
```java
public interface AuthorizedWorkflowService {
    WorkflowInstance create(CreateWorkflowInput input);  // input has NO instanceId
    WorkflowInstance activate(String instanceId);
    TransitionResult transition(String instanceId, String transitionId, long expectedVersion);
    WorkflowInstance snapshot(String instanceId);
    List<TransitionRecord> history(String instanceId);
}

// Infrastructure/internal only; not exposed to controllers or plugins.
interface GuardedWorkflowExecutor {
    WorkflowInstance create(VerifiedCaller caller, GeneratedInstanceId serverId,
                            ExactDefinitionRef definition, ResourceReference resource);
    WorkflowInstance activate(VerifiedCaller caller, WorkflowInstanceId target);
    TransitionResult transition(VerifiedCaller caller, WorkflowInstanceId target,
                                TransitionId transition, long expectedVersion);
    WorkflowInstance snapshot(VerifiedCaller caller, WorkflowInstanceId target);
    List<TransitionRecord> history(VerifiedCaller caller, WorkflowInstanceId target);
}
```

**Every `AuthorizedWorkflowService` method** calls `CurrentPrincipal.requireVerified()` at the trusted boundary; no method accepts user-provided principal/actor/tenant/authority/owner or free-form runtime lambda. CREATE rejects client-supplied `instanceId` with explicit DTO unknown-field rejection; generates opaque 192-bit ID server-side; handles collisions with no hidden existence oracle and **never retries COMMIT_OUTCOME_UNKNOWN**.

**Immutable operation scope** is minted only inside a guarded transaction after trusted identity and authoritative binding checks. A package-private `ScopedWorkflowPersistencePort` is bound to *one* target, one operation, one actor and exact definition/version where applicable; its calls fail before SQL if target/actor/operation differ, scope is already closed, or a read attempts a write. The DB functions **also re-check** the bound cryptographic proof and current ACL, not just the app-side scope.

| Use case | Allowed Core/port calls within scope | Explicitly refused |
| --- | --- | --- |
| CREATE | `loadInstance(generatedId)` for internal collision handling, `loadDefinition(exactId/version)`, `createInstance(generatedId, boundResource, exactVersion)` | Client ID, loading arbitrary existing instance, arbitrary history, another definition/resource |
| ACTIVATE | `loadInstance(target)`, `loadDefinition(persisted exact definition)`, `commitActivation(target)` | Activation of B, history, transition, forged actor |
| TRANSITION | `loadInstance(target)`, `loadDefinition(persisted exact definition)`, `commitTransition(target, verifiedActor, expectedVersion, transitionId)` | B/actor swap, different version/transition, unverified actor |
| SNAPSHOT | `loadInstance(target)` only under guarded READ | Any write or B |
| HISTORY | `loadHistory(target)` only under guarded READ | Any other target/read/write |

**Guarded JDBC protocol:** the infrastructure coordinator acquires one checked **physical JDBC Connection**, sets `READ COMMITTED` and `autoCommit=false`, enforces existing durable PostgreSQL checks, gets candidate tenant privately, acquires permanent tenant row `FOR SHARE`, and **re-reads** authoritative tenant/resource/membership/ACL/definition binding *after lock*. Then it creates the scoped port/`WorkflowRuntime` inside that same transaction; **no inner getConnection/commit/close**. The owner COMMITs exactly once, returning success only after known durable commit. For READ/HISTORY use a coherent post-guard `READ COMMITTED` snapshot. Policy mutation paths use same guard `FOR UPDATE` before child rows and increment revision in the same transaction; lock order guard → resource → instance → history, including implicit FK locks.

**Error bridge:** confirmed rollback allows sanitized HIDDEN / FORBIDDEN / UNAVAILABLE and domain errors to survive; no bare `WorkflowAccessException` through current catch that translates it to `OPERATION_FAILURE`. If write commit is attempted with uncertain result or write rollback fails, `COMMIT_OUTCOME_UNKNOWN` wins, no blind retry or misleading 403/404. HTTP mapping for unknown commit needs approval. Lock timeout/deadlock fail closed without leaking hidden existence.

**Confused-deputy tests before sign-off:** bind scope A, attempt `loadInstance(B)`, `loadHistory(B)` or `commitTransition(B)`; READ scope attempts write; CREATE uses unexpected definition/resource/ID; TRANSITION replaces canonical actor or expected version; reuse after commit/rollback; call SQL function with proof for A and parameters for B. All fail with no protected materialization/write.

**Relevant PostgreSQL 17 guarantee:** `FOR SHARE` conflicts with `FOR UPDATE` on the same row; transaction locks persist through commit/rollback, subject to savepoint semantics. This design only works when **every** ACL mutator locks the same permanent guard, then every Workflow operation rechecks current policy after that lock. See [explicit locking documentation](https://www.postgresql.org/docs/17/explicit-locking.html).

## 4. Approval gates, tests and remaining questions

| Gate | Independent evidence required using disposable PostgreSQL 17, not staging |
| --- | --- |
| SQL-A — privileges | Effective role/table/function privilege matrix including inherited/default/PUBLIC/Supabase grants; direct SELECT/DML/ALTER/TRUNCATE denied under actual runtime role |
| SQL-B — provenance | Real independently approved proof verifier and key custody; forged SQL actor/authority, swapped target, wrong/expired/replayed transaction proof denied |
| API-A — target scope | Five typed operations, no generic callback, non-escaping scoped port; deliberately invoke B after authorizing A and validate no SQL side effect |
| TX-A — durability | One physical JDBC connection, guard lock order/recheck, separate READ/HISTORY; typed known rollback and unknown COMMIT injection |
| DB-A — V5 DDL | v1 FK/UNIQUE/collation and immutable tenant-resource binding, one-way tombstone/ID non-reuse, legacy V3 reconciliation, full mutator/provisioning enforcement |
| SEC-A — visibility | Owner visibility vs other grants, missing/hidden 404 vs visible-denied 403, binding visibility; explicit product/security approval |
| HTTP-A — integration | Genuine Identity JWT/JWKS Spring filter-chain + Request regression, all five routes 401/403/404; HTTP stays unregistered until verified |

**PostgreSQL acceptance:** two physical connections with deterministic barriers for revoke-first and guard-first (CREATE/transition/read/history/membership/binding/tombstone), postcommit fresh connection state/history; no timing-only sleeps. Generated ID collision, lost CREATE response/idempotency and unknown-COMMIT client retry contract need separate sign-off.

**Disposition: V2 DOCUMENT DRAFT READY FOR SECURITY/DB REVIEW; V5 MIGRATION AND PRODUCTION AUTHORIZATION STILL BLOCKED.** Yến's D1–D5 architecture decision remains intact. The next approval needed is SQL-A/SQL-B/API-A/TX-A plus DB-A/SEC-A; Yến re-reviews actual `CurrentPrincipal.requireVerified() → AuthorizedWorkflowService` implementation after it exists. PR #36 DRAFT, Issues #23/#22 OPEN, production HTTP OFF. No SQL ran and no Java or Flyway source was changed by this documentation step.
