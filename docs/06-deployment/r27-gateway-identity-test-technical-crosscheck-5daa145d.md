<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 / #138 — Gateway Identity test technical cross-check (NOT rights clearance)

**Review performed:** 2026-11-06. This is a **read-only technical/source-history evidence report** for Bảo-associated Backend contributions, prepared for independent Yến/Ánh review. It is NOT a first-person contribution declaration or copyright/Apache-2.0 permission. Do not promote this document to a final R24 `source_license` or `source_provenance` clearance.

**Repository:** [tricore-opentrix/opentrix](https://github.com/tricore-opentrix/opentrix). Source candidate baseline: `5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33`; after #136, `main@92bd46bd8386b07fa4618eac077caa6cd1dbd714`. The two tested file **Git blob SHAs match across these two commits**, even though the overall source commit and candidate archive may differ. These are not final stable release SHA/asset digests.

## Verified paths, Git identities and scope

| R27 ID | File (under `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/`) | Git blob SHA at both checked commits | Git history associated with Bảo |
| --- | --- | --- | --- |
| ID-28 | `GatewayIdentityHandoffHttpTest.java` | `70dad237cdb0569ac5c57d239829275c76b24b66` | [Initial file at former gateway path, `f11461d`](https://github.com/tricore-opentrix/opentrix/commit/f11461d61fa3ed98913617738f8857c1b7831c91) (2026-10-19); relocation `f9b9568`; test-only probe registration `b797b99`; nested probe type fix `b370574` |
| ID-29 | `GatewayKeycloakTokenHttpIntegrationTest.java` | `15f11e73b104e34165c9fcfb5738436131a5e07e` | [Initial file `e5793ef`](https://github.com/tricore-opentrix/opentrix/commit/e5793efbef1fb7f5017d841d455fc29dda49ab70) (2026-10-19); probe registration `8905e00`; nested type fix `f0b71c8` |

Historical Git commit [`fa16f30d`](https://github.com/tricore-opentrix/opentrix/commit/fa16f30d5c67a49186b1c36f2babe58d54a15e38) is associated with Yến and adds SPDX/REUSE attribution metadata to the two current files. This change must **not** be treated as proof that Yến authored the test logic, or that Bảo owns all contents.

## Exact source behavior checked (not test execution)

### ID-28 — mocked HTTP Identity-to-Gateway boundary

[Current source at audited SHA](https://github.com/tricore-opentrix/opentrix/blob/5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33/backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java).

- `@WebMvcTest`, `MockMvc`, Spring Security config, and `@MockitoBean JwtDecoder`: the decoder is **mocked**, so this verifies HTTP filter-chain / principal / gateway authorization wiring **without** a real JWT signature or JWKS validation.
- Five named test cases cover: verified read-authority actor/subject mapping, missing `workflow:read` returning 403, unknown/wildcard authority rejection, anonymous/forged identity headers returning 401, and mocked invalid bearer token rejection returning 401.
- `/__test_only/gateway/identity-handoff` is the nested `ProbeController` test-only route, **not a production endpoint**. Source alone does not establish actual successful runtime/CI invocation at the audited SHA.

### ID-29 — opt-in real Keycloak JWT test

[Current source at audited SHA](https://github.com/tricore-opentrix/opentrix/blob/5daa145d3b5f20f2f16fe0799c6d0808fd6c3e33/backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/GatewayKeycloakTokenHttpIntegrationTest.java).

- `@EnabledIfEnvironmentVariable(named = "OIDC_TEST_ACCESS_TOKEN", matches = ".+")` gates execution. This is **not** proof that a real local Keycloak token was available or that the three tests ran.
- `@WebMvcTest` with `SecurityConfig` and no mocked decoder in this file exercises HTTP → Spring Security OIDC/JWKS verification → principal → gateway operation gate **when** a configured local Keycloak and valid token are actually available.
- Three tests check access from real bearer token and read authority; rejection after deliberate signature alteration; and anonymous/spoofed-header rejection. The probe path `/__test_only/gateway/identity-handoff-live` is **not** a production route.
- No test run logs, local Keycloak configuration attestation, token (never post it), or independent runtime proof were collected by this documentation inspection.

## Separately unresolved first-person source/rights facts — NO AUTO-ASSUMPTION

For **each** of ID-28 and ID-29, the Git record does not establish:

1. Whether content was authored manually, generated/edited with ChatGPT/Claude, adapted from an external snippet/template/fixture, or a mixture; the user's earlier **general** AI-assisted Backend workflow report must not become a new per-file attestation.
2. Whether any third-party material in the generated or edited output requires attribution, a preserved LICENSE/NOTICE, replacement, or additional consent. Use of a Spring/JUnit API does not itself prove copying or originality.
3. Which portions Bảo actually has authority to grant under Apache-2.0; rights over other contributors' works, upstream Gradle assets, and vendor code are **not** inferred.

**Required next evidence:** Bảo's genuine, separate per-file answers about the AI/manual workflow, any known or suspected external source/adaptation, and the scope of rights he can authorize. Yến/Ánh independently review this declaration against [R27 #138](https://github.com/tricore-opentrix/opentrix/issues/138), [Identity PR #142](https://github.com/tricore-opentrix/opentrix/pull/142), and this register. Unknown facts must remain **UNKNOWN/PENDING**, not be replaced by `NONE` or `CLEARED` without evidence.

**Technical verdict:** `PATH_BLOB_AND_GIT_HISTORY_VERIFIED / TEST_PURPOSE_REVIEWED_FROM_SOURCE / RUNTIME_EXECUTION_NOT_ATTESTED / RIGHTS_AND_ATTRIBUTION_PENDING`.

**R24 consequence:** [#139](https://github.com/tricore-opentrix/opentrix/pull/139) remains Draft; `v0.2.0.json` retains null source_sha, archive_sha256, evidence URLs and empty reviewed_by until independent source rights and exact final artifact review. No tag, GitHub Release, JAR or GHCR publication authorized.
