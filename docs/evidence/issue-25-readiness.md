# Issue #25 — PostgreSQL JDBC evidence index

**2026-10-28 MVP documentation update.** This file now serves as a short, provider-neutral evidence index. The prior remote-service-specific migration notes, endpoints and operator instructions have been removed from the active source tree to prevent accidental reuse. Their original bytes remain accessible through the immutable Git history at [the pre-cleanup main snapshot](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/docs/evidence/issue-25-readiness.md). Those historical instructions must **not** be executed for the OLP MVP.

## Evidence boundaries
- The production `WorkflowPersistencePort` JDBC implementation in `main` supports PostgreSQL through standard JDBC and immutable V3/V4 definitions.
- Prior development reports describe local JDK 21/PostgreSQL integration regression, transactional instance/history persistence, concurrency and rollback. Those results apply **only** to the exact historical SHAs in their original logs; they are **not** fresh test results from this cleanup.
- Current MVP must be validated on **isolated PostgreSQL 17** using [the local verification runbook](../../backend/README.md) and `scripts/workflow-verify-local.ps1`.
- Full Helpdesk/Keycloak/Portal E2E and Local FileSystem persistence acceptance are **PENDING**. Do not promote historical evidence to current LIVE or CI acceptance.

## Safety invariants
- Do not modify applied Flyway V1–V4, re-run already-applied migrations, or use `flyway repair` to reconcile an unintended mismatch.
- Preserve strict JDBC transaction and confirmed-COMMIT semantics; on `COMMIT_OUTCOME_UNKNOWN`, reconcile using authoritative reads without blind retries.
- Use a private PostgreSQL service with separate migration/operator and least-privilege application runtime identities.
- Do not expose PostgreSQL to browsers, or infer application authorization from database credentials alone.
