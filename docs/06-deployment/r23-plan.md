<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R23 implementation scope

Generate CycloneDX SBOMs for backend, Portal BFF and frontend, implement a fail-closed dependency-license policy, generate THIRD-PARTY-NOTICES, and validate the Gradle wrapper in CI. Preserve immutable Flyway migrations and dependency lockfiles.

Acceptance requires evidence at exact HEAD, independent review, and inclusion of notices in release assets. This plan alone is not implementation acceptance.

## Implementation status and commands

The draft PR creates three CycloneDX JSON SBOMs (Backend Gradle,
Portal BFF Gradle, and Frontend npm) and uploads them with
license-inventory.json and generated THIRD-PARTY-NOTICES.md. The generated
notices inventory is also tracked at repository root; CI uses diff -u to
reject stale notices when resolved dependency versions or licenses change.

    ./backend/gradlew -p backend cyclonedxBom
    ./backend/gradlew -p portal-bff cyclonedxBom
    cd frontend && npm ci && npx --yes @cyclonedx/cyclonedx-npm@6.0.1 --output-format JSON --output-file ../frontend-sbom.json
    cd ..
    python3 scripts/r23-compliance.py --backend backend/build/reports/cyclonedx/bom.json --portal-bff portal-bff/build/reports/cyclonedx/bom.json --frontend frontend-sbom.json --output build/r23

The required repo-hygiene job explicitly fails if the R23 compliance
job fails. The audit rejects missing or unknown licenses. Exact-version
license decisions under review: Hibernate ORM 6.6.53.Final uses LGPL-2.1;
Logback 1.5.34 offers an EPL-2.0 alternative; MIT-0 and BlueOak-1.0.0
are separate recognized license identifiers. This does not waive upstream
license texts, copyright notices or binary redistribution obligations.

**Remaining acceptance:** independently review the license decisions,
review the tracked notice inventory against upstream license/NOTICE
texts, include it and the SBOMs in release assets through R24, inventory
standalone services, and verify exact-HEAD hosted CI. Do not mark R23 Done solely
on a successful metadata scan. Release packaging belongs to R24.
