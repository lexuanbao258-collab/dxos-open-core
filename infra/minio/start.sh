#!/usr/bin/env bash
set -euo pipefail
for name in MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_STORAGE_ACCESS_KEY MINIO_STORAGE_SECRET_KEY MINIO_STORAGE_BUCKET MINIO_STORAGE_KEY_PREFIX; do
  if [[ -z "${!name:-}" ]]; then printf "Missing required environment variable: %s\n" "$name" >&2; exit 1; fi
done
directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
docker compose --env-file /dev/null -f "$directory/compose.yml" build minio
docker compose --env-file /dev/null -f "$directory/compose.yml" up -d --wait minio
docker compose --env-file /dev/null -f "$directory/compose.yml" run --rm minio-init
printf "%s\n" "MinIO is ready; API and console bind to loopback."
