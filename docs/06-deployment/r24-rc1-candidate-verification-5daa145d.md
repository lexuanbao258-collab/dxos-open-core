<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 source-only RC — independently checked candidate baseline (NOT release acceptance)

**Verified 2026-11-05.** This is a reproducibility/integrity observation for the tested candidate baseline, **not** a license/rightsholder approval and **not** approval to push a tag. Do not copy this digest to final acceptance if the tagged source SHA differs.

| Field | Verified value |
| --- | --- |
| Candidate tag | `v0.2.0-rc.1` |
| Tested source SHA | `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33` |
| Release mode | `source-only` |
| Exact archive filename | `dxos-open-core-0.2.0-rc.1.tar.gz` |
| Exact archive SHA-256 | `5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c` |
| Workflow A | [GitHub Actions run 42062136136](https://github.com/tricore-opentrix/dxos-open-core/actions/runs/42062136136) — workflow_dispatch, candidate SUCCESS, publish SKIPPED |
| Source-only artifact A | `r24-source-only-candidate-42062136136` (artifact ID `13431893481`) |
| Workflow B | [GitHub Actions run 42062655485](https://github.com/tricore-opentrix/dxos-open-core/actions/runs/42062655485) — workflow_dispatch, candidate SUCCESS, publish SKIPPED |
| Source-only artifact B | `r24-source-only-candidate-42062655485` (artifact ID `13430934134`) |

## Independent archive inspection

The GitHub connector downloaded both **separate source-only artifact ZIPs**, without relying only on operator-reported hashes. Each ZIP contains `SHA256SUMS`, `RELEASE-NOTES.md` and the release tarball. The tarball bytes were independently hashed with SHA-256.

For **both** archives:

- Calculated `sha256(archive)` equals the exact digest above.
- `SHA256SUMS` records that same digest and archive name.
- `MANIFEST.json`: `tag=v0.2.0-rc.1`, `mode=source-only`, `source_sha=5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`.
- **345** member paths under `source/`; **355** total tar members.
- All **354** manifest file SHA-256 entries match actual extracted member bytes (MANIFEST itself is excluded).
- The standalone `RELEASE-NOTES.md` bytes equal the copy inside the tarball.
- No executable Backend/BFF application JARs, frontend `dist/` files or classes occur under the examined archive members.
- `source/backend/gradle/wrapper/gradle-wrapper.jar` is absent; `source/frontend/public/favicon.svg` is present.
- The ten non-source tar members are: `CHANGELOG.md`, `LICENSE`, `MANIFEST.json`, `NOTICE`, `RELEASE-NOTES.md`, `THIRD-PARTY-NOTICES.md`, `compliance/backend.cdx.json`, `compliance/frontend.cdx.json`, `compliance/license-inventory.json`, and `compliance/portal-bff.cdx.json`.

The two candidate archive SHA-256 values are identical. This independently corroborates Yến's [candidate verification comment](https://github.com/tricore-opentrix/dxos-open-core/pull/139#issuecomment-6663218439).

## Exact reproduction commands

After downloading and extracting the **source-only artifact ZIP** in a separate directory for each run:

```bash
sha256sum -c SHA256SUMS
sha256sum dxos-open-core-0.2.0-rc.1.tar.gz
tar -xOf dxos-open-core-0.2.0-rc.1.tar.gz dxos-v0.2.0-rc.1/MANIFEST.json
```

Both artifact ZIPs were independently parsed; member SHA-256 values were computed from tar member bytes and compared with the embedded `MANIFEST.json`.

## Scope and permission exclusions

This candidate's source-only boundary includes original source, text assets and compliance notices/BOMs. **Source-only does not mean the source can automatically be redistributed.** Files and rights requiring direct contributor/rightsholder declarations remain in [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138). A passing REUSE lint or Git commit history does not establish those rights. In particular `frontend/public/favicon.svg` is in the archive, and requires its own asset/source clearance.

**Release-source freeze caveat:** The current release plan has [Draft PR #141](https://github.com/tricore-opentrix/dxos-open-core/pull/141) proposing a CHANGELOG date change, plus an R26 repository rename. A name-only GitHub rename does not necessarily change Git commit SHA; merging any source change **does**. Before completing [Draft acceptance PR #139](https://github.com/tricore-opentrix/dxos-open-core/pull/139), independently confirm the final tagged source SHA, actual release date, released sources, and whether this digest remains valid. If the source SHA changes, re-run the same two-candidate checks at that SHA and replace the evidence. Do not authorize publication based solely on this evidence.
