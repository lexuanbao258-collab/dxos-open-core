# Issue 26 — real Supabase Storage gate

Fetched PR #35 HEAD for this review: `1997d96bdea50c288f74daee28d0ae4b56afc72f`. Its delta from tested Java commit `14b35586128465d42534b0edc1e700178fed5406` contains documentation/evidence only, with no Java/build changes. The large-download remediation is committed. This review adds adapter-only metadata memory bounds and focused regressions; see [final review and current execution status](../evidence/issue-26-final-review/review.md). No merge approval is implied.

Historical Windows verification at the preceding Java source: [full backend and disposable PostgreSQL](../evidence/issue-26-postgres-17.11/verification-summary.md): **50 suites / 868 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, including **9 PostgreSQL suites / 192 PASS**. Separate [hosted Storage XML](../evidence/issue-26-remediation/hosted.xml): **19 PASS / 0 FAIL / 0 ERROR / 0 SKIP**. These results are not new executions of this review's Java change. Linux/macOS runtime verification remains pending; no successful GitHub CI run is claimed.

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

Historical hosted provenance: the latest user-confirmed hosted run covers the large-download Java source committed as `14b3558`; existing Gradle XML corroborates all 19 passing executions. Command: `.\gradlew.bat supabaseIntegrationTest --rerun-tasks --console=plain` from `backend/`, on Windows 11 with Eclipse Temurin JDK 21.0.12.1 and Gradle Wrapper 8.14.3. BUILD SUCCESSFUL in 1m 6s was user reported. Provider identifiers and credentials are omitted. This historical result does not verify later metadata-codec changes; current hosted availability/execution is reported separately in the final review.

## PR 35 timeout and ambiguity semantics

`REQUEST_TIMEOUT` is an absolute budget for each HTTP exchange, starting immediately before send and covering upload, headers and response consumption. Staging is outside that budget. Each info request and the exact-version download gets its own budget. Lazy handles retain the download's original deadline while idle; reads never reset it. Consuming the body after expiration returns sanitized UNAVAILABLE. Close lazy handles promptly, and set the budget to include intended caller delays and download time. JSON responses remain bounded by MAX_RESPONSE_BYTES; transport buffering retains one delivered batch without a payload copy, regardless of batch size. The publisher controls that batch size; there is no fixed byte cap on retained transport memory and no queue of batches.

On expiration the adapter cancels the actual body subscription and releases buffered resources. An incomplete error body is UNAVAILABLE rather than a confirmed provider result. A timed-out CREATE_ONLY, OVERWRITE or DELETE may already have committed at the provider. The adapter never retries these mutations automatically and does not promise rollback. CREATE_ONLY remains provider-enforced, with no check-then-write.

Historical timeout baseline `0202ae8`: **56 focused PASS**, full backend **666 PASS / 112 SKIP / 0 FAIL / 0 ERROR**. Historical large-download `14b3558`: **66 focused PASS**, earlier full backend **676 PASS / 112 SKIP / 0 FAIL / 0 ERROR**, superseded by the later clean-checkout **868 PASS / 0 SKIP** disposable PostgreSQL regression at the same Java commit. The 112 gated entries expand to 192 cases when enabled. Historical XML, skipped lists and provenance remain unchanged; see [evidence index](../evidence/issue-26-storage.md).

Worker JVMs use the current test JVM's JDK, selecting java.exe on Windows and java on Linux/macOS. The historical Windows launch probe and hosted worker concurrency passed. Linux/macOS runtime verification remains pending. PostgreSQL verification at `14b3558` is complete as recorded above; any new execution is identified separately in the final review.

Before deploying untrusted uploads, implement and verify the [staging liveness protections and reconciliation procedure](../architecture/supabase-object-storage.md#upload-staging-liveness-and-deployment-protections): actual inbound byte limits, upload/read deadlines, concurrency admission limits and staging disk quota. Staging still drains to EOF after exceeding the byte limit, outside the provider deadline. No public HTTP route or Gateway ACL is introduced here. Keep Keycloak and perform user/tenant authorization before the storage port; a private bucket or service credential is not user authorization.
