# DX-OS Baseline and Ownership Control

> Document ID: Pending allocation in Document Register  
> Process: G0 / F04  
> Document state: Draft — not approved  
> Scope: Code and documentation repositories  
> Last prepared: 2026-10-28

## Purpose and Authority

This record defines the baseline selection, workstream accountability, independent review authority, and change controls for DX-OS. It does not replace the document inventory, document lifecycle, or individual Gate decisions.

| Source of authority | Owns |
| --- | --- |
| `docs/DESIGN_PROCESS.md` | Lifecycle, severity rules, and Stage Gate procedure |
| `docs/00-project/document-register.md` | Document IDs, individual owners/reviewers, document status |
| This document | Repository baseline policy, workstream RACI, review authority, escalation |
| Gate review record | Named Gate Approver, approved baseline tuple, sign-off, conditions |
| Git PR and commit records | Immutable change evidence and review history |

These paths are *intended references*, not verified active links. Link them only after checking the documentation repository.

## Baseline Definitions

- **Observed revision:** An actual Git commit independently retrievable from a repository. It is not automatically approved.
- **Candidate baseline:** A proposed repository, branch and full commit SHA to evaluate for a declared purpose.
- **Approved baseline:** A candidate explicitly accepted in a Gate record after independent review, with a precise timestamp and scope.
- **Baseline pair:** Separate immutable code and docs commits, identified together by one review decision; do not assume that identical branch names imply aligned contents.
- **Discrepancy:** A recorded difference between requirements, design, contracts, implementation, tests, or the baseline pair.

## Repository Register and Verification

| Repository | Verified observation | Approved baseline | Required follow-up |
| --- | --- | --- | --- |
| Code: [`lexuanbao258-collab/dxos-open-core`](https://github.com/lexuanbao258-collab/dxos-open-core) | Repository is accessible; default branch advertised as `main`. GitHub returned commit [`0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`](https://github.com/lexuanbao258-collab/dxos-open-core/commit/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed), dated 2026-10-28 13:04:49 UTC, in a recent-commit query. | **Not established** | Resolve the intended branch HEAD, record full SHA, inspection date, reviewer and baseline purpose. |
| Documentation: local name `DX_OS_DOCS` (location user-reported in previous work) | No matching accessible public GitHub repository was found by name search. Local branch and HEAD have **not** been inspected for this document. | **Not established** | Obtain authorized remote URL or local `git remote -v`, `git branch --show-current`, `git rev-parse HEAD` and `git status --short --branch`. |

The code observation above **must not** be described as the current or approved `main` baseline unless the branch ref and review record establish that claim. The documentary repository may be private or locally hosted; failed public discovery is not evidence of non-existence.

### Required baseline record

Complete one entry per repository, and reference the same pair from the Gate record:

| Field | Value to record |
| --- | --- |
| Repository URL | Verified canonical remote URL |
| Purpose and scope | What the baseline is intended to prove |
| Branch or ref | Actual ref name |
| Full commit SHA | 40-character SHA, verified with Git |
| Observed at | Timestamp with timezone |
| Working-tree condition | Clean/dirty, with any exclusions recorded |
| Evidence | Git command output, commit permalink, review/PR |
| Reviewer and Gate Approver | Identified by actual review record; no inferred names |
| Decision | Candidate / Approved / Rejected, with conditions |

A dirty local working tree is not a commit baseline. Note uncommitted modifications separately. Do not overwrite an approved baseline merely because `main` has advanced.

## Workstreams and Ownership Boundaries

| Workstream | Accountable role (not a person assignment) | Typical responsibility | Source for named owner |
| --- | --- | --- | --- |
| Product, analysis and application | Application/BA lead | Problem framing, use cases, business rules, application acceptance | Document Register / assignment PR |
| Core platform and backend | Core/backend lead | Identity, workflow, persistence and internal contracts | Document Register / assignment PR |
| Portal and integration | Frontend/integration lead | UI-to-backend boundary, BFF and end-to-end flows | Document Register / assignment PR |
| Data/AI and measurement, when in scope | Data/AI lead | Data-related claims, experiment or measurement evidence | Document Register / assignment PR |
| Security and release | Designated cross-workstream reviewers | Threat/security review, licensing and release claims | Review or Gate record |
| Documentation governance | Designated documentation coordinator | Register hygiene, links, baseline integrity | Assignment record |

A three-member team may combine workstream responsibilities. The table describes **roles only**; it does not assert which member currently holds them or that every listed workstream is separately staffed.

## Decision RACI

**R** = Responsible (prepares), **A** = Accountable (owns decision), **C** = Consulted, **I** = Informed. Specific people are appointed in the corresponding PR or Gate record. `A` is not necessarily the author.

| Change type | R | A | C | I |
| --- | --- | --- | --- | --- |
| Editorial changes without semantic impact | Document owner | Document owner, subject to review policy | Independent reviewer as required by risk tier | Relevant workstream |
| Contract or externally observable behavior | Contract owner | Designated contract decision owner | Consumer and provider reviewers; security where relevant | Affected workstreams |
| Architecture or security boundary | Architecture proposal owner | Designated architecture decision owner | Independent architecture/security reviewers, affected module owners | All workstreams |
| Stage Gate decision | Gate evidence coordinator | **Named Gate Approver from Gate record** | Independent reviewer(s), affected owners | Team and downstream owners |

No person may independently approve their own authored P0 design or its Gate acceptance. Roles must be resolved before approval; the above matrix does not grant repository permissions or designate an actual Gate Approver.

## Review Independence, Substitution and Escalation

1. The document author submits the proposal and evidence in a PR or review record.
2. At least one reviewer different from the author evaluates the change at the applicable risk tier.
3. The authorized decision owner or Gate Approver records the decision; repository merge permission alone is not evidence of design approval.
4. When a reviewer is unavailable, appoint a different qualified reviewer, record the substitution and reason, and identify the approving authority.
5. If independent review cannot be obtained, keep the item **In Review** or the Gate **NOT REVIEWED/BLOCKED**, as applicable. Never silently substitute self-approval.
6. Escalate unresolved P0 issues, disputes over contract authority, and conflicting Gate decisions to the formally designated project decision owner; if none is established, the decision stays open.

## Change Classification and Control

| Class | Examples | Minimum control |
| --- | --- | --- |
| Editorial | Spelling, formatting, navigation without changed meaning | PR or equivalent tracked change; reviewer checks classification and register impact |
| Contract | API schema, state transitions, authorization semantics | Impact analysis, consumer/provider review, contract/test traceability |
| Architecture | Core/Application boundary, provider policy, deployment trust boundary | Alternatives, recorded decision/ADR where significant, security/architecture review |
| Gate | Acceptance scope, baseline pair, conditions, PASS decision | Independent findings, evidence index, named Approver and signed Gate record |

Misclassified semantic changes must be upgraded to Contract or Architecture before approval. Critical changes cannot be downgraded solely to meet a deadline.

### Change procedure

1. Identify affected document IDs, contracts, claims, tests, and repositories.
2. Open a PR or decision record and classify the change with rationale.
3. Compare proposed behavior with the approved design and implementation baseline pair.
4. Register any discrepancy with ID, observed facts, affected contract, owner (or `TBD`), severity, proposed resolution, and review link.
5. Obtain independent reviewer findings; record ADR for significant architecture choices.
6. Update affected files, Document Register, traceability matrix and evidence references in the same change or in explicitly linked follow-up work.
7. At a review milestone or Gate, record the new SHA pair. The previous approved baseline remains historical evidence in Git/Gate records.

## Discrepancy Record Schema

| Field | Required content |
| --- | --- |
| ID | Stable `DISC-###` |
| Observed mismatch | Source design claim versus code/test behavior, without assumptions |
| Affected scope | Contract IDs, module, documentation IDs and baseline SHA(s) |
| Risk | P0 / P1 / P2 and justification |
| Owner | Confirmed person or `TBD` pending assignment |
| Resolution | Change design / change code / clarify requirement / defer with conditions |
| Evidence and state | PR, tests, reviewer outcome and link to resolution |

A discrepancy is not automatically resolved when a file is edited. Its closure requires verification of the agreed behavior and updates to impacted evidence.

## Milestones and History

Update the *approved baseline pair* only on documented review milestones, Gate acceptance, or a material baseline-impacting change. Preserve earlier decisions in immutable commit/PR/Gate records. Avoid an append-only commit diary in this document.

## G0/F04 Acceptance Requirements

- Both repository identities and exact intended refs are confirmed.
- Both full commit SHAs, purpose, observation times and evidence are captured.
- Current candidate versus approved baseline status is explicit.
- Workstream ownership and document-level ownership sources are unambiguous.
- Independent reviewer and Gate Approver are **actually designated**, not inferred.
- RACI, decision class, escalation and discrepancy controls are understood and applied.
- Corresponding Document Register and Gate review records are checked for consistency.
- The F04 checklist is evaluated against evidence, and independent sign-off is recorded.

**Current assessment:** `DRAFT / NOT REVIEWED`. There is no validated code/docs baseline pair or independent F04 sign-off in the materials available for this draft. This document does **not** authorize a `PASS` or `CONDITIONAL PASS` claim.
