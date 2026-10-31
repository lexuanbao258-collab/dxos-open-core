---
title: DX-OS Open-Core — Workflow Authorization
document_id: G4-F15
status: DRAFT — NOT REVIEWED
version: 1.0-review-draft
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
owner: Application / Identity Security / Workflow — to confirm
reviewers: Backend / Application / Identity / Independent G4 — pending
---

# Workflow Core — Authorization and Trusted Mutation Boundaries

> **Evidence classification:** **OBSERVED** = directly inspected Java source or canonical contract at the pinned SHA; **SELECTED** = one of the 18 user-approved G4-F15 design decisions; **PROPOSED** = required integration behavior or validation, not demonstrated by this document; **VERIFIED** = executed, reproducible evidence at a pinned revision and independently reviewed. No tests were executed for this document and no Gate approval is claimed.

## 1. Purpose, baseline and non-goals

This document describes **who is responsible for authentication, operation authority, application-specific resource eligibility and safe Workflow mutation**. The Workflow capability is a provider-neutral state machine; **transition validity does not imply that an actor has permission to perform it**.

Pinned commit: `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`. Canonical sources:
- [Workflow Core contract](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/workflow-core.md) — blob `11c5aaca1e7e08394710a6d0c5fe8e187d549d83`.
- [Identity Core contract](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/identity-core.md) — blob `8b6a6f412cf405709e85839f2bbe9021149ba33e`.
- [Core/Application boundary](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/01-architecture/core-application-boundary.md) — blob `697ccaa4818bb45829496a34b20c72a91af4125b`, **PROPOSED — REVIEW REQUIRED**, with its own older `source_baseline`.
- [ActorReference.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/workflow/ActorReference.java) — blob `488057cc5c43c9595bf1918ca88da3781dc5c2ec`.
- [WorkflowRuntime.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java) — blob `3b7e7ac755f0021b6ef0ea5a572748bdca585ff2`.

This document does **not** change Core Java signatures, create new authorities/endpoints, certify production authorization integration, define Helpdesk or Facility business policy, add JWT verification inside Workflow, or replace any accepted ADR. Breaking contracts require separately reviewed ADRs.

## 2. Observed capability and trust limits

| Component | OBSERVED or SELECTED responsibility | Must not claim |
| --- | --- | --- |
| Identity Core (OBSERVED contract) | Represents identity, context, authority and authorization-decision facts; verified origin required | An `AuthenticatedPrincipal` or `AuthenticationContext` constructor proves login |
| `ActorReference` (OBSERVED Java) | `record ActorReference(String actorId)` validates nonblank opaque actor ID | Actor authenticity, ticket access, permission to transition |
| `WorkflowRuntime` (OBSERVED Java) | Orchestrates create, activate, transition, instance/history reads and delegates durable writes | Internal caller authentication, resource ACL, JWT parsing or authorization decisions |
| Workflow domain (OBSERVED contract) | Exact definition/version, current state, lifecycle, transition ID, expected version | Application-specific owner/assignee/approver checks |
| Gateway and verified-principal boundary (SELECTED target) | Authenticate via trusted mechanism, enforce exact capability authority and propagate verified facts | Trust unverified `X-User`, `X-Role`, `X-Authorities`, or `X-Actor-Id` |
| Helpdesk/Facility Application (SELECTED target) | Business resource lookup and eligibility, action authorization, trusted actor mapping | Delegating ticket ACL to the generic state-machine constructor |
| Persistence port (OBSERVED contract) | Commit-time version and origin validation, durable atomic state/history | Compensating for a missing upstream resource authorization decision |

**Evidence limitation:** `docs/01-architecture/core-application-boundary.md` describes an intended integration at an older embedded baseline. Its proposal is not proof of the complete Gateway → Application → Workflow call path at the pinned SHA.

## 3. Threat model and security properties

Threat boundaries:
1. **External caller → HTTP/Gateway:** untrusted authentication assertions, headers, request IDs and resource IDs.
2. **Verified identity → Application operation:** legitimate identity and exact authority do not prove eligibility for a specific resource.
3. **Application → Workflow Runtime:** `ActorReference` is an opaque claimed actor value whose trust depends on its authorized caller.
4. **Workflow proposal → Persistence:** domain validation does not prove durable commit; stale version and forged same-key definitions must be rejected at the write boundary.

Required properties (SELECTED):
- Deny invalid/missing authentication **before** reaching a mutation.
- Require exact operation authority; no implicit broad authority by substring, wildcard assumption or unrelated role.
- Check resource ownership/assignment/approval eligibility in the Application at the point of the operation.
- Produce `ActorReference` from a genuinely verified principal mapping, not from arbitrary client input.
- Keep mutating operations behind authorized use-case entrypoints; audit any alternate direct call paths.
- Denied requests must not create a **successful Workflow transition record** or mutate Workflow state as a consequence of that denial.
- Treat security audit events (including denial records) as a **separate Audit/Application concern**. Successful transition history is not a generic security audit trail.

## 4. Target authorization sequence

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant G as Gateway / Verified Identity
    participant A as Authorized Application use case
    participant B as Application business repository
    participant W as WorkflowRuntime
    participant P as WorkflowPersistencePort
    Client->>G: Request operation + resource ID
    G->>G: Validate authentication and exact authority
    alt Missing authentication or operation authority
        G-->>Client: Deny; no Workflow mutation
    else Verified identity / authority
        G->>A: Trusted principal + operation + resource
        A->>B: Load authoritative business resource/policy facts
        B-->>A: Ownership, assignment, eligibility
        A->>A: Check resource-level ACL and action eligibility
        alt Resource denied
            A-->>Client: Deny; no Workflow mutation
        else Resource allowed
            A->>A: Derive trusted ActorReference
            A->>W: executeTransition(TransitionCommand)
            W->>P: Load authoritative instance + exact definition
            P-->>W: Authoritative snapshot and definition
            W->>W: Validate state/expectedVersion
            W->>P: commitTransition(proposal)
            P-->>W: Durable success or normalized failure
            W-->>A: Committed result or error
            A-->>Client: Translate result
        end
    end
```

**Status:** SELECTED/PROPOSED end-to-end design, not an assertion that this exact integration is tested. Java Core permits callers to invoke methods; “only authorized use cases may mutate” is an **enforcement obligation** for wiring, exposure and test reviews, not a fact created by this diagram.

## 5. Operation, authority and resource decision matrix

The operation names below are **candidate exact authority strings** consistent with previously selected Stage-4 mapping; the *current HTTP-to-application enforcement and exact policy rules must be independently inspected*. This document does not declare any role-to-authority grants as proven.

| Operation / protected action | Proposed exact authority | Application resource policy (owner to specify) | Workflow / persistence responsibility |
| --- | --- | --- | --- |
| Create workflow instance | `workflow:create` | May this principal create this class of business request? Does the referenced resource exist and belong to an eligible scope? | Authoritative definition, insert-only and uniqueness |
| Activate instance | `workflow:activate` | Can the principal start this instance for its business resource? | NOT_STARTED→ACTIVE, full expected snapshot/lifecycle compare, version 0 |
| Transition instance | `workflow:transition` | Is actor assigned/owner/approver/otherwise allowed to execute **this transition on this resource now**? | Exact definition/transition, ACTIVE lifecycle, expectedVersion, atomic commit |
| Read instance/history | `workflow:read` | Does the principal have read permission for this specific business resource/history? | Return authoritative snapshot/history; no shared snapshot promise across separate reads |

**No inferred hierarchy:** possession of `workflow:read`, for example, does not automatically grant `workflow:transition`; one exact capability grant does not imply the others. **OPEN:** exact canonical source for authority constants, placement of capability enforcement, API route coverage, resource policy matrix and read-history disclosure rules.

### Example policy boundaries (illustrative, not approved business rules)

| Application | Example denied scenario | Correct owner |
| --- | --- | --- |
| Helpdesk | A technician has `workflow:transition` but is not eligible to resolve the requested ticket | Helpdesk Application |
| Facility | An authenticated user has workflow authority but is not an approved resource-specific approver | Facility Application |
| Both | Stale expectedVersion after an otherwise authorized request | Workflow domain + persistence concurrency contract |

A rule based on ticket assignment must **not** be implemented as a branch in generic Workflow Core.

## 6. Actor spoofing, IDOR and direct-call bypass

- **Headers:** external `X-User`, `X-User-Id`, `X-Role`, `X-Authorities`, `X-Actor-Id` and lookalike fields must not establish a principal or override actor identity. Strip/ignore or reject as appropriate at the verified ingress seam; tests must prove the behavior.
- **Actor spoofing:** `new ActorReference("someone-else")` can satisfy only structural nonblank validation. The authorized Application must derive and validate the actor from verified context. History containing an actor ID is not evidence that the caller was authenticated.
- **IDOR:** attacker-supplied ticket/Facility/instance IDs must be mapped to authoritative business resource ownership and resource permission; never assume an existing instance ID is permission to read or mutate it.
- **Bypass:** inventory every exposed mutating route and any application-accessible direct `WorkflowRuntime`/port entrypoint. No route may accept untrusted caller identity and immediately mutate Workflow without Application authorization.
- **Replay/stale:** security approval cannot waive expectedVersion or durable concurrency checks. Authorized but stale mutations must fail under the Workflow contract.
- **Error disclosure:** mapping authentication failures to HTTP statuses belongs to Gateway/API, separately from the nine `WorkflowErrorCode` values; avoid information disclosure through inconsistent detail before authorization.

## 7. Authorization TOCTOU and unresolved atomicity choices

**SELECTED:** evaluate eligibility at the operation boundary against authoritative business data. Yet a race remains possible:

1. Application reads “technician assigned”.
2. Another transaction removes that technician from the ticket.
3. The first operation commits a Workflow transition based on a now-stale eligibility decision.

The Workflow port enforces state/version consistency **but does not automatically provide an atomic transaction across application-owned business records and Workflow persistence**. This is an OPEN architecture decision, not solved by actor references or optimistic Workflow runtimeVersion.

**Proposed strategies to evaluate separately** (no option is silently selected):
- Business policy version / eligibility predicate re-check coupled to the authorized mutation boundary.
- Shared local transaction with appropriately coordinated business row lock and Workflow commit, if data ownership and adapter boundaries permit it.
- Explicit business-version precondition or domain command serialization.
- Where cross-transaction atomicity is not available, define acceptable consistency windows, compensating workflow and denial/reconciliation rules through an ADR.

Any selected approach must test concurrent reassignment versus transition, and must not compromise the Workflow port's own atomic state/history semantics. **Review blocker:** no claim of TOCTOU closure without chosen atomicity design and executable race proof.

## 8. Negative security acceptance matrix

| ID | Threat / input | Required control | Negative assertion and evidence | Proposed reviewer | Current proof |
| --- | --- | --- | --- | --- | --- |
| WA-01 | Missing/invalid authentication | Verified Gateway boundary | 401-equivalent transport denial, zero state/history mutation | Identity/Gateway | NOT RUN HERE |
| WA-02 | Valid identity, missing exact operation authority | Exact authority gate | Denied; operation not invoked | Identity/Gateway | NOT RUN HERE |
| WA-03 | Authorized capability, ticket not assigned | Helpdesk resource ACL | Denied; no successful history and no state change | Application | NOT RUN HERE |
| WA-04 | Forged actor/header (`X-Actor-Id`, `X-User`, `X-Role`) | Trusted identity derivation | Spoofed identity is rejected/ignored; no spoofed mutation | Security | NOT RUN HERE |
| WA-05 | Direct HTTP route or internal unguarded mutation | Entry-point inventory + authorized use case only | Bypass blocked; route/source trace and DB unchanged | Gateway/Backend | NOT RUN HERE |
| WA-06 | IDOR for another resource's instance/history | Resource ownership on read and write | No unauthorized read/write leakage | Application/Security | NOT RUN HERE |
| WA-07 | Facility actor not eligible as approver | Facility-specific policy | Denied despite generic workflow authority | Facility/Application | NOT RUN HERE |
| WA-08 | Authorized stale/replayed transition | Workflow version and atomic commit checks | `VERSION_CONFLICT`, no history from rejected request | Workflow/Data | NOT RUN HERE |
| WA-09 | Concurrent reassignment and transition | Reviewed policy consistency mechanism | Bounded race test meeting selected invariant | Application/Data | DESIGN OPEN |
| WA-10 | Audit versus successful transition history | Separate Audit policy | Denial may be audited; must not appear as successful transition | Audit/Application | DESIGN OPEN |
| WA-11 | Capability escalation via overlapping authority names | Exact matching | Extra/unrelated authority does not grant mutation | Security | NOT RUN HERE |
| WA-12 | Helpdesk/Facility reuse | Separate policy use cases using unchanged Core | Both negative suites, no app-specific Core branching | Independent reviewer | NOT RUN HERE |

For test evidence collect: exact source SHA; URI, method and route handler; verified role/authorities without secrets; resource ownership/assignment fixture; expected status and error category; before/after durable snapshot and history; test command/exit code; bounded concurrency trace where relevant; reviewer decision. Redact JWTs and private identity data.

**A test case definition is not a PASS result.** Historical identity/Gateway suite reports are not automatically applicable to these Application-specific Workflow negative cases.

## 9. Source → control → test → evidence → reviewer traceability

| Control | Source basis | Required executable evidence | Ownership | Status |
| --- | --- | --- | --- | --- |
| Trusted actor is not authenticated by constructor | `ActorReference.java`; Workflow canonical contract | Forged actor tests through real ingress/use case | Security | OBSERVED DESIGN; TEST NOT VERIFIED |
| Runtime does not authorize calls | `WorkflowRuntime.java` | Source call-graph and endpoint coverage check | Backend | OBSERVED SOURCE; ROUTES NOT VERIFIED |
| Verified identity before authority | Identity Core contract | Real JWT/invalid/expired/header spoof tests | Gateway/Identity | CONTRACT OBSERVED; E2E NOT VERIFIED |
| Resource eligibility before mutation | Core/Application boundary proposal | Helpdesk and Facility ACL negative requests with DB state proof | Applications | SELECTED/PROPOSED |
| Workflow concurrency on authorized operations | Workflow canonical contract | Stale/replay and race DB integration suite | Workflow/Data | CONTRACT OBSERVED; TEST NOT VERIFIED |
| TOCTOU consistency | Open Application transaction design | Reassignment-versus-transition controlled race | Application/Data | OPEN |
| Independent two-App reuse | G4-F15 selection | Evidence under unchanged Core SHA | Independent reviewer | PROPOSED |

## 10. Review gates and release blockers

| Gate | Required review/evidence | Current disposition |
| --- | --- | --- |
| G4-D Design | Decision record, boundary diagram, threat coverage | DRAFT / NOT REVIEWED |
| G4-S Source | Exact code/endpoint inventories, canonical contract reconciliation | PARTIAL SOURCE INSPECTION ONLY |
| G4-T Security | Negative authentication, exact-authority, IDOR, forged-header, bypass and deny-no-mutation suites | NOT RUN HERE |
| G4-E E2E | Trusted Gateway → Application → Workflow → durable state/history evidence | NOT RUN HERE |
| G4-R Reuse | Independent Helpdesk and Facility policy slices without Core business forks | NOT RUN HERE |
| G4-I Independent | Named reviewer verdict on exact evidence bundle | NOT APPROVED |

**P0 release blockers:** unguarded public mutation route; fabricated actor accepted; capability-only authorization used as ticket ACL; successful transition record on denied request; resource IDOR; hidden authority escalation; no established TOCTOU risk disposition; lack of exact-SHA runtime evidence despite PASS claims.

## 11. Open questions and handoff

| ID | Open or deferred item |
| --- | --- |
| WA-OD-01 | Exact authority constant locations and source-verifiable route-to-operation map |
| WA-OD-02 | Trusted principal-to-ActorReference mapping; issuer/subject handling and delegated actions |
| WA-OD-03 | Definitive Helpdesk/Facility resource ACL and policy matrix |
| WA-OD-04 | TOCTOU consistency mechanism and acceptable race windows |
| WA-OD-05 | Unauthorized resource status mapping and information disclosure policy |
| WA-OD-06 | Read/history access, PII redaction and audit event ownership |
| WA-OD-07 | Source-level mutation entrypoint inventory and direct-bypass mitigation |
| WA-OD-08 | Two-Application real negative suites and independent approval |

**Deferred:** importing Identity dependencies into Workflow Core, general workflow business authorization guards, JWT storage in ActorReference, new external mutation endpoints, automatic policy-retry engine and approval of any new authority by this document.

**Final status:** `G4-F15 DRAFT / NOT REVIEWED`. Selected design decisions are not verification of a complete security implementation.
