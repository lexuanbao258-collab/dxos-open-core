# Issue #23 — Stage 6 OLP MVP candidate security evidence (2026-10-20)

**Status: ISOLATED CANDIDATE; not Stage 6 PASS, not S6.1 design-review approval.**

This note records the *scope* of Bảo's Windows/PowerShell test output supplied in the working conversation, together with the additional Stage 6.6 test cases added on the separate implementation branch. It is **not** CI evidence, an independently audited PostgreSQL inventory, a signed test report or a production migration approval.

## Observed test execution (user-supplied local console)

| Candidate / test suite | Repo commit at reported run | Observation | Boundaries |
| --- | --- | --- | --- |
| Stage6CreateGateTest and all ordinary backend tests | `7c7ce22` | Gradle `BUILD SUCCESSFUL` | Java-only; selected counts not verified here |
| Stage6V5PhysicalCandidateIntegrationTest (4 cases) | `c1c419e` | `BUILD SUCCESSFUL`, disposable PG17 container `dxos-stage6-pg17-test` | Candidate V5 under rollback; no Flyway |
| Stage6GuardedCreateCandidateIntegrationTest (8 cases at that commit) | `8fcf506` | `BUILD SUCCESSFUL` on PG17 | One provisional CREATE function, not a production scoped JDBC port |
| Stage6TransactionSafetyIntegrationTest (4 cases) | `6506a97` | `BUILD SUCCESSFUL` on PG17 | Two-session CREATE/revoke and policy audit rollback; not ACTIVATE/TRANSITION |
| Stage6LeastPrivilegeCandidateIntegrationTest (3 cases) | `f585554` | `BUILD SUCCESSFUL` on PG17 | Simulated `SET ROLE`, not true runtime LOGIN |
| Stage6RuntimeLoginPrivilegeIntegrationTest (2 cases) | `045e717` | `BUILD SUCCESSFUL` on PG17 | One ephemeral true LOGIN role and only CREATE SQL candidate |
| All ordinary backend tests after S6.3 Java changes | `bb05663` | `BUILD SUCCESSFUL` | No integrated secured Workflow HTTP/runtime evidence |

The latest Stage 6.6 cases in `Stage6GuardedCreateCandidateIntegrationTest` (the four new cases following `045e717`) are **NOT EXECUTED** until separately run at their exact HEAD. For formal results, collect JUnit XML with explicit tests, failures, errors and skipped counts; a Gradle success banner alone does not prove `skipped=0`.

## Stage 6.6 security checklist: evidence classification

| Yến's requested security evidence | Current scope / evidence | Status |
| --- | --- | --- |
| 401 invalid or missing authentication | Existing verified-principal unit logic; Workflow HTTP deliberately disabled | **OPEN** — genuine HTTP filter-chain test required |
| 403 missing exact authority | CREATE gate Java tests and candidate SQL `FORBIDDEN` | **CANDIDATE** — no HTTP 403 proof |
| 403 missing explicit resource ACL | Owner/member and grant-negative candidate SQL tests | **CANDIDATE** — no HTTP 403 proof |
| Authorized success | Candidate CREATE SQL inserts one initial V3 instance, true LOGIN role test | **CANDIDATE** — not application end-to-end |
| Cross-tenant no leak | Added two-tenant CREATE negative + positive-control test | **NEW TEST / NOT EXECUTED** |
| Owner without ACL denied | Existing candidate CREATE negative test | **CANDIDATE** |
| Revoke race denied | Real two-PG-connection lock-order test | **CANDIDATE** — no ACTIVATE/TRANSITION |
| Unauthorized DB unchanged | Added CREATE-negative assertions covering workflow rows, history, ACL, policy audit, revision | **NEW TEST / NOT EXECUTED** |
| Workflow state + history atomic | CREATE and rollback only, zero expected history | **OPEN** — ACTIVATE/TRANSITION history atomicity required |
| Policy + revision + audit atomic | Rollback and revoke-commit candidate tests | **CANDIDATE** — no guarded policy-admin port yet |

Do not interpret SQL outcomes `HIDDEN`, `FORBIDDEN` or `SNAPSHOT` as observed HTTP statuses. The application/HTTP mapping, production JDBC transaction ownership, owner-approved V5 schema, all six pinned SQL signatures, audited LOGIN grants, and Yến R1–R10 / independent PG security review remain mandatory. Under approved ADR-0025 Option B, runtime DB credentials + connectivity can forge actor/authority assertions; these tests do not remove that known risk.

## Reproduction envelope

Use **only** the explicitly approved PG17 disposable database at `127.0.0.1:55427/stage6_disposable_test`, never the staging PostgreSQL on `55426`. Do not keep `STAGE6_LOGIN_ROLE_TEST_APPROVAL=YES` set after the LOGIN suite. Candidate classes in `src/test` are inert without the scoped environment flags.

For latest CREATE security cases:

```powershell
cd D:\olp\dxos-issue23-stage6-s61\backend
# Explicitly configure STAGE6_DISPOSABLE_PG17_URL, USER, PASSWORD, APPROVAL=YES
.\gradlew.bat test --tests "*Stage6GuardedCreateCandidateIntegrationTest" --no-daemon --rerun-tasks
```

Collect `backend/build/test-results/test/TEST-com.tricore.dxos.infrastructure.workflow.Stage6GuardedCreateCandidateIntegrationTest.xml` (tests / failures / errors / skipped) and record exact git HEAD. Do not merge, migrate, grant runtime production credentials or activate Workflow HTTP on this evidence alone.
