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

- Contribution guidelines, issue and PR templates, and CODEOWNERS.
- Shared CI for PRs to `main`: backend build and tests, applicable application
  builds, repository checks, and Identity–Gateway security regressions.

### Changed

- `backend/gradlew` is executable on Linux and macOS.
- The Generic Workflow HTTP API normalizes `DEFINITION_NOT_FOUND` and
  `INSTANCE_NOT_FOUND` into a public HTTP 404 / `NOT_FOUND` response to avoid
  differentiating hidden and missing resources. This provisional public contract
  will be formally reviewed in Issue #105 before Platform API publication.

### Fixed

- Linux backend build and tests: restored three SQL evidence fixtures, pinned the
  manifest hash to Git LF bytes, and limited a PowerShell-specific test to Windows.
- Helpdesk CREATE rejects missing exact technical authorities before protected
  definition/binding lookups; uncertain Workflow commits return HTTP 503 with
  `COMMIT_OUTCOME_UNKNOWN` and must not be retried automatically.

### Removed

- The separate Issue #23 workflow; its security gates run in shared CI.
