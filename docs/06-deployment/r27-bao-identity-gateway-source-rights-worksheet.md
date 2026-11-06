<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 / #138 — Bảo Identity/Gateway four-file source-origin and rights worksheet (NOT SIGNED / NOT CLEARED)

**Status:** Technical path, blob, Git-history and release-boundary crosswalk verified as noted; **new per-file origin/rights attestations NOT obtained**. This is a maintainer-prepared worksheet, not Bảo's personal signature, not an authorization under Apache-2.0 and not independent legal or release clearance. Keep all unknown fields as `UNKNOWN/PENDING`.

**Current technical archive baseline:** Two independent SOURCE-ONLY candidates for source `main@187dbd2ea6935966f5c4cceb95ad9c3c53708a2e` were inspected: archive SHA-256 `93e6e0ba5d708120c6ec494af3e50c8959cccb4c442a8bba9a2e9c617c2d448f`, reproducibility PASS. All four files remain present at identical Git blob SHAs. Earlier `main@5daa145d` and `main@92bd46b` are *historical comparison baselines*. #141 merged via squash. See [exact final source scope and rights review register](../verification/r27/v0.2.0-source-only-distribution-rights-scope-187dbd2.md). This technical update **does not** sign or clear contributor rights.

## A. Crosswalk to Identity register #142 and Backend register #139

| R27 ID | Exact current source file | Git blob SHA (identical at both tested commits) | Git-associated content introduction | Contributor-source evidence | Rights and attribution disposition |
| --- | --- | --- | --- | --- | --- |
| ID-09 | `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java` | `c57d2207a4eade58e2bf0b03bf6d859be74c7724` | Bảo-associated [`d53a273f`](https://github.com/tricore-opentrix/opentrix/commit/d53a273f63fa07af424825f8775b030d94344fd5) | Bảo previously **reported** primarily ChatGPT/Claude-assisted creation/editing from his requirements, [recorded in #138](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6677124150) | `AI_ASSISTANCE_REPORTED_PER_FILE / EXTERNAL_ORIGIN_AND_LICENSE_AUTHORITY_PENDING` |
| ID-11 | `backend/src/main/java/com/tricore/dxos/infrastructure/identity/package-info.java` | `a8901597f86a88822666b11b538285b3327e1397` | Same Bảo-associated [`d53a273f`](https://github.com/tricore-opentrix/opentrix/commit/d53a273f63fa07af424825f8775b030d94344fd5) | Same earlier file-specific contributor report, separately tracked from ID-09 | `AI_ASSISTANCE_REPORTED_PER_FILE / EXTERNAL_ORIGIN_AND_LICENSE_AUTHORITY_PENDING` |
| ID-28 | `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java` | `70dad237cdb0569ac5c57d239829275c76b24b66` | Bảo-associated [`f11461d`](https://github.com/tricore-opentrix/opentrix/commit/f11461d61fa3ed98913617738f8857c1b7831c91) original at earlier path, `f9b9568` relocation, `b797b99`/`b370574` revisions | **Only general** Bảo AI-assisted Backend workflow report, no file-specific declaration; source inspection in [Gateway technical cross-check](r27-gateway-identity-test-technical-crosscheck-5daa145d.md) | `GIT_HISTORY_VERIFIED / FILE_SPECIFIC_ORIGIN_RIGHTS_PENDING` |
| ID-29 | `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java` | `15f11e73b104e34165c9fcfb5738436131a5e07e` | Bảo-associated [`e5793ef`](https://github.com/tricore-opentrix/opentrix/commit/e5793efbef1fb7f5017d841d455fc29dda49ab70), `8905e00`/`f0b71c8` revisions | **Only general** AI-workflow disclosure; no file-specific declaration | `GIT_HISTORY_VERIFIED / FILE_SPECIFIC_ORIGIN_RIGHTS_PENDING` |

All four current file headers declare TRICORE contributors as the 2026 copyright label and Apache-2.0 as the license identifier under SPDX metadata, added in Yến-associated [`fa16f30d`](https://github.com/tricore-opentrix/opentrix/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38). This **metadata describes repository labelling**; it cannot establish who originated a snippet, who has copyright rights, or that any external template is permissively redistributable.

## B. Token/fixture and test boundary — technical observations only

- ID-28 defines a test-only `@WebMvcTest` HTTP probe and **mocks `JwtDecoder`**; `signed-read-token`, test-only `issuer.example.test`, `user-123`, and local role/authority values are fixture literals in this file. They **do not constitute an actual signed JWT or external token fixture**. **External source of the fixture/test design: UNKNOWN** until contributor attestation.
- ID-29 defines a separate opt-in integration test gated by `OIDC_TEST_ACCESS_TOKEN`; it reads a **runtime environment variable**, does not embed an actual access token in the checked-in source, and assumes locally configured Keycloak and JWKS. The test sources include code for modified-signature and forged-header rejection. This inspection is **not evidence it executed** with a real Keycloak. Whether any sample test code or fixture was adapted from external sources is **UNKNOWN**.
- Spring Security, JUnit, MockMvc, AssertJ, Mockito APIs are referenced by imports. Imports alone do not prove that snippets were copied from or independently created without third-party sample code.

## C. Source-only boundary (two independent final-SHA candidate archives inspected)

The proposed `v0.2.0` source-only packager in [`scripts/r24-release.py` at #141 HEAD](https://github.com/tricore-opentrix/opentrix/blob/7c2bdbedc89fec96c7136f44254d9a08623cee3b/scripts/r24-release.py) reads `git ls-files --cached -z`, then adds tracked UTF-8 text under `source/`; it excludes binary extensions such as `.jar` and rejects NUL/non-UTF-8 content. All four paths are tracked `.java` files and appear in the `main@92bd46b` tree, so all **are expected** in source-only packages under this logic, including the two test files, regardless of whether their JUnit tests run. **Updated exact-SHA verification:** Two actual `v0.2.0` SOURCE-ONLY archives at `187dbd2...` were independently downloaded and inspected. Both tar.gz archives have exactly 356 regular members including all 4 files under `source/`, matching SHA256SUMS and MANIFEST and a common SHA-256 digest `93e6e0ba5d708120c6ec494af3e50c8959cccb4c442a8bba9a2e9c617c2d448f`. This is technical distribution-scope evidence only; rights are not granted or cleared.

No JAR, built frontend dist or GHCR images are authorized. Source-only distribution still requires source-origin/rights and attribution verification for the text and relevant assets actually shipped.

## D. Contributor follow-up — require direct, per-file answers

For **each** of ID-09, ID-11, ID-28 and ID-29, Bảo must confirm or explicitly say `UNKNOWN` for:
1. **Specific creation method:** AI-generated, AI-edited, manually authored, or mixed; which parts if recalled. Prior per-file ID-09/ID-11 reports are recorded above, but additional provenance/permission remains unresolved.
2. **External source:** known copied/adapted GitHub repository, tutorial, sample, Stack Overflow snippet, code/template, fixture, or AI prompt input; any links and original license if known. If no recollection, state **UNKNOWN**, not `NONE` or guaranteed originality.
3. **Token/fixture inputs:** for ID-28/ID-29, whether any sample Keycloak token, JWT claims, JUnit test setup, Spring Security snippet, fixture structure, or code was imported/adapted from somewhere; whether the provided code contains only self-authored and AI-assisted local sample literals to the contributor's knowledge.
4. **Apache-2.0 rights:** identify **only the content Bảo personally has authority to license**, and whether he grants the team such permission; exclude external/upstream works and other contributors' contributions. Do not infer a grant based on Git author identity or SPDX.
5. **Attribution/NOTICE:** known or suspected third-party license notices/attributions to preserve, plus unresolved cases; independently review rather than marking `NONE` without evidence.

**Evidence requested:** Bảo's direct contributor statement (first person) with exact four file paths, any supporting references, signed-off date and explicit uncertainties. **No per-file author declaration is made by this worksheet.** Yến (Identity / #142) and Ánh (Gateway/Data cross-review) should independently verify the source and scope, record disposition per ID, and keep uncertain items open. No approval should be inferred from filing the worksheet.

## E. Outstanding result and release gate

| Check | State |
| --- | --- |
| Four-file paths, current blobs, Git-associated history, CI packaging inclusion logic | **TECHNICAL VERIFIED / EXPECTED SOURCE-ONLY BOUNDARY**, not origin/legal clearance |
| Prior first-person AI report for ID-09 and ID-11 | **REPORTED**; independent rights and external-source review pending |
| New file-specific method/external-input statement for ID-28 and ID-29 | **NOT SUPPLIED** |
| Actual Apache-2.0 contribution authorization and any required third-party notices for each of four files | **UNKNOWN/PENDING** |
| Final candidate source SHA, 2x SOURCE-ONLY reproducibility and archive boundary | **TECHNICAL PASS** at `187dbd2...`, exact archive SHA-256 `93e6e0ba...`; contributor rights still PENDING |
| [#138](https://github.com/tricore-opentrix/opentrix/issues/138) independent rights sign-off and [#139](https://github.com/tricore-opentrix/opentrix/pull/139) independent stable release acceptance | **BLOCKED** |

Historical `main@5daa145d` and `main@92bd46b` remain prior comparisons; the two verified candidate archives are at `main@187dbd2...`. Do **not** promote this technical worksheet into a complete `source_license` or `source_provenance` record. Stable `v0.2.0.json` fields and `reviewed_by` remain empty until all substantive evidence and real reviews exist.
