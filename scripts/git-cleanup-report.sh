#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
#
# Báo cáo những gì cần dọn trên repo. Chỉ ĐỌC, không xoá hay sửa gì.
# Các lệnh dọn được in ra để người điều phối duyệt rồi tự chạy.
#
#   scripts/git-cleanup-report.sh
#
# Cần: git, GitHub CLI (gh) đã đăng nhập.
set -euo pipefail

REPO="${REPO:-lexuanbao258-collab/dxos-open-core}"
STALE_DAYS="${STALE_DAYS:-7}"

git fetch --prune origin >/dev/null 2>&1

echo "== 1. PR đang mở không nhắm vào main (cần đổi base về main hoặc đóng)"
gh pr list --repo "$REPO" --state open --limit 200 \
  --json number,baseRefName,isDraft,title \
  --jq '.[] | select(.baseRefName != "main" and .baseRefName != "gh-pages")
        | "#\(.number)\tbase=\(.baseRefName)\tdraft=\(.isDraft)\t\(.title)"'
echo "   Đổi base:  gh pr edit <số> --repo $REPO --base main"

echo
echo "== 2. PR draft mở quá 2 ngày"
cutoff=$(date -u -d '2 days ago' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-2d +%Y-%m-%dT%H:%M:%SZ)
gh pr list --repo "$REPO" --state open --draft --limit 200 \
  --json number,createdAt,author,title \
  --jq ".[] | select(.createdAt < \"$cutoff\")
        | \"#\(.number)\t\(.createdAt[0:10])\t@\(.author.login)\t\(.title)\""
echo "   Đóng kèm lý do:  gh pr close <số> --repo $REPO --comment \"Đóng theo CONTRIBUTING: draft quá 48 giờ. Mở lại khi cần.\""

echo
echo "== 3. Nhánh đã merge vào main (xoá an toàn)"
git branch -r --merged origin/main \
  | sed 's|^ *origin/||' | grep -v -E '^(HEAD|main|gh-pages)( |$)' || true
echo "   Xoá:  git push origin --delete <tên-nhánh>"

echo
echo "== 4. Nhánh chưa merge, không có PR mở, không commit mới trong $STALE_DAYS ngày"
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
echo "   Kiểm nội dung trước khi xoá:  git log origin/main..origin/<tên-nhánh> --oneline"

echo
echo "== 5. Issue đang mở thiếu người nhận hoặc milestone"
gh issue list --repo "$REPO" --state open --limit 200 \
  --json number,assignees,milestone,title \
  --jq '.[] | select((.assignees | length) == 0 or .milestone == null)
        | "#\(.number)\tassignees=\(.assignees | length)\tmilestone=\(.milestone.title // "-")\t\(.title)"'
