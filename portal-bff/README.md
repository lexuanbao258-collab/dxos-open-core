# DX-OS Portal BFF candidate (Y2.2)

This is an isolated server-side OAuth session boundary for review. It is not connected to the Portal's mock `RequestService`, is not deployed, and does not enable LIVE.

## Contract

- Serve the built Portal and this BFF through one HTTPS origin. Route `/bff/**`, `/oauth2/authorization/**`, and `/login/oauth2/code/**` to the BFF. Keep the Backend private to the BFF. The BFF process binds to `127.0.0.1`; the trusted HTTPS reverse proxy must reject unknown Host values and strip untrusted forwarding headers.
- Set `PORTAL_PUBLIC_ORIGIN` to the exact HTTPS origin and `PORTAL_OIDC_REDIRECT_URI` to `${PORTAL_PUBLIC_ORIGIN}/login/oauth2/code/keycloak`. Register that exact callback with Keycloak. Use a confidential Authorization Code client with a client secret and logout redirect to `${PORTAL_PUBLIC_ORIGIN}/`. PKCE S256 is added to authorization requests.
- Set `PORTAL_OIDC_ISSUER_URI`, `PORTAL_OIDC_CLIENT_ID`, and `PORTAL_OIDC_CLIENT_SECRET` from a secret manager. The Keycloak access token must contain the Backend's exact issuer, audience, and `resource_access` authority claims; the BFF does not create or rewrite these claims.
- Set `PORTAL_BACKEND_BASE_URL` to the private HTTPS Backend origin, or a literal `127.0.0.1` HTTP origin for a local disposable test. No request header, cookie, or browser token is forwarded. Only the server-held access token becomes an `Authorization: Bearer` header.
- `/bff/session` returns `{authenticated:false}` or `{authenticated:true,csrfToken}` with `Cache-Control: no-store`. Browser writes and `POST /bff/logout` must send the token in `X-CSRF-TOKEN`. The cookie is `Secure`, `HttpOnly`, `SameSite=Lax`, host-only, path `/`; Lax permits the top-level OIDC GET callback while CSRF remains enabled.
- `/bff/api/v1/mvp/requests` accepts the nine Helpdesk candidate routes only. Upstream redirects are rejected. Failed writes return `COMMIT_OUTCOME_UNKNOWN` without retry; reconcile through an authorized detail/history read.

## Verification and gates

Run `backend/gradlew.bat -p portal-bff test` with JDK 21. The local tests exercise anonymous/forged-header rejection, CSRF, PKCE, session response, origin checks, bearer forwarding, redirect refusal, and no write retry. They do not prove a real Keycloak session, refresh, provider logout, deployment proxy behavior, PostgreSQL authorization, or a Portal UI adapter.

The base includes PR #46. This BFF review branch excludes the staged PR #52 UI changes, and the frontend remains bound to mocks. Owner Phan Hai Yen and reviewer Le Xuan Bao must review the Identity/Gateway/Helpdesk boundary. Independent security acceptance, real Keycloak/JWKS and PostgreSQL HTTP evidence, a reviewed HTTPS ingress, and Portal UI integration remain gates before LIVE. Status: BFF CANDIDATE APPROVED FOR REVIEW; LIVE_BLOCKED / DO NOT MERGE.
