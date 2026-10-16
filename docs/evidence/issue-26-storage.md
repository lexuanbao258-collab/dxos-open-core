# Issue 26 / PR 35 remediation evidence

Verification date: 2026-10-16. Branch: `feature/issue26-supabase-clean`.
Reviewed remediation baseline and current committed HEAD: `0202ae87747fee558882c464f1198deb8abec3d3`. Status: **PENDING VERIFICATION; not approved to merge**.

The response-body timeout remediation was committed at that SHA and accepted by the reviewer. The results below under **Historical baseline evidence** describe that remediation, originally tested before its commit. They do not verify the subsequent large-download changes now in the worktree. Historical hosted evidence remains available in Git at 0202ae8; the hosted artifact paths now hold the latest tested Large Download capture, described below. Future commit SHA: NOT YET ASSIGNED.

All three historical source manifests were checked against committed blobs at `0202ae8`: 30 entries verified, zero mismatches. Each manifest has four raw Git blob matches and six matches to the explicit CRLF checkout representation of the committed LF blobs. This is not a claim that every recorded hash equals a raw Git blob hash. See [verification details](issue-26-remediation/baseline-source-verification.txt) and [reproducible verifier](issue-26-remediation/verify-baseline.ps1).

## Current large-download remediation after the reviewed baseline

The uncommitted Java changes remove rejection based on transport batch size. The subscriber retains a cursor over one delivered list of buffers and copies bytes directly into the caller's buffer. It requests one additional delivery only after draining the previous batch. There is no application payload copy, batch queue, or whole-download assembly. A transport delivery may exceed 64 KiB in one buffer or in aggregate. No buffer-size sum or narrowing conversion is needed.

The memory bound is **one transport delivery plus constant cursor state**, not a fixed byte cap on transport-owned memory. The HTTP publisher controls the delivery size and has already allocated it before `onNext`. Even a single batch containing an entire object is retained without making another heap copy. Caller-requested `readAllBytes()` allocations are outside the streaming implementation. The 32 MiB regression generates and checks batches incrementally using an 8,191-byte consumer buffer and verifies at most one outstanding delivery. No number of batches accumulates in application memory. A publisher violating demand is cancelled rather than queued.

`onNext` does not wait for a reader, copy a payload, or iterate through buffer contents. Empty buffers are skipped by the reader. Absolute deadline, scheduled expiration, cancellation, idempotent resource release, sanitized failures, JSON limits, object upload limits, declared download length checks, and exact-version resolution/If-Match/ETag behavior remain unchanged. No Core contract or Workflow code changed. Existing timeout tests remain intact; a new real-socket case stalls after 300,000 verified bytes of a declared 600,000-byte version-pinned download and checks cancellation without retries.

Fresh focused tests: **66 passed, zero failed/errors/skipped**, six suites; `BUILD SUCCESSFUL in 10s`. Commands and runtime are the same isolated Java 21 focused command recorded below. Fresh full build results and review status are recorded in [the current run record](issue-26-large-download/run-record.md). [Focused XML](issue-26-large-download/focused.xml), [focused HTML](issue-26-large-download/focused.html), and [new source hashes](issue-26-large-download/focused-source-sha256.txt) identify this worktree separately from the historical baseline. Any further Java modification requires fresh tests and fresh hashes. Latest hosted Storage verification: **19 PASS, 0 FAIL, 0 ERROR, 0 SKIP**, BUILD SUCCESSFUL in 1m 6s (user reported). Capture SUCCESS; XML timestamp 2026-10-16T12:07:45.304Z. [Hosted XML](issue-26-remediation/hosted.xml), [HTML](issue-26-remediation/hosted.html), [source hashes](issue-26-remediation/hosted-source-sha256.txt), [skip list](issue-26-remediation/hosted-skipped.txt). Focused/full/hosted manifests match the three current Java files. Full local build: **676 PASS, 112 PostgreSQL SKIP, 0 FAIL, 0 ERROR**. New CI verification is NOT RUN.

PostgreSQL regression: **PENDING**, requires approved disposable database, runtime/migration roles, and TLS fixtures. Linux JDK 21 worker verification: **PENDING**. Hosted Storage verification of the new changes: **PASS 19/19**. No shared PostgreSQL connection was made during this review. A [prepared PR description](issue-26-large-download/pr35-description.md) is available for review; remote PR/CI state was not changed.

## Historical baseline evidence at 0202ae8

### Design and scope (historical)

The previous `ofInputStream()` handler completed after headers and left JSON and lazy reads without a body deadline. Its error-body catch also swallowed read failures before classifying the HTTP status. The production handler is now a backpressured `BodySubscriber<InputStream>` with an absolute monotonic deadline and explicit `Flow.Subscription.cancel()`.

`requestTimeout` starts immediately before each HTTP send. It includes upload transmission, headers and body consumption; connect timeout and the existing request header timeout remain enabled. Staging precedes send and is outside this budget. Each metadata lookup and the version-pinned download has its own budget. A lazy download retains the download exchange's original deadline while idle, including already buffered unread bytes. Reads do not reset the budget. After EOF has been consumed, repeated EOF is allowed. Parsing an already consumed bounded JSON body is outside the body-consumption deadline.

One daemon scheduled thread per client serves all active body deadlines. It cancels the HTTP subscription, discards buffered bytes, wakes readers and removes the active response. Cancelled scheduled tasks are removed immediately; EOF, close, error and client shutdown release resources idempotently. A waiting reader also checks the monotonic deadline, so timer scheduling delay cannot extend its budget. There is no thread per read, interrupt-only cancellation or header-only future timeout.

The subscriber requests one chunk at a time and accepts at most 64 KiB per chunk, rejecting oversized delivery before copying it. JSON retains the existing `maxResponseBytes + 1` bounded read and validation; downloads stream through the bounded chunk and existing declared-length checks. Caller-requested bulk allocations such as `readAllBytes()` remain the caller's responsibility. Existing exact-version resolution, version query, If-Match and ETag validation are unchanged. Stream ownership is unchanged. Errors exposed to callers have only the sanitized storage code, with no internal causes or suppressed exceptions.

A body must finish successfully before a provider error can be classified. Stalled 503, 409, 404 and 403 responses all expire as `UNAVAILABLE`. Upload, overwrite and delete are never automatically retried by the adapter. CREATE_ONLY still sends provider-enforced `x-upsert=false`, with no check-then-write.

### Runtime and exact commands

Windows 11 amd64. Java: Eclipse Adoptium / Eclipse Temurin OpenJDK `21.0.12.1+1-LTS`, 64-bit. Gradle wrapper `8.14.3`; launcher and daemon used that JDK. Runtime facts were collected with `java -version` and `.\gradlew.bat --version`.

All verification commands ran from `backend/`. For the focused and full local runs, inherited application/provider/database configuration was removed from each child PowerShell process before Gradle; no `.env` was loaded:

```powershell
Get-ChildItem Env: | Where-Object { $_.Name -match '^(SUPABASE_STORAGE_|DXOS_|WORKFLOW_|SPRING_)' } | Remove-Item
.\gradlew.bat test --tests 'com.tricore.dxos.infrastructure.storage.supabase.*' --tests 'com.tricore.dxos.core.data.*' --rerun-tasks --console=plain

Get-ChildItem Env: | Where-Object { $_.Name -match '^(SUPABASE_STORAGE_|DXOS_|WORKFLOW_|SPRING_)' } | Remove-Item
.\gradlew.bat test build --rerun-tasks --console=plain
```

Hosted Supabase command (with privately supplied approved provider configuration):

```powershell
.\gradlew.bat supabaseIntegrationTest --rerun-tasks --console=plain
```

Hosted result supplied by the user: `BUILD SUCCESSFUL in 54s`. Gradle XML totals: TOTAL 19, PASSED 19, FAILED 0, ERRORS 0, SKIPPED 0. The local hosted XML corroborates these totals and records start timestamp `2026-10-16T11:37:35.099Z`. The reported runtime is Windows 11, Eclipse Temurin JDK 21.0.12.1, Gradle Wrapper 8.14.3. This final staging review did not rerun tests or connect to hosted infrastructure.

The final focused and full commands were rerun after a compatibility refinement: fully received malformed error JSON retains the previous status-based mapping, while incomplete/failed consumption remains UNAVAILABLE. A regression assertion covers fully received malformed 403 versus the stalled 403 socket case. Both final commands forced execution rather than relying on an up-to-date test task; each report was captured immediately afterward. Gradle cache access outside the workspace required approved execution. An initial compiler error in the edited test transport lambda was corrected before these runs. An early focused run had 52 pass / 1 failure caused by inherited `DXOS_OBJECT_STORAGE_PROVIDER`; removing only SUPABASE variables was insufficient. Full process isolation resolved that configuration failure without changing or disabling the test.

| Verification | Reported tests | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Focused, 6 suites | 56 | 56 | 0 | 0 |
| Full local test/build, 50 suites | 778 | 666 | 0 | 112 |
| Hosted Supabase, separate task | 19 | 19 | 0 | 0 |
| Live PostgreSQL regression | Not run | No claim | No claim | 112 local report entries below |

Focused breakdown: Core Data 14; SupabaseStorageTest 23; SupabaseHttpTest 5; SupabaseTimeoutTest 10; SupabaseResponseBodyTest 3; SupabaseProcessLauncherTest 1.

### Required timeout and ambiguity checks

Raw local HTTP sockets send headers plus a partial body and then stall. Budget: 500 ms per exchange; operation assertions: 2 seconds; server-observed cancellation: 2 seconds. Socket reads/accepts have 4-second timeouts. Each test has a 10-second outer bound. Teardown closes sockets, cancels clients and waits at most 2 seconds for each executor/client termination.

The ten socket cases cover 200 partial metadata, 503 partial error, partial 409/404/403 errors, CREATE_ONLY and OVERWRITE acknowledgements, DELETE acknowledgement, partial lazy download and an idle lazy download before its first read. All passed with sanitized `UNAVAILABLE`, exact request counts (one mutation or three version-pinned read requests), server-observed socket EOF, and an empty staging directory. Three subscriber tests verify one-chunk demand, repeated EOF, rejection of a chunk over 64 KiB, idempotent cancellation and scheduled-task removal. Existing simulator CRUD/conflict and exact-version tests also passed.

The CREATE_ONLY, OVERWRITE and DELETE simulator cases apply the provider mutation before losing acknowledgement. All three passed: caller received `UNAVAILABLE`, provider state had changed, no retry occurred, and upload staging was removed. This is an unknown commit outcome to the caller, not guaranteed rollback. These are local transport checks, not proof of hosted durability or hosted CREATE_ONLY atomicity.

The child launcher selects `java.home/bin/java.exe` on Windows and `java.home/bin/java` elsewhere. A local probe starts the actual worker class with the test classpath, confirms the same JDK and exits without provider configuration. Existing environment inheritance, hosted READY/start-barrier protocol and concurrency assertions are preserved. Windows runtime PASS; Linux/macOS runtime NOT EXECUTED.

### Skipped suites and reasons

No test was newly disabled or weakened. These are existing environment-gated PostgreSQL suites, skipped because no approved disposable database with the required runtime/migration roles and TLS fixtures was supplied to the isolated process. Counts are Gradle XML entries; disabled parameterized methods may appear as single entries rather than expanded invocations.

| Suite (package `com.tricore.dxos.infrastructure.workflow`) | Skipped | Unset class-level gate |
| --- | ---: | --- |
| PostgresWorkflowPersistenceIntegrationTest | 25 | WORKFLOW_TEST_JDBC_URL |
| WorkflowIsolationIntegrationTest | 34 | WORKFLOW_TEST_JDBC_URL |
| WorkflowEventTriggerGuardIntegrationTest | 12 | WORKFLOW_TEST_JDBC_URL |
| WorkflowEventTriggerReviewIntegrationTest | 8 | WORKFLOW_TEST_CA_CERT |
| WorkflowApprovedMigrationIntegrationTest | 10 | WORKFLOW_AUDIT_LOCAL_JDBC_URL |
| WorkflowDevelopmentSmokeIntegrationTest | 6 | WORKFLOW_AUDIT_LOCAL_JDBC_URL |
| WorkflowRuntimePrivilegeAuditIntegrationTest | 2 | WORKFLOW_AUDIT_LOCAL_JDBC_URL |
| WorkflowSchemaBootstrapIntegrationTest | 9 | WORKFLOW_AUDIT_LOCAL_JDBC_URL |
| WorkflowSecurityDefinerReviewIntegrationTest | 6 | WORKFLOW_AUDIT_LOCAL_JDBC_URL |
| Total | 112 | Pending PostgreSQL verification gate |

Every skipped test name is in [full-skipped.txt](issue-26-remediation/full-skipped.txt), with the suite-level reason above. No skipped test is counted as passed. The hosted Supabase suite is excluded from the ordinary `test` task by the existing `supabase-real` tag; its nine ordinary methods plus ten repetitions passed in the separate hosted task. Hosted storage success does not satisfy the PostgreSQL regression gate.

### Sanitized artifacts and provenance

[Focused XML](issue-26-remediation/focused.xml), [focused HTML](issue-26-remediation/focused.html), [full XML](issue-26-remediation/full.xml), [full HTML](issue-26-remediation/full.html), [focused source hashes](issue-26-remediation/focused-source-sha256.txt), [full source hashes](issue-26-remediation/full-source-sha256.txt).

Historical hosted XML/HTML and staging-review source hashes are available at commit 0202ae8; the corresponding worktree paths now contain the latest Large Download evidence. Empty focused/hosted skipped lists confirm zero skipped entries.

[capture.ps1](issue-26-remediation/capture.ps1) derives local artifacts from Gradle's `backend/build/test-results/test/TEST-*.xml`, or hosted artifacts from `backend/build/test-results/supabaseIntegrationTest/TEST-*.xml`. It retains suite totals, timestamps, names, durations and outcomes, removing logs, properties, hostnames, failure messages and stack traces. HTML is a sanitized table derived from the same Gradle XML, not an unreviewed copy of console logs. Raw reports remain unstaged under `backend/build/`. Capture invocation from repository root: `powershell -NoProfile -ExecutionPolicy Bypass -File docs/evidence/issue-26-remediation/capture.ps1 -Run hosted` (or `full` / `focused` immediately after the corresponding local run). Current hosted artifacts were sanitized from the latest hosted run; historical hosted artifacts remain available at baseline 0202ae8.

Final diff and modified/new-file secret scan results are recorded in [checks.txt](issue-26-remediation/checks.txt). The scan also covered 117 raw HTML/XML/log/text reports; known dummy negative-test credential URLs were reviewed, and the matching full-report test label was redacted in sanitized XML/HTML. No real credential was detected in the artifacts. Raw reports remain unstaged. `D:\olp\data`, Workflow, Flyway, PostgreSQL schemas and Core contracts were not changed.

### Remaining verification gates / finding status

HIGH response-body cancellation: implemented and locally verified. MEDIUM launcher portability: implementation and Windows worker launch verified; Linux/macOS runtime remain pending. MEDIUM evidence: provenance and counts corrected; focused, full local and hosted results recorded. Full PostgreSQL regression remains pending approved disposable infrastructure.

The historical hosted results cover baseline `0202ae8`; the latest hosted capture covers the subsequent Large Download Java changes. Full PostgreSQL regression requires an approved disposable database, distinct runtime/migration roles and required verified TLS fixtures, including trusted and wrong-CA cases. Linux/macOS runtime verification and production deployment verification remain pending. No infrastructure execution was attempted during this final staging review.
