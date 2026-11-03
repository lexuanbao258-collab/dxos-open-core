#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
#
# Report repository cleanup candidates. This script does not delete or modify them.
# Commands are printed for the merge coordinator to inspect and run manually.
#
#   scripts/git-cleanup-report.sh
#
# Requires git and an authenticated GitHub CLI (gh).
set -euo pipefail

REPO="${REPO:-lexuanbao258-collab/dxos-open-core}"
STALE_DAYS="${STALE_DAYS:-7}"

git fetch --prune origin >/dev/null 2>&1

echo "== 1. Open PRs targeting neither main nor gh-pages"
gh pr list --repo "$REPO" --state open --limit 200 \
  --json number,baseRefName,isDraft,title \
  --jq '.[] | select(.baseRefName != "main" and .baseRefName != "gh-pages")
        | "#\(.number)\tbase=\(.baseRefName)\tdraft=\(.isDraft)\t\(.title)"'
echo "   Change base: gh pr edit <number> --repo $REPO --base main"

echo
echo "== 2. Draft PRs open longer than two days"
cutoff=$(date -u -d '2 days ago' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-2d +%Y-%m-%dT%H:%M:%SZ)
gh pr list --repo "$REPO" --state open --draft --limit 200 \
  --json number,createdAt,author,title \
  --jq ".[] | select(.createdAt < \"$cutoff\")
        | \"#\(.number)\t\(.createdAt[0:10])\t@\(.author.login)\t\(.title)\""
echo "   Close with a reason: gh pr close <number> --repo $REPO --comment \"Closing under CONTRIBUTING: draft exceeds 48 hours. Reopen when ready.\""

echo
echo "== 3. Branches already merged into main"
git branch -r --merged origin/main \
  | sed 's|^ *origin/||' | grep -v -E '^(HEAD|main|gh-pages)( |$)' || true
echo "   Delete after review: git push origin --delete <branch-name>"

echo
echo "== 4. Unmerged branches without an open PR or a commit in $STALE_DAYS days"
open_heads=$(gh pr list --repo "$REPO" --state open --limit 200 --json headRefName --jq '.[].headRefName')
now=$(date +%s)
git for-each-ref --format='%(refname:strip=3) %(committerdate:unix) %(committerdate:short)' refs/remotes/origin \
  | while read -r name ts day; do
      case "$name" in HEAD|main|gh-pages) continue ;; esac
      if git merge-base --is-ancestor "origin/$name" origin/main 2>/dev/null; then continue; fi
      if printf '%s\n' "$open_heads" | grep -qx "$name"; then continue; fi
      if [ $(( (now - ts) / 86400 )) -ge "$STALE_DAYS" ]; then
        printf '%s\tlast=%s\n' "$name" "$day"
      fi
    done
echo "   Inspect before deletion: git log origin/main..origin/<branch-name> --oneline"

echo
echo "== 5. Open issues missing an assignee or milestone"
gh issue list --repo "$REPO" --state open --limit 200 \
  --json number,assignees,milestone,title \
  --jq '.[] | select((.assignees | length) == 0 or .milestone == null)
        | "#\(.number)\tassignees=\(.assignees | length)\tmilestone=\(.milestone.title // "-")\t\(.title)"'
