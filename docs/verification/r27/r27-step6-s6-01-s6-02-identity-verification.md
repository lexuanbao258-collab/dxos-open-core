<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27-EVD-007 — S6-01/S6-02 Identity G3/G4 Evidence Reconciliation

**Document ID:** `R27-EVD-007`  
**Version:** `0.1.0`  
**Document status:** `Draft` — coordinator-produced technical reconciliation; **not a new signed first-person declaration**  
**Evidence verification:** `PARTIAL` — 24/24 Git blobs reconciled; provenance of external examples, AI output and fixtures not independently established  
**Rights disposition:** `PENDING` — **zero** rights dispositions cleared by this review  
**Evidence coordinator:** Yến (`@phanyen06ym-ien`)  
**Independent source-rights reviewer:** Ánh (`@ngang02012007-crypto`), excluding any portion she contributed to  
**Canonical issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Historical Identity tree:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`  
**Frozen R24 source tree:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Related step-6 document:** [R27-EVD-006, PR #149 at `329670d`](https://github.com/tricore-opentrix/opentrix/blob/329670d5768c34020828523c65a321494a5b1518/docs/verification/r27/r27-step6-contributor-provenance-verification.md)  
**Per-file audited results:** [S6-01/S6-02 24-file matrix](r27-step6-s6-01-s6-02-identity-file-verification.csv)

## 1. Exact scope and verification performed

The [historical G3/G4 file-origin matrix](https://github.com/tricore-opentrix/opentrix/blob/2844530125ef884a047f976a91b3202103da3c91/docs/verification/r27/identity-g3-g4-per-file-source-origin-matrix-5daa145d.csv) at [Draft PR #142](https://github.com/tricore-opentrix/opentrix/pull/142) contains **8 G3 files (ID-12–19)** and **16 G4 files (ID-20–27 and ID-30–37)** in Yến's Git-associated scope. Bảo's ID-09, ID-11, ID-28 and ID-29 are **not** included; [his separate evidence request](https://github.com/tricore-opentrix/opentrix/pull/149) retains them.

For each of these **24 files**, the GitHub repository contents API returned the **same blob SHA-1 at both immutable commits**, and the historical blob matched the existing per-file source-origin matrix: `24/24 HISTORICAL_BLOB_MATCH`, `24/24 R24_CANDIDATE_BLOB_MATCH`, `0 differences`. This validates the file-identity reconciliation **between these two source trees** only. It does not prove the historical provenance of text, the origin of model-generated expressions, copyright ownership, absence of external adaptation, or that a distinct future distribution contains the same bytes. No test suite was run in this review; no public-code similarity scan was performed.

The companion CSV preserves the per-file ID, full path, frozen blob, matching baseline result, observed material, fixture handling, and pending evidence request. All technical checks refer to **read-only GitHub source inspection** rather than runtime validation. The existing 357-row rights register is canonical and **not changed**.

## 2. S6-01 — G3 infrastructure/security (8 of 8)

| IDs | Examined code and scope | What is verified technically | Still unverified |
| --- | --- | --- | --- |
| ID-12, ID-14, ID-15, ID-18 | Access-token type, audience, expiration and subject validators | Immutable Git blob match; Spring Security Jwt/OAuth2 validator API usages visible | Actual upstream examples or adapted tutorial text, model-output ancestry, precise hunk rights and NOTICE |
| ID-13 | `ApiAuthenticationEntryPoint.java` | Git blob match and prior Codex proposed-edit **line correspondence** documented | Full-hunk source match, actual patch execution, original implementation source, individual licensing authority |
| ID-16 | `SecurityConfig.java` | Git blob match; Spring Security/Nimbus configuration and project validators; prior Codex evidence | Intermediate edits/full hunks, external Spring/Keycloak/OIDC examples if used, upstream rights and NOTICE |
| ID-17 | `SpringSecurityCurrentPrincipal.java` | Git blob match; JWT/principal integration | Specific implementation inputs and rights per portion |
| ID-19 | `package-info.java` | Git blob match; Java package documentation | Actual prose/documentation source and licensed scope |

**Existing AI evidence:** [14 correlated historical Codex tool calls across five G3/G4 files](https://github.com/tricore-opentrix/opentrix/blob/2844530125ef884a047f976a91b3202103da3c91/docs/verification/r27/identity-g3-g4-codex-call-evidence.md), not 14 verified applied patches. The tool-result responses were `{}`, so the documented classification is **`PATCH_EXECUTION_UNKNOWN`** for all. Partial nonempty-line correspondences (e.g. ID-13 30/33 in `52e599cf`) do not establish an exact full-hunk match, originality or copyright. Do not convert AI-assisted development into `AI_OWNED`, `HUMAN_ONLY` or `NO_EXTERNAL_SOURCE`.

**S6-01 disposition:** `G3_FILE_IDENTITY_MATCH=8/8`; `KNOWN_UPSTREAM_EXAMPLE_AND_NOTICE=UNKNOWN`; `FIRST_PERSON_PER_PORTION_AUTHORITY=NOT_VERIFIED`; `INDEPENDENT_RIGHTS_REVIEW=PENDING`.

## 3. S6-02 — G4 tests (16 of 16)

The following are **observations from committed test source**, not proof about who first devised the test data:

| IDs | Test fixture/material classification | Provenance question |
| --- | --- | --- |
| ID-20–25 | Project contracts/models and literal test values | Were any test structures/fixture values adapted from outside tutorials or other test suites? Which parts did Yến design or edit? |
| ID-26–27, ID-30, ID-34–37 | Spring Security test Jwt builders, sample `test-token`, subjects/claims/issuers or mocked JwtDecoder as applicable | Who devised the fixture patterns and claim values? Any copied/adapted examples or retained notices? |
| ID-31 | `OidcKeycloakIntegrationTest.java` reads `OIDC_TEST_ACCESS_TOKEN` from an environment variable for integration testing | Distinguish **code that references a token** from the actual token bytes. Who supplies the runtime test token, how was it obtained, and are any non-sensitive token/claim templates derived from outside examples? |
| ID-32–33 | JWT test helpers using Java RSA key generation and Nimbus signed-JWT APIs | Was the helper structure written specifically for this project or adapted from tutorials/examples? Identify affected portions and any upstream rights. |

**Safety and reproducibility boundary:** Do **not** paste real access tokens, client secrets, private keys, full private AI conversations or credential-bearing logs in GitHub issues/PRs. Record only sanitized claim *field names*, synthetic fixtures, immutable file/commit links and whether secure external evidence exists. Do not claim that a literal `test-token` is a real Keycloak token. Presence of locally generated RSA-signed JWT test helpers does **not** demonstrate the helper code has an original or independently licensed source.

Five files (ID-13, ID-16, ID-30, ID-31, ID-34) have additional bounded Codex correspondence analysis; its `PATCH_EXECUTION_UNKNOWN` and absence of full-hunk matching still apply. All other G4 files remain without verified per-file AI input/output reconstruction.

**S6-02 disposition:** `G4_FILE_IDENTITY_MATCH=16/16`; `FIXTURE_SOURCE_ORIGIN=UNKNOWN`; `EXTERNAL_TEST_EXAMPLE_ORIGIN=UNKNOWN`; `FIRST_PERSON_PER_PORTION_AUTHORITY=NOT_VERIFIED`; `INDEPENDENT_RIGHTS_REVIEW=PENDING`.

## 4. What Yến has already declared — and what is NOT yet established

[Contributor Declaration R27-DEC-001](https://github.com/tricore-opentrix/opentrix/blob/2844530125ef884a047f976a91b3202103da3c91/docs/verification/r27/identity-contributor-declaration-yen.md) reports that Yến designed Identity requirements/logic, used ChatGPT and Codex for generation, reviewed/modified/tested code, and **to the best of her recollection** did not proactively provide third-party code or fixtures to the tools. She consulted technical documentation; **exact historical reference documents are unknown**. She gave Apache-2.0 authorization **only for specific contributions/portions within rights she actually holds**. These are her pre-existing declaration claims, **not a new first-person confirmation created by this document**.

Accordingly, no file can be marked wholly `CLEARED` from the declaration plus a Git blob match. SPDX metadata and a team label also do not prove the lawful licensing authority for every file/portion.

## 5. Required first-person follow-up — answer from actual recollection

Yến must independently confirm or qualify the following on [Issue #138](https://github.com/tricore-opentrix/opentrix/issues/138) or a linked signed review. The coordinator **must not** prefill these with invented responses.

- **G3 external-origin detail:** For each of ID-12–19 (or an explicitly enumerated identical-source group), which references were actually consulted during creation, if remembered? Was any Spring Security, Nimbus, Keycloak or OIDC example copied/adapted, from what real URL/version, and which lines? If none are remembered, say `UNKNOWN`/`NOT_RECALLED`, not `NO_COPY_VERIFIED`.
- **G3 hunk/authority:** Which specific content portions does Yến personally confirm designing, modifying or integrating, and what is the lawful scope of her Apache-2.0 grant? Separate AI-assisted edits and substantive Git content from SPDX-only commit `fa16f30d`.
- **G4 fixtures:** For ID-20–27 and ID-30–37, who devised sample claims, `test-token` cases, mock decoders, RSA signing helpers and test templates? Was external sample/fixture code used? Answer individually where material; uncertainty remains `UNKNOWN`.
- **G4 integration token:** For ID-31, did Yến ever supply code or a fixture from a third party, or only runtime `OIDC_TEST_ACCESS_TOKEN` from a controlled test environment? **Do not disclose token material**. Record actual origin to the extent known or `NOT_RECALLED`.
- **Codex/ChatGPT ancestry:** Are local source patches, command transcripts and original edited hunks available for the five Codex-matched files? If not, record `NOT_AVAILABLE` and keep `PATCH_EXECUTION_UNKNOWN`. Never describe 14 proposed patches as confirmed application.
- **Scope and consent:** Clarify that any renewed Apache-2.0 permission covers only genuinely controlled content and does not purport to grant Bảo's ID-09/11/28/29, dependencies, third-party material or AI output of uncertain rights.

## 6. Independent review and next actions

| Work item | Owner | Independent reviewer | Completion evidence |
| --- | --- | --- | --- |
| Technical reconciliation of 24 immutable blobs | Yến (coordination) | Ánh | This Draft + CSV, independently inspect paths/blob SHAs |
| Actual first-person origin and limited rights confirmation for Yến G3/G4 portions | **Yến, directly** | **Ánh** | Direct GitHub comment/declaration pinned to IDs and exact commit/hunk; no invented recollection |
| Upstream or adapted example/source and required attribution, if identifiable | Yến reports; reviewer verifies | Ánh | Actual upstream URL/version/affected bytes/license and NOTICE, or unresolved origin reported honestly |
| JWT/claim/mock/RSA helper fixture lineage | Yến reports | Ánh | Sanitized file/portion-linked statement; secure token bytes never published |
| Canonical 357-entry rights decision updates | Separate scoped contributor and reviewer | Independent reviewer not the claimant | Evidence-linked status changes in existing #143 ledger only, after review; no blanket changes |
| R24 handoff | Bảo and release reviewers | Independent reviewer(s) | Distinct release acceptance only after applicable rights resolved |

**Acceptance separation:** approving this technical-reconciliation Draft would mean `S6-01/S6-02 TECHNICAL BLOB RECONCILIATION PASS`, **not** `S6-01/S6-02 SUBSTANTIVE PROVENANCE PASS`. Do not claim Step 6 complete before contributor statements, upstream/fixture review and per-portion disposition.

**Current checkpoint:** `G3=8/8 SOURCE_BLOB_MATCH` / `G4=16/16 SOURCE_BLOB_MATCH` / `SOURCE_ORIGIN=UNKNOWN` / `FIXTURE_LINEAGE=UNKNOWN` / `RIGHTS=PENDING` / `R24=BLOCKED`. Keep #138 OPEN and #139 Draft. No merge, tag, clearance or public release authorized by this document.
