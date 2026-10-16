# Issue 26 — real Supabase Storage gate

Status: hosted PASS for the tested uncommitted PR 35 remediation on 2026-10-16: 19 passed, zero failed/errors/skipped; BUILD SUCCESSFUL in 54s. Branch: feature/issue26-supabase-clean. Original committed HEAD 87d6c62346daf035041fc56689281417f925a316 does not include the fixes. Future remediation commit SHA remains pending. See [current evidence and exact counts](../evidence/issue-26-storage.md).

## Setup

For future runs, use a privately provisioned/approved server-side REST credential for the shared project. Existing bucket: dxos-objects, private. Do not create a new bucket or enable public access. No Supabase Auth user is required. No S3 keys are required by this REST-only implementation.

Supply all SUPABASE_STORAGE_* variables from .env.example to the private process environment. REST_BASE_URL ends in /storage/v1. API_KEY_MODE is secret or service-role. CONNECT_TIMEOUT and REQUEST_TIMEOUT use ISO-8601 durations for the integration worker (for example PT10S, PT60S). STAGING_DIRECTORY must be absolute. Product limits must fit real bucket/proxy settings; the snapshot fixture requires MAX_OBJECT_BYTES >= 65536. Use a dedicated test prefix and sufficient physical-key/encoded-metadata/JSON-response limits. No real endpoint or key belongs in source, logs, screenshots or shell history. Spring Boot does not automatically read .env.

From backend with JDK 21:

~~~powershell
.\gradlew.bat test --tests 'com.tricore.dxos.infrastructure.storage.supabase.*' --tests 'com.tricore.dxos.core.data.*'
.\gradlew.bat supabaseIntegrationTest --rerun-tasks
~~~

Regular test/build exclude supabase-real. Explicit integration invocation fails rather than skips when configuration is absent. Reports: build/reports/tests/supabaseIntegrationTest and build/test-results/supabaseIntegrationTest. Each invocation uses unique namespaces and cleans its test key; interrupted runs may leave test objects for explicit operator cleanup.

## Required acceptance evidence

| Gate | Test coverage |
| --- | --- |
| CREATE_ONLY, loser conflict, winner unchanged | createOnlyAndConflictPreserveWinner |
| concurrent CREATE_ONLY, distinct payload/metadata | concurrentCreatorsAcrossIndependentJvmsHaveExactlyOneWinner, ten repetitions, two child JVMs |
| OVERWRITE content/type/full metadata replacement | overwriteReplacesContentTypeAndEntireCustomMap |
| read and metadata | readMetadataAndRestEnvelopeRoundTrip; zeroByteObjectRoundTrips |
| delete and repeated delete | deleteAndRepeatedDelete |
| REST metadata interoperability | readMetadataAndRestEnvelopeRoundTrip |
| exact version retrieval during replacement | exactVersionInfoAndDownloadNeverReturnReplacement |
| open read during overwrite/delete | sameVersionReadSurvivesOverwriteAndDelete |
| restart durability | durabilityAcrossFreshJvmRestarts |
| private bucket/anonymous denial | bucketPrivateAndObjectCannotBeDownloadedAnonymously |

Concurrency workers use independent clients/processes, with only a start barrier. No storage lock is implemented in the test client. Exactly one STORED and one CONFLICT is required; UNAVAILABLE does not count as conflict. Restart workers exit between store/verify/replace/delete/missing checks; credentials are inherited through environment, not command arguments. This verifies fresh adapter JVMs, not the application's unrelated PostgreSQL startup. Also verify the deployed backend's full restart when its database environment is available.

A purged version may return NOT_FOUND before a read resource exists. Returning replacement content for an old version ID is never acceptable. A resource already opened must preserve matching metadata and bytes across overwrite/delete. If hosted version behavior fails these gates, do not label the adapter ready; reconsider isolated S3 reads and add an explicit real interoperability gate.

The user confirms the hosted run covered the current uncommitted remediation; existing Gradle XML corroborates all 19 passing executions. Command: `.\gradlew.bat supabaseIntegrationTest --rerun-tasks --console=plain` from `backend/`, on Windows 11 with Eclipse Temurin JDK 21.0.12.1 and Gradle Wrapper 8.14.3. Provider identifiers and credentials are omitted. The final staging review did not rerun the suite.

## PR 35 timeout and ambiguity semantics

`REQUEST_TIMEOUT` is an absolute budget for each HTTP exchange, starting immediately before send and covering upload, headers and response consumption. Staging is outside that budget. Each info request and the exact-version download gets its own budget. Lazy handles retain the download's original deadline while idle; reads never reset it. Consuming the body after expiration returns sanitized UNAVAILABLE. Close lazy handles promptly, and set the budget to include intended caller delays and download time. JSON responses remain bounded by MAX_RESPONSE_BYTES; transport buffering is limited to one chunk of at most 64 KiB.

On expiration the adapter cancels the actual body subscription and releases buffered resources. An incomplete error body is UNAVAILABLE rather than a confirmed provider result. A timed-out CREATE_ONLY, OVERWRITE or DELETE may already have committed at the provider. The adapter never retries these mutations automatically and does not promise rollback. CREATE_ONLY remains provider-enforced, with no check-then-write.

Local evidence: 56 focused tests passed; full Java 21 build passed with 666 passed, 112 existing PostgreSQL integration entries skipped, zero failures. Names, reasons and sanitized XML/HTML are linked in the evidence report. Full PostgreSQL regression remains pending approved disposable PostgreSQL with runtime/migration roles and TLS fixtures.

Worker JVMs now use the current test JVM's JDK, selecting java.exe on Windows and java on Linux/macOS. The local Windows launch probe and hosted suite, including worker concurrency, passed; Linux/macOS runtime verification remains pending. No post-commit SHA exists because this work stops before commit/push.
