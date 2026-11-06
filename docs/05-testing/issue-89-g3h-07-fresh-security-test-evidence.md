# Issue #89 — G3H-07 Fresh Identity/Gateway/Authorization Test Evidence

**Evidence ID:** E89-005  
**Status:** VERIFIED — targeted offline test execution; integrated acceptance NOT VERIFIED  
**Source baseline:** PR #94, `2b8b29070b9fc51230e008a0ae70f8acefe5994f`  
**Evidence owner:** Yến  
**Review:** Independent Bảo/Ánh review pending  
**Execution worktree:** `D:\DEV\Projects\DX_OS_ISSUE89_TEST_REVIEW` (local, detached clean HEAD)

## 1. Execution summary

Three separate targeted Gradle executions at the same pinned source SHA, with `--offline --no-daemon test --rerun-tasks`, reported the following fresh JUnit outcomes:

| Run | UTC execution window | Test class | Tests | Failures | Errors | Skipped |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| G3H-07F | 2026-11-01 20:47:23–20:48:42 | `AudienceValidatorTest` | 5 | 0 | 0 | 0 |
| G3H-07F | 2026-11-01 20:47:23–20:48:42 | `IssuerAndTimestampValidatorTest` | 6 | 0 | 0 | 0 |
| G3H-07G | 2026-11-01 20:49:55–20:51:14 | `RawTimestampJwtDecoderTest` | 14 | 0 | 0 | 0 |
| G3H-07G | 2026-11-01 20:49:55–20:51:14 | `SecurityConfigTest` | 7 | 0 | 0 | 0 |
| G3H-07H | 2026-11-01 20:58:09–20:59:06 | `GatewayIdentityHandoffHttpTest` | 5 | 0 | 0 | 0 |
| G3H-07H | 2026-11-01 20:58:09–20:59:06 | `WorkflowAuthorizationTest` | 46 | 0 | 0 | 0 |
| **Total** | | **Six suites** | **83** | **0** | **0** | **0** |

Each run reported `BUILD SUCCESSFUL`, 4 actionable tasks executed, a matching final SHA and clean detached HEAD. G3H-07G additionally had fresh XML modification times at 2026-11-01 20:51:12 UTC; G3H-07H checked JUnit freshness within its PowerShell script. The G3H-07F run was also checked against freshly produced JUnit results.

## 2. Reproduction

Run from `backend/` after checking the exact source SHA and clean worktree:

```powershell
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests "*AudienceValidatorTest" --tests "*IssuerAndTimestampValidatorTest"
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests "*RawTimestampJwtDecoderTest" --tests "*SecurityConfigTest"
.\gradlew.bat --offline --no-daemon test --rerun-tasks --tests "*GatewayIdentityHandoffHttpTest" --tests "*WorkflowAuthorizationTest"
```

## 3. Evidence-to-claim mapping

| BL-03 case | Evidence contribution | Verdict |
| --- | --- | --- |
| T01 | JWT audience/issuer/timestamp/decoder tests and mocked Spring Security rejection of malformed bearer and forged identity headers | `PARTIAL_VERIFIED` unchanged |
| T02 | Mocked Gateway authority handoff and synthetic Workflow authorization policy checks | `PARTIAL_VERIFIED` unchanged |
| T10 | Synthetic policy absent-decision/provider-failure fail-closed checks | `PARTIAL_VERIFIED` unchanged |

The Gateway HTTP test uses a test-only probe and mocked decoder, not a production Request endpoint. The Workflow authorization suite uses policy fixtures. These runs do not prove live Keycloak/JWKS integration, Request resource/tenant ACL, real provider outage behavior, guarded-write revocation, PostgreSQL concurrency or combined E2E.

## 4. Provenance and non-duplication

This is a **new execution record** for six Identity/Gateway/Authorization classes, **not** the five Workflow/PostgreSQL-oriented classes previously recorded as `E89-001` (also 83 PASS). Do not replace E89-001, add the two 83-case totals as distinct acceptance coverage, or combine with the 717 PASS full-backend regression. Coverage may overlap across runs.

Raw local console/JUnit artifacts and cryptographic digests have not been published with this document. Results are reported from operator-supplied PowerShell/JUnit logs; independent artifact verification is pending.

## 5. Gate and review disposition

- BL-03 T01/T02/T10 stay `PARTIAL_VERIFIED`; all other T01–T12 statuses remain unchanged.
- F89-01–F89-07 remain unresolved source-review findings.
- Real Keycloak and PostgreSQL provider-gated tests are not promoted to PASS.
- HD-01 P0-01/P0-04 remain BLOCKED.
- PR #100 remains Draft; overall Issue #89 remains `CONDITIONAL — DOCUMENTATION DRAFT`.
- Independent reviewers must validate evidence-to-claim mapping and exact source SHA before any gate promotion.
