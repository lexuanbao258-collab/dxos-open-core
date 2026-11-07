<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->



**Document ID:** `R27-EVD-005`  
**Document version:** `0.1.0` (metadata baseline; existing evidence retained)  
**Document status:** `In Review` (Draft PR #143)  
**Evidence verification:** `PARTIAL` — only explicitly described technical checks may be treated as PASS  
**Rights disposition:** `PENDING` — no file/portion clearance or public release approval  
**Evidence owner:** Bảo — evidence routing; not a signatory for others  
**Independent reviewer:** Yến / Ánh — independent documentation review  
**Tracking issue:** [#138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Document PR:** [#143](https://github.com/tricore-opentrix/opentrix/pull/143)  
**R24 source-only candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Metadata boundary:** these labels route this document only; original claims, reviewer decisions and source-rights obligations are unchanged.

# R27 — contributor-rights source statements and routing (RECONSTRUCTED; NOT SIGNED)

**Scope and evidence baseline.** This is a new documentation-only reconstruction, **not the four lost uncommitted originals**, not a release authorization, and not a first-person attestation created on a contributor's behalf. Use the exact source-only Git baseline [`187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`](https://github.com/tricore-opentrix/opentrix/tree/187dbd2ea6935966f5c4cceb95ad9c3c53708a2e), two independently verified R24 candidate archives ([run 42244683510](https://github.com/tricore-opentrix/opentrix/actions/runs/42244683510), [run 42245290247](https://github.com/tricore-opentrix/opentrix/actions/runs/42245290247)) and the [reconstructed register](r27-rights-remediation-register-187dbd2.csv). The source archive has 346 tracked text/source files plus 10 metadata members; the original Git tree has 347 tracked paths because one upstream Gradle Wrapper JAR was **excluded**.

> **A Git commit, CODEOWNERS assignment, review responsibility or SPDX header is not proof of copyright ownership.** Every register row names a person responsible for *obtaining evidence*, not the alleged rights holder. The independent reviewer is distinct from that evidence coordinator. A contributor can authorize Apache-2.0 only for the creative portions for which that person has legal authority, subject to upstream rights and required notices.

## Existing contributor statements — preserved, not invented

| Contributor / evidence | Supported existing statement | Explicit boundary / still unknown |
| --- | --- | --- |
| **Bảo**, [Backend/Core general disclosure](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663619928) | Nearly all code in his contribution/coordination scope was AI-created/edited using ChatGPT/Claude and similar tools, based primarily on his requirements. He does **not** claim he manually authored all code. | General workflow is not a file-by-file originality, ownership or no-third-party statement. His general scope-limited Apache-2.0 direction is **not** a whole-repository grant. |
| **Bảo**, [limited general outside-input report](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6664569365) | Usually provided requirements; did not proactively provide an outside repository for the typical AI workflow. | Not a claim that no sample, tutorial, external snippet or model-derived third-party output was ever involved. |
| **Bảo**, [ID-09 and ID-11](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6677124150) | Two Identity package-info.java source additions were primarily created/edited with ChatGPT/Claude from his requirements. | External adaptation, attribution/NOTICE, portion-level rights pending. Yến-associated later SPDX changes are **metadata**, not Bảo's substantive content. |
| **Bảo**, [ID-28 and ID-29](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6689684471), [later evidence](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702663219) | Both Gateway tests **“chủ yếu AI”** (primarily AI-assisted). Asked if he supplied outside GitHub/tutorial/JWT/Keycloak examples or fixture inputs, Bảo replied **“hmm không nhớ rõ”**. | For **these two files**, outside material supplied to AI = **UNKNOWN / NOT RECALLED**, not NO. Exact JWT/Keycloak fixture lineage, any upstream snippet, notices and license authority remain pending. |
| **Bảo and Ánh**, [exact fcc45fb diff](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663889012), [Ánh declaration](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663564250) | Ánh contributed the initial Data/Storage test files; Bảo-associated `fcc45fb` later changes only the cited content hunks in two tests. | Never claim Bảo owns the entire file or Ánh owns his later changed lines. Confirm contribution and license **per portion** with both individuals. |
| **Yến**, [Identity declaration in Draft #142](https://github.com/tricore-opentrix/opentrix/pull/142), [recorded origin statement](https://github.com/tricore-opentrix/opentrix/pull/142#issuecomment-6677591954), [scope-limited consent](https://github.com/tricore-opentrix/opentrix/pull/142#issuecomment-6689982586) | Reported design/requirements with ChatGPT and Codex help, followed by review/modification/testing. Consent recorded **only for portions she actually has lawful authority to license**. | G3/G4 external Spring Security/OIDC samples, fixture sources, per-file contributor attribution, NOTICE and rights remain `UNKNOWN/PENDING`; her declaration does not cover Bảo's four files or other authors. |
| **Ánh**, [Data/Storage, Portal and favicon statement](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6663564250), [107-path cross-check](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702703243) | Described requirements and used AI without supplying external code/templates as inputs. She described and prompted ChatGPT to help create the SVG favicon; confirmed a limited Apache-2.0 authorization **only to the extent she holds rights**. | Historical Portal screen-name exact file/portion mapping unknown; whether generated SVG reused an upstream fragment/template and notice obligations unknown; her 107-path Git check does **not** establish copyright of every file. |

## Explicit ID-09/11/28/29 file crosswalk

| ID | Source path | Git blob SHA-1 | Required human follow-up | Evidence owner / reviewer |
| --- | --- | --- | --- | --- |
| ID-09 | `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java` | `c57d2207a4eade58e2bf0b03bf6d859be74c7724` | Bảo: any known external example, exact portion rights, NOTICE. | Bảo / Yến |
| ID-11 | `backend/src/main/java/com/tricore/dxos/infrastructure/identity/package-info.java` | `a8901597f86a88822666b11b538285b3327e1397` | Bảo: same per-file disclosure, do not reuse an unsupported no-outside-code claim. | Bảo / Yến |
| ID-28 | `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java` | `70dad237cdb0569ac5c57d239829275c76b24b66` | Bảo: code/example provenance, mock JwtDecoder pattern, synthetic JWT/test-claims fixture lineage, rights/NOTICE. External inputs **NOT RECALLED**. | Bảo / Yến |
| ID-29 | `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java` | `15f11e73b104e34165c9fcfb5738436131a5e07e` | Bảo: optional Keycloak runtime-token test setup and any sample adaptations, source links, rights/NOTICE. No real access token is included in the tracked file. | Bảo / Yến |

**Technical limits:** ID-28 uses a mocked `JwtDecoder`; `signed-read-token`, `issuer.example.test`, and `user-123` are test literals, not proof of a real signed JWT. ID-29 loads `OIDC_TEST_ACCESS_TOKEN` from an environment variable when its optional integration setup is provided; this does **not** verify a real Keycloak test run. Source static inspection cannot certify no adapted external sample.

## Ownership-safe routing matrix — people own the evidence task, not necessarily the code

| File/group area | Evidence coordinator | Independent reviewer | Concrete outstanding question |
| --- | --- | --- | --- |
| Core/Gateway/workflow, 166 Backend **distributed** files (not 166 Bảo-authored) | Bảo | Yến | Map substantive Git hunks to actual authors/rights, external examples, generated outputs, third-party notices; reconcile with [Backend register](https://github.com/tricore-opentrix/opentrix/blob/e0a991ee3c6aae09aa09a28aaf8dec2cc91bcb85/docs/06-deployment/r27-backend-core-origin-register-5daa145d.md). |
| Identity G1–G6 and legacy exceptions | Yến | Ánh | Separate Bảo's 4 files, Yến's implementation vs metadata-only commits, and the 24 G3/G4 per-file sample/fixture decisions; preserve [Draft #142](https://github.com/tricore-opentrix/opentrix/pull/142). |
| ID-09/11/28/29 | Bảo | Yến | File-specific source origin and rights; do not convert `UNKNOWN` into none or invent broad grants. |
| Data/Storage originals | Ánh | Bảo | Confirm Ánh content/AI scope and any upstream inputs; license portions she can authorize. |
| `fcc45fb` changes in `backend/src/test/java/com/tricore/dxos/core/data/InMemoryObjectStorage.java` and `backend/src/test/java/com/tricore/dxos/core/data/ObjectStoragePortTest.java` | Bảo for his two diffs; Ánh for originals | Yến | Verify exact changed hunks and rights **separately** (two contributors). |
| Frontend/Portal and SVG favicon | Ánh | Yến | Reconcile actual changed Portal screens and favicon source/fragment reuse, third-party NOTICE and authorized portions. |
| Portal BFF and mixed app code | Ánh (mapping) and Bảo (cross-coordination) | Yến | Identify real authors before attributing rights based on CODEOWNERS or responsibility. |
| Gradle scripts/properties **included**; upstream JAR **excluded** | Bảo (technical upstream evidence) | Yến | Preserve original Gradle contributor copyright headers, check Apache-2.0/upstream NOTICE and excluded JAR SHA/origin separately. |
| Root docs/config, repository governance, R24 tooling and generated metadata | Yến (evidence coordination) | Bảo | Map actual contributors and referenced external templates, authorization of `TRICORE contributors` label, 37 historical exceptions and third-party notices. |

**Ánh's detailed routing handoff:** The [109-path handoff](https://github.com/tricore-opentrix/opentrix/issues/138#issuecomment-6702734422) at `187dbd2` covers 107 code/config/asset paths plus two current Data documents, including the nine-contract/two-test mapping, Bảo's separate `fcc45fb` hunks and package boundaries, and exclusion of the two deleted historical Data documents. It records technical mapping, not source-rights clearance; unresolved contribution mapping, external origins, rights and NOTICE remain `UNKNOWN/PENDING`.

## Disposition process and non-invention rule

1. Contributor confirms only **what they actually remember**, with exact affected path/hunk, optional URL and original license if known. Unknown source or model output lineage stays `UNKNOWN`; provide neither fabricated links nor `NONE` without evidence.
2. Independent reviewer records what was **technically inspected**, which human declaration exists, relevant license or upstream notice, whether remediation/replacement/exclusion is needed, and an actual acceptance verdict.
3. If a portion cannot be adequately licensed/distributed, it must be rewritten, excluded or separately authorized in a reviewed change; any source SHA change invalidates the two prior R24 candidate runs.
4. If label/notice statements require change, do so via a separate reviewed documentation/metadata PR; do not mutate checksum-pinned Flyway V1–V4 or source files here.
5. Until **Issue #138** is resolved and **Draft PR #139** receives independent release acceptance, there is **NO-GO** for tag/release/public redistribution.

See [independent review and validation](r27-independent-review-gates-187dbd2.md) for reproducibility and remaining validation steps.
