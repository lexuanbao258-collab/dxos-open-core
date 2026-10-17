# TRICORE DX-OS — Local FileSystem Storage (demo provider)

**Status: Draft PR; Windows/JDK21 focused tests and full backend regression were reported PASS by the operator at `ee8a541` (827 tests), before a 2026-10-17 POSIX directory-durability change in `4b38aee`. The new commit has NOT yet been independently built or tested on Windows/Linux.** This provider is designed for the OLP demo with **no Supabase account, cloud API key, MinIO server, S3 SDK or extra object-storage container**. It implements the existing `com.tricore.dxos.core.data.ObjectStoragePort` without any Core changes. It does **not** remove PR #34 MinIO or PR #35 Supabase and does not replace PostgreSQL for structured data.

## Installation — easiest local path (Windows)

Prerequisites: **JDK 21**, Git, Internet for the first Gradle dependency download. For storage-only tests, **Docker and PostgreSQL are not needed**.

From a *separate checkout* of this branch, in PowerShell:

```powershell
git clone -b feature/local-filesystem-storage-demo --single-branch https://github.com/lexuanbao258-collab/dxos-open-core.git "D:\olp\dxos-local-storage"
cd "D:\olp\dxos-local-storage"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
java -version
.\gradlew.bat test --tests 'com.tricore.dxos.infrastructure.storage.local.*' --rerun-tasks --no-build-cache --no-daemon --console=plain
```

The command runs local provider tests including one cross-process `CREATE_ONLY` race (two child JVMs sharing a temporary test directory). Tests use their own `@TempDir` and **never reach hosted Supabase/MinIO**. Operator-reported Windows/JDK21 evidence at the previous SHA `ee8a541`: focused local-storage tests PASS and full disposable PostgreSQL 17.11 regression **827 PASS / 0 FAIL / 0 SKIP**. These are not GitHub CI/Linux results. **Rerun tests on the current PR HEAD before merging.**

## Using local storage as the default in the backend

The Spring bean is selected when `dxos.object-storage.provider` is **missing** or equals `local`. Unless overridden, the storage root is:

```text
backend/.dxos-storage/
```

The folder is created automatically by the adapter when needed, and is ignored by Git. If starting `bootRun` from `backend/`, no storage-related environment variables are required.

Optional configuration (environment variables understood by Spring Boot):

| Setting | Default | Meaning |
| --- | --- | --- |
| `DXOS_OBJECT_STORAGE_PROVIDER` | `local` when absent | Select storage provider; other providers are optional separate PRs |
| `DXOS_OBJECT_STORAGE_LOCAL_ROOT` | `./.dxos-storage` | Absolute/relative path to a **private persistent** directory |
| `DXOS_OBJECT_STORAGE_LOCAL_MAX_OBJECT_BYTES` | `10485760` (10 MiB) | Per-object cap |
| `DXOS_OBJECT_STORAGE_LOCAL_MAX_METADATA_BYTES` | `16384` (16 KiB) | Stored metadata envelope cap |

For production Linux, set a dedicated absolute root such as `/var/lib/dxos/storage` (owned and writable only by the backend OS user). In Docker, mount a persistent **private volume** at that path. Do **not** place uploaded files in a public web directory, shared Git checkout or ephemeral image layer.

**Backend startup still needs PostgreSQL and the independently approved Workflow schema/privilege configuration.** Follow [backend setup](../../backend/README.md) for `DB_*`, `DXOS_WORKFLOW_SCHEMA`, `SPRING_FLYWAY_ENABLED`, and schema preparation; this storage feature must not run new cloud DB migrations or weaken Workflow DB guards. It does not turn the whole app into a PostgreSQL-free service.

## Storage guarantees / architecture

- User object keys never become filesystem paths: their UTF-16 code units are hashed with SHA-256. This preserves case, traversal-like text, slashes and unpaired surrogates without directory traversal.
- Atomic publication: each write creates a new immutable content+metadata version, syncs files, and atomically moves a small `CURRENT` pointer. **On POSIX filesystems**, the adapter additionally fsyncs its storage directories before publication and fsyncs the key directory after pointer update/deletion. This reduces crash-window risk but **does not prove immunity to abrupt power loss** on every filesystem/hardware configuration. Directory fsync is not portable to Windows and is skipped there. `CREATE_ONLY` and `OVERWRITE` use an on-disk lock with an in-JVM striped lock. Concurrent creators across Java processes should produce exactly one success; prior Windows tests exercised this.
- `read` acquires that lock and creates a private, bounded snapshot of the pinned version before returning a stream, preserving the matching metadata/content through later overwrite or delete; `close` cleans up snapshot. A restart can read the latest persisted version.
- `delete` removes `CURRENT`; missing and repeated delete return `NOT_FOUND`. Old immutable versions are pruned best-effort after successful replacements; crash or IO interruption can leave staging/snapshot/orphan-version files for **operator reconciliation**.
- File errors use sanitized `StorageException` codes. Caller input is never closed; caller-data read failures map to `INVALID_INPUT`.
- Data directory must be private, under a single server's administration. Symlinked configured roots are rejected; relying on secure OS directory ownership/permissions is still mandatory.

**Limits:** This is a **single-host** storage option. Multi-node deployment needs shared *strongly consistent* storage with cross-node filesystem locks or a different provider. Atomic rename and file locks must be supported by the filesystem; do not assume network filesystems, Docker bind mounts across hosts, or abrupt power loss guarantee transactional durability without verification and backups. The adapter copies snapshots, so concurrent reads/staging can require more disk than the stored object size. No global disk quota, user/tenant ACL, HTTP upload endpoint or guaranteed crash-garbage collector is included.

**Security gate before untrusted uploads:** Identity/Gateway must authorize tenant+object access *before* calling the port and enforce request body byte limits, idle/overall deadlines, concurrency admission, storage quota and safe orphan cleanup. Because the Core contract consumes caller streams to EOF, oversized/stalled streams still require deadline and cancellation at the HTTP/request owner. Local storage alone does **not** supply end-user authorization, nor does the Workflow PostgreSQL SECURITY DEFINER review protect OS files.

## Linux staging review and recovery procedure (pending)

- **Re-test current commit** on a POSIX filesystem (Ubuntu ext4/EBS volume recommended) using JDK 21, including cross-JVM CREATE_ONLY, overwrite/delete, fresh-process restart and application restart with a bind-mounted/persistent volume. A clean Java unit test does **not** simulate abrupt host power loss or EBS corruption; do not label it a proven power-cut test.
- Provision a **dedicated, private storage root and persistent mount** before starting the application. Ensure the backend service account owns it, verify real OS ACLs, and confirm the mount survives container recreation; do not rely on the container writable layer.
- **Disk headroom:** The per-object limit does not prevent many objects, concurrent upload staging, or pinned read snapshots from exhausting a small volume. Before enabling user uploads, impose request size/deadline/concurrency and per-tenant/total space quotas in the authorized HTTP layer, plus free-space alerts and backup/restore rehearsal.
- **Crash/temporary-file reconciliation:** A stopped or crashed process may leave `.upload-*.tmp`, `.pointer-*.tmp`, `.snapshot-*.tmp` or unreferenced `v-*.blob/.meta` files. Stop all writers/readers, take a verified backup and inventory `CURRENT` references before any manual cleanup. **Do not** indiscriminately delete a version referenced by `CURRENT`; do not run unsafe cleanup concurrently with the service. Automated crash GC is not part of this PR.
- A failed directory fsync *after* publication can leave an **uncertain outcome**, not a guaranteed rollback. Clients must reconcile state before retrying writes. POSIX directory fsync and file locks must be supported by the actual volume/filesystem. Windows remains best-effort and needs a different platform-specific durability strategy for production.

## Acceptance checklist (before merging)

- [x] JDK21 `LocalStorageContractTest` including independent-JVM race: operator-reported PASS on Windows at `ee8a541`; repeat after the latest fsync change.
- [x] Full backend regression using disposable PostgreSQL 17.11: operator-reported **827 PASS / 0 FAIL / 0 SKIP** at `ee8a541`; repeat on latest HEAD.
- [ ] Fresh-process restart, snapshot during concurrent overwrite/delete, zero bytes, Unicode, failure sanitization and symlink rejection checked on target filesystem.
- [ ] Confirm OS ACLs/ownership, persistence mount/backup/restore, staging/headroom quota and crash cleanup process.
- [ ] Separate review; existing PR #35/#41/#42 remain independent and unmerged until explicitly decided.
- [ ] Before OLP submission, complete [License/SPDX audit #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38). Standard Java I/O avoids adding a new storage-server dependency, but does not automatically finish the repository's license obligations.

**No hosted Supabase data was read or modified in creating this provider.**
