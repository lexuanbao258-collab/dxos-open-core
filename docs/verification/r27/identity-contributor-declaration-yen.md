<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 / Issue #138 — Identity Contributor Source-Origin & Licensing Declaration

**Version:** 2 — Updated Contributor Disclosure  
**Status:** DRAFT — Pending final contributor review and independent rights assessment  
**Contributor:** Phan Hai Yen (`phanyen06ym-ien`)  
**Project:** OpenTrix / DX-OS Open-Core  
**Audited source baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`  
**Identity evidence:** [Draft PR #142](https://github.com/tricore-opentrix/dxos-open-core/pull/142)  
**Compliance issue:** [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138)  
**Related release acceptance:** [Draft PR #139](https://github.com/tricore-opentrix/dxos-open-core/pull/139)

## 1. Purpose and limitations

This document records the contributor's reported development workflow and the technical provenance evidence associated with Identity materials at the specified frozen source baseline.

The declaration distinguishes Git-associated source contributions, AI-assisted development, external-source uncertainty, SPDX metadata, and licensing authority.

It is not a representation that all Git-associated content is independently authored, free of third-party material, or cleared for Apache-2.0 distribution.

Statements classified as `CONTRIBUTOR_DECLARED` are based on the contributor's own responses. They have not necessarily been independently verified.

## 2. Contributor development-method disclosure

### 2.1 Confirmed general workflow

The contributor reports the following development process:

1. I designed the Identity logic and requirements.
2. I used ChatGPT and Codex to assist with writing and modifying implementation code.
3. I reviewed the generated or modified code.
4. I made further modifications and performed testing.

**Development-method classification:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**AI tools reported:** ChatGPT and Codex.

**Contributor role reported:** Logic design, requirements formulation, code review, modification, and testing.

This describes the contributor's general working process.

It does not establish the precise human-written or AI-generated proportion of each file, nor does it independently establish copyright ownership of implementation content.

### 2.2 File-level allocation

The exact allocation of manual writing, AI-generated code and AI-edited content has not been reconstructed individually for all registered files.

**File-specific AI/manual allocation:** `NOT_ITEMIZED`

**Prompt and generation history:** `NOT_PROVIDED`

**Independent verification of generation method:** `NOT_PERFORMED`

The use of AI assistance does not by itself establish either copyright infringement or exclusive licensing authority.

## 3. External source-origin disclosure

The contributor reports that her primary workflow was to provide requirements to AI tools.

She does not currently recall specific external code repositories or reference materials used during development.

This is a statement of current recollection, not a confirmation that external materials were never used.

**Declared workflow:** `REQUIREMENTS_TO_AI_PRIMARY`

**Specific external repositories recalled:** `NOT_IDENTIFIED`

**Specific external code examples recalled:** `NOT_IDENTIFIED`

**Whether external content was copied, adapted or introduced through AI output:** `UNKNOWN`

**Third-party source review:** `NOT_FULLY_VERIFIED`

**Public-source similarity review:** `NOT_PERFORMED`

Where original prompts, source materials or development records cannot be reconstructed, the affected source-origin questions remain unresolved.

## 4. Licensing position

The contributor has stated that her Apache-2.0 licensing consent extends only to contributions she actually has the authority to license.

**Declaration classification:** `CONDITIONAL_CONSENT_TO_ACTUAL_RIGHTS`

This is consistent with the scope-limited historical declaration recorded in [Issue #38](https://github.com/tricore-opentrix/dxos-open-core/issues/38#issuecomment-6360325882).

The contributor does not, through this statement, assert licensing rights over:

- Other contributors' independently created content.
- Unidentified third-party source code or documentation.
- Copied or adapted material where licensing conditions are unresolved.
- AI-assisted material for which applicable rights remain uncertain.
- Entire files solely because a Git commit is associated with her account.

**File-specific Apache-2.0 authority:** `PENDING`

**Independent licensing review:** `PENDING`

## 5. Contribution scope by verified Identity register

The source inventory contains 39 records: 38 current paths and one historical removed document.

The following groups are based on technical Git association, not presumed ownership.

### G1 — Core Identity contracts and models

**IDs:** ID-01 through ID-08.

**Scope:** Core Identity models, contracts, authority and exception structures.

**Git evidence:** Yến-associated substantive commits, as recorded in PR #142.

**General development workflow:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**File-specific method:** `NOT_ITEMIZED`

**External-source origin:** `UNKNOWN`

**Apache-2.0 authority:** `PENDING`

**Required additional evidence:** Identify any source-derived contracts, examples or adaptations where remembered, and substantiate licensing authority for particular portions.

### G2 — Application Identity

**ID:** ID-10.

**File:** `backend/src/main/java/com/tricore/dxos/application/identity/CurrentPrincipal.java`

**Git evidence:** Yến-associated substantive commits.

**General development workflow:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**External-source origin:** `UNKNOWN`

**Apache-2.0 authority:** `PENDING`

**Required additional evidence:** Confirm whether any externally sourced implementation or example was adapted, if the development history can be reconstructed.

### G3 — Infrastructure Identity and Security

**IDs:** ID-12 through ID-19.

**Scope:** Spring Security integration, authentication entry points, JWT/OIDC validation, principal adaptation and security package declarations.

**Git evidence:** Yến-associated substantive commits.

**General development workflow:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**External-source origin:** `UNKNOWN`

**Apache-2.0 authority:** `PENDING`

**Required additional evidence:** Review any identifiable Spring Security, OAuth2, Keycloak or JWT examples and distinguish ordinary API usage from copied or adapted source content.

No claim is made that such external copying occurred.

### G4 — Identity tests

**IDs:** ID-20 through ID-27 and ID-30 through ID-37.

**Scope:** Core Identity unit tests and Infrastructure Identity/security tests.

**Git evidence:** Yến-associated substantive commits.

**General development workflow:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**Test-specific generation history:** `NOT_ITEMIZED`

**External fixtures, snippets or templates:** `UNKNOWN`

**Apache-2.0 authority:** `PENDING`

**Required additional evidence:** Examine identifiable JWT test data, mock configuration, security fixtures, integration-test patterns and externally adapted examples, where applicable.

### G5 — Identity documentation

**Current ID:** ID-D01.

**Historical ID:** ID-D02.

**Scope:**
- `docs/02-core/identity/identity-architecture.md`
- `docs/contracts/identity-core.md` (historical; removed before the audited source baseline)

**General development workflow:** `MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

**Document-specific generation history:** `NOT_ITEMIZED`

**External diagrams, text or templates:** `UNKNOWN`

**Apache-2.0 authority:** `PENDING`

The Bảo-associated commit `c3695fe` is not part of the frozen history of ID-D01.

Historical ID-D02 is not included in the current source-only archive. Any reuse of its content requires separate consideration.

### G6 — SPDX metadata changes

**Scope:** 38 current Identity paths.

**Relevant Git commit:** `fa16f30d5c67a49186b1c36f2babe58d54a15e38`

The commit is Git-associated with Yến and records SPDX metadata additions.

**SPDX technical provenance:** `VERIFIED`

**Authority to license underlying content:** `NOT_ESTABLISHED_BY_METADATA`

The metadata scope includes four paths whose substantive content Git history is associated with Bảo.

## 6. Bảo-associated substantive content

The following records are outside the substantive-content scope proposed for Yến:

| ID | Material | Status |
|---|---|---|
| ID-09 | Core Identity `package-info.java` | Bảo's file-specific AI workflow reported; rights pending |
| ID-11 | Infrastructure Identity `package-info.java` | Bảo's file-specific AI workflow reported; rights pending |
| ID-28 | `GatewayIdentityHandoffHttpTest.java` | Git provenance verified; file-specific origin and rights pending |
| ID-29 | `GatewayKeycloakTokenHttpIntegrationTest.java` | Git provenance verified; file-specific origin and rights pending |

For ID-09 and ID-11, Bảo reported primary AI generation or editing with ChatGPT/Claude from his requirements.

For ID-28 and ID-29, Git-associated substantive commits are recorded, but detailed creation method and source-origin evidence remain incomplete.

Yến's SPDX changes to these records are metadata-only for the purposes of this technical provenance analysis.

See [Bảo's scoped follow-up](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6677124150).

## 7. Historical contribution reconciliation

The historical Identity declaration identified:

- Eight production Java records: ID-01–08.
- Six Core Identity test records: ID-20–25.
- One separately identified historical documentation record: ID-D02.

The historical statement disclosed AI assistance, uncertain source origin and conditional licensing consent.

The current disclosure clarifies that ChatGPT and Codex were used as AI tools in the contributor's general development workflow.

It does not retroactively establish that both tools were used for every historical file.

Historical uncertainty remains applicable where file-specific evidence is unavailable.

## 8. Attribution and third-party notices

No specific third-party source or required NOTICE entry has been established from the contributor's current responses.

This does not establish that no third-party notices are required.

**External-origin completeness:** `PARTIAL`

**Potential third-party attribution:** `UNKNOWN`

**NOTICE / THIRD-PARTY-NOTICES requirement:** `PENDING_REVIEW`

Where relevant third-party material is identified, record the exact source, license, affected content and proposed notice or remediation action.

## 9. Remaining contributor confirmation requirements

The following matters remain open:

1. Whether specific source files contain external code, examples or adapted text.
2. Whether surviving prompts, drafts or source references can identify development inputs.
3. Whether any test fixtures or documentation content were copied or adapted.
4. Which exact contribution portions can be supported by sufficient Apache-2.0 licensing authority.
5. Whether any work was jointly created or contains contributions whose rights cannot be determined.
6. Whether the SPDX wording was approved by the relevant content rightsholders.
7. Whether unresolved source material requires additional review, replacement, exclusion or notices.

Uncertainty must be recorded as uncertainty; no affirmative statement of originality or absence of third-party material is supplied without supporting evidence.

## 10. Independent review and evidence disposition

Ánh or another independent reviewer should evaluate:

- The reported human/AI development workflow and its evidentiary limits.
- Source-origin concerns at file or group level.
- Any identified external material and its licensing terms.
- Bảo's separate content-origin declarations.
- The distinction between Git provenance and substantive licensing authority.
- Required attribution or remediation.

Review conclusions should reference exact register IDs and supporting evidence.

Technical source integrity verification cannot substitute for source-rights clearance.

## 11. Release boundary

`IDENTITY_GIT_PROVENANCE = VERIFIED_AT_FROZEN_SHA`

`YEN_DEVELOPMENT_METHOD = MIXED_AI_ASSISTED_CONTRIBUTOR_DECLARED`

`YEN_AI_TOOLS = CHATGPT_AND_CODEX_CONTRIBUTOR_DECLARED`

`YEN_WORKFLOW = SELF_DESIGNED_LOGIC_AI_CODE_REVIEW_MODIFY_TEST`

`YEN_EXTERNAL_SOURCE_ORIGIN = UNKNOWN`

`YEN_APACHE_2_0_CONSENT = CONDITIONAL_TO_ACTUAL_RIGHTS`

`YEN_FILE_SPECIFIC_LICENSE_AUTHORITY = PENDING`

`BAO_FILE_SPECIFIC_ORIGIN = PARTIAL`

`THIRD_PARTY_ATTRIBUTION_REVIEW = PENDING`

`INDEPENDENT_RIGHTS_REVIEW = PENDING`

`ISSUE_138 = OPEN`

`PR_142 = DRAFT`

`PR_139 = DRAFT / BLOCKED`

`R24_RELEASE_AUTHORIZATION = NOT_GRANTED`

## 12. Declaration status

This document incorporates the contributor's direct general disclosures regarding:

- Her role in designing Identity logic.
- AI-assisted implementation using ChatGPT and Codex.
- Subsequent code review, modification and testing.
- Uncertainty regarding external reference materials.
- Apache-2.0 consent limited to contributions she can actually license.

These statements do not resolve file-level source originality or licensing authority.

**Final document disposition: DRAFT — CONTRIBUTOR DISCLOSURE RECORDED / FILE-SPECIFIC ORIGIN AND RIGHTS PENDING.**

This draft has not been signed, independently rights-cleared or approved for release.
