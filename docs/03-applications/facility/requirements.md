# Facility Maintenance — Application Requirements

**Document ID:** G2-F04  
**Purpose:** Specify reviewable Facility Maintenance requirements and their verification boundary.  
**Scope:** Proposed Layer-2 application and reusable DX-OS Core interactions; full Facility journey deferred.

> **Review qualification:** This is a proposed revision of [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) at `7022de6d3077015cc6a23c75f5126e025574b026`. Business and API contracts are not ratified. Document lifecycle and owner are controlled by the Document Register; no independent acceptance is asserted.

## 1. Purpose and Reference Context

Facility Maintenance is a candidate internal service scenario for reporting, coordinating, repairing and accepting physical-facility issues (for example, lighting, air-conditioning or room maintenance). The reference organization and process remain **hypothetical**, not validated customer research. The primary MVP integration priority is IT Helpdesk. The complete Facility journey is **DEFERRED**; a smaller, separately evidenced Core reuse demonstration remains a product objective, not proof of a LIVE Facility Backend.

This specification preserves `FR-FAC-01`–`FR-FAC-10` from the PR #76 draft. Its acceptance examples are proposals for design review, not assertions about implemented APIs, test results or approved business policy.

## 2. Scope and Capability Boundaries

| Scope class | Intended capability | Constraint |
| --- | --- | --- |
| Design candidate | Submit, inspect and track an authorized facility request | Requires a ratified business contract before LIVE integration |
| Design candidate | Intake, technician assignment, repair outcome | Exact actor eligibility, data fields and transitions unresolved |
| Policy open | Acceptance, rejection, rework, closure | Do not implement or promise behavior pending decisions |
| Reuse proof objective | Distinct workflow definition using the same generic Core algorithm | Actual E2E evidence must be collected separately |
| Deferred | Full Facility HTTP workflow and protected attachments | Mock UI does not satisfy LIVE acceptance |
| Out of scope | Procurement, asset inventory, ERP, BPMN designer, AI | Not part of Facility MVP candidate |

The Portal communicates through authorized published HTTP APIs. No direct Core Java injection, database access, forged identity headers or browser-side administrative storage credentials are permitted.

## 3. Stakeholders and Actor Eligibility

| Actor | Business responsibility | Authorization caveat |
| --- | --- | --- |
| Requester | Reports an issue and views permitted progress | Verified principal, tenant/resource visibility and create/read grants required |
| Facility Coordinator | Assesses intake and coordinates work | Eligibility does not follow automatically from role labels |
| Assigned Technician | Performs assigned repair and reports outcome | Current assignment and relevant resource/action grant must be checked server-side |
| Acceptance Actor | Evaluates repair result | **POLICY OPEN:** requester versus designated inspector and exact authority |
| System Administrator | Operates infrastructure within delegated policy | Administration is not blanket permission to view or alter all requests |

Identity/Gateway and Workflow Core are **system participants**, not business actors. Business role, exact authority, and resource access control are separate concepts.

## 4. Proposed Functional Requirements

| ID | Requirement | Design classification | Proposed verification |
| --- | --- | --- | --- |
| FR-FAC-01 | The authorized requester can submit a facility maintenance request | PROPOSED | Valid submission creates one authorized business request and corresponding workflow instance, if API approved |
| FR-FAC-02 | Capture issue description and physical location | PROPOSED | Required fields validated without invented mandatory-photo rule |
| FR-FAC-03 | Return only facility requests visible to the caller | SECURITY REQUIRED for LIVE | Cross-tenant and hidden-resource requests do not disclose records |
| FR-FAC-04 | Show permitted state and history | PROPOSED | History is derived from authoritative Workflow events, filtered by policy |
| FR-FAC-05 | Authorized intake actor can assess and accept an eligible request | PROPOSED / POLICY OPEN | Exact intake conditions and transition agreed before contract testing |
| FR-FAC-06 | Authorized coordinator can assign an eligible technician | PROPOSED / POLICY OPEN | Assignment eligibility, reassignment and version behavior verified |
| FR-FAC-07 | Authorized assigned worker can record a repair outcome | PROPOSED | Missing/invalid outcome rejected; no false success |
| FR-FAC-08 | An authorized acceptance actor can decide on repair outcome | POLICY OPEN | Actor, evidentiary prerequisites, failure/rework and closure modeled after ratification |
| FR-FAC-09 | Unsupported operations are rejected without state change | SECURITY/CONSISTENCY REQUIRED for LIVE | Invalid transition and unauthorized-operation negatives |
| FR-FAC-10 | Failures are presented without misleading success | CONSISTENCY REQUIRED for LIVE | Timeout and unknown commit never trigger blind retry |

**Requirement status versus API status:** `PROPOSED`, `POLICY OPEN`, and `REQUIRED` describe specification intent. `ACTIVE`, `PLANNED`, `BLOCKED`, and `LIVE_VERIFIED` are separate integration/evidence classifications. None is assigned to a Facility endpoint by this draft.

## 5. Candidate Workflow — Not a Frozen Contract

An earlier business candidate is:

`SUBMITTED → ACCEPTED → ASSIGNED → IN_REPAIR → AWAITING_ACCEPTANCE → CLOSED`

**Qualification:** PROPOSED / CONFLICTED / NOT LIVE. The sequence may differ from the final definition. Assignment may be a business command or a state-changing action **only as ratified for Facility**; do not copy the Helpdesk rule that `assign` is separate, or assume a Facility `ASSIGNED` state already exists. Likewise, `ACCEPTED` at intake must not be confused with acceptance of a completed repair. Final actor eligibility, transition keys, acceptance/closure separation, and rework branches are open.

The reusable Workflow Core is responsible for validated transitions, authoritative state, expected version and successful transition history. The Facility Application owns description, location, assignment, repair facts and acceptance business policy. It must never write a status field to bypass Workflow authority.

## 6. Candidate Information Requirements

| Data item | Purpose | Open question |
| --- | --- | --- |
| Title and description | Identify reported issue | Validation schema/limits |
| Physical location | Identify repair site | Format and access sensitivity |
| Requester identity | Attribute submission | Must derive from trusted identity, not a client-supplied identity |
| Assignment | Route to eligible worker | Ownership, versioning and revocation semantics |
| Repair outcome | Support technical completion | Mandatory evidence and failure representation |
| Acceptance decision | Review work outcome | Actor, criteria, rejection/rework and closure policy |
| Optional image/document | Potential supporting evidence | Protected upload/read API **not approved**; never mandatory by assumption |

Any application/Workflow write coordination must define transaction scope and recovery behavior. No distributed atomicity across PostgreSQL and object storage is assumed.

## 7. Non-Functional and Security Requirements (Proposed)

| ID | Requirement | Verification target |
| --- | --- | --- |
| NFR-FAC-01 | Verify authentication and enforce server-side action/resource authorization | Missing/invalid token denied; forged identity ignored |
| NFR-FAC-02 | Enforce tenant and resource visibility before returning protected facts | Hidden-resource 404 and visible-forbidden 403 as ratified by policy |
| NFR-FAC-03 | Validate current actor eligibility after reassignment or revocation | Former assignee cannot continue protected actions |
| NFR-FAC-04 | Detect stale or conflicting state mutations | Workflow `expectedVersion` and assignment conflict rules independently tested |
| NFR-FAC-05 | Maintain authoritative and consistent state/history | Successful transition writes appropriate history; failed writes do not falsely advance |
| NFR-FAC-06 | Handle uncertain write outcome safely | No blind repeat; reconciliation procedure designed and tested when supported |
| NFR-FAC-07 | Protect attachments and sensitive history fields | No LIVE upload without approved API, least privilege and field/event access checks |
| NFR-FAC-08 | Keep candidate and mock views clearly labeled | UI mock data not represented as Backend/API verification |

These NFR IDs are **new provisional identifiers** requiring collision and ownership review against the canonical Document Register and requirements inventory.

## 8. Exception and Negative Path Candidates

| Scenario | Expected design rule | Status |
| --- | --- | --- |
| Invalid or absent authentication | Reject before protected action | Security requirement, runtime evidence missing |
| Hidden resource / cross tenant | Do not disclose protected existence | Security requirement, exact policy to verify |
| Ineligible coordinator or technician | Deny mutation, preserve state | Policy open |
| Invalid state transition | Reject without history/state mutation | Required Core behavior |
| Stale version / concurrent update | Reject conflict without lost update | Candidate consistency test |
| Wrong-scope facility incident | Route/reject only under approved business policy | POLICY OPEN |
| Failed repair / failed acceptance | No false completion; rework rule must be defined | POLICY OPEN |
| Duplicate write / lost response | Avoid false success and blind retry | Safe reconciliation pending |
| Attachment failure | No dangling or falsely accepted reference | BLOCKED until storage contract reviewed |

## 9. Requirement-to-Verification Traceability

| Requirements | Proposed use case reference | Business/contract dependency | Required evidence before LIVE claim |
| --- | --- | --- | --- |
| FR-FAC-01–02 | `UC-FAC-01` | Approved create/validation contract | Source-pinned HTTP and DB tests |
| FR-FAC-03–04 | `UC-FAC-01` and history/read scenarios | Visibility and event/field policy | 401/403/404 and history filtering tests |
| FR-FAC-05 | `UC-FAC-02` | Intake eligibility and transition decisions | State/actor matrix and negative tests |
| FR-FAC-06 | `UC-FAC-03` | Assignment semantics and version policy | Concurrent assignment and revocation tests |
| FR-FAC-07 | `UC-FAC-04` | Repair outcome validation | Outcome persistence and rollback tests |
| FR-FAC-08 | `UC-FAC-05` | Acceptance actor and closure/rework policy | Ratified contract and authorized transition tests |
| FR-FAC-09–10 | All | Gateway/Workflow security and write semantics | Negative and uncertain-outcome test outputs |
| NFR-FAC-01–08 | Cross-cutting | Identity/Gateway/Data/Workflow readiness | Exact SHA, environment, results, reviewer |

References to `UC-FAC-*` are to **proposed use cases in PR #76**, not approved implementation or API routes. The acceptance matrix is a planned verification map, not a report of tests passed.

## 10. Current Evidence Boundary and Dependencies

The reviewed source was PR #76's Facility requirements draft at commit `7022de6d3077015cc6a23c75f5126e025574b026`. It establishes **draft content only**. [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) defines the Portal consumption boundary; [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) explicitly keeps Business Contract v1.2 pending ratification; [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) identifies two-definition reuse and packaging targets, not completed Facility E2E acceptance.

| Area | Evidence classification for this review | Consequence |
| --- | --- | --- |
| Facility Portal | Mock/development stated in reviewed PR | UX demonstration only |
| Facility Backend/API | Deferred / not approved in reviewed PR | Do not mark ACTIVE |
| Facility workflow definition | Proposed conflicted sequence | Do not freeze state keys |
| Attachment contract | Blocked in reviewed PR | Do not claim upload support |
| Reuse proof | Target in project issues | Exact Core binary/contracts and distinct definition E2E evidence missing |
| Independent review | No approved evidence supplied for this revision | Document remains proposed revision |

The PR-specific status is **as reviewed at the pinned PR head**, not a claim about repository HEAD or external systems now.

## 11. Open Business and Architecture Decisions

| Finding | Impact | Required decision/verification |
| --- | --- | --- |
| FAC-OD-01 | Acceptance actor/access | Requester versus delegated inspector, exact eligibility and authority |
| FAC-OD-02 | Lifecycle semantics | Acceptance and closure as one step or separate operations |
| FAC-OD-03 | Negative business routes | Wrong-scope rejection, failed repair and rework |
| FAC-OD-04 | Assignment semantics | Separate command versus Workflow transition; concurrency and revocation |
| FAC-OD-05 | Data/visibility | Location confidentiality, field/history filtering, repair evidence |
| FAC-OD-06 | HTTP readiness | Actual approved DTOs/routes and integration dependencies |
| FAC-OD-07 | Scope/reuse | Minimum real Facility slice and release timing after Helpdesk |
| FAC-OD-08 | Recovery | Uncertain write reconciliation and cross-resource consistency |

These IDs are **local review findings**, not canonical Open Issue IDs. Confirm owner, priority and Gate mapping in the governing issue register before closure.

## 12. Acceptance Criteria and Non-Claims

The requirements draft is ready to submit for independent review when its `FR-FAC-*` ID mapping, actor responsibilities, Core/Application split, conflicts, exceptions, and candidate verification criteria are internally consistent. Document approval requires a pinned docs revision, review evidence, and authorized sign-off according to the G0 design process.

LIVE Facility acceptance additionally requires ratified business and API contracts, secured published endpoints, integrated real-HTTP workflows, persistence/history evidence, negative tests and a reviewed unchanged-Core reuse proof. **This document records none of those outcomes as achieved.**

## 13. References

- [PR #76 — Application documentation source](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76)
- [Issue #39 — Internal Service Portal](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39)
- [Issue #40 — Local MVP and reuse demonstration](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40)
- [Issue #53 — Business Rules and API Contract](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53)

**Review conclusion: PROPOSED REVISION / NOT REVIEWED.**
