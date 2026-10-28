# DX-OS — Scope, MVP and Golden Paths

> **Status:** PROPOSED — BUSINESS CONTRACT REVIEW REQUIRED
> **Baseline:** `origin/main` `e06fa429cfd92b032c166eb3ebf32803d13191c2`
> **Important:** This document defines target scope, **not** which endpoints already run on the integrated release.

## 1. Objective

Deliver a useful, secure **IT Helpdesk golden path** and a smaller **Facility reuse proof** using the same four Core capabilities. Prefer one complete path with evidence over multiple unfinished demonstration screens.

## 2. MVP boundaries

| Scope | Included | Not implied |
|---|---|---|
| Identity | Verified principal and exact authority checks | DX-OS-managed passwords or automatic IdP migration |
| Gateway/BFF | Authenticated business entry, errors, controlled routes | Wide-open generic workflow endpoint |
| Workflow | Definitions, transitions, versions, history, terminal controls | Full BPMN/timers/SLA automation |
| Data | Application metadata and shared object-storage contract where needed | Single Data-owned CRUD for every module |
| Helpdesk | Request creation, review/triage, assignment, execution, resolution, acknowledgement and closure, subject to contract | Advanced analytics or all facilities |
| Facility | One representative, authorized vertical slice | Fully featured facility management suite |
| Release | Clean-install walkthrough, tests, licensing/attribution review | Production HA or zero-downtime migration |

## 3. MoSCoW proposal

| Priority | Candidate feature | Completion criterion |
|---|---|---|
| MUST | Verified login and trusted user identity | Real OIDC/JWT evidence; forged identity rejected |
| MUST | Helpdesk request create/read | Actor-bound, resource-safe business APIs |
| MUST | Authorized transitions | Tenant/resource policy + eligible actor + state check |
| MUST | Versioned transition history | Concurrency conflict and atomic write tests |
| MUST | Deterministic PostgreSQL deployment | Reviewed migrations, clean install, no secrets in Git |
| MUST | Helpdesk golden path end-to-end | Live evidence pinned to integration commit |
| MUST | Second-app Core reuse proof | Facility flow without Core-specific changes |
| MUST | Open-source release basics | License rights, attribution, installation and source links |
| SHOULD | Attachments via `ObjectStoragePort` | Authorized upload/download + storage tests |
| SHOULD | List/search/filter and basic manager views | Verified functionality and access restrictions |
| SHOULD | Automated architecture dependency checks | ArchUnit blocking new forbidden imports |
| COULD | Provider switch demo | Filesystem ↔ S3-compatible adapter conformance |
| COULD | SLA dashboard and notifications | Separate proposal with acceptance |
| OUT | BPMN engine migration and independent microservices | No effort in MVP |
| OUT | Distributed ACID object-storage transaction | Explicit recovery design instead |

**Review flag:** Whether attachments and provider-switch demonstration are mandatory competition acceptance items must be agreed; SHOULD/COULD classifications here are proposals.

## 4. GP-IT-01 — Helpdesk golden path (proposed product flow)

```mermaid
flowchart LR
    C[Create] --> T[Triage / classify]
    T --> A[Assign]
    A --> S[Start]
    S --> R[Resolve]
    R --> F[Requester confirmation]
    F --> X[Close]
```

**Preconditions:** Authenticated caller; valid business eligibility/tenant/resource binding; authoritative workflow definition available. **Postconditions:** Business data and workflow state/history consistent; authorized requester can inspect allowed results. **Negative cases:** Invalid actor, wrong tenant, non-assignee start/resolve, stale expected version, duplicate command, revoked access, lost commit acknowledgement.

**Critical distinction:** Application product stages (e.g., `CLOSED`) need not match one-to-one with generic Workflow engine states. The exact transition matrix and who closes after confirmation must be approved in the business contract. This file does not override `it-helpdesk@1` or existing candidate PR implementation.

## 5. GP-FAC-01 — Facility reuse path (proposal)

A requester creates a facility issue; an eligible operator reviews/assigns and completes work; requester acknowledgement is handled according to Facility rules. The vertical slice must use the **existing Identity verified principal, Workflow engine, Data storage contract (if attachments are in scope), and Gateway security conventions** without adding Facility-specific branches inside Core.

**Open:** Final actors, status transitions, resource ACL rules and Facility API are TBD; do not invent a validated matrix.

## 6. Business rules and trust boundaries

- A role/authority in JWT is not a grant to every resource.
- The Application evaluates tenant, resource ACL, assignment and requester eligibility.
- Client-controlled `actorId`, `X-User` or `X-Role` cannot establish trusted identity.
- Workflow validates transitions, versions and history; it does not know Helpdesk/Facility permissions.
- Direct persistence and generic HTTP entry points cannot bypass approved policy.
- A successful write requires confirmed commit; unknown commit requires reconciliation, not blind retry.

## 7. Data and persistence

Helpdesk business facts stay owned by Helpdesk; Facility by Facility; Workflow instance and history by Workflow. `ObjectStoragePort` owns provider-neutral object bytes and metadata semantics, not ticket permissions. Database and object-store writes require explicit compensation/recovery; no joint ACID transaction is promised.

## 8. Out-of-scope and deferred decisions

Advanced SLA timers, automatic notifications, dashboard metrics beyond essential views, multi-tenant administration UI, cross-region scaling, workflow modeler/BPMN, file chunk-upload protocol and multi-provider seamless migration are deferred pending requirements and resource estimates.

## 9. Traceability and acceptance matrix

| ID | Acceptance scenario | Required evidence |
|---|---|---|
| MVP-01 | Anonymous/fake identity cannot create requests | AuthN negative + gateway test |
| MVP-02 | Eligible requester can create and read own ticket | Full-stack API test |
| MVP-03 | Unauthorized ticket actions denied | AuthZ matrix and tenant/ACL test |
| MVP-04 | Valid state transition succeeds once | Workflow + DB integration |
| MVP-05 | Concurrent transitions do not overwrite | PostgreSQL concurrency test |
| MVP-06 | History reflects committed transition | Atomicity and history assertions |
| MVP-07 | Confirmation/closure business roles correct | Approved business scenarios |
| MVP-08 | Facility reuses Core unchanged | Diff evidence + real integration flow |
| MVP-09 | Clean install in fresh environment | Installation transcript and checks |
| MVP-10 | Public docs/license/provenance complete | Independent release review |

Only record PASS with exact commit, environment, commands and artifacts. PR-level claims alone are insufficient.

## 10. Dependency and release gates

1. Business contract/matrix approved for Helpdesk.
2. Security boundary and generic route exposure reviewed.
3. Transaction design preserves Workflow and business consistency.
4. Real-keycloak, database, BFF/Portal E2E evidenced for the integration baseline.
5. Facility proof run against the same Core implementation.
6. README/install/release/license review completed.

## 11. Owner and review register

| Area | Owner for confirmation | State |
|---|---|---|
| Helpdesk roles/transitions | Application / Backend | PENDING |
| Facility vertical slice | Application | PENDING |
| Security | Identity/Security reviewer | PENDING |
| Workflow and consistency | Backend / Data reviewer | PENDING |
| Open-source readiness | Licensing reviewer | PENDING |

## 12. References

- [Problem Statement](problem-statement.md)
- [Project Overview](project-overview.md)
- [System Architecture](../01-architecture/system-architecture.md)
- [Workflow Core Contract](../contracts/workflow-core.md)
- [Identity Core Contract](../contracts/identity-core.md)
- [Data Core Contract](../contracts/data-core.md)

**Scope acceptance:** PENDING. This file does not authorize production route activation or merge.
