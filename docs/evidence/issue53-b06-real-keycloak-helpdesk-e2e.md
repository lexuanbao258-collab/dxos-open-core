# B06 — real Keycloak to actual Helpdesk HTTP to disposable PostgreSQL

## Scope
An opt-in focused B06 test inside HelpdeskHttpPostgresTest uses four real, short-lived Keycloak user access tokens verified through the actual Keycloak discovery/JWKS endpoint. It starts Spring Boot on an ephemeral 127.0.0.1 port, exercises **nine Helpdesk route families** (create/list/detail/triage/assign/start/resolve/confirm/history), and uses the isolated PostgreSQL 18 fixture and EXECUTE-only runtime login. No forged/mock token replaces real Keycloak in the B06 case.

Covered: canonical issuer+subject identity returned by API and stored as requester/assignee in DB; anonymous/forged 401; unauthorized 403; other tenant/non-assignee 404; stale assignment/Workflow version 409; four transitions, history and membership revocation. The other mock-JWKS tests remain separate and do not qualify as B06 real-provider evidence.

## Prerequisites
Windows PowerShell, Java 21, Docker and real Keycloak at http://localhost:8180/realms/tricore, with audience tricore-api. Use four distinct dedicated Keycloak test users: employee (workflow:create, workflow:activate, workflow:read), coordinator (workflow:read, workflow:transition), worker (workflow:read, workflow:transition), foreign (workflow:read). Map client roles to actual resource_access.tricore-api.roles. Obtain separate real tokens via the existing approved OIDC Authorization Code + PKCE client, not manually signed JWTs.

Keep tokens only in the **current PowerShell process environment**:
- B06_EMPLOYEE_ACCESS_TOKEN
- B06_COORDINATOR_ACCESS_TOKEN
- B06_WORKER_ACCESS_TOKEN
- B06_FOREIGN_ACCESS_TOKEN

Never put secrets in PRs, terminal history, screenshots, commit files or chat. Existing script scripts/poc/keycloak-auth-code-poc.ps1 demonstrates PKCE login but does **not** persist tokens; token acquisition must be performed through an approved secure test client.

## Execute
From the checked-out PR #63 working-tree root, after setting those four variables:

    .\scripts\helpdesk-b06-local.ps1 -ExecuteDisposable

This creates its own Docker postgres:18.6-alpine instance bound only to 127.0.0.1:55439, with a new random password, user helpdesk_test, database helpdesk_disposable_test, and **no shared volume**. If port is occupied, it fails; it does not reuse or delete another container. It uses the existing isolated SQL test fixture, then destroys only its own randomly named disposable container on exit. The launcher validates **2 Configuration tests + 31 Helpdesk HTTP/PostgreSQL tests**, zero fail/error/skip (28 original + 2 APP-11 + 1 B06).

Report exact HEAD, Java/PostgreSQL/Keycloak versions, test evidence, negative cases, and cleanup. Distinguish NOT_EXECUTED/ENV_BLOCKED from PASS. No successful run is claimed by these committed sources until independently executed.

**Do not merge or enable LIVE** before B06, PR #58/SQL privileges, controlled provisioning and Portal acceptance are independently verified.
