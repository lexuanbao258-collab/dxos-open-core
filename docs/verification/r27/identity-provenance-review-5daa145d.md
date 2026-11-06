# R27 / Issue #138 — Yến Identity provenance register v5 — REVIEW DRAFT

**Frozen technical source baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`.

## Corrections after Codex independent audit

1. **F-01 / ID-29 CORRECTED:** The valid file is `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java`, blob `15f11e73b104e34165c9fcfb5738436131a5e07e`, with Bảo-associated Git content commits `e5793ef`, `8905e00`, `f0b71c8`. Earlier register incorrectly used `GatewayKeycloakTokenHttpTest.java` and Yến association. The correction is to Git attribution only; not copyright proof.
2. **F-03 / B02 outstanding:** Bảo's [Issue #138 follow-up](https://github.com/tricore-opentrix/dxos-open-core/issues/138#issuecomment-6676038509) independently verifies both `core/identity/package-info.java` (blob `c57d2207a4eade58e2bf0b03bf6d859be74c7724`) and `infrastructure/identity/package-info.java` (blob `a8901597f86a88822666b11b538285b3327e1397`) introduced in `d53a273`. Exact method and rights remain PENDING; Bảo must attest separately.
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

The same separation applies to `ID-09` and `ID-11` (the two Identity `package-info.java` files introduced by Bảo-associated `d53a273`) and `ID-28` (the Gateway handoff test with Bảo-associated content commits). Yến-associated `fa16f30` added SPDX headers to all four. Each still needs Bảo's file-specific origin and licensing-authority statement and independent review. The historical document `ID-D02` has no SPDX-only commit in this register; its deletion commit is recorded separately and it is outside the current archive.

`SPDX_METADATA_PROVENANCE=VERIFIED`; `CONTENT_ORIGIN_AND_RIGHTS=PENDING`; `APACHE_2_0_AUTHORIZATION=PENDING`.

## Open compliance questions

- Yến: latest `MIXED / NO_KNOWN_NOT_VERIFIED / RIGHTS_UNSURE` alongside historical conditional consent for parts she can lawfully license; exact file-group origin, external sources, AI role and actual rights are not established.
- Bảo: precise creation method, external input, required notices and actual authority for **two package-info files and two Gateway tests**.
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
