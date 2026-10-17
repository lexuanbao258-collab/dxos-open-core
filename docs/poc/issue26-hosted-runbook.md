# Issue #26 — Guarded fresh hosted Supabase verification (PROPOSED / NOT RUN)

Source base: PR #41, commit `1488bbbcfdd0a9b493a97049efffbc987b134d1d`, which Bảo verified locally with **886 PASS / 0 FAIL / 0 SKIP** (Windows, JDK21, PostgreSQL17.11). The guarded runner is a **separate Draft PR** with an additional script/documentation commit. Neither PR #41 nor the runner is approved for hosted execution or merge merely because local Backend passed.

## Owner and reviewer sign-off before any hosted write

Bảo must approve one controlled session for the **existing private** `dxos-objects` bucket in the correct Supabase project; Yến has been formally requested as independent reviewer of **four changed files in PR #41**. Review the runner independently as well. Check that provider project/bucket, private credentials and scope are approved; verify the bucket is not public before any write. Do not create a bucket, loosen RLS or policies, change shared PostgreSQL tables/Flyway, or commit any keys.

**No execution is authorized by this document or the creation of the script.** The runner is disabled by default.

## Offline reviewer verification (no Supabase credentials)

**At the remediated PR #42 HEAD:** use a fresh/clean local checkout of the PR branch on **Windows PowerShell 5.1**, without setting any `SUPABASE_STORAGE_*` variables. First parse and self-test this script, then verify default dry-run:

~~~powershell
$script = '.\scripts\supabase-hosted-verify-guarded.ps1'
$tokens = $null
$parseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $script).Path, [ref]$tokens, [ref]$parseErrors)
if (@($parseErrors).Count -ne 0) { $parseErrors | Format-List; throw 'PowerShell parse FAILED' }
powershell -NoProfile -ExecutionPolicy Bypass -File $script -SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File $script
~~~

Expect `OFFLINE_SELFTEST=PASS` and `DRY_RUN_ONLY` separately. Neither command uses `-Execute`, reads API credentials, accesses Supabase, invokes Gradle or starts hosted worker JVMs. `-SelfTest` exercises **synthetic** process snapshots, late orphan/cleanup gates and a temporary generated 19-case JUnit fixture; it **does not** prove real Windows child-process-tree teardown or hosted behavior. Retain the console outputs and exact Git SHA for review.

Before considering `-Execute`, independently test Windows 5.1 Java-version detection, paths with spaces, the ACL setup, an **operator-controlled dummy local process tree** with timeouts/parent-exit, and nonzero exit / cleanup failure handling; submit evidence to a separate reviewer. Do not substitute the synthetic self-test for these system-level checks. Because process enumeration is polling-based, rapidly detached children can evade ancestry discovery: supervise the entire run with an independent external watchdog and a disposable isolated environment. This is a **design limitation**, not a claim that every descendant is safely captured.

## Preflight and execution model

Entry point: [`scripts/supabase-hosted-verify-guarded.ps1`](../../scripts/supabase-hosted-verify-guarded.ps1). Windows PowerShell 5.1 and JDK21 are required; no Linux/macOS support is claimed for the runner.

- **Default invocation:** `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\supabase-hosted-verify-guarded.ps1`. Displays `DRY_RUN_ONLY`; reads no private config, makes no network calls, and writes no objects. A separate `-SelfTest` performs synthetic offline checks, creating and cleaning only its own temporary JUnit fixture directory.
- **Execution opt-in, after approvals ONLY:** `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\supabase-hosted-verify-guarded.ps1 -Execute -OperatorApproved -ApprovedCommit <40-character-checkout-SHA> -MaxMinutes 5 -MaxStagingMiB 8`. Never copy this as a recommendation to run on an unreviewed branch. The exact SHA must be independently reviewed and `git status` clean.
- All `SUPABASE_STORAGE_*` settings from the repo's `.env.example` are supplied via **private process environment**, never in CLI arguments, git, comments, screenshots or raw logs. The runner additionally requires `SUPABASE_STORAGE_EXPECTED_HOST` from independently verified project settings; it checks the configured HTTPS `/storage/v1` host against this value without printing the hostname. The original `SUPABASE_STORAGE_KEY_PREFIX` identifies an approved test base prefix, not other teams' objects.
- `SUPABASE_STORAGE_STAGING_DIRECTORY` must be an **existing, absolute, operator-approved local parent directory**; the runner creates a unique child directory with restrictive ACLs, rather than reusing application/shared staging files. Logs and namespace manifest go to another restrictive temporary directory.
- The script derives `BASE_verify_<random32>`; each hosted JUnit execution then appends `_it_<random32>`. Physical object names begin under `<effective-test-prefix>/v1/`; inspect the private reconciliation manifest for exact namespace. Do **not** delete all keys under BASE, and do not treat prefix as an authorization boundary.
- The wrapper performs its own read-only authenticated private-bucket metadata check before launching Gradle. The PR #41 suite separately does `@BeforeAll` preflight. Unknown status, redirect, unexpected JSON, missing access or wrong project **blocks** the run.
- Hosted Gradle invocation is `supabaseIntegrationTest --rerun-tasks --no-build-cache --no-daemon --max-workers=1 --console=plain`. Child test processes inherit credentials through environment, never Java command-line args. Only `@Tag("supabase-real")` is selected. The expected **19** hosted test executions include 10 race repetitions; expect 19 PASS, no failures/errors/skips.
- The external `cmd.exe`/Gradle descendant tree is polled about every second and known descendants are terminated upon exceeding the **5-minute default** elapsed-time budget, **8 MiB default staging observation threshold**, or **16 MiB private stdout/stderr cap**. Discovered process IDs plus run-specific worker names must both be absent before success. This includes staging time outside the Java provider deadline. A fast detached child can escape ancestry polling; **independent OS-level supervision and operator reconciliation remain mandatory**. Max object bytes in the child process are forced down to **128 KiB** (fixture is 64 KiB). These are **best-effort process/usage guards, not a filesystem-enforced hard disk quota**, and cannot undo a remote mutation.
- Before starting a hosted run, any old `TEST-*.xml` in `backend/build/test-results/supabaseIntegrationTest` must be **archived or cleared by an authorized operator**, rather than silently mixed into the new result. A successful run requires zero-exit Gradle, a fresh **single expected suite** (`SupabaseObjectStorageIntegrationTest`), exactly **19 testcases / 0 failures/errors/skips**, a SHA-256 report hash in the private manifest, zero run-specific workers, zero known remaining descendants and no staging files. The runner emits its final `HOSTED_GATE_RESULT=PASS` **only after** the final checks and deletion of its own verified-empty staging directory. Cleanup uncertainty blocks success. The wrapper never deletes remote objects, never enumerates or sweeps shared prefixes, and preserves private logs/manifest locally. Hosted test code cleans up only its own key after verified protocol/worker termination.

## Recovery and cleanup (fail closed)

If the runner times out, is interrupted, a JVM survives, logs/staging limits are exceeded, bucket preflight fails, or any assertion fails: **DO NOT automatically retry or sweep objects**. Read only the private manifest to identify the exact run prefix; check for worker processes using that prefix, then allow in-flight remote operations to settle. An operator with approved scoped credentials may inventory **only the exact run namespace**, identify late/unknown mutations, reconcile expected vs current versions, and explicitly approve individual remote deletions. Do not touch unrelated BASE keys or change shared project policies. Staging files are only removable after proving that no process is using them; never blanket-delete unverified orphan files.

Test logs are private and may contain unexpected provider data. Do not paste or upload raw logs, exception bodies, endpoint URLs, credentials, or raw XML without reviewing and redacting them. For evidence, capture the exact SHA, run time, command flags, **sanitized 19/19 JUnit counts**, private-bucket preflight conclusion, any reconciliation decisions and the final namespace cleanup disposition. Keep this report separate from the historical hosted 19 PASS on old Java; never use the local **886 PASS** to claim current hosted success.

## Required remaining gates

1. An independent human reviews PR #41 and this runner; no self-approval.
2. On Windows PowerShell 5.1, verify AST parser, `-SelfTest`, default dry-run and a locally controlled process-tree failure scenario, including Java `-version` and path-with-spaces behavior. **NOT YET RUN** by the remote source-editing reviewer; do not claim local success merely from the committed source.
3. Operator confirms authorized project, private bucket, shared-environment rules, scoped credential, capacity, hard storage quota/reserved headroom, process monitoring and explicit rollback/reconciliation owner.
4. Only then execute the hosted run in a clean isolated checkout. Document the actual results for that exact source SHA; inspect leftovers before requesting merge.
5. Merge PR #41 into PR #35 feature branch after its own approvals; subsequently gate PR #35 → `main` on its complete fresh acceptance. No PR merge is automatic.

**Current status: REMEDIATED SCRIPT AWAITING WINDOWS OFFLINE VERIFICATION; NO HOSTED SUPABASE TEST EXECUTED.** The change to script and runbook is not approval to invoke `-Execute`.
