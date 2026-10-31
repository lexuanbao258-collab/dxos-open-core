# IT Helpdesk — Acceptance Test Plan

> **Status:** DRAFT — TEST DESIGN ONLY / NOT EXECUTED BY THIS DOCUMENT / LIVE_BLOCKED  
> **Owner:** Application — Ánh (to be confirmed in Document Register)  
> **Related:** Issue [#39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39), [#53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53), [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63), [PR #70](https://github.com/lexuanbao258-collab/dxos-open-core/pull/70), [PR #71](https://github.com/lexuanbao258-collab/dxos-open-core/pull/71).

## Goal, scope and evidence discipline

Define executable, reproducible acceptance scenarios for the initial IT Helpdesk Application consumer. Candidate unit/browser checks and fake transports **do not** prove a real Keycloak/BFF/Backend/PostgreSQL E2E journey.

For every eventual execution, capture exact Git commit SHAs for Portal, BFF and Backend, environment, test data, command, raw result or screenshot, operator, time, failures and independent reviewer decision. Mark cases **NOT RUN** until observed evidence exists.

## Positive scenarios

| ID | Steps / trigger | Expected result | Initial status |
| --- | --- | --- | --- |
| IT-AT-01 | Authenticated eligible requester creates valid IT request | Authorized Backend returns a request in `SUBMITTED`; Workflow version 0; user identity server-derived | NOT RUN (E2E) |
| IT-AT-02 | Authorized requester lists and opens own visible request | Only resource-visible records and authorized detail fields appear | NOT RUN (E2E) |
| IT-AT-03 | Coordinator triages at correct expected version | `SUBMITTED → TRIAGED`, version 1 and one successful transition history record | NOT RUN (E2E) |
| IT-AT-04 | Coordinator assigns eligible worker at assignment version 0 | Assignment changes once; Workflow version remains 1 | NOT RUN (E2E) |
| IT-AT-05 | Current eligible worker starts and resolves with a resolution | `IN_PROGRESS` then `RESOLVED`; versions 2, 3; resolution persisted | NOT RUN (E2E) |
| IT-AT-06 | Original authorized requester confirms resolution | `CONFIRMED`, Workflow version 4; no unsupported `CLOSED` claim | NOT RUN (E2E) |
| IT-AT-07 | Authorized user reads request history after restart | Committed history in correct order from private PostgreSQL, no invented actor notes | NOT RUN (E2E) |
| IT-AT-08 | Browser logs in, refreshes, expires and logs out through BFF | OIDC server session, CSRF and 401 logout behavior tested through actual HTTPS browser path | NOT RUN (E2E) |

## Negative and robustness scenarios

| ID | Trigger | Expected result | Initial status |
| --- | --- | --- | --- |
| IT-NEG-01 | No or invalid session | 401; no protected response or mutation | NOT RUN (E2E) |
| IT-NEG-02 | Authenticated user sees resource but lacks operation authority | 403; no writes | NOT RUN (E2E) |
| IT-NEG-03 | Hidden, nonexistent or cross-tenant resource ID | Indistinguishable 404; no IDOR data disclosure | NOT RUN (E2E) |
| IT-NEG-04 | Missing exact authority or forged role/header | Denied server-side; frontend role switch never grants authority | NOT RUN (E2E) |
| IT-NEG-05 | Stale Workflow version | 409; original state/history preserved | NOT RUN (E2E) |
| IT-NEG-06 | Stale independent assignment version | 409; no unauthorized assignment overwrite | NOT RUN (E2E) |
| IT-NEG-07 | Previous worker acts after reassignment | Worker-only mutation denied regardless of stale UI | NOT RUN (E2E) |
| IT-NEG-08 | Empty title, description or resolution | Validation rejects; no successful business/Workflow commit | NOT RUN (E2E) |
| IT-NEG-09 | Unsupported `CLOSED`, reject, reopen or wrong-state transition | Operation not exposed or rejected; no state/history mutation | NOT RUN (E2E) |
| IT-NEG-10 | CSRF token absent/invalid for protected BFF write/logout | Write blocked; browser never stores bearer/refresh tokens | NOT RUN (E2E) |
| IT-NEG-11 | Network failure during write or uncertain commit | No blind retry or false success; authorized reconciliation required | NOT RUN (E2E) |
| IT-NEG-12 | Concurrent resolution/confirmation | At most one permitted result per expected version; atomic business facts plus history | NOT RUN (E2E) |
| IT-NEG-13 | Client requests unauthorized fields or history events | Backend redacts/rejects according to an approved field-level visibility policy | POLICY OPEN |
| IT-NEG-14 | UI at narrow viewport, 200% zoom, keyboard-only flow | No critical reflow/focus/validation loss; record browser and viewport evidence | NOT RUN (E2E) |

## Source-specific evidence that must not be overstated

The author-reported PR #71 snapshot describes **356/356 unit tests and 7/7 Chromium tests** using fake or MOCK paths. This is **author-reported candidate evidence**, not independent LIVE E2E acceptance. A later Issue #39 responsive UI handoff reports a separate local snapshot (365/365 unit, 26/26 browser); do not mix these numbers or imply that uncommitted work passed at the PR head.

## Exit criteria for independent acceptance

- Business/API contract and responsible reviewers ratified; open wrong-scope/confirmation policies resolved or explicitly excluded.
- Tests against correct HEAD and real Keycloak/HTTPS/BFF/Backend/PostgreSQL17 environment reproduced.
- Exact-actor negative authorization, CSRF, version conflicts, unknown writes and restart durability demonstrated.
- Raw evidence and independent reviewer decision recorded on the matching Issues/PRs.
- Facility's second-definition reuse proof, MinIO runtime proof and full-stack release have separate gates.

**Overall disposition: TEST PLAN DRAFT. No cases in this file are claimed PASS by this document.**
