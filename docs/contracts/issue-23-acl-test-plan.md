# Issue #23 — ACL test plan and executable decision matrix

**Status: PREPARATION ONLY; not a PostgreSQL ACL acceptance result.**
Works with the already-approved product policy in [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), the [ACL review worksheet](issue-23-acl-review-worksheet.md), and the [V5+ schema proposal](issue-23-acl-v5-schema-review.md).
No migrations, shared Supabase actions, fake production principals, Workflow API registration, or edits to Core/Flyway V1–V4 are included here.

## Currently executable without database or OIDC

JUnit: `backend/src/test/java/com/tricore/dxos/application/authorization/WorkflowAclDecisionMatrixTest.java`.

- A **64-case decision matrix** covers 4 Workflow operations (CREATE, ACTIVATE, TRANSITION, READ) × 4 policy decisions (ALLOWED, FORBIDDEN, HIDDEN, UNAVAILABLE) × 4 authority fixtures (exact, none, unrelated operation, wildcard).
- The existing production `WorkflowAuthorization` class is under test, but `WorkflowAccessPolicy` and the principal are **synthetic, test-only fixtures**. An ALLOWED resource response grants access only with the exact operation authority; hidden/forbidden/unavailable decisions always deny. The policy must be called to resolve visibility even if authority is absent.
- 4 null-policy-return tests and 4 provider-exception tests must sanitize/fail closed; 1 synthetic hidden-vs-missing check returns the same `HIDDEN` decision. **Total intended: 73 JUnit parameterized invocations** (the real JUnit report is authoritative, not this arithmetic).
- The existing `WorkflowAuthorizationTest` and `WorkflowHttpExposureTest` remain required. These pure unit tests **cannot** establish that resource membership, database grants, tenant isolation, locks, verified JWT, no data leak, or HTTP behavior actually work.

### Run focused tests on Windows / Java 21

From an up-to-date checkout of PR #36:

```powershell
cd D:\olp\dxos-gateway-acl
git pull --ff-only
git rev-parse HEAD
git status --short
cd backend
.\gradlew.bat test --tests '*WorkflowAclDecisionMatrixTest' --tests '*WorkflowAuthorizationTest' --tests '*WorkflowHttpExposureTest' --rerun-tasks --no-build-cache --no-daemon --console=plain
```

The command uses no configured DB or cloud credentials. Record exact HEAD, build result, JUnit XML test counts/failures/skips and environment. A Gradle `BUILD SUCCESSFUL` verifies only the selected focused tests. Until the user runs it (or an independent CI run exists), **new code is unverified**.

## PostgreSQL ACL acceptance suite — specifications, NOT executable yet

**Gate:** Only implement after the ACL schema, migration, transactional coordinator, error bridge, and mutation/privilege model receive review approval. Use disposable PostgreSQL 17.x with a fresh private schema and dedicated least-privilege roles; never shared Supabase. No `@Disabled` stubs disguised as tests, and no false GREEN from a fake in-memory ACL.

| Case group | Inputs / concurrent steps | Required observation |
| --- | --- | --- |
| A1 tenant membership | OIDC actor from wrong tenant, missing membership, revoked membership, valid membership | No membership or cross-tenant => hidden/no data; valid membership still not sufficient to act |
| A2 owner distinction | Resource owner with active membership, no explicit CREATE/READ grant | Owner may have visibility according to approved policy, but cannot operate Workflow without explicit ACL + exact authority |
| A3 ACL precision | For all CREATE/ACTIVATE/TRANSITION/READ: grant, revoke, wrong operation, wrong actor, wildcard authority | Only exact authority **AND** resource grant allow; revoke denies; no wildcard or case normalization |
| A4 definition binding | Correct tenant/resource type/definition ID/version vs wrong version/tenant, inactive or revoked binding | CREATE requires exact active binding; binding revoke stops new instances but does not independently revoke operations on existing ones |
| A5 tombstone/identity | Tombstone resource; attempt normal read/write/history; try reusing same resource ID or tenant transfer | Deny normal Workflow; immutable global identity never silently rebinds to a new tenant |
| A6 legacy migration | Existing V3 instance refers to a resource without new authoritative registry | Fail closed; no inferred tenant or unreviewed backfill |
| A7 SQL privileges | Runtime role attempts unauthorized ACL change/read bypass, schema/role escape, direct policy writes | Database rejects unapproved routes; migration privileges distinct from runtime; every mutation entry point follows tenant guard protocol |
| B1 revoke wins | Connection A locks tenant guard FOR UPDATE, revokes ACL/membership and commits; B attempts guarded workflow write afterwards | B denies without state/history write, no stale prior grant |
| B2 workflow wins | Connection A locks guard FOR SHARE, checks ACL, holds during Workflow write; B tries revoke FOR UPDATE; A commits, then B proceeds | B cannot commit revoke before A's authorized write commits/rolls back |
| B3 other revocation races | Repeat B1/B2 for membership, owner/visibility, definition binding, tombstone and history read | Deterministic ordering; no post-revoke read or write outside approved semantics |
| B4 lock order/timeouts | Multi-row operations, deadlock, lock timeout, policy provider failure | Stable lock order; fail closed and categorized errors; no hidden snapshot/history |
| B5 guarded reads | Force policy revoke and read/history under two JDBC connections and synchronization barriers | Protected snapshot materialized only after guarded authorization in consistent snapshot; no pre-guard stale repeatable-read snapshot |
| C1 hidden existence | Known inaccessible vs nonexistent instance/resource; CREATE duplicate probe incl. concurrent same ID | Same safe external status/body, no resource oracle and no leak via Location/history |
| C2 ID ownership | Client sends instanceId in create; server-generated IDs; collision; lost response | Reject client ID; no duplicate oracle; explicit idempotency contract; do not blindly retry uncertain commits |
| C3 error bridge | Confirmed rollback following HIDDEN/FORBIDDEN/UNAVAILABLE; failed rollback and injected uncertain COMMIT | Retain sanitized typed decision only if rollback known; COMMIT_OUTCOME_UNKNOWN supersedes uncertain outcomes, no auto retry |
| D1 actual Identity | Verified OIDC/JWKS valid/expired/wrong issuer/audience/signature; forged X-User/X-Role | Real filter chain: verified issuer+subject only; invalid/absent token 401, no writes |
| D2 HTTP integration | Visible but forbidden, hidden vs nonexistent, valid authorized calls and Request regression | Appropriate sanitized 403/404, no protected read/write leak, Request endpoints unchanged |

### Concurrency harness acceptance requirements

1. Use **two independently acquired physical PostgreSQL JDBC connections** (not two operations silently sharing an outer Spring transaction).
2. Coordinate test steps with bounded `CountDownLatch`/`CyclicBarrier` and explicit lock acquisition/COMMIT barriers; do not rely on `Thread.sleep` or timing races as proof.
3. Enforce statement/lock deadlines so broken tests fail rather than hang; assert blocker order and persisted state after fresh-process reads.
4. A policy mutation must take **FOR UPDATE on the same permanent tenant guard row** that guarded Workflow operations hold **FOR SHARE through durable commit**. Prove all admin/job routes obey it.
5. Check instance state, runtime version and transition history, plus no unauthorized row exposure; a thrown error alone is insufficient.
6. Cover WRITE and READ/HISTORY: do not materialize protected results from a snapshot older than a completed revoke.
7. Force known rollback and unknown-commit scenarios separately. Never map `COMMIT_OUTCOME_UNKNOWN` to retryable success or to 403/404 merely because a policy check previously failed.

### Hard blocking dependencies

- [ ] Physical ACL DDL/FKs/unique constraints/privileges approved; separate additive V5+ migration on disposable database only.
- [ ] A narrow transaction-capable coordinator uses one physical JDBC connection for final policy guard and Workflow write; `PostgresWorkflowPersistence` currently commits inside its port methods.
- [ ] Typed error bridge preserves HIDDEN/FORBIDDEN/UNAVAILABLE after confirmed rollback and `COMMIT_OUTCOME_UNKNOWN` if uncertain; cannot just throw `WorkflowAccessException` inside existing JDBC `transaction()`.
- [ ] Identity PR #33 reviewed: verified issuer+subject/authorities; Spring filter-chain and actual JWKS tests.
- [ ] New PostgreSQL concurrency/integration tests implemented and passing, plus full JDK21 build, before considering production Workflow HTTP registration.

**No test here demonstrates production ACL or PostgreSQL races until those integration tests actually exist and pass.**
