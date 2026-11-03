#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
#
# Áp dụng cài đặt GitHub chuẩn cho repo theo CONTRIBUTING.md.
# Cần: GitHub CLI (gh) đã đăng nhập bằng tài khoản có quyền admin của repo.
#
#   DRY_RUN=1 scripts/setup-github.sh   # chỉ in ra lệnh sẽ chạy
#   scripts/setup-github.sh             # áp dụng thật
#
# Chạy lại nhiều lần vẫn an toàn: label và milestone đã có sẽ được cập nhật, không nhân đôi.
set -euo pipefail

REPO="${REPO:-lexuanbao258-collab/dxos-open-core}"
HOMEPAGE="${HOMEPAGE:-https://lexuanbao258-collab.github.io/dxos-open-core/}"
DRY_RUN="${DRY_RUN:-0}"

run() {
  if [ "$DRY_RUN" = "1" ]; then
    printf 'DRY-RUN:'; printf ' %q' "$@"; printf '\n'
  else
    "$@"
  fi
}

say() { printf '\n== %s\n' "$*"; }

command -v gh >/dev/null || { echo "Cần cài GitHub CLI: https://cli.github.com" >&2; exit 1; }
if [ "$DRY_RUN" != "1" ]; then
  gh auth status >/dev/null 2>&1 || { echo "Chạy 'gh auth login' trước." >&2; exit 1; }
fi

say "1/5 Cài đặt repo: chỉ squash merge, tự xoá nhánh sau merge, tắt wiki"
run gh api -X PATCH "repos/$REPO" \
  -f description="DX-OS Open-Core: Identity, Workflow, Data and API Gateway as a headless PaaS, demonstrated by an IT Helpdesk (OLP PMNM 2026)." \
  -f homepage="$HOMEPAGE" \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true \
  -F allow_update_branch=true \
  -f squash_merge_commit_title=PR_TITLE \
  -f squash_merge_commit_message=BLANK \
  -F has_wiki=false \
  --silent

say "2/5 Topics"
run gh api -X PUT "repos/$REPO/topics" \
  -f 'names[]=olp2026' -f 'names[]=dx-os' -f 'names[]=open-source' \
  -f 'names[]=paas' -f 'names[]=keycloak' -f 'names[]=spring-boot' \
  -f 'names[]=helpdesk' --silent

say "3/5 Label (area:* và prio:p0)"
label() { run gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force; }
label "area:identity" "1d76db" "Identity Core, Keycloak, đăng nhập"
label "area:gateway"  "1d76db" "API Gateway, APISIX, BFF"
label "area:workflow" "d93f0b" "Workflow Core, outbox, Node-RED"
label "area:data"     "d93f0b" "PostgreSQL, MinIO, Metabase"
label "area:portal"   "0e8a16" "Portal IT Helpdesk, Facility"
label "area:site"     "0e8a16" "Web giới thiệu, blog, tài liệu API"
label "area:infra"    "5319e7" "Compose, CI, triển khai"
label "area:comms"    "fbca04" "TikTok, khảo sát, báo cáo"
label "prio:p0"       "b60205" "Chặn release gần nhất"

say "4/5 Milestone theo release (hạn 23:59 giờ Việt Nam)"
milestone() {
  local title="$1" due="$2" desc="$3" number
  number=$(gh api "repos/$REPO/milestones?state=all&per_page=100" \
    --jq ".[] | select(.title == \"$title\") | .number" 2>/dev/null || true)
  if [ -n "$number" ]; then
    run gh api -X PATCH "repos/$REPO/milestones/$number" -f due_on="$due" -f description="$desc" --silent
  else
    run gh api -X POST "repos/$REPO/milestones" -f title="$title" -f due_on="$due" -f description="$desc" --silent
  fi
}
milestone "v0.1.0" "2026-11-04T16:59:59Z" "Bản đầu tiên: Core + IT Helpdesk trên main, CI xanh, Apache-2.0"
milestone "v0.2.0" "2026-11-15T16:59:59Z" "Compose một lệnh, APISIX, HTTPS, đăng nhập thật trên trình duyệt"
milestone "v0.3.0" "2026-11-22T16:59:59Z" "Pilot thật, MinIO, Platform API"
milestone "v0.4.0" "2026-11-29T16:59:59Z" "Facility qua API, outbox sang Node-RED, Metabase"
milestone "v1.0.0" "2026-12-05T16:59:59Z" "Bản nộp thi OLP PMNM 2026"
echo "Milestone cũ M1–M4: chuyển issue sang milestone mới rồi đóng bằng tay."

say "5/5 Branch protection cho main và gh-pages"
# Repo cá nhân không hỗ trợ giới hạn ai được bấm merge; quy ước "Yến merge" được giữ bằng CONTRIBUTING.md.
main_protection=$(cat <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["backend", "portal-bff", "frontend", "repo-hygiene"] },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
)
pages_protection=$(cat <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 1 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
)
if [ "$DRY_RUN" = "1" ]; then
  echo "DRY-RUN: PUT repos/$REPO/branches/main/protection"
  echo "$main_protection"
  echo "DRY-RUN: PUT repos/$REPO/branches/gh-pages/protection"
else
  printf '%s' "$main_protection"  | gh api -X PUT "repos/$REPO/branches/main/protection" --input - --silent
  printf '%s' "$pages_protection" | gh api -X PUT "repos/$REPO/branches/gh-pages/protection" --input - --silent
fi

say "Xong. Việc còn làm bằng tay trên GitHub:"
cat <<'TXT'
- Tạo GitHub Project (Board) 5 cột: Backlog, Tuần này, Đang làm, Review, Xong;
  bật workflow mặc định "Item closed -> Xong", "Pull request merged -> Xong".
- Chuyển các issue đang mở sang milestone v0.x mới, gắn label area:*; đóng M1–M4.
- Chạy scripts/git-cleanup-report.sh để rà nhánh và PR cần dọn.
TXT
