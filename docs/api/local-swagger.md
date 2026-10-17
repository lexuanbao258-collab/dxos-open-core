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

## Preferred: database-free Swagger preview for API catalog review

The `docsPreview` Gradle task launches a **test-classpath-only**
Spring MVC application with the actual registered Health and Request controllers
but **Mockito service doubles**. Unlike `bootRun`, it has no DataSource,
Hibernate JPA, Flyway, Workflow persistence component scanning, or DB setup.
All `/api/**` and `/actuator/**` application requests return **403** by
a filter, including GET and POST, so Swagger can display but cannot operate
the prototype business APIs. It explicitly binds to IPv4 loopback.

From a clean checkout of PR #45 in Windows PowerShell:

```powershell
cd D:\olp\dxos-api-docs\backend
.\gradlew.bat test --tests '*DocsPreviewHttpTest' --tests '*OpenApiProfileDefaultsTest' --tests '*WorkflowHttpExposureTest' --rerun-tasks --no-build-cache --no-daemon --console=plain
```

**Only after the focused tests pass**, run in the same directory:

```powershell
.\gradlew.bat docsPreview --no-daemon --console=plain
```

The process intentionally remains running until `Ctrl+C`. Open in the local
browser (port 18080, **not** 8080):

- Swagger UI: http://127.0.0.1:18080/swagger-ui.html
- Grouped OpenAPI JSON: http://127.0.0.1:18080/v3/api-docs/active
- Swagger config: http://127.0.0.1:18080/v3/api-docs/swagger-config

No database environment variables, password, schema preparation or migration
is needed for `docsPreview`. It does not use the temporary `dxos-swagger-pg`
container. **Do not use `bootRun` as an interchangeable command.**
Only docs are functional: health/request business requests are 403 even when
the OpenAPI schema lists them.

This preview is **not** an end-to-end integration of real DB operations, OIDC,
security ACL, or full production app startup. Before Issue #37 DoD, validate
the spec against the actual production Spring configuration in an approved
isolated environment, verify Swagger UI supported submit methods at runtime,
and complete security review. Any missing/ignored empty-method setting is a
blocker for a claim that Swagger UI itself is read-only.

### Read-only verification of preview endpoints

```powershell
$spec = Invoke-RestMethod "http://127.0.0.1:18080/v3/api-docs/active"
$spec.openapi
$spec.paths.PSObject.Properties.Name
if (@($spec.paths.PSObject.Properties.Name | Where-Object { $_ -like "/api/v1/workflow-instances*" }).Count -ne 0) {
    throw "BLOCKED Workflow API appears in docs"
}
```

The preview filter enforces 403 even if the Swagger UI disables fewer buttons
than expected. No need to issue POST requests manually for this preview.

---

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

### Verify Swagger UI really disables submissions

The YAML `supported-submit-methods: []` expresses the intended setting,
but a configuration/unit test **does not prove** it reached Swagger UI.
After local startup, inspect the **read-only** Swagger configuration endpoint:

```powershell
$uiConfig = Invoke-RestMethod -Uri "http://127.0.0.1:8080/v3/api-docs/swagger-config"
$uiConfig | ConvertTo-Json -Depth 8
$hasSetting = $uiConfig.PSObject.Properties.Name -contains "supportedSubmitMethods"
if (-not $hasSetting -or @($uiConfig.supportedSubmitMethods).Count -ne 0) {
    throw "Swagger method submission is NOT verified disabled; do not use Try it out"
}
```

If the empty method list is missing/ignored by the running springdoc version,
treat UI write-disabling as **NOT VERIFIED** and fix the server-side UI config
before claiming the local docs are safe. Never rely on browser UI alone as an
authorization control; use only isolated local test data and do not expose the
Request prototype to untrusted users.

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
