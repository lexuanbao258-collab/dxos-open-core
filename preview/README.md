# DX-OS Developer Portal V2 — PREVIEW

**Public preview path:** https://lexuanbao258-collab.github.io/dxos-open-core/preview/

This directory is a candidate of a documentation-first developer portal inspired by **information architecture**, not a copy of Appwrite or Scalar artwork. The existing production GitHub Pages root \`/dxos-open-core/\` is left unchanged.

## Pages
- \`/preview/\` — overview, route table, Quickstart cards, search, light/dark theme.
- \`/preview/guides/\` — environment setup, Keycloak JWT vs ACL, Workflow and HTTP error handling.
- \`/preview/architecture/\` — conceptual trust boundaries and evidence ledger.
- \`/preview/api/\` — **Scalar API Reference** with a source-derived draft OpenAPI 3.1 specification.
- \`/preview/api/openapi.source-draft.json\` — source-authored schema, **not an official runtime springdoc export**.

## Source of truth and verification
Source: draft PR #79 based on draft PR #74, controller DTO, service response records, and \`docs/api/helpdesk-swagger.md\`.

Verified on a user-run local machine by 2026-10-30:
- Gradle \`test build\` success;
- Browser Swagger displays nine operations;
- Local OpenAPI/HTTP script: exact 9 routes, bearer security scheme and 201 create in JSON, missing/invalid JWT requests return 401.
Unverified:
- valid-token Employee/Coordinator/Worker E2E (local setup had ACTORS=0), 403/404/409, nonloopback HTTP rejection in full runtime;
- exact agreement between **this manually transcribed source-draft spec** and the runtime-generated OpenAPI JSON;
- production deployment of Backend or public API endpoint.

## Safety
- The website is static and does not proxy the Backend, expose a public API server, or contain credentials.
- Scalar configuration: \`hideTestRequestButton: true\`, \`persistAuth: false\`, no proxy URL, Agent disabled, telemetry disabled, developer tools hidden.
- Scalar ESM bundle is loaded from a **version-pinned CDN**: \`@scalar/api-reference@1.69.2\`. Fallback static route list is available if CDN fails.
- Before adopting as the main site, export and review the generated spec from the **same code version**, compare methods/schemas/responses and replace \`openapi.source-draft.json\` with a verified artifact. Keep the browser-side request test disabled until safe backend access exists.

## Promotion
After review, move \`preview/\` to the GitHub Pages root in a separate reviewed commit. Avoid overwriting the current root page until the group approves the visual design and source evidence.
