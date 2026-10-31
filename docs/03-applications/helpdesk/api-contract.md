# IT Helpdesk — Application API Contract

**Status:** DRAFT — PROPOSED / LIVE_BLOCKED<br>
**Owner:** Application Team — Ánh<br>
**Project:** DX-OS Open-Core<br>
**Related:** Issue #22, #23, #24, #39, #53; PR #60, #63, #70, #71

## 1. Purpose

This document records the existing and candidate HTTP capabilities required by the IT Helpdesk application.

It describes the contract between the DX-OS Internal Service Portal and the Backend Application, while preserving the separation between Application-specific business operations and the reusable Workflow Core.

The document does not introduce new production endpoints or authorize LIVE integration.

## 2. Architecture and Communication

The intended integration path is:

**React Portal → HTTPS Ingress → Portal BFF → Backend Application/Gateway → Workflow Core**

The Portal is an HTTP consumer.

The Backend must verify the authenticated principal, operation authority, resource visibility, and applicable authorization policy before returning protected data or executing operations.

The Portal must not connect directly to PostgreSQL, execute Workflow Core methods, or use privileged storage credentials.

## 3. API Capability Classification

| Capability | Status | Notes |
|---|---|---|
| Health endpoint | ACTIVE | Health response only; does not prove business readiness |
| Existing Request API | PROTOTYPE | Separate from Generic Workflow; not approved for multi-user LIVE use |
| Generic Workflow HTTP | BLOCKED | Security and registration gates remain |
| PR #63 IT Helpdesk API | IMPLEMENTED CANDIDATE / LIVE_BLOCKED | Nine guarded Application routes |
| Portal BFF and OIDC | INTEGRATION PENDING | Real browser SSO and session acceptance incomplete |
| Facility business APIs | DEFERRED / POLICY OPEN | No approved full Facility contract |
| Attachment APIs | BLOCKED | Requires authorized Backend Storage API |

An implemented candidate is not equivalent to an approved LIVE contract.

## 4. IT Helpdesk Candidate Endpoints

**Base path:** `/api/v1/mvp/requests`

The following nine routes describe the PR #63 Backend candidate.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/mvp/requests` | Create and activate an IT Helpdesk request |
| GET | `/api/v1/mvp/requests` | List authorized requests |
| GET | `/api/v1/mvp/requests/{id}` | Retrieve an authorized request |
| POST | `/api/v1/mvp/requests/{id}/triage` | Triage a submitted request |
| POST | `/api/v1/mvp/requests/{id}/assign` | Assign an eligible technician |
| POST | `/api/v1/mvp/requests/{id}/start` | Start technical work |
| POST | `/api/v1/mvp/requests/{id}/resolve` | Record resolution |
| POST | `/api/v1/mvp/requests/{id}/confirm` | Confirm resolution |
| GET | `/api/v1/mvp/requests/{id}/history` | Retrieve authorized Workflow transition history |

These endpoints are not enabled for unrestricted production use.

## 5. Request Payloads

### Create Request

**POST `/api/v1/mvp/requests`**

Candidate request fields:

- `title`: Nonblank, maximum 200 characters.
- `description`: Nonblank, maximum 4000 characters.
- `requestType`: Exact value `IT_HELPDESK`.

The Backend generates the request and Workflow identifiers. Clients must not supply authoritative actor identity or Workflow state.

The candidate returns HTTP 201 with a `RequestView` when creation succeeds.

### Triage

**POST `/api/v1/mvp/requests/{id}/triage`**

Required field: `expectedVersion`.

Only an eligible and authorized coordinator may execute this action.

### Assignment

**POST `/api/v1/mvp/requests/{id}/assign`**

Required fields:

- `assigneeId`
- `expectedAssignmentVersion`

Assignment is an Application command separate from the Workflow transition.

It must not increment the Workflow version merely because assignment changes.

### Start Work

**POST `/api/v1/mvp/requests/{id}/start`**

Required field: `expectedVersion`.

The current eligible assignee may initiate the `start_work` Workflow transition when authorized.

### Resolve

**POST `/api/v1/mvp/requests/{id}/resolve`**

Required fields:

- `expectedVersion`
- `resolution`: Nonblank, maximum 4000 characters.

### Confirm

**POST `/api/v1/mvp/requests/{id}/confirm`**

Required field: `expectedVersion`.

The authorized original requester may confirm a resolved request.

The current candidate ends at `CONFIRMED`; no LIVE `CLOSED` endpoint is provided.

## 6. Request and History Response Models

### RequestView

The Backend candidate provides these base fields:

- `id`
- `title`
- `description`
- `requestType`
- `requesterId`
- `assigneeId`
- `resolution`
- `assignmentVersion`
- `workflowInstanceId`
- `state`
- `version`
- `allowedActions`

The authorized detail response may also include:

- `requesterDisplayName`
- `assigneeDisplayName`
- `eligibleAssignees`, only for authorized assignment choices

Display names are optional and must not be treated as authentication evidence.

`allowedActions` is a server-derived UI hint. Every subsequent write must be authorized again by the Backend.

### HistoryView

The candidate transition-history response contains:

- `transitionId`
- `sourceState`
- `targetState`
- `version`
- `occurredAt`

History does not automatically include actor names, internal notes, security audit records or assignment audit entries.

The Portal must not invent fields or present missing evidence as recorded history.

## 7. Listing and Filtering

**GET `/api/v1/mvp/requests`**

The candidate supports:

- `after`: UUID pagination cursor.
- `limit`: 1–100, default 50.

Response structure:

- `items`: Authorized request views.
- `nextCursor`: Cursor for the next page, when available.

The Backend must enforce resource-level visibility before returning list items.

The nine-route candidate does not establish a general server-side filter-by-status, total-count or full work-queue search contract.

The Portal must not assume those capabilities exist.

## 8. Authentication and Authorization

All protected operations must enforce:

1. Verified user identity.
2. Applicable exact operation authority.
3. Resource and tenant visibility.
4. Explicit action/resource authorization.
5. Business eligibility for the requested operation.
6. Final authorization checks at the relevant write boundary.

Expected denial categories include:

| Condition | Expected behavior |
|---|---|
| Missing or invalid authentication | 401 |
| Visible resource but forbidden operation | 403 |
| Hidden, cross-tenant or nonexistent resource | Indistinguishable 404 |
| Stale Workflow or assignment version | 409 conflict |
| Unknown write outcome | No false success or blind retry |

The final field-level and history-event visibility policies remain subject to review.

Frontend role selection and hidden buttons are not security enforcement.

## 9. Portal Mapping Requirements

The Portal must map the Backend candidate explicitly:

| UI feature | Backend capability |
|---|---|
| My Requests | Authorized list |
| Request Detail | Authorized detail |
| Create Request | Create IT request |
| Coordinator Triage | Triage |
| Technician Assignment | Assign |
| Start Work | Start |
| Record Result | Resolve |
| Requester Confirmation | Confirm |
| Timeline | Authorized minimal transition history |

The existing Portal mock model and the Backend `RequestView` are different contracts.

Fields such as mock priority, location, display statuses and timeline notes must not be automatically submitted or assumed present in the candidate API.

Any additional business fields require an approved Application contract change.

## 10. Workflow and Transaction Boundaries

Workflow Core owns authoritative state, version and transition history.

Application owns IT-specific request facts, assignment and resolution data.

The PR #63 Backend candidate coordinates relevant business and Workflow writes through a guarded PostgreSQL transaction.

That implementation is not evidence of atomicity with external object storage or approval of all provisioning, recovery and LIVE integration policies.

If the client loses a write response and cannot establish the outcome from authorized evidence, the result must remain UNKNOWN pending reconciliation.

No general idempotency receipt API is promised.

## 11. Unsupported or Deferred APIs

The first IT Helpdesk candidate does not provide an approved route for:

- Wrong-scope rejection.
- Requester dispute or rework.
- Unresolved incident escalation.
- Production `CLOSED` transition.
- Full Facility Maintenance processing.
- Protected attachment uploads or downloads.
- General server-side advanced search and filtering.

Missing routes must not be simulated as successful LIVE operations.

Related business decisions remain OPEN, BLOCKED or DEFERRED according to the reviewed source contract.

## 12. Integration and Acceptance Gates

Before enabling real Portal-to-Backend operations:

1. The applicable business/API contract must be reviewed.
2. BFF and OIDC browser login must be verified.
3. Backend resource authorization and negative tests must pass for the selected environment.
4. Application request/response mapping must be verified.
5. History visibility and sensitive-field policy must be accepted.
6. Transaction and uncertain-write recovery behavior must be reviewed.
7. End-to-end tests must pass on an identified source revision.

Until those conditions are met, Portal functionality remains MOCK or explicitly BLOCKED.

## 13. References

- Issue #39 — Internal Service Portal
- Issue #53 — MVP Business/API Contract
- PR #60 — Draft Business/API Contract
- PR #63 — Helpdesk Backend Candidate
- PR #70 — Portal BFF Candidate
- PR #71 — Portal Integration Candidate
- PR #73 — Documentation Restructuring

**Document status: DRAFT — REVIEW REQUIRED / LIVE_BLOCKED.**
