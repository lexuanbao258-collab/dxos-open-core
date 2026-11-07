<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 source-only RC — Backend/Core provenance observations (NOT rights clearance)

**Evidence baseline:** `main@5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33` on 2026-11-05. These observations are based on GitHub REST commit history and file content, and are **not** independent ownership, originality or redistribution-permission findings. Any later R26 source update or tag source change needs revalidation.

## Focused `core/identity/package-info.java`

Current path: `backend/src/main/java/com/tricore/dxos/core/identity/package-info.java`

Current Git blob: `c57d2207a4eade58e2bf0b03bf6d859be74c7724`.

The file contains a 2026 `TRICORE contributors` SPDX copyright label, Apache-2.0 SPDX identifier, Java package declaration and one short provider-independent identity contract description. Those labels do **not** establish individual rights or source originality.

GitHub `commits?path=...` returned:

| Commit | GitHub-associated account | Observed scope |
| --- | --- | --- |
| [`d53a273f63fa07af424825f8775b030d94344fd5`](https://github.com/tricore-opentrix/dxos-open-core/commit/d53a273f63fa07af424825f8775b030d94344fd5) | `lexuanbao258-collab` | `chore: add core package boundaries` (2026-10-07) |
| [`fa16f30d5c67a49186b1c36f2babe58d54a15e38`](https://github.com/tricore-opentrix/dxos-open-core/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38) | `phanyen06ym-ien` | `chore(license): enforce repository-wide SPDX and REUSE (#127)` (2026-11-05) |

These are **commit associations, not an authorship/legal-rights proof**. Earlier source-origin review in [Issue #38](https://github.com/tricore-opentrix/dxos-open-core/issues/38#issuecomment-6360185136) also specifically requested independent contributor confirmation for this file.

## Other Backend/Core observed history

- `backend/src/main/java/com/tricore/dxos/core/identity/` has matching GitHub-associated commits `d53a273`, `a60c96a`, `3e06d69`, `d87a26b` and `fa16f30`; Identity source was predominantly committed by Yến, while the original `package-info.java` layout was committed under Bảo's account. See [prior Identity source inspection/declaration](https://github.com/tricore-opentrix/dxos-open-core/issues/38#issuecomment-6360325882).
- `backend/src/main/java/com/tricore/dxos/application/` history includes both GitHub-associated accounts, so ownership cannot be inferred solely from a directory name.
- `backend/gradle/wrapper/gradle-wrapper.jar` entered the repository in [`30597dab62b0602fd59e3617c0701f7b0d059c7b`](https://github.com/tricore-opentrix/dxos-open-core/commit/30597dab62b0602fd59e3617c0701f7b0d059c7b), with GitHub-associated account `lexuanbao258-collab`. This wrapper is upstream Gradle material, **not TRICORE-original**; the source-only packaging contract excludes the JAR itself. Other relevant third-party files/dependencies still need review.
- [Issue #38 owner decision](https://github.com/tricore-opentrix/dxos-open-core/issues/38#issuecomment-6337562995) records an intended Apache-2.0 policy for original materials **where contributors possess and grant rights**. It is explicitly not all-file clearance.

## Required direct contributor confirmations (UNVERIFIED)

**Bảo (@lexuanbao258-collab)** needs to personally attest, with scoped filenames/file groups:
1. Whether `core/identity/package-info.java` and Backend/Core items under his contribution scope were created originally, adapted/copied, AI-assisted or generated, and with what external sources.
2. Whether he possesses rights to authorize the Apache-2.0 license for each original contribution, and any attributions/exceptions.
3. Whether any third-party material requires NOTICE/permission/alternate licensing, excluding upstream Gradle from claims of original authorship.

**Yến** must verify Identity ownership and uncertainties from her earlier 15-file declaration. **Ánh** must provide an independent direct rights/source statement for her contributions. Maintain exceptions and actual rightsholder authority in [Issue #138](https://github.com/tricore-opentrix/dxos-open-core/issues/138).

## Release gate

This document may be linked as **Git provenance/source-scope evidence only**. It is **insufficient by itself** for `source_provenance` or `source_license` in the reviewed R24 acceptance JSON. Before requesting approval, add the contributors' genuine attestations, independently reviewed origin register and any required NOTICE/exception disposition, plus archive scope evidence from the actual frozen source candidate. No one has signed an authorization by the creation of this document.
