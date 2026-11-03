# DX-OS Open-Core

**Reusable Digital Capabilities for Internal Service Applications**

DX-OS Open-Core is TRICORE's modular monolith for exploring reusable capabilities behind internal service applications. The Internal Service Portal / IT Support scenario is the main demonstration target. DX-OS is a digital transformation platform concept, not a computer operating system or a claim about the competition's required product.

**Release documentation baseline:** v0.1.0 candidate based on `main@4428346` (PR #103 squash integration). Release acceptance is separate from source and CI verification.

**Navigate:** [Overview](#overview) · [Architecture](#architecture-overview) · [Status](#implementation-status) · [Run v0.1.0](#run-v010) · [Getting started](#getting-started) · [Documentation](#documentation-and-verification) · [License](#license-and-third-party-notices)

## Overview

Internal service requests often arrive through email, paper, spreadsheets, phone calls, or messaging apps. Scattered channels make ownership, progress, and decision history difficult to track. The proposed Portal brings request submission, assignment, processing, and status into one application.

DX-OS aims to supply reusable technical mechanisms for that application and others. An IT Support request may move from submission through review, assignment, resolution, requester confirmation, and closure. The application defines those business steps and permissions; Core supplies mechanisms that can be reused. This is TRICORE's demonstration scenario, not an assertion that OLP PMNM 2026 mandates this product.

## Why DX-OS?

The intended reuse boundary separates shared mechanisms from application-specific rules:

| Capability | Core responsibility | Application responsibility |
| --- | --- | --- |
| Identity / SSO | Principal, authentication context, and authority contracts | Business roles and request-specific permissions |
| API Gateway | API entry point, routing, trusted-context boundary, and error handling | User-facing business operations |
| Workflow | Definitions, instances, transitions, versions, history, and state safeguards | Business states, transitions, and conditions |
| Data Management | Persistence and storage contracts with provider adapters | Business data, ownership, and retention rules |

The Portal is a demonstration interface, while the Request package is an application/business prototype. Neither defines the reusable Workflow Core. IT Support and Facility/Access Request could use the same Workflow mechanisms with different business lifecycles; demonstrating that reuse remains an acceptance goal, not a completed end-to-end integration claim.

## Architecture Overview

The backend uses Java 21 and Spring Boot 3.5.16 as a **modular monolith**. Its logical boundaries do not imply separately deployed microservices. The diagram shows intended responsibilities and dependency direction:

```text
External client / application
            |
            v
    API Gateway boundary
       /          \
      v            v
 Identity       Application
 contracts         layer
                     |
                     v
                Workflow Core
                     |
                     v
         WorkflowPersistencePort
                     |
                     v
        Data / persistence adapter
                     |
                     v
                 PostgreSQL
```

This is an architecture view, not evidence that the full Gateway → Identity → Portal → Workflow → database path is integrated. Workflow owns `WorkflowPersistencePort`; a Data adapter can implement it without redefining Workflow semantics. Workflow does not own IT Support-specific rules, and Gateway does not own business workflows or persistence. Client-supplied identity headers are not trusted merely because they reached an HTTP boundary.

See the [architecture baseline](docs/architecture/README.md) and [Core contracts](docs/contracts/).

## Core Capabilities

- **Identity / SSO:** OIDC trust and authority contracts, with verified security regressions. Portal BFF has PKCE/session/CSRF implementation; live three-principal Keycloak/browser acceptance remains outstanding.
- **API Gateway:** Spring Boot business API boundary, verified principal checks, exact Workflow authorization and scoped error contracts. APISIX is a selected target, not a verified deployed runtime.
- **Workflow:** Provider-neutral definitions, instances, transition/version/history contracts, and PostgreSQL-backed Helpdesk integration. Flowable BPMN is a selected target, not a verified adapter in this release.
- **Data Management:** PostgreSQL 17 Helpdesk/Workflow persistence and negative transaction checks. MinIO/object-storage provider integration is not a completed v0.1.0 capability.

## Implementation Status

This table summarizes the **v0.1.0 source and tested scope** after PR #103 on `main@4428346`. CI and focused PostgreSQL 17 evidence do not substitute for browser E2E or release sign-off.

| Area | Present in v0.1.0 source | Known verification boundary |
| --- | --- | --- |
| Helpdesk Backend | Nine HTTP application routes; tenant/resource authorization; workflow history and transaction guards | R10 PG17 focused tests passed; full R12 Golden Path pending |
| Identity / Gateway | Verified OIDC/security boundary and exact authority checks | Real three-principal Keycloak/browser SSO pending |
| Portal BFF | OIDC PKCE, server-side session, CSRF and Helpdesk API client | Live browser full-stack acceptance pending |
| React Portal | Standalone MOCK UI and `integration-local` build mode | MOCK is not proof of connected LIVE runtime |
| Workflow / Data | Core contracts, PostgreSQL 17-backed Helpdesk/Workflow tests and ArchUnit rules | Deferred provider, privilege and audit checks remain |
| Reuse / Storage | Core reuse contracts, Facility UI demonstration | Facility E2E, MinIO and Flowable adapter not accepted |

### Historical test checkpoint

On **October 7, 2026**, at commit **`2b51a41`**, documentation recorded **29 Workflow tests, 109 Request tests, and 139 backend tests**. This is historical evidence, not the v0.1.0 test count. See the [Workflow POC](docs/poc/workflow-core-poc.md).

## Technology Stack

| Component | Baseline technology or status |
| --- | --- |
| Backend | Java 21; Spring Boot 3.5.16 |
| Build | Gradle Wrapper 8.14.3 |
| Database | PostgreSQL 17 Helpdesk/Workflow persistence; Flyway and local demo SQL with separate prerequisites |
| ORM configuration | Hibernate `ddl-auto: none` |
| Tests | JUnit and Spring Boot Test; dedicated integration tasks have separate prerequisites |
| Frontend | React 19, TypeScript, Vite; MOCK and integration-local builds |
| Deployment | Requires commit-specific operational and acceptance evidence |

Keycloak is the selected OIDC provider target; APISIX, MinIO and Flowable remain selected technology targets unless their exact runtime and adapter acceptance are independently evidenced.

## Run v0.1.0

### Option A — MOCK Portal (quick UI demonstration)

Requires Node.js **22.12.0 or newer**, npm, and a browser. From the repository root:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Open [http://127.0.0.1:5173](http://127.0.0.1:5173). This uses in-memory demonstration data and **does not require a backend, PostgreSQL, credentials, or live SSO**. Return to the repository root with `Set-Location ..`. On macOS/Linux use `npm` instead of `npm.cmd`.

### Option B — Local PostgreSQL Helpdesk environment

Follow the [local Helpdesk runbook](scripts/README-helpdesk-local.md) from the repository root and use the [isolated setup script](scripts/helpdesk-local-setup.ps1). It requires Windows PowerShell, Docker Desktop Linux Engine, JDK 21 and free demo ports. Run the script's **read-only preflight** before opting into creation of an isolated PostgreSQL 17 demo database; do not run its `-Execute` step against existing resources. The runbook covers required secrets, real identity subject provisioning and backend startup.

The frontend also supplies an `integration-local` build mode (`npm.cmd run build:integration-local` from `frontend/`); building it alone does **not** establish an authenticated browser-to-BFF-to-Backend Golden Path.

## Known limitations

- The MOCK Portal is a demonstration, not a live application or security acceptance.
- The full Keycloak/PKCE browser login, three-persona Helpdesk E2E flow and clean-install release acceptance require R12 evidence.
- Facility workflow runtime reuse, Flowable BPMN, APISIX edge runtime, MinIO storage and production deployment are not claimed as completed v0.1.0 features.
- Focused PostgreSQL 17 R10 passed with two documented skips; deferred database/provider audits and third-party SPDX/license review are tracked separately.

## Getting Started

### Prerequisites

- Git and JDK 21; set `JAVA_HOME` to the JDK.
- Internet access for the first Gradle Wrapper and dependency download.
- PostgreSQL when starting the full backend or running database-dependent integration tests.

### Test and build on Windows

From a PowerShell terminal:

```powershell
git clone https://github.com/lexuanbao258-collab/dxos-open-core.git
Set-Location dxos-open-core\backend
java -version
.\gradlew.bat --version
.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'
.\gradlew.bat test --tests 'com.tricore.dxos.request.*'
.\gradlew.bat test
.\gradlew.bat build
```

Interpret the results from the checked-out commit and actual command output. Some integration tasks have additional prerequisites; see the [backend guide](backend/README.md).

### Run with PostgreSQL

The root [`.env.example`](.env.example) documents `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`. Spring Boot does not automatically load a `.env` file in this baseline. Supply values to the process without committing credentials.

From `backend/` in PowerShell:

```powershell
$env:DB_HOST = Read-Host 'DB_HOST'
$env:DB_PORT = Read-Host 'DB_PORT'
$env:DB_NAME = Read-Host 'DB_NAME'
$env:DB_USER = Read-Host 'DB_USER'
$dbCredential = Get-Credential -UserName $env:DB_USER -Message 'PostgreSQL credentials'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
.\gradlew.bat bootRun
```

Review Flyway and database privilege procedures before connecting to a shared database. If startup succeeds on the default port, check the health endpoint from another PowerShell terminal:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/health'
```

An `UP` health result does not establish Workflow, security, database transaction, or Portal acceptance. See the [backend guide](backend/README.md) for startup and test limitations.

## Demonstration and Reuse

The target IT Support lifecycle is:

```text
Create request → Review / classify → Assign → In progress
               → Resolve → Requester confirms → Close
```

This is the target end-to-end acceptance path. Backend integration is merged, but a full live browser run remains an R12 gate. **`CONFIRMED` and `CLOSED` are distinct states**; the application must retain the ratified transition and permission rules.

| Area | Acceptance evidence to obtain |
| --- | --- |
| Startup | Reproducible commands and configuration without exposed secrets |
| Security | Verified caller identity, authorization, and negative tests |
| Workflow | Valid and rejected transitions, version binding, and history |
| Data | Persistence and retrieval in a reviewed PostgreSQL environment |
| Core reuse | Two business cases using the same mechanism with different rules |
| Traceability | Issue, PR, commit, tests, evidence, and documented limits |

Mark an item `PASS` only when the evidence is tied to the relevant commit and environment. Mock demonstrations and verified live integration are separate kinds of evidence.

## Repository Structure

```text
DX-OS/
├── backend/                  # Java sources, tests, and Gradle Wrapper
│   └── src/main/java/com/tricore/dxos/
│       ├── core/             # Identity, Workflow, and Data contracts
│       ├── application/      # Orchestration and policies
│       ├── gateway/          # API boundary code
│       ├── infrastructure/   # Provider and persistence adapters
│       ├── request/          # Application/business prototype
│       └── common/
├── docs/                     # Architecture, contracts, POCs, evidence
├── infra/
├── scripts/
├── .env.example
└── README.md
```

This is a high-level map, not a claim that every package is fully implemented.

## Documentation and Verification

- [Backend guide](backend/README.md): setup, configuration, APIs, and test limits.
- [Architecture](docs/architecture/README.md): boundaries and dependency direction.
- [Workflow contract](docs/contracts/workflow-core.md): mechanisms and invariants.
- [Workflow POC](docs/poc/workflow-core-poc.md): historical test evidence.
- [Evidence index](docs/evidence/README.md): checkpoints and limitations.
- [Glossary](GLOSSARY.md): project terminology.
- [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38): license, attribution, and SPDX review.

A `PASS` claim should identify its commit, environment, command, result, and limitations. Review the relevant Issues and PRs for work newer than the stated baseline.

## Contributing and Development Workflow

The full workflow is in [CONTRIBUTING.md](CONTRIBUTING.md). In short:

1. Every task has an Issue with one assignee, a release milestone and an `area:*` label.
2. Branch from `main` as `<type>/<area>-<description>`; keep it under three days.
3. Open a pull request into `main` (at most 400 changed lines) with a Conventional Commits title and `Closes #N`.
4. CI (`backend`, `frontend`, `repo-hygiene`) must pass and one other member must approve.
5. Changes land on `main` by squash merge only; nobody pushes to `main` directly.

User-visible changes are recorded in [CHANGELOG.md](CHANGELOG.md).

## Team

TRICORE is a student team participating in OLP PMNM 2026. These are collaboration areas; Issues and PRs record specific work.

| Member | Primary collaboration area |
| --- | --- |
| Lê Xuân Bảo | Architecture, Workflow / Gateway, integration, coordination |
| Phan Hải Yến | Identity / Security, contracts, license and SPDX review |
| Nguyễn Ngọc Ánh | Data / Storage, Portal, integration review |

Responsibilities do not establish copyright ownership of each contributor's work.

## License and Third-Party Notices

DX-OS original materials covered by the repository's [LICENSE](LICENSE) use **Apache License 2.0 (Apache-2.0)**, with attribution in [NOTICE](NOTICE). Third-party and per-file compliance work is tracked in [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38).

Third-party dependencies, the Gradle Wrapper, adapted materials, and externally sourced assets remain subject to their respective licenses and attribution obligations.

Copyright ownership, source-origin verification, NOTICE requirements, and file-level SPDX review are tracked in [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38). Release-level licensing compliance is not claimed until the outstanding requirements have been reviewed and resolved.
