# Facility Maintenance — Application Use Cases

**Status:** DRAFT — PROPOSED / NOT APPROVED  
**Owner:** Application Team — Ánh  
**Project:** DX-OS Open-Core  
**Related:** Issue #39, Issue #53, PR #60, PR #71, PR #73

## 1. Purpose

This document describes the proposed Facility Maintenance use cases for the DX-OS Internal Service Portal.

The application is intended to help employees report facility problems, coordinate repairs, track maintenance progress, and record authorized acceptance outcomes.

Facility-specific business behavior belongs to the Application layer. The generic Workflow Core provides reusable workflow state, transition, versioning, and history capabilities.

## 2. Scope and Implementation Status

The first MVP prioritizes IT Helpdesk integration.

The complete Facility Maintenance workflow is **DEFERRED** from the first IT-focused MVP.

The current Portal supports a Facility demonstration using mock data. This does not prove the existence of an approved Facility Backend API or real end-to-end Workflow integration.

All use cases below describe the proposed business design and require review before LIVE implementation.

## 3. Actors

| Actor | Responsibility |
|---|---|
| Requester | Reports a facility issue and follows its progress |
| Facility Coordinator | Reviews incoming requests and coordinates maintenance |
| Assigned Technician | Performs the repair and reports its outcome |
| Acceptance Actor | Reviews and accepts the repair result, subject to an unresolved eligibility policy |
| Identity/Gateway | Provides the trusted authentication and authorization boundary |
| Workflow Core | Manages workflow state, version, and transition history |

Business roles do not automatically grant authorization. Protected operations require verified identity and appropriate resource/action permissions.

## 4. UC-FAC-01 — Submit a Facility Request

**Primary actor:** Requester

**Preconditions:**

- The user has a verified identity for future LIVE integration.
- The user has the required create eligibility and authorization.
- The requested service is within the approved Facility scope.

**Proposed main flow:**

1. The requester opens the Internal Service Portal.
2. The requester selects Facility Maintenance.
3. The requester enters an issue title, description, and physical location.
4. The Application validates the submitted information.
5. The authorized Backend Application creates the relevant business record and workflow instance according to an approved contract.
6. The Portal displays the request and its current status.

**Expected result:** A submitted Facility request is recorded in the proposed workflow.

**Exceptions:**

- Invalid information must be rejected without creating a request.
- Unauthorized creation must fail safely.
- An unknown write outcome must not be presented as successful.
- Attachment upload is not assumed available.

**Status:** PROPOSED / BACKEND LIVE BLOCKED.

## 5. UC-FAC-02 — Review an Incoming Facility Request

**Primary actor:** Facility Coordinator

**Proposed main flow:**

1. The coordinator views an authorized Facility request.
2. The coordinator examines the issue and location.
3. The coordinator determines whether the request is eligible for maintenance processing.
4. If permitted by the approved workflow, the coordinator accepts the request for further handling.
5. The Application updates the appropriate business facts while Workflow Core manages the authorized transition.

**Expected result:** An eligible request is accepted for maintenance processing.

**Alternative conditions:**

- A request outside the team's scope requires an approved rejection policy.
- Missing information cannot be silently treated as acceptance.
- Invalid transitions must be rejected without a false status change.

**Status:** PROPOSED — rejection policy remains OPEN.

## 6. UC-FAC-03 — Assign a Maintenance Technician

**Primary actor:** Facility Coordinator

**Proposed main flow:**

1. The coordinator opens the accepted request.
2. The coordinator selects an eligible technician.
3. The Backend verifies the technician's current eligibility and applicable authorization.
4. The Application records the assignment and coordinates the corresponding Workflow operation if required by the approved Facility definition.
5. The assigned technician can access the request only within the approved authorization scope.

**Expected result:** The request has a valid assigned technician.

Unlike the first IT Helpdesk candidate, the earlier Facility design proposes an assignment-related Workflow transition. This remains unapproved and must not be inferred from the IT assignment API.

**Exceptions:**

- Ineligible technician assignment is rejected.
- Revoked access must not permit unauthorized writes.
- Concurrent assignment changes require an approved consistency policy.

**Status:** PROPOSED / CONTRACT OPEN.

## 7. UC-FAC-04 — Perform and Report Repair

**Primary actor:** Assigned Technician

**Proposed main flow:**

1. The authorized technician opens the assigned request.
2. The technician begins repair work according to the approved workflow.
3. The technician records the repair result and required supporting facts.
4. The Backend validates the outcome and authorization.
5. The workflow may advance to a state awaiting acceptance.

**Expected result:** The repair outcome is available for authorized review.

**Exceptions:**

- A repair that cannot be completed must not be reported as successful.
- Missing required information prevents invalid completion.
- A technician who has lost assignment eligibility must not perform protected operations.
- Attachment failures must not create false evidence references.

**Status:** PROPOSED — unresolved repair outcomes require a business decision.

## 8. UC-FAC-05 — Review and Accept Repair Result

**Primary actor:** Acceptance Actor — exact role OPEN

**Proposed main flow:**

1. An authorized acceptance actor opens the repair result.
2. The actor reviews the reported outcome and applicable evidence.
3. The actor records a decision under an approved business policy.
4. The Backend validates the actor's eligibility and permissions.
5. The approved workflow transition is executed, if supported by the final definition.

**Expected result:** The acceptance outcome is recorded consistently with the approved business process.

**Open decisions:**

- Whether the requester or a designated inspector performs acceptance.
- Whether acceptance and closure are separate operations.
- What evidence is mandatory before acceptance.
- How failed acceptance and rework should be handled.

The current documentation must not assume that acceptance automatically creates a production `CLOSED` state.

**Status:** POLICY OPEN / LIVE BLOCKED.

## 9. Proposed Workflow Overview

The earlier Facility business candidate is:

`SUBMITTED → ACCEPTED → ASSIGNED → IN_REPAIR → AWAITING_ACCEPTANCE → CLOSED`

**Status: PROPOSED / CONFLICTED CANDIDATE — NOT LIVE.**

This sequence is a business design reference, not confirmation that every state or transition has been implemented or approved.

Workflow Core would own authoritative state and history. The Facility Application would own location, assignment, repair, and acceptance business facts.

## 10. Negative and Exceptional Scenarios

The future acceptance plan must consider:

- Missing or invalid authentication.
- Unauthorized and cross-tenant access.
- Invalid workflow transitions.
- Stale workflow or assignment versions.
- Duplicate user actions.
- Revoked technician eligibility.
- Wrong-scope requests.
- Failed repair and failed acceptance.
- Missing required repair evidence.
- Unknown write outcomes.
- Unsupported attachment operations.

These scenarios require approved business rules and separate Backend/security verification.

Portal mock behavior alone does not establish LIVE acceptance.

## 11. Acceptance Criteria for Documentation

This document is ready for review when:

1. Actors and responsibilities are clearly identified.
2. The proposed use cases align with the Facility requirements.
3. Application and Core responsibilities remain distinct.
4. Unapproved transitions and actor policies are identified.
5. Negative scenarios are included.
6. Facility implementation is not incorrectly represented as LIVE.
7. Open decisions are traceable to Issue #53 and PR #60.

## 12. References

- Issue #39 — Internal Service Portal
- Issue #53 — Business Rules & API Contract
- PR #60 — Draft MVP Business/API Contract
- PR #71 — Portal Integration Candidate
- PR #73 — Documentation Restructuring

**Document status: DRAFT — REVIEW REQUIRED.**
