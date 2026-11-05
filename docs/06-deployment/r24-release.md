<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R24 — release candidates, checksums, GHCR and independent distribution sign-off

**Implementation owner:** Bảo (@lexuanbao258-collab), for tag-triggered CI, release .tar.gz, SHA256SUMS, GHCR images and CHANGELOG notes. **Independent compliance/release acceptance coordinator:** Yến (@phanyen06ym-ien); Ánh provides independent cross-check where relevant. This matches Yến's clarification on R23 PR #131. R24 is **NOT release-cleared** merely because R23 #131 merged.

## Candidate pipeline

- For a PR changing R24 files, Actions "R24 release candidate and publication" builds Backend and BFF bootJar, Frontend dist, all three **runtime** CycloneDX SBOMs, and checks strict R23 policy plus THIRD-PARTY-NOTICES drift. It runs R24 negative/contract tests.
- scripts/r24-release.py package --tag v0.1.0 --sha <40-hex-commit> --output dist/r24 creates dxos-v0.1.0.tar.gz, SHA256SUMS and RELEASE-NOTES.md. Archive includes both bootable jars, frontend static bundle, LICENSE, NOTICE, THIRD-PARTY-NOTICES, CHANGELOG, release notes, three CycloneDX JSONs and license-inventory.json. Every file is covered by archive MANIFEST.json and SHA256. Tar metadata and gzip mtime are normalized for reproducibility.
- PR and workflow_dispatch runs are **candidate-only**, never publish GHCR or GitHub Releases. They use v0.1.0 as a build fixture; this is not permission to reuse that version as a public release.
- Push to a vMAJOR.MINOR.PATCH tag builds the same candidate only if its commit belongs to main. Matching version must appear in CHANGELOG.md. Tags pushed outside main fail.

## Publication fail-closed gate

The tag-triggered publish job has contents: write and packages: write only within that job, and uses protected "r24-release" GitHub Environment. **Administrators must configure required independent reviewers for that Environment, and tag protections, before publication.** Without configured environment reviewers, the versioned acceptance file gate still applies but does not replace independent branch review.

**Do not add** docs/06-deployment/r24-acceptances/<tag>.json until the source SHA is frozen and the release evidence is independently reviewed. Commit the acceptance JSON to **main in a separate reviewed PR**, after the release source commit; the release tag must point to that earlier exact source commit (an ancestor of main), not the acceptance commit. The publish job reads the accepted document from origin/main. This avoids the impossible self-referential requirement to embed a commit's own SHA inside its contents. Its eventual JSON must bind exact tag and 40-character source_sha, have reviewed_by include Yến or Ánh, contain seven HTTPS evidence references (upstream_license_notices, source_and_relink, frontend_assets, service_images, base_images, archive_and_checksums, release_notes), include a pinned java_base_image reference (eclipse-temurin:21-jre@sha256:<64 lowercase hex>), and enumerate **every** resolved R23 component identity (project, name, version, purl) with status: CLEARED, individual HTTPS disposition evidence, and a license_path under compliance/upstream/ included inside the archive. Reviewed upstream license texts should be committed under docs/06-deployment/r24-upstream/ before the tag is created. Missing, partial, stale or PENDING entries block release. Review signatures are enforced by branch protection and GitHub Environment; strings in JSON alone are not proof of independent approval.

Distribution acceptance also requires upstream license/NOTICE texts, actual release and OCI image inventory including JRE base images, applicable source/attribution/relink checks, non-Java bundled assets, real archive checksums and release-note sign-off. The 131 R23 components on #131 are **PENDING**, not cleared. R23 SBOMs do **not** cover independent Superset/Grafana/service images; evaluate what actually ships. Do not create an empty exception or mark all 131 rows cleared by default.

When the publication gate is satisfied, the pipeline builds two non-root Java 21 images from verified jars (ghcr.io/tricore-opentrix/dxos-backend:<tag> and dxos-portal-bff:<tag>) and attaches .tar.gz + SHA256SUMS to GitHub Release with versioned CHANGELOG notes. Publishing package images does **not** prove production OIDC, PostgreSQL, ingress, or browser acceptance. OCI image and GitHub Release publishing are separate external operations, not atomic; in a partial failure, investigate and reconcile exact digest/artifact state rather than blindly rerunning.

## Reproduction and acceptance

~~~sh
python3 -m unittest discover -s scripts -p 'test_r24_*.py'
python3 scripts/r24-release.py verify \
  --archive dist/r24/dxos-v0.1.0.tar.gz --checksums dist/r24/SHA256SUMS
# Publication gate (expected to BLOCK until approved evidence exists):
python3 scripts/r24-release.py gate \
  --archive dist/r24/dxos-v0.1.0.tar.gz --checksums dist/r24/SHA256SUMS \
  --acceptance docs/06-deployment/r24-acceptances/v0.1.0.json \
  --tag v0.1.0 --sha <exact-40-hex-source-SHA>
~~~

Record exact PR HEAD, candidate workflow link and five R24 unit-test results, archive hash, package manifest, applicable CI results, reviewer verdict, acceptance-document PR, tag commit and eventual OCI digests. **R24 implementation Done** requires independent review and merge of its implementation PR; **R24 distribution CLEARED** remains a separate release-specific decision.
