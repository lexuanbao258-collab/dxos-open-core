# IT Helpdesk — Business Rules

**Document ID:** G2-F03  
**Purpose:** Specify reviewable IT Helpdesk business rules and their verification boundaries.  
**Scope:** IT-focused MVP candidate only; no approval of production endpoints or policies.

> This is a **proposed revision** of the draft in [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76), inspected at head `7022de6d3077015cc6a23c75f5126e025574b026`. The canonical document lifecycle status belongs in the Document Register. This draft has not received independent acceptance.

## 1. Context and Business Actors

**Architecture boundary (TO-BE / PROPOSED):** Helpdesk rules decide who may create, assign, progress, read, and confirm a request. Core supplies reusable principal, process, data, and API contracts; provider adapters do not own these business decisions. A future Flowable engine must preserve the Workflow contract rather than redefining these rules, and APISIX edge policy cannot authorize a ticket resource. No Flowable or APISIX integration is claimed by this business-rule draft.

The Internal Service Portal is intended to let authorized people create, triage, assign, work on, resolve and confirm IT service requests. Its representative organization and user pain points remain hypotheses rather than validated customer research. The Application owns IT-specific data and rules, while Workflow Core owns authoritative state, instance version and transition history.

| Actor | Business responsibility | Authorization qualification |
| --- | --- | --- |
| Requester | Create and track requests; confirm reported resolution | The original requester is identified from trusted Backend context and must be permitted on the resource |
| IT Coordinator | Triage and assign eligible technicians | Eligibility, exact operation authority and resource visibility are checked server-side |
| Assigned Technician | Start work and record resolution | Current assignment alone is insufficient without applicable authorization |
| System Administrator | Operate/configure infrastructure | Administrative status does not automatically grant request-business permissions |

Identity/Gateway and Workflow Core are supporting system components, not human business actors. A business role is not an API authority or resource grant.

## 2. Candidate Lifecycle and Ownership

The current Backend candidate proposes:

```text
SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED
```

`CONFIRMED` is the endpoint of this **candidate**; a production `CLOSED` action is not agreed. The `triage`, `start_work`, `resolve` and `confirm` actions are proposed Workflow transitions. `assign` is a **separate Application command**, not a Workflow transition; it uses `assignment_version`, not Workflow `expectedVersion`. No transition key or endpoint is asserted ACTIVE by this document.

| Business information | Authoritative owner |
| --- | --- |
| Workflow state, instance version and successful transition history | Workflow Core |
| Request title, description, assignment and resolution facts | IT Helpdesk Application |
| Verified subject and security claims | Trusted Identity/Backend authorization boundary |
| Persistent records and storage | Approved data adapters; no direct Portal database access |

## 3. Request Creation Rules

| ID | Rule | Verification candidate |
| --- | --- | --- |
| BR-IT-01 | A new request requires a nonblank title and description and any subsequently approved required fields. | Empty/whitespace inputs rejected; no request written |
| BR-IT-02 | A LIVE request can be created only for a verified requester with required operation authority, tenant membership and resource/create eligibility. | Missing/invalid principal or grant denied server-side |
| BR-IT-03 | The requester identity is derived from the trusted Backend security context; client-supplied identity, role and forwarded headers cannot override it. | Forged user/role headers do not change creator identity |
| BR-IT-04 | A successful authorized creation in the candidate binds the request to a Workflow instance initially in `SUBMITTED`; Application must not fabricate the state. | Request and Workflow binding consistent; failed create has no misleading success |

The exact create-binding contract and response DTO remain dependent on accepted Backend/API evidence.

## 4. Workflow and Assignment Rules

| ID | Rule | Preconditions and observable outcome |
| --- | --- | --- |
| BR-IT-05 | An eligible coordinator may execute `triage`. | Visible request in `SUBMITTED`; authority and expected version valid; resulting candidate state `TRIAGED` |
| BR-IT-06 | An eligible coordinator may assign a technician via a separate Application command. | Valid assignee, resource grant and `assignment_version`; no fabricated Workflow transition |
| BR-IT-07 | Only a currently eligible assigned technician may start work. | Check current assignment, operation grant, visibility and `expectedVersion`; candidate `TRIAGED → IN_PROGRESS` |
| BR-IT-08 | A currently authorized assigned technician must provide a nonblank resolution before `resolve`. | No resolution means no successful `IN_PROGRESS → RESOLVED` transition |
| BR-IT-09 | Only the authorized original requester may confirm the resolution. | Original subject matches; resource visible; expected version valid; candidate `RESOLVED → CONFIRMED` |
| BR-IT-10 | Unsupported or invalid transitions must be rejected without mutating state or successful-transition history. | Invalid-state / wrong-actor negative test; history unchanged |

Triage and assignment can succeed or fail independently under their *documented* operation and transaction boundaries. Do not claim one atomic business operation unless a pinned integration test proves it.

## 5. Identity, Authorization and Visibility Rules

| ID | Rule | Verification candidate |
| --- | --- | --- |
| BR-IT-11 | The Backend requires a verified principal for every protected operation. | Absent, invalid or unverifiable authentication rejected (`401` where applicable) |
| BR-IT-12 | Every protected action requires applicable exact authority, actor eligibility and resource/tenant authorization. | Cross-resource/tenant, forged authority and former-assignee attempts denied |
| BR-IT-13 | Request details, list results and history events are returned only if authorized by current visibility policy. | Hidden resource details and event data are not disclosed; field-level policy remains open |
| BR-IT-14 | Expected denial semantics distinguish unauthenticated `401`, visible-but-forbidden `403`, and nonexistent/hidden `404` without leaking existence. | Backend HTTP tests for each category; no assertion of deployment pass |
| BR-IT-15 | A previous assignee loses technician-only eligibility upon reassignment or revocation. | Existing session/token cannot act solely on former assignment |

The final policy for field-level redaction, event visibility, coordinator scope and tenant membership requires independent security review; UI button visibility is only a UX affordance.

## 6. Ownership, Consistency and Recovery Rules

| ID | Rule | Verification candidate |
| --- | --- | --- |
| BR-IT-16 | Workflow Core owns instance state, expected version and successful transition history. | No Application direct status mutation; read history agrees with state |
| BR-IT-17 | Application owns request details, assignment and resolution facts. | Business writes remain within authorized Application operations |
| BR-IT-18 | Workflow transitions compare `expectedVersion`; assignment commands independently compare `assignment_version`. | Stale or concurrent writers rejected without lost updates |
| BR-IT-19 | The UI should mitigate accidental duplicates; the Backend must independently prevent unsafe duplicate effects. | Double click / duplicate request scenarios; do not presume an idempotency contract exists |
| BR-IT-20 | When a write outcome is unknown, neither success nor rollback may be assumed; do not blindly retry without approved reconciliation. | Lost response or `COMMIT_OUTCOME_UNKNOWN` handled as indeterminate until safely checked |
| BR-IT-21 | Business facts and Workflow state/history must respect verified transaction boundaries; do not assert distributed atomicity with object storage. | Failure injection, rollback and consistency evidence at exact code/database baseline |

Attachment upload is not a LIVE capability merely because an ObjectStoragePort/MinIO plan exists. No privileged Portal access to PostgreSQL or MinIO is permitted.

## 7. Scenario-to-Rule and Requirement Traceability

| Use case | Requirement candidates | Business rules | Required evidence before LIVE claim |
| --- | --- | --- | --- |
| UC-IT-01 — Submit | FR-01, FR-02, FR-10 | BR-IT-01–04, BR-IT-11–14, BR-IT-19–21 | Authorized creation, identity binding, validation, duplicate/unknown-write negatives |
| UC-IT-02 — Triage and Assign | FR-04, FR-05, FR-09 | BR-IT-05–06, BR-IT-10–15, BR-IT-18 | Separate version checks, current coordinator/assignee authority, transition correctness |
| UC-IT-03 — Perform Work | FR-06, FR-07, FR-09 | BR-IT-07–08, BR-IT-10–21 | Eligible assignee, required resolution, concurrency and rollback tests |
| UC-IT-04 — Confirm | FR-08, FR-09 | BR-IT-09–14, BR-IT-16, BR-IT-18 | Original requester authorization, version test, correct terminal candidate state |
| Authorized reading/history | FR-02, FR-03 | BR-IT-11–14, BR-IT-16 | No cross-tenant/hidden history disclosure |

FR and UC references are **draft IDs** from the source PR #76 and proposed G2 revisions. A table entry is not evidence that an endpoint exists or a test has passed.

## 8. Negative and Exceptional Scenarios

| Test ID | Scenario | Expected rule-level outcome | Acceptance status |
| --- | --- | --- | --- |
| NEG-IT-01 | Blank required fields | Validation denial, no create | Not verified here |
| NEG-IT-02 | No/invalid authentication | `401`, no protected action | Not verified here |
| NEG-IT-03 | Visible request, forbidden action | `403`, no mutation | Not verified here |
| NEG-IT-04 | Hidden/cross-tenant request | `404` without existence disclosure | Not verified here |
| NEG-IT-05 | Wrong actor / revoked assignment | No transition or assignment write | Not verified here |
| NEG-IT-06 | Stale workflow version | Conflict, no lost update | Not verified here |
| NEG-IT-07 | Stale assignment version | Independent assignment conflict | Not verified here |
| NEG-IT-08 | Invalid-state transition | Rejected, no successful transition history | Not verified here |
| NEG-IT-09 | Duplicate click/request | No unintended repeat effects | Not verified here |
| NEG-IT-10 | Lost write response / unknown commit | Indeterminate result surfaced; no blind retry | Not verified here |
| NEG-IT-11 | Missing resolution | Cannot resolve successfully | Not verified here |
| NEG-IT-12 | Rejection, rework, escalation or closure | Safely unsupported until business/API contract ratified | Policy open |

`401/403/404` are target HTTP semantics, not universal promises for every UI mock or unexposed endpoint.

## 9. Open Decisions and Scope Gaps

| Finding | Unresolved question | Needed decision / evidence | Source |
| --- | --- | --- | --- |
| G2-BR-OD-01 | Wrong-scope rejection and reasons | Business rules, authorized action and API design | [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| G2-BR-OD-02 | Requester disagreement and rework | State/actor policy, acceptance scenarios | [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| G2-BR-OD-03 | Unresolved incidents and escalation | Explicit handling and scope classification | [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) |
| G2-BR-OD-04 | `CONFIRMED` versus G1 `CLOSED` goal | Reconcile MVP acceptance and any future closure action; no silent new transition | [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| G2-BR-OD-05 | Final operation grants and field/history visibility | Independent security/contract review | [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23) |
| G2-BR-OD-06 | Exact write reconciliation and atomicity | Verified Backend/database behavior at pinned SHA | [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| G2-BR-OD-07 | Attachment and object-storage lifecycle | Published protected API and separate acceptance | [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |

The IDs above are local proposed review findings, not confirmed entries in the authoritative Open Issues Register. Ratification of Issue #53's business contract is still required.

## 10. Evidence and Acceptance Boundary

A rule is **specified** when its behavior is written here. It is **accepted as business policy** only when reviewed by the designated decision authority. It is **implemented** only where traceable source at an exact SHA supports it. It is **verified LIVE** only when real authorized Backend HTTP and persistence tests pass with independent review.

The PR #76 Portal description includes `MockRequestService` with synthetic records; mock behavior is neither live Identity verification nor persisted Workflow evidence. PRs #63 and #71 are cited as integration candidates, not accepted deployment proof.

This file can be approved as *documentation* independently of a later LIVE implementation decision, but it must not present unratified business rules as accepted runtime contracts.

## 11. References

- [PR #76 — Application documentation draft](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) — source revision `7022de6d3077015cc6a23c75f5126e025574b026`.
- [Issue #53 — Business and API contract](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53).
- [Issue #39 — Portal consumer](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39).
- [Issue #40 — Local deployment and E2E criteria](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40).
- [PR #63 — Backend candidate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63).
- [PR #71 — Portal candidate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/71).

**Proposed revision — independent review and Git baseline acceptance pending.**
