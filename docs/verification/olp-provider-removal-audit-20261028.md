# OLP MVP — hosted-provider removal audit (2026-10-28)

**Decision:** the DX-OS OLP submission has exactly one intended storage architecture: **PostgreSQL 17 (private local JDBC) + local MinIO Object Storage (Docker)**. Keycloak remains the identity provider. No Supabase or externally hosted DB/object-storage provider is supported in the MVP. The selected MinIO adapter is still Draft PR #34 and needs real-container tests; the former filesystem-only PR #43 was superseded and closed without merge.

**Status:** `DOCS_AND_BACKLOG_UPDATED / JAVA_SOURCE_CLEANUP_PENDING / TESTS_NOT_RUN / DO_NOT_MERGE`.

## Completed on GitHub

- Closed, without merging, provider-specific PRs [#35](https://github.com/lexuanbao258-collab/dxos-open-core/pull/35), [#41](https://github.com/lexuanbao258-collab/dxos-open-core/pull/41), [#42](https://github.com/lexuanbao258-collab/dxos-open-core/pull/42). Immutable commits/comments remain as historical evidence and are **not** MVP deployment choices.
- Retargeted Issues #25/#26 to standard PostgreSQL JDBC and local MinIO Object Storage, respectively; refreshed the Core integration and release acceptance criteria in #27/#40.
- Updated active root/Backend/scripts README, generic PostgreSQL Gradle task wording and the Issue #25 evidence index to remove obsolete hosted setup and hardcoded database endpoint instructions.
- No Flyway V1–V4 migration bytes, credentials, shared databases, Git history or provider infrastructure were changed.

## Remaining source blockers — must be removed or made provider-neutral before merge

The following sources in the **pre-cleanup main baseline** still encode the former remote-provider/host-specific contract, and have **not** been edited or verified in this docs-first change:

- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSmokeRunner.java`: `requireEndpoint` matches a hosted-provider URL.
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeRunner.java`: remote-host pattern and environment project-ref gate.
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaBootstrap.java`: hardcoded former database host.
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowLiveGuard.java`: checks former hosted-domain suffix.
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowReadOnlyPreflight.java`: hosted-only endpoint validation.
- `scripts/workflow-approved-migrations.ps1`, `scripts/workflow-approved-smoke.ps1`, `scripts/workflow-schema-bootstrap.ps1`: historic remote-host/credential/WARP guards.
- Launcher/unit tests such as `WorkflowApprovedSmokeLauncherTest`, `WorkflowApprovedMigrationTest`, `WorkflowSchemaBootstrapTest`, `WorkflowDevelopmentSmokeRunnerTest`, `WorkflowRuntimePrivilegeAuditLauncherTest` contain provider/host fixture assumptions. Inspect **all** test/source references before deleting a launcher or class.

**Do not blindly delete the Java classes:** local workflow/regression tests may call their helper methods, and a code deletion that makes tests fail is not a successful removal. Refactor to **loopback/private PostgreSQL 17 only** or delete obsolete entry points together with dependent tasks/tests, preserving the generic JDBC persistence and transaction guarantees. Keep the production-safe default OFF and non-superuser DB restrictions.

## Required independent verification

1. Check the **actual latest branch/worktree** and protect uncommitted work; do not reset/clean or force-push.
2. On a separate branch based on this cleanup PR, inventory `rg -n -i 'supabase|zsfctpnitkdztlfhzqgw|WORKFLOW_DEV_PROJECT_REF|WORKFLOW_SMOKE_PROJECT_REF' .` on tracked source/docs/config. Classify and remove active references; avoid rewriting immutable Git history/comments.
3. Verify full JDK21 compile/test, isolated disposable PostgreSQL 17 integration suites, local bootstrap, restart, permissions and transaction/unknown-COMMIT regressions. Record exact SHA, command, PASS/FAIL/SKIP.
4. Ensure obsolete Supabase launchers/tasks cannot run, obsolete provider-specific tests are replaced with local-focused negatives, and no live endpoint or privileged credential has become less protected. Preserve the selected MinIO S3-compatible SDK and adapter (PR #34) instead of deleting them during Supabase cleanup.
5. Review with Yến for security and Ánh for Portal/file-access boundaries. Independently verify local storage PR #43 and Portal/Backend E2E; this cleanup does not mark them PASS.
6. Only then mark this PR ready for review/merge and update release docs/CI for a single provider choice: local MinIO. Real MinIO integration and persistence acceptance must be independently recorded on PR #34.

**No tests executed by this GitHub-only documentation cleanup. No code source cleaned yet.**
