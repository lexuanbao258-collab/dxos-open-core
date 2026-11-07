<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 — internal RC 06/11/2026 evidence register (NOT RELEASE-ACCEPTED)

**Classification: INTERNAL / NOT RELEASE-ACCEPTED.** Only SOURCE-ONLY `v0.2.0-rc.1` candidate inspection and controlled TRICORE handoff. **No public RC tag, GitHub Release, JAR, frontend dist or GHCR**.

## Historical candidate evidence (built 05/11, NOT fresh 06/11 evidence)

- Source SHA `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`.
- Two 2026-11-05 manual runs: [#42062136136](https://github.com/tricore-opentrix/opentrix/actions/runs/42062136136) and [#42062655485](https://github.com/tricore-opentrix/opentrix/actions/runs/42062655485). Their earlier independent record reports candidate PASS and publish SKIPPED, identical `v0.2.0-rc.1` SOURCE-ONLY archive digest `5021b52c44e2291943a4d472945c4121f6875ef0376311f7001ada06242f1b9c`, and successful SHA256SUMS/MANIFEST checks. See [historical evidence](r24-rc1-candidate-verification-5daa145d.md).
- These builds are from **05/11**, not proof of a new build, controlled sharing or reviewer decision on **06/11**. Their SHA/tag/checksum is **NOT** an acceptance record for public `v0.2.0`, and #136/#141 have changed the source/release baseline.

## Internal evaluation record (06/11, no invented evidence)

| Required item | Status |
| --- | --- |
| Actual RC source SHA + run/artifact identifier selected for internal review | **PENDING** — historical examples above are not presumed selected |
| Actual SHA256SUMS/MANIFEST validation on the selected archive | **PENDING** for the 06/11 review, historical 05/11 checks separately documented |
| Names/time of internal reviewers and reviewed scope | **PENDING** |
| Approved, controlled internal handoff and recipient confirmation | **PENDING** |
| Internal disposition | **INTERNAL / NOT RELEASE-ACCEPTED**. No publication authorization. |

No private artifact secrets or access tokens should appear in public GitHub evidence. If sharing is through a private channel, log only a sanitized reference permissible for this repository. Missing data must stay PENDING.

## Official release: independent track

**07/11/2026 is a target, not approval:** `v0.2.0` SOURCE-ONLY only; #141 source merge and freeze must come first; run TWO `v0.2.0` source-only candidates on the final exact source SHA, independently verify archive SHA-256, SHA256SUMS, MANIFEST tag/mode/sha and notes; complete [#138](https://github.com/tricore-opentrix/opentrix/issues/138) source rights and independently approve/merge [#139](https://github.com/tricore-opentrix/opentrix/pull/139) with separate `v0.2.0.json`. If any gate is incomplete, postpone and update CHANGELOG before final source freeze. Tag `v0.2.0` must point to the verified earlier source commit, **not** the later #139 acceptance merge SHA.

**No public tag, GitHub Release or GHCR publication is authorized by this file.**
