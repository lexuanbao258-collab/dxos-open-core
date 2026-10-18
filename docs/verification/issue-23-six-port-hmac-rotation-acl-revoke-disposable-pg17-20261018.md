# Issue #23 — Six test-only SQL port functions, rotating HMAC keys, and ACL revoke races (2026-10-18)

**Disposition: LIMITED ISOLATED PROTOTYPE PASS / PRODUCTION SQL V5 NOT IMPLEMENTED AND NOT APPROVED.**

This is execution evidence for an isolated security **test fixture**, not evidence that the actual six `WorkflowPersistencePort` adapters or protected V5 policy SQL have been implemented. No production Workflow route was registered, no shared database migration was attempted, and no current Core signatures were altered.

## Immutable execution evidence

- Draft [PR #36](https://github.com/lexuanbao258-collab/dxos-open-core/pull/36) tested at exact commit `ea856cf728b1c21732ecc6e2e8171d895f544ef5`, based on PostgreSQL 17-compatible tests under `backend/src/test` only.
- [GitHub Actions execution #9](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39133415231), run ID **39133415231**, conclusion **SUCCESS**, Job ID **117693013554**. Dedicated Docker **PostgreSQL 17** service, Java **21**, Gradle **8.14.3**.
- CI asserted the following **JUnit XML test result objects**, not just a green CI badge:

| Suite | Tests | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| `WorkflowSqlProofPrototypeTest` | 1 | 1 | 0 | 0 | 0 |
| `WorkflowSqlPrivilegeDisposableIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `WorkflowSixPortDisposableContractIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| **Total** | **3** | **3** | **0** | **0** | **0** |

- CI emitted `JUNIT_THREE_SUITES_PASS_NO_SKIP` and `BUILD SUCCESSFUL in 37s`; XML archived as artifact **`issue23-disposable-pg17-proof-junit`** ID **12139086315**. Artifact retention is time-limited.
- The first two suites also independently passed in [earlier run #3](https://github.com/lexuanbao258-collab/dxos-open-core/actions/runs/39132405442); this latest run reruns them together with the extended suite.

## Exact tested code

- [`WorkflowSixPortDisposableContractProbe.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSixPortDisposableContractProbe.java): standalone JDK/JDBC fixture, creates randomized, private test-only SQL owner/runtime/policy roles and schemas, one `pgcrypto` verifier, tenant/resource/ACL guard rows, minimal workflow definition/instance/history tables, six `SECURITY DEFINER` SQL entrypoints and policy grant/revoke functions. Test methods perform explicit checks and throw on the first unexpected response; fixture drops objects and the CI discards its dedicated database container.
- [`WorkflowSixPortDisposableContractIntegrationTest.java`](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSixPortDisposableContractIntegrationTest.java): JUnit wrapper enabled **only** when `WORKFLOW_SQL_PROOF_DISPOSABLE_ACK=ONLY_DISPOSABLE_PG17`.
- [`issue23-hmac-disposable-pg17.yml`](../../.github/workflows/issue23-hmac-disposable-pg17.yml): dedicated disposable service on CI port `65521`, server-scoped database disposable marker, Java 21/Gradle targeted test command, three-suite XML validation and artifact.
- Existing [RFC 4231 and strict UTF-8 test](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlProofPrototype.java) and [minimal privilege test](../../backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSqlPrivilegeDisposableProbe.java) remain active and PASS.

## Six test-only entrypoints actually exercised

| SQL function created **inside the fixture only** | Positive behavior checked | Security/semantic limits |
| --- | --- | --- |
| `wf_port_load_definition` | Valid signed CREATE-scope test actor/resource/definition returns test initial state `START` | Does **not** implement exact Java `WorkflowDefinition` reconstruction, all states/transitions, binding and policy product rules |
| `wf_port_load_instance` | Valid READ proof for just-created instance returns its initial state | Does **not** return full immutable Core snapshot or ACTIVATE/TRANSITION-only scoped internal read |
| `wf_port_load_history` | Valid READ proof returns count 0 before and 1 after a transition | Does **not** reconstruct immutable ordered `TransitionRecord` list with exact time/actor |
| `wf_port_create_instance` | Test-only generated ID inserts NOT_STARTED instance; global ID uniqueness at database level | No production C3 authorized service, final V3 exact wire format or collision/idempotency HTTP mapping |
| `wf_port_commit_activation` | NOT_STARTED → ACTIVE with expected version zero, one guarded transaction | No full expected/proposed snapshot comparison or complete error priority |
| `wf_port_commit_transition` | ACTIVE version zero → DONE version one and **one matching history row**, atomic within test JDBC transaction | Test state machine is deliberately simplified; no real authoritative transition definition, instant nanos or all Core error semantics |

Every SQL entrypoint is owned by a **NOLOGIN** test owner and granted EXECUTE only to a real restricted runtime LOGIN. The prototype SQL function verifies a fresh HMAC-bound **actor, function action, target, payload, key-id, backend PID, transaction xid8, nonce, issued/expiry window**, then takes tenant `FOR SHARE`, rechecks ACL, and performs its scoped query. Wrong-function HMAC and repeated completed transition cannot succeed. No generic runtime API for writing ACL is granted.

## HMAC lifecycle / privilege findings verified in fixture

- **Key A ACTIVE -> VERIFY_ONLY -> REVOKED**. Proof signed with A still succeeds during VERIFY_ONLY when within its validity window; after REVOKED it fails. **Key B ACTIVE** permits newly signed requests independently, before and after revoking A.
- Expired proof is refused even with valid HMAC; current-time clock checks are performed before touching private tables and again after the guard lock.
- Real runtime role cannot read private key material, instance table, ACL tables or invoke internal verifier/policy functions. Direct SQL table DML and schema DDL attempts produce **SQLSTATE 42501**.
- The JDK signer constructs deterministic 11-field strict UTF-8 length-framed MAC input; SQL recreates it with PostgreSQL `int4send` and `convert_to` and uses `pgcrypto.hmac`. Test-only SQL compares bytes with ordinary equality: **NO constant-time MAC comparison audit**, NO key-management deployment/secret-store review, and NO complete ADR-0024 canonical issuer/subject+definition+proposal wire proof.

## Two independent physical transaction race checks

**1. Workflow writer wins lock first:** Runtime-credential JDBC connection performs signed test TRANSITION, acquiring tenant guard `FOR SHARE` and writing state+history **without committing yet**. A distinct policy-role JDBC connection tries `wf_policy_revoke('TRANSITION')` (guard `FOR UPDATE`). It must **time out on the held row lock** (SQLSTATE **55P03** with bounded `lock_timeout=200ms`). After writer commits, the policy connection retries in a fresh transaction, commits revoke, and authoritative guarded READ verifies history count = 1. A subsequently signed TRANSITION fails.

**2. ACL revoke wins lock first:** Policy-role connection acquires guard `FOR UPDATE` and changes READ ACL **without committing yet**. Distinct runtime JDBC connection tries signed guarded HISTORY; it must block until bounded `lock_timeout` and produce SQLSTATE **55P03** (no history materialized). After revoke commits, a **fresh** validly signed guarded HISTORY fails with SQLSTATE **42501** due current ACL. The test uses live PostgreSQL row locks, two concurrently open physical connections, and explicit SQL lock timeouts rather than sleep-based ordering.

**What this does NOT prove:** all production policy mutators obey guard protocol, history under every concurrency interleaving, savepoint release/reacquire correctness, actual live multi-tenant V5 tables, application HTTP behavior, failure after uncertain JDBC COMMIT, or Core exact result parity. The two connection tests are an isolated lock-order model, not deployment-level revoke acceptance.

## Safety and remaining blocking gates

**Actual database environment:** GitHub Actions creates a fresh `postgres:17` container and `wfproof_test_ci_20261018` database. SQL test code hard-rejects non-loopback URLs, ports `5432` and staging `55426`, a non `wfproof_test_*` database, anything other than PG major 17, missing server-scoped `dxos.wfproof_disposable=on` and non-disposable admin role. The test installer uses disposable-only random `wf6_*` roles/schema names and cleans up in `finally`; the CI discards the entire container. **No connection or mutation to shared staging, Supabase or local Keycloak occurred.**

**Still BLOCKED before any production PR approval/merge:**

1. Replace simplified SQL fixture with exact **six Core-compatible SQL functions** including authoritative V3 snapshot/definition/history, full error ordering, COMMIT ambiguity, typed target/actor/proof boundaries and the permitted CREATE C3 orchestration.
2. Prove real HMAC verifier's key custody, strong caller provenance from verified Spring Security JWT/JWKS, canonical Unicode and full payload, constant-time comparison, nonce/replay model, key rotations and secure SQL owner/privileges. A demo proof is not an independent cryptographic approval.
3. Review and implement additive V5 physical tenant/resource/ACL registry with immutable/tombstone/FK semantics, approved V3/V4 migration-guard compatibility and **all** policy, provisioning, repair and legacy mutators taking the same permanent tenant guard.
4. Extend disposable PG17 tests to complete state-machine parity, full permission visibility/hidden 404, cross-tenant, savepoint-lock release, member/tombstone/binding revoke races, and lost COMMIT/rollback fault injection. Retest against exact production role/SQL code before claiming protection.
5. Integrate genuine `CurrentPrincipal.requireVerified()`, assert filter-chain/JWKS 401/403, authorized 200/201 and hidden 404, Request/JDK21 full-suite regressions. Workflow HTTP remains unregistered.

**Owner decision workflow:** Bảo may self-approve implementation decisions, but test prototype PASS is **not** independent security certification and an unresolved P0 still blocks production claims.

**Final state:** `PROTOTYPE_3_PASS_0_SKIP; REAL_V5_SECURITY_GATE_BLOCKED`. Keep Issue #23 OPEN, PR #36 DRAFT, Issue #22 Workflow HTTP OFF. No Flyway migration, shared staging privilege change, production SQL function, actual key deployment, or merge has occurred.
