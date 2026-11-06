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

Separately, [independent PR #98 security evidence](../business-contract-review.md#separate-independent-evidence--pr-98-phase-2a) at `25f73d10b51556b446af12e636b9e19cd53ad030` records 331/331 focused PASS and 687/687 single-worker PASS; the initial default-parallel two timeouts remain unresolved. This is Phase 2A frontend source/test evidence only; Phase 2B/LIVE remain BLOCKED and the acceptance designs below remain NOT RUN.

## Additional contract cases for independent execution

| ID | Scenario and required assertion | Status |
| --- | --- | --- |
| IT-CON-01 | System Administrator without separately delegated grants/eligibility cannot list/read/history/assign/confirm; multi-role actor still satisfies each predicate independently | NOT RUN |
| IT-CON-02 | Create 201 + Location; other eight routes 200 on permitted success; exact DTO shapes, nulls/omissions, unknown fields and non-integer versions; no extra mock fields | NOT RUN |
| IT-CON-03 | Unauthorized worker/requester cannot receive eligible directory; authorized coordinator sees detail-only options in TRIAGED; no invented names or audit events | POLICY OPEN / NOT RUN |
| IT-CON-04 | Two commands triage then assign: failed assignment does not fabricate rollback of committed triage; reassignment in IN_PROGRESS denied | NOT RUN |
| IT-CON-05 | Committed, uncommitted and still-ambiguous outcomes; assignment has no transition receipt; lost create without ID; no mutation replay and no false result attribution | BD-05 OPEN / NOT RUN |
| IT-CON-06 | Generic missing codes/messages preserved; Helpdesk hidden/missing parity on detail/history and five resource POSTs including JSON shape/content type/bytes and unchanged facts/state/history | CONTRACT CONDITIONAL / NOT RUN — local-isolated acceptance design |
| IT-CON-07 | Flag absent/false/true route inventory in isolated local scope; legacy consumer compatibility and no alternate-namespace bypass | OWNER CONFIRMATION PENDING / NOT RUN — local-isolated acceptance design |
| IT-CON-08 | Exact read/transition authority, tenant/ACL/assignment revoke races, concurrent same-version Workflow actions, policy unavailable/timeout and rollback | NOT RUN |
| HD01-P1 | Negative test design: submit CREATE with otherwise valid prerequisites, missing exact client-scoped `workflow:create`, and a hidden definition binding. Require rejection before any protected definition/binding lookup; capture authorization/lookup ordering and assert zero protected lookup calls and zero unauthorized business, grant, Workflow state/version or history writes using before/after persistence snapshots. | NOT RUN / BLOCKED |

The [API contract](api-contract.md) records #95's source pin and the latest conditional SEC-97-01 operation decisions; current #97 implementation is NOT ACCEPTED. IT-CON-06/07 Generic/flag coverage is **NOT RUN local-isolated acceptance design** for the disposable candidate only, with no shared/staging/production exposure authorization. These cases are test designs, not execution claims. Existing local counts in PR #71/#95/#97 descriptions are not imported as official test results; source assertions, reported execution and independent acceptance must stay separate. Historical evidence remains at its original source; see [A02 reconciliation](../business-contract-review.md).

## Exit criteria for independent acceptance

- Business/API contract and responsible reviewers ratified; open wrong-scope/confirmation policies resolved or explicitly excluded.
- Tests against correct HEAD and real Keycloak/HTTPS/BFF/Backend/PostgreSQL17 environment reproduced.
- Exact-actor negative authorization, CSRF, version conflicts, unknown writes and restart durability demonstrated.
- Raw evidence and independent reviewer decision recorded on the matching Issues/PRs.
- Facility's second-definition reuse proof, MinIO runtime proof and full-stack release have separate gates.

**Overall disposition: TEST PLAN DRAFT. No cases in this file are claimed PASS by this document.**
