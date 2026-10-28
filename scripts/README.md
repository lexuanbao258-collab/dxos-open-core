# Scripts — local OLP MVP

The supported development and verification path uses **JDK 21 and private PostgreSQL 17**, with no hosted database or third-party object-storage dependency.

`workflow-verify-local.ps1` creates a disposable loopback PostgreSQL fixture, uses isolated credentials and TLS checks, runs JDK 21 Backend build/test and Workflow integration suites, then cleans up its own fixture. Requirements: Docker Desktop, JDK 21 on PATH, and OpenSSL (included with Git for Windows).

```powershell
# From repository root on Windows:
powershell.exe -NoProfile -File .\scripts\workflow-verify-local.ps1
```

For a persistent local PostgreSQL setup and safe Backend startup, follow [backend/README.md](../backend/README.md). The application uses `DB_*`, explicit workflow schema and least-privileged runtime credentials; never commit passwords or use a privileged database owner as the running application identity.

**MVP boundaries:**
- Never run operator bootstrap/migration/smoke scripts intended for obsolete remote environments. This README does not authorize such tools.
- **Local MinIO (Docker)** is the selected object-storage target; [Draft PR #34](https://github.com/lexuanbao258-collab/dxos-open-core/pull/34) is not merged and real MinIO integration is NOT VERIFIED. Do not substitute the superseded filesystem-only PR #43 as an MVP acceptance gate.
- Do not alter applied Flyway V1–V4 bytes/checksums, shared databases or production resources.
- A local JUnit PASS does not prove real Keycloak → Portal → BFF → Backend → PostgreSQL + MinIO E2E.

Historical operator tooling is retained only in Git history until the source/test cleanup is verified; it is **not** part of the active OLP instructions.
