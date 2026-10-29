# DX-OS Portal BFF candidate (Y2.2)

Server-side OAuth boundary imported from PR #70 `d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27`. The integration branch imports the complete `frontend/` tree from PR #71 `c73bbc306a698141169304e5da848ea0686700db`, including its stacked baseline, onto Backend #74 `b6e05d080ebb24b54cc4d61dc4e20e2907fd2eaa`. Only an explicit `integration-local` build connects the Helpdesk Portal to this BFF. Normal builds retain MOCK. LIVE remains blocked; no merge/deployment is authorized.

## Contract

- Serve the built Portal and this BFF through one HTTPS origin. Route `/bff/**`, `/oauth2/authorization/**`, and `/login/oauth2/code/**` to the BFF. Keep the Backend private to the BFF. The BFF process binds to `127.0.0.1`; the trusted HTTPS reverse proxy must reject unknown Host values and strip untrusted forwarding headers.
- Set `PORTAL_PUBLIC_ORIGIN` to the exact HTTPS origin and `PORTAL_OIDC_REDIRECT_URI` to `${PORTAL_PUBLIC_ORIGIN}/login/oauth2/code/keycloak`. Register that exact callback with Keycloak. Use a confidential Authorization Code client with a client secret and logout redirect to `${PORTAL_PUBLIC_ORIGIN}/`. PKCE S256 is added to authorization requests.
- Set `PORTAL_OIDC_ISSUER_URI`, `PORTAL_OIDC_CLIENT_ID`, and `PORTAL_OIDC_CLIENT_SECRET` from a secret manager. The Keycloak access token must contain the Backend's exact issuer, audience, and `resource_access` authority claims; the BFF does not create or rewrite these claims.
- Set `PORTAL_BACKEND_BASE_URL` to the private HTTPS Backend origin, or a literal `127.0.0.1` HTTP origin for a local disposable test. No request header, cookie, or browser token is forwarded. Only the server-held access token becomes an `Authorization: Bearer` header.
- `/bff/session` returns `{authenticated:false}` or `{authenticated:true,csrfToken}` with `Cache-Control: no-store`. Browser writes and `POST /bff/logout` must send the token in `X-CSRF-TOKEN`. The cookie is `Secure`, `HttpOnly`, `SameSite=Lax`, host-only, path `/`; Lax permits the top-level OIDC GET callback while CSRF remains enabled.
- `/bff/api/v1/mvp/requests` accepts the nine Helpdesk candidate routes only. Upstream redirects are rejected. Failed writes return `COMMIT_OUTCOME_UNKNOWN` without retry; reconcile through an authorized detail/history read.

## Verification and gates

Run `backend/gradlew.bat -p portal-bff test` with JDK 21. Tests cover anonymous/forged-header rejection, the actual CSRF header obtained from `/bff/session`, PKCE, confidential-client enforcement, session response, origin checks, bearer forwarding, hidden 404/409 propagation, redirect refusal, and no write retry. Frontend tests cover the concrete transport and session composition with mocked fetch. These checks do not prove a real Keycloak session, refresh, provider logout, HTTPS ingress, or PostgreSQL persistence.

## Local setup (PowerShell, from the integration worktree root)

1. Use a fresh disposable environment; do not restart or reuse the retained Helpdesk/Keycloak data. In this run, reserve PostgreSQL `127.0.0.1:55442`, RunId `browser_20261029`, and a new Keycloak at `127.0.0.1:8182`. The running containers `dxos-stage6-pg17-local` (50835) and `dxos-olp-staging-postgres-1` (55426) are protected and must not be changed. Use `scripts/README-helpdesk-local.md` with the new RunId/port after the real users exist. Keep Java bound to `127.0.0.1:8080`; seed the three actual users' issuer/sub membership and Helpdesk actors in the new database. No Stage6 SQL, Flyway V1–V4, Facility, or MinIO changes are needed here.

2. In realm `tricore`, provision a **new** OpenID Connect client `dxos-portal-bff-local`: Client authentication ON, Standard flow ON, PKCE S256 required, implicit/direct access grants/service accounts OFF. Require `client_secret_basic`. Exact redirect URI: `https://localhost:8443/login/oauth2/code/keycloak`; exact post-logout URI: `https://localhost:8443/`. No wildcard redirects or browser CORS origins are needed. Supply the generated secret only to the BFF process through environment/secret storage. The application refuses reuse of `tricore-api` and public authentication methods.

3. The new isolated environment uses exactly **`http://127.0.0.1:8182/realms/tricore`**. After manual admin/realm setup, verify discovery returns that exact string and pin **both** `OIDC_ISSUER_URI` and `PORTAL_OIDC_ISSUER_URI` to it. The earlier #74 Postman issuer was `http://127.0.0.1:8180/realms/tricore`; the older `.env.example` uses `localhost:8180`. These are different issuer/identity keys, and their old tokens/actors must not be reused. The new Keycloak container is only created, awaiting the user's manual bootstrap admin step. If using an existing trusted HTTPS issuer, use its exact discovery value on both sides and provision the disposable actors for those identities first; never bypass certificate validation.

   Set Backend `OIDC_AUDIENCE=tricore-api`, `OIDC_RESOURCE_CLIENT_ID=tricore-api`. Attach a client scope to the new BFF client that adds access-token audience `tricore-api` (Audience mapper, included client audience `tricore-api`, access token ON). Keep the client-role mapper producing **`resource_access.tricore-api.roles`**, not BFF-client roles or realm roles. Limit role scope to the required roles: Employee `workflow:create`, `workflow:activate`, `workflow:read`, `workflow:transition`; Coordinator/Worker `workflow:read`, `workflow:transition`. Their distinct DB actor memberships/ACL decide workflow capabilities. Inspect claims server-side without logging tokens. See [Keycloak administration](https://www.keycloak.org/docs/latest/server_admin/index.html) for client scopes, audience and role mappers.

4. Build the explicit local composition and run its checks:

   ```powershell
   Push-Location frontend
   npm.cmd ci --ignore-scripts --no-audit --no-fund
   npm.cmd test
   npm.cmd run build:integration-local
   npm.cmd run check:security
   Pop-Location
   ```

   `npm.cmd run build` restores the default MOCK build. Local integration refuses HTTP and non-loopback origins. It reads `/bff/session`, keeps only CSRF in memory, and never reads a demo persona for access. A 401 removes the Helpdesk UI and requires a new login. Unknown writes stay blocked across feature navigation; an authorized detail/history read is for reconciliation, not permission to replay a POST.

5. In a dedicated terminal with JDK 21, start the BFF after providing its secret in the process environment:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   $env:SERVER_PORT = '8081'
   $env:PORTAL_PUBLIC_ORIGIN = 'https://localhost:8443'
   $env:PORTAL_OIDC_REDIRECT_URI = 'https://localhost:8443/login/oauth2/code/keycloak'
   $env:PORTAL_OIDC_ISSUER_URI = 'http://127.0.0.1:8182/realms/tricore'
   $env:PORTAL_OIDC_CLIENT_ID = 'dxos-portal-bff-local'
   $env:PORTAL_BACKEND_BASE_URL = 'http://127.0.0.1:8080'
   if ([string]::IsNullOrWhiteSpace($env:PORTAL_OIDC_CLIENT_SECRET)) { throw 'Supply the BFF client secret through the process environment first.' }
   .\backend\gradlew.bat -p portal-bff bootRun --console=plain
   ```

6. Install Caddy from its official distribution if absent. From the worktree root, validate and run the supplied loopback-only HTTPS ingress:

   ```powershell
   caddy validate --config .\portal-bff\Caddyfile.local --adapter caddyfile
   caddy run --config .\portal-bff\Caddyfile.local --adapter caddyfile
   # In another terminal, after the local CA exists; trust installation may require elevation:
   caddy trust
   curl.exe --fail https://localhost:8443/bff/session
   ```

   Verify browser trust with no certificate exception before logging in. Caddy's local CA must be trusted by the browser/OS; Java must also trust any HTTPS issuer/backend CA it contacts. The ingress strips untrusted forwarding values, pins forwarded host/protocol, serves SPA fallback, rejects `/api/*`, and routes only BFF/OIDC paths to port 8081. Caddy v2.11.7 was downloaded to the ignored `backend/build/helpdesk-browser-local/tools` directory and checked against its published SHA-512 checksum. Validation succeeded. Verified-CA/hostname HTTPS probes return Portal 200, private `/api/*` 404 and mismatched SNI/Host 421 after adding `strict_sni_host on`. The runtime uses a private CA/storage directory and `install_trust=false`; the user must install its public CA in their browser/OS trust store before browser testing. See [Caddy local TLS/trust](https://caddyserver.com/docs/caddyfile/directives/tls), [proxy headers](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), and [SNI/Host enforcement](https://caddyserver.com/docs/caddyfile/options).

## Real browser acceptance — NOT RUN / BLOCKED

Docker Desktop 29.7.2 is available. Retained containers/data were not reused. New Keycloak `dxos-helpdesk-browser-keycloak-20261029` and its private volume/network are prepared but not started; bootstrap admin, client provisioning and secrets are deliberately left to the user. PostgreSQL demo preflight for 55442 passed; actual demo setup awaits real user sub values and setup/runtime passwords. HTTPS ingress and the integration-local Portal build run at 8443, but browser trust installation is still pending. Backend/BFF cannot start until those prerequisites exist, so `/bff/session` currently returns 502. No real BFF→Backend session, browser login, disposable full-stack request, or its persistence is verified. Local manual scripts/instructions are prepared in the ignored `backend/build/helpdesk-browser-local/MANUAL-SETUP.md`; none of their admin/secret/trust steps were executed. BFF/frontend unit tests are not browser E2E evidence.

Backend `test --no-daemon --rerun-tasks --console=plain` was rerun on two newly owned PostgreSQL 17.11 fixtures, including verified TLS and wrong-CA/hostname negatives: Gradle exit 0, **999 tests / 992 passed / 7 skipped / 0 failures/errors**. All nine Workflow DB suites passed (196 cases); Helpdesk passed 46 of 47 cases. The 7 remaining skips require real Keycloak tokens: 6 Identity/Gateway cases and B06. These tests hardcode `http://localhost:8180/realms/tricore`, so the new 8182 issuer's tokens cannot be substituted. The original 155 class-disabled entries expand into parameterized cases when enabled; their counts are not one-to-one. A wrapper port-selection error caused an intermediate failed run; it was corrected to use Docker's allocated port, with no source-test or guard changes, before the successful rerun. Logs/JUnit XML are under `backend/build`; unit/fixture tokens are not evidence of a real Keycloak/BFF browser session.

After setup, use three isolated browser profiles/contexts at `https://localhost:8443/mvp/helpdesk` and genuine Keycloak logins. Create a **new disposable** Employee request; record its returned ID without sensitive data. Coordinator performs Triage then Assign using the server's eligible assignees and current versions. Assigned Worker performs Start then Resolve. Employee performs Confirm. Reload detail and GET history through the BFF: final state `CONFIRMED`, version 4, exactly four transitions `triage`, `start_work`, `resolve`, `confirm`; Assign updates assignmentVersion without adding a workflow transition. Restart Java against the same disposable database and verify detail/history again before claiming persistence.

Check anonymous/expired session 401; valid-session missing/wrong CSRF 403; insufficient role/action 403; unauthorized actor/resource detail and history hidden 404; stale workflow/assignment versions 409 with unchanged state/history; disconnected reads; lost write response `COMMIT_OUTCOME_UNKNOWN` with no second POST. Inspect browser traffic/storage to ensure no direct Backend calls or bearer tokens. Also verify cookie `__Host-DXOS-Portal` is Secure/HttpOnly/host-only/SameSite=Lax and that an unknown Host cannot reach the app. Do not mark any of these cases PASS until actually run. Do not merge or enable production/LIVE from this local candidate.
