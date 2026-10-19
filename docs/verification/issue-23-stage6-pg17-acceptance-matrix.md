# Issue #23 — Stage 6 PG17 acceptance matrix and evidence template

**Status:** PLANNED TESTS ONLY, NOT EXECUTED. Companion to [Stage 6.1 contract](../contracts/issue-23-stage6-s61-postgres-acl-transaction-contract.md). Do **not** count previous PR #36 prototype PG17 tests as production Stage 6 verification; those HMAC, SQL privilege and V3 parity fixtures were separately scoped.

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
| SQL-01 | Login as actual `dxos_wf_runtime` and inspect effective inherited/default/PUBLIC grants | Direct V3/V5 SELECT, INSERT, UPDATE, DELETE, TRUNCATE, ALTER, trigger disable, schema CREATE denied; runtime cannot SET ROLE owner/migrator/admin. |
| SQL-02 | Try unlisted `SECURITY DEFINER`, internal helpers, signing-key relation, PUBLIC EXECUTE, search-path/temp shadowing | Denied with actual SQL permissions, no side effects; only enumerated six port entrypoints callable under valid proof. |
| SQL-03 | Use valid JDBC credentials with forged actor, wrong target, wrong action, old transaction proof, expired proof, modified proposal | Every protected SQL function fails closed **before** disclosure/write; must not rely on actor strings or GUC. |
| SQL-04 | Java signer/SQL verifier use independent strict UTF-8 cross-language vectors including unpaired UTF-16 and non-ASCII issuer/subject | Exactly matching canonical envelope bytes/HMAC, reject malformed/ambiguous frames, wrong key, expired, non-constant encoding. |
| ACL-01 | Owner and active tenant membership but no grant; same tenant other-grant; tenant foreign/no membership | Owner-only knows at most existence but cannot perform operation; foreign/nonmember/hidden indistinguishable no data; positive other grant does not enable requested action. |
| ACL-02 | CREATE exact binding/version active, inactive, missing, alias/mismatched version | Only current exact active binding permits CREATE; blocking CREATE does not implicitly revoke existing-instance operations. |
| ACL-03 | Source null/unavailable, SQL timeout/deadlock, ACL changed during request, orphaned legacy V3 | Typed HIDDEN/FORBIDDEN/UNAVAILABLE according to ratified policy, never ALLOWED; no protected data or write. |
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
| HTTP-01 | Genuine Keycloak/JWKS token / invalid forged expired wrong-issuer/audience, roles and actor/header spoof | Actual filter chain denies with safe 401/403 and hidden 404 per ratified policy; no second decoder or trusting request actor. |
| HTTP-02 | Request endpoint regression and five Workflow HTTP paths with security filters | Existing Request behavior unchanged; WorkflowController not registered until the entire production security gate passes. |

## Port-method evidence (must cite individual suite and exact SQL function)

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
Revoke-first / writer-first / savepoint evidence:
Known rollback and COMMIT_OUTCOME_UNKNOWN fault evidence:
Core/error/actor consistency:
Unresolved P0/SEC/DB findings:
Verdict: PASS for narrowly stated test scope OR BLOCKED / CHANGES REQUIRED
```

**No item in this table is claimed PASS by creating this document.** Owner-led review is not the same as independent PostgreSQL/cryptography sign-off. Never change shared DB roles/privileges or launch production Workflow HTTP based on test fixture results.
