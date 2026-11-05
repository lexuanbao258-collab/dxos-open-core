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

## R24 handoff — independent distribution acceptance (NOT automatic)

Owner: **Yến (R24 release coordination)**. R23 owner Bảo supplies CI
evidence, the complete component inventory, SBOM artifacts, notice snapshot
and reference links. R24 records a release-specific signed disposition.
A license_policy_status of APPROVED means a declaration matched the reviewed
CI policy. It is **not** an approval to redistribute binaries.
Every generated component starts with release_distribution_status=PENDING
until its upstream requirements are checked. A CI result saying
"0 unapproved" must never be described as "0 outstanding obligations".

| Group | Upstream reference | R24 acceptance before distributing |
| --- | --- | --- |
| Hibernate ORM 6.6.53.Final (LGPL-2.1-only) | https://hibernate.org/orm/releases/6.6/ and https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html | Determine actual packaging/linking, preserve LGPL text, provide corresponding source/source offer and replacement/relinking information where applicable; document the distribution model. |
| Logback classic/core 1.5.34 (EPL-2.0 option) | https://logback.qos.ch/license.html and https://www.eclipse.org/legal/epl-2.0/ | Preserve selected EPL-2.0 terms, provenance, notices and applicable source availability for redistributed parts. |
| MIT-0 dependencies | https://spdx.org/licenses/MIT-0.html | Preserve required license text and attribution as applicable. |
| BlueOak-1.0.0 (lru-cache@11.5.3) | https://spdx.org/licenses/BlueOak-1.0.0.html | Deliver the BlueOak license text or a valid link to recipients, and record where it appears. |
| Other dependencies | SBOM PURLs and upstream sources | Verify copyright, license text, upstream NOTICE requirements and any dual-license selection. |
| Standalone service/container images | Superset, Grafana and other images actually shipped | Prepare image-level SBOM and service-license audit; they are outside the npm/Gradle inventory. |

**Release gate:** before R24 publishes, retain a versioned manifest
recording final SHA, package/image versions, per-component dispositions,
reviewer, upstream license links/text and NOTICE obligations. Include
reviewed THIRD-PARTY-NOTICES.md, required upstream license files and
the three SBOM JSON artifacts in actual release assets. Inspect the
archive, digests, access and image coverage. Any pending obligation
at release sign-off remains **BLOCKED**, absent documented authorized
exception; a policy PASS cannot waive this gate. R23 closes after
independent review and accepted R24 handoff, not from green CI alone.

## Strict SPDX-expression parser verification

The license_status function now validates the full token stream,
using a strict SPDX subset: license identifiers, AND/OR, parentheses
and explicitly reviewed WITH exceptions. Invalid syntax is rejected,
even if its individual identifiers are allowlisted. Tests cover
dangling/missing operators, unbalanced parentheses, bad characters
and invalid WITH clauses.
