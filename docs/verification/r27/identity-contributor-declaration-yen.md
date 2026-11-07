<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 — Identity Contributor Source-Origin Declaration

**Contributor:** Phan Hai Yen (`phanyen06ym-ien`)  
**Project:** OpenTrix / DX-OS Open-Core  
**Issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Related PR:** [#142](https://github.com/tricore-opentrix/opentrix/pull/142)  
**Audited source baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`

**Source-only release candidate under current rights review:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e` — file/portion rights at this SHA remain `PENDING`.

**Status: DRAFT — DEVELOPMENT WORKFLOW CONFIRMED / LIMITED CONTRIBUTOR AUTHORIZATION CONFIRMED / RIGHTS REVIEW PENDING**

## 1. Development Method

I confirm that my contributions to the Identity component involved:

1. Designing the Identity requirements and logic.
2. Using **ChatGPT and Codex** to assist in generating implementation code.
3. Reviewing the AI-assisted implementation.
4. Modifying the code based on the requirements and my review.
5. Testing the resulting implementation.

**Development classification:** `CONTRIBUTOR_DESIGNED_AI_ASSISTED_IMPLEMENTATION`

The precise proportion of manually written code and AI-generated code has not been reconstructed for each file.

I do not claim that I manually authored every line of implementation code.

## 2. External Source Inputs

To the best of my recollection, I did not proactively provide third-party source code or test fixtures to AI tools during my Identity development work. I consulted technical documentation; the exact documents, any copied or adapted material, and the historical origin of AI output or test fixtures have not been established.

This statement describes my own actions. It does not certify that the resulting AI-generated code contains no third-party material.

**External source status:**

- Proactively supplied external code or test fixtures: `NO_TO_BEST_OF_RECOLLECTION — CONTRIBUTOR DECLARED`
- Technical documentation consultation: `YES — CONTRIBUTOR DECLARED; EXACT SOURCES UNKNOWN`
- External material potentially incorporated through AI output: `UNKNOWN`
- Copied or adapted external examples: `NOT FULLY VERIFIED`
- Public-source similarity review: `NOT PERFORMED`
- Required third-party attribution: `PENDING REVIEW`

## 3. Contribution Scope

The technical Identity register separates:

- **G1:** Core Identity contracts and models — ID-01–08.
- **G2:** Application Identity — ID-10.
- **G3:** Infrastructure Identity and Security — ID-12–19.
- **G4:** Identity tests — ID-20–27 and ID-30–37.
- **G5:** Identity documentation — ID-D01 and historical ID-D02.
- **G6:** SPDX metadata changes, separated from substantive content.

These groups describe Git-associated contribution evidence and do not independently prove authorship or licensing authority.

**Bảo-associated substantive content:** ID-09, ID-11, ID-28 and ID-29 require Bảo's separate declarations. My SPDX metadata changes do not establish ownership of those files.

## 4. Source-Origin Verification

The static inspection of selected Spring Security, OIDC and Identity test files did not identify an explicitly declared external copied-source origin.

However, this limited inspection does not exclude externally derived content or establish source originality.

The remaining source-origin questions must be reviewed at file or file-group level.

**Source-origin verification:** `PARTIAL`

## 5. Copyright and Apache-2.0 Licensing

This declaration does not make a final assertion of exclusive copyright ownership or repository-wide source-distribution clearance.

**My express, scope-limited confirmation:** I agree to OpenTrix distributing under Apache License 2.0 **only those Identity contributions (or parts of contributions) for which I actually possess lawful licensing authority**. This confirmation is not an assertion that I own every line of Identity source code or that every file in the Identity register is wholly covered by my authority. It does not authorize redistribution of content outside my rights, including rights held by other contributors or third parties. The exact file/portion-level application of this authorization, and any legal obligations for AI-assisted, adapted, or third-party material, still require evidence-based review.

- My contributor authorization within the rights I actually control: `CONFIRMED — SCOPE-LIMITED`
- Verified file/portion-level licensing authority and coverage: `PENDING`
- AI-assisted output and third-party source-origin assessment: `UNKNOWN/PENDING`
- Third-party notices and attribution: `UNKNOWN/PENDING`
- Independent rights review: `PENDING`

No permission is asserted on behalf of Bảo, Ánh, third-party rightsholders or any other contributor.

## 6. Release Boundary

`IDENTITY_TECHNICAL_PROVENANCE = VERIFIED_AT_AUDITED_SHA`

`DEVELOPMENT_WORKFLOW = CONTRIBUTOR_CONFIRMED`

`AI_TOOLS = CHATGPT_AND_CODEX`

`PROACTIVE_EXTERNAL_CODE_INPUT = NO_CONTRIBUTOR_DECLARED`

`EXTERNAL_SOURCE_ORIGINALITY = UNKNOWN`

`APACHE_2_0_CONTRIBUTOR_AUTHORIZATION = CONFIRMED_ONLY_WITHIN_RIGHTS_ACTUALLY_HELD`

`FILE_OR_PORTION_LICENSE_AUTHORITY = PENDING_VERIFICATION`

`REPOSITORY_WIDE_APACHE_2_0_CLEARANCE = NOT_ESTABLISHED`

`INDEPENDENT_RIGHTS_REVIEW = PENDING`

`ISSUE_138 = OPEN`

`PR_142 = DRAFT`

`R24_RELEASE_AUTHORIZATION = NOT_GRANTED`

**Final disposition: DEVELOPMENT-METHOD DISCLOSURE CONFIRMED / CONTRIBUTOR'S SCOPE-LIMITED APACHE-2.0 AUTHORIZATION CONFIRMED / FILE-LEVEL RIGHTS AND REPOSITORY DISTRIBUTION NOT CLEARED.**
