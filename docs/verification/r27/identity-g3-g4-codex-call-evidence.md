<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 G3/G4 historical Codex call evidence (DRAFT)

This bounded technical review covers the five G3/G4 files listed below. Three private historical sessions contain 173, 73 and 49 tool calls respectively (295 total). Calls were numbered in JSONL file order across both `function_call` and `custom_tool_call` response items, starting at one. The 14 entries below were matched to outputs by exact `call_id`. Their returned command results were `{}`. Consequently, each patch has `PATCH_EXECUTION_UNKNOWN`; neither `status=completed` nor a proposed patch proves application.

The line comparisons count nonempty proposed added lines found in the added-line diff of [52e599cf](https://github.com/tricore-opentrix/opentrix/commit/52e599cf012ef94e2690ef55d635ca299540b920) or [3fac3bde](https://github.com/tricore-opentrix/opentrix/commit/3fac3bde8713105686af43e7dedd2d23a7407b19). Counts are **line-level correspondence, not generated-code percentages or full-hunk matches**. Duplicate/generic lines may inflate correspondence. Deleted lines, hunk context, intermediate states and later replacement remain unverified. `PARTIAL_CONTENT_MATCH` means some but not all counted additions correspond; it does not mean `APPLIED_VERIFIED`. No call qualifies for `PATCH_CONTENT_MATCH_VERIFIED`.

| Session | CallNo | call_id | Patch operation and target | Git comparison; observed added lines | Execution | Content classification and limit |
| --- | ---: | --- | --- | --- | --- | --- |
| 2026-10-15T08-54-30 | 53 | `call_949c387b5b6c4dbab00ae6eca32fec68` | Update `OidcKeycloakIntegrationTest.java` | 52e599cf; 1/1 | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; one counted line |
| 2026-10-15T08-54-30 | 61 | `call_f6c7fa8be3384437806087ad64bd6551` | Update `OidcKeycloakIntegrationTest.java`; Add `IssuerAndTimestampValidatorTest.java` | 52e599cf; 13/16; 28/33 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunks unverified |
| 2026-10-15T08-54-30 | 72 | `call_e94ebfad4fb644cb874c8d7109350dcd` | Update `SecurityConfigTest.java` | 52e599cf; 15/25 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunk unverified |
| 2026-10-15T08-54-30 | 80 | `call_95eb9f7c61294e3587acf4c0853057e9` | Add `ApiAuthenticationEntryPoint.java`; Update `SecurityConfig.java` | 52e599cf; 30/33; 12/12 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH` overall; full hunks unverified |
| 2026-10-15T08-54-30 | 88 | `call_404ebbc4bba243ceb66e139e1f601f0b` | Update `SecurityConfigTest.java` | 52e599cf; 7/14 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunk unverified |
| 2026-10-15T08-54-30 | 96 | `call_2635fa4dae034610982de92b17956d82` | Update `SecurityConfigTest.java` | 52e599cf; 23/37 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunk unverified |
| 2026-10-15T08-54-30 | 102 | `call_275d5ee9085a405e9f10343c7e0876ca` | Update `SecurityConfig.java` | 52e599cf; 5/5 | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; full hunk unverified |
| 2026-10-15T08-54-30 | 138 | `call_b614a849e2a1457db74670f4422b4d10` | Update `SecurityConfig.java` | 52e599cf; 2/2 | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; small sample |
| 2026-10-15T08-54-30 | 165 | `call_ab6110e858eb4d89957fd919b1ec8a2e` | Update `OidcKeycloakIntegrationTest.java` and Keycloak POC script (dependency outside five-file scope) | 52e599cf; no counted Java added lines | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; other patch content not assessed here |
| 2026-10-17T14-47-24 | 48 | `call_8d357218cd324d4298dcb7856dbeae39` | Update `SecurityConfig.java` | 3fac3bde; 0/11 | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; no counted line in reference diff |
| 2026-10-17T14-47-24 | 65 | `call_131298f4f34e43f0bb7ddb300a44e269` | Update `SecurityConfig.java` | 3fac3bde; 3/16 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunk unverified |
| 2026-10-17T14-47-24 | 70 | `call_20d9fc54e7cb4a90ab5da70267613a6f` | Update `SecurityConfig.java` | 3fac3bde; 0/3 | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; no counted line in reference diff |
| 2026-10-17T20-25-41 | 10 | `call_93f7b8275efb4d98948432269d82884c` | Update `SecurityConfig.java` | 3fac3bde; 57/62 | `PATCH_EXECUTION_UNKNOWN` | `PARTIAL_CONTENT_MATCH`; full hunk unverified |
| 2026-10-17T20-25-41 | 44 | `call_b9282f5183704ee39e1356cd5d440c0f` | Update `OidcKeycloakIntegrationTest.java` and five related files outside five-file scope | 3fac3bde; 1/1 for in-scope test | `PATCH_EXECUTION_UNKNOWN` | `INCONCLUSIVE`; small sample, related files separate |

## Five-file provenance disposition

| File | Group | Codex evidence | ChatGPT/manual integration | External source; reuse; NOTICE | Rights and remaining gap |
| --- | --- | --- | --- | --- | --- |
| `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/ApiAuthenticationEntryPoint.java` | G3 | Call 80; 30/33 line correspondence with 52e599cf | Contributor reports this workflow at Identity scope; per-file response/integration `UNKNOWN` | `UNKNOWN`; copy/adaptation `UNKNOWN`; NOTICE `UNKNOWN` | `PENDING`; verify full hunk and lawful file/portion rights |
| `backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfig.java` | G3 | Calls 80, 102, 138, 48, 65, 70, 10; varying correspondence in both commits | Per-file ChatGPT response, terminal and manual integration `UNKNOWN` | `UNKNOWN`; copy/adaptation `UNKNOWN`; NOTICE `UNKNOWN` | `PENDING`; reconstruct intermediate versions and rights |
| `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/IssuerAndTimestampValidatorTest.java` | G4 | Call 61; 28/33 line correspondence with 52e599cf | Per-file response/integration `UNKNOWN` | Fixture origin `UNKNOWN`; copy/adaptation `UNKNOWN`; NOTICE `UNKNOWN` | `PENDING`; verify fixture lineage and rights |
| `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/OidcKeycloakIntegrationTest.java` | G4 | Calls 53, 61, 165, 44; partial or small-sample correspondence | Per-file response/integration `UNKNOWN` | Token, claim and JWKS origins `UNKNOWN`; NOTICE `UNKNOWN` | `PENDING`; verify fixtures, later edits and rights |
| `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfigTest.java` | G4 | Calls 72, 88, 96; partial line correspondence with 52e599cf | Per-file response/integration `UNKNOWN` | Test structure and fixture origin `UNKNOWN`; NOTICE `UNKNOWN` | `PENDING`; verify full hunks and rights |

For every row, requirements, business logic, architectural direction and technology choices are **team-directed according to the contributor statement**, not independently verified authorship of the resulting expression. The contributor reports both Codex-assisted editing and ChatGPT-generated files/snippets integrated manually or through terminal/PowerShell. Historical ChatGPT responses and integration records remain incomplete. Unmatched code is `UNKNOWN`, not human-only. `CODEX_ASSISTANCE_OBSERVED` is not `CODEX_PATCH_MATCH_VERIFIED`.

The [existing file-level matrix](identity-g3-g4-per-file-source-origin-matrix-5daa145d.csv), [39-row register](identity-source-origin-register-5daa145d.csv), [Contributor Declaration v2](identity-contributor-declaration-yen.md), and [review findings](identity-provenance-review-5daa145d.md) remain authoritative for their recorded scope. Git content commits are separate from SPDX-only metadata edits. No identifiable copied/adapted external source or license obligation has been verified for these five files. This does not establish absence of external material. Technical provenance remains incomplete; copyright/license authority and independent rights review remain `PENDING`. Issue #138 and PR #142 remain open/draft, and this evidence does not authorize R24 or a public release.
