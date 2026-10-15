#!/bin/sh
# Credentials are inherited from environment. Never enable shell tracing.
set -eu
umask 077
: "${MINIO_ROOT_USER:?required}"
: "${MINIO_ROOT_PASSWORD:?required}"
: "${MINIO_STORAGE_ACCESS_KEY:?required}"
: "${MINIO_STORAGE_SECRET_KEY:?required}"
: "${MINIO_STORAGE_BUCKET:?required}"
: "${MINIO_STORAGE_KEY_PREFIX:?required}"
fail() { printf '%s\n' 'MinIO bootstrap failed; verify private configuration and server readiness.' >&2; exit 1; }
printf '%s' "$MINIO_STORAGE_BUCKET" | grep -Eq '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$' || fail
printf '%s' "$MINIO_STORAGE_KEY_PREFIX" | grep -Eq '^[A-Za-z0-9_-]+$' || fail
[ "$MINIO_STORAGE_ACCESS_KEY" != "$MINIO_ROOT_USER" ] || fail
mc_safe() { mc "$@" >/dev/null 2>&1 || fail; }
mc_safe alias set admin http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
# Refuse to adopt an existing identity with other policies or group memberships.
if mc admin user info --json admin "$MINIO_STORAGE_ACCESS_KEY" > /tmp/user.json 2>/dev/null; then
    jq -e '.policyName == "olp-object-storage" and ((.memberOf // []) | length) == 0' /tmp/user.json >/dev/null || fail
fi
mc_safe mb --ignore-existing "admin/$MINIO_STORAGE_BUCKET"
mc_safe anonymous set none "admin/$MINIO_STORAGE_BUCKET"
jq --arg bucket "$MINIO_STORAGE_BUCKET" --arg prefix "$MINIO_STORAGE_KEY_PREFIX" '
  .Statement[0].Resource = [("arn:aws:s3:::" + $bucket + "/" + $prefix + "/v1/*")]
  | .Statement[1].Resource = [("arn:aws:s3:::" + $bucket)]
  | .Statement[1].Condition.StringLike["s3:prefix"] = [($prefix + "/v1/*")]
' /bootstrap/app-policy.json > /tmp/app-policy.json || fail
mc_safe admin policy create admin olp-object-storage /tmp/app-policy.json
mc_safe admin user add admin "$MINIO_STORAGE_ACCESS_KEY" "$MINIO_STORAGE_SECRET_KEY"
mc_safe admin policy attach admin olp-object-storage --user "$MINIO_STORAGE_ACCESS_KEY"
printf '%s\n' 'Private bucket and scoped application identity configured.'
