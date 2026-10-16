# Issue 26 / PR 35 remediation evidence

Verification date: 2026-10-16. Branch: `feature/issue26-supabase-clean`.
Reviewed baseline and HEAD before verification: `87d6c62346daf035041fc56689281417f925a316` (`git rev-parse HEAD`). This replaces the obsolete branch/base and historical test totals in the previous checkpoint.

These results cover the tested uncommitted remediation worktree. The original committed HEAD `87d6c62346daf035041fc56689281417f925a316` does not include these fixes. The focused and full run SHA-256 manifests match all ten modified/new Java files in the staging review. The user confirms the hosted run tested this same uncommitted remediation; its XML independently confirms the totals below. The hosted source manifest was captured during this staging review, not during the hosted execution. Only documentation/evidence changed during finalization; Java sources were left unchanged. No commit, push, merge or rebase was performed. Future remediation commit SHA: PENDING until committed; record that SHA and its correspondence to the staged source manifests after commit.

## Design and scope

The previous `ofInputStream()` handler completed after headers and left JSON and lazy reads without a body deadline. Its error-body catch also swallowed read failures before classifying the HTTP status. The production handler is now a backpressured `BodySubscriber<InputStream>` with an absolute monotonic deadline and explicit `Flow.Subscription.cancel()`.

`requestTimeout` starts immediately before each HTTP send. It includes upload transmission, headers and body consumption; connect timeout and the existing request header timeout remain enabled. Staging precedes send and is outside this budget. Each metadata lookup and the version-pinned download has its own budget. A lazy download retains the download exchange's original deadline while idle, including already buffered unread bytes. Reads do not reset the budget. After EOF has been consumed, repeated EOF is allowed. Parsing an already consumed bounded JSON body is outside the body-consumption deadline.

One daemon scheduled thread per client serves all active body deadlines. It cancels the HTTP subscription, discards buffered bytes, wakes readers and removes the active response. Cancelled scheduled tasks are removed immediately; EOF, close, error and client shutdown release resources idempotently. A waiting reader also checks the monotonic deadline, so timer scheduling delay cannot extend its budget. There is no thread per read, interrupt-only cancellation or header-only future timeout.

The subscriber requests one chunk at a time and accepts at most 64 KiB per chunk, rejecting oversized delivery before copying it. JSON retains the existing `maxResponseBytes + 1` bounded read and validation; downloads stream through the bounded chunk and existing declared-length checks. Caller-requested bulk allocations such as `readAllBytes()` remain the caller's responsibility. Existing exact-version resolution, version query, If-Match and ETag validation are unchanged. Stream ownership is unchanged. Errors exposed to callers have only the sanitized storage code, with no internal causes or suppressed exceptions.

A body must finish successfully before a provider error can be classified. Stalled 503, 409, 404 and 403 responses all expire as `UNAVAILABLE`. Upload, overwrite and delete are never automatically retried by the adapter. CREATE_ONLY still sends provider-enforced `x-upsert=false`, with no check-then-write.

## Runtime and exact commands

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

## Required timeout and ambiguity checks

Raw local HTTP sockets send headers plus a partial body and then stall. Budget: 500 ms per exchange; operation assertions: 2 seconds; server-observed cancellation: 2 seconds. Socket reads/accepts have 4-second timeouts. Each test has a 10-second outer bound. Teardown closes sockets, cancels clients and waits at most 2 seconds for each executor/client termination.

The ten socket cases cover 200 partial metadata, 503 partial error, partial 409/404/403 errors, CREATE_ONLY and OVERWRITE acknowledgements, DELETE acknowledgement, partial lazy download and an idle lazy download before its first read. All passed with sanitized `UNAVAILABLE`, exact request counts (one mutation or three version-pinned read requests), server-observed socket EOF, and an empty staging directory. Three subscriber tests verify one-chunk demand, repeated EOF, rejection of a chunk over 64 KiB, idempotent cancellation and scheduled-task removal. Existing simulator CRUD/conflict and exact-version tests also passed.

The CREATE_ONLY, OVERWRITE and DELETE simulator cases apply the provider mutation before losing acknowledgement. All three passed: caller received `UNAVAILABLE`, provider state had changed, no retry occurred, and upload staging was removed. This is an unknown commit outcome to the caller, not guaranteed rollback. These are local transport checks, not proof of hosted durability or hosted CREATE_ONLY atomicity.

The child launcher selects `java.home/bin/java.exe` on Windows and `java.home/bin/java` elsewhere. A local probe starts the actual worker class with the test classpath, confirms the same JDK and exits without provider configuration. Existing environment inheritance, hosted READY/start-barrier protocol and concurrency assertions are preserved. Windows runtime PASS; Linux/macOS runtime NOT EXECUTED.

## Skipped suites and reasons

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

## Sanitized artifacts and provenance

[Focused XML](issue-26-remediation/focused.xml), [focused HTML](issue-26-remediation/focused.html), [full XML](issue-26-remediation/full.xml), [full HTML](issue-26-remediation/full.html), [focused source hashes](issue-26-remediation/focused-source-sha256.txt), [full source hashes](issue-26-remediation/full-source-sha256.txt).

[Hosted XML](issue-26-remediation/hosted.xml), [hosted HTML](issue-26-remediation/hosted.html), [hosted staging-review source hashes](issue-26-remediation/hosted-source-sha256.txt). Empty focused/hosted skipped lists confirm zero skipped entries.

[capture.ps1](issue-26-remediation/capture.ps1) derives local artifacts from Gradle's `backend/build/test-results/test/TEST-*.xml`, or hosted artifacts from `backend/build/test-results/supabaseIntegrationTest/TEST-*.xml`. It retains suite totals, timestamps, names, durations and outcomes, removing logs, properties, hostnames, failure messages and stack traces. HTML is a sanitized table derived from the same Gradle XML, not an unreviewed copy of console logs. Raw reports remain unstaged under `backend/build/`. Capture invocation from repository root: `powershell -NoProfile -ExecutionPolicy Bypass -File docs/evidence/issue-26-remediation/capture.ps1 -Run hosted` (or `full` / `focused` immediately after the corresponding local run). Hosted artifacts were sanitized from the existing hosted XML during this staging review.

Final diff and modified/new-file secret scan results are recorded in [checks.txt](issue-26-remediation/checks.txt). The scan also covered 117 raw HTML/XML/log/text reports; known dummy negative-test credential URLs were reviewed, and the matching full-report test label was redacted in sanitized XML/HTML. No real credential was detected in the artifacts. Raw reports remain unstaged. `D:\olp\data`, Workflow, Flyway, PostgreSQL schemas and Core contracts were not changed.

## Remaining verification gates / finding status

HIGH response-body cancellation: implemented and locally verified. MEDIUM launcher portability: implementation and Windows worker launch verified; Linux/macOS runtime remain pending. MEDIUM evidence: provenance and counts corrected; focused, full local and hosted results recorded. Full PostgreSQL regression remains pending approved disposable infrastructure.

The newly supplied hosted results explicitly cover the current uncommitted remediation, superseding the historical hosted claim. Full PostgreSQL regression requires an approved disposable database, distinct runtime/migration roles and required verified TLS fixtures, including trusted and wrong-CA cases. Linux/macOS runtime verification and production deployment verification remain pending. No infrastructure execution was attempted during this final staging review.
