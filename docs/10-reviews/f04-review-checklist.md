# F04 — Baseline and Ownership Review Checklist

> Gate: G0  
> Artifact under review: `docs/00-project/baseline-and-ownership.md`  
> Review status: NOT REVIEWED  
> Prepared: 2026-10-28

## Review instructions

Use `[x]` only when the reviewer has inspected the stated evidence and identified the exact baseline. `TBD`, presumed ownership or inaccessible repositories do not satisfy a requirement. P0 findings block approval; P1 may qualify for conditional acceptance **only** within the Stage Gate rules and with documented scope and remediation.

## A. Baselines and provenance

- [ ] F04-01 Code repository canonical URL and intended branch/ref verified.
- [ ] F04-02 Documentation repository canonical URL and intended branch/ref verified.
- [ ] F04-03 Both full 40-character SHA values independently verified.
- [ ] F04-04 Verification dates/times, purpose and working-tree conditions recorded.
- [ ] F04-05 Candidate and approved baselines explicitly distinguished.
- [ ] F04-06 Baseline pair consistency examined against relevant design claims.

## B. Authority and accountability

- [ ] F04-07 Workstreams and responsibility boundaries reviewed with the team.
- [ ] F04-08 Actual document owners/reviewers cross-checked with F03.
- [ ] F04-09 RACI has clear Responsible and Accountable roles for each decision class.
- [ ] F04-10 Independent reviewer identities verified and differ from the author.
- [ ] F04-11 Named Gate Approver and source of authorization recorded in Gate record.
- [ ] F04-12 Backup reviewer and escalation procedures accepted and feasible.

## C. Change and risk control

- [ ] F04-13 Editorial/Contract/Architecture/Gate classifications defined consistently.
- [ ] F04-14 Semantic and security contract changes have mandatory impact review.
- [ ] F04-15 Discrepancy entries require affected contracts, owner, resolution and evidence.
- [ ] F04-16 Significant architecture decisions trigger ADR review.
- [ ] F04-17 Rules for preserving prior approved baselines are actionable.
- [ ] F04-18 PR changes require assessment of register and traceability impact.

## D. Documentation quality and final review

- [ ] F04-19 English and Markdown structure meet Documentation Standard v1.0.
- [ ] F04-20 No fabricated user assignment, SHA, approval, review or status.
- [ ] F04-21 References are verified or clearly marked as intended references.
- [ ] F04-22 Review findings include severity, action, owner and evidence link.
- [ ] F04-23 Independent reviewer recommendation is recorded for a precise SHA.
- [ ] F04-24 Gate Approver records decision and any conditions separately.

## Findings log

| Finding ID | Severity | Criterion | Evidence / observed gap | Action | Owner | Resolution link |
| --- | --- | --- | --- | --- | --- | --- |
| F04-OPEN-01 | P0 | F04-02, 03, 06 | Documentation repository baseline has not been verified. | Inspect authorized repository and record the baseline pair. | TBD | Pending |
| F04-OPEN-02 | P0 | F04-10, 11, 23, 24 | Independent review and named Gate Approver not provided. | Obtain review and Gate decision against a specific SHA pair. | TBD | Pending |

## Review decision record (to complete)

| Item | Result |
| --- | --- |
| Code SHA reviewed | Not yet established as approved baseline |
| Docs SHA reviewed | Not verified |
| Reviewer and review date | Pending |
| Gate Approver and decision date | Pending |
| Outcome | **NOT REVIEWED** |
| Outstanding P0 | Baseline and approval evidence missing |
| Review URL / PR | Pending |

**No F04 PASS claim is warranted from this prepared checklist.**
