# Facility Maintenance — Business Rules

> **Document ID:** G2-F06  
> **Purpose:** Specify candidate Facility Maintenance rules and separate mandatory safety constraints from unresolved business policy.  
> **Scope:** Internal Service Portal Facility scenario, requirements FR-FAC-01–10 and use cases UC-FAC-01–05.  
> **Governance:** Document lifecycle and ownership are authoritative in the Document Register. This is an unreviewed proposal, not a production contract.

## 1. Scope and Evidence Boundary

Facility Maintenance is a **deferred full business journey** in the IT-first MVP. A narrower second-definition demonstration is a reuse-proof objective, not proof that a complete Facility Backend exists. The Facility Portal flow described by [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) uses mock data; neither a mock nor this document is evidence of LIVE security, persistence or end-to-end acceptance.

The proposed reference workflow is:

`SUBMITTED → ACCEPTED → ASSIGNED → IN_REPAIR → AWAITING_ACCEPTANCE → CLOSED`

**PROPOSED / CONFLICTED — NOT RATIFIED, NOT LIVE.** The treatment of assignment, the acceptance actor, acceptance versus closure, rework and rejection remains unsettled. The table is a reference for analysis only; no API, transition key, or implementation is authorized by it.

Rule classifications used here:

- **SAFETY CONSTRAINT:** A necessary security or consistency requirement for any future LIVE behavior, subject to implementation verification.
- **BUSINESS PROPOSAL:** A candidate business condition requiring business-owner review.
- **POLICY OPEN:** Behavior or eligibility is not determined; the proposed action must not be assumed supported.
- **DEFERRED:** Out of the initial IT-first Facility LIVE scope.

These are **rule qualifications**, not Document Register statuses or claims of current implementation.

## 2. Business Actors and Ownership

| Actor | Candidate responsibility | Authorization caveat |
| --- | --- | --- |
| Requester | Reports a facility problem and views permitted updates | Identity and per-resource visibility required |
| Facility Coordinator | Reviews and coordinates eligible requests | Coordinator title is not an API grant |
| Assigned Technician | Performs and records repair work | Current assignment and explicit permissions required |
| Acceptance Actor | Reviews the outcome | **POLICY OPEN:** requester versus inspector/authorized delegate |
| System Operator | Operates deployment and identity configuration | No implicit ability to alter business workflow |

**Core/Application boundary:** Workflow Core owns authoritative state, immutable definition binding, instance version and successful transition history. Facility Application owns request details, location, assignment, repair/acceptance business facts and their validations. The Portal uses authorized published HTTP contracts; it must never mutate Core state or directly access privileged database/storage credentials.

## 3. Creation and Request Information

| Rule | Qualification | Candidate rule | Linked requirement / use case |
| --- | --- | --- | --- |
| BR-FAC-01 | BUSINESS PROPOSAL | A new request records a nonblank title, issue description and location under an agreed validation policy. | FR-FAC-01/02; UC-FAC-01 |
| BR-FAC-02 | SAFETY CONSTRAINT | The Backend derives requester identity from a verified principal, not caller-provided identity headers or fields. | FR-FAC-01/09; UC-FAC-01 |
| BR-FAC-03 | SAFETY CONSTRAINT | Creation requires current actor eligibility, appropriate operation authority and applicable tenant/resource bindings. | FR-FAC-01/09; UC-FAC-01 |
| BR-FAC-04 | BUSINESS PROPOSAL | Upon a successful, approved creation contract, a Facility request is bound to an appropriate Workflow instance; its initial business state remains subject to ratified definition. | FR-FAC-01; UC-FAC-01 |
| BR-FAC-05 | POLICY OPEN | Out-of-scope requests, missing mandatory facts and their rejection reasons require a business policy. No unsupported rejection transition may be invented. | FR-FAC-02/05; UC-FAC-01/02 |
| BR-FAC-06 | SAFETY CONSTRAINT | Supporting photographs are optional unless a later approved policy says otherwise; do not claim upload succeeds or retain invalid references without an authorized storage API. | FR-FAC-02/10; UC-FAC-01/04 |

## 4. Intake, Assignment, Repair and Acceptance

| Rule | Qualification | Candidate rule | Linked requirement / use case |
| --- | --- | --- | --- |
| BR-FAC-07 | BUSINESS PROPOSAL | An eligible Facility Coordinator reviews a visible submitted request for processing suitability. | FR-FAC-05; UC-FAC-02 |
| BR-FAC-08 | POLICY OPEN | The exact `accept` event, its actor eligibility, required data and resulting state must be ratified before any LIVE transition is exposed. | FR-FAC-05; UC-FAC-02 |
| BR-FAC-09 | BUSINESS PROPOSAL | A coordinator selects an eligible technician, and the Backend validates eligibility at the time of assignment. | FR-FAC-06; UC-FAC-03 |
| BR-FAC-10 | POLICY OPEN | Whether `ASSIGNED` is an actual Workflow state/transition or assignment is a separate Application command is undecided. Do not copy IT Helpdesk semantics by assumption. | FR-FAC-06; UC-FAC-03 |
| BR-FAC-11 | SAFETY CONSTRAINT | A technician who loses assignment or permission cannot continue protected technician-only operations on the former basis. | FR-FAC-06/09; UC-FAC-03/04 |
| BR-FAC-12 | BUSINESS PROPOSAL | An authorized assigned technician records accurate repair facts and a nonempty outcome when required by the approved repair contract. | FR-FAC-07; UC-FAC-04 |
| BR-FAC-13 | POLICY OPEN | Failed repair, blocked work, incomplete outcome and escalation require explicit outcomes and allowed actions; they must not be reported as successful repair. | FR-FAC-07/09; UC-FAC-04 |
| BR-FAC-14 | POLICY OPEN | The final Acceptance Actor, evidence requirements, approve/reject semantics and field visibility require ratification. | FR-FAC-08; UC-FAC-05 |
| BR-FAC-15 | POLICY OPEN | Acceptance versus closure, and any rework after failed acceptance, must be modeled explicitly. `CLOSED` must not be treated as an automatic side effect. | FR-FAC-08; UC-FAC-05 |
| BR-FAC-16 | SAFETY CONSTRAINT | No Application operation directly changes Workflow state outside a verified, authorized Core transition contract. | FR-FAC-05–09; UC-FAC-02–05 |

## 5. Security, Visibility and Concurrency

| Rule | Qualification | Required behavior for a future LIVE implementation | Linked requirement / use case |
| --- | --- | --- | --- |
| BR-FAC-17 | SAFETY CONSTRAINT | Authenticate on the Backend using verified OIDC identity; never trust arbitrary `X-User`/`X-Role` or client role selection. | FR-FAC-01/09; UC-FAC-01–05 |
| BR-FAC-18 | SAFETY CONSTRAINT | Check exact required operation authority **and** relevant tenant/resource authorization and business actor eligibility for the actual target object. | FR-FAC-03/09; UC-FAC-01–05 |
| BR-FAC-19 | SAFETY CONSTRAINT | Enforce visibility before revealing protected request details or existence: unauthenticated 401, visible-but-forbidden 403, hidden/nonexistent 404, subject to approved API contract. | FR-FAC-03/09; UC-FAC-02–05 |
| BR-FAC-20 | POLICY OPEN | Final field/event-level visibility for internal notes, location details, attachments and repair evidence must be defined. Do not expose fields by default. | FR-FAC-03/04; UC-FAC-01–05 |
| BR-FAC-21 | SAFETY CONSTRAINT | Mutations reject invalid transitions and stale expected Workflow versions without writing an unauthorized state/history entry. | FR-FAC-09; UC-FAC-02–05 |
| BR-FAC-22 | POLICY OPEN | If assignment is an independent Application write, its concurrency/version policy and consistency coordination must be ratified; do not presume IT's `assignment_version` schema is shared. | FR-FAC-06/09; UC-FAC-03 |
| BR-FAC-23 | SAFETY CONSTRAINT | A duplicate or uncertain write must not be blindly replayed or falsely reported successful. Resolve `COMMIT_OUTCOME_UNKNOWN` only through a reviewed reconciliation method. | FR-FAC-10; UC-FAC-01–05 |
| BR-FAC-24 | SAFETY CONSTRAINT | The system must respect actual PostgreSQL transaction boundaries; no unsupported claim of atomicity spanning database and MinIO. | FR-FAC-09/10; UC-FAC-01–05 |
| BR-FAC-25 | SAFETY CONSTRAINT | A MOCK/DEVELOPMENT Portal action cannot be represented as LIVE end-to-end evidence; unsupported APIs remain unavailable until reviewed. | FR-FAC-09/10; UC-FAC-01–05 |

## 6. Candidate Workflow Rule Matrix — Not an Executable Contract

| Candidate action | Actor candidate | Business prerequisites | Core/Application semantics | Approval state |
| --- | --- | --- | --- | --- |
| Submit | Requester | Verified identity, valid issue/location | Application create + reviewed Workflow binding | Proposed |
| Intake / accept | Facility Coordinator | Visible request, eligible scope | Workflow transition **if ratified** | Policy open |
| Assign | Facility Coordinator | Eligible current technician | Workflow versus Application semantics **unresolved** | Policy open |
| Begin repair | Assigned Technician | Current assignment and authority | Workflow transition **if ratified** | Proposed |
| Report repair | Assigned Technician | Reviewed outcome evidence | Application facts + authorized transition if defined | Proposed |
| Accept result | Acceptance Actor **TBD** | Reviewed repair outcome | Transition semantics **unresolved** | Policy open |
| Close | Actor **TBD** | Acceptance/closure policy **TBD** | No assumption of implicit closure | Policy open |
| Reject/rework/escalate | Actor **TBD** | Approved alternate path **TBD** | Must not fabricate transition | Deferred / policy open |

The matrix is intentionally **not** an authorization policy, an OpenAPI route list or an active Workflow Definition.

## 7. Negative Scenarios and Verification Proposals

| Test proposal | Scenario | Expected safe outcome | Evidence needed |
| --- | --- | --- | --- |
| NEG-FAC-01 | Missing/invalid token | 401, no protected data or write | Backend HTTP/security test |
| NEG-FAC-02 | Visible request but insufficient action grant | 403, no mutation | Backend authorization test |
| NEG-FAC-03 | Hidden/cross-tenant request | 404 without existence disclosure | Tenant/resource isolation test |
| NEG-FAC-04 | Forged principal header or UI role | No privilege escalation | Security negative test |
| NEG-FAC-05 | Stale expected version | Conflict/rejection, unchanged state/history | PostgreSQL concurrency test |
| NEG-FAC-06 | Revoked technician assignment | Technician-only action rejected | Assignment revocation test |
| NEG-FAC-07 | Unsupported acceptance/rework/closure | Safe rejection, no invented transition | Contract/negative test |
| NEG-FAC-08 | Duplicate submission / lost response | No blind retry or false success | Transaction/reconciliation test |
| NEG-FAC-09 | Missing required location/repair facts | Validation rejection | Application contract test |
| NEG-FAC-10 | Attachment API absent/fails | No false upload success/reference | Backend/storage boundary test |
| NEG-FAC-11 | Unauthorized history or internal note | No sensitive disclosure | Field/event visibility test |
| NEG-FAC-12 | Database write succeeds but object write fails | No claim of distributed atomic commit | Recovery/design verification |

Test IDs are **draft proposals**, not test executions or a repository-confirmed naming namespace.

## 8. Requirement and Use-Case Traceability

| Need / Requirement | Use case | Business rules | Verification proposal |
| --- | --- | --- | --- |
| FR-FAC-01/02 — Report issue/location | UC-FAC-01 | BR-FAC-01–06 | NEG-FAC-01, 04, 09 |
| FR-FAC-03/04 — Authorized visibility/history | UC-FAC-01–05 | BR-FAC-17–20 | NEG-FAC-02, 03, 11 |
| FR-FAC-05 — Intake | UC-FAC-02 | BR-FAC-07/08/16 | NEG-FAC-05, 07 |
| FR-FAC-06 — Assignment | UC-FAC-03 | BR-FAC-09–11/22 | NEG-FAC-05, 06 |
| FR-FAC-07 — Repair outcome | UC-FAC-04 | BR-FAC-12/13 | NEG-FAC-06, 09 |
| FR-FAC-08 — Acceptance | UC-FAC-05 | BR-FAC-14/15 | NEG-FAC-07 |
| FR-FAC-09/10 — Safe rejection and errors | UC-FAC-01–05 | BR-FAC-16–25 | NEG-FAC-01–12 |

No proposed rule is accepted merely because a requirement or use case refers to it.

## 9. Open Business Decisions

| ID | Decision required | Why it matters | Review route |
| --- | --- | --- | --- |
| FAC-OD-01 | Authorized Acceptance Actor | Prevent unauthorized acceptance | Business owner + Identity/Security |
| FAC-OD-02 | Acceptance vs. Closure actions/states | Determines final Workflow and audit events | Business owner + Workflow reviewer |
| FAC-OD-03 | Assignment as Workflow transition or Application command | Determines consistency and version policy | Business owner + Core/Application reviewers |
| FAC-OD-04 | Rejection and required reasons | Determines alternate paths and invalid-state handling | Business owner + Application |
| FAC-OD-05 | Failed repair, rework and escalation | Prevents false success and missing paths | Business owner + Application |
| FAC-OD-06 | Mandatory repair/acceptance evidence | Affects storage, privacy and validation | Business owner + Security |
| FAC-OD-07 | Field/event visibility and resource grants | Affects data exposure | Identity/Security + Application |
| FAC-OD-08 | Final API/DTO, transaction and concurrency contract | Needed for LIVE integration | Core + Application + Security |
| FAC-OD-09 | Release and reuse-proof depth | Distinguishes a second definition proof from full Facility E2E | Gate owner + Application |

The IDs above are **local draft decision identifiers**, not approved ADRs or canonical F05 Open Issue IDs. Reconcile them into approved issue/decision records as appropriate.

## 10. Acceptance and Change Control

Document review requires: completeness of rules and linked FR/UC IDs; no invented authority/transition/API; complete qualification of policy gaps; review of the Core/Application boundary; and a consistent treatment of mock, deferred and LIVE evidence.

**LIVE acceptance additionally requires** ratified business policy and Workflow Definition, reviewed API and data contracts, security and concurrency negative tests at exact source SHAs, independent review and an authorized Gate decision. No such result is claimed here.

Changes to acceptance actor, assignment semantics, workflow final state, visibility or transaction behavior require impact analysis across Facility Requirements, Use Cases, Workflow Definition, API Contract and test evidence.

## 11. References and Verification Limits

- [PR #76 — Application documentation drafts (specific head SHA: `7022de6d3077015cc6a23c75f5126e025574b026`)](https://github.com/lexuanbao258-collab/dxos-open-core/tree/7022de6d3077015cc6a23c75f5126e025574b026/docs/03-applications/facility)
- [Issue #39 — Independent Portal boundary](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39)
- [Issue #53 — Draft business contract](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53)
- [Issue #40 — Local MVP and demo scope](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40)
- [PR #60 — Business/API contract draft](https://github.com/lexuanbao258-collab/dxos-open-core/pull/60)
- [PR #71 — Portal integration candidate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/71)

The source files above describe **proposals**, not verified production behavior. Related local G1 drafts and this document must be linked by relative paths only after the target documentation files are confirmed to exist in the same reviewed Git baseline.
