# DX-OS Open-Core — Canonical Glossary

> **Document ID:** G1-F05  
> **Purpose:** Provide stable, unambiguous terminology for DX-OS engineering and business analysis.  
> **Scope:** Product, architecture, security, workflow, data, integration, quality and governance terminology.  
> **Authority:** Definitions are working editorial conventions; project-specific terms do not supersede unratified business contracts, published code contracts or accepted ADRs. The Document Register governs approval.

## 1. Purpose and Use

This glossary is a **DRAFT reference vocabulary**, not a ratified API, Workflow Definition, Architecture Decision Record (ADR), or proof of implementation. It separates general technical meanings from candidate DX-OS-specific meanings. Reader-facing definitions are in English and stable `TERM-###` identifiers are reserved for this draft; reconcile against an existing glossary before merging to avoid ID collisions.

**Canonical rule:** use one preferred term per concept; expand an acronym on first use; introduce aliases only after a documented review. Preserve exact literals such as `COMMIT_OUTCOME_UNKNOWN` and `expectedVersion` when discussing a code contract, but confirm their existence/signature at a pinned source revision.

## 2. Provenance and Qualification

The source issues below establish **project direction and proposals**, not automatic independent acceptance. Their public issue bodies were examined; complete source-file and ADR inventories could not be established from the available code search.

| Source | What it supports | Limitation |
| --- | --- | --- |
| [#39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) | Independent Portal, trusted API boundary, OIDC/PKCE | Some API/UI work remains dependent on review |
| [#40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) | Local Keycloak/PostgreSQL 17/MinIO demo target and security constraints | Full-stack E2E not verified in issue |
| [#53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) | Candidate Helpdesk/Facility business contract, actor/resource distinctions | Business contract explicitly DRAFT pending ratification |
| [#27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) | Two-definition Core reuse and E2E proof criteria | Not evidence of passed tests |
| [#25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) | Persistence, concurrency and unknown-commit semantics | Exact public code symbols need pinned source verification |
| [#24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24) | Verified OIDC principal design concern | Concrete principal contract needs source review |
| [#23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23) | Workflow authorization / trusted-principal boundary | Exact authorities require code/contract review |

**Qualification labels:** `Technical definition` means general concept; `Design convention` is proposed canonical documentation usage; `Candidate` is unratified business meaning; `Requires source verification` identifies contract names or behaviors not yet proven against a pinned revision. These labels are not document lifecycle or Gate results.

## 3. Alphabetical Term Index

| ID | Canonical term | Domain |
| --- | --- | --- |
| TERM-001 | Acceptance Criterion | Product |
| TERM-002 | Acceptance Evidence | Quality |
| TERM-003 | Actor Eligibility | Security |
| TERM-004 | Adapter | Architecture |
| TERM-005 | ADR Lifecycle | Governance |
| TERM-006 | Application | Product |
| TERM-007 | Application Programming Interface (API) | Architecture |
| TERM-008 | Architecture Decision Record (ADR) | Governance |
| TERM-009 | AS-IS Process | Product |
| TERM-010 | Audit Event | Data |
| TERM-011 | Authentication | Security |
| TERM-012 | Authorization | Security |
| TERM-013 | Backend for Frontend (BFF) | Integration |
| TERM-014 | Business Need | Product |
| TERM-015 | Business Role | Security |
| TERM-016 | Canonical Subject ID | Security |
| TERM-017 | COMMIT_OUTCOME_UNKNOWN | Data |
| TERM-018 | Core | Architecture |
| TERM-019 | Core Contract | Architecture |
| TERM-020 | Data Ownership | Data |
| TERM-021 | Document Lifecycle | Governance |
| TERM-022 | End-to-End (E2E) Test | Quality |
| TERM-023 | Evidence Baseline | Quality |
| TERM-024 | Exact Authority | Security |
| TERM-025 | Expected Version | Workflow |
| TERM-026 | Facility Maintenance | Product |
| TERM-027 | Gate Outcome | Governance |
| TERM-028 | Golden Path | Product |
| TERM-029 | Headless Core | Architecture |
| TERM-030 | HTTP Contract | Integration |
| TERM-031 | Identity Handoff | Integration |
| TERM-032 | Identity Provider (IdP) | Security |
| TERM-033 | Independent Reviewer | Governance |
| TERM-034 | Invalid Transition | Workflow |
| TERM-035 | Issue Lifecycle | Governance |
| TERM-036 | IT Helpdesk | Product |
| TERM-037 | JSON Web Token (JWT) | Security |
| TERM-038 | Layer 2 Portal | Integration |
| TERM-039 | Minimum Viable Product (MVP) | Product |
| TERM-040 | Module Boundary | Architecture |
| TERM-041 | MoSCoW | Product |
| TERM-042 | Negative Test | Quality |
| TERM-043 | Object Storage | Data |
| TERM-044 | OpenID Connect (OIDC) | Security |
| TERM-045 | Operation Availability | Integration |
| TERM-046 | Optimistic Concurrency | Workflow |
| TERM-047 | Persistence | Data |
| TERM-048 | Port | Architecture |
| TERM-049 | Problem Hypothesis | Product |
| TERM-050 | Proof Key for Code Exchange (PKCE) | Security |
| TERM-051 | Requester | Product |
| TERM-052 | Resource Grant | Security |
| TERM-053 | Resource Visibility | Security |
| TERM-054 | Reuse Proof | Product |
| TERM-055 | Reuse Verification | Quality |
| TERM-056 | Review Baseline | Governance |
| TERM-057 | Scope Priority | Governance |
| TERM-058 | Stakeholder | Product |
| TERM-059 | State | Workflow |
| TERM-060 | Tenant Isolation | Security |
| TERM-061 | TO-BE Process | Product |
| TERM-062 | Transaction | Data |
| TERM-063 | Transition | Workflow |
| TERM-064 | Verified Claim | Quality |
| TERM-065 | Verified Principal | Security |
| TERM-066 | Workflow Definition | Workflow |
| TERM-067 | Workflow Engine | Architecture |
| TERM-068 | Workflow History | Workflow |
| TERM-069 | Workflow Instance | Workflow |

## 4. Definitions by Domain

Each entry includes the definition, context, a distinction to prevent misuse, and a source link. The source describes the intended domain; it is **not** by itself verification of the glossary wording.

### Product Terms

#### TERM-001 — Acceptance Criterion

| Field | Meaning |
| --- | --- |
| Canonical definition | A falsifiable condition used to evaluate a specified requirement or deliverable. |
| Context | MVP acceptance |
| Not to confuse with | A checked task is not verification. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Normative usage |

#### TERM-006 — Application

| Field | Meaning |
| --- | --- |
| Canonical definition | A business-facing consumer that owns domain data, forms, validation and business-specific behavior. |
| Context | IT Helpdesk; Facility Maintenance |
| Not to confuse with | Not the reusable Core. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Proposed boundary |

#### TERM-009 — AS-IS Process

| Field | Meaning |
| --- | --- |
| Canonical definition | A model of how work is currently performed in the reference context. |
| Context | Hypothetical service intake |
| Not to confuse with | Not a verified customer observation. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Hypothetical |

#### TERM-014 — Business Need

| Field | Meaning |
| --- | --- |
| Canonical definition | A stakeholder outcome or capability sought in response to a problem. |
| Context | Traceable G1 need |
| Not to confuse with | Not an implementation feature or API endpoint. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Candidate |

#### TERM-026 — Facility Maintenance

| Field | Meaning |
| --- | --- |
| Canonical definition | A candidate service-request scenario for facility repairs and acceptance. |
| Context | Second workflow definition |
| Not to confuse with | Not a confirmed completed application. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Proposed |

#### TERM-028 — Golden Path

| Field | Meaning |
| --- | --- |
| Canonical definition | An end-to-end journey used as a principal product and integration acceptance scenario. |
| Context | IT request creation through closure |
| Not to confuse with | Not a guarantee that runtime E2E passed. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Proposed acceptance scope |

#### TERM-036 — IT Helpdesk

| Field | Meaning |
| --- | --- |
| Canonical definition | A primary candidate application scenario for software-service incidents. |
| Context | Main MVP demonstration |
| Not to confuse with | Not equivalent to DX-OS Core. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Proposed business contract |

#### TERM-039 — Minimum Viable Product (MVP)

| Field | Meaning |
| --- | --- |
| Canonical definition | The smallest scoped solution sufficient to test designated product and engineering hypotheses. |
| Context | MoSCoW MUST scope |
| Not to confuse with | Not a completed or approved release. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Scope planning |

#### TERM-041 — MoSCoW

| Field | Meaning |
| --- | --- |
| Canonical definition | Scope-priority categories MUST, SHOULD, COULD and OUT used in this documentation. |
| Context | Scope matrix |
| Not to confuse with | Not implementation status or verification level. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Documentation convention |

#### TERM-049 — Problem Hypothesis

| Field | Meaning |
| --- | --- |
| Canonical definition | A plausible yet unvalidated assertion about a stakeholder difficulty or cause. |
| Context | AS-IS reference context |
| Not to confuse with | Not an observed pain point or survey finding. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Unvalidated |

#### TERM-051 — Requester

| Field | Meaning |
| --- | --- |
| Canonical definition | An actor who submits and follows an internal service request. |
| Context | Employee actor |
| Not to confuse with | Not necessarily authorized to act on others’ requests. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Candidate role |

#### TERM-054 — Reuse Proof

| Field | Meaning |
| --- | --- |
| Canonical definition | Evidence that distinct workflow definitions use the same unchanged published Core contracts and processing algorithm. |
| Context | IT and Facility demonstration |
| Not to confuse with | Not just sharing UI styles or code duplication. |
| Reference | [GitHub Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) |
| Qualification | Verification required |

#### TERM-058 — Stakeholder

| Field | Meaning |
| --- | --- |
| Canonical definition | A person or group affected by, accountable for, or evaluating the proposed solution. |
| Context | Requester, coordinator, worker, operator |
| Not to confuse with | Not automatically an authorized system role. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Analysis term |

#### TERM-061 — TO-BE Process

| Field | Meaning |
| --- | --- |
| Canonical definition | A proposed future way of working after a planned solution or process change. |
| Context | Candidate intake-to-closure flow |
| Not to confuse with | Not an approved Workflow Definition. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Proposed |

### Architecture Terms

#### TERM-004 — Adapter

| Field | Meaning |
| --- | --- |
| Canonical definition | An implementation connected to a port to integrate an external technology or provider. |
| Context | PostgreSQL or MinIO adapter |
| Not to confuse with | Not a provider-neutral domain contract. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Architectural convention |

#### TERM-007 — Application Programming Interface (API)

| Field | Meaning |
| --- | --- |
| Canonical definition | A published software interface specifying allowed operations and their contracts. |
| Context | Portal to Backend HTTP |
| Not to confuse with | Not automatically ACTIVE because documented. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Architecture term |

#### TERM-018 — Core

| Field | Meaning |
| --- | --- |
| Canonical definition | Provider-neutral platform mechanisms and published contracts intended for reuse across business applications. |
| Context | Identity, Gateway, Workflow and Data |
| Not to confuse with | Not domain-specific application validation. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Proposed boundary |

#### TERM-019 — Core Contract

| Field | Meaning |
| --- | --- |
| Canonical definition | A published interface and behavioral promise that application consumers or adapters rely upon. |
| Context | Workflow operation and persistence ports |
| Not to confuse with | Not an unapproved implementation detail. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Ratification required for some operations |

#### TERM-029 — Headless Core

| Field | Meaning |
| --- | --- |
| Canonical definition | A platform exposed through defined interfaces without requiring an embedded business-facing UI. |
| Context | Layer 1 consumed by Portal |
| Not to confuse with | Not an absence of security controls. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Architecture direction |

#### TERM-040 — Module Boundary

| Field | Meaning |
| --- | --- |
| Canonical definition | A rule governing ownership and dependency direction among architectural components. |
| Context | Core independent of Helpdesk classes |
| Not to confuse with | Not merely a package name. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Architecture direction |

#### TERM-048 — Port

| Field | Meaning |
| --- | --- |
| Canonical definition | An abstraction that defines an interaction required or offered by a component. |
| Context | WorkflowPersistencePort; ObjectStoragePort |
| Not to confuse with | Not a specific JDBC or MinIO implementation. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Architecture term |

#### TERM-067 — Workflow Engine

| Field | Meaning |
| --- | --- |
| Canonical definition | Reusable logic applying definitions, transition rules, authorization checks and state/history changes. |
| Context | Core Workflow |
| Not to confuse with | Not a CRUD update to a status column. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Design direction |

### Security Terms

#### TERM-003 — Actor Eligibility

| Field | Meaning |
| --- | --- |
| Canonical definition | Business rule determining whether an actor may perform a workflow action in a given context. |
| Context | Transition × actor matrix |
| Not to confuse with | Not sufficient alone for backend resource authorization. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Candidate business policy |

#### TERM-011 — Authentication

| Field | Meaning |
| --- | --- |
| Canonical definition | Verification of the claimed identity of a subject. |
| Context | OIDC sign-in |
| Not to confuse with | Not permission to transition any request. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Standard distinction |

#### TERM-012 — Authorization

| Field | Meaning |
| --- | --- |
| Canonical definition | Decision whether a verified subject may perform a particular operation on an actual resource. |
| Context | Backend transition check |
| Not to confuse with | Not merely successful login or a hidden UI button. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Architecture requirement |

#### TERM-015 — Business Role

| Field | Meaning |
| --- | --- |
| Canonical definition | A business responsibility such as requester, coordinator or technician. |
| Context | IT intake coordinator |
| Not to confuse with | Not an exact JWT authority or resource grant. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Candidate role model |

#### TERM-016 — Canonical Subject ID

| Field | Meaning |
| --- | --- |
| Canonical definition | The normalized identity key accepted by the trusted Backend to refer to the verified subject. |
| Context | Verified principal |
| Not to confuse with | Not a client-controlled X-User header. |
| Reference | [GitHub Issue #24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24) |
| Qualification | Exact mapping needs code review |

#### TERM-024 — Exact Authority

| Field | Meaning |
| --- | --- |
| Canonical definition | An operation-specific privilege that the backend requires for an action. |
| Context | Workflow create/read/transition |
| Not to confuse with | Not a broad business role by itself. |
| Reference | [GitHub Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23) |
| Qualification | Exact tokens require contract verification |

#### TERM-032 — Identity Provider (IdP)

| Field | Meaning |
| --- | --- |
| Canonical definition | An external identity service responsible for authentication and identity claims. |
| Context | Keycloak |
| Not to confuse with | Not Workflow business authorization policy. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Provider choice described |

#### TERM-037 — JSON Web Token (JWT)

| Field | Meaning |
| --- | --- |
| Canonical definition | A token format carrying signed or otherwise protected claims subject to validation. |
| Context | OIDC resource access |
| Not to confuse with | Not inherently trustworthy without signature/issuer/audience checks. |
| Reference | [GitHub Issue #24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24) |
| Qualification | Technical term |

#### TERM-044 — OpenID Connect (OIDC)

| Field | Meaning |
| --- | --- |
| Canonical definition | Identity protocol layer over OAuth 2.0 used for authenticated sign-in. |
| Context | Keycloak login |
| Not to confuse with | Not the resource-specific authorization decision. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Selected flow direction |

#### TERM-050 — Proof Key for Code Exchange (PKCE)

| Field | Meaning |
| --- | --- |
| Canonical definition | An OAuth 2.0 extension protecting authorization-code exchange for public clients. |
| Context | Browser sign-in |
| Not to confuse with | Not replacement for backend authorization. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Recommended by source issue |

#### TERM-052 — Resource Grant

| Field | Meaning |
| --- | --- |
| Canonical definition | Permission binding a subject or group to a specific resource/action. |
| Context | Request-specific access |
| Not to confuse with | Not merely possessing a valid token. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Business/security mapping pending |

#### TERM-053 — Resource Visibility

| Field | Meaning |
| --- | --- |
| Canonical definition | Whether a caller is permitted to know a resource exists or access its representation. |
| Context | Visible-forbidden 403 vs hidden 404 |
| Not to confuse with | Not interchangeable with transition permission. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Security design requirement |

#### TERM-060 — Tenant Isolation

| Field | Meaning |
| --- | --- |
| Canonical definition | Prevention of access to another tenant’s protected resources. |
| Context | Cross-tenant negative test |
| Not to confuse with | Not achieved by client-side filtering alone. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Verification pending |

#### TERM-065 — Verified Principal

| Field | Meaning |
| --- | --- |
| Canonical definition | A server-trusted identity derived from appropriately validated authentication evidence. |
| Context | OIDC handoff |
| Not to confuse with | Not request header values supplied by a browser. |
| Reference | [GitHub Issue #24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24) |
| Qualification | Implementation evidence requires review |

### Workflow Terms

#### TERM-025 — Expected Version

| Field | Meaning |
| --- | --- |
| Canonical definition | The version supplied by a caller to guard a state mutation against stale updates. |
| Context | Transition request |
| Not to confuse with | Not an arbitrary timestamp or blind retry token. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Contract-specific field to verify |

#### TERM-034 — Invalid Transition

| Field | Meaning |
| --- | --- |
| Canonical definition | An attempted state change disallowed by the applicable definition or authorization rules. |
| Context | Negative path test |
| Not to confuse with | Not a generic HTTP 500 error. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Behavior must be specified |

#### TERM-046 — Optimistic Concurrency

| Field | Meaning |
| --- | --- |
| Canonical definition | Concurrency control in which a mutation succeeds only if the expected version remains current. |
| Context | Stale version response |
| Not to confuse with | Not proof that concurrent writes were tested. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Runtime verification pending |

#### TERM-059 — State

| Field | Meaning |
| --- | --- |
| Canonical definition | A named position within a particular Workflow Definition. |
| Context | SUBMITTED in a candidate Helpdesk flow |
| Not to confuse with | Not a document lifecycle status. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | State labels are candidate |

#### TERM-063 — Transition

| Field | Meaning |
| --- | --- |
| Canonical definition | An allowed action moving a Workflow Instance between states under defined rules. |
| Context | Resolve request |
| Not to confuse with | Not direct manual editing of a state field. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Exact action keys unratified |

#### TERM-066 — Workflow Definition

| Field | Meaning |
| --- | --- |
| Canonical definition | A versioned description of states, transitions and relevant constraints. |
| Context | Helpdesk vs Facility definitions |
| Not to confuse with | Not a live instance or implementation approval. |
| Reference | [GitHub Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) |
| Qualification | Version semantics require contract review |

#### TERM-068 — Workflow History

| Field | Meaning |
| --- | --- |
| Canonical definition | Persisted record of state changes and associated transition events. |
| Context | Authorized timeline UI |
| Not to confuse with | Not interchangeable with application activity comments. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Expected Core ownership |

#### TERM-069 — Workflow Instance

| Field | Meaning |
| --- | --- |
| Canonical definition | A specific execution of a Workflow Definition with its own state and version. |
| Context | One employee request lifecycle |
| Not to confuse with | Not the reusable definition itself. |
| Reference | [GitHub Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) |
| Qualification | Architecture term |

### Data Terms

#### TERM-010 — Audit Event

| Field | Meaning |
| --- | --- |
| Canonical definition | An attributable record of a security-relevant or business-relevant event. |
| Context | Transition audit |
| Not to confuse with | Not automatically identical to workflow history schema. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Data model verification required |

#### TERM-017 — COMMIT_OUTCOME_UNKNOWN

| Field | Meaning |
| --- | --- |
| Canonical definition | An outcome in which the caller cannot determine whether a write transaction committed; reconciliation is required before replay. |
| Context | Lost response after database commit |
| Not to confuse with | Not permission to retry the same write blindly. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Symbol/contract exactness requires source review |

#### TERM-020 — Data Ownership

| Field | Meaning |
| --- | --- |
| Canonical definition | Assignment of authoritative responsibility for an entity, fact or state. |
| Context | Core workflow state; Application business facts |
| Not to confuse with | Not permission for the Portal to update DB tables directly. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Boundary pending ratification |

#### TERM-043 — Object Storage

| Field | Meaning |
| --- | --- |
| Canonical definition | Storage for unstructured objects such as authorized attachments. |
| Context | Private MinIO behind Backend adapter |
| Not to confuse with | Not an object store directly credentialed from browser. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Provider configuration selected |

#### TERM-047 — Persistence

| Field | Meaning |
| --- | --- |
| Canonical definition | Durable storage and retrieval of domain information through defined contracts. |
| Context | PostgreSQL state/history |
| Not to confuse with | Not a guarantee of cross-store atomicity. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Implementation proof separate |

#### TERM-062 — Transaction

| Field | Meaning |
| --- | --- |
| Canonical definition | A unit of work with defined commit/rollback boundaries. |
| Context | Database state and history update |
| Not to confuse with | Not automatically atomic across PostgreSQL and MinIO. |
| Reference | [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25) |
| Qualification | Exact scope needs review |

### Integration Terms

#### TERM-013 — Backend for Frontend (BFF)

| Field | Meaning |
| --- | --- |
| Canonical definition | An intermediary tailored to frontend interaction while preserving secure backend boundaries. |
| Context | Portal/BFF |
| Not to confuse with | Not a shortcut to privileged DB access. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Topology planned |

#### TERM-030 — HTTP Contract

| Field | Meaning |
| --- | --- |
| Canonical definition | The documented request, response, error and authorization semantics of an HTTP operation. |
| Context | Portal–Backend handoff |
| Not to confuse with | Not proof an endpoint is registered and ACTIVE. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Publication verification required |

#### TERM-031 — Identity Handoff

| Field | Meaning |
| --- | --- |
| Canonical definition | Passing a verified principal across trusted components without accepting forged identity headers. |
| Context | OIDC to Gateway/Backend |
| Not to confuse with | Not parsing an unverified browser-supplied role. |
| Reference | [GitHub Issue #24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24) |
| Qualification | Runtime test evidence separate |

#### TERM-038 — Layer 2 Portal

| Field | Meaning |
| --- | --- |
| Canonical definition | Independently buildable business UI consuming Headless Core through published API. |
| Context | Internal Service Portal |
| Not to confuse with | Not part of the Core algorithm. |
| Reference | [GitHub Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) |
| Qualification | Proposed Layer 2 |

#### TERM-045 — Operation Availability

| Field | Meaning |
| --- | --- |
| Canonical definition | Evidence-backed declaration whether an endpoint is ACTIVE, PLANNED or BLOCKED. |
| Context | API inventory |
| Not to confuse with | Not the same as MoSCoW priority. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Status requires evidence |

### Quality Terms

#### TERM-002 — Acceptance Evidence

| Field | Meaning |
| --- | --- |
| Canonical definition | Immutable or reproducible records demonstrating a specific criterion was checked. |
| Context | Test log pinned to commit |
| Not to confuse with | Not an unverified assertion or screenshot alone. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Governance expectation |

#### TERM-022 — End-to-End (E2E) Test

| Field | Meaning |
| --- | --- |
| Canonical definition | Test exercising an integrated journey across intended runtime boundaries. |
| Context | Real Portal–Backend–DB flow |
| Not to confuse with | Not mock-only UI demonstration. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Runtime verification pending |

#### TERM-023 — Evidence Baseline

| Field | Meaning |
| --- | --- |
| Canonical definition | Exact code/docs revisions and run context against which claims are evaluated. |
| Context | Code SHA and docs SHA |
| Not to confuse with | Not merely latest main. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | G0/G1 baseline pending |

#### TERM-042 — Negative Test

| Field | Meaning |
| --- | --- |
| Canonical definition | Test confirming rejection or safe handling of forbidden, invalid or conflicted actions. |
| Context | 401/403/404, version conflict |
| Not to confuse with | Not a substitute for successful-path tests. |
| Reference | [GitHub Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) |
| Qualification | Evidence required |

#### TERM-055 — Reuse Verification

| Field | Meaning |
| --- | --- |
| Canonical definition | Independent check that multiple business definitions run over unchanged Core behavior. |
| Context | Helpdesk and Facility |
| Not to confuse with | Not established by writing two definitions alone. |
| Reference | [GitHub Issue #27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) |
| Qualification | Pending demonstration |

#### TERM-064 — Verified Claim

| Field | Meaning |
| --- | --- |
| Canonical definition | A statement supported by relevant evidence scoped to a particular baseline. |
| Context | Pinned integration run result |
| Not to confuse with | Not equivalent to a proposed design. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Documentation convention |

### Governance Terms

#### TERM-005 — ADR Lifecycle

| Field | Meaning |
| --- | --- |
| Canonical definition | Decision stages Proposed, In Review, Accepted, Rejected and Superseded. |
| Context | ADR index |
| Not to confuse with | Not document lifecycle. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | F07 governance draft |

#### TERM-008 — Architecture Decision Record (ADR)

| Field | Meaning |
| --- | --- |
| Canonical definition | A record of architectural context, alternatives, decision, rationale, consequences and verification. |
| Context | ADR governance index |
| Not to confuse with | Not an implementation completion report. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | Registry needs verification |

#### TERM-021 — Document Lifecycle

| Field | Meaning |
| --- | --- |
| Canonical definition | Document stages Planned, Draft, In Review, Approved and Superseded managed in the Document Register. |
| Context | F03 register |
| Not to confuse with | Not the Gate verdict. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | G0 design convention |

#### TERM-027 — Gate Outcome

| Field | Meaning |
| --- | --- |
| Canonical definition | Acceptance verdict PASS, CONDITIONAL PASS, BLOCKED or NOT REVIEWED. |
| Context | G0 and G1 Gate records |
| Not to confuse with | Not a document status or API availability. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | G0 design convention |

#### TERM-033 — Independent Reviewer

| Field | Meaning |
| --- | --- |
| Canonical definition | A reviewer sufficiently separate from the primary author to assess evidence objectively. |
| Context | File review |
| Not to confuse with | Not automatically a Gate Approver. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | Assignment must be verified |

#### TERM-035 — Issue Lifecycle

| Field | Meaning |
| --- | --- |
| Canonical definition | Tracking state of an unresolved item, e.g. Open, In Progress, Pending Verification, Closed. |
| Context | Open Issues register |
| Not to confuse with | Not an ADR lifecycle or code test verdict. |
| Reference | [GitHub Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) |
| Qualification | G0 design convention |

#### TERM-056 — Review Baseline

| Field | Meaning |
| --- | --- |
| Canonical definition | The exact revision set presented for formal review. |
| Context | Pinned code/docs SHA |
| Not to confuse with | Not an arbitrary historical local HEAD. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | G0 baseline not yet approved |

#### TERM-057 — Scope Priority

| Field | Meaning |
| --- | --- |
| Canonical definition | MUST, SHOULD, COULD or OUT as a planning classification. |
| Context | MVP item |
| Not to confuse with | Not ACTIVE, Implemented or Verified. |
| Reference | [GitHub Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) |
| Qualification | G1 scope convention |

## 5. High-Risk Distinctions and Examples

**Authentication vs authorization.** An employee can complete an OIDC login and still be forbidden from transitioning a Facility request they do not own or cannot access. Identity validation does not supply a resource-specific grant.

**Business role vs exact authority vs resource grant.** A technician role expresses organizational responsibility. An exact authority expresses a permitted operation. A resource grant scopes an action to an actual resource or tenant. Passing any single check does not eliminate other required checks. The exact authority strings remain subject to the published code contract.

**Workflow Definition vs Workflow Instance vs State.** A Helpdesk definition can specify candidate states; one employee’s request is a separate instance with its own current state and version. A state label in Issue #53 is not an implemented transition or an approved contract.

**Workflow history vs audit event.** History records lifecycle changes. Audit can cover security decisions, denied access or administrative actions as well; overlap does not prove the records have the same schema.

**Unknown commit vs retry.** If an HTTP response is lost after a transaction may have committed, `COMMIT_OUTCOME_UNKNOWN` is a reconciliation problem. Blindly issuing the same write again risks duplicate effects.

**Document status vs ADR status vs Issue status vs Gate outcome.** `Approved` can describe a document, `Accepted` an ADR, `Closed` an issue and `PASS` a Gate. None establishes that a runtime feature is `ACTIVE` or `Verified`.

**Example — candidate Facility workflow:** A physical repair may require intake, assignment, repair, acceptance and closure, but those steps are **candidate business semantics** until contract approval and exact definition/HTTP verification.

## 6. Unresolved Terminology and No-Guess Rules

| Item | Open question | Required resolution |
| --- | --- | --- |
| Business workflow state names and action keys | Issue #53 and #27 describe differing candidate terminal-state detail | Ratify the actual business contract, compare with deployed definition |
| `workflow:create` and other exact authority literals | Wording in issues may differ from code authority constants | Inspect pinned Backend source and security tests |
| `expectedVersion` request syntax | Exact field name/error mapping varies with API contract | Verify published OpenAPI/DTO and tests |
| `COMMIT_OUTCOME_UNKNOWN` | Behavioral intent described; exact symbol and propagation not directly code-verified here | Verify source contract, transaction tests and no-blind-retry behavior |
| ADR IDs and Accepted status | Complete ADR file inventory not confirmed | Inventory tracked ADR files and preserve existing IDs |
| Roles, actor eligibility and resource grant matrix | Business contract remains DRAFT | Review actor-by-transition matrix and independent security checks |

## 7. Maintenance, Traceability and Acceptance

1. When adding a term, search existing canonical terms and aliases before assigning the next unused ID; **do not renumber existing IDs** on sorting. The index is alphabetical, but IDs are permanent identifiers, not positions.
2. When changing a canonical meaning, perform impact analysis against the product overview, problem statement, Scope & MVP, contracts, ADRs and tests. Submit a reviewed change; preserve prior meaning through Git history and link the relevant decision.
3. Use relative links to internal documents **only after confirming the exact paths exist** in the reviewed repository; this draft names internal paths as plain code for that reason. Source claims about implementation need pinned commit/test evidence, not a moving branch reference.
4. Before claiming the glossary approved, verify term uniqueness, contract and ADR consistency, Markdown/link checks and independent reviewer sign-off at a known code/docs baseline. Until then it remains a working draft.

## 8. Related Documents (paths to verify)

- `docs/00-project/project-overview.md` — project framing (G1-F01).
- `docs/00-project/problem-statement.md` — hypotheses and stakeholder needs (G1-F02).
- `docs/00-project/scope-and-mvp.md` — MoSCoW, golden paths and evidence boundary (G1-F04).
- `docs/00-project/document-style-guide.md` — authoring conventions (G0-F08).
- `docs/00-project/document-register.md` — canonical document lifecycle and ownership (G0-F03).
- `docs/08-decisions/README.md` — proposed ADR governance (G0-F07).

**Document preparation outcome:** DRAFT; independent acceptance NOT REVIEWED. **G1 Gate:** NOT REVIEWED.
