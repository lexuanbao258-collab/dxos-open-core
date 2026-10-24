#!/usr/bin/env bash
set -euo pipefail
mode="${1:-}"
case "$mode" in create|verify) ;; *) printf "%s\n" "Usage: bash infra/minio/durability.sh create|verify" >&2; exit 1 ;; esac
: "${MINIO_DEMO_KEY:?Generate and export a unique demo-<32 hex characters> key first}"
if [[ ! "$MINIO_DEMO_KEY" =~ ^demo-[a-f0-9]{32}$ ]]; then printf "%s\n" "Invalid demo key" >&2; exit 1; fi
directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
docker compose --env-file /dev/null -f "$directory/compose.yml" run --rm --no-deps -e MINIO_DEMO_KEY -e "MINIO_DEMO_MODE=$mode" --entrypoint /bin/sh minio-init /bootstrap/durability-inside.sh
