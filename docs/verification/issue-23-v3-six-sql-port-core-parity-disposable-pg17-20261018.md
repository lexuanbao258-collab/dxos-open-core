# Issue #23 — V3-backed six SQL port **test-only** parity / full Core JDBC baseline (2026-10-18)

**Verdict: ISOLATED SQL-V3 PARITY SCENARIO PASS, CORE JDBC CONTRACT BASELINE PASS; V5 ACL/HMAC PRODUCTION GATE STILL BLOCKED.**

This report distinguishes three implementations: (i) the **actual existing Java** `PostgresWorkflowPersistence` port and its full baseline; (ii) six newly written **test-only V3-backed SQL functions** with real snapshot/definition/history columns and lossless epoch seconds+nanos; and (iii) earlier, separate HMAC/SQL role demonstration fixtures. **The third is not yet cryptographically connected to the second.** No production migration, new production SQL function or new Gateway bean was deployed.

## Pinned execution evidence

- **Tested PR #36 commit:** `82ca57d550d1082c3669590ee2a75aa472789962`.
- **[GitHub Actions run #16](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39135080127)**, run ID **39135080127**, conclusion **SUCCESS**, PostgreSQL **17** ephemeral CI container, JDK **21**, Gradle **8.14.3**.
- The CI assertions parse actual JUnit XML; every suite ran and had **zero failures/errors/skips**:

| JUnit suite | Executed | Failed | Errored | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `WorkflowSqlProofPrototypeTest` | 1 | 0 | 0 | 0 |
| `WorkflowSqlPrivilegeDisposableIntegrationTest` | 1 | 0 | 0 | 0 |
| `WorkflowSixPortDisposableContractIntegrationTest` | 1 | 0 | 0 | 0 |
| `PostgresWorkflowPersistenceIntegrationTest` | 56 | 0 | 0 | 0 |
| `WorkflowV3SixPortSqlParityIntegrationTest` | 1 | 0 | 0 | 0 |
| **TOTAL** | **60** | **0** | **0** | **0** |

- CI printed `JUNIT_FIVE_SUITES_INCLUDE_V3_SQL_PARITY_PASS_NO_SKIP` and `BUILD SUCCESSFUL in 1m 1s`. Evidence artifact `issue23-disposable-pg17-proof-junit`, ID **12138534612**, available from the run until its GitHub retention expiry (currently 2026-10-25).
- The first attempt at adding `PostgresWorkflowPersistenceIntegrationTest` failed **during V1–V2 fixture setup**, because CI lacked `WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE=true`. We reproduced the sanitized JUnit failure (`Workflow connection rejected`) and corrected **only this CI variable** (current HEAD includes fix). We did **not** weaken `WorkflowEventTriggerGuard`, production connection validation or pinned migration hashes. A rerun of the full 56-test baseline on the same marked disposable PG17 then passed. Failure history exists in [run #11](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39134185898) and diagnostics in [run #12](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39134403144); these are setup failures, not domain-parity regressions.

## Committed executable test sources

- [`v3-six-port-exact-wire.sql`](../../backend/src/test/resources/workflow-sql-parity/v3-six-port-exact-wire.sql) — six PostgreSQL PL/pgSQL **SECURITY INVOKER** functions in a **random disposable V1–V4 migrated V3 schema**. This is **NOT** `V5__*.sql` and cannot be installed via production Flyway.
- [`WorkflowV3SixPortSqlParityIntegrationTest.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowV3SixPortSqlParityIntegrationTest.java) — creates a fresh private disposable schema, validates PostgreSQL 17 marker/loopback/port, migrates the real pinned V1–V4 through `WorkflowMigrations`, installs the six functions in the **test schema only**, exercises genuine Java Core domain objects, asserts all twelve instance fields / full eight history fields, then drops the test schema. It also verifies a separate runtime login cannot call these helpers (no schema USAGE or function EXECUTE), which is **intentional** until HMAC+ACL implementation exists.
- [`issue23-hmac-disposable-pg17.yml`](../../.github/workflows/issue23-hmac-disposable-pg17.yml) — CI creates an entirely separate disposable `postgres:17` container and temporary runtime login; executes all five suites with XML-based no-skips verification and keeps the artifact.
- The original 56-case [`PostgresWorkflowPersistenceIntegrationTest.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistenceIntegrationTest.java) remains unchanged, covering domain snapshots, authoritative pinned definitions, two writers, history atomicity, timestamp extremes, staging-free migration behavior and `COMMIT_OUTCOME_UNKNOWN` error classification for the existing Java adapter.

## What was actually verified in SQL V3 parity test

| New test-only SQL entrypoint | Scoped tested behavior | Missing for full port claim |
| --- | --- | --- |
| `wf_port_load_definition(id,version)` | Reads real V3 definition, exact version, initial state, states and transitions counts; returned JSONB includes states/transition IDs | No full adversarial/authority/hidden binding tests or complete Java set equality for every definition |
| `wf_port_load_instance(id)` | Returns all 12 V3 snapshot fields with exact epoch seconds and nanos; missing ID raises dedicated SQLSTATE in fixture | Missing/hidden 404 policy, verified proof, strict error mapping |
| `wf_port_load_history(id)` | Ordered V3 history: empty before transition, then two exact records and contiguous resulting runtime versions; actor/time/nanos checked | READ permission, revoke and multi-snapshot materialization not yet coupled |
| `wf_port_create_instance(...)` | DB-authoritative exact initial state and NOT_STARTED version zero, unique global instance ID, duplicate rejected; no hidden instance returned | V5 tenant/definition binding, full original `WorkflowInstance` input validation and durable-port adapter |
| `wf_port_commit_activation(expected,proposed)` | Full expected JSONB snapshot equality; source/version/lifecycle checks; exact ACTIVE timestamp-nanos parity; stale attempt rejected | Exhaustive error precedence, exact Java exceptions, lock/ACL sequencing |
| `wf_port_commit_transition(expected,proposed,record)` | Re-derives transition target from immutable V3 definition, compares complete resulting snapshot/record, updates instance plus history on one real SQL connection; runs two canonical transitions (DRAFT→IN_REVIEW→APPROVED), stale retry fails without extra history | Full authority/actor proof, all transition/terminal variants, JDBC unknown COMMIT classification and exhaustive equality/error precedence |

**Core semantics are not moved into these test fixtures:** the Java Core baseline remains authoritative. The test-only SQL model shows one execution path and selected negatives; **"60 PASS" must NOT be interpreted as complete six-port production compatibility**. SQL error codes are experimental (`P0001`/`P0002`/`23505`), not a signed-off `WorkflowErrorCode` bridge. Functions accept unauthenticated parameters and thus **must never be made callable by runtime** until protected by reviewed actor/proof+ACL guard SQL.

## Remaining P0 gates; next implementation steps

1. **Physical V5 schema and migration approval:** permanent `tenant_guards`, `tenant_memberships`, `resource_registry`, `resource_acl`, `workflow_definition_bindings`, immutable resource→tenant and tombstone/never-reuse, V3 instance reconciliation, FK/collation audit, approved forward-only migration and V3/V4 manifest guard compatibility. No V5 Flyway file is present or approved.
2. **DB-verifiable trusted caller provenance and secrets:** full ADR-0024 issuer/subject UTF-8 framing, actor target+operation+proposal proof, exact per-JDBC-transaction XID binding, replay/key rotation controls, constant-time MAC comparison audit, secure real-key custody and separation from SQL passwords. The earlier simple six-port HMAC fixture is **not yet integrated with real V3 payload SQL**; key/admin/provisioner procedures remain unapproved.
3. **Least-privilege SQL functions:** replace test-only `SECURITY INVOKER` with individually hardened, reviewed `SECURITY DEFINER` functions owned by a NOLOGIN role, PUBLIC EXECUTE revoked and exact runtime role GRANT; prevent direct V3/V5 table DML and helper access. Every function must guard the right target and post-lock current ACL/authority; all mutators use tenant guard `FOR UPDATE`; READ/HISTORY need lock-capable transaction. Prove actual privilege catalog and negative SQL-only impersonation.
4. **Java guarded port adapter with ADR-0023 Option A:** must preserve **each write's confirmed COMMIT** and `COMMIT_OUTCOME_UNKNOWN`, not rely on a deferred-commit Core port. A true Gateway CREATE should use approved C3 avoiding hidden pre-read, never pass client-chosen instance IDs. Exhaustively compare exact method/error priority with the existing Core port.
5. **Security/regression tests on implementation:** unauthorized/hidden/cross-tenant, simultaneous CREATE, pre-read then ACL revoke, FOR SHARE/UPDATE races for all mutators, savepoint rollback lock lifetime, shadow SQL paths, lost COMMIT and failed rollback, full JWT/JWKS and Request/JDK21 suite before any HTTP exposure.

**Result classification:** `TEST_ONLY_V3_WIRE_SCENARIO_PASS_60_OF_60; PRODUCTION_V5_SECURITY_GATE_BLOCKED`.

Bảo's owner-led review choice means only that Bảo makes final decisions; it does **not** transform incomplete cryptography/DB evidence into acceptance. PR #36 stays DRAFT, Issue #23 OPEN, Issue #22 Workflow HTTP OFF; no shared staging `127.0.0.1:55426`, Supabase or Keycloak writes, no V5 migration, no production SQL role privilege changes, no Java Core mutation or merge.
