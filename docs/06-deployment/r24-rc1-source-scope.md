<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 v0.2.0-rc.1 — source-only scope inventory (evidence, not clearance)

**Provisional checkout:** [main at e03fb1aeeafa59dffa18a30a4108d6a67f2c8008](https://github.com/tricore-opentrix/dxos-open-core/tree/e03fb1aeeafa59dffa18a30a4108d6a67f2c8008). GitHub tree API returned a complete, untruncated tree. Replace this inventory with the **final tagged source SHA** if source code, CHANGELOG date or R24 packaging changes.

## Recorded tracked tree

| Item | Observation |
|---|---|
| Git-tracked regular blobs | 346 |
| Git file modes | 343 mode 100644, 3 mode 100755 |
| Git symlinks | 0 |
| Tracked binary excluded by source-only allowlist | `backend/gradle/wrapper/gradle-wrapper.jar` (43,764 bytes) |
| Potential source files after filename exclusion | 345 (subject to content/UTF-8 checks) |
| Tracked environment template | `.env.example`; this is **not** a credentials scan |
| Source-only generated metadata | Three CycloneDX runtime SBOMs, R23 license inventory, LICENSE/NOTICE, CHANGELOG, release notes, SHA256SUMS and MANIFEST |

The `scripts/r24-release.py` source-only packager reads Git-tracked paths, rejects symlinks, excludes listed compiled/binary extensions and rejects NUL/non-UTF-8 data; it embeds tracked text under `source/` and additionally packages the named compliance files. This is a **code contract**, not a statement that a protected release has run on the final source SHA.

**Source-only is not binary/GHCR clearance.** It does not ship JARs, built frontend `dist/` or GHCR images. It still ships text assets including `frontend/public/favicon.svg` and project source; attribution and rights must be reviewed.

## Rights and provenance items requiring actual contributor evidence

- **Bảo / Backend/Core:** file/group origin and Apache-2.0 authorization, especially `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java`; explicitly classify original, adapted, third-party, AI-assisted and generated material. Keep Gradle wrapper as upstream third-party and avoid claiming ownership.
- **Yến / Identity and overall coordination:** independent disposition for the 14 identified Identity Java files and the identity contract, and contributor/rightsholder information for relevant source.
- **Ánh / Frontend, Portal, Data/Storage:** direct author/rightsholder licensing authorization; source/asset provenance including `frontend/public/favicon.svg`, package metadata and any copied/adapted works.
- **All:** confirm root Apache-2.0 LICENSE/NOTICE correctness, protected SQL/workflow sources, third-party attribution and any unresolved exception with evidence under [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138). Git authorship and REUSE lint are not rights proof.

## Exact-archive evidence still needed

The separate [R24 reproducibility fix PR #140](https://github.com/tricore-opentrix/dxos-open-core/pull/140) must be independently reviewed/merged first: generated CycloneDX BOMs currently include run-specific timestamps, random UUIDs and CI run URLs. Without normalization, a tag job rebuilding the archive can fail the independently reviewed archive SHA-256 gate.

After freezing the final source and CHANGELOG date, run R24 `workflow_dispatch` on that exact `main` SHA. Verify source-only `MANIFEST.json` tag, mode and SHA; actual tar members, the source/SBOM boundary and the `SHA256SUMS` hash. The candidate from **PR #139's own HEAD is not valid for a tag pointing to main**. Record an exact run and artifact link in the acceptance JSON only after a human checks the evidence. No approval is inferred from this report.
