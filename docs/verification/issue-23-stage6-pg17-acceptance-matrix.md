# Issue #23 — Stage 6 PG17 acceptance matrix and evidence template

**Status:** PLANNED TESTS ONLY, NOT EXECUTED. Companion to [Stage 6.1 contract](../contracts/issue-23-stage6-s61-postgres-acl-transaction-contract.md). Do **not** count previous PR #36 prototype PG17 tests as production Stage 6 verification; those HMAC, SQL privilege and V3 parity fixtures were separately scoped.

**D2 refinement baseline:** source `e3343de`, 2026-10-20. New criteria below describe proposed design obligations, not executed tests or approval. **D2/D3/D4 and S6.2 remain BLOCKED.** DEC-D2-01/03 need owner/domain/DB review; DEC-D2-02 physical enforcement needs DB review; **DEC-D2-04 PENDING OWNER/YẾN APPROVAL**. Proposed CREATE expectations must be ratified or explicitly revised before becoming implementation acceptance; retain Stage 5 source/prior §10 comparison as evidence of the intentional policy difference.

**MVP scope for review:** tenant-disable has no approved physical lifecycle; propose **DEFER from MVP v1** unless a demonstrated business requirement justifies separate design approval. No disable column/API/schema extension is assumed or tested here. Arbitrary online resource registration stays closed; both NEW and LEGACY provisioning require manifest/approval and a demonstrated authoritative source, which remains unproven.

## Safe execution envelope (mandatory)

- **Only** isolated, explicitly disposable PostgreSQL **17.x** database/container, with a unique test schema, test-only role credentials and test-owned data. No live Supabase, staging at `127.0.0.1:55426`, production schema, or Keycloak writes. Do not inherit `WORKFLOW_TEST_JDBC_URL` without verifying the target is dedicated disposable. Stop if database identity cannot be proved.
- Java 21 + existing Gradle toolchain; new tests must assert JUnit `tests > 0`, `failures=errors=skipped=0`; report *every* selected suite and exact commit. Skipped tests never count as passed.
- Preserve Flyway V1–V4 byte-for-byte and pinned migration checksums, validate candidate forward-only V5 **only in isolated disposable DB after DDL review**. Do not merge production V5 under S6.1.
- Use two **distinct physical PostgreSQL backend PIDs** for concurrency, deterministic `CountDownLatch`/barriers and finite lock timeouts; never infer correctness from `Thread.sleep`, exceptions alone or a single connection. Verify row, history, revisions and grants **after COMMIT on a fresh third connection**.
- For authorization negatives, assert **no protected snapshot/history is materialized to untrusted caller, no row mutation, no history append, no token or SQL internals exposed**.

## Stage 6.2–6.5 candidate test cases and acceptance signals

| ID | Category / scenario | Required observation (fail if absent) |
|---|---|---|
| V5-01 | Fresh disposable PostgreSQL 17 applies reviewed additive schema after pinned V1–V4 | No changed V1–V4 checksum/DDL; exactly reviewed V5 objects; no public schema/role side effects. |
| V5-02 | Duplicate global resource key, cross-tenant rebinding, owner mutations, invalid definition binding FK | Denied by **actual DDL/functions**; identity immutable and exact V3 definition/version required. |
| V5-03 | Resource TOMBSTONED, DELETE/re-INSERT, tenant guard deletion/reuse, historical V3 missing registry | No resurrection/ID reuse, old unregistered instance cannot be exposed or inferred/backfilled automatically. |
| V5-04 | UPDATE tenant guard ID, resource type/ID/tenant/created_at; cascade, UPSERT remap, delete/reinsert, duplicate bootstrap | Future actual constraints/triggers reject identity rewrites and recycling even when no FK child exists; same-tenant owner metadata changes only via vetted API; concurrent bootstrap never resets revision/audit. |
| V5-05 | Reverse tombstone or clear/change its timestamp; import/restore over a reserved key | One-way lifecycle and original tombstoned_at retained; no tenant/resource deletion/reuse. Catalog + negative tests distinguish CHECK/FK shape from trigger transition enforcement; no online owner/replication/trigger/TRUNCATE bypass. |
| LEGACY-01 | Caller attempts arbitrary online registration; controlled NEW provisioning encounters a V3 key without registry, legacy adoption or unapproved tenant mapping | No arbitrary online registration path is enabled. Both NEW/LEGACY require manifest/approval plus demonstrated source provenance/owner/mapping validation; credentials/digest/approval alone cannot prove authority. NEW requires empty legacy inventory and rejects legacy adoption, never silently INSERTs/UPSERTs ownership over old instances. Only separate LEGACY-manifest-authorized reconciliation may associate a whole legacy key; missing/ambiguous keys remain HIDDEN. |
| LEGACY-02 | Reviewed manifest has wrong/missing tenant evidence, duplicate cross-tenant resource keys, stale revision, wrong digest/environment or incomplete instance set | Approval/provenance, exact whole-key inventory and immutable target checks fail closed; wrong external tenant evidence blocks approval (PK/FK/digest alone cannot prove ownership). No partial association, inferred owner, policy grant or hidden instance disclosure. |
| LEGACY-03 | Legacy writer inserts/rebinds/updates between inventory and reconciliation; old pool attempts write during/after coordinated deployment | Deterministic distinct-PID barriers prove old writers drained/fenced through deployment and privilege changes, with affected traffic held. Verify effective permissions as actual login roles before/after changes and fresh-connection inventory/guarded behavior before resumption; no claim of one atomic application/DB transaction. Drift aborts for renewed review; tenant guard alone cannot fence an unguarded V3 writer. NEW absence checks cannot race a legacy insert. |
| LEGACY-04 | Valid whole-key reconciliation, rollback/audit failure and attempted rerun | Only reviewed registry/policy facts and atomic audit/revision commit; no automatic positive grants; V3 IDs, resource/definition pins, snapshots/history/timestamp nanos and V1–V4 bytes unchanged. Fresh verification proves exact inventory coverage/no unintended visibility; confirmed rollback leaves no partial adoption; uncertain commit never blindly replayed. |
| LEGACY-05 | Deployment/privilege-step failure; application rollback after committed DB changes; incompatible rollback or uncertain COMMIT | Fence/traffic hold persists through rollback/recovery. No regrant of old direct-table access or restart of unguarded pools; reconcile committed state, recover a compatible reviewed guarded configuration, recheck effective privileges and fresh-connection state before resuming. Incompatible rollback requires fenced forward recovery; unknown COMMIT never blindly replayed. |
| SQL-01 | Login as actual `dxos_wf_runtime` and inspect effective inherited/default/PUBLIC grants | Direct V3/V5 SELECT, INSERT, UPDATE, DELETE, TRUNCATE, ALTER, trigger disable, schema CREATE denied; runtime cannot SET ROLE owner/migrator/admin. |
| SQL-02 | Try unlisted `SECURITY DEFINER`, internal helpers, signing-key relation, PUBLIC EXECUTE, search-path/temp shadowing | Denied with actual SQL permissions, no side effects; only enumerated six port entrypoints callable under valid proof. |
| SQL-03 | Use valid JDBC credentials with forged actor, wrong target, wrong action, old transaction proof, expired proof, modified proposal | Every protected SQL function fails closed **before** disclosure/write; must not rely on actor strings or GUC. |
| SQL-04 | Java signer/SQL verifier use independent strict UTF-8 cross-language vectors including unpaired UTF-16 and non-ASCII issuer/subject | Exactly matching canonical envelope bytes/HMAC, reject malformed/ambiguous frames, wrong key, expired, non-constant encoding. |
| SQL-05 | Runtime/admin/provisioner try direct identity/membership/audit DML, trigger/replication bypass, broad helpers and inherited owner rights | All denied by effective privileges, including DELETE/TRUNCATE/ALTER and audit UPDATE; only narrow authenticated mutation/reconciliation APIs callable. Separately controlled offline-owner tests prove triggers, not a claim that SQL superuser is contained. |
| ACL-01 | Owner and active tenant membership but no grant; same tenant other-grant; tenant foreign/no membership | Owner-only knows at most existence but cannot perform operation; foreign/nonmember/hidden indistinguishable no data; positive other grant does not enable requested action. |
| ACL-02 | CREATE exact binding/version active, inactive, missing, alias/mismatched version | Only current exact active binding permits CREATE; blocking CREATE does not implicitly revoke existing-instance operations. |
| ACL-03 | Source null/unavailable, SQL timeout/deadlock, ACL changed during request, orphaned legacy V3 | Typed HIDDEN/FORBIDDEN/UNAVAILABLE according to ratified policy, never ALLOWED; no protected data or write. |
| MEMBER-01 | Deactivate actor with multiple positive grants and owned ACTIVE resources; then reactivate | Under one guard transaction all positive grants become false, membership inactive, owners explicitly reassigned and audit/revision committed. Reactivate restores membership only; no old grant or ownership restoration. Owner with no fresh grant cannot operate; every regrant requires a new audited decision. |
| MEMBER-02 | Missing/inactive/cross-tenant replacement owner, partial resource reassignment; concurrent replacement-owner deactivation | Deactivation rejected atomically unless every ACTIVE resource ends with an active same-tenant owner. Guarded cross-row enforcement prevents race; no owner-derived/copy grants. Tombstoned resource may retain inactive owner provenance without resurrection. |
| MEMBER-03 | Grant races deactivate/reactivate or Workflow read/write, both lock orders | Two physical PIDs + barriers prove same tenant guard serialization: earlier writer may complete; deactivate-first denies subsequent operation. No positive grant survives deactivation; post-reactivation allow requires a separately committed fresh grant. No nonvisible snapshot/history release. |
| MEMBER-04 | Audit INSERT failure or mutation failure midway through grants/owner/membership updates; successful regrant and no-op retry | Confirmed rollback restores all ACL/owner/member/revision state and commits no success audit. Success has exact append-only old/new affected identities + approval/provenance/revision, not counts alone; no direct audit rewrite or fake duplicate state change. Fresh third connection verifies results; uncertain commit uses reconciliation/no retry. |
| TX-01 | Revoke **wins first**: conn A policy `FOR UPDATE` then COMMIT; conn B later writer `FOR SHARE` | B sees **post-revoke** ACL, does not commit mutation/history despite preliminary Stage-5 allow. |
| TX-02 | Guarded writer **wins first**: conn B tenant `FOR SHARE` then final mutation/COMMIT; conn A attempts policy `FOR UPDATE` | A cannot finish revoke before B ends transaction; subsequent operations see revoked policy. |
| TX-03 | Revoke between preliminary `loadInstance` read and `commitTransition` or `commitActivation` | Final port transaction re-verifies and denies; previous read is not a write entitlement. |
| TX-04 | Equivalent interleavings for membership revoke, ACL grant revoke, owner change, CREATE binding revoke, resource tombstone | All authoritative mutators hold the **same permanent tenant guard**, no bypass. |
| TX-05 | Guarded SNAPSHOT/HISTORY with zero history, existing history and revocation racing | Current post-lock snapshot only, no hidden existence or history materialization; `READ COMMITTED` lock-capable transaction. |
| TX-06 | Savepoint rollback or PL/pgSQL caught exception **after guard acquired** | Cannot continue with a released guard; reacquire/re-authorize or abort, with deterministic proof. |
| TX-07 | Java SQL exception before COMMIT with confirmed rollback, fail rollback, ambiguous commit, connection loss after commit attempt | Correct sanitized typed outcome for known rollback; uncertain => `COMMIT_OUTCOME_UNKNOWN`, no blind retry, no extra history row. |
| CORE-01 | Port CREATE, ACTIVATE, TRANSITION snapshot/history parity vs existing Core JDBC baseline | Full V3 domain state, lifecycle, version and `Instant` seconds+nanos unchanged; exact transition actor and one history event per successful transition. |
| CORE-02 | Optimistic version conflict, completed instance, wrong definition, stale/forged proposal and state dependent error after revoke | No leak of hidden state; authorized error precedence preserved; fresh read-only authorization before protected domain error escapes. |
| CREATE-01 | C3 generated ID collision with another tenant and two concurrent same-ID INSERTs | No `WorkflowRuntime.createInstance`/hidden `loadInstance` probe, no existing snapshot/history read, safe unique collision classification and no external 409 identifying collided row. |
| CREATE-02 | Client attempts to provide `instanceId`; lost response; unknown commit and retry | Unexpected ID rejected; no blind CREATE replay, reconciliation/idempotency explicitly approved before HTTP. |
| CREATE-03 | Proposed mixed-failure truth table: visibility × explicit CREATE grant × exact JWT authority × exact binding (active/missing/inactive/wrong version) | **Conditional on DEC-D2-04 approval:** reliable hidden resource always HIDDEN; visible missing grant **or** JWT authority always FORBIDDEN regardless of binding; both present + missing/inactive/wrong binding HIDDEN; all present reaches authorized domain checks. Assert no binding/definition materialization on operation denial, no alias/owner shortcut; record changed Stage 5 / prior §10 expectations explicitly. |
| CREATE-04 | Unauthenticated caller; null/throwing/mismatched source; missing definition overlaps grant/authority denial; guarded proof fault; ambiguous write COMMIT | **Conditional on DEC-D2-04 approval:** identity first/no ACL lookup; genuine source/proof uncertainty UNAVAILABLE, never synthetic missing. Trusted visible grant/authority denial precedes definition lookup; only authorized scope loads exact definition. Prove proof validation and post-guard ordering coexist without claiming a missing JWT authority or leaking hidden visibility; uncertain COMMIT still overrides all denials, no retry. |
| HTTP-01 | Genuine Keycloak/JWKS token / invalid forged expired wrong-issuer/audience, roles and actor/header spoof | Actual filter chain denies with safe 401/403 and hidden 404 per ratified policy; no second decoder or trusting request actor. |
| HTTP-02 | Request endpoint regression and five Workflow HTTP paths with security filters | Existing Request behavior unchanged; WorkflowController not registered until the entire production security gate passes. |

## Port-method evidence (must cite individual suite and exact SQL function)

New D2 rows require actual constraint/trigger definitions and negative executions, effective-role catalog evidence, manifest approval/provenance + before/after inventory, and atomic audit/revision observations from a fresh third connection. Policy mocks or documentation inspection cannot satisfy them. Use synthetic authoritative manifests and test-only admin/provisioner proofs in the disposable database; never connect to database/Keycloak as part of this documentation refinement. HTTP expectations remain later integration gates, not PG17-only proof of JWT validation.

| Public `WorkflowPersistencePort` | Expected secured SQL entrypoint | Test essentials |
|---|---|---|
| `loadDefinition` | `wf_port_load_definition` | Exact bound definition/operation scope only; internal CREATE/ACTIVATE/TRANSITION load is **not** an unprotected definition-listing API. |
| `loadInstance` | `wf_port_load_instance` | Exact target and verified scope; internal ACTIVATE/TRANSITION do not demand extra public READ grant; **not called in secured CREATE**. |
| `loadHistory` | `wf_port_load_history` | Exact READ target, guarded existence/history in one coherent transaction. |
| `createInstance` | `wf_port_create_instance` | Server ID + exact definition/resource; final guard and durable COMMIT on same physical connection; collision-safe. |
| `commitActivation` | `wf_port_commit_activation` | Post-lock ACL, exact previous state, fresh authority proof, version/lifecycle parity, confirmed durable COMMIT. |
| `commitTransition` | `wf_port_commit_transition` | Post-lock ACL, true verified actor, complete canonical proposal, exact one-history append + state COMMIT; unknown outcome preserved. |

## Evidence handoff template

```text
Stage: 6.x (exact scope)
HEAD SHA:
Base SHA / PR:
Database: PostgreSQL 17.x, disposable verified (name, PID, isolated identity; never credentials)
Migration path: reviewed V1–V4 SHA + proposed V5 SHA / or NOT EXECUTED
JDK/Gradle:
JUnit XML suite counts: [name] tests=.. failures=0 errors=0 skipped=0
CI run URL/artifact ID:
Distinct connection PIDs and deterministic barriers:
Runtime role catalog negative evidence:
Constraint/trigger identity/tombstone negatives (including empty-child cases):
Approved manifest version/digest, provenance and exact inventory coverage (no sensitive payload):
Authoritative source owner/provenance and mapping-validation approval evidence:
Legacy writer fence/drain, coordinated deployment and effective-privilege post-check evidence:
Deployment/privilege failure, rollback/forward recovery and safe-resumption evidence:
Membership deactivate/reactivate, owner reassignment and fresh regrant evidence:
Durable audit atomicity/privilege/rollback evidence:
DEC-D2-04 owner/Yến decision reference + mixed-failure/source/proof ordering evidence:
Revoke-first / writer-first / savepoint evidence:
Known rollback and COMMIT_OUTCOME_UNKNOWN fault evidence:
Core/error/actor consistency:
Unresolved P0/SEC/DB findings:
Verdict: PASS for narrowly stated test scope OR BLOCKED / CHANGES REQUIRED
```

**No item in this table is claimed PASS by creating this document.** Owner-led review is not independent PostgreSQL/cryptography sign-off. **D2/D3/D4 and S6.2 remain BLOCKED; stop for Technical Lead review.** Never change shared DB roles/privileges or launch production Workflow HTTP based on test fixture results.
