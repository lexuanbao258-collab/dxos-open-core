# F06 — G0 Gate Review Acceptance Checklist

> **Artifact:** `docs/10-reviews/g0-gate-review.md`  
> **Document state:** DRAFT / NOT REVIEWED  
> **Purpose:** Independent verification checklist; unchecked items are not evidence of failure, completion or approval.

## A. Decision Fidelity and Document Quality

- [ ] 01. All fourteen F06 Q01–Q14 decisions are reflected without contradiction.
- [ ] 02. Entire document is in English and follows Documentation Standard v1.0.
- [ ] 03. Exactly one top-level H1; consistent heading hierarchy, terminology and formatting.
- [ ] 04. F06 is a summary Gate record rather than a duplicate of F03, F05 or per-file reviews.
- [ ] 05. Every F01–F07 deliverable and F08 (if separate) is covered, including F06 itself.
- [ ] 06. Document statuses remain distinct from Gate outcomes.

## B. Baseline and Coverage

- [ ] 07. Code repository remote, branch and exact reviewed SHA are independently verified.
- [ ] 08. Docs repository remote, branch, exact reviewed SHA and tracked/untracked inventory are verified.
- [ ] 09. Review timestamp includes timezone and connects to the same code/docs snapshot.
- [ ] 10. All required G0 file paths are validated against Git (no fabricated links).
- [ ] 11. F03 inventory, document status and ownership agree with the reviewed baseline.
- [ ] 12. F07 ADR index is reviewed and F08 editorial standard location is confirmed.

## C. Evidence and Findings

- [ ] 13. Each deliverable has its own evidence, findings and file-level review result.
- [ ] 14. Evidence establishes the actual claim and exact source revision, not just a PR's existence.
- [ ] 15. G0 findings have stable review-local IDs, proposed severities and clear remediation.
- [ ] 16. Confirmed cross-gate open issues are linked to F05 without automatically blocking unrelated Gates.
- [ ] 17. Any P0 G0 finding has a verified resolution before PASS or CONDITIONAL PASS.
- [ ] 18. Residual P1 on CONDITIONAL PASS has owner, action, deadline/trigger, limitation and approval.
- [ ] 19. Reviewer confirms issue severity/Gate mapping rather than accepting preparer proposals silently.
- [ ] 20. Internal/external links, Mermaid (if used) and Markdown render are verified.

## D. Independent Decision

- [ ] 21. Reviewer identity is established and independent of the primary author.
- [ ] 22. Gate Approver is explicitly designated with a documented authority source.
- [ ] 23. Reviewer records the checked claims, outcomes and immutable evidence references.
- [ ] 24. Approver signs PASS / CONDITIONAL PASS / BLOCKED, or leaves NOT REVIEWED if no review occurred.
- [ ] 25. Any approval is bound to a verified code/docs baseline pair and review timestamp.
- [ ] 26. G1 draft permissions and conditions for dependent work are recorded.
- [ ] 27. Post-approval change-impact and re-review triggers are documented.

## Review Record Template

| Field | Entry |
| --- | --- |
| Reviewer | NOT RECORDED |
| Approver | NOT RECORDED |
| Code SHA | NOT FORMALLY PINNED |
| Docs SHA | NOT VERIFIED |
| Review timestamp (ISO 8601) | NOT RECORDED |
| Evidence index / PR | NOT RECORDED |
| P0 findings | NOT INDEPENDENTLY CLASSIFIED |
| P1/P2 findings | NOT INDEPENDENTLY CLASSIFIED |
| Gate verdict | **NOT REVIEWED** |
| Authorized decision link | NOT AVAILABLE |

A checked checklist is **not** a Gate approval by itself; the reviewer and authorized approver must provide the decision record.
