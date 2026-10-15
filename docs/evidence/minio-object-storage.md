# MinIO implementation checkpoint — 2026-10-15

Branch: feature/data-object-storage-adapter. No commit, push, merge, reset or deletion of existing source files performed. Shared Supabase was not contacted.

## Existing changes preserved

Before implementation: four modified tracked files (.env.example, backend/README.md, backend/build.gradle, backend/src/main/resources/application.yml) and 17 untracked Supabase implementation/test/documentation files. There were no staged changes.

SHA-256 comparison after implementation: 116 existing files covering Supabase code/tests/docs, Core and migrations were identical to the inspection baseline. Shared configuration/build/README changes are additive for MinIO; Supabase provider selection, configuration and integration task remain in place. All existing 411 unit tests still pass.

## Implementation

Eight new production classes in infrastructure/storage/minio: adapter, typed properties, conditional Spring configuration, key/metadata codecs, staging, owned read resource and error mapper. AWS SDK v2 2.56.0 with Apache HTTP 5; one attempt, path style, no AWS chunked encoding. Conditional single PUT enforces CREATE_ONLY; OVERWRITE publishes content and complete metadata together. Caller input is staged to EOF and length checked before mutation. Read metadata/content come from one GET. HEAD plus scoped LIST disambiguates missing key/bucket without broad bucket permissions.

Five existing shared files modified: .env.example, backend/README.md, backend/build.gradle, backend/src/main/resources/application.yml, infra/README.md. Added .gitattributes for LF shell scripts. New infra/minio contains source Dockerfile, Compose, scoped policy, private-bucket initialization and start/integration/durability scripts. New architecture and Ubuntu documents describe limits and acceptance gates.

## Verification actually run

JDK: Eclipse Temurin 21.0.12.1; Gradle Wrapper 8.14.3; execution host Windows.

| Check | Result |
| --- | --- |
| gradlew.bat compileJava compileTestJava test build --console=plain | PASS |
| Full unit suite | 437 tests, 0 failures, 0 errors, 0 skipped |
| New MinIO unit tests | 17 PASS |
| New Spring provider/configuration tests | 5 PASS; both configurations loaded, exactly one port when selected |
| New SDK local HTTP wire tests | 4 PASS; conditional header/body, no mutation retry, sanitized 412/500 and single-response GET |
| git diff --check | PASS |
| bash -n on all five new shell scripts | PASS; all scripts LF |
| Docker Compose config --no-interpolate --quiet | PASS; used installed Compose CLI directly, no environment secrets rendered |
| Preservation hashes | PASS, 116/116 unchanged |

Wire testing initially detected SDK AWS chunked encoding (HTTP length differed from staged object length). Configuration was corrected to disable chunking; the final suite verifies the exact Content-Length and bytes. Apache HTTP 5 replaces the deprecated Apache HTTP 4 SDK transport. No deprecation warning in final compilation; the JDK Mockito class-sharing notice is non-fatal.

Gradle initially could not create its wrapper cache lock under restricted execution. It subsequently ran with approved access to the normal Gradle cache/dependency downloads. Final test counts are taken from build/test-results/test/*.xml. Build/test outputs are generated artifacts, not source files. Windows tooling also generated cache artifacts; these were retained under ignored backend/build/windows-tool-cache, without deleting existing files.

## Not run / unresolved acceptance

- minioIntegrationTest: NOT RUN. Docker daemon was unavailable; loopback probes on ports 9000, 9001 and 19000 found no MinIO. No local MinIO test credentials were supplied. As requested, no MinIO integration suite or container provisioning was executed in this environment.
- Source Docker image build and runtime bootstrap: NOT RUN. Compose parsing and shell syntax do not prove successful container startup or policy provisioning.
- Real-server gates pending: CREATE_ONLY across two JVMs; atomic overwrite metadata/content; snapshot completion during overwrite/delete; private anonymous denial; scoped application permissions; strict sequential missing/delete semantics; real metadata round-trip.
- Linux-specific POSIX staging permissions and full backend startup on Ubuntu: NOT RUN. Full application startup requires an independently supplied PostgreSQL environment; no migrations were changed or executed for this work.
- Persistent volume canary across MinIO restart/recreate: NOT RUN. Fresh-JVM integration alone is not proof of server restart durability.
- Community server/client repositories are archived. The built image must be retained by digest, reviewed and scanned before production promotion. Single-node Compose is not an HA/backup solution.

No claim of fully validated production deployment is made until these real-server/Linux gates pass. No Core behavior was weakened to hide an unavailable gate. Delete uses HEAD then DELETE and has no compare-and-swap guarantee against concurrent delete/recreate, as described in the approved plan. Single PUT is limited to at most 5 GiB; default object limit is 64 MiB.

## Ubuntu next steps

Follow ../poc/minio-ubuntu.md: supply private environment credentials, create a mode-0700 staging directory, run bash infra/minio/start.sh, then bash infra/minio/run-integration.sh. Run the canary create/verify scripts around restart and recreate with the same named volume. Record image digest, OS/JDK, actual reports and SHA-256 evidence here. Do not run the existing Supabase integration task against the shared project.

## Exact source/documentation manifest

Modified shared files (existing Supabase additions preserved):

- .env.example
- backend/README.md
- backend/build.gradle
- backend/src/main/resources/application.yml
- infra/README.md

Created files:

- .gitattributes
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioMetadataCodec.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioObjectContent.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioObjectKeyCodec.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioObjectStorageAdapter.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioStagedUpload.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioStorageConfiguration.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioStorageErrorMapper.java
- backend/src/main/java/com/tricore/dxos/infrastructure/storage/minio/MinioStorageProperties.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioHttpTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioObjectStorageContract.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioObjectStorageIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioProcessWorker.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioStorageConfigurationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioStorageTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/storage/minio/MinioTestSupport.java
- docs/architecture/minio-object-storage.md
- docs/evidence/minio-object-storage.md
- docs/poc/minio-ubuntu.md
- infra/minio/Dockerfile
- infra/minio/app-policy.json
- infra/minio/compose.yml
- infra/minio/durability-inside.sh
- infra/minio/durability.sh
- infra/minio/init.sh
- infra/minio/run-integration.sh
- infra/minio/start.sh
