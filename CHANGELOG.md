# Changelog

Important **verified** changes to DX-OS Open-Core will be recorded here. This file distinguishes changes merged into `main` from pending pull requests. It does not invent release numbers, merge dates, deployment approvals, or test results.

Format inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Version and release dates will be added only after an actual tag/release is approved.

## [Unreleased] — documentation candidate

### Documentation

- Propose a repository-level contribution guide, changelog, security reporting guidance, installation notes, and an IT Helpdesk demo/evidence guide in a documentation-only PR.
- Cross-link existing backend, architecture, contracts, glossary and evidence documentation; distinguish the currently merged backend baseline from draft frontend/backend integration candidates.

### Integration work tracked separately — **not released**

| Workstream | Reference | Status as checked on 2026-10-25 |
| --- | --- | --- |
| React Service Portal (mock/development) | [PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51) | Draft / unmerged; UI demo does **not** prove HTTP end-to-end |
| Guarded PostgreSQL SQL dependency | [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) | Draft / unmerged; requires reviewed privilege/provisioning gates |
| Backend IT Helpdesk and focused B06 test candidate | [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) | **B06 focused PASS independently reported** at `e12d105c89400a5fd69dd6cc1fca670ff1f04f51` (2 + 31 = **33 passed**, 0 failed/skipped); **Draft / unmerged / LIVE_BLOCKED** |
| Licensing / source attribution / SPDX | [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38) | Open; not release-level accepted |

**Test evidence, not a release entry:** [Yến's independent B06 report](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935) confirms 33 focused tests (2 Configuration + 31 HTTP/PostgreSQL) passed with real Keycloak 26.8.0 + PKCE, Java 21.0.12.1, Gradle 8.14.3 and disposable PostgreSQL 18.6 at HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`, with cleanup reported. The older [B05 focused 2 + 28 PASS report](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6387996126) remains specific to HEAD `a3bf42e`. **Open gates:** [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) SQL/runtime privileges, approved tenant/identity provisioning, and Portal HTTP integration. Do not mark full E2E integration, production readiness or license compliance as complete.

**No complete LIVE acceptance or released end-to-end Portal is claimed here.** Add a tagged release section only when its commit, integration evidence, migration policy, security sign-off and license status are actually verified.
