# IT Helpdesk — Application Requirements

**Status:** DRAFT — PROPOSED / NOT APPROVED  
**Owner:** Application Team — Ánh  
**Project:** DX-OS Open-Core  
**Related:** Issue #39, Issue #53, PR #60, PR #63, PR #71

## 1. Purpose

The IT Helpdesk application enables employees to submit internal IT support requests, track their progress, and confirm the results of technical support.

IT coordinators and assigned technicians can receive, assign, process, and resolve requests within an authorized workflow.

The application is designed to reuse the generic DX-OS Workflow Core without introducing IT-specific business rules into Core modules.

## 2. Scope

The first MVP prioritizes the IT Helpdesk workflow.

In scope:

- Submit an IT support request.
- View authorized requests and their details.
- Track request status and history.
- Triage incoming requests.
- Assign an eligible IT technician.
- Start work and record a resolution.
- Allow the original requester to confirm the result.
- Handle validation, access-denied, conflict, and uncertain-write scenarios safely.

The initial integration candidate stops at `CONFIRMED`. Production `CLOSED` behavior is not included in this candidate.

## 3. User Roles

| Role | Responsibility |
|---|---|
| Requester | Submits requests, follows progress, confirms results |
| IT Coordinator | Reviews requests, performs triage, assigns technicians |
| Assigned Technician | Performs technical work and records resolution |
| System Administrator | Manages infrastructure and configuration under separately approved policies |

Business roles do not automatically grant API permissions. Backend access requires verified identity, appropriate authority, and resource-level authorization.

## 4. Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-01 | The requester can submit a valid IT support request | Candidate |
| FR-02 | Users can view only requests they are authorized to access | Required security boundary |
| FR-03 | Users can view authorized request details and history | Candidate |
| FR-04 | An eligible coordinator can triage a submitted request | Candidate |
| FR-05 | An eligible coordinator can assign an IT technician | Candidate |
| FR-06 | The currently assigned technician can start work | Candidate |
| FR-07 | The assigned technician can record a resolution | Candidate |
| FR-08 | The original requester can confirm the resolution when authorized | Candidate |
| FR-09 | Unsupported transitions must be rejected without changing the workflow state | Required |
| FR-10 | The interface must handle errors without displaying false success | Required |

These requirements describe the target application contract, not a claim that every feature has passed LIVE end-to-end testing.

## 5. Proposed Workflow

The IT Helpdesk integration candidate follows:

`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`

The assignment operation is an Application command separate from the Workflow `triage` transition.

The Workflow Core owns authoritative workflow state, version, and transition history.

The Application owns request information, business validation, assignment, and resolution facts.

`CONFIRMED` is the endpoint for the first candidate MVP; it must not be represented as production `CLOSED`.

## 6. Validation and Security Requirements

- Mandatory request information must be validated before submission.
- Identity must be established using a verified authentication mechanism.
- Authorization must be enforced by the Backend, not solely by hidden UI buttons.
- Unauthorized access to hidden or cross-tenant resources must not expose protected information.
- Workflow transitions must use the expected version to detect concurrent changes.
- Duplicate submissions and unknown commit outcomes must not trigger unsafe automatic retries.
- Access to request history must respect approved field and event visibility policies.
- Authentication credentials, administrative keys, and service secrets must not be stored in frontend source code.

## 7. Current Implementation Boundary

**Portal UI:** The current demonstration uses `MockRequestService` and synthetic data. It is not evidence of real authentication or Backend authorization.

**Backend:** PR #63 contains a Helpdesk integration candidate. Its existence and focused test evidence do not constitute approval for production LIVE use.

**Identity/BFF:** Real browser SSO, session integration, and relevant security gates remain subject to verification.

**Facility Maintenance:** The complete Facility workflow is deferred from the first IT-focused MVP; its reuse design and outstanding requirements are documented separately.

## 8. Open Business Decisions

The following require explicit Owner, Application, and Security review:

- Wrong-scope request rejection and mandatory rejection reasons.
- Requester disagreement with a resolution and potential rework.
- Unresolved incidents and escalation handling.
- Any future `CLOSED` action after `CONFIRMED`.
- Final actor eligibility, authorization, and visibility policies.
- Recovery behavior for uncertain writes and partial failures.

These items must not be silently treated as implemented or approved.

## 9. Acceptance Criteria

The Application documentation is ready for review when:

1. User roles and requirements are clearly defined.
2. Workflow and Application responsibilities are separated.
3. The proposed workflow matches the reviewed business candidate.
4. Mock, candidate, blocked, and approved capabilities are clearly distinguished.
5. Security and negative scenarios are explicitly identified.
6. Open decisions and dependencies are traceable to their sources.

Runtime acceptance requires separate testing on an identified source revision and approved integration environment.

## 10. References

- Issue #39 — Internal Service Portal
- Issue #53 — Business Rules & API Contract
- PR #60 — Draft MVP Business/API Contract
- PR #63 — Helpdesk Backend Candidate
- PR #71 — Portal Integration Candidate
- PR #73 — Documentation Restructuring

**Document status: DRAFT — REVIEW REQUIRED.**
