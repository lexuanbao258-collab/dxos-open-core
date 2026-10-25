# Security Policy — DX-OS Open-Core

This repository contains security-sensitive Identity, Gateway, Workflow, and PostgreSQL authorization code. Development tests, mock Portal demonstrations and draft pull requests are **not** statements of production security readiness.

## Reporting a vulnerability

**Do not disclose an unpatched vulnerability publicly** in an Issue, PR, screenshot or discussion thread. First contact the repository maintainers privately using the repository's available private security reporting or maintainer contact channel; check the GitHub repository Security tab for an enabled private vulnerability report option. If private reporting is not enabled, request a private maintainer contact route **without posting exploit details publicly**.

Include a concise description, affected component/commit, impact, minimal safe reproduction steps, and redacted evidence. Do not send credentials, bearer tokens, personal data, or production database contents. Maintainers should coordinate with the relevant Identity/Security reviewer before any public disclosure.

## Supported status

This project is undergoing development for OLP 2026. No published production-supported versions, patch SLA, or general-availability security guarantee are asserted by this document. Scope and verification status belong to the exact commit, environment, and review evidence.

- Core unit and HTTP test coverage does not substitute for real OIDC/provider and database privilege acceptance.
- [Draft PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) received an [independent B06 focused real-Keycloak/Helpdesk HTTP/PostgreSQL **PASS** report](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935) for exact HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51` (33 tests, no failures/skips). The limited focused evidence **does not** establish approved production privileges, controlled provisioning, Portal end-to-end integration, or release security acceptance. **LIVE remains BLOCKED** pending those separate gates.
- Approved SQL provisioning and runtime privileges are separate from source-only tests. See [Draft PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58).
- The Portal in [Draft PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51) is a development/mock demonstration, not an authenticated production integration.

## Responsible testing

Use explicitly approved isolated disposable test infrastructure and synthetic identities. Do not test against someone else's system or any shared/staging/production resource without authorization. Preserve tenant/resource privacy, verified identity boundaries, fail-closed errors and unknown-commit behavior.

For ordinary non-sensitive bugs or enhancement proposals, use GitHub Issues and [CONTRIBUTING.md](CONTRIBUTING.md).
