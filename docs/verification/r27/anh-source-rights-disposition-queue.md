<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors

SPDX-License-Identifier: Apache-2.0
-->



**Document ID:** `R27-EVD-004`  
**Document version:** `0.1.0` (metadata baseline; existing evidence retained)  
**Document status:** `In Review` (Draft PR #144)  
**Evidence verification:** `PARTIAL` — only explicitly described technical checks may be treated as PASS  
**Rights disposition:** `PENDING` — no file/portion clearance or public release approval  
**Evidence owner:** Ánh — contributor scope / decision queue  
**Independent reviewer:** Yến — technical review completed; rights disposition pending  
**Tracking issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Document PR:** [#144](https://github.com/tricore-opentrix/opentrix/pull/144)  
**R24 source-only candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Metadata boundary:** these labels route this document only; original claims, reviewer decisions and source-rights obligations are unchanged.# R27 / Issue #138 — Ánh contribution and source-rights disposition queue

**Status:** TECHNICAL INVENTORY CHECKED / ORIGIN PARTIAL / RIGHTS AND NOTICES PENDING  
**Owner of this contributor scope:** Ánh (`@ngang02012007-crypto`).  
**Independent disposition:** Yến or Bảo, provided the reviewer is not approving their own contributions.  
**Audited candidate source:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e` (frozen source-only candidate, **not** a claim about a later release).  
**Related:** [R27 source-rights issue #138](https://github.com/tricore-opentrix/opentrix/issues/138), [candidate rights ledger in Draft #139](https://github.com/tricore-opentrix/opentrix/pull/139), and [Ánh's source-scope handoff](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702734422).

This is a narrowly scoped, reviewable **decision queue**, not a new blanket Apache-2.0 authorization. A file being committed by a contributor, having a REUSE/SPDX header, or passing CI does not prove copyright ownership, absence of external copied material, permission for every embedded portion, or third-party attribution compliance. Do not close #138 or clear public R24 release based only on this document.

## Evidence already recorded

- At the audited tree, the **107 selected paths** in the source-rights ledger matched Git blob hashes: 80 Frontend, 14 Portal BFF, 10 Core Data production, 2 Core Data tests, and one infrastructure/storage package file. The [handoff comment](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702734422) also enumerates two current Data documentation paths; that broader **109-path inventory** must not be confused with a personal authorship count.
- Ánh's earlier direct statement authorizes Apache-2.0 distribution **only for the portions she is entitled to license** and describes her requirements-to-AI workflow without external snippets supplied to the AI. This does **not** establish that an AI output contains no adapted upstream text or that another contributor's changes are covered. [Recorded contributor statement](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663564250).
- The nine Data contract files were introduced in Ánh-associated [`40f0d71`](https://github.com/tricore-opentrix/opentrix/commit/40f0d71c9b54b0ac993bd48c375d3e740d6c20c1); the two Data test files in [`973d395`](https://github.com/tricore-opentrix/opentrix/commit/973d39567335e41cb421f04cb6fed5b48f0a9b8d). Bảo later altered the tests in [`fcc45fb`](https://github.com/tricore-opentrix/opentrix/commit/fcc45fb65dc6c39ee5f75fd32f99f2a4de05241f); Bảo must independently address those portions.
- `frontend/public/favicon.svg` was matched to audited Git blob `2faa0e08edb86261c960119f997703f70849477b`. Ánh reported a text-instructed ChatGPT-generated asset, not an uploaded external visual reference. Static inspection showed no embedded image, script, font or imported asset; **source similarity, external SVG/template reuse and possible required notices remain unresolved**.
- The Frontend/Portal source was integrated via [PR #103](https://github.com/tricore-opentrix/opentrix/pull/103). Squash and review history cannot identify the lawful rights holder of every portion. `frontend/src/App.tsx` and `frontend/src/pages/Dashboard.tsx` are **candidate investigation pointers** for screen-label edits (see [PR #52](https://github.com/tricore-opentrix/opentrix/pull/52/files)), not confirmed attribution for all content.

## Independent disposition matrix

| Source group | Completed technical evidence | Outstanding proof / question | Current verdict | Reviewer action |
| --- | --- | --- | --- | --- |
| Nine `core/data` contract source files | File/commit mapping in #138; contributor-scoped AI disclosure and limited authorization | Identify any external snippets/examples and necessary attribution at affected portions; confirm rights for the exact candidate bytes | `TECHNICAL_PASS / RIGHTS_PENDING` | Independently review recorded scope, confirm permitted portions, document unresolved lines |
| Two Data contract tests | Introduced in `973d395`; Bảo's `fcc45fb` modifications called out separately | Per-portion authorization for Ánh and Bảo, test fixture lineage, notices | `MIXED_CONTRIBUTION / RIGHTS_PENDING` | Seek Bảo's own disposition for his patch; never attribute his patch to Ánh |
| `core/data/package-info.java` and `infrastructure/storage/package-info.java` | Associated with Bảo's `d53a273` history | Bảo's contributor-specific source and licensing disposition | `BAO_REVIEW_REQUIRED` | Do not claim authorization for Ánh |
| Frontend (80 paths) / Portal BFF (14 paths) | Path/blob reconciliation for all 94 candidate paths | Identify which hunks/labels Ánh authored or instructed, any third-party CSS/UI/code/template dependencies and known notices | `INVENTORY_PASS / PORTION_MAPPING_UNKNOWN` | Trace original PRs/diffs and obtain the responsible contributor's declarations per affected portion |
| `frontend/public/favicon.svg` | Exact blob, creator's text-to-AI disclosure and scoped consent, inline SVG source inspected | Confirm no known external SVG/template adaptation, document any attribution obligations, independent reviewer judgement | `ASSET_ORIGIN_PARTIAL / RIGHTS_PENDING` | Yến/Bảo review exact SVG and contributor declaration, record verdict tied to blob |
| Historical Data documentation and deleted paths | Historical paths, introduction and removal noted in #138 | Keep deleted files outside the frozen candidate's release-scope claims | `HISTORICAL_ONLY` | No spurious candidate-source clearance for absent paths |
| Release-wide exceptions / other contributors | 37 historical exception paths inventoried; R24 candidate excludes wrapper JAR in reported source-only archive | Independent rights disposition per applicable path; upstream texts/notices, archived source SHA and manifest validation | `OUT_OF_ANH_SCOPE / RELEASE_BLOCKED` | Yến/Bảo reconcile release-wide register, R24 acceptance and external dependencies |

## Sign-off gates (none implied completed by this document)

1. Independent reviewer checks each group against the exact audited Git blob(s), attached prior declarations and actual affected portions. Record who reviewed what, the date, and whether evidence is `PASS`, `UNKNOWN`, `PENDING`, or `BLOCKED`; do not infer rights from author metadata.
2. Obtain contributor-specific decisions for Bảo's mixed Data test patch and package boundaries; resolve or explicitly disposition UI contribution mapping and external-origin/attribution questions.
3. Independently assess `favicon.svg` provenance and possible third-party template/SVG references, including required attributions.
4. Reconcile this historical candidate with the **final frozen source-only archive**, including full member manifest, SHA-256, license/notice texts and all 37 exceptions. Any different source SHA requires delta review.
5. Only authorized reviewers may record legal/source-rights disposition; even then, R24 public release requires its **separate** acceptance gate (Draft #139). No tag or release approval is granted here.

**Current overall verdict:** `R27_ANH_TECHNICAL_MAPPING_PASS / R27_SOURCE_RIGHTS_PENDING / R24_PUBLIC_RELEASE_BLOCKED`.
