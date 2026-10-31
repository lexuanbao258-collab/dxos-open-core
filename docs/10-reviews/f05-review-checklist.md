# F05 — Open Issues Register Review Checklist

> Status: Draft / Not Reviewed | Review scope: `docs/10-reviews/open-issues.md`

## A. Governance and consistency

- [ ] F01: English, readable Markdown, meaningful headings, supported navigation.
- [ ] F02: Gate outcomes distinct from OI statuses; P0/P1/P2 and conditional acceptance respected.
- [ ] F03: Document Register remains authority for file-level statuses and ownership; no duplicated document inventory.
- [ ] F04: Git baseline and Gate approval authority are not invented; reviewer separate from author.
- [ ] Stable unique OI IDs and controlled update policy documented.

## B. Source reconciliation

- [ ] Record code/docs repository URLs, branches, full SHAs, and snapshot date in review evidence.
- [ ] Re-run GitHub issue search at the recorded baseline; include relevant review/PR findings not represented by Issues.
- [ ] Verify each linked source exists and still describes an unresolved concern.
- [ ] Verify whether the 11 candidate entries are still open, and include newly relevant items.
- [ ] Confirm type/priority per item; distinguish source-declared P0 from independent severity decision.
- [ ] Confirm owner assignments; `TBD` must be resolved or explicitly accepted as a review limitation.
- [ ] Confirm Gate mapping, affected documents, dependencies and closure criteria.

## C. Lifecycle and review policy

- [ ] Open/In Progress/Pending Verification/Closed transitions are defined.
- [ ] A source link and evidence are required for Pending Verification and Closed.
- [ ] Closure includes resolution, affected-file updates, adequate evidence and independent reviewer confirmation.
- [ ] Closed items are retained in GitHub/PR/Gate history rather than silently deleted.
- [ ] P0 affects only linked Gate(s), including transitive dependency review where justified.
- [ ] No claim that an issue is a proven runtime defect solely because it is open.

## D. Acceptance evidence

- [ ] Review covers representative Security, Contract, Dependency and Evidence entries.
- [ ] Findings are classified P0/P1/P2 with rationale and remediation owner.
- [ ] Review record links exact SHA/PR and includes date, reviewer and decision.
- [ ] Gate Approver is designated separately from document author and independent reviewer.
- [ ] No F05 PASS while required baseline, coverage and independent sign-off remain unverified.

## Review record (to be completed by actual reviewers)

| Field | Value |
|---|---|
| Docs baseline SHA | TBD |
| Code baseline SHA | TBD |
| Review date | TBD |
| Author | TBD |
| Independent reviewer | TBD |
| Gate approver | TBD |
| Findings / evidence links | TBD |
| Decision | NOT REVIEWED |

**Current assessment:** DRAFT / NOT REVIEWED. Source-backed initial inventory exists; no independent sign-off is recorded.
