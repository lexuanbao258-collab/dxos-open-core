# F03 — Document Register Review Checklist

> **Review ID:** G0-F03-REVIEW  
> **Status:** NOT REVIEWED  
> **Candidate path:** `docs/00-project/document-register.md`  
> **Reviewed Git SHA:** NOT PROVIDED  
> **Author / Reviewer / Approver:** TBD / TBD / TBD  
> **Date:** 2026-10-28 (draft created; not approval)

## Baseline and inventory

- [ ] Record the exact remote, branch, HEAD SHA and working-tree status of `DX_OS_DOCS`.
- [ ] Capture tracked files (`git ls-files docs`) and untracked files separately.
- [ ] Reconcile every candidate path against Git; do not equate proposal with presence.
- [ ] Assign lifecycle from `Planned / Draft / In Review / Approved / Superseded` only after evidence.
- [ ] For each `Planned` entry, record that its creation is actually accepted as part of the backlog.
- [ ] Review any documents not in the proposed outline, including unexpected or legacy paths.

## Identity and governance

- [ ] Every registered document has exactly one unique `DOC-###` ID.
- [ ] Renamed/retired files retain their stable IDs; new IDs are never recycled.
- [ ] Six main columns are used consistently across 12 areas.
- [ ] Required deliverables have confirmed owner and independent reviewer; otherwise record blocker.
- [ ] Gate Approver is documented in the relevant gate record, not invented in the register.
- [ ] Approved status has an independent review decision tied to the exact revision.
- [ ] Superseded entries identify replacement or explicit reason.

## Integrity, dependencies, and traceability

- [ ] All active links resolve on the reviewed repository baseline.
- [ ] Nonexistent/unverified files use inline code, not Markdown hyperlinks.
- [ ] Gate mappings are correct, especially cross-stage G0/G1/G2 and G3–G9 documents.
- [ ] Dependency index references valid IDs or explicitly labeled candidate paths.
- [ ] Requirements traceability points to a single canonical matrix (if approved/created).
- [ ] Register updates are enforced for document-changing PRs.
- [ ] No document approval implies code/test/reuse acceptance without evidence.
- [ ] English, heading levels, Markdown tables, and style guide conventions conform to F01/F02.

## Reviewer decision

| Field | Entry |
| --- | --- |
| Result | NOT REVIEWED |
| Remaining P0 | Unknown until inventory and review |
| Remaining P1/P2 | Unknown |
| Reviewed baseline | Not available |
| Reviewer sign-off | Pending |
| Gate Approver sign-off | Pending |

**Decision rule:** PASS only when mandatory criteria and independent sign-off are satisfied. CONDITIONAL PASS requires no P0 and bounded P1, owner, next action, and approval. BLOCKED if P0 or mandatory evidence/dependency/review is missing at decision time. Until an actual review occurs, use NOT REVIEWED.
