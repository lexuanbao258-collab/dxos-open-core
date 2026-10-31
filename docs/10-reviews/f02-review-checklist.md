# F02 — Design Process Review Checklist

> **Document ID:** DXOS-G0-F02-REV  
> **Version:** 1.0.0-draft  
> **Status:** DRAFT / NOT REVIEWED  
> **Owner:** Review coordinator (pending assignment)  
> **Reviewer:** Independent Reviewer (pending assignment)  
> **Last updated:** 2026-10-28

## Review Scope

Target: `docs/DESIGN_PROCESS.md`, at a **pinned, recorded documentation commit**. All unchecked items are **unverified**, not failed or passed. Do not record PASS from a locally authored file alone.

## A. Decision Fidelity

- [ ] F02-01 Q01: Stage-gated iterative model is explicit.
- [ ] F02-02 Q02: Inputs, outputs, Gate conditions and responsibility roles are specified without copying all stage checklists.
- [ ] F02-03 Q03: G0–G10 stages are preserved in order.
- [ ] F02-04 Q04: Concurrent drafts have explicit dependency approval gates.
- [ ] F02-05 Q05: Scope → Alternatives → Decision → Draft → Review → Evidence → Approve is defined.
- [ ] F02-06 Q06: P0/P1/P2 document-workflow tailoring is actionable.
- [ ] F02-07 Q07: Owner, Independent Reviewer and Gate Approver have separate responsibilities.
- [ ] F02-08 Q08: Deliverables, traceability, evidence and reviewer decision jointly govern gate exit.
- [ ] F02-09 Q09: Planned, Draft, In Review, Approved and Superseded are defined distinctly from a blocked dependency.
- [ ] F02-10 Q10: PR plus review; ADR reserved for architecture-significant changes.
- [ ] F02-11 Q11: One canonical traceability matrix with stable IDs is required.
- [ ] F02-12 Q12: Each substantive claim connects to an appropriate evidence category and baseline.
- [ ] F02-13 Q13: P0/P1/P2 findings and PASS/CONDITIONAL PASS/BLOCKED verdicts are operationally defined.
- [ ] F02-14 Q14: Discrepancies trigger impact analysis and controlled updates.

## B. Technical and Governance Quality

- [ ] F02-15 Every G0–G10 row has purpose/input, outputs, and an exit criterion.
- [ ] F02-16 Stage dependencies do not imply strict waterfall or unrestricted parallel approval.
- [ ] F02-17 An author cannot self-approve critical deliverables; team-of-three assignment remains practical.
- [ ] F02-18 Approved document != approved gate != observed runtime result.
- [ ] F02-19 Conditional PASS has explicit restrictions and cannot conceal P0 issues.
- [ ] F02-20 Planned files are labeled and not presented as clickable verified links.
- [ ] F02-21 Evidence claims name the pinned SHA, context and limitations appropriate to their type.
- [ ] F02-22 Change-control updates contract dependents and invalidates or re-reviews affected approval claims.
- [ ] F02-23 The Document Register alone is authoritative for status/owners; central traceability remains singular.

## C. Editorial and Repository Verification

- [ ] F02-24 English normative text, one H1, consistent heading levels and terminology.
- [ ] F02-25 Mermaid renders in GitHub; Markdown tables and code fences are valid.
- [ ] F02-26 All active relative links resolve to tracked files at the pinned revision.
- [ ] F02-27 Source and docs repository baselines and working-tree status are recorded.
- [ ] F02-28 Named owner, independent reviewer and gate approver are documented.
- [ ] F02-29 No unsupported `PASS`, `VERIFIED`, `IMPLEMENTED` or release claim.
- [ ] F02-30 No secrets, credentials or sensitive test data disclosed.

## Reviewer Commands (PowerShell)

Run in the **documentation repository** after adding the files:

```powershell
git remote -v
git branch --show-current
git rev-parse HEAD
git status --short --branch
git diff --check
git ls-files docs
Get-Content .\docs\DESIGN_PROCESS.md -Encoding utf8
```

For **new untracked files**, `git diff --check` does not inspect their contents. Stage or otherwise inspect the new files before trusting the check, then re-run `git diff --cached --check`. Check GitHub's rendered Mermaid and all newly active links separately.

## Findings and Gate Record

| Field | Value |
| --- | --- |
| Documentation repository URL | NOT RECORDED |
| Documentation commit SHA | NOT RECORDED |
| Source repository commit SHA (if referenced) | NOT RECORDED |
| Reviewer / Approver | NOT ASSIGNED |
| Review date | NOT RECORDED |
| P0 / P1 / P2 findings | NOT REVIEWED |
| Dependency status | NOT VERIFIED |
| F02 verdict | **NOT REVIEWED** |

### Decision Rubric

- **PASS:** all mandatory checks verified, no open blocking issue, independent reviewer and approver sign-off recorded.
- **CONDITIONAL PASS:** no P0; bounded P1 with owner, action, due trigger, restrictions, and recorded acceptance.
- **BLOCKED:** P0, missing mandatory dependency or evidence, or no credible independent review.
- **NOT REVIEWED:** default until an actual review is performed.

## Findings Log (complete during review)

| Finding ID | Severity | Location / claim | Evidence / baseline | Owner | Action | Status |
| --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | — | No findings recorded yet; review not performed |

**Gate approval must be recorded at a specific revision.** Do not turn checkboxes into historical claims until the evidence is available.
