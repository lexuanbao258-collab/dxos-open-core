<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# ADR-008 - MVP analytics dashboard provider

**Status:** ACCEPTED (architecture decision; PR #126 merge and R21 checklist acceptance pending)
**Decision date:** 2026-11-05
**Tracking:** R21; implementation planned under R54
**Scope note:** R21 here is the dashboard decision; the compose work previously labelled "R21" (#119, PR #123) is R32a.

## Context and decision

OpenTrix requires an analytics dashboard for operational reporting across applications built on the shared DX-OS Core. The dashboard should support ticket backlog, workflow duration and technician workload without introducing Helpdesk-specific dependencies into Core.

The team selects **Apache Superset (Apache-2.0)** instead of Metabase OSS as the analytics dashboard provider. Superset better aligns with the project's Apache-2.0 licensing preference and planned requirements for embedded dashboards and tenant-aware reporting.

Superset will run as an independent service in Docker Compose, with its own metadata database. It will access PostgreSQL reporting views using a dedicated read-only database role. Core remains the authoritative source for workflow state and transition events; the dashboard does not own or modify business state.

The planned Portal integration uses embedded dashboards and backend-issued guest tokens. Keycloak/OIDC integration and tenant-specific row-level security require separate implementation and verification. Tenant identity must come from a trusted authenticated context, not an unverified client parameter.

Metabase OSS was considered because it is simpler to deploy initially and offers free guest embedding. However, JWT SSO, full-app embedding, and row and column security (formerly data sandboxing) have edition-dependent limits associated with Metabase Pro/Enterprise. Apache Superset's Apache-2.0 license aligns with the project's licensing direction, while its planned guest-token embedding and row-level security can be integrated with DX-OS's trusted, tenant-aware backend and read-only reporting views. This is an architectural fit, not a claim that Superset has been deployed or passed security testing.

## Consequences and acceptance

The decision supports a reusable analytics capability without coupling Core to dashboard technology. Superset introduces additional runtime services, resource requirements and authentication configuration.

Implementation must preserve database least privilege and tenant isolation. Reporting access must not expose unrestricted Core tables, application data or cross-tenant records. Embedded guest-token issuance must be authorized and tested.

R21 acceptance requires this ADR to be reviewed by Bao and Anh, merged into the repository, and linked from the project checklist. R23 must record Superset's license and distribution implications. R54 owns actual deployment, reporting views, Portal embedding, tenant-isolation tests and dashboard verification.

This architecture decision does **not** assert that Superset integration, SSO, embedding or runtime deployment has already passed testing.

## References

- [Apache Superset official documentation](https://superset.apache.org/docs/intro/)
- [Metabase embedding documentation](https://www.metabase.com/docs/latest/embedding/introduction)
- [Metabase SSO and authentication documentation](https://www.metabase.com/docs/latest/people-and-groups/authenticating-with-sso)
- [Metabase row and column security documentation](https://www.metabase.com/docs/latest/permissions/data-sandboxes)
- [GitHub Issue #119](https://github.com/tricore-opentrix/dxos-open-core/issues/119)
- [GitHub PR #123](https://github.com/tricore-opentrix/dxos-open-core/pull/123)
