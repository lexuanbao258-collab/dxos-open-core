# Facility Maintenance — Acceptance Test Plan

> **Status:** DRAFT / TEST DESIGN ONLY / NOT EXECUTED / LIVE_BLOCKED
> **Owner:** Application (Ánh); independent reviewer to confirm
> **References:** Issue #27, #39, #53; PR #60, PR #76, PR #78

## Scope and evidence rule

The Facility Maintenance full business journey is **DEFERRED** from initial IT Helpdesk LIVE scope. These tests are proposals that must be reconciled to approved rules before running. A Portal MOCK demonstration or second definition candidate is not a validated full Facility E2E.

All rows start `NOT RUN` or `POLICY OPEN`; never convert a row to PASS without exact commit SHA, environment, command, raw evidence and independent reviewer disposition.

## Proposed positive scenarios

| ID | Scenario | Intended acceptance condition | Status |
| --- | --- | --- | --- |
| FAC-AT-01 | Submit valid scoped request | Verified requester; authorized creation and durable business/Workflow binding once contract exists | NOT RUN |
| FAC-AT-02 | Review and accept valid intake | Authorized coordinator, approved acceptance rule and version-safe state/history | POLICY OPEN |
| FAC-AT-03 | Assign eligible technician | Current eligibility, proper ownership and concurrency; resolved assignment semantics | POLICY OPEN |
| FAC-AT-04 | Record repair start and result | Only current authorized assignee; validated facts; no invented success | POLICY OPEN |
| FAC-AT-05 | Review outcome and optionally close | Approved Acceptance Actor, reject/rework and separation of acceptance from closure | POLICY OPEN |
| FAC-AT-06 | Read history across restart | Only authorized transition records persisted in PostgreSQL; actor/field redaction | NOT RUN |
| FAC-AT-07 | Independent Core reuse proof | Second genuinely distinct definition uses unchanged Workflow Core with real authorized Backend persistence | NOT RUN |

## Proposed negative and failure scenarios

| ID | Trigger | Expected behavior | Status |
| --- | --- | --- | --- |
| FAC-NEG-01 | No session or forged identity | Reject authentication and prevent protected writes | NOT RUN |
| FAC-NEG-02 | Wrong tenant or hidden ID | Return protected 404; no resource existence disclosure | NOT RUN |
| FAC-NEG-03 | Visible request with missing operation authority | Deny 403 with no mutation | NOT RUN |
| FAC-NEG-04 | Stale expected Workflow version | 409 conflict and unchanged history | NOT RUN |
| FAC-NEG-05 | Removed or unqualified technician attempts write | Reject server-side irrespective of UI button visibility | NOT RUN |
| FAC-NEG-06 | Wrong-scope request or missing location | Validation policy to be approved; do not accept silently | POLICY OPEN |
| FAC-NEG-07 | Failed repair or rejection of acceptance | Only documented outcomes; no invented terminal state | POLICY OPEN |
| FAC-NEG-08 | Uncertain SQL/storage write | No blind retry, false success or orphan evidence reference | NOT RUN |
| FAC-NEG-09 | Unauthorized access to location/notes/evidence | Backend enforces final field-level visibility | POLICY OPEN |
| FAC-NEG-10 | Browser logout/session expiry/invalid CSRF | Safe BFF state change and protected request denial | NOT RUN |

## Exit criteria

1. Issue #53 resolves acceptance, assignment, closure, wrong-scope and rework policies or excludes them explicitly.
2. Backend/Portal/BFF integration provides reviewed HTTP paths, exact authorization and version contracts.
3. Separate runtime demonstration validates actual unchanged Workflow Core reuse for a second definition.
4. Raw traces, deterministic fixture data, negative checks and independent approval tie to exact code revision.
5. Deployment/release claims remain blocked until separately accepted.

**Overall disposition:** DRAFT / NOT RUN; no Facility runtime approval.
