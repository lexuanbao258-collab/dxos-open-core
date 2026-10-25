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
| Backend IT Helpdesk and focused B06 test candidate | [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) | Draft / unmerged; latest candidate HEAD at this checkpoint `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`; the new B06 test has **not** been independently executed |
| Licensing / source attribution / SPDX | [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38) | Open; not release-level accepted |

The independent **2 Configuration + 28 HTTP/PostgreSQL** focused tests reported on [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6387996126) apply to earlier HEAD `a3bf42ef4a01db5d01c193e7378cdb6e6c2785df`, **not** to the newer APP-11/B06 candidate. Separately reported 6 OIDC/Gateway tests do not constitute the missing real Keycloak-to-all-Helpdesk-routes-to-PostgreSQL evidence.

**No complete LIVE acceptance or released end-to-end Portal is claimed here.** Add a tagged release section only when its commit, integration evidence, migration policy, security sign-off and license status are actually verified.
