<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R26 — OpenTrix repository cutover

**Target:** `tricore-opentrix/opentrix` (OpenTrix — DX-OS Open-Core).
**Status:** REPOSITORY RENAMED / POST-CUTOVER VERIFICATION INCOMPLETE (2026-11-06). **Owner:** Bảo. **Review:** Yến and Ánh. **Tracking:** Issue #135.

## Historical preflight requirements (recorded before rename)

1. The R26 checklist requires no PR awaiting merge. Coordinate a freeze with Yến and Ánh; do not close unrelated PRs just to clear this gate.
2. Capture the current numeric repository ID, `main` and `gh-pages` SHAs, open PRs, branch protection/rulesets, CODEOWNERS, collaborator rights, Pages settings and release/tag references.
3. Verify that `opentrix` is available. Decide and confirm the actual GitHub Pages public URL; `https://tricore-opentrix.github.io/opentrix/` is a **candidate**, not verified live.
4. Review canonical-link PRs on `main` and `gh-pages`. Do not merge links to a repo that does not exist.

## Cutover sequence (rename completed; downstream checks and reviewed merges pending)

1. An administrator renames the **existing** Organization repository in GitHub Settings: `tricore-opentrix/dxos-open-core` → `tricore-opentrix/opentrix`. Do not create a second repository.
2. Verify repository ID unchanged, old URLs redirect, and new clone succeeds. Update each local remote with `git remote set-url origin https://github.com/tricore-opentrix/opentrix.git`.
3. Recheck branch protection, CODEOWNERS, required checks, workflows, issue/PR URLs, external integrations and all three collaborators. Do not weaken independent review requirements.
4. Merge reviewed `main` and Pages PRs in the correct sequence, resolving pending R16 mobile CSS PRs #121/#125 first.
5. Update the published v0.1.0 release notes links and external channel links via their owners; **preserve tag, archive assets and SHA256SUMS bytes**. GitHub Pages URLs do not necessarily redirect after a repository rename.


## Post-rename evidence snapshot — 2026-11-06 (not final R26 acceptance)

The checks below come from read-only GitHub repository/branch/release metadata and a local isolated exact-byte shell verification. They must not be interpreted as a full browser, network, clean-clone, branch-rules or live Pages acceptance. The baseline source SHA is **not frozen for R24**.

| Check | Observation and evidence | Disposition |
| --- | --- | --- |
| New repository | `tricore-opentrix/opentrix`, public, Organization owner `tricore-opentrix`, stable repository ID `1398198415`; GitHub API resolves both older Org and personal paths to the same canonical repository ID. This verifies API alias resolution, **not the HTTP 301/302 redirect status**. | **PASS** new identity / **NOT VERIFIED** HTTP redirects |
| Main branch | `main@5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`; API reports protected and required checks `backend`, `portal-bff`, `frontend`, `repo-hygiene`. Snapshot only, not an independent admin settings readback. | **PASS** metadata presence / **NOT VERIFIED** full policy equivalence |
| Pages branch | `gh-pages@a0e975dedb088d1aff5026636b6748ae731c9ad5`; API reports `protected:true` but enforcement level `off` with empty required check list. Yến/Ánh must evaluate whether expected controls are preserved. | **PENDING** protection review |
| CODEOWNERS and contributors | `.github/CODEOWNERS` is available on `main`; GitHub collaborator API returns `admin` for Bảo, Yến and Ánh. Presence is not review-policy enforcement proof. | **PASS** file and permission presence / **PENDING** policy effectiveness |
| Historical tag/release | `refs/tags/v0.1.0` exists (annotated tag object `98530b948c9200bcb43cec73759882a8750be5f4`); GitHub release ID `433767067` has two assets: `dxos-open-core-0.1.0.tar.gz` (563495 bytes) and `SHA256SUMS` (94 bytes). Neither assets' bytes nor original checksums were re-downloaded here. | **PASS** release metadata / **NOT VERIFIED** asset-byte equivalence |
| Historical workflows | GitHub Actions run [`42207609421`](https://github.com/tricore-opentrix/opentrix/actions/runs/42207609421) reports a successful Pages deployment of `gh-pages@a0e975d`. Prior R26 PR #136 run [`42034280836`](https://github.com/tricore-opentrix/opentrix/actions/runs/42034280836) passed on earlier HEAD `acb8c165`. **Neither is CI approval for the new PR HEAD.** | **PASS** historical metadata / **PENDING** new-head CI |
| Pages public URL | Repository metadata `homepage` is `https://tricore-opentrix.github.io/opentrix/` and `has_pages=true`, but independent live HTTP/content fetch was unavailable; do not silently take this URL as verified. `scripts/setup-github.sh` no longer PATCHes the `homepage` field. | **NOT VERIFIED** live URL/content |
| Clone/network redirects | Local sandbox HTTP and `git ls-remote` failed at DNS resolution, so no clean clone, remote redirect status or fresh-clone acceptance can be claimed. Operator must verify from a network-enabled machine. | **NOT VERIFIED** |
| Setup scripts in PR #136 | PR commit [`b4ca820f`](https://github.com/tricore-opentrix/opentrix/commit/b4ca820f1105bce4a83373be66eee58a28f9541c) restores Git mode `100755` to both scripts. Local isolated fixture exactly matched Git blobs `87ee8548df32bbbbc22baf18f3467748234d0177` and `c5d0e9ca0edd55d0eea37952f52b65ef0cc91b4a`; `bash -n` both PASS, `git diff --cached --check` on isolated fixture PASS, setup `DRY_RUN=1` printed 16 commands with an intercepting `gh` stub and **did not execute network-mutating commands**. This is **not** a full PR checkout `git diff --check`. | **PASS** targeted mode/syntax/dry-run / **PENDING** hosted full PR CI |
| Website and external channels | PR #137 is unmerged; PR #121 (R16 mobile) is open and #125 is closed/superseded. No post-merge deployed-HEAD desktop/mobile browser run, external site owner verification, TikTok check, or verified Pages URL is recorded here. | **PENDING / NOT VERIFIED** |

**Release dependency:** Do not merge the R26 release date PR #141 or complete source-only acceptance PR #139 using the proposed November 6 date without a new, explicitly confirmed future publication plan. R27/#138 rights remain pending. Preserve the original v0.1.0 tag and asset bytes; no tag, GHCR/JAR release, or binary distribution is authorized by this snapshot.

## Acceptance (collect exact links and evidence)

- [ ] Same repository ID; new canonical name; previous personal and org URLs redirect.
- [ ] `git ls-remote https://github.com/tricore-opentrix/opentrix.git HEAD` and clean clone PASS.
- [ ] README, NOTICE, CHANGELOG, docs/scripts use canonical links; pinned historical SHAs still resolve.
- [ ] Release v0.1.0 page, two assets and original SHA256SUMS resolve with unchanged hashes.
- [ ] Live Pages homepage, blog, API and release links work at the verified URL.
- [ ] Protected branches, CODEOWNERS, CI and reviewer requirements remain intact.
- [ ] Ánh confirms website and TikTok changes; Yến and Ánh review R26 evidence.
- [ ] Update Checklist R26; mark Done only after full post-cutover verification.

**Recovery:** Record all affected settings and PR refs before any rename. A rename back alone does not prove recovery of Pages, webhooks, external links or DNS.
