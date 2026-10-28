# Issue #53 — Java-authorized Helpdesk MVP integration gate

**Date:** 2026-10-28  
**Branch:** `feature/issue53-java-helpdesk-mvp-20261028`  
**Pinned clean base:** `main@e06fa429cfd92b032c166eb3ebf32803d13191c2`  
**Initial scope:** integration handoff/checklist only. No Backend implementation is included by this document.  
**Status:** `SOURCE_IMPORT_BLOCKED / TEST_NOT_RUN / LIVE_BLOCKED / DO_NOT_MERGE`

## Decision and provenance

Follow the **2026-10-27 isolated-local-MVP scope decision** in Issues #23 and #40: Keycloak-verified identity and application-level authorization in the Backend, private PostgreSQL 17 persistence using a non-admin runtime role, one reproducible IT Helpdesk HTTP path, and negative security/concurrency tests. The independent physical PostgreSQL D2/D3/D4 ACL engine, six privileged Stage 6 SQL ports, offline r5 operator provisioning, and external production reviewer are **DEFERRED_FOR_PRODUCTION**, not completed or silently waived for production.

- Existing [Draft PR #63](../../pull/63) is a **historical SQL-guarded candidate**, not the source of truth for the Java-authorized redesign. Do not merge it as the new MVP or blindly cherry-pick its Stage 6 security engine.
- PR #63 source baseline reported at `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`. It originally targeted `feature/issue23-p1-02-integrity`, not `main`.
- On 2026-10-28 GitHub comparison, `feature/issue53-helpdesk-backend` was 88 commits ahead of and 10 commits behind `main`. Do not assume direct merge safety.
- The newer Java-authorization/PG17 refactor was **reported as local worktree-only**. Its exact local HEAD, diff, test XML and uncommitted changes **have not been inspected by this GitHub-only handoff**. Never label them pushed or merged without verification.

## Required source transfer — maintain existing worktree intact

1. On the Windows developer machine, run `git status --short`, `git rev-parse HEAD`, `git branch --show-current`, and `git diff --check` **inside the actual Backend worktree**. Record SHA, branch, modified/untracked files, and any hidden ignored files that are relevant to the build. **Do not reset, clean, force-push, or delete the local worktree.**
2. Preserve the local candidate as a reviewable patch or local commit series **without committing credentials/test outputs**. Record source file inventory and test evidence separately.
3. Check out this **clean integration branch in a different directory/worktree**. Compare its main-based source with the local candidate. Transfer only the approved Java-authorization, Helpdesk, minimal JDBC/DDL, disposable PG17 setup, and associated test/docs changes. Keep original code authorship; split into small reviewable commits.
4. **Do not copy** the old `Stage6WorkflowPersistence` / `wf_port_*` runtime security dependency into the redesigned Helpdesk path. Preserve historical Stage 6 sources/tests for separate work but do not register them in the local-MVP request path.
5. Existing Flyway V1–V4 bytes/checksums, Workflow Core contracts and runtime semantics, and shared/staging data must remain unchanged.

## Backend functional and security gates

- [ ] The nine actual protected routes under `/api/v1/mvp/requests` work: create, list, detail, triage, assign, start, resolve, confirm, history.
- [ ] `it-helpdesk@1` exact immutable definition and binding; `SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`. Assignment is an Application command with its **own** version; it does not mutate Workflow history/version.
- [ ] Verified `CurrentPrincipal` from actual signed OIDC/JWT. Ignore client-supplied actor, tenant, owner, `X-User` and `X-Role`. Tenant is Backend-owned. Exact authorities, tenant membership, resource visibility, explicit operation grants and business eligibility are enforced server-side.
- [ ] Deny missing/invalid/expired JWT as **401**; visible-but-unauthorized resources as **403**; hidden, missing, or cross-tenant resources as **404**. Filter list **before** pagination; scope detail/history and allowedActions consistently. Owner identity alone is not an implicit READ grant.
- [ ] Submitter/coordinator/worker eligibility remains distinct from JWT authority. Only authorized coordinator can triage/assign; only current eligible assignee can start/resolve; only original eligible requester can confirm when the contract permits.
- [ ] The persisted definition/version and transition are authoritative. Reject stale `expectedVersion`, stale `expectedAssignmentVersion`, unsupported actions and concurrent losing writes with safe errors (including **409** where contracted).
- [ ] One JDBC transaction/connection protects business facts, resource/definition binding, Workflow instance/state/history and relevant authorization snapshot. Re-check eligibility under appropriate lock before COMMIT. No nested or early commits; inject failures and prove full rollback.
- [ ] Preserve `COMMIT_OUTCOME_UNKNOWN` and reconciliation via safe reads. **No blind retries** after an uncertain write.
- [ ] Local PostgreSQL 17 runs privately on disposable/loopback resources; the application DB identity is not owner/superuser and has only the SQL privileges needed for persistence. Parameterized SQL, no public DB, no secrets in Git/Portal.
- [ ] Default HTTP exposure stays **OFF** until secure local runtime is explicitly enabled; do not register unreviewed generic Workflow/Storage endpoints. No shared Supabase, AWS or production mutation.

## Evidence required before any READY/merge claim

- [ ] Review the exact candidate HEAD/diff; document actual change list and why each file is needed.
- [ ] Run JDK 21 focused + full Backend/Workflow regressions on isolated PostgreSQL 17; record **real** PASS/FAIL/SKIP, command, runtime, exact SHA, and test reports. Historical test counts from PR #63 or different HEADs do not count.
- [ ] Run HTTP negative tests: forged identity, token failures, 401/403/hidden-404, cross-tenant and unauthorized list/history, revocation races, stale versions, concurrent assignment, persistence/activation/history rollback, and uncertain COMMIT.
- [ ] Run real Keycloak/JWKS integration on an explicitly isolated setup and verify a signed principal through the HTTP filter chain. Do not represent fixtures as full IdP E2E.
- [ ] Independent Backend/security review by Yến and API/DTO review with Ánh; resolve findings at the exact reviewed HEAD.
- [ ] After the Backend is independently accepted, integrate PR #70 BFF session and PR #71 Portal adapter in an **isolated integration environment**; verify Portal → BFF → Backend → PostgreSQL with real login. Mock browser tests do not fulfill this gate.
- [ ] Clean-clone/runbook verification for #40. No unapproved Docker/shared database actions.

## Explicit outcome of this initial commit

This branch is initialized at a clean `main` base with **documentation only**. **No Java, SQL or tests were imported, executed or approved by this step.** It is deliberately not ready for merge. The next action requires the local Backend worktree to be inspected and transferred safely.
