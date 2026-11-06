# Facility Maintenance — Application Requirements

**Status:** DRAFT — PROPOSED / NOT APPROVED<br>
**Owner:** Application Team — Ánh<br>
**Project:** DX-OS Open-Core<br>
**Related:** Issue #39, Issue #53, PR #60, PR #71, PR #73

**Source and scope:** [Original ISP-BA analysis, PR #68](https://github.com/lexuanbao258-collab/dxos-open-core/tree/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis); [Bảo's product scope direction](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6386250475). The [A02 reconciliation](../business-contract-review.md) preserves provenance and distinguishes that scope direction from unapproved detailed rules.

## 1. Purpose

The Facility Maintenance application is intended to support the submission, coordination, tracking, and acceptance of internal facility repair requests.

Examples include damaged equipment, air-conditioning failures, lighting problems, and meeting-room maintenance.

The application is designed to reuse the generic DX-OS Workflow Core while maintaining Facility-specific business rules within the Application layer.

## 2. MVP Scope and Status

The current MVP prioritizes IT Helpdesk integration.

The full Facility Maintenance business journey is **DEFERRED** from the first IT-focused MVP.

The existing Portal may demonstrate Facility requests using mock data. This does not demonstrate an approved Facility Backend API or production Workflow execution.

This document describes proposed requirements for future review and reuse validation.

| Scope item | Evidence-backed disposition | Approval boundary |
| --- | --- | --- |
| Prioritize Helpdesk; use a distinct second definition to demonstrate unchanged Core reuse | Product direction aligned in the linked owner comment | Exact Facility reuse definition, actor/facts, HTTP scope and executed acceptance still OPEN |
| Full Facility submit-to-closure journey in the first MVP | DEFERRED under that product direction | Does not erase the broader proposed MUSTs in original ISP-BA-05/06/07; preserve the scope change explicitly |
| Facility screens and synthetic requests | MOCK / DEVELOPMENT demonstration only | No Backend, actual repair, persisted workflow or E2E acceptance |
| Exact lifecycle, assignment state, acceptance actor/evidence, reject/rework/close, HTTP DTOs | CANDIDATE / POLICY OPEN | No final detailed Facility Business Contract ratification found in the inspected sources |

Facility physical-location and repair/acceptance facts differ from IT software support and original-requester confirmation. Do not send Facility through `requestType=IT_HELPDESK`, reuse IT action semantics or declare Facility LIVE because the same Core can support definitions.

## 3. Stakeholders and Actors

| Actor | Responsibility |
|---|---|
| Requester | Submits facility issues and tracks progress |
| Facility Coordinator | Reviews incoming requests and coordinates maintenance |
| Assigned Technician | Performs assigned repair work |
| Acceptance Actor | Reviews the repair result; exact eligibility remains OPEN |
| System Administrator | Manages infrastructure under separately approved policies |

Business roles do not automatically grant access. Backend operations require verified identity and applicable resource-level authorization.

## 4. Proposed Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-FAC-01 | Submit a facility maintenance request | PROPOSED |
| FR-FAC-02 | Provide issue description and location | PROPOSED |
| FR-FAC-03 | View authorized facility requests | PROPOSED |
| FR-FAC-04 | Track request status and history | PROPOSED |
| FR-FAC-05 | Review and accept requests for processing | PROPOSED |
| FR-FAC-06 | Assign an eligible maintenance technician | PROPOSED |
| FR-FAC-07 | Record the repair outcome | PROPOSED |
| FR-FAC-08 | Support an authorized acceptance decision | POLICY OPEN |
| FR-FAC-09 | Reject unsupported actions safely | REQUIRED |
| FR-FAC-10 | Handle failures without false success messages | REQUIRED |

These requirements are documentation proposals, not proof of completed LIVE implementation.

Requirements map to UC-FAC-01 (FR-01/02), UC-FAC-02 (FR-05), UC-FAC-03 (FR-06), UC-FAC-04 (FR-07), UC-FAC-05 (FR-08), and protected reads/history (FR-03/04). FR-09/10 apply across every candidate command; [use-case guards](use-cases.md#candidate-preconditions-facts-and-postconditions) and the [acceptance plan](acceptance-tests.md) define observable expectations without claiming execution.

## 5. Proposed Facility Workflow

The earlier Facility business candidate describes:

`SUBMITTED → ACCEPTED → ASSIGNED → IN_REPAIR → AWAITING_ACCEPTANCE → CLOSED`

This sequence is a **PROPOSED / CONFLICTED CANDIDATE — NOT LIVE**.

The authorized acceptance actor, combined acceptance/closure behavior, rejection paths, and rework conditions require explicit business decisions.

The Application must not implement or present these candidate transitions as approved Backend capabilities.

## 6. Information Requirements

A Facility request may require:

- Issue title.
- Description of the facility problem.
- Physical location.
- Requester identity derived from verified authentication.
- Assigned technician, where applicable.
- Repair information and outcome.
- Authorized acceptance result, when the policy is defined.

Proposed minimum intake facts are a meaningful title, description and physical location; exact lengths, location taxonomy and DTO names are OPEN. Technician identity is derived/validated server-side; a reported outcome must distinguish successful repair from incomplete/failed work. Acceptance needs a separately authorized decision and applicable rationale/evidence. These are conceptual validation requirements, not additional fields on the IT DTO.

Photos or documents may be useful supporting evidence, but protected attachment APIs are currently BLOCKED.

A photo must not be treated as mandatory for the initial request unless an approved contract establishes that requirement.

## 7. Core and Application Responsibilities

**Workflow Core responsibilities:**

- Maintain authoritative workflow state.
- Validate configured transitions.
- Manage workflow versioning.
- Record successful transition history.

**Facility Application responsibilities:**

- Validate Facility-specific request information.
- Manage business facts and technician assignment.
- Coordinate repair outcomes and acceptance requirements.
- Present authorized request data to users.

Facility-specific business rules must not be hardcoded into reusable Core modules.

Use the same provider-neutral Core contracts and algorithms, changing only approved definitions, Application policy/facts and provider adapters as needed. Facility reuse acceptance must demonstrate unchanged Core behavior with a genuinely distinct definition; a copied Helpdesk label or mock screen is insufficient.

## 8. Security and Authorization

The target integration must provide:

- Verified user identity.
- Resource and tenant visibility checks.
- Explicit operation and resource permissions.
- Technician eligibility validation.
- Appropriate protection for internal notes and sensitive details.
- Safe rejection for unauthorized requests.
- Concurrency protection for state-changing operations.

Frontend role selection and hidden buttons are not evidence of Backend authorization.

Final Facility field/event visibility policies remain subject to review.

## 9. Exceptional Scenarios

The business specification must address:

- Requests outside the Facility team's scope.
- Rejected maintenance requests.
- Technician assignment changes.
- Failed repair or unsuccessful acceptance.
- Repair outcomes that cannot be completed.
- Stale versions and concurrent updates.
- Lost responses or unknown commit outcomes.
- Attachment failures without false references.

Several exception paths remain **POLICY OPEN / BLOCKED**, and must not be silently treated as implemented or approved.

## 10. Integration Status

| Component | Current status |
|---|---|
| Facility Portal UI | MOCK / DEVELOPMENT |
| Shared Workflow Core | Reusable architecture and contracts exist |
| Complete Facility Backend API | DEFERRED / NOT APPROVED |
| Identity and resource authorization | LIVE integration gates pending |
| Protected attachments | BLOCKED |
| Facility end-to-end acceptance | NOT VERIFIED |

The nine-route IT Helpdesk candidate in PR #63 does not establish a Facility API contract.

## 11. Acceptance Criteria for Documentation

The Facility requirements document is ready for review when:

1. The proposed actors and responsibilities are identified.
2. Functional requirements are traceable to the business scope.
3. Facility and Workflow Core responsibilities are separated.
4. Unapproved transitions and acceptance policies are clearly labeled.
5. Error and authorization scenarios are included.
6. Deferred implementation and LIVE acceptance are not misrepresented.

Implementation acceptance requires separate approved contracts, source revisions, and executable verification evidence.

## 12. Open Decisions

The following require Owner, Application, and Identity/Security review:

- Exact Facility acceptance actor.
- Whether acceptance and closure are separate actions.
- Mandatory rejection and rework behavior.
- Repair-failure handling.
- Assignment and authorization policy.
- Facility HTTP API and response models.
- Scope and timing of the complete Facility release.

BD-06/07 in the [decision register](../business-decisions.md) record proposals, impacts, owners and OPEN status; BD-01/02/04/05 cover wrong scope, failure handling, visibility and unknown outcomes. Original BL-02 / AT-MVP-12 (cannot repair further) stays blocked until a terminal policy or explicit exclusion is approved; it must not be reported as fulfilled maintenance.

## 13. References

- Issue #39 — Internal Service Portal
- Issue #53 — Business Rules & API Contract
- PR #60 — Draft MVP Business/API Contract
- PR #71 — Portal Integration Candidate
- PR #73 — Documentation Restructuring

**Document status: DRAFT — REVIEW REQUIRED.**
