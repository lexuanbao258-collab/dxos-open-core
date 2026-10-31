# DX-OS IT Helpdesk — Swagger/OpenAPI (MVP local)

> **Status:** documentation candidate for the unmerged Java Helpdesk PR #74.
> **Not production-ready.** The Helpdesk API is disabled by default; Swagger
> is separately disabled by default. The core WorkflowController is **not**
> a registered production HTTP controller and must not be described as live.

## Scope and base path

Nine routes are exposed *only when* `DXOS_HELPDESK_ENABLED=true` with the
guarded local PostgreSQL/Keycloak demo configured per
[`scripts/README-helpdesk-local.md`](../../scripts/README-helpdesk-local.md).

| Method | Endpoint | Authorized actor | Purpose |
|---|---|---|---|
| POST | `/api/v1/mvp/requests` | Employee / submitter | Create; HTTP 201 and SUBMITTED |
| GET | `/api/v1/mvp/requests?after=&limit=50` | Tenant + resource ACL | Cursor page of visible requests |
| GET | `/api/v1/mvp/requests/{id}` | Visible, authorized reader | Detail + allowedActions |
| GET | `/api/v1/mvp/requests/{id}/history` | Visible, authorized reader | Four possible Workflow transitions |
| POST | `/api/v1/mvp/requests/{id}/triage` | Coordinator | SUBMITTED → TRIAGED |
| POST | `/api/v1/mvp/requests/{id}/assign` | Coordinator | Assign eligible Worker; separate assignment version |
| POST | `/api/v1/mvp/requests/{id}/start` | Assigned Worker | TRIAGED → IN_PROGRESS |
| POST | `/api/v1/mvp/requests/{id}/resolve` | Assigned Worker | IN_PROGRESS → RESOLVED |
| POST | `/api/v1/mvp/requests/{id}/confirm` | Original submitter | RESOLVED → CONFIRMED |

"Role" above is only the business step. Every call must also pass verified JWT,
active tenant membership, relevant operation authority, visibility/resource ACL,
state and version checks. Identity is verified by Keycloak OIDC; **Keycloak is
not the resource-authorization decision-maker**. A hidden resource is not
necessarily distinguishable from a non-existent one (404).

## Local run: default-off, loopback-only

After setting up a **disposable local PostgreSQL** and Keycloak per the
existing runbook, launch the backend with the existing Helpdesk runtime variables
and additionally:

```powershell
$env:SERVER_ADDRESS = '127.0.0.1'
$env:DXOS_HELPDESK_ENABLED = 'true'
$env:DXOS_OPENAPI_ENABLED = 'true'
Set-Location backend
.\gradlew.bat --no-daemon bootRun
```

Do not override other environment checks or replace real OIDC with fake tokens.
With API docs enabled and server bound to loopback, open:
- UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`
- Only Helpdesk group: `http://127.0.0.1:8080/v3/api-docs/it-helpdesk-mvp`

**Startup fails closed** if OpenAPI is enabled without
`server.address=127.0.0.1`. Swagger paths are denied when disabled.
These docs paths may be read without a token *only in the opt-in loopback
configuration*; the actual business API continues to require an authenticated,
authorized user. Never publish a live Swagger endpoint or bearer token.
To publish documentation, export a reviewed specification and serve it as a
**static artifact**; do not reverse-proxy the local backend.

## Requests and version semantics

Create as Employee:

```json
{"title":"Máy tính không kết nối Internet","description":"Máy tính phòng học không truy cập được mạng.","requestType":"IT_HELPDESK"}
```

Transition payload (`triage`, `start`, `confirm`):

```json
{"expectedVersion":0}
```

Resolve payload:

```json
{"expectedVersion":2,"resolution":"Đã sửa cấu hình mạng; người dùng kiểm tra lại."}
```

Assignment payload:

```json
{"assigneeId":"<canonical-actor-id>","expectedAssignmentVersion":0}
```

The ID above is an **actor ID selected from eligibleAssignees**, not an arbitrary
display name. Use a real value from the current authorized detail response;
do not paste subject IDs or tokens from another environment.

- Workflow starts at `version=0`, then increments on triage, start, resolve
  and confirm, ending at `version=4`.
- Assignment only increments `assignmentVersion`; it does **not** increment
  Workflow `version`, nor create a fifth Workflow history transition.
- `expectedVersion` and `expectedAssignmentVersion` are optimistic-concurrency
  guards. Read current server state before sending a command.
- `allowedActions` is calculated for the *current caller*; it is not the
  source of authority. Backend rechecks permissions and versions at write time.

## Errors and safe usage

- `400`: invalid request or input, where applicable.
- `401`: missing or invalid authentication.
- `403`: visible resource but operation forbidden.
- `404`: hidden/cross-tenant/nonexistent resource (avoid existence leaks).
- `409`: version conflict or invalid transition, depending on mapping.
- An operation can fail after being displayed in Swagger; Swagger only describes
  the interface, **it does not grant permission**.

**Demo safety:** Use a disposable dataset; avoid reusing already terminal
`CONFIRMED` requests, attempting writes against a shared/staging database, or
sharing Keycloak tokens/screenshots that expose credentials.

## Review / verification required

The annotations are source-derived. Before treating the OpenAPI export as
accepted evidence, check: 9 documented routes, proper schemas, 201 create,
JWT behavior, denied writes, version conflicts, list pagination, Swagger off
by default, loopback-only startup, and CI regressions. Test counts must come
from the **new** branch/commit, not from prior PR #74 evidence.


## Runtime OpenAPI export and drift review (added 2026-10-31)

The public Developer Portal currently renders `preview/api/openapi.source-draft.json`
from `gh-pages`. It is a source-derived candidate, **not the direct Springdoc
runtime export**, and must not silently replace an authoritative artifact.

From the existing Windows terminal with the **local** Helpdesk Backend still
running at `127.0.0.1:8080` (real Keycloak issuer and disposable database):

```powershell
Set-Location D:\olp\dxos-swagger-review
git pull --ff-only origin docs/helpdesk-swagger-openapi-20261030
.\scripts\verify-helpdesk-openapi-local.ps1
.\scripts\export-helpdesk-openapi-local.ps1
```

The exporter performs a read-only GET against
`http://127.0.0.1:8080/v3/api-docs/it-helpdesk-mvp`,
requires precisely the nine expected operations, strips the transient
`servers` block, rejects likely embedded credentials, and writes to
`backend/build/openapi-review/it-helpdesk.runtime.local.json`.
It **never uploads or commits** the file. Do not paste any real JWT.

To review the result against a current portal-quality checkout (branch
`docs/portal-quality-gates-20261031`), pass it to the static contract tool:

```powershell
py portal-tools/portal_quality.py --runtime-json "D:\olp\dxos-swagger-review\backend\build\openapi-review\it-helpdesk.runtime.local.json"
```

The tool compares method/path inventory and request property names,
plus response fields where both versions expose properties.
It does **not** replace manual schema/examples/ACL review, and a PASS
does not constitute an authenticated HTTP E2E test.

CI for this Swagger PR now verifies source-controller routes against the
`gh-pages` source draft and runs full Gradle tests on Java 21.
It is **not** a live runtime Springdoc exporter. Do not claim the actual
export has been validated until a reviewed local export exists.
