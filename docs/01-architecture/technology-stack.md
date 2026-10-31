---
title: DX-OS Open-Core — Technology Stack and Evidence Register
status: DRAFT — NOT REVIEWED
version: 1.0-review-draft
date: 2026-10-29
source_baseline: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
owner: TO ASSIGN
reviewers: NOT RECORDED
---

# DX-OS Open-Core — Technology Stack and Evidence Register

## 1. Purpose, Scope and Evidence Rules

This document records **why** technologies support DX-OS's architectural capabilities, **where** they are configured, their verified or unknown versions, alternatives and trade-offs, and the tests needed before claiming operational readiness. It covers backend, browser Portal/BFF, identity, data persistence, object storage, integration, build, testing and local deployment. It is not a purchase recommendation, an automatic upgrade authorization, a release bill of materials or proof that candidate PRs run end to end.

**Inspection anchor:** immutable repository commit [`0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`](https://github.com/lexuanbao258-collab/dxos-open-core/commit/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed). The separately authored G3-F03/F04/F05 review packages have *not* been established as merged at this source SHA. Their design directions are used for consistency; this document does not silently promote their revisions to the inspected Git baseline.

**Status lexicon (non-exclusive and dimension-specific):**

| Label | Meaning | What it cannot establish |
| --- | --- | --- |
| SELECTED | Team architecture direction or a documented target | Actual installation, accepted ADR or deployed integration |
| OBSERVED | Version/configuration/dependency present in source at exact SHA | Running service, resolved transitive version or working connection |
| CANDIDATE | Workstream or unmerged PR targeting integration | Mainline inclusion, live route, release acceptance |
| VERIFIED | An identified claim with reproduced command, environment, SHA, artifacts and reviewer | A different claim outside the test's scope |
| UNKNOWN | Exact version/configuration not verified from reviewed evidence | Permission to choose or invent a version |

No **VERIFIED operational/CI/E2E** status is assigned by this documentation exercise. Tests were not run in this pass.

## 2. Architecture Constraints That Technology Choices Must Preserve

- **SYS-ADR-01 (team-selected direction):** Java/Spring Boot **modular monolith** backend; separate logical Identity, Workflow, Data Management and Gateway capabilities, not mandatory microservices.
- **SYS-ADR-02:** Hexagonal/Ports & Adapters; owner-defined neutral contracts, infrastructure implementations inward dependency.
- **SYS-ADR-06:** each capability/Application owns its *semantics* even when physically sharing PostgreSQL.
- **SYS-ADR-08:** a generic Workflow state machine without IT Helpdesk/Facility-specific branches.
- Portal/BFF and Backend belong to the DX-OS **logical system of interest**; Keycloak has distinct identity trust responsibility. C4 Context, Container/Component and Deployment are separate views.
- Backend authorization requires a **verified principal**, exact operation authority, tenant/resource eligibility and authorized Application orchestration. A valid JWT or UI role is not a resource grant.
- Helpdesk is the MVP golden path; Facility is the distinct **candidate/deferred** reuse proof. A mock Portal or source-level adapter alone is not the full proof.

These are **architectural design constraints**, not formal ADR approvals. A referenced SYS-ADR ID is not the same as an accepted ADR document.

## 3. Technology Inventory — Inspected Source and Target

| Area / technology | Version | Source status and basis | Architecture role / qualification |
| --- | --- | --- | --- |
| Java toolchain | **21** | **OBSERVED** in `backend/build.gradle` | Spring Boot modular backend; configured toolchain, not proof of local JVM installation |
| Spring Boot plugin | **3.5.16** | **OBSERVED** in `backend/build.gradle` | Backend runtime, REST and dependency management; actual resolved library graph requires build evidence |
| Gradle Wrapper | **8.14.3** | **OBSERVED** in `backend/gradle/wrapper/gradle-wrapper.properties` | Reproducible build distribution; wrapper execution not tested here |
| Spring Web / Validation / Actuator | **UNKNOWN resolved versions** | **OBSERVED dependency declarations**, BOM-managed | HTTP input boundary, bean validation, health indicators; security policy still requires tests |
| Spring Security + OAuth2 Resource Server | **UNKNOWN resolved versions** | **OBSERVED dependency declarations** | Backend JWT/OIDC integration; mere dependency does not verify issuer, audience or ACL |
| Spring Data JPA | **UNKNOWN resolved version** | **OBSERVED dependency declaration** | Legacy/business persistence option; not synonymous with Workflow JDBC transaction model |
| Flyway Core + PostgreSQL module | **UNKNOWN resolved versions** | **OBSERVED dependency declarations** | Schema migration; approved migration/rollback policies and real DB tests remain separate |
| PostgreSQL JDBC driver | **UNKNOWN resolved version** | **OBSERVED runtime declaration** | Relational connectivity; PostgreSQL server version is not the driver version |
| PostgreSQL server | **17** | **SELECTED TARGET** in deployment topology/prerequisites | Private relational database; not a claim a server was installed or migrated on this SHA |
| Keycloak / OIDC IdP | **UNKNOWN** | **SELECTED TARGET** in architecture/prerequisites | Identity provider, separated from resource authorization; realm/client/runtime must be verified |
| MinIO S3-compatible object server | **UNKNOWN** | **SELECTED TARGET** in topology/prerequisites | Private object content via provider-neutral `ObjectStoragePort`; real adapter integration not established |
| React Portal | **UNKNOWN** | **CANDIDATE** PR #71 | Browser UI; version/lockfile and live adapter mode must be checked at a pinned PR HEAD |
| Portal BFF | Runtime/version **UNKNOWN** | **CANDIDATE** PR #70 | OAuth/session/CSRF and backend route allowlist; language/runtime dependencies need manifest evidence |
| REST over HTTP | Protocol version n/a | **SELECTED** integration style in architecture | Published business API contract; route registration is independently tested |
| HTTPS ingress / reverse proxy | Implementation/version **UNKNOWN** | **SELECTED TARGET** in deployment model; exact deployment not verified | TLS entry point, truststore, redirect/hostname consistency |
| Docker / Compose | Version **UNKNOWN** | **TARGET/TO VERIFY** for local deployment | Packaging and networking; no root `docker-compose.yml` was confirmed at inspected SHA |
| JUnit / Spring Boot Starter Test | **UNKNOWN resolved versions** | **OBSERVED dependency declarations** | Test framework; no PASS is asserted |
| ArchUnit | **UNKNOWN / PROPOSED** | G3-F05 rule enforcement proposal, not an observed dependency in inspected Gradle declarations | Dependency constraint tests and CI no-new-violations gate |
| CI automation | Workflow/tool versions **UNKNOWN** | **TO VERIFY** for target SHA | Builds, test logs and release checks; presence of config alone is not a passing run |

**Version rule:** explicit numbers above are configurations at the inspected commit or *server targets*. All transitive versions, container tags/digests, React/BFF package versions and Keycloak/MinIO versions are **UNKNOWN** until their exact manifests and resolved artifacts are inspected. Never infer installed versions from a project name or latest upstream release.

## 4. Selection Rationale and Alternatives

| Choice | Capability / fitness | Considered alternative and why not chosen for this MVP | Key cost / risk |
| --- | --- | --- | --- |
| Java 21 + Spring Boot modular monolith | Team-sized transaction-oriented backend; reusable internal contracts | Separate Core microservices introduce distributed consistency/ops costs | Boundaries need architecture tests; monolith alone does not enforce modularity |
| Ports & Adapters instead of provider-coupled services | Keeps Core contracts independent of persistence/IdP SDKs | Direct SDK/JDBC calls from Core simplify first demo but lock in providers | More adapters, conformance tests and contract ownership discipline |
| Keycloak with OIDC rather than custom password service | Delegates login/password/identity protocol to an IdP | Building proprietary IAM adds auth/security maintenance burden | Issuer/subject migration, PKCE, token validation, operational configuration |
| PostgreSQL 17 target | Supports relational facts, Workflow history and DB-level consistency checks | Separate database for every Core not required in single-backend MVP | Connection/privilege design, actual transaction-port semantics and migration discipline |
| Local MinIO behind ObjectStoragePort | Private demo-compatible S3-style storage without making application logic provider-specific | AWS S3 deployment is **not selected**; filesystem-only/Supabase not part of selected target | SDK semantics, durability, cleanup and DB/object split commits |
| React Portal + BFF candidate | Browser experience separated from backend business authorization; BFF can contain session boundary | Direct browser-to-DB and front-end ACL are rejected | CSRF, cookies, token relay, route allowlist and integration complexity |
| Gradle/JUnit with proposed ArchUnit | Source-declared build/test setup; architecture enforcement can become executable | Manual review alone does not prevent new forbidden imports | Version pinning, architecture debt allowlists, seeded negative CI test |
| Local HTTPS ingress | Required coherent browser/IdP callback and secure cookie environment | Unreviewed plain-HTTP external exposure not acceptable | Cert trust, hostname, redirect and reverse-proxy misconfiguration |

These comparisons are **design rationale**, not quantified benchmark findings or a procurement evaluation.

## 5. Provider Independence, Compatibility and Lifecycle Policy

**Adapter substitution is conditional**, not free: the new adapter must preserve identity mapping, error normalization, transaction and durable-return semantics, stream and metadata behavior, access controls, retry safety and operational recovery. Verify with the same contract suite plus migration and failure tests; changing a provider may still require data migration and configuration changes.

| Dependency class | Upgrade/replace entry gate | Acceptance evidence |
| --- | --- | --- |
| Java/Spring Boot/Gradle | Reviewed compatibility matrix and pinned dependency resolution | Build/test logs, package import/CI checks, API compatibility |
| Spring Security / Keycloak | Protocol/issuer/client migration plan | Real OIDC positive and negative flows, canonical subject mapping |
| JDBC/JPA/Flyway/PostgreSQL | Schema/migration privilege and transaction impact review | Fresh disposable DB, migration verification, concurrency/rollback tests |
| ObjectStoragePort/MinIO | Contract and object lifecycle review | Streaming, CREATE_ONLY/OVERWRITE, metadata, normalized errors and orphan reconciliation |
| React/BFF | Lockfile, session/cookie/CSRF, API behavior review | Browser/Backend integration, denied-resource tests, dependency audit |
| Docker/TLS ingress | Image digest, ports, truststore, secrets, volume and networking review | Clean-install runbook and secured network evidence |

Versions must be pinned at a specific reproducible source commit and captured in an evidence manifest. Prefer explicit image digests and package lockfiles where adopted; **do not pin guessed versions**. Check maintenance/security advisories at the time of an actual upgrade review; no current advisory scan was executed here. Security patches require controlled review and compatibility verification, not silent upgrades.

## 6. Claims → Source/Contract → Test → Evidence → Reviewer

| Architectural claim | Source / contract required | Executable proof required | Current result / reviewer |
| --- | --- | --- | --- |
| Java/Spring Boot/Gradle configured | `backend/build.gradle`, wrapper properties at SHA | Build/reproducibility check | Configuration **OBSERVED**, execution **NOT VERIFIED** |
| Core boundaries are real | System Architecture, Core/Application Boundary, Dependency Rules; compiled production classes | ArchUnit, seeded forbidden-import CI | **NOT VERIFIED**; reviewer TO ASSIGN |
| Verified identity through Keycloak | Identity Core contract and trusted Spring Security adapter | OIDC/JWKS/issuer/audience/forged-header negatives | **NOT VERIFIED**; Identity/Security reviewer TO ASSIGN |
| Helpdesk transitions are authorized and atomic | Workflow Core contract, application transaction design, migration | Real PostgreSQL rollback, ACL race, unknown commit | **NOT VERIFIED**; Workflow/Application reviewer TO ASSIGN |
| MinIO is substitutable | `ObjectStoragePort`, adapter implementation | Real-provider conformance and crash/recovery | **NOT VERIFIED**; Data reviewer TO ASSIGN |
| Portal/BFF is safely integrated | PR #70/#71 manifest, accepted profile and routes | TLS/OIDC/CSRF/session and backend E2E | **CANDIDATE; NOT VERIFIED** |
| Facility proves shared Core | Both application paths and unmodified Core source diff | Independent Facility vertical-slice integration | **DEFERRED; NOT VERIFIED** |
| Local environment can be reproduced | Pinned Compose/network/Keycloak/DB/MinIO manifests | Independent clean installation and restoration | **TARGET; NOT VERIFIED** |

Evidence must name **command, runtime/config, exact commit, machine/environment, output/log artifact and independent reviewer**. A past PR comment or isolated mock test may inform design but cannot establish a different integrated release claim.

## 7. Risks, Open Decisions and Review Ownership

| ID | Risk / missing decision | Impact | Proposed reviewer/mitigation (not assignment accepted) |
| --- | --- | --- | --- |
| TECH-R01 | React/BFF/Keycloak/MinIO/image versions not pinned | Non-reproducible environment | Frontend/DevOps: verify manifests and image digests |
| TECH-R02 | OIDC issuer/JWKS/CSRF integration unproven | Account or session spoofing | Identity/Security: run real protocol and adversarial tests |
| TECH-R03 | PostgreSQL and Workflow durable commit contract unresolved | Partial business/state/history updates | Workflow/Application: real DB transaction and unknown-commit proof |
| TECH-R04 | DB and MinIO not one ACID unit | Orphaned objects or stale references | Data/Application: recovery/reconciliation design and tests |
| TECH-R05 | Provider-neutral port does not guarantee compatibility | Costly migration or semantic regression | Core/Data: conformance suite and migration review |
| TECH-R06 | ArchUnit absent from inspected dependency list | Architectural coupling unnoticed | Backend/CI: introduce and run version-pinned checks |
| TECH-R07 | Portal/BFF and Facility only candidate/deferred | Reuse/demo cannot be claimed E2E | Application/Frontend: exact-HEAD integration evidence |
| TECH-R08 | Formal ADR status and merged G3 docs not reconciled | False acceptance and broken links | Architecture/Docs: source SHA and Decision Register review |
| TECH-R09 | Secrets, TLS and database exposure not independently audited | Data compromise / failed clean install | Ops/Security: secure configuration and local network verification |

Open questions: accepted backend/Portal lockfiles, current Keycloak image and issuer config, final PostgreSQL/MinIO versions, integration profile and container bindings, release service accounts, formal ADR decisions and independent review owners. Do not resolve these by assumption.

## 8. Source References, G3 Alignment and Review Gate

**Verified source links at inspection SHA:**

- [Backend Gradle build](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/build.gradle) — Java 21, Spring Boot 3.5.16 and direct dependency declarations.
- [Gradle Wrapper properties](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/gradle/wrapper/gradle-wrapper.properties) — 8.14.3 distribution.
- [System Architecture](system-architecture.md), [Core/Application Boundary](core-application-boundary.md), [Dependency Rules](dependency-rules.md) — existing review drafts at inspected SHA; separately revised G3-F03/F04/F05 packages must be reconciled at merge.
- [Deployment Topology](deployment-topology.md) — existing target draft at inspected SHA; a separate G3-F07 package describes the expanded target, not confirmed merged.
- [Deployment Prerequisites](../06-deployment/prerequisites.md) — target-only status.
- [Identity Core](../contracts/identity-core.md), [Workflow Core](../contracts/workflow-core.md), [Data Core](../contracts/data-core.md).
- [Portal BFF PR #70](https://github.com/lexuanbao258-collab/dxos-open-core/pull/70), [Portal PR #71](https://github.com/lexuanbao258-collab/dxos-open-core/pull/71), [Helpdesk PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) — **candidate references**; do not treat as merged or accepted.

`docs/08-decisions/README.md` was not found at this SHA; ADR-007 and other planned decision files are **not linked** without explicit path verification. The intended G3-F02 System Context draft was also not found in the inspected Git tree. Review the merged Documentation Workspace before inserting those links.

**G3-F06 draft acceptance requires:** exact source inventory and dependency resolution; chosen/observed/candidate/verified separation; verified compatibility/security test artifacts; approved provider and architecture decisions; independent review and authorized Gate outcome. None of the missing execution/approval gates was completed by authoring this document.

**Disposition: DRAFT / NOT REVIEWED; NO CI PASS, E2E PASS, ADR APPROVAL OR GATE DECISION CLAIMED.**
