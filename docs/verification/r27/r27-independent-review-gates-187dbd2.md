<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 — reconstructed package validation and independent review gates

**Scope:** four new documentation artifacts (CSV register, CSV REUSE sidecar, contributor routing/declaration notes, this checklist) reconstructed from previously verified R24 candidate archives and independently read GitHub R27 statements. **Not the missing uncommitted originals.** This Draft PR records documentation evidence only; it is **not** a contributor signature, rights clearance, merger authorization, public release approval, tag or source/binary publication.

**Review status:** The ten checks below concern **technical integrity and routing of this documentation package**, not independent clearance of the underlying source rights. At the original PR HEAD `7c458be171135554a85c7ea02e624d8a0cdfcb1e`, [CI run 42346124296](https://github.com/tricore-opentrix/opentrix/actions/runs/42346124296) passed all five jobs (`backend`, `frontend`, `portal-bff`, `r23-compliance`, `repo-hygiene`). This is **commit-specific evidence**; any later commit requires fresh CI verification. No independent contributor-rights verdict is recorded merely by these technical checks. The `TRICORE contributors` SPDX copyright label on this new documentation also requires confirmation of actual attribution authority; its presence does not license or clear the source files inventoried in the register.

## File identities

- [357-row remediation CSV](r27-rights-remediation-register-187dbd2.csv) — 347 `TRACKED_GIT_PATH` rows plus 10 `ARCHIVE_METADATA` rows. Metadata entries are **not** ten extra Git paths. Columns include exact original Git path, expected archive member, Git blob SHA-1 (tracked), actual distributed SHA-256 (included), contributor *evidence coordinator*, independent reviewer, unresolved evidence and source link.
- `r27-rights-remediation-register-187dbd2.csv.license` — CSV REUSE sidecar. This metadata labels newly reconstructed documentation under the repository's existing Apache-2.0 convention. It is **not evidence that rights in referenced third-party source have been cleared**; independent review of project-wide copyright labels remains open.
- [Existing human statements and scope-safe routing](r27-contributor-declarations-and-routing-187dbd2.md) — distinguishes source authors, content vs SPDX changes, Git history and declared AI workflows; no invented first-person rights claims.
- This document — tests, blockers and human release decision gates.

## R24 exact-source and archive checks

| Check | Result | Evidence |
| --- | --- | --- |
| Git source SHA | `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e` | [main tree](https://github.com/tricore-opentrix/opentrix/tree/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e); source archive `MANIFEST.json` |
| Git tracked **file** paths | **347** | Source archive includes 346 Git text blobs; one independently identified upstream Gradle binary is absent; reconstructed 347 path/blob aggregate matches GitHub tree fingerprint |
| Source archive regular files | **356** | 346 `source/` entries + 10 archive metadata entries |
| Backend paths | **167 tracked**, **166 shipped** | Exact file path prefixes in reconstructed CSV and tar source |
| Excluded file | **1**, `backend/gradle/wrapper/gradle-wrapper.jar` | Git blob SHA-1 `1b33c55baabb587c669f562ae36f953de2481846`; no jar in archive |
| Two independent candidate runs | Candidate PASS, publication SKIPPED | [first](https://github.com/tricore-opentrix/opentrix/actions/runs/42244683510) and [second](https://github.com/tricore-opentrix/opentrix/actions/runs/42245290247) |
| Archived tar.gz SHA-256 (both) | `93e6e0ba5d708120c6ec494af3e50c8959cccb4c442a8bba9a2e9c617c2d448f` | Byte-for-byte identical payloads in two R24 ZIP artifacts |
| MANIFEST path/hash checks | **355/355 match**, with the manifest itself excluded from its own hash map | `MANIFEST.json` and actual archived members |
| Source/Git tree aggregate | `FNV-1a-64 = eba26f7cd47ac7dd` for canonical sorted `path\tgit_blob_sha\n` lines | Same fingerprint independently computed from GitHub's **untruncated** recursive tree at `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`; noncryptographic cross-check, individual Git SHA-1 values are separately reconstructed from archive source bytes with Git text normalization where applicable |

**Archive metadata (10 paths, not Git paths):** `CHANGELOG.md`, `LICENSE`, `MANIFEST.json`, `NOTICE`, `RELEASE-NOTES.md`, `THIRD-PARTY-NOTICES.md`, `compliance/backend.cdx.json`, `compliance/frontend.cdx.json`, `compliance/license-inventory.json`, `compliance/portal-bff.cdx.json`.

## Ten-point independent-review matrix

| # | Mandatory check | Reconstruction verification | Remaining decision |
| --- | --- | --- | --- |
| 1 | Exactly 347 Git paths and 10 metadata entries | **PASS** — CSV has 357 data rows (347+10), unique path and archive-member sets | Review owners and disposition separately |
| 2 | Exactly one tracked Gradle Wrapper JAR excluded | **PASS** — JAR is only absent tracked Git blob | Upstream rights/attribution of **included** Gradle scripts still PENDING |
| 3 | No unsupported `CLEARED` per file | **PASS** — all 357 rows have `RIGHTS_PENDING`; no licensed ownership inferred | Human source-rights reviews required |
| 4 | Routing not copyright ownership | **PASS** — columns distinguish `evidence_action_owner`, `claimed_copyright_owner=UNVERIFIED`, independent reviewer | Determine actual author/rightsholder per portion |
| 5 | 166 shipped Backend != 167 tracked Backend | **PASS** — 166 packaged, one excluded upstream JAR | No claim Bảo authored all 166 |
| 6 | Data/Storage overlap split | **PASS** — two `fcc45fb` files routed as mixed hunks, distinct Ánh original vs Bảo later diff | Human per-portion confirmation; Yến independent review |
| 7 | ID-09/11/28/29 disclosures preserved | **PASS** — linked Bảo reports and narrow outside-input uncertainty | Outside code/fixture, NOTICE and licensable portion pending |
| 8 | Yến and Ánh statements preserved | **PASS** — limited statements linked without implied ownership of 107 paths or another member's work | Independent rights assessment, favicon and prompt/source provenance |
| 9 | All pending rights rows have a named evidence coordinator and ask | **PASS** — 357 rows each contain specific action text, a person and distinct reviewer | Reviewer must confirm genuine scope, not merely routing correctness |
| 10 | Internal links, CSV, blobs, privacy, whitespace | **PASS** — UTF-8/CSV structure, 347 Git blobs, 356 archive hashes, links, privacy and whitespace checked; REUSE 6.3.0 `lint-file` passed for all four files | GitHub Draft PR CI/REUSE independent checks still required after upload |

## Blocking human confirmations (no automatic legal clearance)

| Priority | Evidence action owner | Exact required follow-up | Independent reviewer |
| --- | --- | --- | --- |
| P0 | **Bảo** | ID-09/11 exact outside-source (if known), per-portion license/NOTICE; ID-28/29 JWT/Keycloak snippet and fixture lineage, or explicitly unresolved; scope of Bảo's own `fcc45fb` diffs. | Yến |
| P0 | **Yến** | Resolve Identity G3/G4 origin/fixture provenance and per-portion licensable rights (not just Git/SPDX), plus historic Identity exceptions and Apache-2.0 authorization limited to own rights. | Ánh |
| P0 | **Ánh** | Resolve favicon upstream fragment/template origin and NOTICE, identify Portal screen-name changes by actual Git hunk, Data original rights; reconcile 107 path/blob technical review with actual authorship. | Yến (Bảo for Data technical cross-check) |
| P0 | **Bảo** | Verify Gradle upstream Wrapper scripts and notices; distinguish the single excluded JAR from included generated scripts/properties. | Yến |
| P0 | **Yến** | Allocate true authors and reviewers for remaining repository files; itemize and disposition 37 historical exception paths, collective `TRICORE contributors` label and any copied third-party material. | Bảo and Ánh as independent scoped reviewers |
| P0 | **Yến** | Confirm archived `LICENSE`, `NOTICE`, third-party SBOM metadata and any required upstream texts/permissions; source-only vs future binary distribution constraints. | Bảo |

## Release acceptance boundary

- `#138` must remain **OPEN** until independently reviewed rights/attribution gaps are genuinely resolved or appropriate removal/rewrite/relicensing has passed review.
- `#139` must remain **Draft** with the stable acceptance record unfilled until real sign-offs and original-source evidence exist; PR CI PASS does not authorize public distribution.
- `#142` is Yến's separate Draft Identity evidence work; this package does not replace its declarations or modify it.
- No commit, push, tag, GitHub Release, Docker image or other distribution is authorized by this reconstructed material. If source SHA changes during remediation, rebuild the candidate and redo the two stable independent verification runs.
- Package may be submitted as a *documentation-only Draft PR for review* after checkout into the correct repository and local Git/REUSE/CI checks, but should **not** be merged or treated as release acceptance before a real reviewer verdict.

## Local validator contract

The ZIP is expected to contain **exactly four files**, preserving their relative directory paths under `docs/verification/r27/`. Validation of reconstructed materials checks:

1. CSV UTF-8, rectangular columns, 357 unique entries, distinct type counts, no duplicate Git or metadata paths, correct archive-inclusion statuses, no formula prefix in path fields.
2. SHA-1 Git blob identifiers for all included source files (computed from archived bytes with Git CRLF-to-LF normalization where needed), one independently obtained excluded JAR SHA, SHA-256 for every packaged member, and canonical 347-file Git tree aggregate.
3. Every row has valid human evidence owner, concrete request, independent reviewer and `RIGHTS_PENDING`, with mixed Data hunk routing not flattened into single-file ownership.
4. Relative links stay within the four reconstructed files, external links use explicit GitHub URLs; no raw prompt/session history, real JWTs, tokens, private contact details, or secret-bearing source dumps included.
5. Markdown+CSV+license sidecar have LF line endings, UTF-8, no trailing whitespace and end-of-file newline.
