# PR 35 final remediation and independent review

Review date: 2026-10-17. Repository: `lexuanbao258-collab/dxos-open-core`.

## Source identity and scope

Fetched remote PR HEAD: **`1997d96bdea50c288f74daee28d0ae4b56afc72f`**. The remote description still named `14b3558`, 676 PASS / 112 SKIP and pending PostgreSQL. At fetch, GitHub reported an open PR with an empty status-check rollup. No successful CI execution is claimed.

`git diff --name-status 14b35586128465d42534b0edc1e700178fed5406..1997d96bdea50c288f74daee28d0ae4b56afc72f` contains documentation/evidence and the PowerShell capture script only. It contains no Java/Gradle/build changes. The prior backend/PostgreSQL execution therefore applies to the fetched Java source, but not to this review's later metadata change.

Fresh tested Java commit: **`be605c803e978ab89be533deccd37b653cb58c9d`**. A subsequent evidence-only commit may advance HEAD without changing Java/build sources. Worktree: `D:\olp\data\.worktrees\pr35-final-remediation`; local review branch: `review/pr35-final-remediation`. The original workspace's staged, unstaged and untracked work was not edited or reset. See [exact changed files](changed-files.txt) and [checkout source SHA-256 manifest](source-sha256.txt). The manifest covers tracked backend Java/Gradle sources and is a checkout-byte manifest, not raw Git blob hashes.

## Findings

**Resolved: metadata allocation before validation.** Previously, encode sorted the complete map, constructed UTF-16 byte arrays/Base64 strings and a JSON tree, serialized the complete JSON byte array, and encoded it again before enforcing `maxMetadataBytes`. Decode also created a complete serialized JSON byte array merely to check size. Oversized caller metadata could amplify allocation before rejection.

The infrastructure codec now computes the complete compact JSON and outer Base64 size using `long` arithmetic and existing String lengths before sorting, string transformation or tree creation. UTF-16 Base64 contributes `4 * ceil(2 * length / 3)` ASCII bytes per string; pair delimiters, commas and the fixed envelope are included. Each entry is checked as it is visited. Serialization writes through a bounded sink to cover extra serializer formatting, and decode counts serialized JSON through a sink without retaining a serialized copy. INVALID_INPUT/UNAVAILABLE mapping and the wire format are preserved. Core still validates and copies the caller map before the adapter is invoked; upstream request parsing and caller-owned objects require their own limits. Operator-selected very large limits can still permit large allocations.

Nine focused codec regressions use small fixtures (largest value 16 KiB) for single oversized values/names, aggregate metadata, content type, exact encoded and decoded byte boundaries, empty maps, UTF-16/case fidelity, formatting expansion and decode without whole-tree byte materialization. Existing adapter coverage rejects oversized metadata before transport. One staging regression verifies EOF consumption, caller stream ownership, bounded disk writes and file cleanup after actual input exceeds the maximum.

**Documented and preserved: staging liveness.** `StagedObjectUpload.create` stops writing at excess input but continues reading to EOF. A stalled or endless input can retain a worker, descriptor and partial staging file indefinitely or consume CPU/network after rejection is inevitable. Aggregate concurrent uploads and crash leftovers can exhaust local resources. The provider exchange has not started, so its absolute deadline does not cover staging. Arbitrary caller streams cannot reliably be stopped by interrupting a Java thread, and the adapter must not close caller input. No consume-to-EOF contract change was made.

The [architecture](../../architecture/supabase-object-storage.md#upload-staging-liveness-and-deployment-protections) specifies actual inbound byte limits, overall upload plus idle/read deadlines with owner-side cancellation, bounded concurrency/admission, a dedicated staging quota and safe orphan cleanup, and reconciliation after unknown mutation outcomes. Those Gateway/deployment protections remain operational requirements, not features implemented by this PR.

**Resolved: stale documentation.** Architecture/PoC/evidence index now describe absolute per-exchange response-body deadlines and distinguish current execution from historical results. PostgreSQL verification at `14b3558` is complete. Earlier uncommitted-source and pending-PostgreSQL statements are explicitly historical. Historical XML/HTML, hashes, skipped lists and run artifacts were not rewritten. The [proposed PR description](pr35-description.md) is for owner review only; remote metadata must not be updated before approval.

## Security regression assessment

| Control | Evidence and limit |
| --- | --- |
| Backend-only credentials | `SupabaseStorageConfiguration` is conditional backend configuration; REST client sets apikey, and only service-role mode adds Bearer. Secret mode has no user JWT. Unit/simulator tests check both modes. No credential values are included here. |
| HTTPS and redirects | Properties reject HTTP, embedded credentials, query/fragment and traversal prefixes. Production client explicitly uses `HttpClient.Redirect.NEVER`. Redirect policy was reviewed in production source; simulator clients are not proof of production TLS configuration. |
| Error/log hygiene | Storage failures expose only stable codes, with no cause, suppressed exceptions or stack trace. Properties stringify as redacted. Production adapter has no request/body/provider logging. Negative tests exercise credential-shaped messages and caller/runtime faults. Deployment HTTP wire/debug logging must remain restricted. |
| Safe keys/traversal | UTF-16 code-unit Base64URL physical keys preserve identity; raw slash, backslash, traversal, percent escapes and Unicode cannot become path syntax. Existing tests cover all 65,536 code units and length rejection. Prefix is not an authorization boundary. |
| CREATE_ONLY races | Provider `x-upsert=false`, no check-then-write and no local locking. Local tests check headers/conflict preservation. Hosted independent-JVM winner/loser race passed historically, not freshly in this review. |
| Version-pinned reads | Info resolves current UUID, exact-version info is revalidated, download uses that UUID plus available If-Match/ETag checks. Tests verify mismatch/vanished-version failure without fallback. Hosted overwrite/delete snapshot fidelity is historical evidence only. |
| Timeout/cancel/close | Existing raw-socket tests cover partial metadata/errors, CREATE_ONLY/OVERWRITE/DELETE acknowledgements and partial/idle lazy reads. Subscription cancellation, waiting-reader wakeup, task removal, close idempotence and large-batch streaming are locally covered. Staging and post-consumption JSON parsing remain outside provider deadlines. |
| Lost acknowledgement | Simulator mutates before withholding acknowledgement; local tests require UNAVAILABLE, changed provider state and one mutation request. There is no automatic application retry or rollback promise. Reconciliation is required. |
| Metadata fidelity and limits | Existing Unicode/case/empty/isolated-surrogate round trips plus nine new codec regressions and adapter no-transport rejection. Input preflight and bounded serialization are adapter-only. Provider JSON allocation is separately bounded by maxResponseBytes. |
| Private bucket/anonymous denial | Historical hosted `bucketPrivateAndObjectCannotBeDownloadedAnonymously` passed and checked private bucket plus anonymous public-path denial. No fresh hosted credentials are available; current deployed bucket state was not revalidated. |
| User authorization | No Core, Workflow, Gateway ACL or HTTP-route change. Backend service credentials can bypass provider user policies; application/Gateway must authorize the logical object/tenant before calling the port. Keycloak remains the identity provider. No Supabase Auth substitution or claim of end-user ACL verification. |

## Fresh execution

Environment: Windows 11 amd64, Eclipse Temurin JDK **21.0.12.1+1-LTS**, Gradle wrapper **8.14.3**, local Docker PostgreSQL **17.11**. Every execution below used Java commit `be605c803e978ab89be533deccd37b653cb58c9d`; the backend source diff against that commit was empty throughout verification.

| Execution | Suites | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused Supabase/Core Data | 7 | 76 | 0 | 0 | 0 |
| All backend tests and build, with disposable PostgreSQL | 51 | 878 | 0 | 0 | 0 |
| PostgreSQL integration subset of backend total | 9 | 192 | 0 | 0 | 0 |
| Hosted Supabase | NOT RUN | No claim | No claim | No claim | No executed report |

Focused command (from `backend/`, with inherited SUPABASE_STORAGE_/DXOS_/WORKFLOW_/SPRING_ variables removed in the child process):

```powershell
.\gradlew.bat test --tests 'com.tricore.dxos.infrastructure.storage.supabase.*' --tests 'com.tricore.dxos.core.data.*' --rerun-tasks --console=plain
```

**BUILD SUCCESSFUL in 13s**, all four tasks executed. [Focused XML](focused.xml), [HTML](focused.html), [empty skip list](focused-skipped.txt). The precommit development run also passed, but is not counted as separate acceptance evidence.

Full regression command (from repository root, same child-process configuration isolation):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/workflow-verify-local.ps1
```

The unchanged, repository-approved script ran `.\gradlew.bat test build --no-daemon` against a uniquely named disposable local Docker PostgreSQL 17.11 container with a loopback-only random port, generated private credentials, separate runtime/migration roles and trusted/wrong-CA TLS fixtures. **BUILD SUCCESSFUL in 3m 14s**; `LOCAL_WORKFLOW_VERIFICATION=PASS tests=878 failures=0 skipped=0 postgres=17.11`. The test task executed (its environment gate disables up-to-date reuse); three compilation/resource tasks were up to date. The script returned exit code 0 after labelled-container shutdown/removal and checked temporary-certificate cleanup. No shared Supabase PostgreSQL connection was made. [Full XML](full.xml), [HTML](full.html), [empty skip list](full-skipped.txt).

PostgreSQL subset: persistence 56; approved migration 19; development smoke 12; event-trigger guard 28; event-trigger review 8; isolation 46; runtime privilege audit 3; schema bootstrap 13; SECURITY DEFINER review 7; total **192**. All nine suites have zero failures/errors/skips. Full and focused suite counters match individual testcase outcomes; the 76 focused cases also pass in the full run. These totals are not added together.

Reports were captured with the unchanged `docs/evidence/issue-26-remediation/capture.ps1`, passing `-OutputDirectory docs/evidence/issue-26-final-review` and `-Run focused` or `full` immediately after each respective execution. Reports omit logs, properties, hostnames, failure details and stack traces. Empty capture-generated `*-source-sha256.txt` files mean no modified/untracked Java/Gradle source relative to the tested commit, not a complete source-tree digest. The separate [source manifest](source-sha256.txt) covers tracked backend Java/Gradle checkout files. [Artifact hashes](artifact-sha256.txt) cover canonical Git blob bytes of all other files in this directory, so they are independent of checkout CRLF conversion. No raw provider logs, credentials or private TLS fixture keys are committed.

## Final scope checks and verdict

`git diff --check`: **PASS**, including explicit whitespace checks on new evidence. Secret/endpoint checks of changed source/docs and fresh artifacts: **PASS**, with no credential-bearing URLs, provider hostnames, JWT/private-key/API-token material or raw provider logging captured. Existing dummy-key literals in unchanged adapter tests are not credentials.

Git diffs against refreshed main confirm no Core, Workflow, Gateway production code or Flyway migration changes. The only production Java modification since fetched HEAD is `SupabaseMetadataCodec`. No HTTP route or Gateway ACL was introduced. All three historical evidence directories have zero diff. See [checks](checks.txt) and [exact changed files](changed-files.txt).

**Verdict: READY FOR INDEPENDENT REVIEW.** This is not merge approval. Fresh hosted verification, Linux/macOS runtime verification, CI status, deployment staging controls and owner approval of the proposed PR description remain separate gates/decisions. The remediation/evidence may be pushed to the existing PR branch, but no remote description/title/labels or issue state may be changed before owner review and approval of the text. No automatic merge is performed.

## Historical evidence (not new executions)

At tested Java commit `14b35586128465d42534b0edc1e700178fed5406`:

- Backend: **50 suites / 868 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, including **9 PostgreSQL suites / 192 PASS**. [Summary](../issue-26-postgres-17.11/verification-summary.md), [XML](../issue-26-postgres-17.11/full.xml), [HTML](../issue-26-postgres-17.11/full.html).
- Hosted Supabase: **19 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, separate from the backend total. [XML](../issue-26-remediation/hosted.xml), [HTML](../issue-26-remediation/hosted.html), [source hashes](../issue-26-remediation/hosted-source-sha256.txt). This was a user-reported execution corroborated by captured reports, not a fresh run here.
- Earlier large-download backend report: **676 PASS / 112 SKIP / 0 FAIL / 0 ERROR**. [XML](../issue-26-large-download/full.xml), [112-name skipped list](../issue-26-large-download/full-skipped.txt). The gated entries expand to 192 real PostgreSQL cases when enabled. Earlier timeout baseline results remain separately labeled in the [historical index](../issue-26-storage.md#historical-records-preserved).

## Remaining gates

Fresh hosted verification of the metadata change is **NOT RUN**: no private authorized provider configuration was available in the process or known worktree `.env` locations. No test execution or pass is claimed for it, and the explicit hosted task was not invoked without configuration. Linux/macOS runtime verification is **PENDING / NOT RUN**. GitHub CI is **NOT RUN by this review; no successful run claimed**. These are separate from executed local test skips. Production staging protections and user-level Gateway ACL must be verified by their owners before exposing uploads to untrusted users.

No merge, Issue #26 closure, changes to PR #33/#34/#36, public routes, Gateway ACL, Supabase Auth, Core/Workflow behavior or Flyway V1-V4 changes are authorized or included. Remote PR description/title remains owner-controlled until the proposed text is approved.
