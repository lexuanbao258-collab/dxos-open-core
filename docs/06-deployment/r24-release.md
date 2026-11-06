<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 — reproducible release automation and independent clearance

Automation owner: Bảo (@lexuanbao258-collab). Compliance/release acceptance coordinator: Yến (@phanyen06ym-ien), with Ánh (@ngang02012007-crypto) as co-reviewer. R23 policy PASS is not permission to distribute.

## Candidate and naming

- Output: **dxos-open-core-<version>.tar.gz**, e.g. dxos-open-core-0.2.0.tar.gz, plus SHA256SUMS and RELEASE-NOTES.md.
- Accept vMAJOR.MINOR.PATCH and vMAJOR.MINOR.PATCH-rc.N (N >= 1) tags. A matching CHANGELOG [<version>] section with nonempty release notes is required.
- PR/manual workflow runs build nonpublishable candidates for `v0.2.0`; binary CI validation artifacts are NOT distribution authorization. Publish mode is selected exclusively from the independently accepted manifest on main. The tag workflow selects release scope from the independently reviewed acceptance JSON on main; tagged commit must be an ancestor of main. **No tag is created by PR #134.**
- Archive metadata are reproducible. SHA256SUMS and MANIFEST.json cover candidate bytes. The clearance gate additionally requires independently reviewed archive_sha256 in the acceptance JSON and verifies standalone release notes match the archive member. Publication extracts the verified archive notes.

## Binary scope

Binary packages contain backend and Portal BFF bootJars, frontend dist, three CycloneDX runtime SBOMs, R23 inventory, original LICENSE/NOTICE, CHANGELOG, notes, upstream-index.json, and actual extracted upstream texts. GHCR images may be published only after the binary scope is accepted.

The upstream collector reads matched Gradle-cache JAR META-INF LICENSE/NOTICE and npm package LICENSE/COPYING/NOTICE, with optional reviewed supplements under docs/06-deployment/r24-upstream/<project>/<name>-<version>/. File contents are preserved, hashed and listed in upstream-index.json. Found files are COLLECTED_UNREVIEWED, missing texts remain MISSING. Maven type=pom entries are metadata-only, not shipped JARs; package.json license identifiers are not substitutes for upstream text.

One independently accepted **permissive license group** may cover Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC and 0BSD. Actual bundled components still require upstream license bytes in the archive. Any non-group license (e.g. EPL, GPL+Classpath, OFL and CC0) needs individual evidence/choice in the exceptions list. The inventory determines those exceptions, including logback and jakarta.annotation-api where shipped; no blanket permission is inferred from the license name. The gate blocks missing/unchecked files and all unresolved source, NOTICE, asset, JRE and OCI obligations.

## Source-only `v0.2.0` (planned; fail-closed)

Mode source-only archives Git-tracked source, LICENSE, NOTICE, CHANGELOG, release notes, three runtime SBOMs and notices inventory. It **excludes JARs, frontend dist and GHCR images**. The SBOM records resolved build/runtime dependencies, not distributed binaries. An independent source provenance/licensing review is still required; source-only clearance does not satisfy later binary release obligations. The 2026-11-06 target is conditional: the first public `v0.2.0` release, if independently cleared, is SOURCE-ONLY. Do not publish `v0.2.0-rc.1` publicly. No JAR, built frontend dist or GHCR image is authorized. If #138/#139 or checksum evidence is incomplete, postpone the public tag/release and update CHANGELOG to the eventual actual date before source freeze.

## Independent acceptance and protected publishing

A versioned JSON must be merged to main in a **separate independently reviewed PR after source SHA is frozen**, under docs/06-deployment/r24-acceptances/<tag>.json. Point the tag at the earlier source SHA (not the newer acceptance commit), avoiding a circular self-reference. The publish workflow reads this file from main.

Both modes require tag, full source_sha, archive_sha256, explicit mode, independent reviewer assertion and supporting HTTPS evidence. Assertions in JSON are not review proof: verify the acceptance PR and underlying evidence.

- Binary mode: pinned eclipse-temurin:21-jre@sha256 digest, a CLEARED permissive group evidence URL, individual CLEARED exceptions for all other shipped licenses, source/relink, upstream texts, assets, OCI service/base images, checksums and notes evidence.
- Source-only mode: evidence for source_provenance, source_license, sbom_source_boundary, archive_and_checksums and release_notes; binary license groups do not authorize this mode.

**Yến must establish the protected r24-release GitHub Environment with required independent reviewers (Yến and Ánh) plus v* tag protection ruleset BEFORE any tag is created or pushed.** A workflow environment declaration alone does not guarantee protected configuration.

GHCR image tags and GitHub Releases are non-atomic: partial publishing requires manual digest reconciliation rather than blind retries. The implementation owner must record final package digests, artifact hashes and status.

## Reproduce

1. Run python3 -m unittest discover -s scripts -p 'test_r24_*.py'.
2. Produce the R23 SBOMs and inventory; run python3 scripts/r24-upstream.py --root . --inventory build/r23/license-inventory.json --output build/r24/upstream.
3. Run python3 scripts/r24-release.py package --mode source-only --tag v0.2.0 --sha <full-source-SHA> --output dist/r24.
4. Run python3 scripts/r24-release.py verify --archive dist/r24/dxos-open-core-0.2.0.tar.gz --checksums dist/r24/SHA256SUMS and, from dist/r24, sha256sum -c SHA256SUMS. After the final planned source SHA is frozen, run **two** workflow_dispatch source-only candidates on that same SHA, and independently compare their archive digest, MANIFEST tag/source SHA/mode, SHA256SUMS and notes.
5. After merge, source SHA freeze, independent acceptance, Yến's environment/tag readiness and approved tag publication, verify real release assets, GHCR image digests (binary only), and the exact archive hash.

**R24 implementation Done** requires independent review, green CI and merge. **Full R24 source-only release Done** requires #138 rights clearance, exact-source #139 acceptance and review, protected `v0.2.0` tag publication, GitHub Release source-only assets and an independently verified downloaded SHA256SUMS. **Binary/JAR/GHCR distribution remains BLOCKED** until separately authorized; none of its obligations are waived.
