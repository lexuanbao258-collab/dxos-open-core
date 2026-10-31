# DX-OS — Gate-Oriented Open Issues Register

> **Document:** G0–F05 | **Status:** Draft / Pending Independent Review | **Snapshot:** 2026-10-28 (GitHub issue search) | **Baseline:** No approved code/docs baseline established
>
> **Purpose:** Track unresolved design, contract, dependency, security, evidence, and documentation decisions that may affect a G0–G10 acceptance gate. This document is **not** a replacement for GitHub Issues, ADRs, or test reports.

## 1. Scope and authoritative sources

This register contains *open issues with documented gate implications*, not the entire development backlog. GitHub Issues remain the source for individual issue discussion and technical status; this register is the curated source for cross-gate impact. The [DX-OS source repository](https://github.com/lexuanbao258-collab/dxos-open-core) was queried for open Issues on 2026-10-28. The separate `DX_OS_DOCS` repository and its approved Git baseline were **not verified**. This snapshot does not establish issue closure, current deployment readiness, or approved architecture.

The register's **OI-###** IDs are stable, independent of GitHub issue numbers. Items are included because their issue descriptions explicitly report unfinished acceptance work; they are not independently reproduced runtime defects. A GitHub issue being open does **not** by itself prove a P0 technical defect.

## 2. Classification and lifecycle

- **Types:** Decision; Architecture; Contract; Security; Evidence; Dependency; Documentation. Select one primary type and describe other impacts in the issue record.
- **Priority:** **P0** blocks its *affected* Gate when the unresolved condition is essential to safe/valid acceptance; **P1** is material but may qualify for a documented conditional decision; **P2** is lower-impact. Do not propagate a P0 automatically to unrelated Gates.
- **Status:** `Open` → `In Progress` → `Pending Verification` → `Closed`. Reopening returns to `Open`; status requires a traceable source. The lifecycle here is independent of the document statuses in `document-register.md`.
- **Ownership:** exactly one accountable OI owner must be assigned before acceptance. A GitHub coordinator, assignee, or reviewer is **not automatically** the OI owner or Gate Approver.
- **Gate mapping:** mapping below is **proposed for F05 review**, not a confirmed Gate decision. See [Design Process](../DESIGN_PROCESS.md) if that file is present locally; the path is printed, not hyperlinked, until repository verification.

## 3. Open issues — sourced working inventory

**Target-architecture decisions pending review:** assess Flowable BPMN against the existing `WorkflowRuntime`/`WorkflowPersistencePort` definition, version, history, concurrency, and uncertain-commit semantics; define APISIX-to-Backend trust and bypass protection without transferring resource authorization to the edge; define the structured Data public contract separately from `ObjectStoragePort`, Application repositories, and Workflow-owned persistence; and approve provider migration and conformance criteria. These are proposed review topics under the existing issue lifecycle, not closed decisions or verified implementations. Assign stable OI IDs only through the register's review process.

**Document inventory decision pending review:** reconcile the 20 tracked Markdown files absent from the [Document Register](../00-project/document-register.md), including four potential `architecture.md`/`*-architecture.md` canonical overlaps, twelve additional Core documents, and four supplementary review checklists. Decide registration, canonical paths, and whether existing `G4-F##` authoring metadata needs a reviewed relationship to `DOC-###` IDs. Do not infer that relationship, assign owners, change statuses, or create the 63 absent intended files from this audit. Allocate any new OI ID through the existing review process.

`TBD` = not independently assigned/confirmed. Priorities labeled *source P0* come from the linked issue, not an independent F05 severity review. Proposed Gates are marked `Gx?`.

| ID | Type | Priority | Affected Gate | OI Owner | Status | Source / unresolved acceptance concern |
|---|---|---|---|---|---|---|
| OI-001 | Contract | P0 (source) | G2?/G5?/G6? | TBD | Open | [#53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53): Portal business rules/API contract v1.2 remains draft pending ratification; UC/transition/API-gap DoD outstanding. |
| OI-002 | Dependency | P0 (source) | G6?/G7?/G8? | TBD | Open | [#23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23): trusted Workflow HTTP authorization; Stage 6.2 marked OFF/BLOCKED. |
| OI-003 | Security | TBD | G4?/G6?/G7? | TBD | Open | [#24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24): verified OIDC principal and handoff still require final implementation/test review. |
| OI-004 | Contract | TBD | G4?/G6?/G7? | TBD | Open | [#22](https://github.com/lexuanbao258-collab/dxos-open-core/issues/22): secured Workflow HTTP API release depends on #23/#24/#25. |
| OI-005 | Evidence | TBD | G6?/G7? | TBD | Open | [#27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27): Core live end-to-end acceptance marked OPEN/LIVE_NOT_ACCEPTED. |
| OI-006 | Dependency | P0 (source) | G5?/G6?/G7? | TBD | Open | [#39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39): Portal real HTTP consumer requires security/API handoffs; mock is not live E2E evidence. |
| OI-007 | Evidence | P0 (source) | G7?/G8?/G9? | TBD | Open | [#40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40): reproducible full-stack deployment/demo marked FULL_STACK_E2E_NOT_VERIFIED/LIVE_BLOCKED. |
| OI-008 | Dependency | TBD | G4?/G6?/G7? | TBD | Open | [#25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25): PostgreSQL adapter outstanding test/security/integration review. |
| OI-009 | Security | TBD | G4?/G6?/G8? | TBD | Open | [#26](https://github.com/lexuanbao258-collab/dxos-open-core/issues/26): MinIO adapter marked IMPLEMENTATION_REVIEW_REQUIRED/LIVE_BLOCKED. |
| OI-010 | Documentation | TBD | G6?/G9? | TBD | Open | [#37](https://github.com/lexuanbao258-collab/dxos-open-core/issues/37): OpenAPI/published API documentation acceptance pending. |
| OI-011 | Documentation | P0 (source) | G9? | TBD | Open | [#38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38): license/SPDX/ownership audit remains open pending DoD. |

**Scope caution:** these are *issue-backed unresolved acceptance topics*, not a claim that every associated code path fails. GitHub labels, exact assignees, and reviewer approvals were not comprehensively verified. Treat source text and its date as a snapshot, not an immutable assertion about current runtime.

## 4. Critical dependency paths requiring review

| Path | Why the dependency matters | Source | Gate impact (proposed) |
|---|---|---|---|
| Identity → Gateway → secured Workflow HTTP | An unverified identity or unauthorized route cannot establish secure live integration | [#24](https://github.com/lexuanbao258-collab/dxos-open-core/issues/24), [#23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), [#22](https://github.com/lexuanbao258-collab/dxos-open-core/issues/22) | G4/G6/G7 |
| Business contract → Portal → live E2E | UX actions must match approved transitions, actual endpoints and authorization | [#53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53), [#39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39), [#27](https://github.com/lexuanbao258-collab/dxos-open-core/issues/27) | G2/G5/G6/G7 |
| PostgreSQL + MinIO → clean install/demo | Runtime storage and operational evidence must match the chosen local MVP topology | [#25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25), [#26](https://github.com/lexuanbao258-collab/dxos-open-core/issues/26), [#40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40) | G6/G8/G9 |
| API docs + licenses → release | Public contracts and legal compliance need independent acceptance | [#37](https://github.com/lexuanbao258-collab/dxos-open-core/issues/37), [#38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38) | G9 |

## 5. Issue detail and decision discipline

Maintain detailed alternatives, affected document IDs, decision required, accountable owner, reviewer, resolution plan and verification evidence in the linked GitHub issue/review record. For a significant architectural change, create or update an ADR through PR review. Do **not** replace a pending decision with a proposed solution labeled as approved.

Before a Gate decision, reviewers must confirm its exact issue set and dependencies, assign priority using F02 criteria, identify any P0 blockers **for that Gate**, and verify the corresponding source links at the review baseline. A Gate may be conditionally accepted only under the F02 policy and with explicit documented residual risk; this file alone never grants a PASS.

## 6. Resolution and closure policy

An OI may be removed from the active table only when all requirements below are met:

1. The resolution is recorded in the source issue or linked PR/ADR with exact commit or immutable review evidence where applicable.
2. Relevant code, contracts, architecture documents, test expectations and the Document Register are reconciled.
3. The claimed behavior is verified with the **appropriate** evidence (design review for a design claim; executable test/log for a runtime claim).
4. A reviewer independent of the change author confirms the closure criteria and any remaining limitations.
5. The issue's affected Gate record is updated; closed-item history remains in GitHub/review records rather than expanding this active table indefinitely.

A merged PR or an author's `Done` comment is not sufficient on its own. If closure cannot be verified, keep the OI active and specify the missing evidence.

## 7. Maintenance rules

- Re-check this inventory during a relevant PR review, at each Gate review, and when a linked dependency changes.
- New OI: allocate the next unused OI ID; do not recycle IDs. Record its type, source, issue-specific impact, owner decision and lifecycle state.
- Do not silently downgrade a P0. Reclassification requires a dated rationale, source evidence and an independent review.
- Do not duplicate full issue discussions, sensitive information, private credentials, or unverified business claims.
- Track F05 changes via PR; indicate the Git SHA and reviewer in the review record, not as an invented approved baseline here.

## 8. F05 acceptance state

**F05 document status: Draft / Not Reviewed. Gate result: NOT REVIEWED.** A working inventory has been prepared from accessible GitHub issues. The docs repository baseline, complete issue coverage, official OI owners, final priorities, approved Gate mappings, and independent reviewer sign-off remain unverified. **No PASS or conditional approval is claimed.**
