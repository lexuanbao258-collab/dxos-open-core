# DX-OS — Project Glossary

> **Status:** PROPOSED — TERMINOLOGY REVIEW
> **Baseline:** `origin/main` `e06fa429cfd92b032c166eb3ebf32803d13191c2`
> **Usage:** Shared meanings for Architecture, Core contracts and Application documents. Exact Java semantics prevail over informal description.

## 1. Product and architecture

| Term | Meaning in DX-OS |
|---|---|
| DX-OS Open-Core | Reusable digital capability platform concept for internal service apps, not a computer OS |
| Core capability | Shared technical mechanism with defined contracts, owner and invariants |
| Application | Domain-specific use cases, permissions, API payloads and business data |
| Modular Monolith | One backend deployable with logically separated modules; BFF may be separate |
| Hexagonal Architecture | Domain/application ports with infrastructure adapters at boundaries |
| Dependency Inversion Principle (DIP) | High-level modules depend on abstractions; adapters implement and depend on those abstractions |
| Port | Stable operation and behavioral contract owned by the consumer/capability |
| Adapter | Technology-specific implementation of a port or external interface |
| Contract test | Test verifying implementation semantics, not just Java interface compatibility |
| Golden Path | Demonstrated end-to-end scenario satisfying business and quality acceptance |
| Vertical slice | Minimal complete feature across UI/API, policy, domain, persistence and verification |
| ADR | Architecture Decision Record recording context, options, choice and consequences |
| AS-IS / TO-BE | Hypothesized current process / proposed target; AS-IS may need field validation |
| Baseline SHA | Commit identifier on which source/evidence claims are based |

## 2. Identity and authorization

| Term | Meaning |
|---|---|
| Authentication (AuthN) | Establishing a verified caller identity |
| Authorization (AuthZ) | Deciding what an established caller may do |
| Identity Provider (IdP) | External service authenticating users, e.g., Keycloak |
| OIDC | OpenID Connect authentication protocol layer atop OAuth 2.0 |
| OAuth2 access token | Token used for authorized API access; distinct from browser session and ID token |
| Principal | Identity representation returned by trusted verification boundary |
| `CurrentPrincipal` | Application port whose adapter must return a verified principal |
| Authority | String-backed operation permission fact; DX-OS uses exact matching where specified |
| Resource ACL | Resource-level access control policy; cannot be replaced by JWT role check alone |
| Tenant | Organizational boundary relevant to the application's resource authorization policy |
| Canonical subject | Adapter representation combining issuer and subject; not necessarily stable across IdP migration |
| BFF | Backend-for-Frontend; browser-session-facing intermediary responsible for OAuth/session handling where deployed |
| CSRF | Cross-Site Request Forgery, relevant to cookie-authenticated BFF requests |
| Trust boundary | Point where untrusted input must be verified before use as authority |

## 3. Workflow

| Term | Meaning |
|---|---|
| Workflow Definition | Immutable identified/versioned set of states/transitions |
| Workflow Instance | One runtime execution bound to exact definition version and resource |
| State | Position within a workflow definition |
| Transition | Named allowed move from source state to target state |
| Transition ID | Identifier selecting a definition-owned transition; client must not choose arbitrary target state |
| `WorkflowRuntime` | Provider-neutral orchestration over domain and persistence port |
| `WorkflowPersistencePort` | Workflow-owned contract for authoritative reads and durable atomic writes |
| `definitionVersion` | Version of the immutable workflow definition |
| `runtimeVersion` | Optimistic-concurrency version of a running instance |
| Lifecycle | NOT_STARTED, ACTIVE or COMPLETED in the generic engine baseline |
| History | Persisted successful transition records ordered by resulting version |
| `VERSION_CONFLICT` | Write rejected because expected runtime state/version is stale |
| `COMMIT_OUTCOME_UNKNOWN` | Write result unknown; may or may not have committed, requiring reconciliation |
| Optimistic concurrency | Rejecting stale updates instead of overwriting a concurrent winner |
| TOCTOU | Time-of-check to time-of-use race, including ACL revoke vs write |

## 4. Data and storage

| Term | Meaning |
|---|---|
| Data Core | Provider-neutral shared object-storage capability in current contracts; not universal owner of all DB tables |
| Object Storage | Key-addressed storage of bytes and metadata, e.g., filesystem or S3-compatible service |
| `ObjectStoragePort` | Shared `store`, `read`, `metadata`, `delete` contract |
| `ObjectKey` | Opaque, case-sensitive logical object identifier |
| `ObjectMetadata` | Content type, exact byte length and custom metadata |
| `CREATE_ONLY` | Atomic conditional create; existing object yields CONFLICT |
| `OVERWRITE` | Replace object content and metadata; current contract has no expected-version CAS |
| Streaming | Transferring content via stream without requiring full in-memory byte buffer |
| Object orphan | Stored object that no longer has a valid business metadata reference |
| Compensation | Explicit recovery action after partial success across stores |
| Reconciliation | Comparing authoritative state after uncertain outcomes to plan safe recovery |
| Provider migration | Moving existing object/database data and confirming integrity when switching providers |
| Flyway migration | Versioned relational schema change; applied migration should not be rewritten |

## 5. Testing, review and evidence

| Term | Meaning |
|---|---|
| Unit test | Isolated logic/contract validation |
| Integration test | Test involving actual adapters, security/runtime or database resources |
| E2E | End-to-end scenario across agreed components, not merely a mock controller |
| Conformance suite | Same contract expectations run against different provider implementations |
| Evidence | Command output, JUnit XML, log, screenshot or report pinned to commit/environment |
| `PROPOSED` | Design suggested, not approved |
| `ACCEPTED` | Decision approved, not necessarily implemented |
| `IMPLEMENTED` | Corresponding code exists on a specified commit |
| `VERIFIED` | Executed acceptance with recorded evidence |
| `LIVE_BLOCKED` | Live/production-like use is not authorized because required gate is incomplete |
| Legacy prototype | Earlier `request/*` application code; not automatically a generic Core API |

## 6. Terms requiring business confirmation

| Term | Why confirmation is required |
|---|---|
| Requester vs Employee | Eligibility and API actor terminology may differ |
| Technician vs Assignee | Current assignment and operation privileges must be explicit |
| Manager vs Coordinator | Review/triage/assign permissions need a business matrix |
| `CONFIRMED` vs `CLOSED` | Not interchangeable: requester acknowledgement and final closure may be distinct |
| Facility | Exact use cases, statuses and role matrix require sign-off |
| Audit event vs Workflow history | Audit scope may extend beyond successful state transitions |
| Priority / SLA | Categories, deadlines and enforcement not implied by generic Workflow Core |

## 7. Usage and consistency rules

- Prefer canonical Java type names for Core concepts; distinguish them from UI labels.
- Do not use 'user ID' to mean raw email, OIDC `sub` and canonical ID interchangeably.
- Do not call a PR-level test PASS a release verification.
- Do not claim Data Core owns every relational table.
- Amend terminology through reviewed documentation changes and track API/schema compatibility.

## 8. References

- [Project Overview](project-overview.md)
- [Problem](problem-statement.md)
- [Scope](scope-and-mvp.md)
- [Identity Contract](../contracts/identity-core.md)
- [Workflow Contract](../contracts/workflow-core.md)
- [Data Contract](../contracts/data-core.md)
- [Existing root glossary](../../GLOSSARY.md)

**Review:** Core owners and Application owner — PENDING.
