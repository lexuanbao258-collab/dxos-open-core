# IT Helpdesk — Application Use Cases

**Status:** DRAFT — PROPOSED / NOT APPROVED  
**Owner:** Application Team — Ánh  
**Related:** Issue #39, Issue #53, PR #60, PR #63, PR #71

## 1. Purpose

This document describes the proposed IT Helpdesk user journeys for the DX-OS Internal Service Portal.

The application reuses the generic Workflow Core while keeping IT-specific rules, assignment, resolution information, and user experience within the Application boundary.

The current Portal demonstration uses mock data. The Backend integration candidate is not approved for production LIVE usage.

## 2. Actors

| Actor | Responsibility |
|---|---|
| Requester | Submits an IT issue, tracks progress, confirms resolution |
| IT Coordinator | Triages requests and assigns eligible IT technicians |
| Assigned Technician | Starts work and records the resolution |
| Identity/Gateway | Verifies authentication and enforces applicable access boundaries |
| Workflow Core | Maintains workflow state, version and transition history |

All business actions additionally require verified identity and server-side resource/action authorization.

## 3. UC-IT-01 — Submit an IT Support Request

**Primary actor:** Requester

**Preconditions:**

- A valid authenticated session is established for LIVE integration.
- The requester is eligible to create requests.
- The required create authority and resource authorization are satisfied.

**Main flow:**

1. The requester opens the Internal Service Portal.
2. The requester selects IT Helpdesk.
3. The requester enters the issue information.
4. The Application validates the request.
5. An authorized Backend Application operation creates and activates the request workflow.
6. The Portal displays the submitted request and its current status.

**Expected result:** A request exists in the `SUBMITTED` state in the Backend candidate.

**Exceptions:**

- Invalid input: reject submission without creating a request.
- Unauthorized creation: reject safely without disclosing protected information.
- Unknown write outcome: do not automatically retry or display false success.

## 4. UC-IT-02 — Triage and Assign

**Primary actor:** IT Coordinator

**Main flow:**

1. The coordinator opens an authorized request.
2. The coordinator reviews the submitted issue.
3. The coordinator performs the `triage` Workflow transition.
4. The request moves from `SUBMITTED` to `TRIAGED`.
5. The coordinator assigns an eligible IT technician using a separate Application command.
6. The Application records the assignment and its version.

**Expected result:** The request is triaged and assigned to an eligible technician.

**Important boundary:** `triage` is a Workflow transition. `assign` is a separate Application operation and must not be presented as another Workflow transition.

Wrong-scope rejection remains a proposed business requirement without an approved implementation in this first candidate.

## 5. UC-IT-03 — Perform Technical Work

**Primary actor:** Assigned Technician

**Main flow:**

1. The assigned technician opens an authorized request.
2. The technician selects the start-work action.
3. The Backend validates current assignment, authority, permissions and expected Workflow version.
4. Workflow advances from `TRIAGED` to `IN_PROGRESS`.
5. The technician performs the required technical work.
6. The technician records a nonblank resolution.
7. Workflow advances from `IN_PROGRESS` to `RESOLVED`.

**Expected result:** The resolution is recorded and the request awaits requester confirmation.

**Exceptions:**

- A revoked or replaced technician cannot continue using former assignee eligibility.
- Invalid or stale versions are rejected without silently overwriting current state.
- An unresolved incident must not be reported as successfully resolved.

## 6. UC-IT-04 — Confirm Resolution

**Primary actor:** Original Requester

**Main flow:**

1. The original requester opens the resolved request.
2. The requester reviews the reported outcome.
3. The requester selects the confirmation action.
4. The Backend verifies identity, requester eligibility, authorization and version.
5. Workflow advances from `RESOLVED` to `CONFIRMED`.

**Expected result:** The candidate IT workflow reaches `CONFIRMED`.

`CONFIRMED` is the endpoint of the first MVP candidate. It must not be represented as the production `CLOSED` state.

Disagreement, rework and a future closure action remain subject to separate business decisions.

## 7. Proposed IT Workflow Summary

`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`

- `triage`: Workflow transition.
- `assign`: Separate Application command.
- `start_work`: Workflow transition.
- `resolve`: Workflow transition.
- `confirm`: Workflow transition performed by the authorized original requester.

Workflow Core owns the authoritative state and transition history. Application owns the IT request information, assignment and resolution facts.

## 8. Negative and Exceptional Scenarios

The acceptance plan must cover:

- Missing or invalid authentication.
- Unauthorized or cross-tenant access.
- Invalid transitions.
- Stale `expectedVersion` values.
- Duplicate user actions.
- Assignment revocation or replacement.
- Missing resolution information.
- Unknown commit outcome without blind retry.
- Unsupported rejection, rework or closure actions.

These cases require separate Backend/security evidence. Portal mock behavior alone is not proof of LIVE authorization.

## 9. Acceptance Criteria

The use-case specification is ready for review when:

1. Requester, coordinator and technician responsibilities are distinct.
2. The main IT flow is consistent with the current candidate.
3. Assignment is separate from Workflow transitions.
4. Authorization and concurrency constraints are identified.
5. Unsupported alternate paths are explicitly marked OPEN or BLOCKED.
6. No production LIVE acceptance is claimed without independent evidence.

## 10. References

- Issue #39 — Internal Service Portal
- Issue #53 — Business Rules and API Contract
- PR #60 — Draft IT Helpdesk and Facility Contract
- PR #63 — Helpdesk Backend Candidate
- PR #71 — Portal Integration Candidate

**Document status: DRAFT — REVIEW REQUIRED.**
