# IT Helpdesk — Business Rules

**Status:** DRAFT — PROPOSED / NOT APPROVED<br>
**Owner:** Application Team — Ánh<br>
**Project:** DX-OS Open-Core<br>
**Related:** Issue #23, Issue #39, Issue #53, PR #60, PR #63, PR #71

## 1. Purpose

This document defines the proposed business rules for IT Helpdesk within the DX-OS Internal Service Portal.

The rules distinguish Application-specific business behavior from reusable Workflow Core capabilities.

This document does not authorize production endpoints, approve the final security policy, or claim LIVE end-to-end acceptance.

## 2. Business Roles

| Role | Responsibilities |
|---|---|
| Requester | Submit requests, track progress, confirm results |
| IT Coordinator | Triage requests and assign eligible technicians |
| Assigned Technician | Start work and record resolution |
| System Administrator | Manage system configuration under separately approved policies |

A business role does not automatically grant permission to access or modify a request.

## 3. Request Creation Rules

**BR-IT-01 — Valid Request Information**

An IT request must contain valid information, including a nonblank title and description.

**BR-IT-02 — Authorized Creation**

Only a verified and eligible requester with the required Backend permissions may create a LIVE request.

**BR-IT-03 — Server-Controlled Identity**

The requester identity must come from the verified Backend authentication context, not from a user-supplied name, role or header.

**BR-IT-04 — Initial Workflow State**

The proposed Backend candidate creates and activates the request in the `SUBMITTED` state.

The Application must not directly modify Workflow state to simulate a successful transition.

## 4. Workflow Rules

The first IT Helpdesk MVP candidate uses:

`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`

| Rule | Business condition |
|---|---|
| BR-IT-05 | An eligible coordinator may triage a submitted request |
| BR-IT-06 | A coordinator may assign an eligible technician using a separate Application command |
| BR-IT-07 | Only the currently eligible assigned technician may start work |
| BR-IT-08 | The assigned technician must provide a valid resolution before completing technical work |
| BR-IT-09 | Only the authorized original requester may confirm the reported resolution |
| BR-IT-10 | An unsupported transition must be rejected without changing the Workflow state |

**Important:** `triage` is a Workflow transition, while `assign` is an Application command with its own assignment version.

The current candidate ends at `CONFIRMED`. No production `CLOSED` operation is included in this first MVP candidate.

## 5. Authorization Rules

**BR-IT-11 — Verified Identity**

The Backend must verify the user's identity before accessing protected request resources.

**BR-IT-12 — Resource-Level Authorization**

Every protected operation must check the appropriate authority and resource/action permissions.

Showing or hiding a button in the frontend is not sufficient authorization.

**BR-IT-13 — Request Visibility**

Users must only receive request information authorized for their identity and resource scope.

The final event-level and field-level visibility policy remains subject to review.

**BR-IT-14 — Denied Access**

Expected security behavior includes:

- `401` for missing or invalid authentication.
- `403` for an authorized-to-see resource where the requested operation is forbidden.
- `404` for hidden, cross-tenant or nonexistent resources, without revealing protected existence.

These conditions must be validated by Backend security tests, not inferred from mock UI behavior.

**BR-IT-15 — Assignment Revocation**

When assignment changes, the previous technician must not retain technician-only operational eligibility solely because of a former assignment.

## 6. Data and Consistency Rules

**BR-IT-16 — Workflow Ownership**

Workflow Core owns authoritative workflow state, instance version and successful transition history.

**BR-IT-17 — Application Ownership**

Application owns IT-specific business information, including request details, assignment and resolution facts.

**BR-IT-18 — Concurrency Control**

Workflow transitions must use the expected Workflow version.

Assignment updates use their separate Application assignment version.

**BR-IT-19 — Duplicate Operations**

The Application should prevent accidental duplicate submissions. The Backend must enforce correctness independently.

**BR-IT-20 — Unknown Commit Outcome**

If a write result is unknown, the system must not display false success or automatically repeat the operation without an approved reconciliation procedure.

**BR-IT-21 — Transaction Consistency**

Business facts and Workflow operations must respect the reviewed transaction boundary. Application must not claim successful atomicity without corresponding implementation and verification evidence.

## 7. Error Handling Rules

| Scenario | Expected behavior |
|---|---|
| Invalid input | Reject the operation with an understandable validation response |
| Unauthorized request | Deny access without exposing protected data |
| Stale version | Reject the conflicting update |
| Missing assignment | Prevent unauthorized technician operations |
| Duplicate submission | Prevent unintended repeated writes |
| Unknown write outcome | Require safe reconciliation; do not blindly retry |
| Unsupported operation | Reject safely without fabricating a transition |

## 8. Open Business Decisions

The following items remain unresolved or outside the currently approved integration preparation scope:

- Wrong-scope rejection and mandatory reasons.
- Requester disagreement with a resolution.
- Rework after an unsatisfactory result.
- Unresolved incidents and escalation handling.
- A future authorized `CLOSED` operation.
- Final role eligibility and resource/field visibility policies.

These are not silently approved or implemented by the existence of the Backend candidate.

Bảo owns final business-scope decisions, with Application and Identity/Security review where applicable.

## 9. MOCK and LIVE Boundary

The current Portal uses `MockRequestService` and synthetic records for UI demonstrations.

Mock roles, transitions, request histories and permission messages demonstrate intended user experience only.

The Backend candidate in PR #63 and the Portal integration candidate in PR #71 require their respective integration and security acceptance gates before LIVE use.

No mock action should be described as proof of real authentication, authorization or persisted Workflow execution.

## 10. Review and Acceptance Criteria

This document is ready for review when:

1. Business actors and responsibilities are clearly separated.
2. Workflow transitions and Application assignment
