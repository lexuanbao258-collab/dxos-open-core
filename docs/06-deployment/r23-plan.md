<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R23 — runtime SBOM and license-policy implementation

R23 implements reproducible CycloneDX inventories for the backend and Portal BFF Gradle runtime classpaths and the frontend npm **production** dependency graph. It validates the Gradle wrapper, applies fail-closed license policy, generates the tracked `THIRD-PARTY-NOTICES.md` inventory, and makes the license gate mandatory through the required `repo-hygiene` CI job. Preserve historical Flyway migrations, dependency lockfiles, upstream notices and application behavior.

**Separation of decisions:** R23's machine-policy approval verifies dependency declarations against a reviewed allowlist. It is NOT permission to distribute binaries and is NOT R24 release/distribution acceptance.

## Prerequisite — JPA cleanup (#133)

PR [#133](https://github.com/tricore-opentrix/dxos-open-core/pull/133) was squash-merged into `main@84a7ca9e5b5f1012b95f676c86c2ec1d95dd3ea7`. The synchronized backend declares `spring-boot-starter-jdbc` instead of the obsolete `spring-boot-starter-data-jpa`.

Fresh R23 CycloneDX generation must prove `hibernate-core`, `jakarta.persistence-api`, `antlr4-runtime` and `hibernate-commons-annotations` absent from the resolved **backend runtime** inventory. The prior Hibernate 6.6.53.Final LGPL policy exception is now **removed**; retaining `hibernate-validator` for Bean Validation is expected and does not imply Hibernate ORM use. A future JPA/ORM reintroduction must undergo new independent license policy and redistribution review.

## Reproduction / exact-HEAD CI evidence

The R23 job installs npm packages for tool execution, but `--omit dev` **excludes** frontend development/test dependencies from the SBOM. For the intended runtime inventory, `vitest`, `jsdom`, `typescript` and `@vitest/*` must not appear unless independently proven to be actual production dependencies. Do not indiscriminately delete MIT-0 or BlueOak packages: retain them whenever present in the resolved runtime graph.

```sh
./backend/gradlew -p backend cyclonedxBom --no-daemon
./backend/gradlew -p portal-bff cyclonedxBom --no-daemon
(cd frontend && npm ci && npx --yes @cyclonedx/cyclonedx-npm@6.0.1 --omit dev --output-format JSON --output-file ../frontend-sbom.json)
python3 scripts/r23-compliance.py \
  --backend backend/build/reports/cyclonedx/bom.json \
  --portal-bff portal-bff/build/reports/cyclonedx/bom.json \
  --frontend frontend-sbom.json --output build/r23
diff -u THIRD-PARTY-NOTICES.md build/r23/THIRD-PARTY-NOTICES.md
python3 -m unittest discover -s scripts -p 'test_r23_*.py'
```

Every candidate HEAD must pass required hosted CI `backend`, `portal-bff`, `frontend`, `r23-compliance`, and `repo-hygiene`; the last checks `needs.r23-compliance.result == success`. Archive all three CycloneDX JSON BOMs, license-inventory.json and generated THIRD-PARTY-NOTICES.md with the exact-run evidence. Generated notices are an inventory, not an upstream license/NOTICE collection or legal certification.

## R23 implementation — Definition of Done (NOT release acceptance)

R23 may be marked **Done only after** all of these are satisfied:

1. **#133 has merged into main** and R23 is synchronized to that main SHA without unexpected changes to code, historical SQL or lockfiles.
2. Resolved **runtime** inventories match the intended dependency scopes: backend JPA/Hibernate ORM leftovers eliminated, frontend development/test dependencies omitted, real runtime licensing obligations retained; no unreviewed/unknown declarations.
3. The generated notice inventory exactly matches the checked-in `THIRD-PARTY-NOTICES.md`, and the three SBOMs, inventory, notices and strict SPDX negative-test evidence correspond to the same final HEAD.
4. Independent reviewers record the applicable formal verdict on that exact HEAD; the required CI `repo-hygiene` gate enforces R23; **PR #131 is merged into `main`** under branch protection.
5. The concrete **R24 release/distribution handoff list is documented and accepted** by the responsible owner(s): upstream license/NOTICE and provenance review, component and service-image coverage, packaging/relinking obligations when applicable, and release artifact acceptance criteria.

R23 completion does **not** require R24 to have attached reviewed license/NOTICE texts and SBOMs to a *future release archive*. Such archive publication and redistribution sign-off belong to R24. CI `APPROVED` must never be presented as `distribution CLEARED`.

## R24 — independently gated distribution acceptance

**R24 automation implementation owner: Bảo. R24 compliance/distribution acceptance coordinator: Yến.** Bảo hands over the exact-head SBOMs, notices inventory, pending-obligations matrix, reproducible commands and CI artifact IDs. The R23 inventory's `release_distribution_owner=R24 / Yến` denotes the compliance acceptance workstream, not transfer of release-workflow coding ownership. R24 independently determines actual release contents and each required license/source/notice disposition.

Each component enters the inventory with `release_distribution_status=PENDING` and `release_distribution_owner=R24 / Yến`. That state does not change merely because `license_policy_status=APPROVED` or CI is green. R24 must not distribute a release while material obligations are unresolved, except for an explicitly documented, authorized exception.

| Scope when actually shipped | Evidence / required R24 disposition |
| --- | --- |
| Logback classic/core 1.5.34 (selected EPL-2.0 option) | [Upstream dual-license statement](https://logback.qos.ch/license.html), [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/): record selected terms, downstream notices, provenance and applicable source availability |
| Hibernate ORM LGPL-2.1 **only if still shipped** | [Hibernate ORM releases](https://hibernate.org/orm/releases/6.6/), [LGPL-2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html): evaluate actual linking/packaging, license text, source/offer and replacement/relink rights; do not carry forward this obligation when verified absent after #133 |
| MIT-0 runtime dependencies | [MIT-0](https://spdx.org/licenses/MIT-0.html): preserve required text and attribution where applicable |
| BlueOak-1.0.0 runtime dependencies | [BlueOak](https://spdx.org/licenses/BlueOak-1.0.0.html): provide license text or valid link to recipients; do not infer runtime coverage from the former dev-only npm SBOM |
| Other shipped dependencies and frontend assets | Per-component upstream license/NOTICE texts, provenance, source/source-offer requirements where applicable and any dual-license selection; verify actual output bundles |
| Superset, Grafana or other independently shipped service images | Independent image inventory/SBOM, service license texts, attestations and downstream notices beyond the three Gradle/npm BOMs |

**R24 distribution acceptance** requires versioned release SHA and package/image manifest, approved per-component dispositions, the reviewed upstream license and NOTICE texts, correctly included/generated SBOMs and THIRD-PARTY-NOTICES.md in the **actual release archive**, archive/digest checks, accessibility verification, image coverage and recorded independent sign-off. Unresolved items keep the release **BLOCKED**. These release-asset checks are not R23-implementation completion criteria.

## Strict SPDX expression checks

`license_status` consumes the full token stream using a constrained SPDX grammar: identifiers, AND/OR, parentheses and explicitly reviewed `WITH` exceptions. Malformed expressions, unknown licenses, unexpected characters, unsupported `WITH` clauses (including under an accepted `OR` sibling), and upgraded versions outside a pinned exception fail closed. Tests include negative canaries.
