<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R26 — OpenTrix repository cutover

**Target:** `tricore-opentrix/opentrix` (OpenTrix — DX-OS Open-Core).
**Status:** PREPARED ONLY. **Owner:** Bảo. **Review:** Yến and Ánh. **Tracking:** Issue #135.

## Preflight (do not rename until approved)

1. The R26 checklist requires no PR awaiting merge. Coordinate a freeze with Yến and Ánh; do not close unrelated PRs just to clear this gate.
2. Capture the current numeric repository ID, `main` and `gh-pages` SHAs, open PRs, branch protection/rulesets, CODEOWNERS, collaborator rights, Pages settings and release/tag references.
3. Verify that `opentrix` is available. Decide and confirm the actual GitHub Pages public URL; `https://tricore-opentrix.github.io/opentrix/` is a **candidate**, not verified live.
4. Review canonical-link PRs on `main` and `gh-pages`. Do not merge links to a repo that does not exist.

## Cutover

1. An administrator renames the **existing** Organization repository in GitHub Settings: `tricore-opentrix/dxos-open-core` → `tricore-opentrix/opentrix`. Do not create a second repository.
2. Verify repository ID unchanged, old URLs redirect, and new clone succeeds. Update each local remote with `git remote set-url origin https://github.com/tricore-opentrix/opentrix.git`.
3. Recheck branch protection, CODEOWNERS, required checks, workflows, issue/PR URLs, external integrations and all three collaborators. Do not weaken independent review requirements.
4. Merge reviewed `main` and Pages PRs in the correct sequence, resolving pending R16 mobile CSS PRs #121/#125 first.
5. Update the published v0.1.0 release notes links and external channel links via their owners; **preserve tag, archive assets and SHA256SUMS bytes**. GitHub Pages URLs do not necessarily redirect after a repository rename.

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
