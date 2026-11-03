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

### Fixed

- Linux backend build and tests: restored three SQL evidence fixtures, pinned the
  manifest hash to Git LF bytes, and limited a PowerShell-specific test to Windows.

### Removed

- The separate Issue #23 workflow; its security gates run in shared CI.
