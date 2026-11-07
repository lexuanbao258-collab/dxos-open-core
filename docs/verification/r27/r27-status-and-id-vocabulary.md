<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 — Document IDs and Status Vocabulary (Step 4)

**Document ID:** `R27-REG-DOC-002` (proposed supplemental convention; distinct from `R27-REG-001` source-rights CSV)  
**Document version:** `0.1.0`  
**Document status:** `In Review` (Draft documentation; not policy approval)  
**Evidence verification:** `NOT_VERIFIED` for this convention's implementation across all R27 artifacts; individual cited checks retain their own result  
**Rights disposition:** `PENDING` for unresolved source contributions; this document does not adjudicate source rights  
**Owner (documentation coordination):** Yến (`@phanyen06ym-ien`)  
**Independent reviewers (proposed):** Bảo and Ánh, with no self-approval of underlying contributions  
**Tracking issue:** [R27 #138](https://github.com/tricore-opentrix/opentrix/issues/138)  
**Companion index:** [R27 README — Draft #146 at inspected HEAD `fc9d3bb`](https://github.com/tricore-opentrix/opentrix/blob/fc9d3bb5a293cec4008bc2147e430fb283145cde/docs/verification/r27/README.md)  
**Audited R24 source-only candidate:** `187dbd2ea6935966f5c4cceb95ad9c3c53708a2e`  
**Historical Identity evidence baseline:** `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`

## Purpose and non-goals

This **proposed** vocabulary makes documentation state, technical-evidence findings, and copyright/source-rights dispositions separate, traceable decisions. Step 3 standardized metadata and its reviewed README index; Step 4 defines meanings and gate rules. Adoption requires a distinct independent documentation review. This draft does **not** change any existing register row, contributor statement, SPDX header, archived source or R24 acceptance artifact. No release, tag, merge or rights approval follows automatically.

## 1. Three independent status axes

| Axis and canonical field | Allowed value | Exact meaning | Authority / evidence |
| --- | --- | --- | --- |
| `document_status` | `Planned` | Document is identified but not yet created | Document register; do not link to nonexistent file |
| `document_status` | `Draft` | Initial or incomplete document exists; no final documentation review | Author/coordinator |
| `document_status` | `In Review` | Document submitted for scoped review; findings may remain | PR and exact HEAD |
| `document_status` | `Approved` | Scoped documentation content approved at named SHA by authorized independent reviewer(s) | Exact-HEAD review and applicable checks; **not** source-rights clearance |
| `document_status` | `Superseded` | Prior version replaced, with traceable successor | Link to replacement and original SHA; retain history |
| `evidence_verification` | `NOT_VERIFIED` | No scoped independent verification recorded | No claim of PASS |
| `evidence_verification` | `PARTIAL` | Specific checks passed, but relevant claims or inputs remain unverified | State exactly what passed and what remains unverified |
| `evidence_verification` | `PASS` | **Named** technical claim verified for an exact source/commit/artifact and method | Reviewer, run/log/manifest, scoped claim; not an unrestricted global PASS |
| `evidence_verification` | `FAIL` | Named technical check failed or contradicted by evidence | Failure evidence, owner and corrective action |
| `rights_disposition` | `PENDING` | Insufficient scoped authority/origin/license/NOTICE evidence for a distribution decision | Identified open questions; no assumed clearance |
| `rights_disposition` | `REMEDIATION_REQUIRED` | Review identified a concrete action needed before distribution | Issue, affected file/portion, accountable owner, re-review evidence |
| `rights_disposition` | `CLEARED` | Distribution rights and obligations resolved **for the identified file/portion and specific release scope** | Lawful contributor/upstream authority, applicable license/NOTICE, independent reviewer, source SHA |
| `rights_disposition` | `EXCLUDED` | File/portion deliberately not shipped in the specific distribution | Archive manifest and verification of absence; not a license grant for future packages |
| `rights_disposition` | `NOT_APPLICABLE` | Item has no applicable source-rights decision in the defined distribution scope | Reason and independent review; not a shortcut for unresolved rights |

**Legacy/record compatibility.** Existing source-rights CSV may use `RIGHTS_PENDING` (or other established row-specific values); this document does not bulk-rename those fields. Interpret `RIGHTS_PENDING` as a pending source-rights case when navigating reports, while preserving the CSV's original schema, exact string and per-row provenance. `UNKNOWN` and `NOT_RECALLED` describe missing origin/recollection **facts**; they are not an alternative to a rights decision and must not be silently converted to `NO_EXTERNAL_INPUT` or `CLEARED`.

## 2. Decision gates and state transitions

| Decision | Permitted progression | Required gate | Prohibited inference |
| --- | --- | --- | --- |
| Document | `Planned → Draft → In Review → Approved`; `Approved → Superseded` on documented replacement | Actual file, exact-HEAD reviewer decision, resolved blocking documentation findings, CI/checks where required | Document `Approved` ⇒ source rights `CLEARED` |
| Evidence | `NOT_VERIFIED → PARTIAL / PASS / FAIL`; reassess on changed inputs | Specific question and immutable inputs; reproduce/inspect and cite result | CI PASS ⇒ fixture origin known; code originality established |
| Rights | `PENDING → REMEDIATION_REQUIRED / CLEARED / EXCLUDED / NOT_APPLICABLE`; `REMEDIATION_REQUIRED → CLEARED / EXCLUDED` only after correction and re-review | Scope, person(s) with lawful authority, identified third-party obligations, reviewer independent of relevant contribution, immutable evidence | Git author, AI generation, SPDX, REUSE or documentation review ⇒ licensing authority |
| Release R24 | `BLOCKED → separate authorized acceptance` **only after** all applicable R27 rights gates and R24 checks pass | Final frozen source/archive, rights dispositions, exceptions, notices and approval records | R27 document PASS ⇒ release/tag permission |

Re-open or downgrade a previous `Approved`/`PASS`/`CLEARED` when the relevant document, source blob, upstream obligation or release scope materially changes; retain the prior SHA and decision as history. Never carry a result to a later HEAD silently. `FAIL` can be resolved only with a recorded fix and new verification, not by editing the result label alone. When source inputs change, reconcile all 357 remediation entries against the *new* frozen candidate and repeat affected source/archive verification rather than copying the prior R24 archive PASS.

## 3. IDs, names and ownership boundaries

- Stable **document IDs**: `R27-INV-NNN`, `R27-DEC-NNN`, `R27-REG-NNN`, `R27-EVD-NNN`, `R27-LIC-NNN`, `R27-EXC-NNN`, `R27-REV-NNN`, `R27-VER-NNN`, `R27-RPT-NNN`. The companion README already uses `R27-REG-DOC-001` for its index to avoid collision with `R27-REG-001` (rights register); this supplemental vocabulary uses `R27-REG-DOC-002`. Do not mint duplicate IDs or reassign an ID to another artifact.
- **Source-level IDs** such as `ID-01`–`ID-37`, `ID-D01`, `ID-D02`, `G1`–`G6`, 347 tracked Git paths and the 357 source-rights remediation entries are *not* document IDs. Preserve each existing identifier, row key, path and blob. A single document can reference multiple source rows; one source row can have multiple actual contributing portions.
- Document owner/coordinator is responsible for collecting evidence, **not presumed the creator or legal copyright owner**. Contributor declarations must be first-person or directly attributable; a compiled evidence checkpoint is not a signed declaration.
- Reviewer for a source-rights decision must be independent of the **specific portion** whose rights are being assessed. When Ánh and Bảo both changed a Data test, route their respective portions independently; do not grant either person blanket authority over the whole file.
- New document naming recommendation: `r27-<type>-<subject>.md`. Keep existing paths unchanged. Maintain immutable commit/PR links and distinguish current Draft PR HEAD from historical source baseline and frozen R24 candidate.

## 4. Minimum evidence for a source-rights decision

For each applicable distributed source file/portion, record: immutable source path, blob SHA and candidate baseline; actual content/metadata commit or hunk if mixed; contributor(s) and direct scope-limited statements; known third-party URLs or **explicit UNKNOWN** if not established; applicable license, attribution and NOTICE obligations or verified absence *within the scoped claim*; reviewer and decision timestamp; disposition; links to remediation and archive membership.

A `CLEARED` outcome is **never derived** from an AI prompt, an SPDX label, a green CI run, a filename, PR merge or a contributor's general project role. `EXCLUDED` must be matched to actual distributed bytes. Historical R25's 37 exception paths require explicit disposition or documented irrelevance to the final distribution, not automatic clearance.

## 5. Concrete examples (illustrative; not new decisions)

| Source / artifact | Document status | Technical evidence status | Source rights | Why |
| --- | --- | --- | --- | --- |
| R27 README PR #146 at `fc9d3bb` | Documentation review APPROVED at this exact HEAD; PR remains Draft | CI 5/5 PASS for the named commit | `PENDING` for unresolved underlying source rights | [Bảo review](https://github.com/tricore-opentrix/opentrix/pull/146#pullrequestreview-5811310182); [Ánh review](https://github.com/tricore-opentrix/opentrix/pull/146#pullrequestreview-5811314353); approval covers index only |
| Identity G3/G4 fixtures in #142 | `In Review` | `PARTIAL` origin evidence | `PENDING` | Test/helper observations exist, but historical outside sample/fixture lineage and licensing/NOTICE questions remain open |
| Backend ID-28/ID-29 | `In Review` evidence workstream | `PARTIAL` method evidence | `PENDING` | Bảo reported primarily AI-assisted work; outside inputs not recalled, not known to be absent |
| Gradle Wrapper JAR excluded from R24 source-only archive | N/A — source item, not document | Scope-specific absence check PASS | `EXCLUDED` **for this one archive only** | Exclusion does not clear Gradle text-wrapper obligations |
| New `R27-LIC-001` / `R27-RPT-001` | `Planned` until created | `NOT_VERIFIED` | `PENDING` | Do not mark delivered or approved before evidence and review |

## 6. Step-4 documentation acceptance checklist

- [ ] Bảo and Ánh independently review this *proposed vocabulary* at its current Draft PR HEAD; owner does not self-approve.
- [ ] Confirm all three enums are exclusive by **field** and have the exact meanings and transitions above; no `PASS`/`Approved`/`CLEARED` conflation.
- [ ] Confirm stable document IDs and non-collision with source IDs, review group codes and existing remediation CSV schema/values.
- [ ] Verify references to #138, #139, #142, #143, #144 and #146 are scoped; no nonexistent artifact is presented as completed.
- [ ] Confirm 357-entry CSV, evidence statements, release inputs and previous approvals are **unchanged** by this Draft PR.
- [ ] Exact-HEAD CI and repo-hygiene/REUSE checks PASS; document approvals record this specific SHA.
- [ ] Once reviewed, add `R27-REG-DOC-002` to the index in a **separate**, reviewed update, without rewriting underlying rights dispositions.

**Expected Step-4 result:** reviewed, versioned status/ID policy for R27 documentation. **Not** R27 rights clearance, not a document that adjudicates any author's rights, and not R24 release authorization. Keep [#138](https://github.com/tricore-opentrix/opentrix/issues/138) OPEN and R24 #139 BLOCKED until their own source-rights and release gates are fulfilled.
