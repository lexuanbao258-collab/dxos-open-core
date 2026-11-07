<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27-RPT-001 — Source-Rights Verification & R24 Handoff Report (Draft)

**Document ID:** `R27-RPT-001`  
**Version:** `0.1.0`  
**Document status:** `Draft` — Step 5 preparation, **not** final accepted report  
**Evidence verification:** `PARTIAL` (bounded technical evidence cited below)  
**Rights disposition:** `PENDING` — no repository-wide source-rights clearance  
**Report coordinator:** Yến (`@phanyen06ym-ien`)  
**Independent reviewers (proposed):** Bảo and Ánh for disjoint evidence and documentation; rights decisions routed per portion  
**Tracking issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138) — OPEN  
**R24 release candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`; release remains **BLOCKED**  
**Historical Identity audit baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`  
**Companion license/NOTICE work queue:** [R27-LIC-001](r27-third-party-license-notice-assessment.md)  
**Approved documentation conventions:** [PR #146 at `7dc2493`](https://github.com/tricore-opentrix/opentrix/blob/7dc2493c6b116689384976f2c7f872307674b3c2/docs/verification/r27/README.md); [Step-4 PR #147 at `94a9989`](https://github.com/tricore-opentrix/opentrix/blob/94a998978e811a546fdd7a8840af3e4b9686f286/docs/verification/r27/r27-status-and-id-vocabulary.md)

## Executive decision

**R27 final verdict: NOT YET DETERMINED — PENDING SOURCE-RIGHTS RESOLUTION.** This report is a **draft evidence handoff**, not a final permission, legal opinion, certification, release announcement, source distribution or approval to merge any Draft PR. The current record supports technical integrity of the **frozen, source-only** R24 candidate but not independently verified copyright/Apache-2.0 authority for all distributed content. Do not use `PASS` without naming an exact technical claim and SHA.

## 1. Audited scope and reproducibility

| Measure | Verified scope at candidate | Qualification |
| --- | --- | --- |
| Git source | `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e` | Fixed source-only candidate, not current PR documentation HEAD |
| Tracked Git files | 347 | Includes one excluded Gradle upstream JAR |
| Packaged tracked source/text | 346 | No implied source-rights decision |
| Archive-only ancillary metadata | 10 | They are additional members, not Git-tracked source |
| Regular archive members | 356 | 346+10 |
| Rights remediation records | 357 | 347 original tracked Git paths + 10 archive-only items |
| Historical exception inventory | 37 paths | Requires evidence-backed disposition; count does not indicate approval |
| Exact-source archive runs | [42244683510](https://github.com/tricore-opentrix/opentrix/actions/runs/42244683510), [42245290247](https://github.com/tricore-opentrix/opentrix/actions/runs/42245290247) | Independent reproducibility work passed; publication SKIPPED |
| Matching source tar.gz SHA-256 | `93e6e0ba5d708120c6ec494af3e50c8959cccb4c442a8bba9a2e9c617c2d448f` | Applies only to the two inspected source-only archives |
| Source archive manifest checks | 355/355 | Manifest itself excluded from hash map; archival integrity is not rights clearance |

Evidence: [R27-REV-001 independent technical gates](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-independent-review-gates-187dbd2.md), [PR #139](https://github.com/tricore-opentrix/opentrix/pull/139), [357-row rights remediation CSV](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-rights-remediation-register-187dbd2.csv).

## 2. Workstreams and open decisions

| Evidence / workstream | Current reviewed technical/document state | Outstanding rights or attribution gate | Evidence coordinator / independent reviewer |
| --- | --- | --- | --- |
| Repository-wide register / R27-REG-001 | 357 rows with `RIGHTS_PENDING`; Draft [#143](https://github.com/tricore-opentrix/opentrix/pull/143) | File/portion rightsholder, upstream origin and reviewer decision; 37 historical exceptions | Bảo (register) / Yến, Ánh for disjoint portions |
| Identity G1–G6 / R27-DEC-001 & EVD-001/002 | Yến's personal, **scope-limited** contributor statement and G3/G4 technical origin matrix; [Draft #142](https://github.com/tricore-opentrix/opentrix/pull/142) | 14 historical uncertain source files, G3/G4 outside examples/fixtures, Apache-2.0 extent, upstream NOTICE; separate Bảo ID-09/11/28/29 | Yến / Ánh |
| Backend/Core/Gateway / R27-DEC-002 & EVD-003 | Bảo's **unsigned** evidence checkpoint and reported AI-assisted methods; [Draft #139](https://github.com/tricore-opentrix/opentrix/pull/139) | Actual external examples/JWT fixtures, separate rights/NOTICE and signed/scoped confirmation; `NOT_RECALLED` is unresolved | Bảo / Yến |
| Ánh Data/Frontend/Portal/favicon / R27-EVD-004 | Technical 107-source/109-handoff split, mixed Data contributions and review routing; [Draft #144](https://github.com/tricore-opentrix/opentrix/pull/144) | Source/asset and UI template origins, exact personal contribution hunks, favicon fragment and notices, Bảo `fcc45fb` portion | Ánh / Yến; Bảo for own hunks |
| Contributor routing and reviewers / R27-EVD-005 & REV-001 | Documentation package accepted for metadata-only at PR #143 HEAD `6cb5a32` | Human declarations, reviewer decisions and affected portion scope remain pending | Bảo (routing) / independent per claim |
| License and NOTICE / R27-LIC-001 | Step-5 Draft queue identifies existing project and generated notices | Actual upstream license/NOTICE text requirements, font/icon source and attribution, Gradle included scripts, collective labeling | Yến coordinating / Bảo, Ánh distinct scopes |
| Documentation convention / R27-REG-DOC-001/002 | [Step 3 approved #146](https://github.com/tricore-opentrix/opentrix/pull/146); [Step 4 approved #147](https://github.com/tricore-opentrix/opentrix/pull/147) and index sync re-reviewed at `7dc2493` | Approved documentation is **not** evidence of copyright ownership or release authorization | Yến coordinating / Bảo and Ánh |

## 3. Step-5 Draft handoff checklist

- [x] Identify all live R27 documentation workstreams and link existing Draft evidence (no synthetic contributor declarations).
- [x] Create `R27-LIC-001` and this `R27-RPT-001` as **Draft** deliverables with unresolved obligations visible.
- [x] Separate the historical Identity audit SHA, frozen release candidate and document PR HEADs.
- [x] Preserve `UNKNOWN/NOT_RECALLED/RIGHTS_PENDING` evidence states; never imply global source clearance.
- [ ] Receive exact-HEAD CI and independent documentation review of both new Draft artifacts.
- [ ] Add their stable IDs and pinned links to Document Register #146 through a separately reviewed update.
- [ ] Verify license/NOTICE upstream texts and any remediation decisions with independent rights reviewers (primarily subsequent Step 6–7).
- [ ] Reconcile final R24 package inputs and distribution scope; rerun necessary tests if new source SHA or archive members changed.
- [ ] Produce final, separately authorized R27 acceptance record only after evidence-backed decisions cover all applicable source/portion rights and exceptions (Step 8).

## 4. Risk and blockers — not yet cleared

| Priority | Blocking matter | Required closure evidence | Proposed routing |
| --- | --- | --- | --- |
| P0 | Copyright scope per source/portion and collective SPDX/NOTICE authority | Direct scoped contributor/rights-holder authorization plus independent reviewer, specific affected paths/blobs | Yến coordinates; Bảo/Ánh attest only own portions |
| P0 | Outside snippets, adapted examples and AI-assisted output origin gaps | Honest source links when known, actual third-party licensing and NOTICE obligations or remediate/remove | Identity: Yến/Ánh; Backend: Bảo/Yến |
| P0 | Mixed Data tests / `fcc45fb` changes | Hunk-level evidence, separate contributor rights and independent decisions | Ánh originals, Bảo hunks; Yến review |
| P0 | Favicon, UI screens/templates, font/icon packages | Real origin/inclusion evidence, upstream license/attribution and independent asset decision | Ánh evidence, Yến review |
| P0 | Gradle included wrapper scripts/properties | Upstream rights/notice checks independent of excluded binary JAR | Bảo evidence, Yến review |
| P0 | Historical 37 exceptions and all applicable 357 remediation items | Explicit resolved, remediated or verified excluded decisions per release scope; no blanket clearance | Yến routing, Bảo/Ánh independent checks |
| P0 | LICENSE, NOTICE, THIRD-PARTY-NOTICES and archive bundle | Source-specific upstream obligations, final distribution manifest and independent assessment | Bảo/Yến |
| P0 | R24 separate release acceptance | Reproducible final candidate and authorized R24 sign-off; source-only != future binary/image release | R24 owner and distinct approval |

## 5. Completion/acceptance gates (future; not currently satisfied)

### Step 5 — Documentation-only readiness
1. This draft and the companion `R27-LIC-001` reference traceable existing materials, distinguish facts from questions, and preserve open rights.
2. CI and scoped Bảo/Ánh reviews approve documentation at the exact current HEAD.
3. Document Register references both stable IDs after its own independent reviewed update.

### Step 6–7 — Source-rights verification and remediation
4. Contributor-owned or third-party-controlled source is assessed per affected portion with traceable permission/license/NOTICE obligations; missing information is not fabricated.
5. Each historical exception and applicable remediation record is explicitly dispositioned, or the source is rewritten/replaced or demonstrably excluded.
6. Any new source candidate/source-only archive is reconciled with new SHA, manifest and reproducibility evidence.

### Step 8 — Independent R27 acceptance
7. Independently review final file/portion rights, attribution/NOTICE, exceptions, all release-included member checks and updated immutable evidence.
8. An authorized reviewer signs the R27 acceptance/remaining-blocker verdict; only if fully satisfied may R27 be marked `PASS`.
9. R24 still requires its **own** release acceptance. R27 acceptance cannot by itself authorize merging PRs, tagging or publishing.

**Current conclusion:** **STEP 5 DRAFT PREPARED / R27 SOURCE RIGHTS PENDING / R24 RELEASE BLOCKED.** Maintain Issue #138 OPEN and [R24 #139](https://github.com/tricore-opentrix/opentrix/pull/139) Draft. Do not state `R27 PASS` or `RIGHTS CLEARED` on the basis of this report.
