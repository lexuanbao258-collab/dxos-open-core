<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 v0.2.0-rc.1 — SOURCE-ONLY acceptance preflight (NOT CLEARED)

This document accompanies the separate draft acceptance JSON. Its presence and all passing CI jobs **do not** constitute distribution approval. Binary/JAR/GHCR acceptance remains BLOCKED; the source-only mode never publishes GHCR images.

## Verified update — 2026-11-05

- Yến posted screenshots of the protected `r24-release` environment and the `protect-release-tags` ruleset on [Issue #40](https://github.com/tricore-opentrix/dxos-open-core/issues/40). GitHub's ruleset API independently reports this tag ruleset as `active`. The environment configuration is supported by Yến's screenshots; the connected GitHub API cannot independently read that administration endpoint.
- **Resolved implementation predecessor:** [PR #140 — stabilize packaged SBOM generator data](https://github.com/tricore-opentrix/dxos-open-core/pull/140) has been independently approved and squash merged into `main@5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`. Two manual source-only candidates at that exact SHA have independently verified identical archive SHA-256. This is *only* baseline reproducibility PASS, not source-rights acceptance.
- Do not reuse the original provisional `main@e03fb1a` source SHA. The tested `main@5daa145d` baseline is subject to change if the R26/CHANGELOG PRs merge; the final tag date and source SHA need to be reconfirmed before acceptance.

## Fixed prerequisites and dependency ordering

1. **Release calendar / source SHA:** the checked-in CHANGELOG currently says `[0.2.0-rc.1] - 2026-11-05`. Align this to the actual *future* tag date, if different, in a separately reviewed/merged source change **before freezing the tagged source SHA**. The independently checked **candidate** baseline is `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`; if any source change merges, verify the final SHA and recompute both candidate hashes. Do not place a provisional SHA into final acceptance. Do not tag the acceptance PR commit.
2. **Exact-SHA candidate artifact:** run the existing GitHub Actions **R24 release candidate and publication** workflow via `workflow_dispatch` on **main at the frozen source SHA**. A manual run produces distinct `r24-candidate-<run-id>` (binary, unapproved) and `r24-source-only-candidate-<run-id>` (source-only) artifacts. Do not use an older PR candidate or the binary candidate's checksum.
3. **Verified archive digest:** from the downloaded source-only candidate ZIP, inspect the `dxos-open-core-0.2.0-rc.1.tar.gz`, `SHA256SUMS`, and `RELEASE-NOTES.md`. From that directory run `sha256sum -c SHA256SUMS` and `sha256sum dxos-open-core-0.2.0-rc.1.tar.gz`. Record the latter 64-hex digest in the acceptance JSON `archive_sha256`. Check the archived `MANIFEST.json` binds the same `source_sha` and `mode: source-only`.
4. **Source licensing / provenance:** obtain independent evidence for source origin, author/rightsholder permissions and Apache-2.0 redistribution, including the Backend/Core responsibility boundary, from [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138) and a verified reviewable report/PR. No source provenance or permission is automatically implied by Git authorship, SPDX lint, R23 policy, or an AI-assisted contribution.
5. **Source-only scope review:** inspect the actual archive to establish that it contains tracked source files and declared SBOM/notices, but no shipped runtime JAR, built Frontend dist, images, or copied upstream dependency binaries. Review LICENSE/NOTICE and link the exact source/SBOM boundary evidence.
6. **Independent acceptance PR:** replace every null in `r24-acceptances/v0.2.0-rc.1.json` with verified evidence; add `reviewed_by` only as an accurate representation of formal Yến/Ánh acceptance. This draft PR must **not be merged or marked ready** while any fields remain null or Issue #138 has unresolved material ownership rights for released scope. Require independent review of exact source SHA, archive digest, notes and source rights.
7. **Administration BEFORE tag:** Yến must prove (with settings screenshots / links in [Issue #40](https://github.com/tricore-opentrix/dxos-open-core/issues/40)) that `r24-release` has required independent reviewers and that `v*` tag ruleset is active and enforced. The workflow's `environment` key is insufficient evidence by itself.
8. **After accepted/merged PR and confirmed protections:** Yến authorizes creation/push of immutable `v0.2.0-rc.1` pointing at the earlier frozen source SHA. Verify the tag-triggered run regenerates the **same reviewed archive digest**, publishes only the approved source-only GitHub Release artifacts and yields successful SHA256SUMS validation. Source-only approval does not authorize JAR or GHCR publication.

## Gate snapshot after two candidate runs (2026-11-05)

| Required evidence | Current status |
| --- | --- |
| R24 implementation #134/#140 | Both merged; tested baseline `main@5daa145d` |
| Correct final CHANGELOG RC date | Not confirmed for actual tag date |
| Source-only artifact from tested `5daa145d` baseline | **Two manual runs PASS**; verified sha256 `5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c` — NOT final if SHA changes |
| Independent #138 provenance / rights acceptance | PENDING |
| Candidate archive contents/source boundary | **Technical inventory PASS** at tested baseline; source/asset distribution rights PENDING |
| Protected environment and tag ruleset | Yến published screenshots in #40; active tag ruleset independently observed |
| Binary dependency texts and OCI release | Out of source-only scope; separately BLOCKED |


Independent archive inspection and hashes are recorded in [`r24-rc1-candidate-verification-5daa145d.md`](r24-rc1-candidate-verification-5daa145d.md). This is not substitute for #138 rightsholder evidence or the independent approval of final acceptance JSON.

**Do not replace missing data with fabricated hashes, synthetic links, or blanket legal clearance.**
