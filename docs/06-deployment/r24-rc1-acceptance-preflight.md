<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 v0.2.0-rc.1 — SOURCE-ONLY acceptance preflight (NOT CLEARED)

This document accompanies the separate draft acceptance JSON. Its presence and all passing CI jobs **do not** constitute distribution approval. Binary/JAR/GHCR acceptance remains BLOCKED; the source-only mode never publishes GHCR images.

## Fixed prerequisites and dependency ordering

1. **Release calendar / source SHA:** the checked-in CHANGELOG currently says `[0.2.0-rc.1] - 2026-11-05`. Align this to the actual *future* tag date, if different, in a separately reviewed/merged source change **before freezing the tagged source SHA**. The current provisional source baseline is `e03fb1aeeafa59dffa18a30a4108d6a67f2c8008`; if any source change is merged, replace this SHA in the acceptance JSON. Do not tag the acceptance PR commit.
2. **Exact-SHA candidate artifact:** run the existing GitHub Actions **R24 release candidate and publication** workflow via `workflow_dispatch` on **main at the frozen source SHA**. A manual run produces distinct `r24-candidate-<run-id>` (binary, unapproved) and `r24-source-only-candidate-<run-id>` (source-only) artifacts. Do not use an older PR candidate or the binary candidate's checksum.
3. **Verified archive digest:** from the downloaded source-only candidate ZIP, inspect the `dxos-open-core-0.2.0-rc.1.tar.gz`, `SHA256SUMS`, and `RELEASE-NOTES.md`. From that directory run `sha256sum -c SHA256SUMS` and `sha256sum dxos-open-core-0.2.0-rc.1.tar.gz`. Record the latter 64-hex digest in the acceptance JSON `archive_sha256`. Check the archived `MANIFEST.json` binds the same `source_sha` and `mode: source-only`.
4. **Source licensing / provenance:** obtain independent evidence for source origin, author/rightsholder permissions and Apache-2.0 redistribution, including the Backend/Core responsibility boundary, from [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138) and a verified reviewable report/PR. No source provenance or permission is automatically implied by Git authorship, SPDX lint, R23 policy, or an AI-assisted contribution.
5. **Source-only scope review:** inspect the actual archive to establish that it contains tracked source files and declared SBOM/notices, but no shipped runtime JAR, built Frontend dist, images, or copied upstream dependency binaries. Review LICENSE/NOTICE and link the exact source/SBOM boundary evidence.
6. **Independent acceptance PR:** replace every null in `r24-acceptances/v0.2.0-rc.1.json` with verified evidence; add `reviewed_by` only as an accurate representation of formal Yến/Ánh acceptance. This draft PR must **not be merged or marked ready** while any fields remain null or Issue #138 has unresolved material ownership rights for released scope. Require independent review of exact source SHA, archive digest, notes and source rights.
7. **Administration BEFORE tag:** Yến must prove (with settings screenshots / links in [Issue #40](https://github.com/tricore-opentrix/dxos-open-core/issues/40)) that `r24-release` has required independent reviewers and that `v*` tag ruleset is active and enforced. The workflow's `environment` key is insufficient evidence by itself.
8. **After accepted/merged PR and confirmed protections:** Yến authorizes creation/push of immutable `v0.2.0-rc.1` pointing at the earlier frozen source SHA. Verify the tag-triggered run regenerates the **same reviewed archive digest**, publishes only the approved source-only GitHub Release artifacts and yields successful SHA256SUMS validation. Source-only approval does not authorize JAR or GHCR publication.

## Blockers at draft creation (2026-11-05)

| Required evidence | Current status |
| --- | --- |
| R24 automation PR #134 | Merged, main `e03fb1a`; shared CI PASS |
| Correct final CHANGELOG RC date | Not confirmed for actual tag date |
| Source-only artifact from frozen main SHA | NOT RUN / SHA256 unknown |
| Independent #138 provenance / rights acceptance | PENDING |
| Exact source-only source/SBOM boundary report | PENDING |
| Protected environment and tag ruleset | Yến to confirm in #40 |
| Binary dependency texts and OCI release | Out of source-only scope; separately BLOCKED |

**Do not replace missing data with fabricated hashes, synthetic links, or blanket legal clearance.**
