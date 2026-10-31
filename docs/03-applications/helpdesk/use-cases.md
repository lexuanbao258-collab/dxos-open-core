# IT Helpdesk — Application Use Cases

**Document ID:** G2-F02  
**Purpose:** Specify reviewable candidate IT Helpdesk user journeys and verification obligations.  
**Scope:** Internal Service Portal, first IT-focused MVP candidate; not a LIVE acceptance statement.

## Context and Evidence Boundary

This proposed revision preserves the four use cases (`UC-IT-01` to `UC-IT-04`) from [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76), reviewed at head commit [`7022de6d3077015cc6a23c75f5126e025574b026`](https://github.com/lexuanbao258-collab/dxos-open-core/commit/7022de6d3077015cc6a23c75f5126e025574b026). PR #76 was open/unmerged when inspected. It is not a ratified business specification. [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) labels Business Contract v1.2 a draft awaiting ratification; the Portal's mock behavior is not proof of real Backend operation.

The first IT candidate uses `SUBMITTED → TRIAGED → IN_PROGRESS → RESOLVED → CONFIRMED`. **`CONFIRMED` is not `CLOSED`.** A post-confirmation close, rejection, rework and escalation are unresolved or out of this first candidate. These candidate state/action names must be checked against the exact reviewed Workflow Definition and API before any LIVE claim.

## Actors and Responsibilities

| Actor / participant | Responsibility and limit |
| --- | --- |
| Requester | Create and view authorized requests; original requester may confirm when the Backend permits it |
| IT Coordinator | Triage and separately assign eligible technicians; business role alone grants no authority |
| Assigned Technician | Perform work and resolve when currently assigned and authorized |
| System Administrator | Infrastructure administration under separately approved policy, **not** automatic business permission |
| Identity / Gateway / Backend | Establish trusted principal and enforce exact operation and resource-level permissions |
| Workflow Core | Own definition, authoritative instance state, version, permitted transitions and history |
| Helpdesk Application | Own request details, business validation, assignment, resolution facts and user-facing orchestration |

The final actor/authority/visibility matrix remains open for ratification. UI visibility or selecting a role is never sufficient authorization.

## Shared Invariants and Preconditions

- A LIVE call requires verified identity, applicable exact operation authority, tenant/resource visibility and actual-resource authorization. A hidden resource must not be disclosed.
- A Workflow transition uses its expected Workflow instance version. Assignment updates use a **separate assignment version**; they must not be conflated.
- A failed or unauthorized request must not cause successful business or Workflow state writes.
- Any uncertain write (`COMMIT_OUTCOME_UNKNOWN` or lost response) requires safe read/reconciliation where supported; the client must not blindly retry or report success.
- The Application does not directly change Core's state or history; it calls authorized published Backend operations. The Portal must not access Core Java services, JDBC or privileged storage credentials.
- Any reference to a real endpoint, exact error code or atomic transaction requires source/test evidence at a pinned revision; this document does not assert such acceptance.

## UC-IT-01 — Submit an IT Support Request

**Primary actor:** Requester. **Trigger:** An employee needs IT assistance.

**Preconditions:** The actor has an authenticated session and is eligible to create an IT request; LIVE server-side permissions are satisfied. Required fields and exact endpoint availability must be validated against the approved contract.

**Main success flow (candidate):**

1. The requester opens the Portal and selects IT Helpdesk.
2. The requester supplies a nonblank title, description and other approved required fields.
3. The Application validates the input and sends a request through its authorized Backend API.
4. The Backend derives requester identity from its verified principal, checks creation authority, and coordinates business record and Workflow instance creation/activation according to the reviewed transaction contract.
5. The Workflow instance reaches the candidate `SUBMITTED` state; authoritative identifiers and state come from the Backend response.
6. The Portal presents the returned status without inventing a successful result.

**Postconditions on confirmed success:** An authorized request and corresponding authoritative Workflow state exist consistently; no duplicate success is inferred from a timeout.

**Alternatives and exceptions:** Invalid fields → reject without a write; no valid authentication → `401`; creation denied → safe denial subject to published policy; lost response/unknown commit → outcome unknown, reconcile without blind retry; an unsupported API → `BLOCKED`, not a mock-backed LIVE claim.

**Test candidates:** `TC-IT-01-VALID`, `TC-IT-01-INVALID`, `TC-IT-01-DENIED`, `TC-IT-01-UNKNOWN`.

## UC-IT-02 — Triage and Assign

**Primary actor:** IT Coordinator. **Trigger:** An authorized submitted request requires initial assessment and assignment.

**Preconditions:** An existing visible request is in `SUBMITTED`; the coordinator is authorized for the requested actions, and expected versions are available.

**Main success flow (candidate):**

1. The coordinator opens an authorized request and reviews its submitted facts.
2. The Backend validates resource visibility, actor eligibility, exact transition authority and expected Workflow version.
3. The coordinator requests `triage`; Workflow Core advances `SUBMITTED → TRIAGED`, recording its transition history.
4. The coordinator selects an eligible technician and sends a **separate Application `assign` command**.
5. The Backend checks assignment eligibility and assignment version, then records the assignment as an Application fact.
6. The Portal renders the authoritative Workflow state and assignee information returned by supported APIs.

**Postconditions on confirmed success:** The request is `TRIAGED`, with an authorized assigned technician; assignment has its own version/history semantics as defined by the approved Application contract.

**Alternatives and exceptions:** Stale Workflow version → transition rejected; stale assignment version → assignment rejected without pretending Workflow rollback; ineligible technician or revoked grant → deny; request out of scope → rejection policy **OPEN**, not assumed; partial success between steps 3 and 4 → show accurate intermediate state and reconcile rather than claim the combined operation atomic.

**Boundary:** `triage` is a Workflow transition; `assign` is **not** a Workflow transition in this candidate.

**Test candidates:** `TC-IT-02-TRIAGE`, `TC-IT-02-ASSIGN`, `TC-IT-02-STALE-WF`, `TC-IT-02-STALE-ASSIGN`, `TC-IT-02-PARTIAL`.

## UC-IT-03 — Perform Technical Work

**Primary actor:** Currently assigned IT Technician. **Trigger:** An authorized assigned task is ready to start.

**Preconditions:** Request is visible and `TRIAGED`, technician is the currently eligible assignee, and the Backend recognizes the actor's relevant authorities and expected Workflow version.

**Main success flow (candidate):**

1. Technician opens the request through an authorized Backend read.
2. Technician requests `start_work`; Backend rechecks current assignment and authority.
3. Workflow Core validates version and advances `TRIAGED → IN_PROGRESS`.
4. Technician performs work outside the system and records a nonblank resolution through the Application contract.
5. Backend checks eligible actor, approved resolution fields and version, then coordinates Application facts with the `resolve` transition.
6. Workflow advances `IN_PROGRESS → RESOLVED`; the requester can subsequently review the reported outcome.

**Postconditions on confirmed success:** A valid resolution fact is recorded and the authoritative state is `RESOLVED`; history reflects successful transitions only.

**Alternatives and exceptions:** Assignment revoked/replaced → deny; blank resolution → validation failure; unresolved incident → no fabricated resolution; stale version → conflict and refresh; unknown write outcome → reconcile; an unsupported orchestration or non-atomic write combination must remain a documented contract gap until proven.

**Test candidates:** `TC-IT-03-START`, `TC-IT-03-RESOLVE`, `TC-IT-03-REVOKED`, `TC-IT-03-EMPTY`, `TC-IT-03-STALE`.

## UC-IT-04 — Confirm Resolution

**Primary actor:** Original Requester. **Trigger:** A resolved IT request awaits confirmation.

**Preconditions:** Request is visible in `RESOLVED`, the actor is the original authorized requester, and the Backend accepts the expected version and `confirm` operation.

**Main success flow (candidate):**

1. The requester reads the authorized resolution details and relevant history.
2. The requester chooses to confirm the reported outcome.
3. The Backend checks verified identity, resource visibility, original-requester eligibility, exact authority and expected Workflow version.
4. Workflow Core advances `RESOLVED → CONFIRMED` and records the transition.
5. The Portal displays confirmed state from the Backend response.

**Postconditions on confirmed success:** State is `CONFIRMED` with an authorized transition history record. No `CLOSED` state is inferred.

**Alternatives and exceptions:** Different requester → deny without disclosure; stale version → conflict; disagreement with repair/resolution → **policy OPEN**; attempted `CLOSED` action → unsupported in this candidate; lost response → reconcile rather than blindly retry.

**Test candidates:** `TC-IT-04-CONFIRM`, `TC-IT-04-WRONG-REQUESTER`, `TC-IT-04-STALE`, `TC-IT-04-NO-CLOSE`.

## Scenario-to-Contract Traceability

| Use case | Requirement / business rule source | Backend capability to verify | Verification dependency |
| --- | --- | --- | --- |
| UC-IT-01 | FR-01/02/09/10; BR-IT-01–04 | Authorized create/activate, visible detail | #22/#23/#24, #53, PR #63 |
| UC-IT-02 | FR-04/05/09; BR-IT-05/06/18 | `triage` transition; separate `assign` command | #23, #53, PR #63 |
| UC-IT-03 | FR-06/07/09; BR-IT-07/08/15–21 | `start_work`, resolution fact, `resolve` | #23/#25, #53, PR #63 |
| UC-IT-04 | FR-08/09/10; BR-IT-09/10/14 | `confirm`, authorized history | #23/#24, #53, PR #63 |

FR/BR identifiers above refer to candidate documentation from PR #76, not approved cross-repository baseline artifacts. Before merging, compare them against the reviewed `requirements.md` revision and actual Backend DTO/endpoint signatures. No API is labeled ACTIVE in this document.

## Cross-Cutting Negative and Security Scenarios

| Scenario | Expected contract behavior | Verification requirement |
| --- | --- | --- |
| Missing or invalid authentication | `401` per published API policy | Real OIDC / HTTP negative test |
| Visible request, forbidden action | `403` subject to approved visibility rules | Server-side resource/action test |
| Hidden or other-tenant request | Non-disclosing `404` | ID-swap and cross-tenant test |
| Invalid state transition | Safe rejection; no state/history mutation | Core + HTTP test |
| Stale Workflow or assignment version | Reject conflicting update for the respective version | Separate concurrency tests |
| Duplicate click / lost response | No fabricated success or blind write replay | Uncertain-write / reconciliation test |
| Revoked assignment or permissions | Re-evaluate current eligibility | Authorization test |
| Unsupported rejection/rework/close | Explicitly unsupported until approved | Contract gap / negative test |

The numeric HTTP responses above are intended policy candidates grounded in project Issues; they require endpoint-specific validation. No status or evidence in this document grants production permission.

## Open Decisions and G1 Alignment

| Decision gap | Required disposition |
| --- | --- |
| `CONFIRMED` versus `CLOSED` in earlier G1 golden-path aspirations | Record first-candidate terminal state `CONFIRMED`; treat actual closure as a separately approved scope/contract change |
| Wrong-scope rejection and mandatory reason | Define policy/actor and transition semantics before implementation |
| Requester disagreement, rework and escalation | Keep OPEN; do not invent paths or state names |
| Final operation authorities, role eligibility and field/event visibility | Ratify in #53 and security/contract review |
| Transaction boundary of Application fact + Workflow operation | Verify actual code, persistence behavior and recovery evidence |
| Exact published endpoints and `MockRequestService` replacement | Inspect API and integration evidence at pinned SHA; keep MOCK and LIVE separate |

The [G1 scope and MVP](../../00-project/scope-and-mvp.md) is a draft-level intended link; verify file existence on the target documentation baseline before merging. G1 business aspirations do not override the narrower first integration candidate recorded here.

## References and Review Status

- [PR #76 — Application documentation drafts](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76)
- [Issue #53 — Business Contract draft](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53)
- [Issue #39 — Portal boundary](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39)
- [PR #63 — Backend integration candidate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63)
- [PR #71 — Portal integration candidate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/71)

**Document lifecycle:** Authoring revision prepared for independent review; authoritative status belongs to the Document Register. **Application contract acceptance and LIVE E2E verification are NOT ESTABLISHED.**
