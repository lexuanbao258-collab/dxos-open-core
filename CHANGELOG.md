<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Changelog

User-visible DX-OS Open Core changes are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- R24 tag-triggered release-candidate packaging (Java jars, frontend, SBOM/NOTICE inventory, SHA256SUMS), with independent distribution-acceptance gate before GitHub Release/GHCR publication.

### Changed

- R26: Prepare OpenTrix canonical repository identity and update organization links, documentation and setup defaults after the existing repository rename; independent post-cutover acceptance remains pending.

## [0.2.0-rc.1] - 2026-11-05

### Added

- R24 source and binary candidate packaging, archive checksums and versioned release-note generation.
- A fail-closed independent release acceptance gate; candidate builds do not imply redistribution clearance.

### Known verification limitations

- This is a release-candidate documentation baseline, not a claim of production integration, R12 browser acceptance or permission to publish binary artifacts.

## [0.1.0] - 2026-11-04

### Added

- IT Helpdesk Backend HTTP API (nine application routes) with tenant/resource-scoped authorization, PostgreSQL 17 persistence and audited workflow transitions.
- Portal BFF with OIDC Authorization Code + PKCE, server-side browser session, and CSRF protection.
- React IT Helpdesk Portal: standalone MOCK demonstration and `integration-local` integration build mode; live browser/SSO acceptance remains a separate gate.
- Apache-2.0 root `LICENSE` and `NOTICE` files for original DX-OS work, subject to ongoing third-party/SPDX verification tracked by Issue #38.
- ArchUnit Core dependency rules and negative canary tests.
- Contribution guidelines, issue and PR templates, and CODEOWNERS.
- Shared CI for PRs to `main`: backend build and tests, applicable application
  builds, repository checks, and Identity–Gateway security regressions.

### Changed

- `backend/gradlew` is executable on Linux and macOS.
- The Helpdesk HTTP API uses a scoped HTTP 404 / `NOT_FOUND` response for
  hidden or missing resources; the Generic Workflow HTTP API retains its
  pre-existing distinct `DEFINITION_NOT_FOUND` / `INSTANCE_NOT_FOUND` public
  codes and messages. Generic contract evolution is tracked in Issue #105.

### Fixed

- Linux backend build and tests: restored three SQL evidence fixtures, pinned the
  manifest hash to Git LF bytes, and limited a PowerShell-specific test to Windows.
- Helpdesk CREATE rejects missing exact technical authorities before protected
  definition/binding lookups; uncertain Workflow commits return HTTP 503 with
  `COMMIT_OUTCOME_UNKNOWN` and must not be retried automatically.

### Removed

- R25: Remove the legacy `com.tricore.dxos.request` prototype (controllers, domain, DTOs, repositories, services and tests) and retire `/api/v1/requests/*` routes and their `CLOSED` lifecycle. Preserve historical Flyway V1/V2 migration bytes; Helpdesk remains available at `/api/v1/mvp/requests`.

- The separate Issue #23 workflow; its security gates run in shared CI.

[0.1.0]: https://github.com/tricore-opentrix/opentrix/releases/tag/v0.1.0
