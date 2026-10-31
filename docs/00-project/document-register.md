# DX-OS — Document Register

> **Document ID:** G0-F03  
> **Revision:** 0.1 (provisional inventory)  
> **Status:** DRAFT (document and Gate review not verified)
> **Owner:** TBD  
> **Reviewer:** TBD  
> **Last updated:** 2026-10-28  
> **Scope:** Documentation inventory; not a source-code inventory.

## Purpose and authority

This document is the **canonical intended register** for document identity, lifecycle, owner, and reviewer once accepted at G0. Until independent review is recorded, the tables below remain a **planning-and-audit candidate list**, not an approved file inventory. The initial paths came from a proposed documentation tree and G0 plans; tracked paths have now been checked at the baseline noted below, while absent intended paths remain unapproved.

`docs/README.md` is the navigation portal; `docs/DESIGN_PROCESS.md` specifies stages and approval rules. This register holds the minimum inventory fields, while review records hold PR links, verified SHA and sign-off, and the central requirements traceability matrix holds requirement-to-artifact mappings.

## Repository verification status

**Verified checkout baseline:** the numbered canonical directories and this register were inspected on `docs/core-architecture-alignment` at HEAD `05c7e04f4e5d1e5693a6422fb301f0774155bf01`; the same commit was previously inspected on `docs/integrate-pr78-workspace`. This is a Git tree/path check, not an independent document or Gate approval. Many rows below remain intended, absent paths; `—` retains its existing unreviewed meaning. Existing files named `architecture.md` and `contracts.md` under each Core capability are tracked even where this provisional inventory names a different intended leaf. Reconcile IDs and paths through independent review before changing the six-column inventory or assigning lifecycle statuses.

| Check | Result | Implication |
| --- | --- | --- |
| Proposed structure | Supplied in the design conversation | Candidate paths only |
| Historical public GitHub discovery of `DX_OS_DOCS` | Repository not found by public repository-name search during initial authoring | Does **not** prove that a private/unindexed repository is absent; not a finding about the current checkout |
| Current checkout Git tree, HEAD and branch | `docs/core-architecture-alignment` at `05c7e04f4e5d1e5693a6422fb301f0774155bf01` inspected | Tracked paths distinguished from absent intended paths; independent review pending |
| Draft artifacts generated in conversation | Some, including F01/F02 and this F03 draft | Local files do **not** prove repository existence |
| Owner/reviewer assignments | Not supplied for individual documents | `TBD` until confirmed |
| Gate approval evidence | Not supplied | No document marked `Approved` |

**Interpretation of `—` in Status:** document lifecycle has not been independently audited; **not** a sixth lifecycle state, and **not** synonymous with `Planned`. After review, assign one of `Planned`, `Draft`, `In Review`, `Approved`, `Superseded` with supporting records. `Planned` means explicitly adopted work not present as an active authored document; it must not be inferred solely from an absent path or a failed external search. Root and G0 draft items require review before lifecycle assignment.

### Tracked Markdown outside this provisional inventory

At the verified checkout, 20 tracked Markdown documents under `docs/` have no `DOC-###` path entry below. This is a registration discrepancy, not authorization to allocate IDs or change lifecycle status:

- **Four potential canonical overlaps:** `docs/02-core/{data,gateway,identity,workflow}/architecture.md` coexist with the four intended `*-architecture.md` register paths. Review which path is canonical before assigning an existing ID or creating a new one.
- **Twelve additional Core documents:** `docs/02-core/data/{consistency-recovery,domain-model}.md`, `gateway/{error-handling,routing-model,security-model}.md`, `identity/{authorization-model,domain-model,security-boundaries}.md`, and `workflow/{authorization,domain-model,persistence,state-machine}.md`. Several carry `G4-F##` authoring metadata, which is not a `DOC-###` registration or an approved equivalence mapping.
- **Four supplementary review checklists:** `docs/10-reviews/f04-review-checklist.md` through `f07-review-checklist.md` have no register entries. Decide whether they require registration or are supporting review artifacts.

The 116 existing `DOC-###` IDs remain unique and unchanged. Sixty-three intended register paths are absent at this checkout; absence alone does not establish an approved `Planned` lifecycle status. See [Open Issues](../10-reviews/open-issues.md) for the unresolved inventory decision.

## Registry rules

1. Use the stable `DOC-###` identifiers below. A rename preserves its ID; a newly introduced document receives the next unused number. Never recycle IDs of retired documents.
2. Store paths as inline code until confirmed tracked; convert to relative Markdown links **only when the target exists in the reviewed Git revision**. A path here does not certify presence.
3. Confirm owner and independent reviewer per document; keep Gate Approver and sign-off in a dated gate review record, not in these tables.
4. The register's six columns are authoritative upon approval: **ID, Document, Gate, Owner, Reviewer, Status**. Use review records for PR/SHA/evidence, not extra ad-hoc columns.
5. Approve a document only with a review record tied to a revision, dependency checks, and appropriate evidence. File creation or merge alone is insufficient.
6. Update the register in the same PR when adding, renaming, replacing, or changing the lifecycle/ownership of a documented artifact. If the change cannot be approved together, record an explicit discrepancy.
7. `Superseded` requires a replacement ID/path or a justified terminal disposition in a review record. Never delete the stable identifier from the register.
8. Design documentation approval does not establish implementation, reuse, runtime security or test success.

## Candidate inventory by documentation area

The entries below are *intended* documents, not verified repository contents. Classification into `G0/G1/G2` and other gate ranges is preliminary and must be refined at inventory review. The source list is a design outline, not an approved scope commitment. Use separate Issue/PR records for scheduling.

## 00-project

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-115 | `docs/DESIGN_PROCESS.md` | G0 | TBD | TBD | — |
| DOC-116 | `docs/README.md` | G0 | TBD | TBD | — |
| DOC-001 | `docs/00-project/baseline-and-ownership.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-002 | `docs/00-project/document-register.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-003 | `docs/00-project/document-style-guide.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-004 | `docs/00-project/glossary.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-005 | `docs/00-project/problem-statement.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-006 | `docs/00-project/product-vision.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-007 | `docs/00-project/project-overview.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-008 | `docs/00-project/requirements-traceability.md` | G0/G1/G2 | TBD | TBD | — |
| DOC-009 | `docs/00-project/scope-and-mvp.md` | G0/G1/G2 | TBD | TBD | — |

## 01-architecture

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-010 | `docs/01-architecture/README.md` | G3 | TBD | TBD | — |
| DOC-011 | `docs/01-architecture/core-application-boundary.md` | G3 | TBD | TBD | — |
| DOC-012 | `docs/01-architecture/dependency-rules.md` | G3 | TBD | TBD | — |
| DOC-013 | `docs/01-architecture/deployment-topology.md` | G3 | TBD | TBD | — |
| DOC-014 | `docs/01-architecture/system-architecture.md` | G3 | TBD | TBD | — |
| DOC-015 | `docs/01-architecture/system-context.md` | G3 | TBD | TBD | — |
| DOC-016 | `docs/01-architecture/technology-stack.md` | G3 | TBD | TBD | — |

## 02-core

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-017 | `docs/02-core/README.md` | G4 | TBD | TBD | — |
| DOC-018 | `docs/02-core/data/README.md` | G4 | TBD | TBD | — |
| DOC-019 | `docs/02-core/data/acceptance-tests.md` | G4 | TBD | TBD | — |
| DOC-020 | `docs/02-core/data/data-architecture.md` | G4 | TBD | TBD | — |
| DOC-021 | `docs/02-core/data/contracts.md` | G4 | TBD | TBD | — |
| DOC-022 | `docs/02-core/data/data-ownership.md` | G4 | TBD | TBD | — |
| DOC-023 | `docs/02-core/data/lifecycle.md` | G4 | TBD | TBD | — |
| DOC-024 | `docs/02-core/data/security.md` | G4 | TBD | TBD | — |
| DOC-025 | `docs/02-core/data/storage-adapters.md` | G4 | TBD | TBD | — |
| DOC-026 | `docs/02-core/gateway/README.md` | G4 | TBD | TBD | — |
| DOC-027 | `docs/02-core/gateway/acceptance-tests.md` | G4 | TBD | TBD | — |
| DOC-028 | `docs/02-core/gateway/bff-integration.md` | G4 | TBD | TBD | — |
| DOC-029 | `docs/02-core/gateway/gateway-architecture.md` | G4 | TBD | TBD | — |
| DOC-030 | `docs/02-core/gateway/contracts.md` | G4 | TBD | TBD | — |
| DOC-031 | `docs/02-core/gateway/lifecycle.md` | G4 | TBD | TBD | — |
| DOC-032 | `docs/02-core/gateway/public-api.md` | G4 | TBD | TBD | — |
| DOC-033 | `docs/02-core/gateway/routing-and-exposure.md` | G4 | TBD | TBD | — |
| DOC-034 | `docs/02-core/gateway/security-boundary.md` | G4 | TBD | TBD | — |
| DOC-035 | `docs/02-core/identity/README.md` | G4 | TBD | TBD | — |
| DOC-036 | `docs/02-core/identity/acceptance-tests.md` | G4 | TBD | TBD | — |
| DOC-037 | `docs/02-core/identity/authentication-flow.md` | G4 | TBD | TBD | — |
| DOC-038 | `docs/02-core/identity/authorization-boundary.md` | G4 | TBD | TBD | — |
| DOC-039 | `docs/02-core/identity/identity-architecture.md` | G4 | TBD | TBD | — |
| DOC-040 | `docs/02-core/identity/contracts.md` | G4 | TBD | TBD | — |
| DOC-041 | `docs/02-core/identity/lifecycle.md` | G4 | TBD | TBD | — |
| DOC-042 | `docs/02-core/identity/provider-adapters.md` | G4 | TBD | TBD | — |
| DOC-043 | `docs/02-core/identity/security.md` | G4 | TBD | TBD | — |
| DOC-044 | `docs/02-core/workflow/README.md` | G4 | TBD | TBD | — |
| DOC-045 | `docs/02-core/workflow/acceptance-tests.md` | G4 | TBD | TBD | — |
| DOC-046 | `docs/02-core/workflow/authorization-boundary.md` | G4 | TBD | TBD | — |
| DOC-047 | `docs/02-core/workflow/lifecycle.md` | G4 | TBD | TBD | — |
| DOC-048 | `docs/02-core/workflow/persistence-adapters.md` | G4 | TBD | TBD | — |
| DOC-049 | `docs/02-core/workflow/state-transition-model.md` | G4 | TBD | TBD | — |
| DOC-050 | `docs/02-core/workflow/transaction-consistency.md` | G4 | TBD | TBD | — |
| DOC-051 | `docs/02-core/workflow/workflow-architecture.md` | G4 | TBD | TBD | — |
| DOC-052 | `docs/02-core/workflow/contracts.md` | G4 | TBD | TBD | — |

## 03-applications

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-053 | `docs/03-applications/README.md` | G5 | TBD | TBD | — |
| DOC-054 | `docs/03-applications/facility/acceptance-tests.md` | G5 | TBD | TBD | — |
| DOC-055 | `docs/03-applications/facility/api-contract.md` | G5 | TBD | TBD | — |
| DOC-056 | `docs/03-applications/facility/business-rules.md` | G5 | TBD | TBD | — |
| DOC-057 | `docs/03-applications/facility/data-model.md` | G5 | TBD | TBD | — |
| DOC-058 | `docs/03-applications/facility/requirements.md` | G5 | TBD | TBD | — |
| DOC-059 | `docs/03-applications/facility/use-cases.md` | G5 | TBD | TBD | — |
| DOC-060 | `docs/03-applications/facility/workflow-definition.md` | G5 | TBD | TBD | — |
| DOC-061 | `docs/03-applications/helpdesk/acceptance-tests.md` | G5 | TBD | TBD | — |
| DOC-062 | `docs/03-applications/helpdesk/api-contract.md` | G5 | TBD | TBD | — |
| DOC-063 | `docs/03-applications/helpdesk/business-rules.md` | G5 | TBD | TBD | — |
| DOC-064 | `docs/03-applications/helpdesk/data-model.md` | G5 | TBD | TBD | — |
| DOC-065 | `docs/03-applications/helpdesk/requirements.md` | G5 | TBD | TBD | — |
| DOC-066 | `docs/03-applications/helpdesk/use-cases.md` | G5 | TBD | TBD | — |
| DOC-067 | `docs/03-applications/helpdesk/workflow-definition.md` | G5 | TBD | TBD | — |

## 04-integration

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-068 | `docs/04-integration/bff-backend-contract.md` | G6 | TBD | TBD | — |
| DOC-069 | `docs/04-integration/end-to-end-flows.md` | G6 | TBD | TBD | — |
| DOC-070 | `docs/04-integration/gateway-application-contract.md` | G6 | TBD | TBD | — |
| DOC-071 | `docs/04-integration/identity-gateway-handoff.md` | G6 | TBD | TBD | — |
| DOC-072 | `docs/04-integration/integration-architecture.md` | G6 | TBD | TBD | — |
| DOC-073 | `docs/04-integration/integration-baseline.md` | G6 | TBD | TBD | — |
| DOC-074 | `docs/04-integration/workflow-data-transaction.md` | G6 | TBD | TBD | — |

## 05-testing

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-075 | `docs/05-testing/acceptance-report.md` | G7 | TBD | TBD | — |
| DOC-076 | `docs/05-testing/concurrency-tests.md` | G7 | TBD | TBD | — |
| DOC-077 | `docs/05-testing/contract-tests.md` | G7 | TBD | TBD | — |
| DOC-078 | `docs/05-testing/e2e-tests.md` | G7 | TBD | TBD | — |
| DOC-079 | `docs/05-testing/security-tests.md` | G7 | TBD | TBD | — |
| DOC-080 | `docs/05-testing/test-matrix.md` | G7 | TBD | TBD | — |
| DOC-081 | `docs/05-testing/test-strategy.md` | G7 | TBD | TBD | — |

## 06-deployment

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-082 | `docs/06-deployment/backup-and-recovery.md` | G8 | TBD | TBD | — |
| DOC-083 | `docs/06-deployment/configuration-reference.md` | G8 | TBD | TBD | — |
| DOC-084 | `docs/06-deployment/database-migrations.md` | G8 | TBD | TBD | — |
| DOC-085 | `docs/06-deployment/docker-compose.md` | G8 | TBD | TBD | — |
| DOC-086 | `docs/06-deployment/https-ingress.md` | G8 | TBD | TBD | — |
| DOC-087 | `docs/06-deployment/keycloak-setup.md` | G8 | TBD | TBD | — |
| DOC-088 | `docs/06-deployment/prerequisites.md` | G8 | TBD | TBD | — |
| DOC-089 | `docs/06-deployment/quick-start.md` | G8 | TBD | TBD | — |
| DOC-090 | `docs/06-deployment/troubleshooting.md` | G8 | TBD | TBD | — |

## 07-open-source

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-091 | `docs/07-open-source/community-guidelines.md` | G9 | TBD | TBD | — |
| DOC-092 | `docs/07-open-source/dependency-licenses.md` | G9 | TBD | TBD | — |
| DOC-093 | `docs/07-open-source/licensing-policy.md` | G9 | TBD | TBD | — |
| DOC-094 | `docs/07-open-source/release-checklist.md` | G9 | TBD | TBD | — |
| DOC-095 | `docs/07-open-source/third-party-attributions.md` | G9 | TBD | TBD | — |

## 08-decisions

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-096 | `docs/08-decisions/ADR-001-modular-monolith.md` | G3–G9 | TBD | TBD | — |
| DOC-097 | `docs/08-decisions/ADR-002-core-boundaries.md` | G3–G9 | TBD | TBD | — |
| DOC-098 | `docs/08-decisions/ADR-003-security-model.md` | G3–G9 | TBD | TBD | — |
| DOC-099 | `docs/08-decisions/ADR-004-gateway-architecture.md` | G3–G9 | TBD | TBD | — |
| DOC-100 | `docs/08-decisions/ADR-005-identity-provider.md` | G3–G9 | TBD | TBD | — |
| DOC-101 | `docs/08-decisions/ADR-006-workflow-persistence.md` | G3–G9 | TBD | TBD | — |
| DOC-102 | `docs/08-decisions/ADR-007-data-storage.md` | G3–G9 | TBD | TBD | — |
| DOC-103 | `docs/08-decisions/README.md` | G3–G9 | TBD | TBD | — |

## 09-evidence

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-104 | `docs/09-evidence/README.md` | G7–G10 | TBD | TBD | — |

## 10-reviews

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-105 | `docs/10-reviews/architecture-review.md` | G0–G10 | TBD | TBD | — |
| DOC-106 | `docs/10-reviews/f01-review-checklist.md` | G0–G10 | TBD | TBD | — |
| DOC-107 | `docs/10-reviews/f02-review-checklist.md` | G0–G10 | TBD | TBD | — |
| DOC-108 | `docs/10-reviews/f03-review-checklist.md` | G0–G10 | TBD | TBD | — |
| DOC-109 | `docs/10-reviews/final-acceptance.md` | G0–G10 | TBD | TBD | — |
| DOC-110 | `docs/10-reviews/g0-gate-review.md` | G0–G10 | TBD | TBD | — |
| DOC-111 | `docs/10-reviews/integration-review.md` | G0–G10 | TBD | TBD | — |
| DOC-112 | `docs/10-reviews/open-issues.md` | G0–G10 | TBD | TBD | — |
| DOC-113 | `docs/10-reviews/security-review.md` | G0–G10 | TBD | TBD | — |

## 11-poc

| ID | Document (intended path) | Gate | Owner | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| DOC-114 | `docs/11-poc/README.md` | G3–G7 | TBD | TBD | — |

## Critical dependency index (proposed; verify IDs and baselines)

| Dependency | Upstream | Downstream | Gate condition |
| --- | --- | --- | --- |
| DEP-F03-01 | Repository Git inventory and baseline | This register | All `—` rows classified, tracked paths verified |
| DEP-F03-02 | Design process and style standard review | G0 document approvals | Naming, lifecycle and review rules accepted |
| DEP-F03-03 | G1 scope and G2 requirements | G3 architecture contracts | Requirements and assumptions reviewed |
| DEP-F03-04 | G3 Core/Application boundaries | G4 Core and G5 App design | Dependent design cannot be approved on provisional interfaces |
| DEP-F03-05 | G4/G5 accepted contracts | G6 integration and reuse verification | Contract versions reconciled |
| DEP-F03-06 | Traceability matrix (`docs/00-project/requirements-traceability.md`, candidate) | G7 evidence and G10 final review | Requirement-to-test/evidence mappings reviewed |

This is a dependency *index*, not a substitute for the traceability matrix or the stage-specific gate checklist. No listed dependency is asserted resolved.

## Review records and external evidence

- **Document status and people:** this register, after verified baseline and acceptance.
- **Stage procedure:** [Design Process](../DESIGN_PROCESS.md) (tracked at the current checkout; approval remains pending).
- **Requirements traceability:** `docs/00-project/requirements-traceability.md` (candidate path; existence unverified).
- **Open issues:** [Open Issues](../10-reviews/open-issues.md) (tracked at the current checkout; approval remains pending).
- **Gate approval:** `docs/10-reviews/g0-gate-review.md` (candidate path; existence unverified).
- **F03 reviewer checklist:** [F03 review checklist](../10-reviews/f03-review-checklist.md) (tracked at the current checkout; review status separate).
- **Implementation evidence:** pinned commit/PR/test-run references supplied by actual reviewers; never infer them from current branch labels.

## Repository reconciliation procedure

The following commands preserve the **historical `DX_OS_DOCS` authoring procedure**; they do not describe the currently verified `D:\DEV\Projects\dxos-open-core` checkout. Run equivalent read-only inventory commands in the actual review checkout and attach their exact outputs to the F03 review record:

```powershell
cd D:\DEV\Projects\DX_OS_DOCS

git remote -v
git status --short --branch
git rev-parse HEAD
git ls-files docs
git ls-files --others --exclude-standard docs
Get-ChildItem docs -Recurse -File | Select-Object -ExpandProperty FullName
```

Then reconcile each candidate ID with the Git tree: **tracked**, **untracked local draft**, or **not present**. Only after this assessment assign the F02 lifecycle and, where applicable, `Planned` to approved backlog entries. Check all Markdown relative links on the exact reviewed commit. A reviewer must confirm the data; the author must not sign off on their own inventory.

## F03 approval criteria

F03 may be accepted only when: the Git commit and branch are pinned; all candidate entries are reconciled; IDs are unique and stable; owners/reviewers are assigned or explicit blockers filed for mandatory deliverables; links work; dependencies and traceability pointers are unambiguous; and an independent reviewer plus Gate Approver records the decision. The current outcome is **NOT REVIEWED**, not PASS.
