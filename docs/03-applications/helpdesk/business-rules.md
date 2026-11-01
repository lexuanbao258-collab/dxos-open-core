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
## 11. Helpdesk Business Action & Transition Matrix

**Status:** DRAFT / APPLICATION OWNER REVIEW / FINAL SECURITY APPROVAL PENDING  
**Owner:** Ánh — Application & Frontend  
**Related:** Issue #53, PR #74, PR #70, PR #71, PR #76

### 11.1. Candidate Workflow

`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`

- `assign` is an Application business command, not a Workflow transition.
- `CONFIRMED` is the terminal state of the current Helpdesk MVP candidate.
- `CLOSED`, rejection, reopen and rework remain outside the current approved implementation scope.

### 11.2. Actor – Action – Transition Matrix

Backend API base path: `/api/v1/mvp/requests`

| Actor | Action | Precondition | API | Workflow outcome |
|---|---|---|---|---|
| Requester | Create | Verified eligible requester; valid title and description; required create permissions | `POST /` | New `SUBMITTED` request, version 0 |
| Authorized user | List | Verified identity and resource visibility | `GET /` | No state change |
| Authorized user | View detail | Resource and tenant visibility | `GET /{id}` | No state change |
| IT Coordinator | Triage | `SUBMITTED`; coordinator eligibility; valid `expectedVersion` | `POST /{id}/triage` | `TRIAGED`; version +1 |
| IT Coordinator | Assign | `TRIAGED`; eligible technician; valid `expectedAssignmentVersion` | `POST /{id}/assign` | Assignment changes; Workflow state/version unchanged |
| Assigned Technician | Start work | `TRIAGED`; current eligible assignee; valid `expectedVersion` | `POST /{id}/start` | `IN_PROGRESS`; version +1 |
| Assigned Technician | Resolve | `IN_PROGRESS`; current eligible assignee; valid version; nonblank resolution | `POST /{id}/resolve` | `RESOLVED`; version +1 |
| Original Requester | Confirm | `RESOLVED`; requester eligibility; valid version | `POST /{id}/confirm` | `CONFIRMED`; version +1 |
| Authorized user | View history | Verified identity and approved history visibility | `GET /{id}/history` | No state change |

### 11.3. Authorization Requirements

For each protected operation, the Backend must verify:

1. A trusted authenticated principal.
2. The exact required operation authority.
3. Tenant and resource visibility.
4. Resource-level action permission.
5. Business eligibility, including current assignment when applicable.
6. The applicable Workflow or assignment version for mutations.

UI button visibility and demo roles do not grant authorization.

**OPEN:** Exact authority strings, final actor eligibility, field visibility and history-event disclosure require Yến's security review and Backend contract confirmation.

### 11.4. API Request Fields

| Operation | Required request fields |
|---|---|
| Create | `title`, `description`, `requestType: "IT_HELPDESK"` |
| Triage | `expectedVersion` |
| Assign | `assigneeId`, `expectedAssignmentVersion` |
| Start | `expectedVersion` |
| Resolve | `expectedVersion`, `resolution` |
| Confirm | `expectedVersion` |

The Backend derives requester identity. The browser must not supply trusted principal or authority information.

### 11.5. Error Contract and UI Feedback

| Condition | Expected Backend behavior | Required Portal feedback |
|---|---|---|
| Invalid data | Validation rejection, typically 400 | Show the field error; do not claim success |
| Missing/invalid authentication | 401 | Show session expiration or login requirement |
| Visible resource, forbidden action | 403 | Show a permission-denied message |
| Hidden, cross-tenant or nonexistent request | Protected 404 | Show a generic unavailable/not-found message |
| Stale Workflow or assignment version | 409 | Explain the conflict and request a fresh read |
| Network failure / unknown write outcome | Preserve uncertainty | Do not automatically retry the write or display false success |
| Unsupported transition | Reject with no successful mutation | Do not expose the operation as an available LIVE action |

### 11.6. Workflow and History Invariants

- Workflow Core owns authoritative state, version and committed transition history.
- Application owns Helpdesk business information, assignment and resolution.
- Assignment increments the independent assignment version, not the Workflow version.
- The uninterrupted candidate path reaches Workflow version 4 after four transitions.
- Only successful transitions produce their corresponding successful Workflow history records.
- Assignment must not be fabricated as a fifth Workflow transition.
- A denied or conflicting operation must not create successful transition history.
- `allowedActions` is computed by the Backend and must be revalidated when an action is executed.

### 11.7. OPEN Business Decisions

The following require explicit review before extending the MVP:

1. Wrong-scope rejection and reasons.
2. Requester disagreement and rework.
3. Escalation of unresolved incidents.
4. Whether a future `CLOSED` action is required.
5. Final field-level and history-event visibility.
6. Approved reconciliation after uncertain commit outcomes.
7. Facility-specific assignment and acceptance rules.

### 11.8. Review and Acceptance

**Bảo — Backend/Workflow:** Confirm API/DTO compatibility, transaction semantics, versioning and Backend enforcement.

**Yến — Identity/Gateway:** Confirm exact authorities, trusted-principal boundaries, tenant/resource policy and security error behavior.

**Ánh — Application:** Confirm actor eligibility, business transition mapping, required facts and frontend feedback.

**Disposition: DRAFT — REVIEW REQUIRED.**

This matrix does not authorize LIVE deployment, merge, or final end-to-end acceptance.
