# DX-OS public API reference (static)

This branch is **GitHub Pages content only**. It does **not** expose a running Backend, Keycloak, PostgreSQL, bearer token, or a demo sign-in.

- Entry: [index.html](index.html)
- Source contract: draft PR #79 on top of draft PR #74, as of 2026-10-30.
- Status: source-derived MVP documentation. The nine Swagger operations have been visually confirmed on a local loopback-bound deployment, but authenticated end-to-end tests and runtime OpenAPI JSON export remain to be independently verified.
- Public-facing base URL is intentionally **not provided**: there is no publicly deployed Backend API.
- No externally hosted JavaScript, CDN, images, tracking, credentials or API proxy.

## Publish through GitHub Pages

In GitHub repository Settings → Pages:
1. **Build and deployment → Source: Deploy from a branch**.
2. Branch: **gh-pages**, folder: **/(root)**; save.
3. Wait for GitHub's Pages deployment to succeed; then verify the URL shown in Settings, expected on a standard account to be:
   https://lexuanbao258-collab.github.io/dxos-open-core/

Do not report it as published before a real deployment and an external HTTP check pass.

## Next verification gates

- Run [scripts/verify-helpdesk-openapi-local.ps1](https://github.com/lexuanbao258-collab/dxos-open-core/blob/docs/helpdesk-swagger-openapi-20261030/scripts/verify-helpdesk-openapi-local.ps1) on the local machine where Keycloak and Backend are running.
- Verify production docs remain disabled by default and public bind fails closed.
- Independently review source-derived descriptions and compare with exported OpenAPI JSON before calling a machine-readable snapshot verified.
- Do not publish real tokens, account identifiers, internal hostname details or screenshots with secrets.

The website does not claim a formal independent security audit.