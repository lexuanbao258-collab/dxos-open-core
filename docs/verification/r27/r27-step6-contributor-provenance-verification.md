<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27-EVD-006 — Contributor/Portion Rights & Provenance Verification Queue (Step 6)

**Document ID:** `R27-EVD-006`  
**Version:** `0.1.0`  
**Document status:** `Draft` — evidence request and mapping, not substantive rights approval  
**Evidence verification:** `PARTIAL` — already reported technical identities and statements are cited; required new confirmations are NOT_VERIFIED  
**Rights disposition:** `PENDING` for all unresolved included sources and affected portions  
**Coordinator:** Yến (`@phanyen06ym-ien`); this role does not imply copyright ownership  
**Review routing:** Ánh reviews Yến's Identity portions; Yến reviews Bảo's Backend/Gateway and Ánh's Data/Frontend/Portal portions; Bảo reviews eligible Yến-coordinated root/metadata portions that he did not contribute; mixed Data tests require portion-level independence  
**Tracking:** [Issue #138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Candidate SHA:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`; historical Identity baseline `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`  
**Existing rights ledger:** [R27-REG-001, #143 at `6cb5a32`](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-rights-remediation-register-187dbd2.csv)  
**Step-5 obligation queue:** [R27-LIC-001, #148 at `25faa21`](https://github.com/tricore-opentrix/opentrix/blob/25faa214835093d591eb381be910e1efc1b8c085/docs/verification/r27/r27-third-party-license-notice-assessment.md)  
**Related machine-readable queue:** [step-6 contributor action matrix](r27-step6-contributor-rights-action-matrix.csv)

## 1. What Step 6 verifies — and what this Draft does not

Step 6 must obtain **actual contributor-scoped source-origin/rights confirmations**, map them to the correct immutable path and portion, identify externally adapted source/fixtures/assets and their license/NOTICE obligations, and ask an **independent reviewer** to accept, challenge or reject each concrete claim. This Draft is a structured starting point, **not** a completed verification, not any contributor's first-person declaration, and not an authorization for Apache-2.0 distribution.

Audit boundary: 347 tracked Git paths including one excluded upstream Gradle JAR; **346** source/text Git paths plus **10** archive-only ancillary members in the R24 source-only archive; **357** existing remediation rows (347+10). The [R25 exception inventory](https://github.com/tricore-opentrix/opentrix/blob/35e90c47/docs/verification/r25/exception-review.csv) has 37 data paths requiring individual treatment. No historical or existing CSV data is rewritten here. A rights **task owner** is not a verified copyright owner.

## 2. Existing attestations and limitations to preserve

- **Yến / Identity:** [R27-DEC-001 at #142](https://github.com/tricore-opentrix/opentrix/blob/2844530125ef884a047f976a91b3202103da3c91/docs/verification/r27/identity-contributor-declaration-yen.md) describes personally designed requirements and ChatGPT/Codex-assisted review/edit/test. It authorizes Apache-2.0 *only for portions within her lawful authority*. Specific external documentation/samples, model-output lineage, origin and license/NOTICE for files/fixtures remain `UNKNOWN/PENDING`. Historical G3/G4 [per-file source matrix](https://github.com/tricore-opentrix/opentrix/blob/2844530125ef884a047f976a91b3202103da3c91/docs/verification/r27/identity-g3-g4-per-file-source-origin-matrix-5daa145d.csv) is not a no-copy proof.
- **Bảo / Backend/Core/Gateway:** [R27-EVD-003 at #139](https://github.com/tricore-opentrix/opentrix/blob/2f6b8d7608214983be92db1f5ba0e4b8703885db/docs/verification/r27/bao-backend-core-gateway-evidence-checkpoint-187dbd2.md) is an **unsigned reconstructed checkpoint**, not a first-person legal consent. His existing [issue-reported work methods](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702663219) identify AI-assisted code for ID-09/11/28/29, but for ID-28/29 historical external snippet/fixture input is **NOT_RECALLED**. The 166 packaged Backend source files are not automatically Bảo-authored.
- **Ánh / Data/Frontend/Portal:** [R27-EVD-004 at #144](https://github.com/tricore-opentrix/opentrix/blob/188969ada812e0016ed952b5ad422e210b9b0055/docs/verification/r27/anh-source-rights-disposition-queue.md) maps **107 selected Git source/asset paths** (80 Frontend, 14 Portal BFF, 10 Data production, 2 Data tests, 1 infrastructure/storage package file), with 2 separate Data docs in a wider **109-path handoff**. This is *technical inventory*, not proof Ánh personally authored 107 files. Her direct [scope-limited declaration](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663564250) cannot cover Bảo's hunks or unknown external asset origin.
- **Mixed Data:** Ánh-associated test introductions at [`973d395`](https://github.com/tricore-opentrix/opentrix/commit/973d39567335e41cb421f04cb6fed5b48f0a9b8d); Bảo-associated later changes in [`fcc45fb`](https://github.com/tricore-opentrix/opentrix/commit/fcc45fb65dc6c39ee5f75fd32f99f2a4de05241f). Both `backend/src/test/java/com/tricore/dxos/core/data/InMemoryObjectStorage.java` and `backend/src/test/java/com/tricore/dxos/core/data/ObjectStoragePortTest.java` require **at least two separate content-portion checks**, independently judged by Yến. Neither contributor can approve their own portion.
- **Favicon:** `frontend/public/favicon.svg`, frozen Git blob `2faa0e08edb86261c960119f997703f70849477b`, was reported as text-prompted AI SVG. This neither proves upstream originality nor removes possible license/attribution review.
- **Third-party:** the [root NOTICE](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/NOTICE) and [CycloneDX-generated third-party notices](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/THIRD-PARTY-NOTICES.md) exist, but do not prove correct authorizations or satisfy all upstream NOTICE obligations. Follow [R27-LIC-001](https://github.com/tricore-opentrix/opentrix/blob/25faa214835093d591eb381be910e1efc1b8c085/docs/verification/r27/r27-third-party-license-notice-assessment.md).

## 3. Evidence-to-decision requirements (for EACH included file or material portion)

1. **Identity and scope:** original Git path, candidate `187dbd2` blob SHA-1, exact relevant introduction/change commit/hunk, distributed archive member or independently verified exclusion. If a historical Identity file differs from the frozen R24 blob, reconcile it: never transfer historic `5daa145d` rights decisions automatically.
2. **First-person statement:** actual contributor confirms only their own requirements/implementation/edit/AI use, remembers any externally supplied or adapted examples, and identifies **exact portion** they can authorize under Apache-2.0. If unknown, use explicit `UNKNOWN` / `NOT_RECALLED`; never invent `NONE`.
3. **External third-party source:** if identified, link authentic upstream source URL/version/license, specific adaptation, preserved copyright text and relevant LICENSE/NOTICE obligations; if none proven, do not assert no third-party input solely from prompt history or Git metadata.
4. **Independent review:** a reviewer unrelated to the relevant substantive portion must inspect the actual statement, code/hunk, archive scope and upstream obligations; record dated link, exact source/review SHA, claim accepted/rejected, unresolved questions and next action.
5. **Disposition:** update the **existing** 357-row rights register through separately reviewed work. Only authorized, evidence-backed `CLEARED`, `EXCLUDED`, `REMEDIATION_REQUIRED` or `NOT_APPLICABLE` decisions may replace `RIGHTS_PENDING`; a reviewer choosing `UNKNOWN` on origin implies that applicable rights remain `PENDING`, not clearance.
6. **Material changes:** if remediation changes source or archive bytes, generate a new source candidate and rerun affected manifest, rights and R24 two-run verification. Preserve the original candidate SHA and earlier decisions as audit history.

## 4. Direct contributor follow-up — precise questions, not inferred statements

### Yến: Identity and root coordination (Ánh independent review; Bảo for unrelated root material)
- For G1–G6 and especially G3/G4, identify actual user-supplied external snippets, Spring Security/OIDC API docs and fixture examples **if remembered**, with URLs only when genuinely known, affected portions and license/NOTICE; retain `UNKNOWN` if not. Distinguish own content from Bảo ID-09/11/28/29 and from SPDX-only changes.
- For tests, identify whether JWT samples/claims were project-created, adapted from external tutorials or unspecified; provide original files/commits where possible and explicit uncertainties.
- For root docs, `.github` templates, CI/scripts, Flyway V1–V4 (immutable), and 37 historic exceptions, identify original content owners and outside templates; coordinate group review without declaring blanket rightsholder status.

### Bảo: Backend/Core/Gateway and his specific Data hunks (Yến independent review)
- For ID-09/11/28/29, give **Bảo's own** file/portion-specific Apache-2.0 permission only within the rights Bảo holds, and disclose outside snippet/fixture sources, if any are actually recalled; an honest `NOT_RECALLED` is valid evidence of uncertainty but does not clear the portion.
- Specifically distinguish ID-28 mocked JWT claims and ID-29 optional Keycloak token test input from third-party copied fixtures. Never publish real tokens, credentials or private tool transcripts.
- For two `fcc45fb` Data-test changes, identify exact changed lines and any external snippets; do not claim ownership of the entire tests. Address Gradle included scripts/properties versus excluded JAR and upstream copyrights/NOTICE.

### Ánh: Data originals, Frontend/Portal and favicon (Yến independent review; Bảo only for portions he did not contribute)
- Confirm separately the Data contracts and original Data test hunks she actually designed/edited; preserve Bảo's `fcc45fb` portion as another contributor.
- Reconstruct specific Frontend/Portal screen name and UI/template/content changes, including `frontend/src/App.tsx` and `frontend/src/pages/Dashboard.tsx` **as investigation leads**, not established original author attribution.
- For SVG blob `2faa0e08`, explain any known external logos/templates/examples or model inputs, if any; identify actual font/icon packages embedded in source-only vs future built bundles and appropriate license/notice. Don't infer no copied content from geometric inline SVG alone.

## 5. Phase acceptance / release boundaries

**Step-6 documentation kickoff** may be accepted if this factual request matrix is correctly scoped and independent reviewers approve it at the *exact Draft PR HEAD*, with CI. **Step-6 substantive provenance verification** is complete only once each applicable distributed path and individual contributor portion has actual, independently reviewed origin and lawful licensing evidence, or is explicitly remediated/excluded with proof. A 357-row pending inventory is coverage of requests, **not proof of rights**. Step 7 handles remediation and re-verification; Step 8 handles final independent R27 acceptance.

- [ ] Bảo and Ánh validate this routing document and matrix (documentation review, exact HEAD/CI).
- [ ] Each contributor supplies new first-person, per-portion follow-up anchored to specific blobs/commits (or explicitly documents inability to establish provenance).
- [ ] Independent reviewers record accepted or rejected claim-by-claim decisions with evidence links.
- [ ] Every applicable one of 357 remediation rows and 37 historical exceptions is accounted for; no unresolved applicable right is declared cleared.
- [ ] Any material source changes reconciled to a fresh R24 candidate and reverified.
- [ ] A separate source-rights gate is actually satisfied before considering R27 PASS; R24 release signoff remains distinct.

**Current outcome:** `STEP_6_EVIDENCE_REQUEST_DRAFT / ACTUAL_CONTRIBUTOR_PROVENANCE_PENDING / R27_RIGHTS_PENDING / R24_RELEASE_BLOCKED`. Keep [#138](https://github.com/tricore-opentrix/opentrix/issues/138) OPEN and [#139](https://github.com/tricore-opentrix/opentrix/pull/139) Draft. Do not merge, tag or publish on this document's authority.
