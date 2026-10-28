# DX-OS — Product Vision and Positioning

> **Status:** PROPOSED — REVIEW REQUIRED
> **Baseline:** `origin/main` `e06fa429cfd92b032c166eb3ebf32803d13191c2`

## 1. Vision

**Enable teams to create secure internal-service applications faster by reusing a small set of trusted, provider-neutral capabilities rather than duplicating authentication, workflow, storage and API-boundary code.**

DX-OS is a **digital capability platform concept**, not a desktop/server operating system and not a claim that all proposed capabilities are already production-ready.

## 2. Product positioning

**For** small teams developing internal service tools, **who need** traceable requests and reliable access control, **DX-OS Open-Core is** a modular backend foundation providing Identity, Gateway, Workflow and Data contracts, **unlike** separately written ticket applications, **because** shared contracts and adapter conformance tests are intended to make reuse and technology replacement demonstrable.

This is a project hypothesis and technical positioning, **not** a verified commercial market claim.

## 3. Users vs integrators

| Audience | Value proposition | Product surface |
|---|---|---|
| Internal employee | Submit/track a service request | Portal / application API |
| Operator/technician | Manage eligible work | Business use cases |
| Manager/coordinator | View and assign requests | Business reporting and policy |
| Application developer | Reuse four Core capabilities | Internal contracts and selective public APIs |
| Infrastructure engineer | Replace deployment adapters while preserving contracts | Configuration, providers, test harness |

## 4. Product principles

1. **Core is mechanism, Application is policy.** No Helpdesk/FACILITY branches inside Core.
2. **Secure by verified boundary.** Never treat request-supplied user/role as verified identity.
3. **Stable contracts over vendor coupling.** SDK and database details belong to adapters.
4. **Correctness before generalized platform features.** Enforce atomicity, concurrency and failure semantics.
5. **A second app demonstrates reuse.** A new workflow definition alone is not proof.
6. **Evidence-backed claims.** Record baseline SHA, test environment and independent review.
7. **Open-source readiness.** Licensing, provenance, docs and clean install are release gates.

## 5. Core and application promise

| Capability | Product promise | Boundary |
|---|---|---|
| Identity | Provider-neutral principal and authority representation | Does not implement a custom password/SSO database |
| Gateway | Common authenticated HTTP boundary and controlled exposure | Does not determine ticket-specific business permissions alone |
| Workflow | Reusable state transitions/history/versioning | Does not define Helpdesk-specific authorization |
| Data | Provider-neutral object storage operations | Does not own all business tables |
| Helpdesk | End-to-end IT request handling | Business rules belong here |
| Facility | Proves another domain can consume the same Core | Domain-specific process must be independently defined |

## 6. Experience storyboards (targets)

**Employee / Helpdesk:** Authenticate → create request → view progression → acknowledge resolution → close according to approved business contract.

**Facility operator:** Authenticate → view eligible facility work → carry out and record an allowed transition → retrieve authorized attachment, using the same identity and storage contracts.

**Developer:** Implement Facility API/use cases/definition → connect existing Core ports → run contract/security tests → demonstrate no Core domain changes.

## 7. What the project will not promise

- Universal replacement of arbitrary IdPs/databases without adapters, migration and conformance tests.
- Distributed transactions between PostgreSQL and object storage.
- A full enterprise BPMN engine, timer/parallel orchestration or multi-region HA in MVP.
- Security based on JWT role alone without Application resource policy.
- Release quality solely because CI on a feature branch passed.

## 8. Product success criteria (non-numeric baseline)

| Goal | Objective evidence |
|---|---|
| Useful request app | Authenticated Helpdesk golden path demonstrated |
| Trusted security | 401/403/404/negative authorization cases verified |
| Reliable state changes | Persistence, conflict and unknown-commit semantics tested |
| Reuse | Facility slice consumes same Core without modifying Core internals |
| Replaceability | Two storage adapters satisfy the same contract suite |
| Installability | Clean install from documented configuration |
| Open-source compliance | Reviewed license/attribution and reproducible release artifacts |

Quantitative user impact and SLAs require a measured baseline and are not yet claimed.

## 9. Strategy and priorities

**Now:** Stabilize four Core boundaries, complete Helpdesk integration, produce Facility reuse proof, and make the demo reproducible.

**Next (conditional):** Review provider migration procedures, stronger observability and advanced workflow features if users or acceptance evidence justify them.

**Out of scope for current MVP:** Microservice split, BPMN replacement, extensive generic administration and speculative platform modules.

## 10. Open product decisions

| ID | Decision needed | Proposed owner |
|---|---|---|
| PV-01 | Exact Facility vertical-slice lifecycle and roles | Application |
| PV-02 | Required Helpdesk close/confirm semantics | Application and Backend |
| PV-03 | Which provider is mandatory for clean install | Data and Operations |
| PV-04 | Minimal public API exposure and BFF arrangement | Gateway/Security |
| PV-05 | Evidence baseline for OLP acceptance | Entire team |

## 11. References

- [Overview](project-overview.md)
- [Problem Statement](problem-statement.md)
- [Scope and MVP](scope-and-mvp.md)
- [System Architecture](../01-architecture/system-architecture.md)

**Sign-off:** Product owner — PENDING; Architecture reviewer — PENDING.
