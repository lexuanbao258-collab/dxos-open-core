# DX-OS Open-Core — Documentation Style Guide

> **Document ID:** DXOS-G0-F08  
> **Purpose:** Define consistent engineering-writing, Markdown, template and evidence conventions.  
> **Scope:** DX-OS design documentation across G0–G10.  
> **Standard:** Documentation Standard v1.0 — proposed for adoption, not independently approved.

## 1. Authority and Boundaries

This guide specifies **how documents are written**; it does not grant approvals. `docs/DESIGN_PROCESS.md` defines workflow, review and Gate decisions; `docs/00-project/document-register.md` is authoritative for each document's lifecycle, owner and reviewer; `docs/00-project/baseline-and-ownership.md` defines baseline and approval authority; `docs/10-reviews/open-issues.md` tracks unresolved cross-gate issues; the ADR index governs decisions. All paths here are repository-root paths and must be verified before activating links. A file may be drafted before G0 acceptance; do not treat draft content as a ratified policy.

**Source-of-truth precedence:** a concise local document metadata header is a reader aid, not a competing status register. If its status or owner disagrees with the Document Register, open a discrepancy and reconcile through review. Do not silently choose either value.

## 2. Language, Terminology and Precision

- Write all normative prose, headings, diagram labels, table headings, checklists and review records in **concise technical English**. Preserve literal product identifiers, code, quoted stakeholder input and source-language quotations when necessary; label translations.
- Expand specialized acronyms at first use in a substantive document; prefer one term for one concept. Maintain definitions in the canonical `docs/00-project/glossary.md` when it exists; until then identify unresolved terminology as provisional.
- **Authentication** establishes identity; **authorization** determines permission to act on a specific resource. A valid token is not evidence of permission.
- **Core** owns reusable mechanisms and published contracts; **Application** owns domain-specific facts/rules. Avoid drawing dependency arrows from Core toward application implementation without an approved exception.
- **Workflow state** is a specific persisted position in a defined state machine; **document status** is editorial lifecycle; **Gate outcome** is independent acceptance. Do not use the generic word *status* where ambiguity matters.
- Distinguish **Proposed**, **Designed**, **Implemented**, **Verified** as *claim maturity*, not document lifecycle or ADR decision states.

### Good and Bad — Precise Claims

**Bad:** “The Gateway is secure and E2E passed.”

**Good:** “The proposed Gateway verifies an authenticated principal before authorization. Runtime verification remains **unverified** until a pinned test run establishes the security claim against the reviewed commit.”

**Bad:** “ADR accepted, therefore the workflow is deployed.”

**Good:** “ADR acceptance records a design decision; deployment and behavior require separate evidence.”

## 3. File Naming, Structure and Metadata

- Lowercase `kebab-case.md` for authored files. Keep established exceptions (`README.md`, `DESIGN_PROCESS.md`, pre-existing ADR IDs and filenames). Never rename legacy files solely to improve style without link-impact review.
- Exactly **one H1 (`#`)** per file. Follow heading order without skipping levels. README/index and ADR files use semantic unnumbered headings; long engineering reports may use consistent numbering. One file should have one subject and a clear audience.
- Minimum metadata for a normative design file: **Document ID, Purpose, Scope**. Version or reviewed baseline may be added when useful. Lifecycle, owner and reviewer belong canonically in the Document Register, not in duplicate hand-edited metadata tables. Existing draft metadata may be retained temporarily but must be reconciled at review.
- Use short paragraphs, named assumptions, explicit limitations, fenced code with language tags, and compact tables. Avoid tables with many fields; split details into sections or separate documents.
- Dates use ISO `YYYY-MM-DD`; timed evidence uses offset-aware ISO 8601. Never invent dates, owners, version IDs, acceptance decisions or commit SHAs.

### Good and Bad — Markdown

**Bad:**

~~~markdown
# Identity
#### Authorization
We tested it, works.
~~~

**Good:**

~~~markdown
# Identity Authorization Boundary

> **Document ID:** DXOS-CORE-ID-AUTH  
> **Purpose:** Specify the authorization boundary.  
> **Scope:** Identity-to-Gateway handoff.

## Authorization Assumptions

The Gateway authorizes the target resource after validating the principal.
~~~

## 4. Document Types and Minimal Templates

The structures below are **minimum guidance**, not exhaustive checklists. Specialized templates can be created as separately registered artifacts after review.

| Document type | Required sections / logical fields |
| --- | --- |
| Portal / index | Purpose, Start Here, reading paths, map, governance, contribution link |
| Architecture / module | Purpose, Context, Boundaries, Components, Flows, Invariants, Risks, Verification |
| Core/API contract | Purpose, Inputs, Outputs, Preconditions, Authorization, Errors, Invariants, Tests |
| Application analysis | Actors, use cases, business rules, data ownership, transitions, acceptance criteria |
| ADR | Context, Alternatives, Decision, Rationale, Consequences, Verification |
| Test / evidence | Claim, baseline, environment, method, observation, result, limitations |
| Review / Gate record | Scope, pinned baseline, reviewed artifacts, findings, dependencies, reviewer and decision |

### Architecture Template Example

~~~markdown
# [Component] Architecture

> **Document ID:** [stable-ID]  
> **Purpose:** [specific design question]  
> **Scope:** [inside/outside boundaries]

## Context
## Boundaries
## Components
## Flows
## Invariants
## Risks and Trade-offs
## Verification Plan
~~~

### Contract Template Example

~~~markdown
# [Contract] Specification

> **Document ID:** [stable-ID]  
> **Purpose:** [consumer and capability]  
> **Scope:** [operations and exclusions]

## Inputs and Outputs
## Preconditions
## Authorization
## Errors and Failure Semantics
## Invariants
## Acceptance Tests
~~~

**Bad contract:** “`POST /transition` changes state.”

**Good contract fragment:** “A transition request includes instance ID, action and `expectedVersion`; the service validates resource visibility, operation authority and transition eligibility, then either records one valid state/history mutation or returns a documented error. The exact endpoint and error codes remain **proposed** until the API contract is approved.”

## 5. Diagrams and Mermaid

- Prefer Mermaid committed as text for C4-inspired context/container views, sequence diagrams and state machines. State diagram type, purpose, boundaries, arrow meaning and important assumptions in surrounding text. Use a legend if line/shape meaning is not obvious.
- Diagrams are **design proposals unless verified**; diagrams do not prove deployment or implementation. Keep names and direction consistent with the contract and published architecture.
- Avoid embedding passwords, real usernames, internal endpoints or secrets. For complex diagrams use SVG/PNG with descriptive alt text plus the editable source when available.
- Mermaid render check is a technical step; visual reviewer approval remains separate.

**Bad:** `Core --> Helpdesk` with no meaning stated: it suggests Core depends on application logic.

**Good conceptual example** (illustrative; no implemented integration claim):

~~~mermaid
flowchart LR
    USER[Requester] --> APP[Helpdesk Application]
    APP --> API[Published Gateway Contract]
    API --> WF[Reusable Workflow Core]
    API --> ID[Identity Boundary]
~~~

The arrows represent **conceptual calls or dependencies**, not verified physical network routes. Application-specific rules stay in the Application; the diagram does not assert that the HTTP paths are live.

## 6. Links, Citations and Claim-Level Evidence

- Internal links must be **relative to the source Markdown file** and point to a file **actually tracked at the reviewed commit**. Until confirmed, use inline-code path + `Planned / Unverified`, never a broken clickable link.
- For code behavior use a pinned commit permalink and, where appropriate, line range. For test outcomes cite test name, exact SHA, environment, command, outcome, dated log/CI run and known limitations.
- A PR URL proves that a PR exists, **not** that it was merged or that its tests passed. A mutable `main` URL is not a reproducible baseline.
- Distinguish source quote, team decision, observed implementation, runtime verification and unsupported hypothesis.
- Use evidence IDs (`EVD-...`) and requirement/test identifiers (`REQ-...`, `NFR-...`, `UC-...`, `BR-...`, `TC-...`) when defined by canonical registers. Do not invent identifiers to imply approval.

### Good and Bad — Evidence

**Bad:** `Implemented and verified — https://github.com/owner/repo/tree/main`

**Good:** `Claim: verified under reviewed SHA [FULL_SHA]. Evidence: [test ID], [exact command], [CI run or sanitized test output], [reviewer record]. Until these are available, report: NOT VERIFIED.`

## 7. Tables, Checklists and Review Language

- Prefer 3–7 informative columns. Large matrices belong in dedicated contract/test documents. Use descriptive column names, one logical item per row, and stable IDs where needed.
- A checklist item must be **falsifiable and repeatable**: named target, expected result, evidence location and reviewer in its review record. Checking a box is not the same as Gate approval.
- Preserve separate vocabularies: Document `Planned / Draft / In Review / Approved / Superseded`; ADR `Proposed / In Review / Accepted / Rejected / Superseded`; Issue `Open / In Progress / Pending Verification / Closed`; Gate `PASS / CONDITIONAL PASS / BLOCKED / NOT REVIEWED`.
- P0/P1/P2 is severity/priority classification, not a document lifecycle stage. The impact on a Gate must be evaluated explicitly.

**Bad:** `- [x] API finished`

**Good:** `- [ ] TC-AUTH-01: At the pinned revision, a request with an invalid token returns the approved denial behavior. Attach the reproducible test result and reviewer finding before marking complete.`

## 8. Editorial and Change Control

- Use Git PRs as the change and revision record. Add a logical document version only when a reviewed contract/standard needs a recognizable revision label. Never claim a version was ratified merely because it was written.
- For editorial changes, apply normal PR/link/lint review. For changes to security boundaries, contracts, data ownership or deployment strategy, follow F02 impact analysis and ADR policy; do not silently revise an Accepted decision.
- Changes affecting approved evidence or a Gate's baseline require proportionate re-review. Never replace the canonical source by copying its full table into multiple documents.
- P0 uses the full Scope → Alternatives → Decision → Draft → Review → Evidence → Approve workflow; P1/P2 may use a shorter documented review without weakening truthfulness or independent approval requirements.

## 9. Quality Checks and Tools

Suggested tools (availability must be confirmed locally): Markdownlint, a repository-aware relative-link checker, and Mermaid preview/render validation. Record tool versions, command, result and scope where used. A successful lint or render check **cannot validate** a business rule, authorization guarantee or architecture boundary.

~~~powershell
# Run at the documentation repository root.
git status --short --branch
git rev-parse HEAD
git diff --check
git ls-files docs
~~~

Before requesting review, check language, one H1, heading order, file naming, link targets, term consistency, claim/evidence matching, diagrams, safety redactions, decision traceability and pending assumptions.

## 10. Compatibility with F01–F07

| Artifact | Inherited or clarified convention | Reconciliation required |
| --- | --- | --- |
| F01 README | Concise portal and 12-area navigation | Keep links limited to tracked existing files |
| F02 Design Process | Stage Gate, RACI and review are governed there | Do not treat `BLOCKED` as a document lifecycle value |
| F03 Document Register | Canonical document status/owner/reviewer and ID assignment | Remove conflicting duplicated metadata values |
| F04 Baseline & Ownership | Exact code/docs SHA and named approval authority | Do not infer authority from repository access |
| F05 Open Issues | Track unresolved issues; status vocabulary is separate | Do not mix issue `Closed` with document `Approved` |
| F06 G0 Review | Only formal evidence-based Gate outcome | A drafted checklist cannot issue `PASS` |
| F07 ADR Governance | Preserve existing ADR IDs and Accepted ≠ Implemented | Do not renumber legacy ADRs or assume status |

An earlier draft used an extra document status `BLOCKED`; under F02, **BLOCKED is a Gate outcome**, while blocked work is captured as a dependency/finding in F05. Prior drafts with richer metadata are **not automatically invalid**; reconcile them during the next review, with F03 remaining authoritative.

## 11. Adoption and Acceptance

This document is **DRAFT / NOT REVIEWED** and is a proposed editorial baseline. The F08 review must check all eighteen selected requirements, examples and link hygiene, compare F01–F07 at fixed code/docs SHAs, verify lint/render checks where available, obtain independent reviewer assessment, and record an authorized decision in the G0 Gate record. **F08 and G0 are not PASS.**
