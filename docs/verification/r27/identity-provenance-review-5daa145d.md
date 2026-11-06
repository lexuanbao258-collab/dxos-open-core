# R27 / Issue #138 — Yến Identity provenance register v5 — REVIEW DRAFT

**Frozen technical source baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`.

## Corrections after Codex independent audit

1. **F-01 / ID-29 CORRECTED:** The valid file is `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java`, blob `15f11e73b104e34165c9fcfb5738436131a5e07e`, with Bảo-associated Git content commits `e5793ef`, `8905e00`, `f0b71c8`. Earlier register incorrectly used `GatewayKeycloakTokenHttpTest.java` and Yến association. The correction is to Git attribution only; not copyright proof.
2. **F-03 / B02 outstanding:** Bảo's [Issue #138 follow-up](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6676038509) independently verifies both `core/identity/package-info.java` (blob `c57d2207a4eade58e2bf0b03bf6d859be74c7724`) and `infrastructure/identity/package-info.java` (blob `a8901597f86a88822666b11b538285b3327e1397`) introduced in `d53a273`. Bảo already reported the file-specific ChatGPT/Claude-assisted method for ID-09/11. External adaptation, attribution/NOTICE and rights remain PENDING.
3. **F-04 / Evidence columns:** The register now records the frozen Git blob SHA, introduction commit, substantive changes, SPDX-only changes, Git-associated contributors and commit links for all 39 records. Git association is technical provenance, not proof of authorship or licensing rights.
4. **F-05 / R24 source evidence:** Codex reports archive digest `5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c`, `354/354` MANIFEST member hashes, and 38/38 Identity paths with byte-level comparison to Git blobs. Source-only candidate integrity and current Identity boundary PASS at tested source SHA; this does not grant distribution rights.
5. **Historical 14-file issue:** Resolved exact historical scope as 8 Core Identity production Java + 6 Core Identity Java unit tests. The 15th historical declaration file `docs/contracts/identity-core.md` was removed from current source. [Issue #38 evidence](https://github.com/tricore-opentrix/dxos-open-core/issues/38#issuecomment-6360325882).


## Frozen-baseline attribution correction — ID-D01 (2026-11-06)

- **Affected file:** `docs/02-core/identity/identity-architecture.md` (blob `2d878315541f679e3c78150a0d5ac4e8dcfdde2d` at `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`).
- **Corrected register value:** `git_associated=YEN` (previously `YEN_AND_BAO`). `git_associated_contributors=Phan Hai Yen` was already correct.
- **Frozen-SHA file history:** [`3f0f0b6b633d2dbf93e3bc73e12abdcc50d38b9e`](https://github.com/tricore-opentrix/dxos-open-core/commit/3f0f0b6b633d2dbf93e3bc73e12abdcc50d38b9e) (introduction), [`fa16f30d5c67a49186b1c36f2babe58d54a15e38`](https://github.com/tricore-opentrix/dxos-open-core/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38) (SPDX metadata). GitHub path-filtered commit history at the frozen SHA lists these two only.
- **Excluded from baseline attribution:** Bảo-associated [`c3695fe6fddd0fbf2d8a5bcf05904c7d5a4b7c16`](https://github.com/tricore-opentrix/dxos-open-core/commit/c3695fe6fddd0fbf2d8a5bcf05904c7d5a4b7c16) changes that path on another history; GitHub compare `5daa145d...c3695fe` returns `diverged`. It is **not an ancestor** of frozen source SHA and must not be counted as that baseline's source contribution. A future baseline requires a fresh tree/history check.
- **Rights:** `PENDING`; Git commit provenance is not an authorship or licensing-rights attestation.

## Audit status at frozen baseline

- **Git provenance:** PASS for the 39-record technical register at `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`; commit association does not prove authorship.
- **Historical 14 Java paths:** PASS for eight Core production files and six Core tests; the historical contract document is recorded separately.
- **R24 source-only boundary:** PASS at the tested SHA. The archive SHA-256 is `5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c`; `SHA256SUMS`, 354 manifest member hashes and all 38 current Identity source bytes were checked against the frozen Git tree.
- **AI-assisted development:** Contributor declarations are recorded, with exact per-file method still unverified.
- **External source/origin:** PARTIAL; public-source similarity was not performed and absence of attribution markers is not proof of originality.
- **Contributor licensing authority:** PENDING; conditional statements and SPDX metadata do not establish rights over every file.
- **Independent rights review:** PENDING.
- **R24 release acceptance:** BLOCKED. This report is not approval to distribute.

## Content commits and SPDX metadata commits

The register now separates Git accounts associated with source content from those associated only with SPDX metadata. These are commit associations, not proof of who created copyrightable expression or who can license it. General AI-assistance disclosures do not establish the creation method or external origin of any particular file. Scope-specific origin and licensing authority remain pending.

For `ID-29` (`GatewayKeycloakTokenHttpIntegrationTest.java`), Bảo-associated [`e5793ef`](https://github.com/tricore-opentrix/dxos-open-core/commit/e5793efbef1fb7f5017d841d455fc29dda49ab70) introduced the test (112 lines). Bảo-associated [`8905e00`](https://github.com/tricore-opentrix/dxos-open-core/commit/8905e00d013c46061ca77223ac3baa5f948c9dcd) registered its test-only probe controller, and [`f0b71c8`](https://github.com/tricore-opentrix/dxos-open-core/commit/f0b71c88bb3b4c62de7997745c27fe487b502d4b) qualified the nested probe type. Yến-associated [`fa16f30`](https://github.com/tricore-opentrix/dxos-open-core/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38) added only the SPDX header. All four commits precede the frozen SHA. `ID29_TECHNICAL_PROVENANCE=VERIFIED`; content origin and rights remain `PENDING`.

The same separation applies to `ID-09` and `ID-11` (the two Identity `package-info.java` files introduced by Bảo-associated `d53a273`) and `ID-28` (the Gateway handoff test with Bảo-associated content commits). Yến-associated `fa16f30` added SPDX headers to all four. ID-09 and ID-11 already have Bảo's file-specific ChatGPT/Claude-assisted development disclosure; external adaptation, notices, and rights remain pending. Bảo subsequently confirmed that ID-28 and ID-29 were both developed primarily with AI ([contributor follow-up](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6689684471)). This closes the predominant-method field only; precise AI/manual split, external examples, JWT/Keycloak fixtures, third-party attribution/NOTICE and licensing authority remain UNKNOWN/PENDING. Independent review remains pending for all four. The historical document `ID-D02` has no SPDX-only commit in this register; its deletion commit is recorded separately and it is outside the current archive.

`SPDX_METADATA_PROVENANCE=VERIFIED`; `CONTENT_ORIGIN_AND_RIGHTS=PENDING`; `APACHE_2_0_AUTHORIZATION=PENDING`.

## Open compliance questions

- Yến: latest `MIXED / NO_KNOWN_NOT_VERIFIED / RIGHTS_UNSURE` alongside historical conditional consent for parts she can lawfully license; exact file-group origin, external sources, AI role and actual rights are not established.
- Bảo ID-09/11: AI-assisted method already declared; external adaptation, notices and rights remain PENDING. Bảo ID-28/29: primarily AI-assisted method now contributor-reported for both files; precise AI/manual split, fixtures, external inputs, notices and rights remain UNKNOWN/PENDING.
- Ánh: independent origin and rights review on Yến-associated Identity, as reviewer, not presumed Identity author.
- Public-source similarity: NOT_PERFORMED. Absence of identifiable upstream headers does not exclude third-party-derived material.
- PR #139 release acceptance: remain DRAFT, JSON clearance fields null until real final source SHA, archive digest, rights review and independent approval.

## Evidence/approval disposition

`IDENTITY_REGISTER_ROWS=39 / UNIQUE=39 / ID29_CORRECTED`

`IDENTITY_37_JAVA_GIT_BLOB_EVIDENCE=RECORDED`

`IDENTITY_38_PATH_SOURCE_ONLY_INTEGRITY=PASS_AT_5daa145d`

`HISTORICAL_14_SCOPE=RECONCILED`

`IDENTITY_SOURCE_RIGHTS=PENDING`

`APACHE_2_0_DISTRIBUTION_AUTHORITY=PENDING`

`ISSUE_138=OPEN; PR_139=DRAFT; RELEASE=BLOCKED`

This document is an evidence/report correction, **not** a first-person rights declaration and **not posted to GitHub**.


## Independent Git commit matrix at frozen source SHA

Commit IDs below are shortened for display; the CSV contains full SHAs and links. Git author/committer associations are technical metadata, not rights evidence. `fa16f30` was verified as SPDX-only for current files. Historical deletion is separate.

| ID | Introduction | Substantive later changes | SPDX only | Deletion | Git-associated contributors |
| --- | --- | --- | --- | --- | --- |
| ID-01 | a60c96ac9 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-02 | a60c96ac9 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-03 | a60c96ac9 | d87a26b74 | fa16f30d5 | - | Phan Hai Yen |
| ID-04 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-05 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-06 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-07 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-08 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-09 | d53a273f6 | - | fa16f30d5 | - | Phan Hai Yen;lexuanbao258-collab |
| ID-10 | 34021e2f2 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-11 | d53a273f6 | - | fa16f30d5 | - | Phan Hai Yen;lexuanbao258-collab |
| ID-12 | 52e599cf0 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-13 | 52e599cf0 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-14 | 34021e2f2 | 3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-15 | 52e599cf0 | 3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-16 | 34021e2f2 | 52e599cf0,3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-17 | 34021e2f2 | 3d6b9e28f,a3dee696a,3fac3bde8,815ce029f | fa16f30d5 | - | Phan Hai Yen |
| ID-18 | 52e599cf0 | 3fac3bde8,815ce029f | fa16f30d5 | - | Phan Hai Yen |
| ID-19 | 34021e2f2 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-20 | a60c96ac9 | 8cc03d607 | fa16f30d5 | - | Phan Hai Yen |
| ID-21 | a60c96ac9 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-22 | a60c96ac9 | d87a26b74 | fa16f30d5 | - | Phan Hai Yen |
| ID-23 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-24 | 3e06d69a6 | d87a26b74 | fa16f30d5 | - | Phan Hai Yen |
| ID-25 | 3e06d69a6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-26 | 34021e2f2 | 3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-27 | 3fac3bde8 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-28 | f11461d61 | f9b9568b8,b797b99fb,b3705742d | fa16f30d5 | - | Phan Hai Yen;lexuanbao258-collab |
| ID-29 | e5793efbe | 8905e00d0,f0b71c88b | fa16f30d5 | - | Phan Hai Yen;lexuanbao258-collab |
| ID-30 | 52e599cf0 | 3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-31 | 52e599cf0 | 3fac3bde8 | fa16f30d5 | - | Phan Hai Yen |
| ID-32 | 3fac3bde8 | 815ce029f | fa16f30d5 | - | Phan Hai Yen |
| ID-33 | 3fac3bde8 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-34 | 34021e2f2 | 52e599cf0,3fac3bde8,88162e9c3 | fa16f30d5 | - | Phan Hai Yen |
| ID-35 | 34021e2f2 | 3d6b9e28f,a3dee696a,3fac3bde8,815ce029f | fa16f30d5 | - | Phan Hai Yen |
| ID-36 | 3fac3bde8 | 815ce029f | fa16f30d5 | - | Phan Hai Yen |
| ID-37 | 52e599cf0 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-D01 | 3f0f0b6b6 | - | fa16f30d5 | - | Phan Hai Yen |
| ID-D02 | 8cc03d607 | d87a26b74,a3dee696a,52e599cf0,3fac3bde8 | - | 2b4989054 | Phan Hai Yen |

**Completed commit-evidence rows:** ID-01, ID-02, ID-03, ID-04, ID-05, ID-06, ID-07, ID-08, ID-10, ID-12, ID-13, ID-14, ID-15, ID-16, ID-17, ID-18, ID-19, ID-20, ID-21, ID-22, ID-23, ID-24, ID-25, ID-26, ID-27, ID-30, ID-31, ID-32, ID-33, ID-34, ID-35, ID-36, ID-37, ID-D01, ID-D02. Source rights and Apache-2.0 authorization remain PENDING. The historical document is not in the current source archive. No release approval is asserted.

## G3/G4 targeted static source-origin review — 2026-11-06

**Review method and scope:** Read-only inspection of the exact Java blobs at `main@5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`; this section does **not** claim that tests were executed, code was compared against public repositories, or that AI-generated output can be certified free from third-party material. The register's existing Git blob SHAs and contributor boundaries are unchanged.

### G3 — Identity infrastructure/security (ID-12–19)

Inspected all **eight** registered paths at `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/`:

- `AccessTokenTypeValidator.java` (ID-12), `ApiAuthenticationEntryPoint.java` (ID-13), `AudienceValidator.java` (ID-14), `ExpirationValidator.java` (ID-15).
- `SecurityConfig.java` (ID-16), `SpringSecurityCurrentPrincipal.java` (ID-17), `SubjectValidator.java` (ID-18), `package-info.java` (ID-19).

**Observed:** These files import and use Spring Security OAuth2/JWT and Nimbus JOSE/JWT interfaces; `SecurityConfig.java` wires a Nimbus-based decoder, issuer validation and custom subject/claim validation. The source snippets inspected contain SPDX metadata and no separately identified copied-example URL, upstream copyright notice, tutorial attribution or explicit adaptation comment. Library usage/imports alone do not establish that a code sample was copied, nor do they disprove copying. No public-source similarity search, prompt history reconstruction, dependency-license audit or independent external origin verification was performed.

**Disposition:** `G3_STATIC_PATH_INSPECTION=8_OF_8`; `THIRD_PARTY_EXAMPLE_ORIGIN=UNKNOWN`; `PUBLIC_SOURCE_SIMILARITY=NOT_PERFORMED`; `RIGHTS_AND_NOTICE=UNKNOWN/PENDING`. Ask contributor to identify any known adapted Spring Security, Keycloak, OIDC or Nimbus examples at file/group level; preserve links, licenses and attribution if identified. **Do not infer NONE** from missing notices.

### G4 — Identity tests (ID-20–37)

Inspected all **16 test files in Yến's registered content scope**: `ID-20`–`ID-27` plus `ID-30`–`ID-37`. Inspections are static at the same audited source SHA.

- Core tests `ID-20`–`ID-25`: six JUnit test sources, mainly project-specific contract/model assertions, with no explicit external sample-origin marker identified.
- Security tests `ID-26`, `ID-27`, `ID-30`, `ID-34`, `ID-35`, `ID-36`, `ID-37`: test-built `Jwt` objects use `test-token`, local issuer strings and sample claims. Their appearance is consistent with synthetic fixtures, but the historical origin of the fixture design is **not independently verified**.
- `ID-31` / `OidcKeycloakIntegrationTest.java`: tests are conditional on `OIDC_TEST_ACCESS_TOKEN` and local Keycloak issuer configuration. The code reads a runtime environment variable; code inspection alone **does not** prove that integration executed or establish the real token's original issuer or handling.
- `ID-32` / `RawSubjectJwtDecoderTest.java` and `ID-33` / `RawTimestampJwtDecoderTest.java`: construct serialized signed JWTs inside test helpers to exercise claim typing and invalid-value cases. Example issuer domains and sample claims are visible; no external sample-source URL was identified in the inspected source. This does **not** independently establish the test-helper design's source originality.
- **Excluded from Yến's personal attribution:** `ID-28` / `GatewayIdentityHandoffHttpTest.java` and `ID-29` / `GatewayKeycloakTokenHttpIntegrationTest.java`. These are Bảo-associated content contributions; Yến's `fa16f30d` was SPDX-only. Their technical test scope is cross-referenced in the Bảo worksheet in Draft PR #139: ID-28 mocks `JwtDecoder`, while ID-29 is env-gated on `OIDC_TEST_ACCESS_TOKEN`. Bảo's **primarily AI-assisted development method for both files is contributor-reported**; precise AI/manual split, adapted fixture/third-party source and actual rights declarations remain `UNKNOWN/PENDING`.

**Disposition:** `G4_YEN_STATIC_PATH_INSPECTION=16_OF_16`; `G4_BAO_SEPARATE_REVIEW=ID28_AND_ID29`; `SYNTHETIC_FIXTURE_LITERALS=OBSERVED`; `FIXTURE_ORIGIN=UNKNOWN`; `REAL_KEYCLOAK_EXECUTION=NOT_VERIFIED_BY_THIS_REVIEW`; `ADAPTED_EXAMPLE_LICENSES=UNKNOWN/PENDING`.

### Source-rights and release boundary

Yến's first-person declaration confirms that she designed Identity requirements/logic and used **ChatGPT and Codex** to help generate code, then reviewed and modified it; she did **not proactively supply external source code to the AI**. This statement cannot prove the resulting output contains no third-party material and does not constitute Apache-2.0 authorization.

`G3_G4_STATIC_REVIEW=RECORDED`; `SOURCE_ORIGINALITY=UNVERIFIED`; `RIGHTS_CLEARANCE=PENDING`; `ISSUE_138=OPEN`; `PR_142=DRAFT`; `R24_RELEASE_AUTHORIZATION=NOT_GRANTED`.

**REUSE/CI note:** The two provenance artifacts (this report and the register CSV) are annotated in `REUSE.toml` using the repository's existing SPDX metadata convention because the initial PR #142 CI check identified missing metadata. Such labels are metadata only, **not an independent source-rights finding or a grant by the reviewer**.

## R27 CI closure, final-candidate source delta and historical exception mapping — 2026-11-06

**Exact PR #142 CI:** [run 42249253049](https://github.com/tricore-opentrix/opentrix/actions/runs/42249253049) completed **SUCCESS** at `cac764bcfb2a89b049c42ddc3737e1c065234f89`, with all five jobs (`backend`, `frontend`, `portal-bff`, `r23-compliance`, `repo-hygiene`) successful. The two path-specific `REUSE.toml` metadata annotations cleared the earlier REUSE missing-metadata failure. This establishes CI compliance at that exact PR HEAD, **not** copyright ownership, third-party source originality or release rights. A new commit to this report requires its own exact-HEAD CI result.

**Identity source delta, historical → current candidate:** independently compared **untruncated** GitHub trees at `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33` and source-only candidate `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`. Among the **38** current Identity register paths, **37 Git blob SHAs are identical**. The only changed path is current document `ID-D01` (`docs/02-core/identity/identity-architecture.md`), from blob `2d878315541f679e3c78150a0d5ac4e8dcfdde2d` to `194af8d28e38b0faa9b0d4c598f167c270d06bd1`. Its six changed lines (89–94) update old repository URLs to canonical `tricore-opentrix/opentrix` links, including a historical baseline-pinned contract-document link. **No Identity production/test blob change was observed across these two exact trees.** This comparison proves byte/history scope only, not rights, and does not reclassify the historical removed `ID-D02` as a shipped file.

**R25 37 exception mapping:** read `docs/verification/r25/exception-review.csv` from the historical R25 audit at `35e90c47ae95da23dac75c157a4a816d3b73ce08`. It contains **37 unique exception paths**; all **37 paths are present** in the untruncated current candidate Git tree `187dbd2...`. This is **path-only reconciliation**, not 37 verified upstream origins, author permissions, attribution dispositions or source-only archive inclusions. In particular the upstream Gradle JAR remains excluded from the stable source-only candidate, while other included text files and `frontend/public/favicon.svg` still require actual rights and notice review. Do not transfer an R25 historical `PENDING` classification to `CLEARED` based on existence or REUSE metadata.

**Source-rights crosswalk:** the new repository-wide Draft #139 technical ledger `docs/verification/r27/v0.2.0-source-only-git-rights-ledger-187dbd2.csv` accounts for **347 tracked paths**, **346 tracked text/source paths expected in the verified archive**, plus the excluded upstream Gradle JAR. Every record requires file/group-appropriate contributor/third-party permission and independent review; the ledger is not a rights grant.

**Outstanding and assigned disposition:**
- **Yến G1–G5 own scope:** `AI_WORKFLOW_CONTRIBUTOR_CONFIRMED`; `G3_8_OF_8_AND_G4_16_OF_16_STATIC_PATHS_INSPECTED`; `EXTERNAL_EXAMPLE_OR_FIXTURE_ORIGIN=UNKNOWN/PENDING`; `APACHE_2_0_FILE_GROUP_AUTHORITY=PENDING`. No public-source similarity audit or comprehensive external example/fixture lineage was performed.
- **Bảo ID-09/11/28/29:** separate substantive content boundaries. ID-09/11 file-specific ChatGPT/Claude-assisted method and ID-28/29 primarily AI-assisted method have now been directly contributor-reported. Specific external origin, fixtures, notices and licensing authority remain `UNKNOWN/PENDING`; independent rights review is still required.
- **Ánh independent review:** reviewer requested on #142; a GitHub formal `APPROVED` is required before any conclusion requiring that approval. An unsubmitted or merely `COMMENTED` review is not approval.
- **Release:** `ISSUE_138=OPEN`; `PR_142=DRAFT`; `PR_139=DRAFT/BLOCKED`; `RELEASE_AUTHORIZATION=NOT_GRANTED`.

**Verdict:** `HISTORICAL_TO_CANDIDATE_IDENTITY_BLOBS_37_UNCHANGED_1_DOC_LINK_DELTA / R25_EXCEPTION_PATHS_37_OF_37_RECONCILED / SOURCE_RIGHTS_AND_INDEPENDENT_ACCEPTANCE_PENDING`.

## G3/G4 file-by-file source-origin evidence matrix — 2026-11-06

[Open 24-row CSV matrix](identity-g3-g4-per-file-source-origin-matrix-5daa145d.csv). It tracks exactly **G3 ID-12–19 (8 files)** and **Yến-scope G4 ID-20–27 and ID-30–37 (16 files)**; Bảo-associated **ID-28 and ID-29 are deliberately excluded** and remain in his separate declaration/review scope. All 24 records preserve their audited `5daa145d...` Git blob identities and record the observed use of frameworks/tests separately from unknown external origins.

The matrix explicitly separates: (1) repository path and SHA; (2) observed library/API or test fixture; (3) actual external source URL; (4) copying/adaptation classification; (5) third-party attribution/NOTICE; (6) contributor development-method disclosure; (7) independently verified file/portion-level Apache-2.0 rights; (8) evidence and required next actions.

**Rules and cautions:**
- A Spring Security, Keycloak/OIDC or Nimbus import is an **observed dependency**, not an identified copied upstream snippet and not proof that copying did not occur.
- `Jwt.withTokenValue`, local example issuers, dynamically signed test JWTs and `OIDC_TEST_ACCESS_TOKEN` are **observations of test sources**, not verified historical origin/ownership of fixtures. The runtime access token is not recorded, and must never be published in the register.
- All 24 external-source-reference fields and adaptation fields are `UNKNOWN`; all third-party notice obligations, specific file/portion rights and independent clearance remain `UNKNOWN/PENDING`. `NOT_APPLICABLE_TO_TEST_FIXTURES` for a production file only indicates that its row is not a test fixture, not that no external source could exist.
- The method label `YEN_CONTRIBUTOR_DESIGNED_CHATGPT_CODEX_ASSISTED_SCOPE_LEVEL` describes Yến's **general Identity contribution disclosure**, not a reconstructed per-file AI/manual split or a warranty of originality.
- Yến's express limited Apache-2.0 consent applies **only to material she can lawfully license**. The CSV's per-file authority remains PENDING and no licensing right is asserted for Bảo, Ánh, AI-generated third-party-derived material or any external source.
- A public-source similarity check has `NOT_PERFORMED` status; missing attribution markers never imply `NONE`.
- This CSV is a **technical source-origin questionnaire and evidence queue**, not independent legal clearance or R24 release acceptance. Metadata in `REUSE.toml` makes the CSV lintable without changing its data rows.

**Next evidence gate:** collect source-specific contributor answers (if a known external code example, tutorial or reused fixture exists, record its exact URL, affected file/portion, license/NOTICE and documented permission). If no conclusive supporting evidence exists, leave the relevant field `UNKNOWN/PENDING`. Ask Ánh to review each row's supported observation and classification; never self-mark `APPROVED`.

`G3_G4_MATRIX_ROWS=24`; `EXTERNAL_ORIGIN_CLEARANCE=0_CONFIRMED`; `INDEPENDENT_RIGHTS_APPROVAL=PENDING`; `#138=OPEN`; `#142=DRAFT`; `PUBLIC_RELEASE=BLOCKED`.

## G3/G4 historical commit crosswalk and official API reference boundary — 2026-11-06

The [24-file matrix](identity-g3-g4-per-file-source-origin-matrix-5daa145d.csv) now carries each file's historical **introduction commit URL**, known **substantive modification commit URLs**, separate **SPDX-only commit URL**, and a bounded textual description of the observed API or fixture. Commit data were mapped from the frozen R27 39-row source-origin register. Selected introduction/substantive commits (notably `34021e2`, `52e599c`, `3fac3bd`) were also checked against their GitHub commit file lists and descriptions; these corroborate Git history only, not upstream copying or copyright.

The matrix also provides **official framework API documentation for comparison only** for relevant Security/JWT/OIDC files: [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring Security Jwt API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/Jwt.html), [NimbusJwtDecoder API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html), and [Keycloak OIDC layers](https://www.keycloak.org/securing-apps/oidc-layers). These URLs are **reference documentation for reviewing semantics**, not identified copied-source URLs, not a declaration that Yến used these pages historically, and not a substitute for examining the relevant dependency or example licenses.

**Disposition:** All 24 external-source URLs remain `UNKNOWN`; copied/adapted use remains `UNKNOWN`; all 24 historic external-example verdicts remain `UNKNOWN_NO_DIRECT_HISTORICAL_SOURCE_LINK_VERIFIED`; file/portion licensing authority and attribution/NOTICE remain `PENDING`. Project Git attribution, library imports, test token placeholders, and current official API docs do not establish original authorship or rights. More historical prompt/source data or contributor-specific external-reference identification is required before any row is promoted from `UNKNOWN`.
