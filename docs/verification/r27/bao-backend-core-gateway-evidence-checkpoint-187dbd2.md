<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->



**Document ID:** `R27-EVD-003`  
**Document version:** `0.1.0` (metadata baseline; existing evidence retained)  
**Document status:** `In Review` (Draft PR #139)  
**Evidence verification:** `PARTIAL` — only explicitly described technical checks may be treated as PASS  
**Rights disposition:** `PENDING` — no file/portion clearance or public release approval  
**Evidence owner:** Bảo — evidence coordination (NOT a personal signed declaration)  
**Independent reviewer:** Yến — independent evidence review  
**Tracking issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Document PR:** [#139](https://github.com/tricore-opentrix/opentrix/pull/139)  
**R24 source-only candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Metadata boundary:** these labels route this document only; original claims, reviewer decisions and source-rights obligations are unchanged.# R27 / #138 — Bảo Backend/Core/Gateway source-origin evidence checkpoint (UNSIGNED / RIGHTS PENDING)

**Review state:** technical Git/source-only scope PASS; contributor method partly reported; **third-party source origin, fixture lineage, per-portion licensing authority and attribution NOT CLEARED**. This is a *maintainer-prepared factual evidence index*, **not Bảo's signed declaration, a transfer or grant of rights, a plagiarism search, a legal opinion, or a reviewer approval**. Do not mark any UNKNOWN as NONE.

## 1. Exact R24 package boundary

- Frozen candidate Git source: [`main@187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`](https://github.com/tricore-opentrix/opentrix/tree/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e).
- **167 Backend tracked Git blobs**: 166 UTF-8/text/config/source files included in SOURCE-ONLY `source/backend/`, plus **one Gradle upstream `backend/gradle/wrapper/gradle-wrapper.jar` EXCLUDED**. These are file-inclusion counts, **not 167 files authored or licensed by Bảo**.
- Two actual independent manual candidate runs: [42244683510](https://github.com/tricore-opentrix/opentrix/actions/runs/42244683510) and [42245290247](https://github.com/tricore-opentrix/opentrix/actions/runs/42245290247). SHA-256 of each verified `dxos-open-core-0.2.0.tar.gz`: `93e6e0ba5d708120c6ec494af3e50c8959cccb4c442a8bba9a2e9c617c2d448f`.
- Verified global scope: 347 Git blobs, 346 shipped source/text Git paths and ten ancillary archive metadata/compliance entries (356 regular files total). [Exact 347-path ledger](v0.2.0-source-only-git-rights-ledger-187dbd2.csv) and [independent artifact checks](../../06-deployment/r24-v0.2.0-source-only-two-run-verification-187dbd2.md).
- **Ownership caveat:** [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) assigns Bảo core tests/review/admin responsibilities; it does not establish copyright ownership or licensing authority over all 166 shipped Backend paths. Yến and Ánh have distinct content contributions, including Identity and Data/Storage.

## 2. Bảo's *reported* workflow (only what was actually confirmed)

1. [General statement](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663619928): in Bảo's Backend/Core work, code was **largely produced or edited with ChatGPT/Claude/other AI** following his requirements and coordination; he does not claim to have written every line by hand.
2. [General outside-input clarification](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6664569365): for his *typical workflow* he did not deliberately supply an external repository for copying. This **is not** evidence that no third-party snippets/examples ever entered a specific output.
3. [Per-file report](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6677124150): ID-09 and ID-11 were primarily ChatGPT/Claude-assisted two-line Identity package additions.
4. [Per-file follow-up](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6689684471): ID-28 and ID-29 each were **“chủ yếu AI”** (primarily AI-assisted).
5. **Additional limited recollection, 2026-11-06 chat:** specifically asked whether external GitHub/Stack Overflow/tutorial code, JWT/Keycloak examples or fixtures had been supplied to AI **for ID-28/ID-29**, Bảo responded **“hmm không nhớ rõ”**. Record this field **UNKNOWN / NOT RECALLED**, *not* `NO_EXTERNAL_INPUT`, `NO_COPY` or `CLEAR`. This answer does not establish what the AI output might have incorporated.

These are development-method and recollection reports only. There is no fresh file-by-file ownership attestation, no blanket rights grant, and no demonstrated third-party-source similarity clearance.

## 3. Four exact Identity/Gateway file identities and open questions

| ID / path | Git blob at frozen source | Content/provenance evidence | What is still unresolved |
| --- | --- | --- | --- |
| **ID-09** `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java` | `c57d2207a4eade58e2bf0b03bf6d859be74c7724` | Bảo-associated `d53a273f`; primarily ChatGPT/Claude-assisted two-line addition | External examples/adaptation, any notices, specific licensing authority **PENDING** |
| **ID-11** `backend/src/main/java/com/tricore/dxos/infrastructure/identity/package-info.java` | `a8901597f86a88822666b11b538285b3327e1397` | Same Bảo-associated `d53a273f`; primarily ChatGPT/Claude-assisted two-line addition | External examples/adaptation, any notices, specific licensing authority **PENDING** |
| **ID-28** `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java` | `70dad237cdb0569ac5c57d239829275c76b24b66` | Bảo-associated `f11461d`, `f9b9568`, `b797b99`, `b370574`; primarily AI-assisted; `@WebMvcTest` mocks `JwtDecoder`; `signed-read-token` is synthetic | Origin of example/mock/claims/fixture patterns; external inputs **NOT RECALLED**; individual license and notices **PENDING** |
| **ID-29** `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java` | `15f11e73b104e34165c9fcfb5738436131a5e07e` | Bảo-associated `e5793ef`, `8905e00`, `f0b71c8`; primarily AI-assisted; accesses `OIDC_TEST_ACCESS_TOKEN` at runtime and does not embed a real token | External Keycloak/JWT examples, token/fixture design origin; external inputs **NOT RECALLED**; individual license and notices **PENDING** |

The two Gateway tests being present in the source-only archive **does not mean** an actual Keycloak integration execution was verified. See [four-file worksheet](../../06-deployment/r27-bao-identity-gateway-source-rights-worksheet.md) and [Gateway technical source check](../../06-deployment/r27-gateway-identity-test-technical-crosscheck-5daa145d.md). Yến's later metadata-only change is not Bảo's original file content; authorship and source rights must be checked separately.

## 4. Shared-origin, third-party and other Backend/Core work

| Group | Verified scope | Disposition still needed |
| --- | --- | --- |
| **Data/Storage overlap** `fcc45fb` | Bảo-associated diffs in `backend/src/test/java/com/tricore/dxos/core/data/InMemoryObjectStorage.java` and `ObjectStoragePortTest.java` build on Ánh's pre-existing code from `973d395`; do **not** attribute the full two files to Bảo. | Review only Bảo's changed lines/test, AI/external inputs, Bảo's actual rights; independently retain Ánh's authorizations/notices |
| **Workflow/Core/Gateway/application/infrastructure and other Backend paths** | Historical [167-blob / 20-group Backend inventory](../../06-deployment/r27-backend-core-origin-register-5daa145d.md) reconciled to candidate SHA; each exact path/blob is in the [347-path ledger](v0.2.0-source-only-git-rights-ledger-187dbd2.csv). | Identify which portions Bảo actually contributed or merely coordinated/reviewed; resolve each group's known external derivations and actual licensable rights; don't assign all to Bảo from team role |
| **Gradle upstream wrapper** | Binary `backend/gradle/wrapper/gradle-wrapper.jar` excluded; text `backend/gradlew`, `gradlew.bat` and wrapper properties **included**. Scripts identify upstream authors and license in headers. | Preserve upstream scripts/bytes and notices; verify their actual distribution attribution requirements. They are **not** Bảo-authored |
| **Flyway SQL and third-party dependencies** | Historical migration V1–V4 are checksum-sensitive; [`REUSE.toml`](../../../REUSE.toml) is metadata only; [generated third-party notices](../../../THIRD-PARTY-NOTICES.md) list dependency license metadata. | No unreviewed SQL byte/header edits; check source origin/rights and any applicable upstream license texts/NOTICE separately; metadata is not legal proof |
| **Prior 37 exceptions / project-wide contributors and assets** | Referenced in #138 review; multi-owner and favicon/Frontend/Portal work extend beyond Bảo's Backend scope. | Yến/Ánh to reconcile exact affected paths and independently disposition relevant exceptions; **not all can be resolved from Bảo's Git role** |

## 5. Specific review questions and release gates

- **For Bảo to clarify directly where possible:** which files/portions he created, modified or only reviewed; whether known tutorials/snippets/templates/third-party fixtures were copied or adapted; source URLs and existing upstream terms; the legal scope (if any) he personally can authorize under Apache-2.0; any notices. If not remembered, use **UNKNOWN**, not an invented negative answer.
- **For Yến and Ánh to independently check:** Git content vs metadata commits, actual file/portion provenance, plausible external-source similarity, test fixtures/claims, Gradle original notices, cross-author Data/Storage rights, and any required remediation (replacement/exclusion/attribution). If contributors cannot clear a significant source input, keep the release blocked and decide a documented remediation.
- **Not yet proved:** independent full rights disposition for 166 shipped Backend paths; licensing authority for each relevant contribution; completeness of third-party attribution/NOTICE. The upstream Gradle binary is excluded, but the text wrapper files still require license observance.
- **No release permission:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138) remains OPEN; [#139](https://github.com/tricore-opentrix/opentrix/pull/139) remains DRAFT. The stable acceptance JSON must not be completed or marked accepted based on this checkpoint. If remediation changes the frozen source SHA or date-sensitive release inputs, re-freeze and repeat the two stable candidate runs. No tag, GitHub Release or GHCR publish.

**Verdict:** `TECHNICAL_PROVENANCE_AND_PACKAGE_SCOPE_INDEXED / PRIMARY_AI_METHOD_REPORTED_FOR_ID_09_11_28_29 / ID_28_29_OUTSIDE_INPUTS_NOT_RECALLED / RIGHTS_AND_ATTRIBUTION_PENDING`.
