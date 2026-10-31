# Facility Maintenance — Application Use Cases

**Document ID:** G2-F05  
**Purpose:** Define reviewable candidate Facility Maintenance use cases without claiming approved business policy or LIVE integration.  
**Scope:** Layer-2 Internal Service Portal, Facility-specific business behavior, and its interaction with reusable DX-OS Core.

> **Design qualification:** PROPOSED REVISION / NOT REVIEWED. The complete Facility workflow is **DEFERRED** from the first IT-focused MVP. The Portal's mock demonstration is not evidence of an approved Facility Backend API, real authorization, or end-to-end execution. The Document Register remains authoritative for document lifecycle status.

## 1. Source and Boundary

This proposal preserves `UC-FAC-01` through `UC-FAC-05` from [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76), reviewed at head commit [`7022de6d3077015cc6a23c75f5126e025574b026`](https://github.com/lexuanbao258-collab/dxos-open-core/commit/7022de6d3077015cc6a23c75f5126e025574b026). [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) defines the independent Portal boundary; [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) records the **DRAFT** business contract. This is a proposed specification, not a ratification or change to production Core.

The reference organization and its maintenance workflow are **hypothetical**, not validated through customer interviews. No time-savings, request volume, or achieved KPI is asserted.

## 2. Actors and Shared Invariants

| Actor | Responsibility | Authorization qualification |
| --- | --- | --- |
| Requester | Report a facility problem and track authorized information | Verified identity and resource grants required |
| Facility Coordinator | Assess scope and coordinate work | Role alone does not grant transition authority |
| Assigned Technician | Perform assigned repair and report facts | Current assignment eligibility checked for each action |
| Acceptance Actor | Review repair outcome | **POLICY OPEN:** requester versus designated inspector; eligibility unresolved |
| Backend Identity/Gateway (supporting system) | Verify principal and enforce trusted access boundary | Never trust user-supplied role or principal headers |
| Workflow Core (supporting system) | Own state, version, and transition history | No Facility-specific hardcoding in reusable Core |

Shared safety conditions for all LIVE candidate flows: verified authentication; explicit operation permission; tenant/resource visibility; current actor eligibility; consistent version checks for writes; authoritative Backend response; no unsafe retry when commit outcome is unknown. Application owns location, issue description, assignment, repair outcome and acceptance facts. Core owns Workflow instance/state/version/history. Any broader atomicity claim requires specific verified implementation evidence.

## 3. UC-FAC-01 — Submit a Facility Request

**Primary actor:** Requester  
**Related requirement candidates:** FR-FAC-01, FR-FAC-02, FR-FAC-09, FR-FAC-10  
**Status:** PROPOSED / FACILITY BACKEND LIVE BLOCKED

**Trigger:** Requester wishes to report a physical maintenance problem.

**Preconditions (for future LIVE flow):** Valid authenticated session and authorized create eligibility; request type and required inputs are approved by the Application contract; the Facility Backend route exists and is authorized. The current mock Portal does not satisfy these preconditions.

**Proposed main flow:**
1. The requester selects Facility Maintenance in the Portal.
2. The requester enters a nonempty problem description, title and physical location under the proposed validation rules.
3. The Application checks required inputs and displays errors without creating a false success state.
4. An authorized Backend operation validates the real principal, tenant and create permissions.
5. The Backend creates the Facility business record and Workflow instance under a reviewed consistency contract.
6. The Portal displays the authoritative response and only authorized request information.

**Success postcondition:** One authorized Facility request and its associated Workflow instance are created consistently; no state is inferred from a UI-only mock. The exact initial state is conditional on the approved Workflow definition.

**Alternatives and failures:** Missing location or description → validation rejection/no write. Unauthorized create → safe denial/no record. Duplicate submission → Backend correctness requirement; no assumed idempotency. Lost response or `COMMIT_OUTCOME_UNKNOWN` → show uncertain status and reconcile through a **reviewed** read mechanism, never blind retry. Upload/attachment → optional and unavailable until authorized API approval.

**Verification candidate:** `VAL-FAC-01` — authorized create, invalid input, unauthorized create, duplicate and unknown-write response at a pinned LIVE revision.

## 4. UC-FAC-02 — Review an Incoming Facility Request

**Primary actor:** Facility Coordinator  
**Related requirement candidates:** FR-FAC-03, FR-FAC-04, FR-FAC-05, FR-FAC-09  
**Status:** PROPOSED / REJECTION POLICY OPEN

**Trigger:** An authorized coordinator examines a newly submitted Facility request.

**Preconditions:** The request exists and is visible to the coordinator; appropriate decision authority and a supported transition are confirmed against the actual definition and resource.

**Proposed main flow:**
1. Coordinator retrieves authorized request facts and current Workflow state/version.
2. Coordinator examines the description and location, then determines whether the request belongs to Facility Maintenance.
3. The Backend checks current eligibility, authorization and version before any approved acceptance action.
4. An approved transition, **if defined**, is executed through Workflow Core; Application facts are recorded within reviewed consistency limits.
5. Coordinator sees the authoritative resulting state and history.

**Success postcondition:** A valid intake outcome exists with no unsupported state change or bypass of Core.

**Alternatives and failures:** Wrong-scope request → rejection reason and policy **not yet defined**; do not fabricate rejection transition. Missing information → hold/return-for-information behavior **POLICY OPEN**. Unauthorized, stale version or unsupported transition → no state change and safe error response.

**Verification candidate:** `VAL-FAC-02` — authorized review, hidden resource, insufficient information, forbidden transition and stale-version rejection.

## 5. UC-FAC-03 — Assign a Maintenance Technician

**Primary actor:** Facility Coordinator  
**Related requirement candidates:** FR-FAC-06, FR-FAC-09, FR-FAC-10  
**Status:** PROPOSED / ASSIGNMENT CONTRACT OPEN

**Trigger:** Coordinator selects a technician for an eligible Facility request.

**Preconditions:** The coordinator can access the resource and has assignment permission; technician is eligible under a separately approved policy; assignment/version contract exists.

**Proposed main flow:**
1. Coordinator opens authorized request and current assignment context.
2. Coordinator selects a technician from eligible candidates.
3. Backend checks coordinator grant, resource visibility, candidate eligibility and concurrency preconditions.
4. Application records the new assignee and a reviewed assignment version.
5. **Only if approved by the Facility definition**, Backend also performs an assignment-related Workflow transition through Core.
6. Backend returns the current assignment and authorized state; former assignees lose assignee-only action eligibility.

**Success postcondition:** Exactly one current authorized assignment is visible and synchronized with any actually approved Workflow operation.

**Alternatives and failures:** Ineligible technician → reject. Simultaneous reassignment → conflict rather than lost update. Revoked assignee → no protected follow-on operations. Partial/unknown commit → no false success, safe reconciliation. **Contract fork:** the earlier Facility candidate mentions `ASSIGNED` as a Workflow state, while IT Helpdesk treats `assign` as an Application command; do not import the IT semantics or assume the Facility semantics are approved.

**Verification candidate:** `VAL-FAC-03` — eligible assignment, ineligible/revoked assignee, concurrent assignments and cross-tenant denial.

## 6. UC-FAC-04 — Perform and Report Repair

**Primary actor:** Currently Assigned Technician  
**Related requirement candidates:** FR-FAC-07, FR-FAC-09, FR-FAC-10  
**Status:** PROPOSED / REPAIR FAILURE POLICY OPEN

**Trigger:** An eligible assigned technician begins and reports repair work.

**Preconditions:** Current assignee eligibility, verified authorization, supported Workflow action and expected version; required repair facts defined by approved Application rules.

**Proposed main flow:**
1. Technician retrieves an authorized assigned request and its current state.
2. Technician starts repair only through a supported, approved Backend action.
3. Technician records work performed and repair outcome.
4. Backend validates facts, current assignment, permission and version.
5. If a reviewed definition permits it, Workflow advances to a state awaiting acceptance; Core records transition history.
6. Technician receives authoritative response and no promise of completed acceptance.

**Success postcondition:** Repair facts are recorded consistently with the permitted Workflow state; acceptance remains pending if the definition requires it.

**Alternatives and failures:** Incomplete/failed repair → alternate handling **POLICY OPEN**, not a fabricated success. Missing required facts → reject without successful completion. Revoked/reassigned technician, stale version or unsupported transition → safe denial/no improper write. Attachment failure → no fabricated evidence record or claim of cross-store atomicity.

**Verification candidate:** `VAL-FAC-04` — current assignee, revoked assignee, invalid outcome, version conflict, denied state change and storage failure.

## 7. UC-FAC-05 — Review and Accept Repair Result

**Primary actor:** Acceptance Actor — **ELIGIBILITY UNRESOLVED**  
**Related requirement candidates:** FR-FAC-08, FR-FAC-09, FR-FAC-10  
**Status:** POLICY OPEN / LIVE BLOCKED

**Trigger:** Repair result is ready for an authorized acceptance decision.

**Preconditions:** An approved acceptance policy identifies actor eligibility, mandatory facts and allowed state transitions. **These prerequisites are not currently ratified.**

**Proposed main flow (conditional design only):**
1. The authorized acceptance actor views permitted repair facts and any approved evidence.
2. Actor selects an acceptance decision within a ratified business policy.
3. Backend verifies actor identity, resource visibility, permission and expected version.
4. Application records the acceptance decision and calls only an approved Core transition if one exists.
5. Backend returns a consistent outcome and authorized history.

**Success postcondition:** A recorded acceptance decision with a valid Workflow result. **No implicit production `CLOSED` state follows from a mock acceptance.**

**Alternatives and failures:** Requester versus inspector authority **OPEN**. Failed acceptance/rework and mandatory reasons **OPEN**. Separate acceptance and closure operations **OPEN**. Missing evidence, revoked permission, stale state or lost response → safe rejection or reconciliation, not fabricated closure.

**Verification candidate:** `VAL-FAC-05` — acceptance permission matrix, failed acceptance, stale version, optional evidence policy and separation of acceptance from closure, after ratification.

## 8. Proposed Workflow and Decision Boundaries

The earlier business candidate is:

`SUBMITTED → ACCEPTED → ASSIGNED → IN_REPAIR → AWAITING_ACCEPTANCE → CLOSED`

**PROPOSED / CONFLICTED CANDIDATE — NOT LIVE.** These labels are *not* an approved definition, valid transition matrix, verified endpoint list or proof of implementation.

| Open policy | Consequence for the use cases | Decision source |
| --- | --- | --- |
| Exact Facility intake/rejection behavior | UC-FAC-02 alternate flows unratified | [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Assignment as state change versus Application-only command | UC-FAC-03 integration contract unratified | Issue #53 / PR #76 |
| Acceptance actor and authority | UC-FAC-05 cannot be LIVE accepted | Issue #53 |
| Separate closure, rework and failed repair policy | UC-FAC-04/05 postconditions conditional | Issue #53 |
| Field/event visibility and attachment security | Protected data access and upload blocked pending contract | [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Real Facility Backend endpoints and two-definition reuse evidence | No LIVE or reuse PASS claim | [Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27), [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |

## 9. Negative-Test Coverage and Traceability

| Candidate validation | Covered UC | Expected check | Evidence state |
| --- | --- | --- | --- |
| VAL-FAC-01 | UC-FAC-01 | Create validation, permission, duplicate/unknown result | NOT VERIFIED |
| VAL-FAC-02 | UC-FAC-02 | Intake eligibility, hidden resources, stale version | NOT VERIFIED |
| VAL-FAC-03 | UC-FAC-03 | Assignee eligibility, revocation, version conflict | NOT VERIFIED |
| VAL-FAC-04 | UC-FAC-04 | Repair facts, unauthorized write, failed outcome | NOT VERIFIED |
| VAL-FAC-05 | UC-FAC-05 | Acceptance policy, actor permission, separation from closure | BLOCKED BY POLICY / NOT VERIFIED |
| VAL-FAC-SEC | UC-FAC-01–05 | 401, visible 403, hidden 404, tenant/resource isolation | NOT VERIFIED |
| VAL-FAC-CONS | UC-FAC-01–05 | Failed writes, stale version, unknown commit recovery | NOT VERIFIED |

`FR-FAC-01`–`FR-FAC-10` are taken from the PR #76 requirements draft; the `VAL-FAC-*` identifiers here are **new proposed review identifiers**, not accepted test cases. The Document Register and formal traceability baseline must be reconciled before approval.

## 10. Acceptance Criteria for This Specification

A reviewer should verify that: all five preserved use cases have actors, triggers, preconditions, numbered main flows, postconditions, alternatives and exceptions; every linked `FR-FAC-*` exists; no candidate state, actor policy or attachment operation is mislabeled LIVE; the Workflow/Application ownership split is maintained; and each unresolved decision is routed to an accountable owner and a future ratification record.

This document's approval **does not** certify the Facility runtime. Runtime acceptance requires a ratified contract, verified exact API/source revision, executable security/concurrency checks and independently reviewed evidence.

## 11. References

- [PR #76 — Application documentation drafts](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) (source of preserved UCs; open, not ratified at review snapshot).
- [Issue #53 — Business/API Contract](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) (draft policy, two candidate scenarios).
- [Issue #39 — Internal Service Portal](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) (Layer-2 API and authorization boundary).
- [Issue #27 — Core E2E](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) (reuse proof criteria).
- [Issue #40 — Deployment and demo](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) (release evidence conditions).

**Proposed document disposition:** DRAFT / NOT REVIEWED. **Facility LIVE acceptance:** NOT VERIFIED / DEFERRED.
