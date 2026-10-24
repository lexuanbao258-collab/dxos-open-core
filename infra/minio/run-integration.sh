#!/usr/bin/env bash
set -euo pipefail
directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
endpoint="${MINIO_STORAGE_ENDPOINT:-http://127.0.0.1:${MINIO_API_PORT:-9000}}"
# Loopback guard is also enforced inside the Java suite before any SDK operation.
case "$endpoint" in http://127.0.0.1:*|https://127.0.0.1:*|http://localhost:*|https://localhost:*) ;;
  *) printf "%s\n" "Integration tests require a loopback MinIO endpoint." >&2; exit 1 ;;
esac
curl --fail --silent --max-time 5 "$endpoint/minio/health/ready" >/dev/null
export MINIO_TEST_ENDPOINT="$endpoint"
export MINIO_TEST_ACCESS_KEY="${MINIO_STORAGE_ACCESS_KEY:?required}"
export MINIO_TEST_SECRET_KEY="${MINIO_STORAGE_SECRET_KEY:?required}"
export MINIO_TEST_BUCKET="${MINIO_STORAGE_BUCKET:?required}"
export MINIO_TEST_KEY_PREFIX="${MINIO_STORAGE_KEY_PREFIX:?required}"
export MINIO_TEST_STAGING_DIRECTORY="${MINIO_STORAGE_STAGING_DIRECTORY:?required}"
cd "$directory/../../backend"
bash ./gradlew minioIntegrationTest --rerun-tasks --console=plain
