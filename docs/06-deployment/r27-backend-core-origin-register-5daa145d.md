<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 — Backend/Core file-group source-origin register (technical inventory; RIGHTS PENDING)

**Frozen evidence baseline:** [`main@5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`](https://github.com/tricore-opentrix/dxos-open-core/tree/5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33) on 2026-11-06. This is the **tested R24 candidate baseline**, not necessarily the final R26/CHANGELOG/tagged source SHA. GitHub REST `git/trees/<sha>?recursive=1` reported an **untruncated** tree. **167 tracked blobs under `backend/`, comprising 166 source/text/config/documentation blobs and one upstream binary wrapper JAR**. The technical inventory tracks Git objects, not ownership.

**Purpose:** This is the Backend/Core portion of the maintained source-origin/rights register requested in [R27 Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138). It is staged in [R24 Draft PR #139](https://github.com/tricore-opentrix/dxos-open-core/pull/139) for independent review and reference, **without filling the release acceptance JSON**. It does not address the separate full Frontend, Portal BFF, site, docs, asset, or dependency-rights inventories.

## Evidence and status vocabulary

| Status | Meaning |
| --- | --- |
| `TREE_VERIFIED` | Exact path, mode and Git blob SHA from the untruncated GitHub tree at the frozen baseline; does **not** establish who wrote or can license content. |
| `AI_DISCLOSED_GENERAL` | Bảo states most code under his Backend/Core contribution scope was generated/edited with ChatGPT/Claude/etc.; he generally supplied requirements and did not proactively supply an external repository for copying. This applies **only to his own contribution scope**, not every file in Backend. |
| `GIT_DIFF_VERIFIED` | Exact added/changed source lines confirmed in the named commit; not source originality or legal authorization. |
| `UPSTREAM_GRADLE` | Identifiable Gradle-generated or supplied material; not TRICORE-original. Preserve upstream attribution. |
| `RIGHTS_PENDING` | No independently reviewed per-file/group external-origin, rightsholder, licensing-permission and notice disposition. **Not** `RIGHTS_CLEARED`. |

**Evidence links:** [Bảo's broad AI-assisted declaration](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6663619928); [Bảo's requirements-to-AI/external-repository clarification](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6664569365); [Ánh's first-person Data/Storage declaration](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6663564250); [exact `fcc45fb` and Gradle technical findings](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6663889012); [earlier provenance observations in this PR](r24-rc1-backend-provenance-review.md).

**Ownership caution:** [CONTRIBUTING.md](../../CONTRIBUTING.md) assigns review/coordination roles; it is not a per-file certificate of copyright ownership. A GitHub commit account and repository-wide `SPDX-FileCopyrightText: 2026 TRICORE contributors`/Apache-2.0 markers do not by themselves clear distribution rights. The reported workflow cannot prove the model never generated outside-derived material. Uncertainty is recorded, not filled in from inference.

## Backend inventory grouped by path

| Area (see exact blob list below) | Tracked files | Review route, not ownership | Current origin / rights disposition |
| --- | ---: | --- | --- |
| `main Java core/package-info.java` | 1 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `backend root/build/docs` | 3 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `Gradle Wrapper (upstream/generated)` | 4 | Yến/Bảo: preserve upstream Gradle terms | `UPSTREAM_GRADLE`; preserve scripts/header/REUSE metadata; independent notice check PENDING |
| `main resources (SQL/YAML/manifest)` | 10 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java root` | 1 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java common` | 3 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java application` | 11 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java core/data` | 10 | Ánh for original Data contracts/tests; Bảo for the two fcc45fb test diffs; cross-review | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java core/identity` | 9 | Yến (Identity origin); Bảo only for proven package boundary diffs | `TREE_VERIFIED`; package-info diff verified; Yến's other Identity file rights PENDING |
| `main Java core/workflow` | 15 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java gateway` | 13 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `main Java infrastructure` | 28 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java architecture` | 3 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java application` | 2 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java common` | 1 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java core/data` | 2 | Ánh for original Data contracts/tests; Bảo for the two fcc45fb test diffs; cross-review | `TREE_VERIFIED`, `GIT_DIFF_VERIFIED` for fcc45fb only; broader origin/rights PENDING |
| `test Java core/identity` | 6 | Yến (Identity origin); Bảo only for proven package boundary diffs | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java core/workflow` | 7 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java gateway` | 3 | Bảo core/repo review coordination; actual authors per file UNVERIFIED | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |
| `test Java infrastructure` | 35 | Yến/Bảo to assign per-file author/rightsholder | `TREE_VERIFIED`; original/adapted/AI/third-party + rights and attribution PENDING |

**Count check:** 20 groups; 167 backend Git blobs. The 166 non-JAR blobs are within the tested tracked-source boundary; the one `backend/gradle/wrapper/gradle-wrapper.jar` (upstream Gradle) was excluded from both R24 source-only archives at this baseline (see [R24 source-scope evidence](r24-rc1-source-scope.md)). These are **technical packaging facts only**.

## Specific provenance entries requiring named reviewer disposition

### B01 — Data/Storage test overlap: Bảo's exact fcc45fb diffs

- **`backend/src/test/java/com/tricore/dxos/core/data/InMemoryObjectStorage.java`:** [`fcc45fb65dc6c39ee5f75fd32f99f2a4de05241f`](https://github.com/tricore-opentrix/dxos-open-core/commit/fcc45fb65dc6c39ee5f75fd32f99f2a4de05241f) changes `catch (IOException error)` to `catch (IOException | RuntimeException error)`; normalizes input read failures to `StorageException(INVALID_INPUT)`. **Only this one-line diff is linked to Bảo's commit account**, not the original test fake as a whole.
- **`backend/src/test/java/com/tricore/dxos/core/data/ObjectStoragePortTest.java`:** same commit adds `runtimeInputReadFailureIsSanitizedWithoutClosingInputOrOverwritingObject()` (new 31-line test) checking runtime read exceptions are normalized, no secret error details leak, the caller input remains open and preexisting bytes/metadata are preserved.
- Both files were present in [Ánh-associated `973d39567335e41cb421f04cb6fed5b48f0a9b8d`](https://github.com/tricore-opentrix/dxos-open-core/commit/973d39567335e41cb421f04cb6fed5b48f0a9b8d) before the Bảo-associated patch. **Do not treat Bảo's diff as authorization over Ánh's original content.**
- **Generation/source:** `AI_DISCLOSED_GENERAL` for Bảo's contributions, but exact prompt, model-edited vs manual content and any specific copied/adapted external snippet/template: **UNVERIFIED**. His report that he generally did not feed an external repository is not evidence that none of these lines derive from other sources.
- **Rights/attribution:** `RIGHTS_PENDING`. Bảo permits Apache-2.0 only for his own contribution rights insofar as they exist; Ánh's original tests require her separate statement/review. No new third-party notices have been verified or ruled out.

### B02 — Identity package boundary

- **Path:** `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java`. Original Bảo-associated [`d53a273f63fa07af424825f8775b030d94344fd5`](https://github.com/tricore-opentrix/dxos-open-core/commit/d53a273f63fa07af424825f8775b030d94344fd5) added only the two lines `/** Provider-independent identity and SSO contracts and models. */` and `package com.tricore.dxos.core.identity;`.
- Yến-associated [`fa16f30d`](https://github.com/tricore-opentrix/dxos-open-core/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38) added repo-wide SPDX/Apache-2.0 markers. The file currently declares Apache-2.0 and 2026 TRICORE contributors but no separate validated creator/rightsholder record.
- **Generation/source:** Bảo's `AI_DISCLOSED_GENERAL` is recorded; whether these two exact lines were AI-generated, manually written or adapted externally is **not independently confirmed**. Do not use Bảo's brief package boundary change to claim the other Identity files; Yến must review her own Identity materials and historical UNCERTAIN entries.
- **Rights/attribution:** `RIGHTS_PENDING`. Resolve external textual origin if any, contributor/rightsholder authority and attribution through direct confirmation/independent review.

### B03 — Gradle Wrapper and wrapper configuration (upstream)

- `backend/gradle/wrapper/gradle-wrapper.jar`: upstream Gradle Wrapper JAR, **excluded** from tested source-only archive. [`30597dab62b0602fd59e3617c0701f7b0d059c7b`](https://github.com/tricore-opentrix/dxos-open-core/commit/30597dab62b0602fd59e3617c0701f7b0d059c7b) added it under Bảo's Git account; this is not a Bảo/TRICORE originality assertion. `REUSE.toml` declares Gradle contributors and Apache-2.0 for this JAR.
- `backend/gradlew` (POSIX) and `backend/gradlew.bat` (Windows) are generated upstream launch scripts **included as source text**. Both contain original-authors copyright/license notice and `SPDX-License-Identifier: Apache-2.0`. Preserve those exact upstream headers; do not relabel as solely TRICORE-authored.
- `backend/gradle/wrapper/gradle-wrapper.properties` points at `https://services.gradle.org/distributions/gradle-8.14.3-bin.zip`, in the source-only text scope. Repo `REUSE.toml` annotates it as TRICORE contributors/Apache-2.0 metadata; verify attribution accuracy instead of equating config metadata with upstream code authorship.
- **Disposition:** technical upstream classification VERIFIED; final package text/attribution and any altered upstream script review **PENDING**. No production code is changed by this register.

### B04 — Remaining Backend/Core paths

- Every file in the exact inventory below is `TREE_VERIFIED`, but neither the area/path nor commit author is sufficient to infer origin, copyright owner or scope of Bảo's Apache-2.0 authorization.
- Source paths in `core/data/`: distinguish Ánh's 9 Data/Storage contract file contributions and test contributions, Bảo's two targeted diffs, and any later author edits before determining rights.
- Source paths in `core/identity/` and `infrastructure/identity/`: reconcile Yến's Identity inventory (including her previously UNCERTAIN declarations) and the one narrow Bảo-associated package boundary change.
- Source paths in Workflow, Gateway, Helpdesk, application, infrastructure, SQL and test areas: obtain file/group-scoped origin evidence and individual contributor authority as applicable; do not infer authorship from folder assignment.
- `backend/src/main/resources/db/migration/V1__create_requests.sql` through `V4__seed_workflow_definitions.sql` are checksum-sensitive migration history. Do **not** edit them to add headers. Current REUSE metadata has separate annotations for these exact paths; origin/rights remains to be reviewed.
- Repo `REUSE.toml` also has external Apache metadata for the four protected Helpdesk SQL sources, workflow manifest, and Gradle properties. Sampled direct SPDX headers in Java/Gradle/YAML/docs and the `REUSE.toml` annotations establish **metadata mechanism**, not independent legal rights for all 167 blobs. This register does not represent a full byte-by-byte SPDX audit.

## Exhaustive path/blob inventory

**Interpretation:** Each line is a Git-tracked path and its exact Git blob SHA at the evidence baseline. All project-code lines remain `RIGHTS_PENDING` unless covered by a subsequently reviewed file/group decision. Upstream Gradle is specifically flagged; for any one file, the Git object can be compared without changing bytes.

### main Java core/package-info.java — 1 blob(s)

- `backend/src/main/java/com/tricore/dxos/core/package-info.java` — Git blob `d959a83009dd4bd50c25646bbae3376a79f476e8`

### backend root/build/docs — 3 blob(s)

- `backend/build.gradle` — Git blob `7625b62e9ad46285ad16dcb8668e0481eb38c02f`
- `backend/README.md` — Git blob `6763cd09648480651f0dad97f93be1187c84ae2a`
- `backend/settings.gradle` — Git blob `2c989ab40de0ae3302051a8a68d6ab4948b1a4b0`

### Gradle Wrapper (upstream/generated) — 4 blob(s)

- `backend/gradle/wrapper/gradle-wrapper.jar` — Git blob `1b33c55baabb587c669f562ae36f953de2481846` — **UPSTREAM BINARY; excluded from source-only**
- `backend/gradle/wrapper/gradle-wrapper.properties` — Git blob `d4081da476bb3ee087bb538a0bb6718958af29a4`
- `backend/gradlew` — Git blob `23d15a9367071145e9c79bb4ddf879d1fbe78b5d`
- `backend/gradlew.bat` — Git blob `db3a6ac207e507b0bc1635a9f2c18d3b174e682e`

### main resources (SQL/YAML/manifest) — 10 blob(s)

- `backend/src/main/resources/application.yml` — Git blob `cdbc015bd83136fd6eb73489f60c5945965a2bed`
- `backend/src/main/resources/db/helpdesk/definition.sql` — Git blob `9ddc4bc02d4e8eb98e011d89a7cbfdb9c9ab2933`
- `backend/src/main/resources/db/helpdesk/policy-schema.sql` — Git blob `c591506a289d9793dd84c4df76ac780b09122800`
- `backend/src/main/resources/db/helpdesk/runtime-grants.sql` — Git blob `6a1ca1a5460318f8007f4c33d22eceefec4ef039`
- `backend/src/main/resources/db/helpdesk/schema.sql` — Git blob `d194672649b031a661785806701f35d0537b9f9b`
- `backend/src/main/resources/db/migration/V1__create_requests.sql` — Git blob `4b52d0800873362b3f435bc59e0a23718d8abb32`
- `backend/src/main/resources/db/migration/V2__add_request_workflow.sql` — Git blob `272a6696e4ca7279c6f3fed569cc2ebcc9d0a41a`
- `backend/src/main/resources/db/migration/V3__create_workflow_persistence.sql` — Git blob `a65966f1e69c296938ca302d5fe9006c788d0635`
- `backend/src/main/resources/db/migration/V4__seed_workflow_definitions.sql` — Git blob `cd869689550f310ac746c2a50bec1bbadd8997af`
- `backend/src/main/resources/workflow-event-trigger-manifest.json` — Git blob `ac83b2b1696f061d0bf839d94baff3df3ceb8169`

### main Java root — 1 blob(s)

- `backend/src/main/java/com/tricore/dxos/DxosApplication.java` — Git blob `c47c056da9a16ecfaca33dbbb1351064acd527e3`

### main Java common — 3 blob(s)

- `backend/src/main/java/com/tricore/dxos/common/error/ApiError.java` — Git blob `81692a1f7255b335ac89616ed32d79661a4be152`
- `backend/src/main/java/com/tricore/dxos/common/health/HealthController.java` — Git blob `c2de3e682102a01ea2064cedc937fdbb169cbb41`
- `backend/src/main/java/com/tricore/dxos/common/package-info.java` — Git blob `c07dc5aef2f5766eaa6c16d43a555ed4e6d5aae1`

### main Java application — 11 blob(s)

- `backend/src/main/java/com/tricore/dxos/application/authorization/TenantResourceWorkflowAccessPolicy.java` — Git blob `53eaa287463d9b34e184ba7d116bf1ee9143b8f2`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowAccessDecision.java` — Git blob `065188fbbdd2e1ce0f5613f3c90bbb7cebf6069b`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowAccessException.java` — Git blob `53e78fd1d7c1f016cf8cc6c9266b0bec1e743123`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowAccessPolicy.java` — Git blob `e987b7898e61c9e232651dbf747cf75d0ceb5727`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowAclSnapshotReader.java` — Git blob `b77bbf862840d0eadd912ad6a91813faeaafda42`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowAuthorization.java` — Git blob `19f9488b8abaf8a2fd57aec409bf03fd7a874dbb`
- `backend/src/main/java/com/tricore/dxos/application/authorization/WorkflowOperation.java` — Git blob `a78321651e8b108a6dc2304363d5c29dca8976f9`
- `backend/src/main/java/com/tricore/dxos/application/facility/.gitkeep` — Git blob `6961172bdbdebd09befd9bd5cf622d85d5d9926c`
- `backend/src/main/java/com/tricore/dxos/application/helpdesk/.gitkeep` — Git blob `655c02b74c1fb62cd7e9973925cca5730d3a18f7`
- `backend/src/main/java/com/tricore/dxos/application/identity/CurrentPrincipal.java` — Git blob `79d81f7e9bbc932fda96c3d888aeb4631cd3cae5`
- `backend/src/main/java/com/tricore/dxos/application/package-info.java` — Git blob `9dc6b583dabc5d762d52f30354c3f86fe463dc88`

### main Java core/data — 10 blob(s)

- `backend/src/main/java/com/tricore/dxos/core/data/ObjectContent.java` — Git blob `ed0219e987ffe5fef4762e8feb467d6254e7ff5a`
- `backend/src/main/java/com/tricore/dxos/core/data/ObjectKey.java` — Git blob `0b93e68383aced826251620bc99ec56c94cb174f`
- `backend/src/main/java/com/tricore/dxos/core/data/ObjectMetadata.java` — Git blob `9355cd158919bf6dc10e2e7d77146fc5059c90f1`
- `backend/src/main/java/com/tricore/dxos/core/data/ObjectStoragePort.java` — Git blob `20b495e6bb74074b69ae5c97edc6b94b6f0f7a80`
- `backend/src/main/java/com/tricore/dxos/core/data/package-info.java` — Git blob `95db14f4555b73463030b1cf384f74ce1aca07c4`
- `backend/src/main/java/com/tricore/dxos/core/data/StorageErrorCode.java` — Git blob `42d40baab52652d87626e86a32eb086a943c4731`
- `backend/src/main/java/com/tricore/dxos/core/data/StorageException.java` — Git blob `d47e3666f6ee28a7172f1fe65f94bfcc1a42b057`
- `backend/src/main/java/com/tricore/dxos/core/data/StorageValues.java` — Git blob `ad9870cf377a55ccf3f2f9b2cf50d91a9b7d3f3d`
- `backend/src/main/java/com/tricore/dxos/core/data/StoreMode.java` — Git blob `a199c68d78863c09b475e5a8c39baa84425a5062`
- `backend/src/main/java/com/tricore/dxos/core/data/StoreObjectCommand.java` — Git blob `1933fe012e5a46c415313e3700989c85ea9ca198`

### main Java core/identity — 9 blob(s)

- `backend/src/main/java/com/tricore/dxos/core/identity/AuthenticatedPrincipal.java` — Git blob `e9293a0caa81c62738a6d5d4214c4c2a299e43fd`
- `backend/src/main/java/com/tricore/dxos/core/identity/AuthenticationContext.java` — Git blob `782480d2029c36fd5c2b12198e988f2a6919e9e5`
- `backend/src/main/java/com/tricore/dxos/core/identity/Authority.java` — Git blob `4b5715642df1d65b41974c2b3865ce5eaed18f28`
- `backend/src/main/java/com/tricore/dxos/core/identity/AuthorizationDecision.java` — Git blob `988c922913c4109de57822418a26a7c451870bc7`
- `backend/src/main/java/com/tricore/dxos/core/identity/AuthorizationReason.java` — Git blob `c9b4c2e6267e3e8d7de81400a0e49fea1f768b75`
- `backend/src/main/java/com/tricore/dxos/core/identity/IdentityErrorCode.java` — Git blob `75b80ed21de4e456e43637a129005c18207bed96`
- `backend/src/main/java/com/tricore/dxos/core/identity/IdentityException.java` — Git blob `74b73e4508b1d88c04e4e6641d6c17f72ea02a1a`
- `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java` — Git blob `c57d2207a4eade58e2bf0b03bf6d859be74c7724`
- `backend/src/main/java/com/tricore/dxos/core/identity/RequiredAuthority.java` — Git blob `5a7c87ede726daa4a46d2befa1a8d6f1ceff0459`

### main Java core/workflow — 15 blob(s)

- `backend/src/main/java/com/tricore/dxos/core/workflow/ActorReference.java` — Git blob `b7665cfc72ca4adb891975aa382af23a429eb679`
- `backend/src/main/java/com/tricore/dxos/core/workflow/package-info.java` — Git blob `04dadf22a8aaa65e9a5c85d41349b8707267c26b`
- `backend/src/main/java/com/tricore/dxos/core/workflow/ResourceReference.java` — Git blob `c585fde2976c4b408783760e8656440871525bac`
- `backend/src/main/java/com/tricore/dxos/core/workflow/TransitionCommand.java` — Git blob `7423d511b0b91b076d1ee555ee2681a999c0dd49`
- `backend/src/main/java/com/tricore/dxos/core/workflow/TransitionRecord.java` — Git blob `3f1232e6b1a2dce965d8d8a6eb780f219248ad1a`
- `backend/src/main/java/com/tricore/dxos/core/workflow/TransitionResult.java` — Git blob `1ce520a233a4e9e9d4642ec68fef33599c421689`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowDefinition.java` — Git blob `762ded80eccba58120dc5d1561a22f952dd8853e`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowErrorCode.java` — Git blob `390ecbb2a0e019410fa584d20db23287bc029ed5`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowException.java` — Git blob `cd1a8eb93cfd2ae9b369ddaa5a9dfeefbbabb820`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowInstance.java` — Git blob `85cb008a525a06c3fe489e11c24ff9a9335da1ab`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowLifecycle.java` — Git blob `3f1f779d454aa532736225ec4fead1d5f326e10c`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowPersistencePort.java` — Git blob `b36bd313d7884e5da80266d18af8060e64064020`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java` — Git blob `a89e55681fe1c96c7eb04f55bcb5e2ff68dcdbbe`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowTransition.java` — Git blob `c18f9f4dd6e6835f83e6526af248cf2cb0ede569`
- `backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowValues.java` — Git blob `02ef75385bad49c0fa75f6403cabd69a22c53f12`

### main Java gateway — 13 blob(s)

- `backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java` — Git blob `b6738c5cbc89e4a29ef9b27ca4444661e207c223`
- `backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskExceptionHandler.java` — Git blob `1627692ae174bb36738e8eb1f3c4a1048b37f1da`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/controller/WorkflowController.java` — Git blob `46cd991ef44ff6de247ff253d027a3c3b996a7cf`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/controller/WorkflowExceptionHandler.java` — Git blob `564d0b505879101e9343b40fe57fa93db0b1ac9d`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/ActivateWorkflowInstanceRequest.java` — Git blob `cac531da0630b74f650ac626b2d954d3da74d438`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/CreateWorkflowInstanceRequest.java` — Git blob `5fa760601de1d93d9012ad06cc82c4865bdf6b14`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/ExecuteWorkflowTransitionRequest.java` — Git blob `e7576edcb3d1894af7b27a52efd4eeec009e926f`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/WorkflowHistoryResponse.java` — Git blob `5a6471cb97b4af35cf15215b071e076d501ae638`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/WorkflowInstanceResponse.java` — Git blob `3640af9d0a654a50b16758ddd3837044bc7e1400`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/WorkflowTransitionResponse.java` — Git blob `0a8064dfb56dc323af9b84781d25c92f58cf29c2`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/WorkflowVersionDeserializer.java` — Git blob `478f8a7481eca98570074c86de0c914f63ae8006`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/security/WorkflowGatewayPrincipalGate.java` — Git blob `052a1e1427a838e16221381c115cc64d72138abc`
- `backend/src/main/java/com/tricore/dxos/gateway/workflow/WorkflowJsonConfiguration.java` — Git blob `d73f8b4d7effe7818e08a70a189cd23b338ec1fb`

### main Java infrastructure — 28 blob(s)

- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/package-info.java` — Git blob `a8901597f86a88822666b11b538285b3327e1397`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/AccessTokenTypeValidator.java` — Git blob `4616739ca0cd7d3dcc784074fcae48a97c21c397`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/ApiAuthenticationEntryPoint.java` — Git blob `19818681d920b06eb5c6c3c3bb102236cc46a498`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/AudienceValidator.java` — Git blob `c7f81c187c4669087638e1fd6f9781bdcff5618a`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/ExpirationValidator.java` — Git blob `fe7425ba760d30c36bd9f73ccbd60daf15e9c3d3`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/package-info.java` — Git blob `7ea9dd563380a7d253fcda94e901cbcf974470d6`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfig.java` — Git blob `5b57855538a46d048a358b882fa48309ea3ba033`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipal.java` — Git blob `75a221001441db5eaeb588da2d754c9d5a50b76b`
- `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SubjectValidator.java` — Git blob `a0165f1490694ec792c8eeee8402813d5ef0e2da`
- `backend/src/main/java/com/tricore/dxos/infrastructure/package-info.java` — Git blob `743ace74f4e601902fea45b0cac4816f3b6dc4d3`
- `backend/src/main/java/com/tricore/dxos/infrastructure/persistence/package-info.java` — Git blob `b4a125570c789ef78f1763afc1b2d6d21a2a93bc`
- `backend/src/main/java/com/tricore/dxos/infrastructure/storage/package-info.java` — Git blob `cb61abba92b0686fd505a79cb02145699f2d3549`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskConfiguration.java` — Git blob `c42b025696096c98ffbc166efda800f83d6adc1f`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java` — Git blob `e95395a2374e5abdf16a2c87273f6cc2640f25ac`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistence.java` — Git blob `c91bb96158b2a510276b3623a6f6f442c83cb33a`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowApprovedMigration.java` — Git blob `fd812ea83cbad668f65105798b176a138d5b3ec9`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeRunner.java` — Git blob `0267d6215d2704a6bf60e0ad9c33a8d2c01e8726`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerGuard.java` — Git blob `2e3693ac1c7326271a776efbbe8d07194c5504a5`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerReview.java` — Git blob `e0a89466fd97688f36ce9d86fce7776f334f110c`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowLiveGuard.java` — Git blob `5be5b34eff4e8ff8a20a52c60c2c1e0548fff16b`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowMigrations.java` — Git blob `b40989293a6ef1cdcc62d4873daaea2a6db480a2`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowPersistenceConfiguration.java` — Git blob `99ecd15264b201dd60dfac7cc8b84eb27a97d6e4`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowReadOnlyPreflight.java` — Git blob `4644c54626ab24af8ba60e7489d1e13732dbb5cf`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAudit.java` — Git blob `de70eec1d990a674a0b07a782cfba491563d8193`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchema.java` — Git blob `b5888269747acbf900a1f7f53afffdbb206363be`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaBootstrap.java` — Git blob `802c2139040bb36e8d02d3d3c554b497c53139f1`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReview.java` — Git blob `1832bfdacdf2146ea7b94767e5d913b7a234c264`
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSmokeRunner.java` — Git blob `c131fffee2f6e59e76692bded520901aa6ef6270`

### test Java architecture — 3 blob(s)

- `backend/src/test/java/com/tricore/dxos/architecture/canary/core/ForbiddenCoreCanary.java` — Git blob `a9330d939ef1a675f0a8e533a44272f4a90b422e`
- `backend/src/test/java/com/tricore/dxos/architecture/canary/infrastructure/ForbiddenAdapterCanary.java` — Git blob `cbcf30881b46fd4907539a2f50b015391bfe7666`
- `backend/src/test/java/com/tricore/dxos/architecture/CoreDependencyRulesTest.java` — Git blob `0b27724218cedcc455528caf804ad01dabbe0cca`

### test Java application — 2 blob(s)

- `backend/src/test/java/com/tricore/dxos/application/authorization/TenantResourceWorkflowAccessPolicyTest.java` — Git blob `2c344c7088a8306a498dc5f926a33dd869260a98`
- `backend/src/test/java/com/tricore/dxos/application/authorization/WorkflowAuthorizationTest.java` — Git blob `992bebc99be326cee08a184324c7cacddcf77ac7`

### test Java common — 1 blob(s)

- `backend/src/test/java/com/tricore/dxos/common/health/HealthControllerTest.java` — Git blob `fbb4d20f1413edefb6af760262defda1d21ebc73`

### test Java core/data — 2 blob(s)

- `backend/src/test/java/com/tricore/dxos/core/data/InMemoryObjectStorage.java` — Git blob `364010e2943a67f77b2076e546be45520ef112be`
- `backend/src/test/java/com/tricore/dxos/core/data/ObjectStoragePortTest.java` — Git blob `c4b064741adf6bfe9b2c6f267343bee3c31278a7`

### test Java core/identity — 6 blob(s)

- `backend/src/test/java/com/tricore/dxos/core/identity/AuthenticatedPrincipalTest.java` — Git blob `eae00599f1a2731f6063a22ef3af6bd583d42604`
- `backend/src/test/java/com/tricore/dxos/core/identity/AuthenticationContextTest.java` — Git blob `b41ed80f616924b60add7142714d023fce5121d8`
- `backend/src/test/java/com/tricore/dxos/core/identity/AuthorityTest.java` — Git blob `a12d4b4d17f755ea51443ae7c0a9dd808c0e7e6b`
- `backend/src/test/java/com/tricore/dxos/core/identity/AuthorizationDecisionTest.java` — Git blob `49f8d2dac13256c8473c334240fae71474485f63`
- `backend/src/test/java/com/tricore/dxos/core/identity/IdentityExceptionTest.java` — Git blob `20b2208ee98a7932c7f18b9e7e51287cb4797396`
- `backend/src/test/java/com/tricore/dxos/core/identity/RequiredAuthorityTest.java` — Git blob `37283b7fb00da5fe98dd2b60a2eae6ca07439776`

### test Java core/workflow — 7 blob(s)

- `backend/src/test/java/com/tricore/dxos/core/workflow/InMemoryWorkflowPersistence.java` — Git blob `96c833867060f273d2ff1091592aa76b5d9dcb66`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowContractTest.java` — Git blob `407e5f2ed4977a94dd55cd48b3e98a33339493b4`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowDefinitionTest.java` — Git blob `26d7c26f857e64fe7508cc8b6928e4e6af59ba7b`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowHttpRuntimeTest.java` — Git blob `9ac13bb573d247592645b43117b7dbfdf5689bbb`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowInstanceTest.java` — Git blob `51f0a8fa0ddd254db80efcff75df9cd091895915`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowPersistencePortTest.java` — Git blob `9d0752b226a7c15dc7cbff99388df2e72c697ccd`
- `backend/src/test/java/com/tricore/dxos/core/workflow/WorkflowRuntimeTest.java` — Git blob `d5f192a7040e6b819d2b86b1fec17d459d8648ae`

### test Java gateway — 3 blob(s)

- `backend/src/test/java/com/tricore/dxos/gateway/helpdesk/HelpdeskErrorContractTest.java` — Git blob `3d5018b682e1addbeb862542ebfa4ca662cdbba7`
- `backend/src/test/java/com/tricore/dxos/gateway/workflow/controller/WorkflowControllerTest.java` — Git blob `8eff5776bd18090115bf5f8513632589a8f77ad3`
- `backend/src/test/java/com/tricore/dxos/gateway/workflow/controller/WorkflowHttpExposureTest.java` — Git blob `e62636ebf8948c67466714674658cc4422e546a8`

### test Java infrastructure — 35 blob(s)

- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/AudienceValidatorTest.java` — Git blob `68be940db9b15f131d65f84231acd5e1c30a4d07`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/ExpirationValidatorTest.java` — Git blob `97e680db43b1842bc2e40837197b21e58889b461`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java` — Git blob `70dad237cdb0569ac5c57d239829275c76b24b66`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java` — Git blob `15f11e73b104e34165c9fcfb5738436131a5e07e`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/IssuerAndTimestampValidatorTest.java` — Git blob `900ac0d214b616d0e123d749ebee8f40bdbd103b`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/OidcKeycloakIntegrationTest.java` — Git blob `d52c2c6ef47ff7df44c37b42f43cbe59570708ca`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/RawSubjectJwtDecoderTest.java` — Git blob `9127446b4b16cbbfb803cdd9b7a040cd0ee499a0`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/RawTimestampJwtDecoderTest.java` — Git blob `cdf92da9d8090f28280d1e4b9599a9057de76b29`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfigTest.java` — Git blob `f948ed749c03d9da0013ef3b4b40aaf1e2ce8b10`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipalTest.java` — Git blob `d05c46b9ee31c6465a6dbd48444737ca03e3dd36`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SubjectValidatorTest.java` — Git blob `c1686a52d65439cb4cccc264b94b6873449c4084`
- `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/TokenTypeValidatorTest.java` — Git blob `8aef76e36f58da4dc8ec1dde2d64be6016e40102`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/HelpdeskConfigurationTest.java` — Git blob `fe5a94e5812e2ec5ac59efafcc4b51c3292cb8d4`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/HelpdeskHttpPostgresTest.java` — Git blob `97b16de2ceb88eae5efe75d9da3e22dcd3805aa9`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistenceIntegrationTest.java` — Git blob `46aa4d2fe82c032c042af4e0ccb551b29025f4a7`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowApprovedMigrationIntegrationTest.java` — Git blob `573d7bd9e96138afb4750bf5a8cca777ea2b499c`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowApprovedMigrationTest.java` — Git blob `b2ce4af63a14ed3c4191989171d0512ee9d694a1`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowApprovedSmokeLauncherTest.java` — Git blob `1883d1f1d54ed64808cf61079761915172f78493`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeIntegrationTest.java` — Git blob `701daaa7463e95669808de6fbefd0afadc2a1175`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeRunnerTest.java` — Git blob `8cd2441eb111f1cf9e9263a8b3b7131465f6292e`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerGuardIntegrationTest.java` — Git blob `8e84f735d93eb13b909228319277298397b80921`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerReviewIntegrationTest.java` — Git blob `047242d8d3818ae4cd3de3db5eb1c2e124a7797a`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowIsolationIntegrationTest.java` — Git blob `982f5090d667b6c17dd28d8f9b92ed13adc1ea2c`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowLiveGuardTest.java` — Git blob `ab37bd5941f8b304b325367ee2fe4d9caaef5314`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowReadOnlyPreflightTest.java` — Git blob `6dcd116984a522921b6515e18f59446073e54771`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditIntegrationTest.java` — Git blob `248e73af3713047f7a7645bdcf4f7505e30e826b`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditLauncherTest.java` — Git blob `9e9d99a1d4adfd47bc6ac0d5ac7845d315d97d8a`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditTest.java` — Git blob `752f9e17146dd698ec8d64e7fc6aef0dd4f2b7cb`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaBootstrapIntegrationTest.java` — Git blob `989ea8eb69e1f08df9e184cf0c02976ae2f4e8a1`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaBootstrapTest.java` — Git blob `6168038194a36d240294203d550e3b4e9bbe1e20`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaTest.java` — Git blob `ad26132a7c4fa13f0c0ae2369b3946722cb669ff`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewIntegrationTest.java` — Git blob `238e3e9d0ca2fafb2f892a57f69836118fcd580c`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewLauncherTest.java` — Git blob `4baecaf66187d38bab92b4df56d127fca279fb47`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewTest.java` — Git blob `fc25465fa79c899e60d9721c0fedcb161313dd0e`
- `backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSmokeRunnerEndpointTest.java` — Git blob `481dedc713ddfdf0b3fe2cb4388b896ec95a491a`

## Required independent dispositions / release decision

1. **Bảo**: for B01's **two exact diffs**, B02's two lines, and other contributions he can identify, directly classify AI-generated/AI-edited/manual vs external adapted/copy if known; identify source references/NOTICE exceptions when applicable. If provenance cannot be demonstrated, record `UNKNOWN` and identify review/replace/exclude decision. Broad requirements-to-AI self-report is already linked and must not be converted into a no-third-party warranty.
2. **Yến**: reconcile Identity first-person attestations, 14 historical UNCERTAIN entries, joint/mixed areas, individual authorization and collective `TRICORE contributors` notices; map responsibilities and source origin at file/group level. Record who independently reviewed which disposition.
3. **Ánh**: review original Data/Storage contracts/tests and Bảo's later changes separately. Confirm whether any of these file groups have external adapted material or attribution requirements.
4. **Gradle/SQL**: preserve upstream Wrapper headers and protected migrations byte-for-byte; review the distribution-side notices and metadata without modifying protected files.
5. **R24 dependency:** once contributor evidence and independent reviewers resolve material exceptions, link this register to [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138) and **only then** evaluate [PR #139's source-only acceptance JSON](r24-acceptances/v0.2.0-rc.1.json). Keep its `source_provenance`, `source_license`, checksum/sha fields null and `reviewed_by` empty until actual acceptance evidence exists. Final source SHA may differ following R26/CHANGELOG; repeat Git inventory and candidate digest verification at the frozen SHA.

**Current disposition: `BACKEND_TREE_INVENTORIED / SELECTED_GIT_DIFFS_VERIFIED / SOURCE_ORIGIN_AND_RIGHTS_PENDING`. No legal certification, no release approval, no protected tag authorization, and no binary/GHCR clearance.**
