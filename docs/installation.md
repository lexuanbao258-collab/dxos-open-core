# DX-OS Installation and Verification Guide (Windows / Local Development)

> **Documentation checkpoint — October 25, 2026:** The `main` branch contains the modular-monolith Backend and Core documentation. The React/Vite Frontend is in unmerged [Draft PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51). The integrated Helpdesk API and B06 E2E test candidate are in unmerged [Draft PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63), which also depends on [Draft PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58). **None of the steps below establishes that LIVE integration or full end-to-end acceptance has passed.**

## Prerequisites

- **Git** and **PowerShell** on Windows.
- **Java JDK 21** with a valid `JAVA_HOME` for the Backend. Use the Gradle Wrapper under `backend/`; no global Gradle installation is required.
- **Node.js >= 22.12.0** and npm **only if previewing the Portal in PR #51** (see that branch's `frontend/package.json`).
- **Docker** when running local PostgreSQL verification; the existing Docker Compose configuration is only for its documented local infrastructure.
- **For B06 only:** actual local Keycloak at `http://localhost:8180/realms/tricore`, valid short-lived access tokens from separately provisioned test accounts, an isolated disposable PostgreSQL 18 database, and an authorized operator. Do not substitute mock or locally forged JWTs for real-provider E2E verification.

You do not need to supply passwords to read the source or run most unit tests. Never commit real `.env` files, JWTs, credentials, private keys, or certificates.

## 1. Clone the repository and verify the Backend on main

In PowerShell, use `D:\olp` or a different directory you control:

```powershell
Set-Location D:\olp
git clone https://github.com/lexuanbao258-collab/dxos-open-core.git
Set-Location .\dxos-open-core\backend
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11' # Adjust for your own JDK 21 installation
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat build
```

These commands test **the commit you actually checked out**. They were not executed as part of this documentation change, so this guide does not report any new PASS results. Some integration suites need their own environment gates and may be skipped without the required infrastructure. Consult the [Backend README](../backend/README.md) for supported commands and each test suite's limitations.

Do **not** enable `dxos.helpdesk.enabled` on `main` expecting endpoints from unmerged PR #63. A successful `GET /api/v1/health` response proves only basic health; it does not prove Workflow persistence or live authorization.

## 2. Local PostgreSQL: two different purposes

### A. Reproducible, disposable Workflow verification (preferred)

From `backend/`, after installing **Docker, JDK 21, and OpenSSL from Git for Windows**:

```powershell
powershell.exe -NoProfile -File ..\scripts\workflow-verify-local.ps1
if ($LASTEXITCODE -ne 0) { throw 'Local verification failed' }
```

The existing Backend documentation describes this launcher as creating **disposable PostgreSQL 17.11 on loopback**, running its defined verification suite, and cleaning up only resources it owns. **Inspect the script before running it**, and use only a local Docker daemon you control. This is **not** the real-Keycloak B06 suite.

### B. Persistent local database for development and restart checks

`infra/compose.workflow-postgres.yml` defines **PostgreSQL 17.11**, published on `127.0.0.1:55425`, with database/user `workflow_test` and a **named data volume**.

From `backend/`, **only on your own local Docker daemon**:

```powershell
$localDb = Get-Credential -UserName workflow_test -Message 'Local PostgreSQL credentials'
$env:WORKFLOW_LOCAL_DB_PASSWORD = $localDb.GetNetworkCredential().Password
docker compose -f ..\infra\compose.workflow-postgres.yml up -d --wait
```

**Starting Compose is not enough** to provision the application schema, a separate runtime role, migrations/seed data, Request business tables, or a Portal integration. Follow the [Backend README](../backend/README.md) instructions for starting the Backend from a fresh clone with a **new** local volume, least-privilege setup, and the approved order of operations. Do not assume these commands can safely be repeated against a populated volume.

Stopping Compose normally preserves the volume. Avoid `down -v` while that data is needed, and never blindly replay a migration or smoke test using an existing run ID.

Provisioning commands, Flyway changes, role/ACL operations, data-bearing schema changes, and write-capable smoke tests against **shared, staging, or production** infrastructure are **out of scope** and require separate explicit authorization. Do not default to cloud Supabase credentials.

## 3. Preview the Frontend Portal from Draft PR #51 — mock/development only

The Frontend currently resides in `feature/issue39-portal-ui` / Draft PR #51 and is **not on main** at this checkpoint. Clone into a **separate directory** to avoid changing any ongoing Backend work:

```powershell
Set-Location D:\olp
git clone --branch feature/issue39-portal-ui --single-branch https://github.com/lexuanbao258-collab/dxos-open-core.git dxos-portal-preview
Set-Location .\dxos-portal-preview\frontend
node --version
npm.cmd ci
npm.cmd run dev
```

Open `http://127.0.0.1:5173/session` if Vite reports its default port; otherwise use the URL displayed in your terminal.

Optional build and test commands:

```powershell
npm.cmd run build
npm.cmd run test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
```

See the [Frontend README on the Portal branch](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/README.md) and its [mock demo guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md). This demo uses synthetic fixtures and `MockRequestService`; the Backend is not required. `HttpApiClient` currently blocks LIVE calls. Simulated UI responses for 401/403 do not demonstrate actual Backend authorization.

## 4. Integrated Helpdesk Backend and B06 — test candidate, not a production runbook

[PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) proposes nine Helpdesk routes under `/api/v1/mvp/requests`. Its HTTP contract includes `requestType=IT_HELPDESK`, `state`, `requesterId`, `assigneeId`, `version`, `assignmentVersion`, `allowedActions`, the list envelope `{items,nextCursor}`, and some access-controlled optional detail fields. **These endpoints are not available on main** at this checkpoint.

An authorized operator running the focused **real Keycloak → HTTP → disposable PostgreSQL 18** candidate must first read the [B06 run guide at the recorded candidate HEAD](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/docs/evidence/issue53-b06-real-keycloak-helpdesk-e2e.md). Execute only at the **exact current PR #63 HEAD** being verified. The four real Keycloak tokens must stay in the operator's **process environment**, and the database must be created by the isolated launcher. Never reuse an unrelated database or a token from another test.

**B06 focused PASS is now independently reported:** [Yến's October 25, 2026 evidence](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935) covers **exact HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`** using real Keycloak 26.8.0/PKCE, Java 21.0.12.1, Gradle 8.14.3 and disposable PostgreSQL 18.6. Results: `HelpdeskConfigurationTest` **2 PASS**, `HelpdeskHttpPostgresTest` **31 PASS**, **33 total PASS, 0 FAIL, 0 SKIP**, `BUILD SUCCESSFUL`, `B06_RESULT=PASS`, `B06_EPHEMERAL_CONTAINER=CLEANED`. These are reviewer-reported results, not tests run as part of this documentation PR. The earlier 2 + 28 B05 passes applied only to older HEAD `a3bf42e`. The **complete Portal LIVE and production SQL/provisioning gates remain blocked**; never substitute this focused B06 report for them.

## 5. Verification checklist

- Record the exact checked-out SHA: `git rev-parse HEAD`.
- Keep `main`, Frontend mock, and Backend candidate branches distinct.
- Record operating environment, executed commands, failure/skip counts, and local URLs, with all secrets redacted.
- Never infer that **SSO → Gateway → Helpdesk → PostgreSQL** works merely because React opens or a health endpoint returns JSON.
- For sensitive findings, follow [SECURITY.md](../SECURITY.md). For contributions and review expectations, follow [CONTRIBUTING.md](../CONTRIBUTING.md).

**Deployment limitation:** Running `docker compose up` at repository root is **not** an accepted one-command full-stack deployment for Frontend + Backend + Keycloak. This repository does not yet contain an independently accepted complete stack for that path.
