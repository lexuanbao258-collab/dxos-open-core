# IT Helpdesk — Business Rules

**Status:** DRAFT — PROPOSED / NOT APPROVED<br>
**Owner:** Application Team — Ánh<br>
**Project:** DX-OS Open-Core<br>
**Related:** Issue #23, Issue #39, Issue #53, PR #60, PR #63, PR #71

**A02 scope evidence:** [Ánh's Application Owner review](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6551136261) acknowledges the current candidate **with conditions**. Final Business Contract ratification and LIVE acceptance remain pending. [Source reconciliation](../business-contract-review.md) records the inspected revisions and [business decisions](../business-decisions.md) retain all unresolved choices as OPEN.

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

### Actor eligibility and authoritative facts

| Actor | Responsibility and eligibility | Limits |
| --- | --- | --- |
| Requester | Verified canonical actor; active tenant membership; provisioned submitter eligibility. Original requester is persisted by Backend at creation. | May create with required grants and read permitted information; may confirm only own resolved request while still eligible and authorized. Ownership is not a bypass. |
| IT Coordinator | Tenant-scoped coordinator eligibility from authoritative provisioning; explicit permissions for the actual request. | Triage and assign/reassign in permitted states. Department membership/title alone grants neither every ticket nor technician/confirm rights. |
| Assigned Technician | Current persisted assignee, active membership and worker eligibility; actual request read/action grants. | Start/resolve only while currently assigned and authorized. Former assignment or cached UI is insufficient. Cannot confirm for requester merely because they performed the work. |
| System Administrator | Configuration/infrastructure responsibility under separately approved administrative policy. | No automatic list/detail/history, assignment, transition, confirmation or impersonation permission. If separately delegated a business role, all the same checks apply. |

One person may satisfy more than one business predicate, but each action is checked separately. Original requester, current assignee and ACL resource owner are distinct facts even if the candidate initially records the requester as resource owner. Verified issuer/subject mapping and exact authorities come from Identity, not browser roles, display names, arbitrary headers or client DTO fields.

The inspected [Backend candidate](https://github.com/lexuanbao258-collab/dxos-open-core/blob/fe55347908eed6a351ca9724be899138f7683081/backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java) obtains eligibility from tenant-scoped actor facts, membership from the authoritative membership source, and requester/assignee from persisted Helpdesk facts. This describes candidate source, not approved provisioning or LIVE access policy.

## 3. Request Creation Rules

**BR-IT-01 — Valid Request Information**

An IT request must contain valid information, including a nonblank title and description.

**BR-IT-02 — Authorized Creation**

Only a verified and eligible requester with the required Backend permissions may create a LIVE request.

**HD-01 — CREATE design prerequisites; implementation BLOCKED, not PASS.** Before protected definition/binding lookup, require a verified principal, trusted tenant, active tenant membership, authoritative `submitter=true`, exact client-scoped `workflow:create`, and authoritative tenant/resource-type CREATE permission. Provisional ownership of a not-yet-created resource grants no authority. Create/activate/response-read must satisfy their respective permissions within a reviewed transaction boundary; denial must leave no unauthorized business, grant, Workflow state/version or history writes. Provisioning, authorization ordering and exact-SHA independent evidence remain required; this design does not establish implementation acceptance.

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

### Resource, field and history visibility

The following is a **proposed business disclosure policy for review**, alongside the actual candidate payload inventory. It does not authorize new response fields, silently redact existing wire DTOs or approve D1/D2/D3 classifications.

| Information | Requester proposal | Coordinator proposal | Assigned Technician proposal | System Administrator | Candidate / remaining decision |
| --- | --- | --- | --- | --- | --- |
| List membership / request existence | Authorized own requests | Explicitly granted resources within responsibility | Explicitly granted current work resources | No implicit access | Per-item Backend scope; other departments/tenants hidden absent an explicit valid grant |
| Title, description, type, state, resolution | Own authorized progress and result | Granted intake/work facts | Granted facts needed to perform current work | No implicit access | Base RequestView currently includes these for authorized readers; actual per-field policy OPEN |
| Requester/assignee IDs, Workflow ID, both versions | Only contract-required authorized metadata | Same resource and field policy | Same resource and field policy | No implicit access | Candidate fields exist; not proof each actor should receive every field |
| Display names | Approved display information only | Same | Same | No implicit directory access | Optional detail-only trusted names; absence is not an error and UI must not invent a person |
| Eligible technician directory | No automatic access | Detail only when server allows `assign` in `TRIAGED` | No access solely as technician | No automatic access | `eligibleAssignees[{id,displayName}]`; no general directory/search API |
| Transition history | Authorized own progress events | Granted relevant events | Granted work events | No implicit access | Candidate five fields only: transitionId, sourceState, targetState, version, occurredAt |
| Assignment audit, actor attribution, internal notes, sensitive decisions | No automatic disclosure | Only explicitly approved need/scope | Only explicitly approved need/scope | Separate policy | Not fields in candidate HistoryView; audit/event schema and access OPEN |

All data, including apparently shareable fields, still requires resource authorization. List access does not approve all detail fields; detail access does not settle event-level history visibility. The current source history operation reuses request READ and emits minimal transitions; separate history business policy is unresolved. Record any approved field/event exclusions before implementation and test serialized responses, not only hidden UI controls.

Only committed transitions appear as successful Workflow history. Creation, assignment, denied actions and version conflicts are not fabricated transition events. The Portal must not synthesize actor names, reasons, assignment events or security audit trails from a state change.

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

### Candidate concurrency and recovery details

Application owns title/description/type, original requester, assignment and resolution; Workflow Core owns authoritative state, pinned definition, version and committed transition history. No second writable status is introduced. The definition remains `it-helpdesk@1` in candidate source.

Transitions require the exact current `expectedVersion`; assignment requires the current `expectedAssignmentVersion`. Denial or conflict must not leave successful business/state/history writes. Single-Connection PostgreSQL coordination is a candidate implementation property requiring independent verification, not a PostgreSQL/MinIO atomicity promise.

On 409, reread and ask the actor to reconsider the action; never automatically replay with a refreshed version. Prevent accidental double submission in UI without claiming general Backend idempotency. A lost response or `COMMIT_OUTCOME_UNKNOWN` may mean committed, uncommitted or unresolved; preserve UNKNOWN until attributable authorized evidence resolves it. See BD-05 in the [decision register](../business-decisions.md).

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

The [decision register](../business-decisions.md) records proposals, impacts, deciding owners and OPEN status. Wrong-scope rejection remains a proposed MUST in the original analysis and an unresolved gap in this narrower candidate; lack of a route is not an approved exclusion. Requester silence is not confirmation. An unresolved incident must not be recorded as a successful resolution. Future closure, rework and escalation need an explicit contract decision.

## 9. MOCK and LIVE Boundary

The current Portal uses `MockRequestService` and synthetic records for UI demonstrations.

Mock roles, transitions, request histories and permission messages demonstrate intended user experience only.

The Backend candidate in PR #63 and the Portal integration candidate in PR #71 require their respective integration and security acceptance gates before LIVE use.

PR #63 is historical lineage; #74/#95 are newer candidates. PR #95 is the inspected source, not an accepted LIVE baseline; #97 remains a separate compatibility proposal.

No mock action should be described as proof of real authentication, authorization or persisted Workflow execution.

## 10. Review and Acceptance Criteria

This document is ready for review when:

1. Business actors and responsibilities are clearly separated.
2. Workflow transitions and Application assignment rules are clearly distinguished.
3. The proposed Workflow states and business actions are consistent with the reviewed MVP candidate.
4. Verified identity, resource authorization and negative error scenarios are explicitly documented.
5. Workflow versioning, assignment concurrency and uncertain commit outcomes are addressed.
6. Unsupported transitions and unresolved business decisions remain explicitly OPEN or DEFERRED.
7. Bảo confirms Backend/API compatibility and transaction semantics.
8. Yến reviews the security, authority and resource-visibility requirements.
9. Runtime and E2E acceptance require separate test evidence at an exact integration SHA.

## 11. Helpdesk Business Action & Transition Matrix

**Status:** DRAFT / APPLICATION OWNER REVIEW / FINAL SECURITY APPROVAL PENDING  
**Owner:** Ánh — Application & Frontend  
**Related:** Issue #53, PR #74, PR #70, PR #71, PR #76

### 11.1. Candidate Workflow

`SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`

- `assign` is an Application business command, not a Workflow transition.
- `CONFIRMED` is the terminal state of the current Helpdesk MVP candidate.
- `CLOSED`, rejection, reopen and rework have no approved operation in the current candidate. Their business disposition remains OPEN; wrong-scope rejection is not an approved MVP exclusion.

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

Assignment/reassignment is available only in `TRIAGED` in this candidate. The target must be an active eligible technician in the same tenant. Assignment increments `assignmentVersion` once while Workflow state/version/history remain unchanged. Create returns Workflow version 0 and assignment version 0; creation is not a transition-history event. All action rows also require the shared authorization conditions in §11.3; original requester confirmation requires current eligibility and a recorded resolution. Removed-assignee eligibility is governed by BR-IT-15; independent permissions are evaluated separately.

### 11.3. Authorization Requirements

For each protected operation, the Backend must verify:

1. A trusted authenticated principal.
2. The exact required operation authority.
3. Tenant and resource visibility.
4. Resource-level action permission.
5. Business eligibility, including current assignment when applicable.
6. The applicable Workflow or assignment version for mutations.

UI button visibility and demo roles do not grant authorization.

**OPEN:** Final actor eligibility, provisioning, field visibility and history-event disclosure require Yến's security review and Backend contract confirmation. Exact operation authorities are defined in the canonical contract; their endpoint mapping and enforcement still need review at the selected integration SHA.

Every protected operation requires verified identity, active tenant/resource scope, exact operation authority, explicit resource/action permission and applicable business eligibility. Hidden/nonexistent/cross-tenant resources must remain indistinguishable; a visible resource can still be forbidden. Writes re-evaluate current authorization and business facts at the transaction boundary.

[Canonical operation mapping](../../contracts/workflow-authorization.md) uses exact `workflow:create`, `workflow:activate`, `workflow:read` and `workflow:transition`; no wildcard, role label or new JWT history authority is implied. The Helpdesk candidate combines create + activate + response-read in one command, requires read for mutations returning a view, uses read for history, and uses transition permission for the separate assignment command. Final CREATE provisioning/order, history-specific policy and field/event disclosure remain owner-review gates.

UI `allowedActions` is a Backend projection for the authenticated actor at read time. It never substitutes for checking the write again after grants, eligibility, assignment or versions change.

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
