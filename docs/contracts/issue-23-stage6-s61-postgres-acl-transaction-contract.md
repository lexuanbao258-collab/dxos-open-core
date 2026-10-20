# Issue #23 — Stage 6.1: PostgreSQL authoritative ACL and transaction contract

**Status: REVIEW CANDIDATE / DESIGN ONLY — NOT DDL OR PRODUCTION APPROVAL**
**Date:** 2026-10-19
**Owner:** Bảo, Issue #23. **Identity/application reviewer:** Yến. **PostgreSQL/cryptography independent sign-off:** not established; owner-led review is not independent DB certification.
**Base:** Stage 5 [PR #49](https://github.com/lexuanbao258-collab/dxos-open-core/pull/49), reviewed exact HEAD `32ca73fa8444ab51cf7cf30baa90fb39938b627b`.
**Reference architecture:** Draft [PR #36](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36) at `ff824fc827cdef448fff82d907cef0d50113d81a`: [ADR-0023 Option A](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/adr/ADR-0023-workflow-acl-transaction-boundary.md), [ADR-0024 C3 + HMAC proposal](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/adr/ADR-0024-workflow-create-and-sql-principal-attestation.md), [six-port SQL matrix](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/contracts/issue-23-workflow-port-sql-authorization-matrix-v1.md), and [V5 v2 candidate](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/contracts/issue-23-v5-ddl-guarded-tx-review-v2.md).

> **STOP RULE:** Do not create or run `V5__*.sql`, modify V1–V4, grant SQL roles, create signing keys, cherry-pick PR #36 SQL fixtures into production, touch shared Supabase/staging, or register Workflow HTTP from this document. Execution is deferred until explicitly approved design, migration compatibility, privileges, and isolated PostgreSQL 17 evidence. No Spring `@Transactional` wrapper can substitute for the durable port boundary.

## 1. Baseline and scope

The Stage 3 `AuthorizedWorkflowService` is a **non-registered** application seam with a precheck; Stage 4 pins exact verified operation authorities; Stage 5 adds `WorkflowAclSnapshotReader` contract and `TenantResourceWorkflowAccessPolicy` with synthetic authoritative facts, **not a PostgreSQL implementation**. Stage 5's precheck can become stale before execution. `PostgresWorkflowPersistence` in current production source performs direct V3 table SQL, owns a separate JDBC transaction per port method and maps non-`WorkflowException` failures generically. V3 instances carry global `instance_id` and resource reference, **no tenant ID**. Hence none of Stages 3–5 demonstrates a safe live tenant/ACL write.

**S6.1 outcome:** pin the V5 candidate data invariants, target+actor+operation SQL boundary, one-port/one-transaction protocol, error bridge, mutator lock obligations, CREATE C3 route and deterministic PG17 evidence **before writing executable migration or guarded JDBC code**. S6.2 onwards will implement only after review.

The service remains a Spring Boot modular monolith for the OLP MVP; no additional microservice, message broker, distributed transaction, custom IAM, or new Core signatures.

## 2. Stage 5 decisions: status and provisional behavior

**Already owner-approved:** (a) owner with active tenant membership may see resource existence, but owner is **not** implicitly allowed a Workflow operation; exact JWT-derived authority AND explicit resource grant required; (b) CREATE requires exact active `(tenant_id, resource_type, definition_id, definition_version)` binding; its revocation blocks subsequent CREATE but does not automatically revoke existing instances; (c) no tenant transfer in MVP v1, permanent tombstone, no reuse of resource/tenant identities.

**Two product policy decisions ratified by Issue #23 owner on 2026-10-20 (not SQL/HTTP security approval):**

| ID | Recommended MVP rule (Stage 5 candidate) | Status / acceptance action |
|---|---|---|
| DEC-VIS | A resource exists for a viewer only if the registry entry is ACTIVE, viewer has active **membership of that exact tenant** and (viewer is persisted owner OR has >=1 current positive explicit Workflow ACL grant). A visible caller without the *requested* operation's grant returns FORBIDDEN; foreign/nonmember/nonvisible returns HIDDEN. `workflow:read` alone does not grant resource visibility. | **OWNER APPROVED** ([record](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6280476807)); production HTTP 404 equivalence and independent security integration evidence still BLOCKED. |
| DEC-CREATE | If an exact requested CREATE definition binding is absent/inactive, classify HIDDEN even when resource itself is visible and CREATE grant exists, to avoid leaking definition binding state. No alias, latest-version fallback or binding inference. | **OWNER APPROVED** ([record](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6280476807)); confirmed-domain-error equivalence and production SQL enforcement still BLOCKED. |

These two rules are now **explicit owner-approved product semantics**, matching the previously reviewed Stage-5 application-policy candidate. Yến's earlier Stage-5 PASS did not itself ratify them. This approval does not ratify V5 DDL, cryptography, PostgreSQL runtime privilege or Workflow HTTP.

**D2 refinement against source HEAD `e3343de` (2026-10-20):** the following are review proposals, not additional owner ratification. Unmapped or inconsistent historical rows **fail closed**; no automatic backfill that guesses a tenant.

| ID | Proposed refinement | Decision status |
|---|---|---|
| DEC-D2-01 | Separate new-resource registration from legacy reconciliation; use a reviewed authoritative manifest, exact inventory validation and a fenced legacy-writer cutover (§3 and companion DB1b). | **PENDING OWNER / PostgreSQL REVIEW** |
| DEC-D2-02 | Enforce permanent tenant/resource keys, no transfer/reuse/resurrection through constraints, triggers, restricted privileges and narrow mutation protocol (companion DB1a). | Product no-transfer/no-reuse rule already approved; **physical enforcement PENDING PostgreSQL REVIEW** |
| DEC-D2-03 | Deactivate membership and revoke every positive grant atomically; reactivate without restoring grants; reassign ACTIVE-resource ownership to active same-tenant members or reject deactivation; retain durable audit (companion DB2a). | **PENDING OWNER / YẾN / PostgreSQL REVIEW** |
| DEC-D2-04 | CREATE order: authenticated identity → reliable source → resource visibility → explicit CREATE grant + exact JWT authority → exact active definition binding (§10). | **OWNER DESIGN DIRECTION RECORDED** in [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6288471117); **YẾN RE-REVIEW PENDING**. Mixed-failure sequencing differs from Stage 5; no Java/DB implementation authorized. |

Global resource namespace compatibility, physical audit storage and the above approvals remain open. **Tenant-disable has no approved physical lifecycle; propose DEFER from MVP v1 unless a demonstrated business requirement justifies reopening its design.** No tenant status column, disable mutator or schema change is implied. DEC-VIS / DEC-CREATE ratification does **not** settle DEC-D2-04's ordering when failures overlap.

## 3. Proposed V5 physical relations (schema contract; NOT executable DDL)

Use the existing private Workflow Flyway schema **candidate**, not `public`, and keep V1–V4 byte/checksum immutable. Definitions and instances continue under existing V3/V4. Proposed *five relations*, with actual DDL/FKs/indexes/collation still subject to review:

| Relation | Keys and facts | Required invariant |
|---|---|---|
| `tenant_guards` | `tenant_id` PK; nonnegative monotonic `acl_revision` | Permanent, never deleted/recreated or ID reused; all protected operations take guard `FOR SHARE`, all policy mutators `FOR UPDATE`. |
| `tenant_memberships` | `(tenant_id, actor_id)` PK, active flag; FK tenant guard | Exact canonical OIDC actor; proposed deactivation sets **all** positive grants false in the same transaction; reactivation restores membership only, never old grants. |
| `resource_registry` | Global PK `(resource_type, resource_id)`, tenant FK, owner metadata, ACTIVE/TOMBSTONED; unique tenant/resource composite | Resource-to-tenant binding and keys immutable; tombstone one-way, no resurrection/delete/reuse; owner is *not* a grant. |
| `resource_acl` | `(tenant_id, resource_type, resource_id, actor_id, operation)` PK; FKs registry+membership | Only four exact `workflow:*` operation values; positive current grant distinct from mere owner/authority. Revoke/membership mutations must share tenant guard + audit. |
| `workflow_definition_bindings` | `(tenant_id, resource_type, definition_id, definition_version)` PK; FK guard+V3 exact definition; active | CREATE requires exact active binding; deactivation does not invalidate existing pinned instances alone. |

**DDL review requirements:** exact constraints for permanent tenant/resource keys and tombstone, SQL `COLLATE "C"` vs V3 PostgreSQL collations/FK operator semantics, composite tenant equality, owner lifecycle, no TRUNCATE/direct trigger bypass, default privileges and migration rollback policy. A normal FK alone cannot enforce irreversible tombstone or prove guard locking. If existing V3 instances have no authoritative registry entry, do not expose them or guess ownership. A candidate V5 must not modify V3 history or alter its immutable snapshot semantics.

**Provision/admin mutators to enumerate** (no generic policy SQL executor): tenant creation; controlled manifest-approved NEW/LEGACY resource provisioning, tombstone, owner change; membership grant/deactivate; resource ACL grant/revoke; exact definition binding grant/revoke; definition provisioning. Tenant-disable is proposed DEFER, not part of this mutator list. Every mutator, including batch jobs/backfills/admin operations, must take the same permanent tenant guard `FOR UPDATE` *before child rows* and increment revision, audit and commit on that same connection. Never allow an online runtime user to directly DML those tables or `SET ROLE` into owner/migrator.

### D2 provisioning and lifecycle refinements — proposed, not implemented

- **New registration is not legacy adoption.** MVP v1 does **not** open arbitrary online resource registration: both NEW and LEGACY provisioning require the controlled manifest/approval process, and the authoritative source of tenant/resource/owner facts still needs evidence and review. A fresh NEW registration must prove the global key is unused in both registry and V3 inventory under a reviewed writer fence; discovering legacy instances rejects that route. Only separately authorized LEGACY reconciliation may associate those instances with a tenant. A tenant guard alone cannot serialize the current unguarded V3 writer. Fence/drain old writers, compare inventory again and retain the fence through coordinated application deployment, privilege verification and post-deployment checks; drift aborts for renewed review. Application deployment and DB privilege changes are **not one atomic transaction**; reviewed rollback/recovery must retain the fence and prevent unguarded access. No online backfill or guessing from Request assignee, instance/history actor, payload or token tenant. See companion **DB1b**.
- **Permanent identity needs multiple controls.** PK/UNIQUE, restrictive composite FKs and lifecycle CHECKs establish shape; triggers reject key/tenant UPDATE, DELETE and tombstone reversal; online roles cannot TRUNCATE, disable/bypass triggers or enter owner/migrator roles. Narrow mutators recheck target and guard before updating permitted fields. No cascade, delete/reinsert, UPSERT remapping or restore of tombstones; owner change stays within the immutable tenant. Guard bootstrap is a separately reviewed provisioning exception because no guard exists yet. See **DB1a**.
- **Membership lifecycle is destructive revocation, not temporary masking.** Proposed deactivate revokes positive ACL rows (`granted=false`), resolves ACTIVE-resource ownership, changes membership and writes revision + durable audit in one guarded transaction. Reactivation creates no grants; every later grant is a new explicit audited decision. Owner visibility may return on reactivation if the actor is explicitly reassigned ownership, but ownership never grants an operation. Audit storage/append-only privileges are a D2/D3 prerequisite; application logs or V3 transition history are insufficient. See **DB2a**.

## 4. SQL trust and privilege boundary

**Threat model to preserve until explicitly revised:** an attacker with only the online PostgreSQL `dxos_wf_runtime` password and arbitrary SQL must be unable to SELECT/DML protected V3/V5 tables or forge another actor/action/target through callable functions. A compromised trusted Java signer or SQL owner/superuser is outside that narrower claim.

Candidate roles, mutually separated:
- `dxos_wf_owner` NOLOGIN owns trusted SQL functions and protected schema/tables.
- `dxos_wf_migrator` deployment-only DDL; not used by the HTTP connection pool.
- `dxos_wf_runtime` LOGIN only schema USAGE + exact vetted `wf_port_*` EXECUTE; **no protected table SELECT/DML/ALTER/TRUNCATE, SQL schema CREATE, owner/role inheritance, helper EXECUTE, BYPASSRLS, or key access**.
- `dxos_wf_policy_admin` invokes only authenticated and guard-locking `wf_policy_*` allowlist.
- `dxos_wf_provisioner` invokes only authenticated provisioning allowlist.
- `PUBLIC`, Supabase API roles and unrelated Request roles have **no** protected function or table privileges.

Harden each `SECURITY DEFINER`: fixed search path `pg_catalog,pg_temp`; fully qualified trusted references; no attacker-controlled dynamic SQL; owner non-login and non-inheritable; REVOKE PUBLIC EXECUTE **within function-creation transaction**, audit existing/default grants and role inheritance, test with actual runtime LOGIN. Switching grants before the guarded adapter exists would break current V3 `PostgresWorkflowPersistence`; plan and test the coordinated fenced deployment, effective-privilege checks, rollback/recovery and post-deployment verification described in DB1b in an isolated database first. Transactional DB grants do not make application deployment atomic with those grants.

### Caller authenticity at SQL boundary — **open independent cryptography gate**

A Java `AuthenticatedPrincipal` constructor, free-form SQL `actor_id`, caller-controlled tenant, JWT string parameter, or custom GUC is **not** DB-verifiable authentication. ADR-0024 proposes a **per-physical-transaction HMAC-SHA-256** envelope minted only after `CurrentPrincipal.requireVerified()`. It cryptographically binds canonical UTF-8 issuer+subject, actor, exact authority, port action, target/definition/proposal, backend PID + PostgreSQL transaction ID, deployment/key-id, timestamp and nonce.

One port invocation => one fresh checked JDBC connection/transaction => **new proof**; never reuse a proof from preliminary read for final write. Each vetted SQL function verifies proof and full typed arguments **before** protected materialization, then *after guard lock* rechecks live ACL. An SQL runtime credential alone cannot mint valid proof **only if** distinct key custody/least privilege and verifier implementation actually hold. **P0 OPEN:** exact cross-language strict UTF-8 vector, key custody/rotation, MAC comparison timing, nonce/replay, expiry under lock waits, transaction-pooler backend affinity, SQL function privilege and grant evidence. The isolated PR #36 HMAC fixture and six-port V3 parity fixture are separate and do not prove this combined path. The owner has now **selected the weaker trusted-backend MVP threat model** ([Issue #23 decision](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6289594814), [ADR-0025](../adr/ADR-0025-proposed-mvp-trusted-backend-threat-model.md)), explicitly giving up DB-credential-compromise actor-spoof resistance **for MVP scope only**. Separate Identity/PostgreSQL/security review and effective SQL grants are still blocking before any implementation. The preceding HMAC envelope describes **deferred ADR-0024 stronger profile**, not a requirement to implement HMAC/PID/XID in the MVP. Never claim SQL actor authenticity under Option B.

## 5. Durable JDBC authorization — ADR-0023 Option A (binding)

**One physical JDBC transaction PER protected port call.** The public `WorkflowPersistencePort` and `WorkflowRuntime` Core contracts remain provider-neutral. A multi-step Runtime call is **NOT one global transaction**; its preliminary authorized reads and final durable write may use distinct connections. The *final write-port method* must re-authorize after locking, perform state/history mutation and complete durable COMMIT on **its own same physical connection**. Never implement an outer `@Transactional` that defers the port's durable success.

Proposed operation protocol (pseudocode, NOT implementation):

```text
VerifiedIdentity -> operation/target scope (non-forgeable application boundary)
  begin checked Connection C for ONE port method:
    READ COMMITTED; autoCommit=false; lock-capable, bounded timeouts
    bind fresh per-C actual transaction actor/action/target proof
    read only minimal PRIVATE target->candidate tenant locator
    SELECT permanent tenant guard FOR SHARE
    RE-READ current resource/tenant key, membership, visibility, explicit ACL,
      exact authority and (if CREATE) exact active definition binding
    if missing/changed/forbidden/unavailable: fail CLOSED, confirm rollback
    now and only now materialize target or (for writes) lock instance FOR UPDATE
    verify complete version/lifecycle/proposal and canonical actor
    mutate snapshot/history atomically (WRITE only)
    COMMIT via C (WRITE); success only after CONFIRMED durable COMMIT
  close C after transaction completes
```

**Lock ordering** across Workflow and policy admin: tenant guard -> resource -> instance -> history, including implicit FK/trigger locks. No protected snapshot/history materialized before final post-guard check. Guard must remain held until confirmed COMMIT/ROLLBACK. PostgreSQL `READ COMMITTED` and recheck **after** waiting for guard avoids stale membership/ACL. Guarded READ/HISTORY needs lock-capable `READ COMMITTED` transaction (`setReadOnly(false)` at JDBC transaction level for `SELECT ... FOR SHARE`), with *application/SQL-role privileges* still prohibiting writes; read materialization uses that single coherent guard transaction. Never roll back to a savepoint created before lock acquisition and continue believing guard remains held.

### Port-by-port enforcement map

| Existing Java method | Bound scope and guarded SQL function candidate | Transaction notes |
|---|---|---|
| `loadDefinition(id,version)` | `wf_port_load_definition`; only exact definition pinned to authenticated CREATE/ACTIVATE/TRANSITION target; not a public list | Guarded READ under originating operation scope; CREATE additionally verifies active binding. |
| `loadInstance(id)` | `wf_port_load_instance`; public SNAPSHOT requires READ; internal ACTIVATE/TRANSITION may read under their own exact scope | Guarded READ; **never called in secured C3 CREATE** even as collision probe. |
| `loadHistory(id)` | `wf_port_load_history`; READ only | Guarded READ and history materialization with same snapshot/lock; zero history must not reveal hidden instance. |
| `createInstance(initial)` | `wf_port_create_instance`; CREATE, generated ID + exact resource/definition | Final ACL/binding recheck + global unique INSERT + durable COMMIT within same port call. Do not read existing collision target. |
| `commitActivation(expected, activated)` | `wf_port_commit_activation`; ACTIVATE scope for exact instance | Recheck guard/ACL + full previous snapshot/current version/activation, durable COMMIT. |
| `commitTransition(result)` | `wf_port_commit_transition`; TRANSITION scope, verified actor and exact proposal/version | Re-derive canonical transition/snapshot/history, atomic state + *one* record COMMIT, never accept client actor. |

`loadDefinition`, `loadInstance` and `loadHistory` have independently completed guarded read transactions. `createInstance`, `commitActivation` and `commitTransition` each own durable write transaction and committed result. Any `OperationScope` must bind target and method; no generic arbitrary `Function<WorkflowRuntime,T>` callback or unbound SQL function capable of using proof for resource A against B.

**Concurrency contract:** (a) revoke `FOR UPDATE` commits before guarded final `FOR SHARE`: operation rechecks revised ACL and writes nothing; (b) operation obtains `FOR SHARE` first: revoke waits until operation's commit/rollback. A revoke between pre-read and write is caught by the **final** port guard. All owner/membership/grant/binding/tombstone mutators must take the same guard lock or this guarantee fails. The Stage 5 immutable ACL snapshot is only preliminary application policy, not this transaction lock.

## 6. CREATE C3 and error classification

Secured CREATE **must not invoke** `WorkflowRuntime.createInstance` because its existing duplicate-ID probe calls `loadInstance(generatedId)`, which can expose a hidden existing row. In the trusted application boundary, generate server-owned opaque ID from PR #36's `WorkflowInstanceIdGenerator` (192-bit random), guard-load only exact authorized definition, construct `WorkflowInstance.notStarted` and call `createInstance(initial)` through a narrowly scoped guarded port. Domain Core and public port method signatures remain unchanged. Require separate equivalence/security tests for re-ordered duplicate-vs-definition errors; **do not** wire current Stage 3 `createInstance(instanceId,...)` to production HTTP.

**Error classes:** missing/hidden/foreign/nonmember/tombstoned/unmapped -> HIDDEN (eventual indistinguishable 404), visible missing operation ACL/authority -> FORBIDDEN (eventual 403), null/timeout/DB/proof verification failure -> fail-closed UNAVAILABLE (candidate sanitized 503, not a finalized HTTP API contract). CREATE mixed-failure **ordering remains pending DEC-D2-04 in §10**. On a *known* rollback, the infrastructure bridge transports typed outcomes; existing `PostgresWorkflowPersistence.transaction()` would otherwise wrap a bare `WorkflowAccessException` as `OPERATION_FAILURE`. Must preserve canonical Core failure precedence for authorized callers without releasing state-dependent domain errors after revocation: do a new guarded read-only classification for such errors, **not a write retry**.

**Commit uncertainty dominates:** if `commit()` was attempted but the outcome is unknown, or failed rollback leaves possible commitment, return `COMMIT_OUTCOME_UNKNOWN` and never auto-retry, even when an earlier ACL or domain error occurred. Preserve transition+history atomicity and exact nanosecond timestamp fields. Generated-ID collision on confirmed no-commit is internal sanitized denial; no hidden ID/snapshot or leaked SQLSTATE. Lost-response/idempotency and unknown-commit HTTP semantics remain separate P0 approval gates.

## 7. S6.1 review gates (DESIGN acceptance only)

> **Response to Yến's [S6.1 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6280356582):** all eight requested items are **explicitly dispositioned** below; a proposed baseline is not equivalent to independent SQL/security sign-off. See [Physical V5 / SQL role / caller-proof review baseline](issue-23-stage6-s61-v5-physical-privilege-review.md) for detailed DB1/DB2 and SQL-A/SQL-B review tables. This document is the normative architecture request; code execution is still blocked.

- **S6.1-D1:** owner-ratified Stage 5 public rules DEC-VIS / DEC-CREATE recorded in Issue #23; physical SQL/HTTP proof remains a distinct gate.
- **S6.1-D2:** reviewed schema physical DDL draft includes FK/collation, immutable tenant/resource/tombstone, legacy V3 fail-closed reconciliation and V1–V4 manifest compatibility.
- **S6.1-D3:** runtime/admin/provisioner/migrator/owner SQL role and exact function allowlists reviewed; no table/function or credential bypass and no premature privilege switchover.
- **S6.1-D4:** owner chose ADR-0025 Option B for MVP: HMAC/PID/XID and signer-key protocol deferred, stolen-runtime-DB-password actor-forgery resistance explicitly NOT CLAIMED. Independent Identity/PostgreSQL/security review of the narrower trusted-backend assumption, grants and effective protection remains blocking; ADR-0024 is not silently superseded.
- **S6.1-D5:** each write port's **own** verified COMMIT after final guard and mutation, READ/HISTORY guard coherence, all mutators `FOR UPDATE`, no savepoint bypass, fresh proof for every physical transaction.
- **S6.1-D6:** HIDDEN/FORBIDDEN/UNAVAILABLE typed bridge and `COMMIT_OUTCOME_UNKNOWN` precedence documented, plus ADR-0024 C3 CREATE and lost response.
- **S6.1-D7:** isolated PG17 two-connection, role/privilege and negative/fault test plan reviewed; exact HEAD and scope identified; **no claim of tests at this design HEAD**.
- **S6.1-D8:** no code, SQL migration, DB/staging operations, HTTP registration or Core signature modifications. This design-only change is ready for *architectural feedback*, not Stage-6-wide PASS.

### Specific review requests

**Yến:** please independently review compatibility with verified Identity/canonical issuer+subject UTF-8 and exact role mapping, owner-ratified Stage 5 visibility/denial rules, operation scope/delegation and error priorities. Yến's review alone is **not** PostgreSQL/cryptography sign-off.

**Bảo / PostgreSQL-security competent reviewer:** confirm the already recorded owner DEC-VIS and DEC-CREATE decisions are correctly carried into SQL; independently review V5 DDL FK/collation/migration path, all SQL privileges and actor-proof threat model, tenant-lock/savepoint semantics, rollback/COMMIT ambiguity, privilege revocation sequence, and actual disposable-PG17 evidence before S6.2 deployment. If only owner-led self-review occurs, label it as such and retain any P0 without independent security evidence.

**Current decision: S6.1 REVIEW DRAFT; S6.2 IMPLEMENTATION BLOCKED until designated gates are approved.**


## 8. S6.1 re-review addendum — eight findings and exact dispositions

**Updated 2026-10-20 in response to Yến's PR #50 review at old HEAD ae5efb0.** The decision labels below distinguish accepted product/architectural requirements from blocked physical security gates; none is a claim that Stage 6 SQL implementation passes.

| Gate | Resolution / artifact | Disposition at new HEAD | Responsible decision / review authority |
|---|---|---|---|
| **D1** — Stage 5 DEC-VIS / DEC-CREATE | Both ratified in [owner's Issue #23 decision](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6280476807); hidden/forbidden criteria frozen below | **ACCEPTED — product rules only** | Bảo owner; Yến checks Identity/application consistency |
| **D2** — V5 physical model | Five-table PK/FK/UNIQUE/CHECK, immutable resource/tenant/tombstone, private schema, V3 reconciliation and collation review baseline in [DB design file](issue-23-stage6-s61-v5-physical-privilege-review.md) | **BLOCKED — pending DB/DDL compatibility sign-off and disposable PG17 tests** | Bảo implementation owner + independent PostgreSQL reviewer |
| **D3** — SQL privilege model | Owner/migrator/runtime/policy-admin/provisioner/PUBLIC explicit effective role matrix and function restrictions in [SQL-A matrix](issue-23-stage6-s61-v5-physical-privilege-review.md) | **BLOCKED — privileges are proposed, not implemented/audited** | Independent PostgreSQL/security reviewer; Bảo integrates |
| **D4** — SQL actor trust boundary | [Owner selects ADR-0025 Option B for OLP MVP](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6289594814): trusted backend, no HMAC/PID/XID, no DB-independent actor attestation; explicit accepted scope risk if stolen runtime DB credential + DB access enables actor spoof | **OWNER DECIDED — independent Identity/PG security acceptance BLOCKED (not PASS)**; stronger ADR-0024 test profile deferred, not silently marked PASS | Bảo scope decision; Yến Identity review; independent PostgreSQL/security reviewer |
| **D5** — final guarded transaction invariants | MUST/MUST NOT requirements frozen in §9 below, ADR-0023 Option A | **ACCEPTED — architecture contract only; implementation and concurrency evidence BLOCKED** | Bảo + independent PostgreSQL/concurrency reviewer |
| **D6** — failure precedence, C3 CREATE | §10 retains error classes and COMMIT_OUTCOME_UNKNOWN; contrasts old baseline with owner-selected DEC-D2-04 direction | **OWNER DIRECTION RECORDED; YẾN/SECURITY RE-REVIEW PENDING; JDBC/HTTP fault evidence BLOCKED** | Bảo + Yến on HTTP/Identity + independent DB reviewer |
| **D7** — PG17 evidence | [Test matrix](../verification/issue-23-stage6-pg17-acceptance-matrix.md) enumerates two-connection barrier, role/proof negatives, fault injection and exact JUnit evidence | **DEFERRED to S6.2–S6.5 after physical design approval; no Stage 6 test PASS** | Bảo executor, independent DB reviewer |
| **D8** — scope and safety | This PR updates **documentation only**. No V5 executable migration, V1–V4 edit, PostgreSQL role/secret/live DB change, production HTTP registration, or merge | **ACCEPTED — process boundary at this HEAD** | Bảo, verified by GitHub diff |

**S6.1 overall status remains NOT YET PASS** until D2/D3/D4 physical security architecture and relevant design sign-offs are obtained. D7 execution is deferred by agreement: Yến explicitly did **not** request running PG17 during this documentation-only review. **S6.2 is BLOCKED**, and no GitHub comment/owner decision overrides these gates.

## 9. Normative transaction invariants — MUST / MUST NOT

1. **MUST** use the verified Identity actor (issuer+subject) and exact Workflow authority from `CurrentPrincipal.requireVerified()`; **MUST NOT** trust client actor/tenant/role, JWT parsed a second time, SQL actor string/GUC as principal proof.
2. **MUST** start each protected port method in a checked **new physical JDBC connection transaction** (READ COMMITTED, autoCommit=false, finite configured deadlines). Each transaction MUST have its own transaction-bound actor/action/target proof, independently verifiable by trusted SQL. A request may have multiple such transactions; **MUST NOT** reuse a proof from a preliminary Runtime read for a later final write.
3. **MUST** resolve minimal private target->candidate tenant without disclosing protected snapshot/history; lock permanent tenant guard `FOR SHARE`, then **re-read current authoritative** resource binding, active membership, visibility, operation grant, exact JWT authority and exact CREATE binding if applicable. A changed tenant/target, missing guard, timeout or verifier fault **MUST fail closed**; **MUST NOT** continue based on a stale earlier snapshot.
4. **MUST** perform the **final** ACL recheck, instance row lock/version/lifecycle/proposal revalidation, state mutation, history append and confirmed durable **COMMIT all within the same physical JDBC transaction** of the final `createInstance`, `commitActivation` or `commitTransition` port invocation. Never return port write success before confirmed COMMIT. **MUST NOT** substitute an outer Spring `@Transactional` that permits deferred commit or blind retries.
5. Every grant/revoke, membership, owner, binding, tombstone, provisioning/admin/batch mutation **MUST** take the same tenant guard `FOR UPDATE` before dependent rows; revision and audit + COMMIT MUST be atomic. **MUST NOT** permit direct runtime SQL DML bypass or a savepoint rollback that releases guard then continues without full reacquisition/revalidation.
6. READ and HISTORY **MUST** use a separately completed coherent guarded `READ COMMITTED` transaction with lock-capable `FOR SHARE` before protected materialization, and release only authorized detached results. An internal ACTIVATE/TRANSITION preliminary instance read is restricted by **its own operation scope**, not automatically a public READ grant.
7. **MUST** keep the port Core semantics/returned confirmed-write durability, actor provenance, error equivalence for authorized requests, timestamp nanos and transition+history atomicity. **MUST NOT** expose a generic target-swapping callback or expose `WorkflowRuntime.createInstance` collision probe in secured CREATE HTTP.
8. **MUST** lock in deterministic tenant guard -> resource -> instance -> history order (including FK/trigger effects), with no unreviewed online superuser/owner access; demonstrate revoke-first and writer-first with two actual physical PG17 connections before production.
9. The production WorkflowController **MUST NOT** be registered, and no V5 SQL privilege cutover/migration is authorized, until the actual SQL-A/SQL-B, TX-A, API-A and HTTP gates pass. No P0 can be waived merely because earlier mock Java tests passed.

## 10. Error classification — baseline and pending CREATE precedence

The classes below document the existing design baseline. The owner selected DEC-D2-04 Option 2 and Yến **AGREED to its semantics** at [PR #50 review comment](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289261865). However, Yến confirmed that the **orchestration/data-access compatibility contract is still OPEN**, and Stage-5 source/test changes and SQL security sign-off remain pending. The prior §10 rule “hidden existence/binding before authority denial” conflicts with parts of Stage 5 source and with DEC-D2-04; the comparison below makes that conflict explicit rather than silently replacing an approved rule.

- **Authn:** absent/expired/forged/wrong issuer-or-audience JWT -> authentication failure (intended safe HTTP **401**) before any ACL lookup; exact JWT authority alone cannot grant resource permission.
- **UNAVAILABLE** (fail-closed): provider/ACL source null, throws, timeout, unverifiable SQL proof, actor/resource mismatch, DB availability fault, or lock uncertainty. No ALLOWED fallback. Candidate sanitized HTTP 503 is **not yet signed-off**.
- **HIDDEN**: missing/foreign/inactive-member/tombstoned/unmapped legacy V3/nonvisible resource, or approved DEC-CREATE absent/inactive wrong exact binding. No protected materialization; eventual external response must be indistinguishable from nonexistent resource (**404** same body/headers/cache policy).
- **FORBIDDEN**: caller has resource visibility (approved DEC-VIS) but lacks requested explicit resource operation ACL **or** exact verified operation authority, once source is reliable and lookup done. Proposed eventual **403**.
- **Authorized domain errors** (VERSION_CONFLICT, INVALID_TRANSITION, etc.) may only be returned after it is safe to release state-dependent details. If a preliminary read error occurs after ACL revocation, MUST perform a *fresh separately guarded classification* before returning; **MUST NOT retry the write**.
- **COMMIT_OUTCOME_UNKNOWN overrides all preceding policy/domain classifications** if a write COMMIT was attempted with uncertain outcome or rollback failure leaves the write possibly committed. **MUST NOT** automatically retry, mark a certain 403/404, or fabricate a success. The exact public HTTP/idempotency/reconciliation contract remains **BLOCKED** pending explicit approval.
- **Known-rollback generated CREATE collision:** classify as a sanitized internal collision, never disclose colliding instance ID or probe hidden data. Secured C3 CREATE generates its own ID and bypasses Runtime CREATE collision probe. Lost-response behavior must be explicitly approved before production.

**Prior §10 baseline, superseded for proposed CREATE decision-order design only once D2-04 re-review passes:** when no write may have committed, provider/proof uncertainty => UNAVAILABLE; otherwise hidden existence/binding before authority denial; visible caller's absent grant/authority => FORBIDDEN; only positively authorized operations may surface safe domain results. **After an attempted write whose COMMIT may be unknown, COMMIT_OUTCOME_UNKNOWN always dominates.**

### DEC-D2-04 — OPTION 2 SEMANTICS AGREEMENT (YẾN); DATA-ACCESS CONTRACT STILL OPEN

Owner-selected CREATE decision order **for design review**, not yet the deployed policy (known no-commit only):

1. **Authenticated identity:** require verified canonical caller first; authentication failure precedes ACL lookup. Possession of an identity does not imply exact `workflow:create` authority.
2. **Reliable source:** establish trustworthy actor/resource-scoped facts and SQL proof. Null/exception/timeout, unverifiable proof or inconsistent actor/target -> UNAVAILABLE, never convert an unknown result into missing/forbidden. A confirmed missing resource is a reliable negative, not an outage.
3. **Resource visibility:** missing, foreign, inactive membership, tombstoned, unresolved legacy or DEC-VIS nonvisible -> HIDDEN, irrespective of grants/authority/binding. Resolve tenant only from authoritative registry.
4. **Explicit CREATE grant AND exact verified `workflow:create` authority:** on a visible resource either absent -> FORBIDDEN, independently of whether a definition binding exists. Do not inspect/materialize definition or binding to classify this denial. No role alias or owner shortcut.
5. **Exact active definition binding:** only after step 4 passes, missing/inactive/wrong exact tuple -> HIDDEN per DEC-CREATE; a reliable positive permits guarded exact definition loading/domain validation. A required lookup failure remains UNAVAILABLE; no alias/latest fallback or unguarded definition-first probe.

This order governs classification and protected disclosure, not permission to bypass proof validation. D4 must reconcile authenticated-but-missing-authority denials with ADR-0024's authority-bound capability design; never sign a false authority or return early 403 that reveals a hidden resource. Repeat current facts after guard acquisition for every port, including final CREATE. No blanket promise of one global request transaction, no write retry and no change to unknown-COMMIT priority.

**Source comparison at `e3343de`:** `AuthorizedWorkflowService.createInstance` verifies caller, loads the definition **before** ACL evaluation (missing definition -> HIDDEN; lookup fault -> UNAVAILABLE), then calls authorization. `TenantResourceWorkflowAccessPolicy.forCreation` checks reliable facts, resource visibility and explicit CREATE grant **before** binding. Only if that whole policy returns ALLOWED does `WorkflowAuthorization.authorize` check exact JWT authority. Thus Stage 5 is **definition lookup → source → visibility → grant → binding → JWT authority**, after authentication. Existing Stage 5 tests cover separate denials, not proof that all mixed failures follow the proposed order. No Stage 5 Java/test or policy-document edit is authorized here.

For a reliable source and an existing exact definition:

| Overlapping CREATE conditions | Stage 5 source today | Prior §10 literal order | DEC-D2-04 proposal |
|---|---|---|---|
| No CREATE grant; binding absent; JWT authority present | FORBIDDEN (grant first) | HIDDEN (binding first) | FORBIDDEN; do not inspect binding |
| CREATE grant present; JWT authority absent; binding absent | HIDDEN (binding before JWT) | HIDDEN | FORBIDDEN; do not inspect binding |
| CREATE grant + JWT authority present; binding absent/inactive/wrong version | HIDDEN | HIDDEN | HIDDEN (DEC-CREATE retained) |
| Visible; binding active; either grant or JWT authority absent | FORBIDDEN | FORBIDDEN | FORBIDDEN |
| Resource nonvisible; grant/authority/binding also absent | HIDDEN | HIDDEN | HIDDEN; no binding/definition disclosure |

DEC-D2-04 removes the binding-dependent HIDDEN/FORBIDDEN difference for a visible caller lacking JWT CREATE authority and aligns grant-first handling with Stage 5. It changes the prior §10 binding-first wording and moves Stage 5's initial definition lookup behind resource/operation authorization: missing definition can no longer mask a visible grant/authority denial. ADR-0024 C3 §1 step 1 also proposes checking exact authority upfront; this proposal defers its denial until visibility, so Yến/owner must explicitly reconcile that wording while preserving C3's no-collision-probe and unchanged Core contracts. DEC-CREATE's positive-grant-and-authority/missing-binding HIDDEN rule and DEC-VIS remain intact; their prior approval is **not** approval of this new precedence. Public 401/403/404/503 equivalence, source/domain error combinations and the capability/error bridge still need review and PG17/HTTP evidence.

## 11. Change boundary and next review

This refinement changes only the three existing Stage 6.1 documents against source HEAD `e3343de`. Owner ratification of DEC-VIS/DEC-CREATE affects product/error policy, not credentials or SQL approval; DEC-D2-01/03 remain proposals and **DEC-D2-04 has owner direction but Yến/security re-review is pending**. The companion baseline supplies constraints/triggers, manifest reconciliation, membership mutation and role review requirements but **does not implement SQL or establish independent security sign-off**. Stop for **Technical Lead review**, then owner/Yến and independent PostgreSQL/cryptography review of D2/D3/D4. **D2/D3/D4 and S6.2 remain BLOCKED.** No Java/SQL/Flyway or V1–V4 change, V5 creation, database/Keycloak access, Workflow HTTP activation, PR merge or Stage 6.2 work is authorized. Documentation-only commits to this Draft PR are permitted.


## 12. Owner decision / MVP simplification review handoff (2026-10-20)

**Owner record:** [Issue #23 comment](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6288471117). This section is the **owner-selected D2-04 design direction**, not Yến's review and not PostgreSQL/security sign-off. The owner-selected ordering has Yến's **semantics agreement**, but cannot be marked D2-04 RESOLVED until the source/data-access contract explicitly guarantees no binding/definition materialization before CREATE grant + exact JWT authority and an interaction-level test specification is reviewed. DEC-VIS, DEC-CREATE, ADR-0023 Option A and all S6.1 SQL safety stops continue unchanged.

### D2-04 denial truth table — reliable source, no attempted write

| Verified authn | Resource visible under DEC-VIS | Explicit CREATE grant | Exact verified workflow:create authority | Active exact binding | Safe classification / lookup obligation |
|---|---|---|---|---|---|
| No | Any | Any | Any | Any | 401 before protected ACL/binding lookup |
| Yes | Unknown because source/proof failed | Any | Any | Any | UNAVAILABLE (sanitized), no synthetic HIDDEN/403 |
| Yes | No (foreign, nonmember, tombstoned, unmapped or no visibility) | Any | Any | Any | HIDDEN/404 with externally equivalent hidden/missing response; NO binding/definition read |
| Yes | Yes | No | Yes or No | Present or absent | FORBIDDEN/403; no binding/definition read |
| Yes | Yes | Yes | No | Present or absent | FORBIDDEN/403; no binding/definition read |
| Yes | Yes | Yes | Yes | Missing/inactive/wrong ID/version | HIDDEN/404; exact lookup occurs only now |
| Yes | Yes | Yes | Yes | Present/active/exact | Proceed to guarded exact definition/domain validation; write only after fresh post-guard authorization and durable commit |

**Cross-branch failure precedence:** a source/proof outage returns UNAVAILABLE only when the reliable minimum needed to classify the request is unavailable; never query binding prematurely and turn its outage into UNAVAILABLE for unauthorized callers. On a write with possibly unknown COMMIT, COMMIT_OUTCOME_UNKNOWN always takes precedence and no blind retry is permitted. HIDDEN/403/404 equivalence must include body, headers/cache behavior and no secret-dependent materialization. A matching 403 alone **does not prove** that binding was not looked up.

### Required Stage 5 and C3 discrepancy review (NO Java edits in S6.1)

- At Stage 5 [PR #49 HEAD 32ca73fa](https://github.com/lexuanbao258-collab/dxos-open-core/pull/49), the service preloads the definition, the resource policy checks grant then binding, and only later the authorization wrapper checks JWT authority. Mixed denials can depend on binding presence and definition lookup. **Do not mark this table implemented**, nor use old single-negative tests as mixed-denial evidence.
- Before coding the policy change, add a separate reviewed Stage 5 implementation plan for an authentication/visibility/CREATE grant/authority-first classification; only a fully authorized actor can cause exact definition/binding materialization. Preserve negative behavior of hidden/foreign resources and never permit unverified proof as a shortcut. Explicitly reconcile ADR-0024 C3 section 1's eager exact-authority check and actor-capability signing sequence; any inability to obtain reliable SQL proof must fail closed without leaking hidden resource existence.
- Tests must use an instrumented reader/spy to assert **zero binding or definition lookups** for unauthorized actors and hidden resources, in addition to comparing public denials. Cover at least grant×authority×binding cross-product (2×2×active/inactive/missing/wrong-version), visible/nonvisible, source unavailable, target mismatch, proof failure, and post-lock revoke races. Only after acceptance may implementation and HTTP/PG17 tests be scheduled.

### D3 simplification proposal — no loss of permission boundary

Keep one closed SQL permission surface for the actual Core persistence ports (currently six load/write operations), provided exact signatures and protected READ/HISTORY are justified. **Reduce redundant administration/provisioning helper count only after** enumerating all necessary membership deactivation, owner reassignment, grant/binding/tombstone, legacy manifest/recovery and atomic audit mutations. No fixed function count is an MVP requirement, but no externally callable helper may have broader grants than its reviewed operation. Runtime direct V3/V5 DML, PUBLIC/default EXECUTE, role inheritance/SET ROLE escalation, arbitrary SQL and incomplete audit stay prohibited. Any simplification is a **review proposal**, not permission to weaken the current SQL role matrix.

### D4 threat-model decision — OPTION B OWNER-SELECTED; SECURITY SIGN-OFF PENDING

See [ADR-0025 OWNER-SELECTED Option B](../adr/ADR-0025-proposed-mvp-trusted-backend-threat-model.md) and [Issue #23 owner decision](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6289594814). For the student MVP the owner explicitly **defers HMAC/PID/XID, nonce/replay and signing-key rotation**, accepting that theft of runtime DB credentials **plus DB network access** may enable impersonation through allowed SQL functions. OIDC verified at the backend, exact Workflow ACL/authority and least-privilege PostgreSQL permissions, post-lock transaction discipline, C3 and error/privacy safeguards remain mandatory. **The owner scope choice is final, but Identity/PostgreSQL/security review of that weaker trust boundary is still BLOCKED; D4 is NOT PASS.** ADR-0024 stronger HMAC claim is not formally superseded without such review. Do not claim DB-verifiable SQL actor provenance or turn deferred cryptographic tests into PASS.

**Exit conditions:** Yến has agreed to **Option 2 semantics only** at HEAD `fb53f6f57b6326c36e31d706dfcf0eebb88d9899`; source/data-access contract requires exact-HEAD re-review after this addendum, while D4 Option B has been owner-selected but awaits independent Identity/PostgreSQL/security acceptance; independent DB reviewer examines D2 physical invariants, D3 effective grants and D4 risk. Stage 6.1 remains **NOT PASS**, Stage 6.2 **BLOCKED**, PR #50 Draft. No productive SQL, Flyway V5, credential/role changes, public HTTP, or merge.


## 13. DEC-D2-04 Option 2: binding/definition non-materialization contract (2026-10-20)

**Reviewer finding (exact HEAD `fb53f6f57b6326c36e31d706dfcf0eebb88d9899`):** [Yến agrees to Option 2 semantics, but confirms a compatibility gap and keeps D2-04 OPEN](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289261865). This section provides the missing design-contract obligation **without adding a Core port, business workflow or executable SQL**. The proof/SQL trust-model and independent physical review remain distinct gates.

### Normative orchestration and data-access MUST/MUST NOT (CREATE only)

1. **Trusted identity FIRST.** The application uses the already verified `CurrentPrincipal.requireVerified()` issuer/subject and exact JWT authorities; it MUST NOT accept client actor, tenant, JWT/GUC or a second token validator. Missing/invalid authentication MUST terminate before private ACL/definition queries.
2. **Pre-authorization facts must be minimal.** The source-backed preliminary CREATE classification MUST obtain **only** the private target→tenant locator, current membership, active-resource state/visibility and whether the verified actor possesses the **requested explicit CREATE ACL grant**. The returned authorization facts MUST NOT contain, preload, batch-join, cache-populate or materialize any `workflow_definition_bindings` row/active-binding set or V3 definition payload, even if an application field is not subsequently accessed. A source fault or target/actor mismatch remains sanitized UNAVAILABLE.
3. **Visibility BEFORE forbidden classification.** For missing/foreign/nonmember/tombstoned/nonvisible resource, return HIDDEN with no binding/definition access, irrespective of claimed JWT authority. On a visible resource, missing explicit CREATE grant **or** missing exact verified `workflow:create` authority returns FORBIDDEN; do not look up either binding or definition to distinguish these cases. Merely delaying access to `snapshot.activeDefinitionBindings()` in Java is **insufficient** if the `WorkflowAclSnapshotReader.read()` implementation already populated that set.
4. **Definition/binding only AFTER both checks.** A fully visible, explicitly CREATE-granted, exact-JWT-authorized operation may request the **specific tuple** `(tenant_id, resource_type, definition_id, definition_version)` from the trusted guarded data source, and only then privately load the exact Workflow definition. Missing/inactive/wrong exact binding is HIDDEN; definition-source failure is UNAVAILABLE; no latest-version/alias substitution or public definition-listing endpoint. Operation/target context MUST be bound to the verified actor and must not be reusable for another tenant/resource.
5. **Final transaction is authoritative.** Preliminary classification is never a durable write permission. The secured C3 CREATE path MUST generate the server ID and use the existing `WorkflowPersistencePort.createInstance` write, with **fresh** tenant guard `FOR SHARE`, authoritative resource/member/ACL/authority/binding recheck **after** lock and atomic insert + confirmed COMMIT on that physical JDBC transaction. The permitted definition/binding check in this final transaction occurs **after its own successful visibility+grant+authority check**, not merely after a precheck in another connection. Admin/policy mutators keep guard `FOR UPDATE`. No old Core `WorkflowRuntime.createInstance` hidden collision read, no extra public Core port, no blind retry after unknown COMMIT.
6. **SQL actor authenticity is a separate unresolved gate.** Under pending ADR-0024, a capability bound to exact CREATE authority cannot be fabricated for a missing-authority user merely to classify a 403. The future trusted classifier/SQL function design MUST preserve private resource visibility and fail closed **without pretending unauthorized requests carry valid CREATE capability**. D4 HMAC vs ADR-0025 risk acceptance is **NOT** resolved by this ordering; a reviewer must approve how private classification works under the chosen model before implementing SQL.

### Existing Stage-5 seams are NOT compliant as currently written

- `AuthorizedWorkflowService.createInstance()` preloads `persistence.loadDefinition()` before ACL/JWT authorization; the current method also invokes `WorkflowRuntime.createInstance()`. Neither behavior satisfies secured C3 CREATE.
- `TenantResourceWorkflowAccessPolicy.forCreation()` evaluates ACL then checks the binding field, but its current `WorkflowAclSnapshotReader.ResourceAclSnapshot` **includes** `Set<DefinitionBinding> activeDefinitionBindings` filled by one `reader.read()`. A future PostgreSQL implementation of this eager snapshot is **not allowed** to claim the no-materialization guarantee. A narrow application/infrastructure contract adjustment is REQUIRED: expose **minimal authorization facts independently of authorized exact-binding/definition lookup**. Implementation details need separate review; this is not approval to add a public API or change any Workflow Core signature.
- `WorkflowAuthorization.authorizeCreation()` checks exact JWT authority only after the existing `policy.forCreation()` binding decision. That order must be reconciled with Option 2 without returning FORBIDDEN for hidden resources and without granting an unauthorized capability.
- Current Stage-5 mock tests assert decision codes/Runtime delegation, not actual definition/binding read order. No source behavior or PG17 test is claimed fixed by this document.

### Acceptance at the existing CREATE-03/CREATE-04 gates

| Scenario | Result | Required **observable** read/call ordering (not merely status code) |
|---|---|---|
| Unverified actor | Authn failure | No private ACL, binding, definition, runtime read or write |
| Reliable hidden/nonmember/foreign/tombstoned target | HIDDEN | Minimal resource/membership/visibility lookup only; **zero binding/definition reads** |
| Visible + CREATE grant missing; any binding state / JWT state | FORBIDDEN | Minimal ACL facts then decision; **zero binding/definition reads**, no CREATE write |
| Visible + CREATE grant present + exact JWT authority missing; binding active/missing/inactive/wrong | FORBIDDEN | Minimal ACL facts and exact authority check; **zero binding/definition reads**, no false signed CREATE capability |
| Visible + grant+authority present + binding missing/inactive/wrong exact tuple | HIDDEN | Binding lookup only AFTER both authorization conditions; no V3 definition materialization on binding denial |
| Visible + grant+authority+binding present | Authorized domain checks only | Exact binding and definition read AFTER successful authorization; final guarded SQL transaction separately repeats facts and confirms COMMIT |
| Provider/SQL proof error at a required stage | UNAVAILABLE | No fallback ALLOWED/HIDDEN; log/response contain no privileged payload |
| Policy revoke between pre-read and final CREATE | Deny at final guard | No CREATE row/history committed, regardless of earlier preliminary allow |
| COMMIT attempted, outcome uncertain | COMMIT_OUTCOME_UNKNOWN | No blind retry/collision probe; later reconciliation requires separately approved contract |

**Test method:** add *interaction-level* spy/recorded-call assertions on app reader and definition persistence, plus future **actual SQL trace/spy** on disposable PG17 with representative secret binding/definition rows to prove the absence of lookup/materialization. Include missing/inactive/wrong exact binding, visible/hidden, 2×2 grant×authority and source/proof failures, not only equality of 403/404. Assert no hidden existence oracle and no cross-tenant privilege escalation. These tests are **specified**, NOT EXECUTED.

**D2-04 disposition:** `SEMANTICS AGREED BY YẾN / CONTRACT REVIEW CANDIDATE / IMPLEMENTATION AND INTERACTION EVIDENCE BLOCKED`. D2/D3/D4 remain blocked as applicable; Stage 6.1 overall **NOT YET PASS** and Stage 6.2 **BLOCKED**. PR #50 stays Draft. No new workflow, executable V5, SQL roles, Java implementation or Workflow HTTP activation from this addendum.


## 14. Owner D4 MVP scope decision (2026-10-20)

**Controlling owner decision:** [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6289594814) chooses **ADR-0025 Option B trusted backend** for the isolated OLP MVP. This supersedes *the earlier undecided owner scope statements in this draft*; it does **not** supersede the stronger ADR-0024 security ADR without reviewer acceptance. HMAC-SHA-256 SQL actor proof, PID/XID binding, nonce/replay and key rotation are **not MVP delivery work**. The original limitation is disclosed: stolen runtime PostgreSQL credentials together with DB connectivity can permit forged SQL actor arguments.

**Unchanged mandatory controls:** genuine verified OIDC principal and exact JWT authorities in trusted backend; no client principal/actor/tenant header trust; tenant/resource ACL, D2-04 binding non-disclosure, minimum effective PostgreSQL privileges, safe definer functions, final post-guard ACL+state/history+confirmed COMMIT within the same JDBC port transaction, revocation races, C3 and unknown COMMIT handling, reviewed provisioning/audit, and disposable PG17 evidence. No extra workflow, Core method, SQL function count, schema table or stage is introduced.

**D4 disposition:** `OWNER OPTION B DECIDED / INDEPENDENT SECURITY REVIEW PENDING / NOT PASS`. Yến's [D2-04 agreement at exact HEAD aec2bf2](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289513010) is **not** an endorsement of Option B. D2/D3 security design gates and S6.2 remain BLOCKED; PR #50 Draft and Workflow HTTP OFF. No SQL/Java/migration/test implementation is claimed by this documentation update.
