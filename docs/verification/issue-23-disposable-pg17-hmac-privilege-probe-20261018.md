# Issue #23 — Disposable PG17 HMAC / SQL-runtime privilege probe (2026-10-18)

**Evidence state: LIMITED PROTOTYPE PASS — NOT V5 / PRODUCTION GATE APPROVAL.**

## Verified code and execution

- **Tested PR #36 HEAD:** `aef97150bd0ec2f569140929753e9aca6657e280`, on `feature/issue23-secure-instance-id-foundation`; Draft PR remains unmerged.
- **Successful actual PostgreSQL CI:** [GitHub Actions run #3](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39132405442), run ID `39132405442`, conclusion **SUCCESS**, PostgreSQL **17.11**, JDK **21**, Gradle **8.14.3**. Job `JDK 21 / PG17 isolated runtime-role negative tests` ran only the two specified test classes.
- **JUnit XML verified in CI:** 
  - `WorkflowSqlProofPrototypeTest`: **1 test, 0 failures, 0 errors, 0 skipped**.
  - `WorkflowSqlPrivilegeDisposableIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**.
  - CI emitted `JUNIT_TWO_SUITES_PASS_NO_SKIP`, `BUILD SUCCESSFUL in 48s`.
  - Raw JUnit XML stored as GitHub Actions artifact `issue23-disposable-pg17-proof-junit` (artifact ID `12137649191`, [run artifacts](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39132405442#artifacts)); retention ends 2026-10-25, subject to GitHub settings. Artifact content has 2 XML reports, 1 test per suite.
- **Local independent JDK21 check:** `WorkflowSqlProofPrototype.java` was compiled by Java 21 and run without test/DB dependencies. The local file's Git blob SHA-1 `68bda9ce4d4edd94bc509b0dc32f74de25df6c10` matched the GitHub file blob exactly. Output `JAVA_HMAC_PROTOTYPE_PASS: RFC4231, strict UTF-8, target/action/xid binding`. This corroborates only the standalone Java codec.
- **No claims** of full backend regression, JWKS tests, all-six production SQL APIs, actual HMAC key management or deployed V5 DDL. Real SQL test success is scoped to the isolated minimal proof harness described below.

## Committed test files

| File | Purpose |
| --- | --- |
| [`WorkflowSqlProofPrototype.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlProofPrototype.java) | **Test-only** canonical seven-field binary length-framing, strict Java UTF-8 rejection, HMAC-SHA-256. Includes RFC 4231 positive vector and target/action/xid tampering negatives; has standalone JDK main. |
| [`WorkflowSqlProofPrototypeTest.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlProofPrototypeTest.java) | Runs Java vector checks in Gradle JUnit 5. |
| [`WorkflowSqlPrivilegeDisposableProbe.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlPrivilegeDisposableProbe.java) | Sets up random dedicated NOLOGIN function-owner + LOGIN runtime roles, protected data/key schema, `pgcrypto` HMAC `SECURITY DEFINER` **READ-only demo**, and real-runtime-login negative SQL assertions; tears them down. |
| [`WorkflowSqlPrivilegeDisposableIntegrationTest.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlPrivilegeDisposableIntegrationTest.java) | Strict env-gated JUnit integration test, SKIPPED in ordinary Gradle build when not opted in. |
| [Isolated GH Actions workflow](../../.github/workflows/issue23-hmac-disposable-pg17.yml) | Creates **its own Postgres 17 service container** on GH runner port `65521`, marks named disposable test DB, executes only two suites, asserts both actually ran and saves JUnit XML. |

## Actual tested behavior — deliberately limited

1. Real `dxos_wf_runtime`-**like** disposable SQL LOGIN (randomly generated `wfproof_runtime_*`) has **no direct** protected `SELECT` on data or key tables; `UPDATE` and `DELETE` on data denied; `CREATE TABLE` in protected schema denied. PostgreSQL returned `42501`; expected denials are *test successes*, not unexpected failures.
2. One minimally scoped `SECURITY DEFINER` function under a separate NOLOGIN table owner allows a **correct HMAC proof** for an exact test actor + READ + target + backend PID + PostgreSQL transaction XID + nonce. Returns one preseeded authorized value.
3. Invalid proofs (forged actor, alternate target, wrong operation, zero MAC) are rejected with `42501`. A proof from an already committed transaction is rejected when reused in a new transaction. Fixture test does **not** claim single-use within the same transaction.
4. Java canonicalizer rejects unpaired UTF-16 surrogate / NUL and binds method, target and transaction identity. Test proof uses **seven text fields**, length-prefix encoding and a read-only SQL function. It does not sign all fields in ADR-0024's full proposed payload or all six port methods.
5. PostgreSQL disposable fixture uses `pg_catalog.pg_current_xact_id()` and `pg_backend_pid()` and `pgcrypto.hmac` in a guarded function. This proves **interoperability for that limited fixture**, not SQL proof verifier constant-time safety, JWT provenance or production crypto secret custody.

## Explicit non-staging safety boundary

The integration runner **rejects** invocation unless *all* of the following hold:

- `WORKFLOW_SQL_PROOF_DISPOSABLE_ACK=ONLY_DISPOSABLE_PG17`.
- Loopback JDBC URL of the form `jdbc:postgresql://127.0.0.1:<dedicated-port>/wfproof_test_<random-suffix>`; rejects ports **5432 and 55426**, non-loopback hosts and ordinary `postgres` databases.
- The connected database must be PostgreSQL **major 17**, writable, and have the **server-scoped** marker `current_setting('dxos.wfproof_disposable',true) = 'on'`. Role-creating user must be superuser on the disposable cluster; `pgcrypto` cannot be preinstalled in that dedicated test DB.
- CI supplies only disposable throwaway credentials and no user/project GitHub secrets. Fixture creates randomized roles and schemas, installs `pgcrypto` in a temporary namespace, tears down those objects in `finally` and disposes the entire PostgreSQL service container. Verify the CI run and normal cleanup; aborted runners are isolated.

**No queries or modifications occurred on shared staging PostgreSQL `127.0.0.1:55426`, Supabase, local Keycloak `8180`, or any actual Workflow migrations.** No production source or V1–V4 SQL migration was modified.

## What remains blocking before production implementation/merge

**Still P0 and NOT proven by the two passing tests:**
- Full ADR-0024 versioned canonical proof envelope: real `issuer`/`subject` UTF-8 actor canonical identity, full target/definition/proposal signing, expiry/nonce policy, key IDs, key generation/storage/rotation, protection from SQL runtime role, signer trust boundary, replay semantics and audited comparison. The demo uses SQL BYTEA equality, **not proven constant-time**; never ship it as the final verifier.
- Physical V5 tables + immutable tenant/resource binding, nonreusable tombstones, SQL runtime/admin/provisioning function allowlist, actual full privilege catalog and all mutators locking the permanent tenant guard.
- All six `WorkflowPersistencePort` methods with protected SQL implementations, Java/Core error parity, authoritative definition/history and durable `COMMIT_OUTCOME_UNKNOWN`.
- Deterministic **two-physical-connection** ACL revoke-first vs write-first/read/history races, savepoint rollback lock-release tests; visibility 403/hidden 404; CREATE C3 forced collision/lost response.
- Yến's real `CurrentPrincipal.requireVerified()` JWKS/OIDC integration, 401/403/404, Request regression, full backend JDK21 suite and release review.

**Disposition:** **PROTOTYPE_VERIFIED / V5_SECURITY_GATE_BLOCKED**. ADR-0023 Option A and ADR-0024 C3/HMAC direction remain candidate inputs; the team owner may choose review workflow but must not interpret this test as independent PostgreSQL/cryptographic sign-off. Keep Issue #23 OPEN, Draft PR #36 DRAFT, Issue #22 HTTP OFF. Next task should extend the isolated disposable fixture toward exact production-proof fields and least-privilege SQL functions, then tackle guarded two-connection revocation tests **without touching shared infrastructure**.
