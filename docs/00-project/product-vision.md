# DX-OS Open-Core — Product Vision

> **Document ID:** G1-F03  
> **Purpose:** Express the product's intended value, strategic direction and falsifiable platform-reuse hypothesis.  
> **Scope:** Internal service applications and a reusable Headless Core; not a business-contract approval, implementation report or release claim.

## 1. Purpose and Qualification

This **problem-driven platform vision** connects the hypothetical needs in the [Problem Statement](problem-statement.md) to the bounded commitments in [Scope and MVP](scope-and-mvp.md). The underlying multi-department organization is a **reference scenario**, not a surveyed customer. No customer demand, savings, operational improvements, competitive superiority, deployed feature or end-to-end (E2E) outcome is asserted as verified by this document.

**Terminology:** Use the [Canonical Glossary](glossary.md) for the distinction between Core, Application, Workflow Definition, Authentication, Authorization, evidence and approval. The [Project Overview](project-overview.md) provides a broader reader entry point. Those paths must be checked against the reviewed documentation commit before approval.

## 2. Vision Statement

**DX-OS Open-Core aims to provide a reusable, security-conscious foundation for internal service applications that need consistent identity, authorized workflow execution and traceable data handling. It seeks to let different service domains use published platform contracts without embedding domain-specific rules in the reusable Core. Initial validation focuses on a narrow Internal Service Portal and demonstrable end-to-end and cross-workflow evidence rather than a claim of general enterprise adoption.**

This statement is a **north-star aspiration**, not a declaration that the target architecture, integrations or outcomes have been delivered. The primary product proposition is not simply a better ticket screen; it is the ability to preserve reliable cross-cutting behavior while application-specific workflows vary.

## 3. Intended Users and Value

| Audience | Candidate need (not surveyed) | Intended value | Boundary of claim |
| --- | --- | --- | --- |
| Requester / employee | Know where a request stands and who is responsible | Authorized request status and history visibility | Subject to API authorization and real user validation |
| Intake/coordinator and service workers | Route requests and perform allowed work | Clear next actions, responsibility and controlled transitions | Actor eligibility and actual business rules need ratification |
| Manager / acceptance actor | Understand decisions and authorize relevant closure | Traceable actions and explicit acceptance/rejection where supported | Not a promise of a complete analytics suite |
| Internal application developer | Avoid recreating common lifecycle and security mechanisms | Reusable identity, gateway, workflow and data contracts | Reuse must be demonstrated without Core algorithm changes |
| Platform operator / maintainer | Operate and investigate the platform safely | Repeatable local setup, least privilege and inspectable evidence | Production operability is outside the demo claim |

The user-side and developer-side benefits are **value hypotheses**. They may be rejected or revised after interviews, technical measurements or end-to-end evaluation.

## 4. Problem-to-Value Alignment

| Source hypothesis | Intended product response | Outcome hypothesis | Validation route |
| --- | --- | --- | --- |
| PP-01: request status may be unclear | Authorized state/history views | Requesters can retrieve current progress | Structured user task study and HTTP access checks |
| PP-02: intake handoffs may be fragmented | Explicit eligible actions and processing workspace | Coordinators can determine the next valid action | Candidate workflow walkthrough and actor-policy tests |
| PP-03: worker context may be incomplete | App-owned facts associated with authorized request operations | Workers can access information needed for their assignments | User review and resource-visibility tests |
| PP-04: decision history may be scattered | Core-owned transition history with an audit trail | Reviewers can reconstruct a permitted action sequence | Persistence/history integrity and authorization tests |
| PP-05: capabilities may be duplicated across services | One stable set of Core contracts plus separate workflow definitions | A second domain can reuse Core without domain hardcoding | Source diff, contract tests and two-definition E2E test |

`PP-01`–`PP-05` are **unvalidated hypotheses** from G1-F02, not customer findings. The matrix is conceptual traceability; precise requirement and test IDs belong in the later requirements and test matrices.

## 5. Strategic Product Objectives

### 5.1 Business Outcomes

- Make a request's **authorized** progress, assigned responsibility and relevant history understandable to its participants.
- Represent processing, completion and exceptional paths as explicit, reviewable business operations rather than arbitrary status updates.
- Support distinct internal request domains while retaining separate application-owned forms, facts and eligibility policies.

### 5.2 Platform Outcomes

- Expose the smallest useful **Headless Core** capabilities for Identity, Gateway, Workflow and Data.
- Protect stable published contracts and domain boundaries so applications consume authorized APIs rather than Core internals or the database.
- Establish a measurable basis for **reuse**: distinct workflow definitions, consistent Core semantics and no second-domain changes to the Core algorithm.

### 5.3 Engineering Outcomes

- Preserve verified principal and resource/operation authorization at the server; UI visibility is not an authorization decision.
- Demonstrate state/history consistency, meaningful failure semantics and controlled concurrency through tests at an exact source revision.
- Enable reproducible, private local installation and a transparent separation between implemented, planned and blocked behavior.

These are intended outcomes. A completed implementation or passed acceptance test must be documented in its own evidence record.

## 6. Product Principles

| Principle | Required product interpretation |
| --- | --- |
| Security by design | Authenticate identities and authorize the actual operation/resource server-side; no forged user headers or browser-held privileged credentials |
| Core/Application separation | Workflow Core owns state, definitions and history; applications own domain-specific facts, forms and business rules |
| Explicit contracts | Consumers use published, reviewed HTTP/API contracts; missing endpoints are not represented as ACTIVE |
| Verifiability | Treat a design statement, code existence, integration test and independent acceptance as distinct claims |
| Reuse before breadth | Prove a second genuinely distinct workflow with unchanged Core processing rather than accumulating unrelated features |
| Minimal viable scope | Prioritize a credible IT Helpdesk golden path, safe negative tests and demonstrable reproducibility over speculative platform breadth |
| Operational transparency | Label mock data, proposed transitions, pending evidence and unsupported behavior unambiguously |

## 7. Positioning and Alternatives

| Approach | Potential merit | Trade-off / open question |
| --- | --- | --- |
| Manual or messaging-based handling | Familiar workflow; minimal initial tooling | Whether visibility, accountability or history actually cause pain **must be studied**, not assumed |
| Dedicated off-the-shelf Helpdesk software | May satisfy IT requests with lower initial development effort | May not establish the project-specific cross-domain Core reuse objective; comparative evaluation not yet conducted |
| Custom application-specific service modules | Direct match to one domain and near-term UX | Duplicate cross-cutting behavior is a **hypothesis**, not a measured cost |
| Proposed DX-OS Open-Core + independent Portal | Offers testable common semantics across two candidate request domains | Increases Core-contract, security and integration responsibilities; reuse value remains unproven |

No comparative product benchmark or total-cost-of-ownership study has been performed. No competitor is claimed to be inferior.

## 8. Target Product Shape and MVP Boundary

**North star:** reusable, provider-conscious Core contracts support multiple independently designed internal service applications. This aspiration does **not** create a commitment to ERP, all enterprise workflows, microservices, a BPMN designer or AI features.

**Candidate committed MVP** (the authoritative MoSCoW register is [Scope and MVP](scope-and-mvp.md)):

1. **Layer 1:** Identity/SSO, trusted Gateway, definition-driven Workflow and controlled Data persistence/storage boundaries.
2. **Layer 2:** an independently runnable Internal Service Portal consuming published Backend/Gateway HTTP APIs; never direct database access or Java Core injection.
3. **Primary evidence path:** IT Helpdesk request creation, authorized processing, resolution and authorized confirmation/closure with durable state and history; exact transitions depend on a ratified business contract.
4. **Reuse candidate:** Facility Maintenance uses a distinct Workflow Definition and application-owned business facts against the **same unchanged Core**. Specific Facility steps and acceptance depth remain conditional on contract and API evidence.
5. **Conceptual exercise only:** Access Request illustrates possible extensibility; it is **not** a third required application implementation.

A proposed `MUST` remains a *scope priority*, not evidence of an `ACTIVE` API, implemented feature or acceptance. The MVP excludes ERP/inventory/procurement, BPMN editing, AI, speculative microservices rewriting and nonessential expansion.

## 9. Reuse Hypothesis and Falsifiable Proof

**H-VIS-01 — Cross-domain reuse hypothesis:** If IT Helpdesk and Facility Maintenance can execute distinct approved Workflow Definitions using the same pinned reusable Core implementation and published contract without modifying Core algorithms for Facility-specific rules, then there is evidence supporting cross-domain Core reuse.

The planned proof must capture: (a) two different definitions and their domain distinctions; (b) the same pinned Core revision and public contracts; (c) real protected API operations and persisted history; (d) positive and negative actor/resource/version checks; (e) a code diff/architecture review showing application-specific changes are outside Core; (f) independent review of the result.

**Counterevidence:** Facility can run only by adding department-specific conditionals to Core, bypassing authorized APIs, weakening security, directly changing state, or relying solely on mocked transitions. That would not support the stated reuse hypothesis. A conceptual diagram or a second UI screen alone is insufficient proof.

## 10. Success Indicators and Validation

No value below has a measured baseline. **All current baselines: `BASELINE UNKNOWN`.** Do not infer improvement percentages or ROI.

| Candidate indicator | Baseline | How to validate | Required evidence before claim |
| --- | --- | --- | --- |
| Requester ability to find state/assignee | BASELINE UNKNOWN | Timed task and user observation in a defined scenario | Task protocol, sample and raw observations |
| Visibility of authorized history | BASELINE UNKNOWN | Access-controlled request/history checks | Exact-SHA HTTP assertions and negative tests |
| Invalid or unauthorized operation handling | BASELINE UNKNOWN | 401, visible 403, hidden 404, cross-resource and invalid transition tests | Reproducible test logs and reviewer conclusion |
| State/history consistency under conflicts | BASELINE UNKNOWN | Stale version, concurrent writer and uncertain COMMIT recovery tests | PostgreSQL-backed test evidence at pinned revision |
| Effort to introduce a second definition | BASELINE UNKNOWN | Compare application/definition changes vs Core diff | Change set, two-definition test and architecture review |
| Clean local installation/restart | BASELINE UNKNOWN | Reproduce setup in isolated environment | Commands, environment versions, output and reviewer evidence |

The project may later specify targets with a justified measurement protocol. No acceptance threshold is invented here.

## 11. Assumptions, Risks and Evidence Boundaries

| ID | Assumption or risk | Consequence | Response / open validation |
| --- | --- | --- | --- |
| PV-R01 | Reference organization and user pain points have not been validated | Business value may be less than assumed | Interviews/task study; retain hypotheses until evidence exists |
| PV-R02 | Business Contract v1.2 in [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) is draft | Workflow actor/transition assumptions may change | Ratify application rules before claiming contract acceptance |
| PV-R03 | Protected API handoff may be incomplete | Portal demonstration may remain mocked or blocked | Verify API inventory and #22/#23/#24/#39 evidence |
| PV-R04 | Facility reuse requires Core changes | Platform reuse hypothesis could be falsified | Review pinned code diff, contracts and two-definition tests |
| PV-R05 | Data/storage integration, security or installation may be incomplete | End-to-end and release claims cannot be made | Verify #25/#26/#27/#40 and review evidence |
| PV-R06 | Scope expands beyond essential workflows | MVP deliverability and security review suffer | Apply MoSCoW and change control from G1-F04 and F02 |

**Project-source qualification:** [Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39) describes independent Portal/API boundaries; [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) records a local-demo direction with Keycloak/OIDC, private PostgreSQL 17 and MinIO and explicitly states full-stack E2E is not verified; [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) proposes Helpdesk/Facility business behavior but explicitly awaits ratification. Issue text is design/source context, **not** implementation or acceptance evidence. Review source snapshots and formal decisions before freezing this document.

## 12. Traceability, Governance and Next Decisions

| Vision component | Source / downstream location | Responsibility |
| --- | --- | --- |
| Reference problem and hypotheses | [Problem Statement](problem-statement.md) | Owns problem framing, AS-IS hypotheses and needs |
| Broad project definition | [Project Overview](project-overview.md) | Owns reader-level product introduction |
| MVP priorities, Golden Paths and exclusions | [Scope and MVP](scope-and-mvp.md) | Owns specific MUST/SHOULD/COULD/OUT and acceptance criteria |
| Canonical vocabulary | [Glossary](glossary.md) | Owns terminology; project-specific exact symbols require code verification |
| Document status and owner | G0 Document Register | Sole lifecycle authority; verify its tracked repository path before linking |
| ADR decisions and signed Gate review | G0 ADR governance / G1 review records | Must not be inferred from the creation of this draft |

**Next decisions:** validate customer problem assumptions; ratify the application business contract and exact Workflow Definitions; verify live Backend/API boundaries and two-definition reuse at pinned code/docs SHAs; confirm independent reviewer and approver. This document's preparation does not confer approval.
