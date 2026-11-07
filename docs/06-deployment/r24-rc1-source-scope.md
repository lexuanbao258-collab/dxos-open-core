<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 v0.2.0-rc.1 — source-only scope inventory (evidence, not clearance)

**Independently inspected candidate baseline:** [main at 5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33](https://github.com/tricore-opentrix/dxos-open-core/tree/5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33). GitHub tree API returned a complete, untruncated tree; both manually triggered Actions source-only archives on this same SHA were independently downloaded and checked. This is **not necessarily the final tagged source SHA** if R26/CHANGELOG source commits merge later.

## Recorded tracked tree

| Item | Observation |
|---|---|
| Git-tracked regular blobs | 346 |
| Git file modes | 343 mode 100644, 3 mode 100755 |
| Git symlinks | 0 |
| Tracked binary excluded by source-only allowlist | `backend/gradle/wrapper/gradle-wrapper.jar` (43,764 bytes) |
| Source files actually included in tested tar | **345** under `source/` (confirmed from extracted archive) |
| Tracked environment template | `.env.example`; this is **not** a credentials scan |
| Source-only generated metadata | Three CycloneDX runtime SBOMs, R23 license inventory, LICENSE/NOTICE, CHANGELOG, release notes, SHA256SUMS and MANIFEST |

The `scripts/r24-release.py` source-only packager reads Git-tracked paths, rejects symlinks, excludes listed compiled/binary extensions and rejects NUL/non-UTF-8 data; it embeds tracked text under `source/` and additionally packages the named compliance files. This is both a packaging contract and a **tested-candidate observation** on `5daa145d`. There has been no protected release run and no final distribution acceptance.

**Source-only is not binary/GHCR clearance.** It does not ship JARs, built frontend `dist/` or GHCR images. It still ships text assets including `frontend/public/favicon.svg` and project source; attribution and rights must be reviewed.

## Rights and provenance items requiring actual contributor evidence

- **Bảo / Backend/Core:** file/group origin and Apache-2.0 authorization, especially `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java`; explicitly classify original, adapted, third-party, AI-assisted and generated material. Keep Gradle wrapper as upstream third-party and avoid claiming ownership.
- **Yến / Identity and overall coordination:** independent disposition for the 14 identified Identity Java files and the identity contract, and contributor/rightsholder information for relevant source.
- **Ánh / Frontend, Portal, Data/Storage:** direct author/rightsholder licensing authorization; source/asset provenance including `frontend/public/favicon.svg`, package metadata and any copied/adapted works.
- **All:** confirm root Apache-2.0 LICENSE/NOTICE correctness, protected SQL/workflow sources, third-party attribution and any unresolved exception with evidence under [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138). Git authorship and REUSE lint are not rights proof.

## Tested artifact integrity — verified baseline only

Yến's two manual runs [#42062136136](https://github.com/tricore-opentrix/dxos-open-core/actions/runs/42062136136) and [#42062655485](https://github.com/tricore-opentrix/dxos-open-core/actions/runs/42062655485) succeeded; both independently downloaded source-only tarballs have exact SHA-256:

```text
5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c
```

Each archive has **355 total members**, including 345 tracked source text entries, three CycloneDX BOMs, R23 license inventory, LICENSE/NOTICE/THIRD-PARTY-NOTICES, CHANGELOG, MANIFEST and release notes. `SHA256SUMS` matches the actual tar SHA-256; the manifest has correct tag, mode and source SHA, and all 354 member hashes match their extracted bytes. `RELEASE-NOTES.md` matches byte-for-byte inside/outside the archive. The Gradle wrapper executable JAR and application JARs/frontend build output are absent; `frontend/public/favicon.svg` **is present**.

Full archive-file and digest verification details: [`r24-rc1-candidate-verification-5daa145d.md`](r24-rc1-candidate-verification-5daa145d.md).

## Still pending for final acceptance

1. Confirm final tag date and source SHA after R26 repository rename and any merged CHANGELOG/source changes. A GitHub repository rename alone does not mutate source commit bytes; merging a source change does. Re-run both source-only candidates if the tagged source commit changes.
2. Complete independently reviewed, first-person source/rightsholder licensing and exceptions under [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138), **including favicon asset rights**. No source-only archive is automatically distribution-cleared.
3. Only after final-sha checksum verification and genuine rights evidence are recorded should the acceptance JSON in [Draft PR #139](https://github.com/tricore-opentrix/dxos-open-core/pull/139) be completed and independently approved. Do not use PR #139's own HEAD candidate as tagged source or bypass independent approval.
