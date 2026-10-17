# DX-OS API catalog — ACTIVE vs BLOCKED (Issue #37)

This catalog describes **registered HTTP routes on `main` at `2115de355e8df4f0567e8e8421dae8465da9cea8`**.
It is distinct from product plans and pure-Java Core interfaces. The local Swagger
group `/v3/api-docs/active` is intended to contain the ACTIVE HTTP routes only;
do not label pure-Java WorkflowRuntime methods as active HTTP endpoints.

> **Security warning:** Registered Request prototype routes are not proof of
> verified OIDC, resource ACL, or safe multitenant production exposure.
> The Request prototype is *not* the generic Workflow Core.
> Only run examples against isolated **loopback** local test deployments.

## ACTIVE — current Spring MVC routes

| Method | Route | Current Request/response contract |
| --- | --- | --- |
| GET | `/api/v1/health` | `200`, `{"status":"UP"}` from the application HealthController; this is **not** the same as Actuator `/actuator/health`. |
| GET | `/api/v1/requests` | `200`, `RequestResponse[]`; list Request prototype records. |
| POST | `/api/v1/requests` | `201`, `RequestResponse` and relative `Location` header; body has `title` (required, <=200), `description` (required), `requestType` (required, <=100). Unrecognized fields are rejected. **Mutates the local database.** |
| GET | `/api/v1/requests/{id}` | `200` `RequestResponse`, `404` if missing, `400` if invalid UUID. |
| POST | `/api/v1/requests/{id}/assign` | `RequestResponse`; body `{"assigneeId":"..."}` (required, <=100). |
| POST | `/api/v1/requests/{id}/start` | `RequestResponse`; Request-specific workflow stage transition. |
| POST | `/api/v1/requests/{id}/resolve` | `RequestResponse`; body `{"resolution":"..."}` (required, <=4000). |
| POST | `/api/v1/requests/{id}/confirm` | `RequestResponse`; Request-specific workflow stage transition. |
| POST | `/api/v1/requests/{id}/close` | `RequestResponse`; Request-specific workflow stage transition. |
| GET | `/api/v1/requests/{id}/history` | `200`, `RequestHistoryResponse[]` for the Request prototype. |

The Request-specific `RequestResponse` includes `id` UUID, `title`,
`description`, `requestType`, `status`, nullable `assigneeId`,
nullable `resolution`, nullable/assigned `version`, `createdAt` and
`updatedAt`. `RequestHistoryResponse` contains `fromStatus`,
`toStatus`, `action`, `changedAt`.

Observed Request-specific safe error handling in `RequestExceptionHandler`:
`400 VALIDATION_FAILED/INVALID_BODY/INVALID_ID`, `404 REQUEST_NOT_FOUND`,
`409 INVALID_REQUEST_TRANSITION/REQUEST_CONFLICT`. This is **not** an
exhaustive contract for every infrastructure failure and **not** a claimed
security 401/403 mapping. Before finalizing annotations, verify actual status
and response DTO via tests and observed runtime OpenAPI.

## BLOCKED or PLANNED — do not include as ACTIVE

| Capability | Planned/unregistered API | Release dependency |
| --- | --- | --- |
| Generic Workflow HTTP | `POST /api/v1/workflow-instances`, `POST /{id}/activate`, `POST /{id}/transitions`, `GET /{id}`, `GET /{id}/history` under `/api/v1/workflow-instances` | **BLOCKED** by #22 (secured adapter), #23 (tenant/resource ACL), #24 (verified OIDC). The controller class is intentionally **not registered as a Spring component**. No Swagger generated operation for it. |
| Keycloak/OIDC backed auth | Protected API bearer/OIDC scheme and 401/403 behavior | **BLOCKED** until Yến's Identity integration (#24/PR #33) is reviewed and wired. Do not invent an auth endpoint, real token verification or auth header context. |
| Storage HTTP | Secured upload/download/metadata/delete endpoints | **PLANNED** pending reviewed Storage adapter #26/PR #43 and HTTP authorization #23/#24. `ObjectStoragePort` is not a public HTTP route. |
| Layer 2 Portal | Independent UI consuming secured APIs | #39 in progress with independently labeled MOCK/DEVELOPMENT data until backends are safe. |

## Contract verification gates

- [ ] Swagger / OpenAPI runtime exported from a real **local-only** backend and parsed as valid OpenAPI 3.x JSON.
- [ ] Enumerate generated path+method pairs and confirm only current registered ACTIVE operations (note grouping rules and actuator handling).
- [ ] Confirm **no** `/api/v1/workflow-instances` path/operation in the JSON, and existing `WorkflowHttpExposureTest` remains PASS.
- [ ] Ensure no tokens, credentials, JDBC/Supabase URLs or internal security headers appear in responses/spec/example values.
- [ ] Default profile denies or has no `/v3/api-docs`, `/v3/api-docs/active`, `/swagger-ui.html`; docs-local binds to 127.0.0.1; verify with actual HTTP tests.
- [ ] Confirm complete request DTO validation, status/error response documentation and full JDK 21 backend regression before declaring Issue #37 done.
