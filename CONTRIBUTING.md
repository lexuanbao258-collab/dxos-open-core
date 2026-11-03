<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Contributing to DX-OS Open Core

Only work merged into `main` is considered complete.

## Ownership and review

| Responsibility | Person | GitHub |
| --- | --- | --- |
| Core integration, release coordination, and merge coordination | Yến | @phanyen06ym-ien |
| Primary Core testing and review; repository administration | Bảo | @lexuanbao258-collab |
| IT Helpdesk application and related Core review | Ánh | @ngang02012007-crypto |

Development ownership identifies who implements a component. CODEOWNERS requests review for matching files; it does not restrict who may contribute. Independent review requires approval from a person other than the PR author. Manual co-review requires approvals from all named reviewers and is not fully enforced by GitHub CODEOWNERS or branch protection: GitHub may accept one approval from a rule listing multiple owners.

| Changes | Review policy |
| --- | --- |
| Backend Core, infrastructure, and repository governance | Bảo, with Yến as an independent fallback |
| Portal BFF | Bảo and Ánh as code owners; apply Helpdesk co-review when its API is affected |
| Frontend | Yến and Ánh as code owners |
| Helpdesk-related Core paths and BFF API | Both Bảo and Ánh must review and approve |
| Tests authored by Bảo | Yến provides independent review |

Authors cannot approve their own PRs. If Bảo or Ánh authors a Helpdesk-related Core PR, document the conflict in the PR and obtain an independent-review exception or alternative reviewer decision before merge; self-approval never counts. Yến coordinates merges after all applicable approvals and checks, subject to repository permissions and the agreed process. Review and merge responsibilities are distinct.

Regular review windows are 12:00–12:30 and 21:00–21:30. Merge trains run at 21:00 on Tuesdays and Fridays.

## Workflow

```text
Ready issue → branch from main → PR to main → CI → independent approval → squash merge → release
```

1. Give work taking over two hours one issue, one assignee, one milestone, and an `area:*` label.
2. Branch from current `main` with a `<type>/<area>-<description>` name. Keep branches open no longer than three days where practical.
3. Target `main`. Link the issue with `Closes #N`. Keep PRs near the 400-line guideline.
4. Require passing applicable CI and at least one independent approval. Apply additional manual co-review rules above.
5. Squash merge. The PR title becomes the commit title on `main`.

Do not push directly to `main` or `gh-pages`. Do not create `review/*`, `candidate/*`, or `diagnostic/*` branches.

## Daily commands

```bash
# Start a task.
git switch main
git pull --ff-only
git switch -c feat/workflow-outbox-event

# Commit focused changes.
git add -p
git commit -m "feat(workflow): write outbox event in transition transaction"

# Integrate changes from main.
git fetch origin
git merge origin/main

# Push and open the PR.
git push -u origin HEAD
gh pr create --base main --fill --assignee @me

# After merge, remove the local topic branch.
git switch main
git pull --ff-only
git branch -d feat/workflow-outbox-event
```

If a commit is accidentally made on local `main`, preserve it on a topic branch and inspect the working tree before restoring `main`. Never discard uncommitted work.

## Naming and PRs

Use [Conventional Commits](https://www.conventionalcommits.org/) for commits and PR titles: `<type>(<area>): <short lowercase description>`. Allowed types are `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, and `build`. Common areas are `identity`, `gateway`, `bff`, `workflow`, `data`, `helpdesk`, `facility`, `portal`, `site`, `infra`, `docs`, and `repo`.

Use the PR template and describe verification. Aim for at most 150 words in the description. A draft should normally be ready within 48 hours. Avoid stacked PRs; if a dependency is necessary, write `Depends on #N` and wait for that PR to merge.

The 400-line PR guideline currently warns but does not block CI. The REUSE licensing job is also advisory until its separate enforcement work is agreed.

## Review

Conclude reviews with **Approve** or **Request changes**. Attach focused comments to code lines, up to ten per round. Mark blocking comments `[blocking]` and optional suggestions `[minor]`. Keep general summaries short; put longer design analysis in an ADR or issue. Track out-of-scope work in a separate issue.

## Issues, milestones, and completion

Use `area:identity`, `area:gateway`, `area:workflow`, and `area:data` for Core work; `area:portal`, `area:site`, `area:infra`, and `area:comms` for applications and supporting work. `prio:p0` blocks the next release. Release milestones are `v0.1.0` (November 4), `v0.2.0` (November 15), `v0.3.0` (November 22), `v0.4.0` (November 29), and `v1.0.0` (December 5). Project board columns track status; status labels are unnecessary.

A task is done when its PR is merged into `main`, applicable CI passes, issue acceptance criteria are met, new logic has tests, new files have SPDX headers, user-visible changes appear in `CHANGELOG.md`, and README or API documentation reflects changed external behavior.

New source files begin with:

```java
// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0
```

Use `--` for SQL, `#` for YAML and shell, and `<!-- -->` for Markdown.

Each person posts a three-line daily update at 22:00: completed work, next work, and blockers. Yến opens the weekly Sunday report issue with verifiable links.

Never commit secrets, tokens, passwords, or real user data. Use placeholder values in `.env.example`; never commit a real `.env`, raw survey responses, or images without permission.
