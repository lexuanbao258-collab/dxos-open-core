#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 TRICORE contributors
# SPDX-License-Identifier: Apache-2.0
# Configure repository metadata, topics, labels, and milestones.
# Branch protection is configured manually by a repository administrator.
# Usage: DRY_RUN=1 scripts/setup-github.sh
set -euo pipefail

REPO="${REPO:-tricore-opentrix/opentrix}"
DRY_RUN="${DRY_RUN:-0}"
run() {
  if [ "$DRY_RUN" = "1" ]; then
    printf 'DRY-RUN:'; printf ' %q' "$@"; printf '\n'
  else
    "$@"
  fi
}
say() { printf '\n== %s\n' "$*"; }
command -v gh >/dev/null || { echo "GitHub CLI is required: https://cli.github.com" >&2; exit 1; }
if [ "$DRY_RUN" != "1" ]; then
  gh auth status >/dev/null 2>&1 || { echo "Run 'gh auth login' first." >&2; exit 1; }
fi

say "1/4 Repository metadata and merge settings"
run gh api -X PATCH "repos/$REPO" \
  -f description="OpenTrix (DX-OS Open-Core): Identity, Workflow, Data and API Gateway as a headless PaaS, demonstrated by an IT Helpdesk (OLP PMNM 2026)." \
  -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true -F allow_update_branch=true \
  -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=BLANK \
  -F has_wiki=false --silent

# Page URL must be verified independently before setting repository Homepage.
# This setup script deliberately never updates the homepage field.
say "Repository Homepage left unchanged pending verified Pages URL and content"

say "2/4 Topics"
run gh api -X PUT "repos/$REPO/topics" \
  -f 'names[]=olp2026' -f 'names[]=dx-os' -f 'names[]=open-source' \
  -f 'names[]=paas' -f 'names[]=keycloak' -f 'names[]=spring-boot' \
  -f 'names[]=helpdesk' --silent

say "3/4 Labels (area:* and prio:p0)"
label() { run gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force; }
label "area:identity" "1d76db" "Identity Core and Keycloak authentication"
label "area:gateway" "1d76db" "API Gateway, APISIX, and BFF"
label "area:workflow" "d93f0b" "Workflow Core, outbox, and Node-RED"
label "area:data" "d93f0b" "PostgreSQL, MinIO, and Metabase"
label "area:portal" "0e8a16" "IT Helpdesk and Facility portal"
label "area:site" "0e8a16" "Public website, blog, and API documentation"
label "area:infra" "5319e7" "Compose, CI, and deployment"
label "area:comms" "fbca04" "Communications, surveys, and reporting"
label "prio:p0" "b60205" "Blocks the next release"

say "4/4 Release milestones (deadline: 23:59 Vietnam time)"
milestone() {
  local title="$1" due="$2" desc="$3" number
  if [ "$DRY_RUN" = "1" ]; then
    run gh api -X POST "repos/$REPO/milestones" -f title="$title" -f due_on="$due" -f description="$desc" --silent
    return
  fi
  number=$(gh api "repos/$REPO/milestones?state=all&per_page=100" \
    --jq ".[] | select(.title == \"$title\") | .number")
  if [ -n "$number" ]; then
    run gh api -X PATCH "repos/$REPO/milestones/$number" -f due_on="$due" -f description="$desc" --silent
  else
    run gh api -X POST "repos/$REPO/milestones" -f title="$title" -f due_on="$due" -f description="$desc" --silent
  fi
}
milestone "v0.1.0" "2026-11-04T16:59:59Z" "Initial Core and IT Helpdesk release on main, passing CI, Apache-2.0"
milestone "v0.2.0" "2026-11-15T16:59:59Z" "Single-command Compose, APISIX, HTTPS, browser authentication"
milestone "v0.3.0" "2026-11-22T16:59:59Z" "Live pilot, MinIO, Platform API"
milestone "v0.4.0" "2026-11-29T16:59:59Z" "Facility through API, outbox to Node-RED, Metabase"
milestone "v1.0.0" "2026-12-05T16:59:59Z" "OLP PMNM 2026 submission"
echo "Move issues from old M1-M4 milestones to release milestones, then close M1-M4 manually."

say "Remaining manual GitHub administration"
cat <<'TXT'
- Record the current main and gh-pages protection settings before changing them.
- Configure branch protection manually. Require backend, portal-bff, frontend, and
  repo-hygiene checks on main and at least one independent approval. Code-owner
  review and the additional Helpdesk approvals in CONTRIBUTING.md are separate.
- Record protection settings after modification. To restore previous rules,
  reapply the recorded settings; disabling protection is not a complete rollback.
- Create a GitHub Project with Backlog, This Week, In Progress, Review, and Done.
- Move open issues to release milestones, assign area:* labels, and close M1-M4.
- Run scripts/git-cleanup-report.sh to review branches and PRs needing cleanup.
TXT
