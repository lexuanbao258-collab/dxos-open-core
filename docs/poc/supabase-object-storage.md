# Issue 26 — real Supabase Storage gate

Status: PASS — the full hosted `supabaseIntegrationTest` suite passed, based on user-provided Gradle output; snapshot consistency and two-JVM CREATE_ONLY concurrency also passed. Recorded 2026-10-16. This staging review did not rerun the hosted suite. Production deployment has not been verified.

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

The user reports that all hosted gates passed in the full suite. Exact run date, project/provider version, JDK, counts and normalized output were not supplied in this staging request. Record those details when the run output is attached; do not infer them from local results. Future reruns must still pass every hosted gate. A passing local simulator or full build alone is insufficient.
