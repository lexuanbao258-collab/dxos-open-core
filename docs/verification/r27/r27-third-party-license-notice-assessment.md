<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27-LIC-001 — Third-party License & NOTICE Assessment (Draft)

**Document ID:** `R27-LIC-001`  
**Version:** `0.1.0`  
**Document status:** `Draft` — proposed Step-5 assessment, not independently approved  
**Evidence verification:** `PARTIAL` — existence of named files and prior technical audits is observed; upstream rights and NOTICE completeness have **not** been verified  
**Rights disposition:** `PENDING` for unresolved distributed source/third-party content  
**Coordinator:** Yến (`@phanyen06ym-ien`)  
**Review route (proposed):** Bảo — release/Backend/Gradle & license inventory; Ánh — frontend/asset/attribution; neither approves rights in their own portions  
**Issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Frozen R24 source candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Identity historical baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`  
**Approved Step-4 vocabulary (documentation scope):** [R27-REG-DOC-002 — PR #147 at `94a9989`](https://github.com/tricore-opentrix/opentrix/blob/94a998978e811a546fdd7a8840af3e4b9686f286/docs/verification/r27/r27-status-and-id-vocabulary.md)  
**Source-rights register:** [357-row Draft #143 register at `6cb5a32`](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-rights-remediation-register-187dbd2.csv)

## Assessment boundary

This is a **discovery and action register**, not legal clearance or a certification of complete software dependencies. Only the fixed R24 **source-only** candidate is considered. Future Docker images, compiled/binary packages, container base images, embedded assets, frontend distribution bundles and runtime dependencies have distinct checks under R24; source-only inclusion does not automatically decide their obligations.

The candidate consists of 347 tracked Git paths, of which 346 source/text paths were included, one upstream Gradle Wrapper JAR was excluded, and 10 archive-only metadata/compliance paths were added. The 357 remediation entries equal 347 Git paths plus 10 archive-only entries, **not** 357 files shipped. The two-run R24 source-only payload was technically reproduced ([run A](https://github.com/tricore-opentrix/opentrix/actions/runs/42244683510), [run B](https://github.com/tricore-opentrix/opentrix/actions/runs/42245290247)); publication was skipped. These statements originate in [R27-REV-001](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-independent-review-gates-187dbd2.md). Do not infer ownership from inclusion or technical integrity.

## Existing license and inventory evidence (NOT a rights decision)

| Input at frozen candidate | What the material establishes | What remains unresolved |
| --- | --- | --- |
| [Root `LICENSE`](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/LICENSE) | Apache License 2.0 text is present at repository root | Whether each original/adapted portion is actually licensable by its rightsholder |
| [Root `NOTICE`](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/NOTICE) | OpenTrix/TRICORE project attribution is declared and separately refers to third-party notices | Whether `TRICORE contributors` is authorized for each attributed creative portion; actual upstream notice terms |
| [`THIRD-PARTY-NOTICES.md`](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/THIRD-PARTY-NOTICES.md) | Generated CycloneDX dependency names, versions, declared license expressions, some license choices | Generated inventory explicitly disclaims legal certification and replacement of upstream license/NOTICE texts |
| R24 archive-only `compliance/backend.cdx.json`, `frontend.cdx.json`, `portal-bff.cdx.json`, `license-inventory.json` | Four inventory/compliance payload members are recorded in the R24 manifest | Resolve package scope and upstream licensing requirements for what is actually distributed; source-only vs binary boundary |
| [`REUSE.toml`](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/REUSE.toml) | File annotations; original Gradle Wrapper JAR is designated upstream and migration SQL is treated as checksum-sensitive | REUSE lint verifies metadata presence, not copyright authority; Flyway bytes are not to be silently changed |
| [Wrapper properties](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/backend/gradle/wrapper/gradle-wrapper.properties) | `gradle-8.14.3-bin.zip` is the configured distribution URL | Check provenance, notices and actual content/rights of included wrapper scripts/properties; JAR excluded for this exact archive only |
| [`frontend/package.json`](https://github.com/tricore-opentrix/opentrix/blob/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e/frontend/package.json) | Direct frontend dependency declarations include `@fontsource/be-vietnam-pro`, `lucide-react`, React and tooling | Licenses and embedded font/icon/asset obligations in actual frontend package/bundle; do not assume a declaration alone clears fonts/assets |

**Illustrative declared SBOM license situations requiring upstream validation:** `HdrHistogram@2.2.2` lists BSD-2-Clause and CC0-1.0; `jakarta.annotation-api@2.1.1` lists EPL-2.0 and GPL-2.0-with-classpath-exception; `logback-classic@1.5.34` lists EPL-2.0 and LGPL-2.1-only with recorded `EPL-2.0` choice. These are **reported inventory values**, not decisions about actual legal obligations, license compatibility or which texts must ship. Verify upstream notices and actual package versions before making a distribution claim.

## Obligation and provenance work queue

| ID | Affected material / scope | Current claim supported | Unresolved rights, license or NOTICE question | Evidence coordinator → independent review |
| --- | --- | --- | --- | --- |
| LIC-01 | Project-wide original source, docs and archived `NOTICE` | Root `LICENSE` and project `NOTICE` exist | Exact contributor/portion authorization to apply Apache-2.0 and collective `TRICORE contributors` label | Yến → Bảo / Ánh, disjoint from own portions |
| LIC-02 | Third-party dependency manifests and generated `THIRD-PARTY-NOTICES.md` | CycloneDX-derived inventory exists | Validate actual upstream texts, choices, copyright notices, completeness and obligations for **source-only** candidate | Bảo (release evidence) → Yến |
| LIC-03 | `backend/gradle/wrapper/gradle-wrapper.jar` vs included Gradle scripts/properties | One binary JAR excluded in exact source-only archive | Validate upstream origins and notices of **included** wrapper files; JAR exclusion not a grant; future packages separate | Bảo → Yến |
| LIC-04 | Identity G3/G4, Spring Security/OIDC/JWT examples, test fixtures | Yến's scope-limited statement and static technical checks exist | Actual consulted/copied/adapted source/fixtures, license/NOTICE, portion-rightsholder and AI-output ancestry `UNKNOWN/PENDING` | Yến → Ánh |
| LIC-05 | Backend/Core ID-09/11/28/29 | Bảo's AI-assisted work method reported, outside input for ID-28/29 `NOT_RECALLED` | Any adapted JWT/Keycloak/mock/fixture samples, authorizing rights and upstream notice | Bảo → Yến |
| LIC-06 | Data/Storage and mixed `fcc45fb` test portions | Ánh originals vs Bảo later hunks technically distinguished | Each contributor's precise licensable portions and any upstream adapted code/notice | Ánh (originals), Bảo (later hunks) → independent Yến |
| LIC-07 | Frontend/Portal UI, CSS, SVG favicon, fonts/icons | 107 source-ledger selected paths vs 109 handoff paths; asset prompt statement exists | Actual contribution hunks; favicon upstream fragment/template similarity; font/icon licenses and attributions; portal screen-name edits | Ánh → Yến, Bảo for appropriate technical cross-check |
| LIC-08 | Root documentation, imported templates, REUSE annotations, historical SQL | Scope evidence and metadata annotations exist | 37 historical exceptions; copied/adapted docs/code; collective attribution; preserve checksum-pinned Flyway V1–V4 bytes | Yến (coordination) → Bảo / Ánh by independent portion |
| LIC-09 | 10 R24 archive-only metadata items | Presence in exact archive documented | Verify final archived license texts, generated inventories, manifest membership and any upstream notices; do not edit frozen manifest in-place | Bảo (archive) → Yến |
| LIC-10 | Future images/binaries/bundles outside source-only candidate | Explicitly **not** accepted by this assessment | Additional licenses, embedded content, build/runtime distribution duties and R24 artifacts | R24 owner(s) → distinct release reviewer |

## Evidence-to-disposition instructions

1. On each affected source path/portion record immutable Git blob and exact candidate SHA; where mixed contribution exists, identify actual commit/hunk and reviewer. Link the canonical [357-row CSV](https://github.com/tricore-opentrix/opentrix/blob/6cb5a32897563df018cf79dd66b92a20a3e823f1/docs/verification/r27/r27-rights-remediation-register-187dbd2.csv), not a competing source-rights ledger.
2. For any identifiable upstream source, record **real URL, version/hash, affected portion, upstream license, copyright notice, required attribution/NOTICE and evidence of compliance**. If unknown, write `UNKNOWN/PENDING`; never invent negative-source claims or URLs.
3. Link first-person contributor scope-limited statements, not an inferred full-file grant based on Git authorship, SPDX or AI-generated content.
4. Where required clearance is unavailable, record `REMEDIATION_REQUIRED` with rewrite/replacement/permission/exclusion evidence and a new independent review. `EXCLUDED` must be proven against actual archive membership.
5. After correcting obligations, update source-rights register **through separately reviewed work**. A change affecting distributed bytes requires reconciliation with the new frozen SHA, re-created artifacts, and repeated R24 checks.
6. Keep evidence claim-specific: `Document Approved` is not `Evidence PASS` and neither implies `Rights CLEARED`. Preserve `RIGHTS_PENDING` as the existing CSV string; document-level vocabulary uses `PENDING`.

## Step-5 reviewer checklist (NOT completed by writing this Draft)

- [ ] Bảo checks LIC-01/02/03/05/09 against actual archived license inventory, Gradle and release scope; assigns any further concrete upstream NOTICE actions.
- [ ] Ánh checks LIC-04/06/07 and actual frontend/asset contribution boundaries without attesting for Bảo's portions.
- [ ] Yến independently checks Bảo-owned portions; reviewers confirm `UNKNOWN/PENDING` is never mistaken for `CLEARED`.
- [ ] Identify exact LICENSE/NOTICE text gaps with upstream source links and obligations; unresolved cases remain open.
- [ ] Validate this Draft has no source, rights CSV, attribution rewrites, release source SHA or approval changes.
- [ ] Exact-HEAD CI and independent metadata/content review PASS; only then mark **this documentation** approved within scoped review.
- [ ] Reference `R27-LIC-001` from Document Register #146 **by separately reviewed index update**, without clearing any rights.

**Step-5 output:** a reviewable license/NOTICE obligations queue and explicit gaps, not a final distribution clearance. **Issue #138 OPEN; rights PENDING; R24 #139 release BLOCKED.**
