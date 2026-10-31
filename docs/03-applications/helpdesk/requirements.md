# IT Helpdesk — Application Requirements

> **Document ID:** G2-F01  
> **Purpose:** Specify traceable IT Helpdesk application requirements for review.  
> **Scope:** First IT-focused integration candidate; requirements, not proof of LIVE implementation.  
> **Source baseline:** [PR #76, head `7022de6d3077015cc6a23c75f5126e025574b026`](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76/commits/7022de6d3077015cc6a23c75f5126e025574b026).  
> **Document lifecycle authority:** G0 Document Register. Proposed revision only; NOT REVIEWED.

## Purpose and Context

In a **hypothetical** multi-department organization, employees may need a controlled way to submit and track IT support requests. The absence of a validated customer study means operational pain points and claimed benefits remain hypotheses; see the G1 Problem Statement. This specification translates the current IT-focused Application candidate into testable requirements without asserting production readiness.

This document refines, rather than supersedes by authority, the [nine-file documentation PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) and the [draft business contract in Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53). It must be reconciled with the existing source documents before any merge.

## Stakeholders and Actors

| Actor | Business responsibility | Security caveat |
| --- | --- | --- |
| Requester | Create, view, follow, confirm own eligible request | Identity alone grants no resource permission |
| IT Coordinator | Review, triage, select eligible technician | Requires server-verified action and resource grants |
| Assigned Technician | Begin work, provide resolution | Assignment is current and separately versioned |
| System Administrator | Operate configured infrastructure | No automatic business-request access |

The Portal is a separate HTTP consumer of published Backend contracts. Identity/Gateway and Workflow Core are system components, **not human business roles**.

## Scope and Requirements Classification

**MUST for the proposed first IT-focused candidate:** request creation; authorized list/detail/history; triage; separate assignment; work start; resolution; requester confirmation; server-side authorization; concurrency safety; safe error handling; persistent state and history; representative real-HTTP verification when security gates permit.

**CONDITIONAL:** file attachments only after protected Backend/MinIO APIs, authorization and lifecycle checks are approved and tested.

**NOT PART OF THE CURRENT INTEGRATION CANDIDATE:** production `CLOSED` transition after `CONFIRMED`, wrong-scope rejection, formal rework/escalation and full Facility journey. These are **open scope/business decisions**; an earlier G1 aspirational path mentions closure and therefore requires reconciliation, not silent removal of a committed MVP acceptance claim.

**OUT:** ERP, procurement/inventory, BPMN designer, AI, microservices rewrite and frontend-driven database updates.

Requirements labeled MUST are targets, not evidence of implementation or API `ACTIVE` status.

## Functional Requirements

| ID | Requirement | Proposed acceptance criterion | Source / dependency |
| --- | --- | --- | --- |
| FR-01 | Authorized requester submits a valid IT support request | Nonblank title/description accepted; invalid data creates no record; initial proposed state `SUBMITTED` | PR #76; BR-IT-01–04; #53 |
| FR-02 | Only visible requests can be listed or opened | Requester cannot enumerate hidden or cross-tenant requests; denial semantics verified on Backend | BR-IT-11–14; #23 |
| FR-03 | Authorized request detail and history can be viewed | Only permitted state/history/business fields returned, with a consistent ordering contract | PR #76; #22/#23; field visibility OPEN |
| FR-04 | Eligible coordinator triages a submitted request | Authorized `SUBMITTED → TRIAGED` succeeds once; invalid/stale transition creates no change | BR-IT-05/10/18; #53 |
| FR-05 | Eligible coordinator assigns a technician | Assignment uses **Application command** and assignment version, not Workflow transition/version | BR-IT-06/15/18; PR #63 |
| FR-06 | Current eligible assignee starts work | Authorized `TRIAGED → IN_PROGRESS`; former/revoked assignee denied | BR-IT-07/15; #23 |
| FR-07 | Assigned technician records a valid resolution | Nonblank resolution business fact and authorized `IN_PROGRESS → RESOLVED`; failure does not falsely succeed | BR-IT-08/16/17/21; #53 |
| FR-08 | Authorized original requester confirms resolution | `RESOLVED → CONFIRMED`; other users rejected; does **not** imply production `CLOSED` | BR-IT-09; Issue #53 OPEN |
| FR-09 | Unsupported or stale transitions are rejected | No unauthorized state/history writes after conflict or invalid action; separate version checks preserved | BR-IT-10/18; #25/#27 |
| FR-10 | Application presents safe and truthful outcomes | Explicit 401/403/hidden-404/conflict/unknown commit response; no false success or blind retry | BR-IT-14/19/20; #23/#27 |

The existing `FR-01`–`FR-10` IDs are retained from PR #76. Reviewers must confirm criteria against the authoritative contracts before accepting them.

## Proposed Workflow and Business Ownership

**IT integration candidate:** `SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`.

| Action | Ownership | Consistency condition |
| --- | --- | --- |
| Triage, start work, resolve, confirm | Authorized Workflow transition coordinated by Backend Application | Workflow instance `expectedVersion` |
| Assign or reassign technician | Application business operation | Separate `assignment_version` |
| Store IT request details and resolution facts | Application | Validate business facts and transaction boundary |
| Store authoritative workflow state and transition history | Workflow Core / persistence port | Exact version and append history on successful transition |

Do **not** treat assignment as a `TRIAGED → ASSIGNED` Workflow transition, directly set status, or assert atomicity between PostgreSQL and MinIO. A decision on post-confirmation `CLOSED` remains open.

## Nonfunctional and Security Requirements

| ID | Requirement | Verification candidate |
| --- | --- | --- |
| NFR-IT-01 | Use verified OIDC identity for LIVE actions | Real token signature, issuer/audience and server principal tests |
| NFR-IT-02 | Enforce explicit operation grants and resource/tenant visibility server-side | 401, visible-but-forbidden 403, hidden/nonexistent 404, IDOR/BOLA negatives |
| NFR-IT-03 | Reject invalid/stale versions safely | Concurrent-write and replay/conflict tests |
| NFR-IT-04 | Maintain durable workflow state/history | PostgreSQL persistence and restart/recovery tests |
| NFR-IT-05 | Never retry uncertain writes blindly | Lost-response/`COMMIT_OUTCOME_UNKNOWN` reconciliation evidence |
| NFR-IT-06 | Avoid secrets or privileged direct data access in browser | Client bundle/config and network inspection |
| NFR-IT-07 | Reproducible isolated local verification | Clean environment steps, source SHA and test report |

These are proposed quality requirements, **not measured compliance claims**. Quantitative performance thresholds remain **BASELINE UNKNOWN / POLICY OPEN**.

## Error and Alternate-Path Coverage

| Scenario | Required safe behavior | Current contract disposition |
| --- | --- | --- |
| Missing/invalid authentication | 401; no protected information | Requires LIVE test evidence |
| Visible resource, operation forbidden | 403 without mutation | Requires LIVE test evidence |
| Hidden, cross-tenant or nonexistent resource | Non-disclosing 404 where applicable | Requires LIVE test evidence |
| Invalid transition / stale version | Reject without state/history corruption | Requires LIVE test evidence |
| Duplicate action or lost response | Do not claim success; reconcile before retry | Recovery protocol open |
| Assignment revoked / reassigned | Former technician cannot perform assignee-only action | Policy/test evidence pending |
| Wrong-scope request / rejection reason | No invented supported rejection transition | Business rule open |
| Requester disagreement / rework | No invented return path | Business rule open |
| Closure after confirmation | No assumed `CLOSED` implementation | Scope and contract gap |
| Attachment failure | No false attachment reference or client secrets | Protected API blocked until verification |

## API Capability and Evidence Boundary

The Portal may call only **published** and **authorized** Backend APIs. Do not infer that listing, assignment, attachment operations or every use case is supported merely because a Workflow create/transition endpoint exists. For every HTTP operation, the API-contract reviewer must record `ACTIVE`, `PLANNED` or `BLOCKED` on a **pinned revision with runtime evidence**. This document assigns **no `ACTIVE` status**.

The PR #76 text reports a `MockRequestService` Portal demonstration and a Backend candidate in PR #63. Those claims are documentation context rather than independently verified runtime acceptance in this review. Evidence for identity, gateway, data, transactions, real Portal HTTP and clean deployment must be linked separately.

## Requirement-to-Verification Traceability

| Coverage | Requirement IDs | Candidate evidence |
| --- | --- | --- |
| Creation and validation | FR-01; NFR-IT-01 | Authorized create and invalid-input tests |
| Visibility/history | FR-02, FR-03; NFR-IT-02 | 401/403/404 and field/history access tests |
| Workflow state machine | FR-04, FR-06–FR-09; NFR-IT-03–04 | Transition, stale version, persistence/history tests |
| Business assignment | FR-05; NFR-IT-03 | Assignment version and revoked eligibility tests |
| Truthful UI and failure recovery | FR-10; NFR-IT-05–06 | Unknown commit, no blind retry, browser mock/LIVE distinction |
| Full E2E and reuse proof | All applicable | Exact-sha live HTTP IT journey; separate Core reuse test with second definition |

## Open Decisions and Review Dependencies

| ID | Unresolved decision or evidence | Why it matters | Source |
| --- | --- | --- | --- |
| G2-HD-OD-01 | `CONFIRMED` vs later `CLOSED` acceptance | G1 scope and current IT candidate differ | PR #76; G1 Scope & MVP; #53 |
| G2-HD-OD-02 | Wrong-scope rejection and rework | Missing supported transitions/business rules | #53; PR #76 |
| G2-HD-OD-03 | Actor/field/history visibility policy | Prevent unauthorized data disclosure | #23; #53 |
| G2-HD-OD-04 | Published list/assign/attachment HTTP contracts | Portal cannot invent routes | #22/#37/#39; PR #76 |
| G2-HD-OD-05 | Transaction and unknown-commit recovery policy | Prevent inconsistent writes and unsafe replay | #25/#27/#53 |
| G2-HD-OD-06 | Independent E2E/reuse acceptance | MUST and design do not prove runtime DONE | #27/#39/#40 |

These IDs are **local review candidates**, not confirmed canonical Open Issue Register IDs. Record dispositions in the approved decision and issue registers after review.

## References

- [Source PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) — requirements and Application documentation drafts.
- [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) — draft business rules and API contract.
- [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) — separate Portal consumer and interface boundaries.
- [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) — official local demo target and verification gaps.
- [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) — cited Backend candidate; no acceptance claimed here.

**Review disposition: PROPOSED REVISION / NOT REVIEWED.** Do not replace PR #76's source document until reviewed on the chosen branch and baseline.
