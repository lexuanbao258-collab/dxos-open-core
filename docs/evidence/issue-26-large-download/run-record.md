# PR 35 large-download verification

Date: 2026-10-16. Branch: `feature/issue26-supabase-clean`.
Reviewed timeout remediation baseline / unchanged committed HEAD: `0202ae87747fee558882c464f1198deb8abec3d3`.
Status: **PENDING VERIFICATION, not approved to merge**. These results cover the subsequent uncommitted worktree, not a new commit or a CI run.

The subscriber now consumes one transport delivery through an iterator and a current ByteBuffer, without allocating a payload copy or summing batch sizes. Demand stays at one delivery; the next request occurs only after the prior delivery is exhausted. A single large buffer and a list exceeding 64 KiB are both accepted. Memory retained from the transport is bounded to one delivery, whose size the publisher controls; this is not a fixed byte bound on transport allocations. Application-owned cursor state is constant. There is no queue or whole-download assembly, producer wait, spool, or added thread. Deadline/cancellation paths drop both cursor references.

Fresh runs on Windows 11 amd64, Eclipse Temurin JDK `21.0.12.1+1-LTS`, Gradle wrapper `8.14.3`:

| Run | Suites | Tests | Passed | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Focused | 6 | 66 | 66 | 0 | 0 | 0 | BUILD SUCCESSFUL in 10s |
| Full test/build | 50 | 788 | 676 | 0 | 0 | 112 | BUILD SUCCESSFUL in 28s |
| Hosted Supabase | 1 | 19 | 19 | 0 | 0 | 0 | BUILD SUCCESSFUL in 1m 6s (user reported) |

Both commands ran from `backend/` in child PowerShell processes, without loading `.env`. Application/provider/database configuration was removed before Gradle. These focused/full commands did not access hosted services or shared databases. The subsequent hosted Storage run is recorded below; no shared PostgreSQL connection was made. Initial sandbox invocation could not create the Gradle cache lock; the actual runs used approved cache access. No failed Java test run occurred. One attempted Python edit could not run because Python was unavailable; the actual changes were applied with the patch tool before testing.

```powershell
Get-ChildItem Env: | Where-Object { $_.Name -match '^(SUPABASE_STORAGE_|DXOS_|WORKFLOW_|SPRING_)' } | Remove-Item
.\gradlew.bat test --tests 'com.tricore.dxos.infrastructure.storage.supabase.*' --tests 'com.tricore.dxos.core.data.*' --rerun-tasks --console=plain

Get-ChildItem Env: | Where-Object { $_.Name -match '^(SUPABASE_STORAGE_|DXOS_|WORKFLOW_|SPRING_)' } | Remove-Item
.\gradlew.bat test build --rerun-tasks --console=plain
```

Focused suite totals: Core Data 14; SupabaseStorageTest 23; SupabaseHttpTest 5; SupabaseTimeoutTest 11; SupabaseResponseBodyTest 12; SupabaseProcessLauncherTest 1. Existing snapshot/version tests and all ten prior socket timeout cases passed without weakening their deadlines. The old oversized-rejection test was replaced with successful delivery coverage. Ten additional executions versus the baseline comprise nine net additional subscriber tests and one additional socket test.

Subscriber coverage includes:

- A 262,161-byte single read-only ByteBuffer with exact output.
- A 200,003-byte aggregate delivery using slices, empty buffers and a direct buffer.
- A 33,556,224-byte download in 256 generated batches of 131,079 bytes. An 8,191-byte scratch buffer checks every byte across batch boundaries; demand assertions verify at most one outstanding delivery. The test does not allocate the complete object.
- Partial consumption followed by idempotent close, late delivery after close, and sanitized provider error.
- Absolute expiration of a completed but unread large delivery, and a provider stall after 300,000 bytes.
- Closing a reader after a large delivery, rejection of unsolicited extra delivery without queuing, and 10,000 empty deliveries without recursive demand growth.

The new real-socket test checks 300,000 exact bytes from a declared 600,000-byte version-pinned object, then stalls. It retains the 500 ms exchange budget, 2-second operation and socket-cancellation bounds, exact three requests, and staging cleanup checks. This verifies actual HttpClient cancellation as well as subscriber behavior. Existing maxObjectBytes validation, declared-length validation, If-Match/ETag checks and sanitized storage exceptions were not modified.

[Focused XML](focused.xml), [focused HTML](focused.html), [full XML](full.xml), [full HTML](full.html), [full skipped names](full-skipped.txt). The 112 skipped entries are the existing gated PostgreSQL suites; reasons and suite counts remain in [the baseline report](../issue-26-storage.md#skipped-suites-and-reasons). Skipped entries are not counted as passed. The hosted task remains separately excluded by its existing tag.

[Focused source hashes](focused-source-sha256.txt) and [full source hashes](full-source-sha256.txt) agree for all three changed Java files. They hash exact worktree bytes at the corresponding executions. No Java modification followed these runs. Further Java modifications require new tests and hashes. The remaining source is inherited from the reviewed baseline. Historical focused/full artifacts remain in issue-26-remediation; historical hosted XML and manifest remain available in Git at baseline 0202ae8. The hosted paths in that directory now contain the latest Large Download capture. The [baseline verification](../issue-26-remediation/baseline-source-verification.txt) checks all 30 historical entries, with explicit raw versus CRLF representation provenance. Latest hosted verification covers this new code: 19 PASS, 0 FAIL, 0 ERROR, 0 SKIP. The sanitized hosted XML records 2026-10-16T12:07:45.304Z; the user reports BUILD SUCCESSFUL in 1m 6s and successful evidence capture. [Hosted XML](../issue-26-remediation/hosted.xml), [HTML](../issue-26-remediation/hosted.html), [source hashes](../issue-26-remediation/hosted-source-sha256.txt) and [empty skip list](../issue-26-remediation/hosted-skipped.txt) corroborate the capture. All three manifests match the exact current Java worktree bytes. Future commit SHA: NOT YET ASSIGNED.

Sanitized artifacts were captured immediately after each final run using:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File docs/evidence/issue-26-remediation/capture.ps1 -Run focused -OutputDirectory D:/olp/issue26-verify/docs/evidence/issue-26-large-download
# After the full build, the same command with -Run full.
```

The capture omits logs, properties, hostnames, messages and traces, and redacts credential-shaped negative-test URL labels. Raw reports remain ignored under `backend/build/`. No real credential or endpoint is included in these records.

External gates: Linux JDK 21 worker runtime **PENDING**; full PostgreSQL regression **PENDING** approved disposable database, distinct runtime/migration roles, trusted and wrong-CA TLS fixtures; hosted Storage checks for the changed Java **PASS 19/19**; new CI verification **NOT RUN**. The PR description is prepared locally in [pr35-description.md](pr35-description.md); remote PR and CI metadata were not accessed or changed. Ready to hand off for Linux/PostgreSQL checks: **YES**, once approved infrastructure is provided. Merge approval remains pending.

No Core contracts, Workflow code, migrations, MinIO, AWS SDK code or `D:\olp\data` changed. No commit, push, merge or rebase was performed.
