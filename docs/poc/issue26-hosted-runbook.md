# Issue #26 — Guarded fresh hosted Supabase verification (PROPOSED / NOT RUN)

Source base: PR #41, commit `1488bbbcfdd0a9b493a97049efffbc987b134d1d`, which Bảo verified locally with **886 PASS / 0 FAIL / 0 SKIP** (Windows, JDK21, PostgreSQL17.11). The guarded runner is a **separate Draft PR** with an additional script/documentation commit. Neither PR #41 nor the runner is approved for hosted execution or merge merely because local Backend passed.

## Owner and reviewer sign-off before any hosted write

Bảo must approve one controlled session for the **existing private** `dxos-objects` bucket in the correct Supabase project; Yến has been formally requested as independent reviewer of **four changed files in PR #41**. Review the runner independently as well. Check that provider project/bucket, private credentials and scope are approved; verify the bucket is not public before any write. Do not create a bucket, loosen RLS or policies, change shared PostgreSQL tables/Flyway, or commit any keys.

**No execution is authorized by this document or the creation of the script.** The runner is disabled by default.

## Preflight and execution model

Entry point: [`scripts/supabase-hosted-verify-guarded.ps1`](../../scripts/supabase-hosted-verify-guarded.ps1). Windows PowerShell 5.1 and JDK21 are required; no Linux/macOS support is claimed for the runner.

- **Default invocation:** `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\supabase-hosted-verify-guarded.ps1`. Displays `DRY_RUN_ONLY`; reads no private config, makes no network calls, and writes no objects.
- **Execution opt-in, after approvals ONLY:** `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\supabase-hosted-verify-guarded.ps1 -Execute -OperatorApproved -ApprovedCommit <40-character-checkout-SHA> -MaxMinutes 5 -MaxStagingMiB 8`. Never copy this as a recommendation to run on an unreviewed branch. The exact SHA must be independently reviewed and `git status` clean.
- All `SUPABASE_STORAGE_*` settings from the repo's `.env.example` are supplied via **private process environment**, never in CLI arguments, git, comments, screenshots or raw logs. The runner additionally requires `SUPABASE_STORAGE_EXPECTED_HOST` from independently verified project settings; it checks the configured HTTPS `/storage/v1` host against this value without printing the hostname. The original `SUPABASE_STORAGE_KEY_PREFIX` identifies an approved test base prefix, not other teams' objects.
- `SUPABASE_STORAGE_STAGING_DIRECTORY` must be an **existing, absolute, operator-approved local parent directory**; the runner creates a unique child directory with restrictive ACLs, rather than reusing application/shared staging files. Logs and namespace manifest go to another restrictive temporary directory.
- The script derives `BASE_verify_<random32>`; each hosted JUnit execution then appends `_it_<random32>`. Physical object names begin under `<effective-test-prefix>/v1/`; inspect the private reconciliation manifest for exact namespace. Do **not** delete all keys under BASE, and do not treat prefix as an authorization boundary.
- The wrapper performs its own read-only authenticated private-bucket metadata check before launching Gradle. The PR #41 suite separately does `@BeforeAll` preflight. Unknown status, redirect, unexpected JSON, missing access or wrong project **blocks** the run.
- Hosted Gradle invocation is `supabaseIntegrationTest --rerun-tasks --no-build-cache --no-daemon --max-workers=1 --console=plain`. Child test processes inherit credentials through environment, never Java command-line args. Only `@Tag("supabase-real")` is selected. The expected **19** hosted test executions include 10 race repetitions; expect 19 PASS, no failures/errors/skips.
- The whole external `cmd.exe`/Gradle process tree is watched every second and terminated upon exceeding the **5-minute default** elapsed-time budget, **8 MiB default staging observation threshold**, or **16 MiB private stdout/stderr cap**. This includes staging time outside the Java provider deadline. Max object bytes in the child process are forced down to **128 KiB** (fixture is 64 KiB). These are **best-effort process/usage guards, not a filesystem-enforced hard disk quota**, and cannot undo a remote mutation.
- Every run must confirm zero-exit Gradle, fresh JUnit XML exactly **19 PASS**, no run-specific orphan `SupabaseProcessWorker` and no staging files. Only then can its own **empty, newly created staging directory** be removed. The wrapper never deletes remote objects, never enumerates or sweeps shared prefixes, and preserves private logs/manifest locally. Hosted test code cleans up only its own key after verified protocol/worker termination.

## Recovery and cleanup (fail closed)

If the runner times out, is interrupted, a JVM survives, logs/staging limits are exceeded, bucket preflight fails, or any assertion fails: **DO NOT automatically retry or sweep objects**. Read only the private manifest to identify the exact run prefix; check for worker processes using that prefix, then allow in-flight remote operations to settle. An operator with approved scoped credentials may inventory **only the exact run namespace**, identify late/unknown mutations, reconcile expected vs current versions, and explicitly approve individual remote deletions. Do not touch unrelated BASE keys or change shared project policies. Staging files are only removable after proving that no process is using them; never blanket-delete unverified orphan files.

Test logs are private and may contain unexpected provider data. Do not paste or upload raw logs, exception bodies, endpoint URLs, credentials, or raw XML without reviewing and redacting them. For evidence, capture the exact SHA, run time, command flags, **sanitized 19/19 JUnit counts**, private-bucket preflight conclusion, any reconciliation decisions and the final namespace cleanup disposition. Keep this report separate from the historical hosted 19 PASS on old Java; never use the local **886 PASS** to claim current hosted success.

## Required remaining gates

1. An independent human reviews PR #41 and this runner; no self-approval.
2. The runner receives Windows PowerShell dry-run/syntax verification before anyone enables `-Execute`. **NOT RUN** by this review environment.
3. Operator confirms authorized project, private bucket, shared-environment rules, scoped credential, capacity, hard storage quota/reserved headroom, process monitoring and explicit rollback/reconciliation owner.
4. Only then execute the hosted run in a clean isolated checkout. Document the actual results for that exact source SHA; inspect leftovers before requesting merge.
5. Merge PR #41 into PR #35 feature branch after its own approvals; subsequently gate PR #35 → `main` on its complete fresh acceptance. No PR merge is automatic.

**Current status: RUNBOOK/SCRIPT PREPARED FOR REVIEW; NO HOSTED SUPABASE TEST EXECUTED.**
