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

- Portal: OpenTrix Visual V7 restyle of the `/design-v621/*` preview (Workflow Rail, Overview with step distribution, Queue table/rows). MOCK/DEV only, no data or behaviour change.
- Portal: OpenTrix V6.2.1 design preview at `/design-v621/*` (React, MOCK/DEV only, lazy-loaded, isolated from the existing routes). Uses its own preview fixtures; no service, LIVE call or `allowedActions` is introduced. Self-hosts IBM Plex Mono (OFL-1.1) alongside Be Vietnam Pro.

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

- The separate Issue #23 workflow; its security gates run in shared CI.

[0.1.0]: https://github.com/lexuanbao258-collab/dxos-open-core/releases/tag/v0.1.0
