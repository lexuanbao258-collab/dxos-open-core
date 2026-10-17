# Issue #37 — run Swagger / OpenAPI safely on Windows

**Status:** Development staging instructions; these steps are **not** evidence that
the backend has already booted or that Swagger UI has been smoke-tested.

## Access restrictions

OpenAPI JSON and Swagger UI are **disabled in the base `application.yml`**.
Only the explicit `docs-local` profile enables them. That profile sets
`server.address: 127.0.0.1` and disables Swagger `Try it out` methods,
so no write operation should be triggered from Swagger UI. A caller with shell
access can override configuration; don't deploy this profile to a public host.
**No changes to API authentication or data authorization are performed here.**

## Preconditions

- Windows / JDK 21 / Gradle wrapper and a clean, reviewed branch checkout.
- If starting the backend: a **disposable isolated local PostgreSQL** with
  already reviewed Request + Workflow schemas/configuration, a non-admin
  application runtime role and all backend-required `DB_HOST`,
  `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
  `dxos.workflow.schema` values. Set secrets locally, **never in Git**
  or in shared chat logs. Existing Workflow startup guards still apply.
- Do **not** run this against shared Supabase, cloud staging, or publicly
  reachable 0.0.0.0 bind. Do not apply Flyway migrations merely to view docs.

## First: build and verify without starting the server / database

```powershell
cd D:\olp\dxos-api-docs\backend
java -version
.\gradlew.bat test --tests '*OpenApiProfileDefaultsTest' --tests '*WorkflowHttpExposureTest' --tests '*RequestControllerTest' --rerun-tasks --no-build-cache --no-daemon --console=plain
```

These tests check *only* the selected configurations and controller behavior,
not runtime Swagger generation. Capture exact Git SHA and Gradle logs.

## Optional actual local Swagger smoke only after safe DB setup

In a separate PowerShell window with **already verified isolated-local**
database environment and PostgreSQL startup prerequisites configured:

```powershell
cd D:\olp\dxos-api-docs\backend
$env:SPRING_PROFILES_ACTIVE = "docs-local"
.\gradlew.bat bootRun --no-daemon --console=plain
```

After a confirmed healthy startup, inspect using a browser or read-only GET:

- Swagger UI: http://127.0.0.1:8080/swagger-ui.html
- Active group JSON: http://127.0.0.1:8080/v3/api-docs/active
- Application health: http://127.0.0.1:8080/api/v1/health

If a different **local** server port is configured, substitute that port.
For safety, do not click any POST operations or send "Try it out" requests.
Swagger UI config also disables HTTP method submissions.

For a **read-only** JSON path check:

```powershell
$spec = Invoke-RestMethod -Uri "http://127.0.0.1:8080/v3/api-docs/active"
$spec.openapi
$spec.paths.PSObject.Properties.Name
if (@($spec.paths.PSObject.Properties.Name | Where-Object { $_ -like "/api/v1/workflow-instances*" }).Count -ne 0) {
    throw "SECURITY FAILURE: blocked Workflow API appears in published spec"
}
```

If app startup fails on PostgreSQL/guard prerequisites, **stop and fix the
isolated environment**; do not disable production guards, set `permitAll`,
skip unreviewed migrations, or use shared Supabase just to make Swagger start.
The UI and JSON are not verified until a real operator test and reviewer signoff.

## Limitations / next work

- Request prototype API is registered but does not imply integrated
  OIDC/tenant-resource ACL. It is not the generic Workflow Core API.
- springdoc starter is pinned to 2.8.17 for Spring Boot 3.5.x; test its
  exact behavior in the target environment before merge.
- Verify errors/DTO annotations and real docs endpoint disablement with
  integration tests; run full backend build/tests on JDK21.
- Swagger local is documentation only; Developer Portal UI #39 is separate.
