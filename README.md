# DX-OS Open-Core

**Reusable Digital Capabilities for Internal Service Applications**

DX-OS Open-Core is TRICORE's modular monolith for exploring reusable capabilities behind internal service applications. The Internal Service Portal / IT Support scenario is the main demonstration target. DX-OS is a digital transformation platform concept, not a computer operating system or a claim about the competition's required product.

**Documentation PR base:** `main` at `6f96719f249fe982826a84315443bc576697e378` (checked 2026-10-25). The implementation-status table below retains its *historical* `2115de3` checkpoint; it is **not** a claim of current integration or current test PASS. Newer unmerged work is listed separately.

**Navigate:** [Overview](#overview) · [Architecture](#architecture-overview) · [Status](#implementation-status) · [Getting started](#getting-started) · [Installation guide](docs/installation.md) · [Demo guide](docs/demo-guide.md) · [Contributing](CONTRIBUTING.md) · [Changelog](CHANGELOG.md) · [Security](SECURITY.md) · [License](#license-and-third-party-notices)

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

- **Identity / SSO:** Core identity and authorization contracts exist. Complete SSO/OIDC enforcement through Gateway is not established in the stated main baseline.
- **API Gateway:** API boundary code exists, but the generic Workflow HTTP adapter is not registered as a production endpoint in this baseline. Trusted caller propagation and protection require separate evidence.
- **Workflow:** Generic definitions, instances, transitions by transition ID, exact definition-version binding, optimistic concurrency, history, terminal-state safeguards, and a persistence port have code and tests. Application-specific Request workflow remains separate.
- **Data Management:** Object-storage contracts and Workflow persistence work exist. A complete object-storage provider and release-level PostgreSQL behavior are not established by those contracts alone.

## Implementation Status

The table describes **`origin/main` at `2115de3`**, inspected on October 22, 2026. It is a source snapshot, not a test run performed on that date.

| Area | Present in the baseline | Still to verify or complete |
| --- | --- | --- |
| Workflow Core | Contracts, runtime, version binding, history, persistence port, and related tests | Full Portal integration and release acceptance |
| Identity Core | Principal, context, authority, and decision contracts | End-to-end SSO/OIDC protection on main |
| Data Core | Object-storage contracts and Workflow persistence mechanisms | Complete storage provider and reviewed live behavior |
| Gateway / API | Request HTTP endpoints and API boundary code | Protected generic Workflow HTTP registration and trusted-context path |
| Request prototype | Separate application code and tests | Integration with generic Workflow Core |
| Frontend Portal | Demonstration direction and work outside the main baseline | A merged, verified Portal release |
| PostgreSQL / Flyway | Configuration and migrations V1–V4 | Startup, privileges, rollback, and live-environment acceptance evidence |

Identity–Gateway integration, the Portal, a complete demonstration, proof of Core reuse across two applications, and reproducible release packaging require their own reviewed evidence. An unmerged PR or roadmap item is not a capability already available on main.

### Current integration candidates — **unmerged as of 2026-10-25**

| Workstream | PR | Interpretation |
| --- | --- | --- |
| React/TypeScript/Vite Service Portal | [Draft #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51) | Mock/development UI including IT Support and Facility; `frontend/` is on the PR branch, **not main** |
| Guarded PostgreSQL dependency | [Draft #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) | SQL privilege/provisioning/security review pending |
| IT Helpdesk HTTP integration, APP-11 and B06 test harness | [Draft #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) | **B06 focused E2E PASS at `e12d105c`** (independent reviewer report: 33/33 tests). Full production/Portal integration and PostgreSQL privilege gates remain **LIVE_BLOCKED** |

**Exact-HEAD B06 evidence (October 25, 2026):** Independent reviewer Phan Hải Yến [reported focused real-Keycloak → Helpdesk HTTP → disposable PostgreSQL 18.6 E2E PASS](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935) at PR #63 HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`: `HelpdeskConfigurationTest` **2 passed**, `HelpdeskHttpPostgresTest` **31 passed**, **33 passed / 0 failed / 0 skipped**, `BUILD SUCCESSFUL`, and ephemeral-container cleanup reported. Keycloak 26.8.0, Java 21.0.12.1 and Gradle 8.14.3 were recorded. This replaces the prior **B06 not-yet-executed** documentation claim, but is **not** sign-off for [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58), controlled identity/tenant provisioning, Portal live integration, release, or production deployment. Earlier B05 evidence at `a3bf42e` remains valid **only for that older HEAD**. Keep `DRAFT / LIVE_BLOCKED`. See the [demo guide](docs/demo-guide.md).

### Historical test checkpoint

On **October 7, 2026**, at commit **`2b51a41`**, project documentation recorded **29 Workflow tests, 109 Request tests, and 139 backend tests**, with a successful build. The commands, environment, and limits are in the [Workflow POC](docs/poc/workflow-core-poc.md). These are historical results, **not** test counts or results from October 22.

At that checkpoint, [Workflow Core Issue #13](https://github.com/lexuanbao258-collab/dxos-open-core/issues/13) was closed through [PR #15](https://github.com/lexuanbao258-collab/dxos-open-core/pull/15); [Identity Issue #12](https://github.com/lexuanbao258-collab/dxos-open-core/issues/12) and [Data Issue #14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/14) were open. These are historical issue states, not a claim about their current status.

Mock, fake, and MVC-slice tests do not by themselves establish PostgreSQL transactions, live security enforcement, or a complete Portal demonstration.

## Technology Stack

| Component | Baseline technology or status |
| --- | --- |
| Backend | Java 21; Spring Boot 3.5.16 |
| Build | Gradle Wrapper 8.14.3 |
| Database | PostgreSQL target; Flyway migrations V1–V4 |
| ORM configuration | Hibernate `ddl-auto: none` |
| Tests | JUnit and Spring Boot Test; dedicated integration tasks have separate prerequisites |
| Frontend | Portal work outside this main snapshot |
| Deployment | Requires commit-specific operational and acceptance evidence |

Keycloak, a standalone gateway product, an object-storage provider, and a third-party workflow engine should be treated as options or proof-of-concept subjects unless a decision and implementation are evidenced for the relevant commit.

## Getting Started

For a first-time user, follow the **[combined installation guide](docs/installation.md)**. It separates (1) build/test of Backend on `main`, (2) the Portal **mock UI** on PR #51, and (3) real-Keycloak Helpdesk B06 **test candidate** on PR #63. There is **no accepted single-command full-stack Docker deployment** in the stated baseline. Do not use production/staging databases for local experiments.

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

This is a demonstration plan, not a claim that every step is available through the main branch's APIs or UI. **`CONFIRMED` and `CLOSED` are distinct states**; the application must retain the ratified transition and permission rules.

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
│   ├── installation.md       # Safe backend/frontend candidate setup paths
│   └── demo-guide.md         # Mock vs real integration demo checklist
├── infra/
├── scripts/
├── .github/                  # PR template for new contributions
├── .env.example
├── CONTRIBUTING.md
├── CHANGELOG.md
├── SECURITY.md
└── README.md
```

This high-level map includes documentation **proposed in this PR**. The `frontend/` React Portal is still **only in Draft PR #51**, so it is intentionally not shown as a root directory of this `main`-based documentation tree. The map does not claim every backend package is fully implemented.

## Documentation and Verification

- [Installation guide](docs/installation.md): safe Windows setup, Backend main build, Portal mock branch, Docker/PostgreSQL test modes.
- [Demo guide](docs/demo-guide.md): IT Helpdesk vs Facility mock, candidate E2E steps and acceptance evidence.
- [Backend guide](backend/README.md): setup, configuration, APIs, and test limits.
- [Architecture](docs/architecture/README.md): boundaries and dependency direction.
- [Workflow contract](docs/contracts/workflow-core.md): mechanisms and invariants.
- [Workflow POC](docs/poc/workflow-core-poc.md): historical test evidence.
- [Evidence index](docs/evidence/README.md): checkpoints and limitations.
- [Glossary](GLOSSARY.md): project terminology.
- [Contributing](CONTRIBUTING.md): branch, PR, tests, access and migration rules.
- [Changelog](CHANGELOG.md): unreleased vs verified changes.
- [Security reporting](SECURITY.md): privately report sensitive findings.
- [PR template](.github/pull_request_template.md): scoped evidence and review checklist.
- [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38): license, attribution, and SPDX review.

A `PASS` claim should identify its commit, environment, command, result, and limitations. Review the relevant Issues and PRs for work newer than the stated baseline.

## Contributing and Development Workflow

TRICORE uses Issues and pull requests to define scope, review contracts, and attach reproducible evidence. See [CONTRIBUTING.md](CONTRIBUTING.md) for the detailed process and non-destructive database/security rules:

1. Record acceptance criteria, exclusions, and ownership in an Issue.
2. Work on a short-lived branch from the appropriate baseline.
3. Include tests and explain assumptions or unverified behavior.
4. Attach relevant command output and limitations to the PR.
5. Obtain review before merging.

Migration, provider, security-policy, and licensing changes require the appropriate specialist review. A merge alone is not end-to-end verification.

## Team

TRICORE is a student team participating in OLP PMNM 2026. These are collaboration areas; Issues and PRs record specific work.

| Member | Primary collaboration area |
| --- | --- |
| Lê Xuân Bảo | Architecture, Workflow / Gateway, integration, coordination |
| Phan Hải Yến | Identity / Security, contracts, license and SPDX review |
| Nguyễn Ngọc Ánh | Data / Storage, Portal, integration review |

Responsibilities do not establish copyright ownership of each contributor's work.

## License and Third-Party Notices

Apache License 2.0 (`Apache-2.0`) is the selected license for original DX-OS materials that the relevant rights holders have authorized for distribution under these terms. The licensing work is tracked separately in [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38).

Third-party dependencies, the Gradle Wrapper, adapted materials, and externally sourced assets remain subject to their respective licenses and attribution obligations.

Copyright ownership, source-origin verification, NOTICE requirements, and file-level SPDX review are tracked in [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38). Release-level licensing compliance is not claimed until the outstanding requirements have been reviewed and resolved.
