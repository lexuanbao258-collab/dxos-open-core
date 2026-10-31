# DX-OS Capability and Technology Baseline — Team Decision (2026-10-31)

**Decision:** Team-selected target stack, as explicitly approved for coordination on 2026-10-31.
**Status:** APPROVED TARGET DIRECTION; individual runtime integrations are NOT automatically verified or merged.
**Source baseline for inventory:** `main@2a94a46579c4d3d9999b0da831d9c3bb505cc3e2`.
**Governance:** Issue #87 (integration), #88 (canonical docs), #90 (source boundaries), #53 (business contract). This document supplements rather than silently supersedes accepted contracts or ADRs.

## 1. Required capability ownership and target technology

| Capability | DX-OS-owned contract | Selected target technology | Accountable team member | Integration status as of source baseline |
|---|---|---|---|---|
| Identity | Principal, Authority and provider-independent Identity contracts | **Keycloak + OpenID Connect (OIDC)** | Yến | Core types, Spring Security/OIDC adapter source present on main; real SSO end-to-end still requires evidence |
| Workflow | Generic Workflow/Process contracts, transition, authorization, version/history | **Flowable BPMN** | Bảo | Existing Java WorkflowRuntime and PostgreSQL persistence present; **Flowable adapter is a target, not a verified integrated engine** |
| Data | Structured Data + Object Storage contracts, ObjectStoragePort | **PostgreSQL + MinIO** | Bảo; Ánh reviews consumer semantics | PostgreSQL adapter source on main; MinIO in draft PR #34, not yet on main |
| Gateway | API and Security contracts, trusted principal propagation and authorization boundary | **Apache APISIX + Spring Boot Backend** | Yến (security/Gateway), Bảo (Backend runtime) | Spring Boot workflow Gateway source on main; **APISIX integration is a target, not verified on main** |
| Application | IT Helpdesk business rules/use cases, with Facility as reuse consumer | **Spring Boot + React + BFF** | Ánh (Application/React), Bảo (Helpdesk Backend), Yến (BFF session security) | Request backend on main; React PR #51/#71, BFF PR #70, Helpdesk candidate PR #74/#63 not integrated on main |

## 2. Non-negotiable architectural boundaries

1. **Core owns contracts, not vendors.** No Keycloak, Flowable, MinIO, APISIX or framework-specific dependencies leak into provider-neutral Core domain contracts.
2. **Infrastructure implements ports.** Keycloak OIDC adapter implements trusted Identity principal boundary; Flowable adapter (when introduced) implements Workflow/Process ports without replacing the stable public Core contracts; PostgreSQL and MinIO implement their storage contracts.
3. **Business policy belongs to Application.** IT Helpdesk defines ticket states, rules and resource ownership; Facility must reuse Core without special-casing generic Core for Facility.
4. **Gateway enforces trust, not business ownership.** APISIX is the selected target edge gateway. Existing Spring Boot authentication/authorization checks remain in force until an independently verified APISIX integration is approved; APISIX alone must not authorize resource-level transitions.
5. **BFF controls browser session.** React does not receive privileged server-side secrets; BFF handles OAuth2 login, cookies, CSRF and server-to-server calls.
6. **Monolith first.** Current Backend is a Spring Boot modular monolith; choosing APISIX and Flowable does not mandate splitting the runtime into microservices.

## 3. Migration rule: target does not equal MVP prerequisite

- **MVP executable path:** Existing Spring Boot WorkflowRuntime + PostgreSQL and Spring Security OIDC may be used as a documented temporary adapter/implementation baseline while testing Keycloak, Helpdesk and BFF.
- **Target path:** Flowable BPMN engine and Apache APISIX edge integration must be tracked as **separate gated implementation decisions and deliverables**. Do not claim these run on `main`, in a demo, or in CI without test evidence.
- An owner must document adapter contract, incompatibilities, migration sequence, rollback, security and repeatable tests before switching any runtime provider.
- Do not add both legacy and target implementations to the default runtime without explicit selection/feature flags and independent approval.

## 4. Implementation handoff checklist

### Yến — Identity and Gateway (Issues #23, #24, #89; PR #70)
- [ ] Verify Keycloak issuer/JWKS, OIDC callback, canonical subject, exact authority and forged header rejection.
- [ ] Verify BFF PKCE S256, state/CSRF, session cookie, logout, Host/Forwarded/TLS security on an isolated environment.
- [ ] Define APISIX edge routes, trust boundary and threat model as a **new gated adapter task**; confirm the Spring Boot security gate is not bypassed.
- [ ] Submit exact-SHA evidence to the cross-module test matrix; distinguish source inspection from real-provider tests.

### Bảo — Workflow, Data and Backend (Issues #22, #25, #26, #27, #87; PRs #34, #74/#63)
- [ ] Compare Backend candidates #74 and #63 and choose an integration SHA with preserved tests/evidence.
- [ ] Verify PostgreSQL atomicity, rollback, expectedVersion conflict and history integrity.
- [ ] Review and integrate MinIO ObjectStoragePort adapter #34 with isolated provider tests.
- [ ] Define a Flowable BPMN process adapter and compatibility mapping **without silently replacing** WorkflowRuntime; document BPMN versioning, authorization, actor and history equivalence.

### Ánh — Application and Portal (Issues #39, #53; PRs #51, #71, #76)
- [ ] Freeze Helpdesk use cases, role/resource/transition matrix, DTOs, errors and acceptance scenarios with Bảo and Yến.
- [ ] Integrate React API consumer through the BFF; distinguish MOCK/DEMO from LIVE.
- [ ] Demonstrate a Facility reuse scenario using the same Core contracts, without modifying Core business-neutral logic.
- [ ] Review that APISIX/Flowable adoption does not change Application-facing contracts unexpectedly.

## 5. Acceptance and change control

Before an implementation PR is called VERIFIED:
1. Link to its Issue/ADR, exact reviewed source SHA, owner and independent reviewer.
2. Demonstrate contract compatibility and negative/security regression tests.
3. Declare runtime provider(s), fallback, feature flags and environment.
4. Publish test commands, logs, skipped cases and deployed integration SHA.
5. Record PASS / CONDITIONAL / BLOCKED; only explicitly authorized integration merges enter main.
6. Update this baseline and Document Register if an approved target changes.

**Do not assume target technology selection implies software is installed, running, merged, or production-ready.**
