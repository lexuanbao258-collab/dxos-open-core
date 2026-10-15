#!/bin/sh
set -eu
umask 077
fail() { printf '%s\n' 'MinIO canary verification failed.' >&2; exit 1; }
: "${MINIO_DEMO_KEY:?required}"
: "${MINIO_DEMO_MODE:?required}"
printf '%s' "$MINIO_DEMO_KEY" | grep -Eq '^demo-[a-f0-9]{32}$' || fail
printf '%s' "$MINIO_STORAGE_BUCKET" | grep -Eq '^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$' || fail
printf '%s' "$MINIO_STORAGE_KEY_PREFIX" | grep -Eq '^[A-Za-z0-9_-]+$' || fail
mc alias set app http://minio:9000 "$MINIO_STORAGE_ACCESS_KEY" "$MINIO_STORAGE_SECRET_KEY" >/dev/null 2>&1 || fail
target="app/$MINIO_STORAGE_BUCKET/$MINIO_STORAGE_KEY_PREFIX/v1/$MINIO_DEMO_KEY"
printf '%s' 'olp-minio-volume-canary' > /tmp/expected
case "$MINIO_DEMO_MODE" in
  create)
# Unique operator-generated key: refuse an existing canary instead of replacing it.
    if mc stat "$target" >/dev/null 2>&1; then fail; fi
    mc cp /tmp/expected "$target" >/dev/null 2>&1 || fail ;;
  verify) ;;
  *) fail ;;
esac
mc cat "$target" > /tmp/actual 2>/dev/null || fail
expected="$(sha256sum /tmp/expected | cut -d " " -f 1)"
actual="$(sha256sum /tmp/actual | cut -d " " -f 1)"
[ "$expected" = "$actual" ] || fail
printf 'Canary verified: %s SHA-256=%s\n' "$MINIO_DEMO_KEY" "$actual"
