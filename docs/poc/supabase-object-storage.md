# Issue 26 — real Supabase Storage gate

Status: **PENDING VERIFICATION, not approved to merge**. Reviewed timeout remediation baseline: `0202ae87747fee558882c464f1198deb8abec3d3`. Historical hosted result: 19 passed, zero failed/errors/skipped on Windows; BUILD SUCCESSFUL in 54s, supplied by the user. Current Large Download changes are uncommitted: hosted 19 PASS, 0 FAIL, 0 ERROR, 0 SKIP, BUILD SUCCESSFUL in 1m 6s (user reported), with separate focused/full evidence. Future commit SHA NOT YET ASSIGNED. See [current evidence and exact counts](../evidence/issue-26-storage.md).

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

The latest user-confirmed hosted run covers the current uncommitted Large Download changes after baseline `0202ae8`; existing Gradle XML corroborates all 19 passing executions. Command: `.\gradlew.bat supabaseIntegrationTest --rerun-tasks --console=plain` from `backend/`, on Windows 11 with Eclipse Temurin JDK 21.0.12.1 and Gradle Wrapper 8.14.3. Provider identifiers and credentials are omitted. BUILD SUCCESSFUL in 1m 6s (user reported), 19 PASS, 0 FAIL, 0 ERROR, 0 SKIP; sanitized capture verified. Future commit SHA is not yet assigned. The final staging review did not rerun the suite.

## PR 35 timeout and ambiguity semantics

`REQUEST_TIMEOUT` is an absolute budget for each HTTP exchange, starting immediately before send and covering upload, headers and response consumption. Staging is outside that budget. Each info request and the exact-version download gets its own budget. Lazy handles retain the download's original deadline while idle; reads never reset it. Consuming the body after expiration returns sanitized UNAVAILABLE. Close lazy handles promptly, and set the budget to include intended caller delays and download time. JSON responses remain bounded by MAX_RESPONSE_BYTES; transport buffering retains one delivered batch without a payload copy, regardless of batch size. The publisher controls that batch size; there is no fixed byte cap on retained transport memory and no queue of batches.

On expiration the adapter cancels the actual body subscription and releases buffered resources. An incomplete error body is UNAVAILABLE rather than a confirmed provider result. A timed-out CREATE_ONLY, OVERWRITE or DELETE may already have committed at the provider. The adapter never retries these mutations automatically and does not promise rollback. CREATE_ONLY remains provider-enforced, with no check-then-write.

Historical baseline local evidence: 56 focused tests passed; full Java 21 build passed with 666 passed, 112 existing PostgreSQL integration entries skipped, zero failures. Fresh large-download evidence is recorded separately in the current evidence report. Names, reasons and sanitized XML/HTML are linked in the evidence report. Full PostgreSQL regression remains pending approved disposable PostgreSQL with runtime/migration roles and TLS fixtures.

Worker JVMs now use the current test JVM's JDK, selecting java.exe on Windows and java on Linux/macOS. The local Windows launch probe and hosted suite, including worker concurrency, passed; Linux/macOS runtime verification remains pending. The reviewed remediation is committed at `0202ae8`; subsequent large-download changes stop before commit/push. Linux JDK 21 and disposable PostgreSQL checks remain PENDING.
