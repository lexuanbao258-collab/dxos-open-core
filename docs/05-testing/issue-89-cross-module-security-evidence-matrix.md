# Issue #89 — Cross-Module Security Evidence Matrix v0.2

**Project:** DX-OS Open-Core / OPENTRIX
**Issue:** #89 — Cross-Module Security, Contract & Regression Evidence Matrix
**Status:** DRAFT — Independent review pending
**Evidence owner:** Yến
**Independent reviewers:** Bảo (Workflow/Data), Ánh (Application/Portal)
**Document base:** `2a94a46579c4d3d9999b0da831d9c3bb505cc3e2`
**Backend execution SHA:** `2b8b29070b9fc51230e008a0ae70f8acefe5994f`
**Frontend execution SHA:** `25f73d10b51556b446af12e636b9e19cd53ad030`

## 1. Scope and evidence policy

This document records independent test execution and contract-to-test
traceability for Issue #89. It does not authorize merge, LIVE activation,
database changes or production acceptance.

Test results from separate SHAs must remain separate. Unit, mocked HTTP,
in-memory, real-provider integration and browser E2E are distinct levels
of evidence.

The BL-03 specification is PROPOSED, pending Bảo/Ánh review. Its test IDs
are reused for traceability, not represented as approved acceptance IDs.

HD-01 P0-01/P0-04 remain BLOCKED and outside this change.

## 2. Exact evidence baselines

| Source | SHA | Evidence boundary |
| --- | --- | --- |
| Main documentation base | `2a94a46579c4d3d9999b0da831d9c3bb505cc3e2` | Documentation only |
| PR #80 | `16d1f00493ec6b8eaeb5fac466a48c7f0469ed1e` | SQL fixtures |
| PR #93 | `f06e0b05176702c7c5d49207f956f35357a17905` | Windows test fixture changes |
| PR #94 | `2b8b29070b9fc51230e008a0ae70f8acefe5994f` | Backend tests executed locally |
| PR #98 | `25f73d10b51556b446af12e636b9e19cd53ad030` | Separate frontend BFF test evidence |

These commits are not asserted to form one approved merged integration
baseline.

## 3. Execution evidence

| ID | Execution | Result | Verdict |
| --- | --- | --- | --- |
| E89-001 | Five focused backend test classes at PR #94 SHA | 83 PASS, 0 FAIL, 0 ERROR, 0 SKIP | VERIFIED — offline |
| E89-002 | Full backend Gradle test at PR #94 SHA | 835 total: 717 PASS, 118 SKIP, 0 FAIL, 0 ERROR; 57 XML files | VERIFIED — with skips |
| E89-003 | Frontend BFF focused security tests at PR #98 SHA | 331 PASS | VERIFIED — separate source/test |
| E89-004 | Frontend BFF serial regression at PR #98 SHA | 687 PASS | VERIFIED — serial mode only |
| E89-005 | [G3H-07 fresh Identity/Gateway/Authorization evidence](issue-89-g3h-07-fresh-security-test-evidence.md), PR #94 SHA | 83 PASS across 6 suites, 0 FAIL, 0 ERROR, 0 SKIP; 3 fresh offline runs | VERIFIED — targeted test execution only; integrated acceptance NOT VERIFIED |

The E89-001 and E89-005 runs each reported 83 PASS but cover different targeted class sets. Neither total is added to the 717 full-regression passes; runs may overlap in coverage and do not constitute integrated acceptance.

An earlier parallel frontend run experienced two timeouts. Serial PASS
does not establish parallel execution stability.

Raw local JUnit XML and console logs were inspected; durable evidence
publication and artifact digests are still pending.

### 3.1 Reproduction commands

Run in `backend/` at the pinned PR #94 SHA:

```powershell
.\gradlew.bat test --tests "*WorkflowApprovedMigrationTest" --tests "*WorkflowApprovedSmokeLauncherTest" --tests "*WorkflowSecurityDefinerReviewLauncherTest" --tests "*WorkflowRuntimePrivilegeAuditTest" --tests "*WorkflowSecurityDefinerReviewTest" --no-daemon --rerun-tasks --console=plain

.\gradlew.bat test --no-daemon --rerun-tasks --console=plain
```

Run in `frontend/` at the pinned PR #98 SHA:

```powershell
npm.cmd test -- --maxWorkers=1 --no-file-parallelism
```

Commands are reproduction references, not evidence that every provider
integration was executed.

## 4. Provider-gated test inventory

| Required class-level gate | Skipped cases | Coverage |
| --- | ---: | --- |
| `OIDC_TEST_ACCESS_TOKEN` | 6 | Real Keycloak/JWKS/Gateway |
| `WORKFLOW_TEST_JDBC_URL` | 71 | PostgreSQL persistence, trigger guard, isolation |
| `WORKFLOW_AUDIT_LOCAL_JDBC_URL` | 33 | Migration, smoke, privilege audit, bootstrap, definer |
| `WORKFLOW_TEST_CA_CERT` | 8 | Event trigger review |
| **Total** | **118** | **NOT EXECUTED** |

All 118 JUnit skipped cases lacked an XML skip-message reason. Their
class-level environment gates were verified against source. Some cases
also have method-level gates, fixture requirements or assumptions.

No disposable PostgreSQL or real Keycloak tests were executed in this
Issue #89 review run. No provider PASS is inferred.

## 5. BL-03 specification provenance

Specification:
`docs/reviews/issue-53/bl-03-security-test-matrix.md`
at PR #62 SHA `7bde57373f85b110d456629552f964af8df7d9c4`.

The same file at PR #74 SHA
`f943d33c7b7c003fc44d0234e4c912f24dcee847`
has identical Git blob SHA
`b33807cca41497c835162a3e938fc731c57e1c8b`.

Status: PROPOSED; original specification execution status:
NOT EXECUTED.

BL-03 addresses the Request prototype. Existing Core or Gateway test
results do not automatically satisfy the integrated Request acceptance
scenarios.

## 6. Contract-to-test evidence mapping

Paths below are relative to the backend test root:
`backend/src/test/java/com/tricore/dxos/`.

| BL-03 ID | Verified test method / source candidate | Proven behavior and execution level | Remaining acceptance gap | Status |
| --- | --- | --- | --- | --- |
| T01 | `infrastructure/identity/security/SecurityConfigTest.java`: `spoofedIdentityHeadersCannotAuthenticateAnonymousRequest`, `rejectsMalformedBearerTokenBeforeController`; `GatewayIdentityHandoffHttpTest.java`: `invalidBearerTokenIsRejectedBeforeGateway` | Mock HTTP: 401; forged identity headers denied; service/probe not invoked in tested variants | Real Keycloak, integrated Request routes, side-effect proof across all JWT variants | PARTIAL_VERIFIED |
| T02 | `infrastructure/identity/security/GatewayIdentityHandoffHttpTest.java`: `validJwtWithoutRequiredWorkflowReadAuthorityReturns403`, `unknownOrWildcardAuthorityDoesNotGrantRead`; `application/authorization/WorkflowAuthorizationTest.java`: `rejectsCreationWithoutRequiredAuthority` | Mock HTTP 403 for missing/wildcard/altered read authority; policy rejects missing CREATE authority | Request operation mapping, resource policy, transition behavior and protected writes | PARTIAL_VERIFIED |
| T03 | `application/authorization/WorkflowAuthorizationTest.java`: `hiddenAndMissingResourceFixturesHaveTheSameSanitizedDenial` | Policy unit: hidden and missing return sanitized HIDDEN without cause | Same HTTP 404 status/body and no existence disclosure through Request persistence | PARTIAL_VERIFIED |
| T04 | No exact Request history-specific ACL execution evidence mapped | Workflow history mechanics exist separately | Approved history ACL plus HTTP/persistence negatives | GAP |
| T05 | No exact cross-tenant Request listing execution evidence mapped | None established for this BL-03 behavior | Authoritative tenant/resource filtering, pagination/counts | GAP |
| T06 | No verified D1/D2/D3 Request DTO projection | Policy/field classification pending | Approved classification and sanitized list/detail responses | POLICY_PENDING |
| T07 | No verified requester-specific confirmation policy | None established for BL-03 | Wrong actor denied with no Request, version or history mutation | GAP |
| T08 | No verified eligibility/grant revocation at final write boundary | None established for BL-03 | Ratified guarded-write contract and race evidence | BLOCKED |
| T09 | `core/workflow/WorkflowPersistencePortTest.java`: `rejectsStaleConcurrentProposalAndReplayWithoutChangingStateOrHistory`, `concurrentCommitsHaveExactlyOneWinner` | In-memory: stale version conflict; one winner; state/history consistency | Two real PostgreSQL connections, durable state verification and Request path | PARTIAL_VERIFIED |
| T10 | `application/authorization/WorkflowAuthorizationTest.java`: `treatsAbsentPolicyDecisionAsUnavailable`, `sanitizesPolicyFailureAndNeverGrantsAccess` | Policy unit: unavailable rejects access; failure cause sanitized | Request HTTP 503 contract, response and protected lookup/write effects | PARTIAL_VERIFIED |
| T11 | Core history tests; no approved protected timeline projection mapped | Core history mechanics only | History resource ACL, event visibility, classified fields, E2E | POLICY_PENDING |
| T12 | `core/workflow/WorkflowPersistencePortTest.java`: `unknownTransitionOutcomeCommitsInstanceAndHistoryTogetherAndRejectsReplay` | In-memory: COMMIT_OUTCOME_UNKNOWN and replay conflict; matching state/history | Real PostgreSQL commit uncertainty and authorized reconciliation | PARTIAL_VERIFIED |

`PARTIAL_VERIFIED` means a specific test layer and assertion passed.
It never means the complete BL-03 case is accepted.

## 7. Critical negative coverage

| Scenario | Evidence status | Owner |
| --- | --- | --- |
| Forged identity headers | VERIFIED — mock HTTP slice | Yến |
| Missing/altered exact workflow authority | VERIFIED — policy/mock HTTP slice | Yến |
| Hidden vs missing resource HTTP parity | NOT VERIFIED — policy-only evidence | Yến / Ánh |
| Unauthorized history and classified data | GAP / POLICY_PENDING | Ánh / Yến |
| Cross-tenant Request list | GAP | Ánh |
| Wrong actor confirmation | GAP | Ánh |
| Revoked grant or eligibility before commit | BLOCKED — contract pending | Bảo / Ánh / Yến |
| Stale version concurrent commit | VERIFIED — in-memory only; PostgreSQL SKIPPED | Bảo |
| Authorization backend unavailable | VERIFIED — policy-only; HTTP pending | Yến / Ánh |
| Unknown commit and reconciliation | VERIFIED — in-memory only; real DB SKIPPED | Bảo |

## 8. Independent review assignments

**Yến — Identity/Gateway/Security**

- Verify JWT, authority, forged-header and policy-error mappings.
- Confirm actual assertions and prevent unsupported E2E claims.
- Maintain exact SHA/test-to-contract traceability.

**Bảo — Workflow/Data**

- Validate workflow version/history/unknown-commit coverage.
- Review PostgreSQL skipped suite prerequisites and disposable-only envelope.
- Check SQL/fixture integrity and evidence boundaries.

**Ánh — Application/Portal**

- Validate Request history, listing, confirmation and DTO gaps.
- Review BFF/Portal evidence against application contracts.
- Confirm whether BL-03 specification applies to current Helpdesk APIs.

Reviewer outcomes must be PASS, CONDITIONAL or BLOCKED with
finding IDs and exact reviewed SHA.

## 9. Open blockers and disposition

- BL-03 remains PROPOSED pending independent review.
- No approved combined Identity/Gateway/Workflow/Request/Portal E2E
  baseline has been established.
- Real PostgreSQL and Keycloak integration evidence is NOT EXECUTED.
- HD-01 P0-01/P0-04 stay BLOCKED; this document does not change
  their contracts or disposition.
- No production acceptance, merge or LIVE activation is authorized.

**Overall Issue #89 disposition: CONDITIONAL — DOCUMENTATION DRAFT.**

## 10. Request Legacy Security Findings — PR #94 Source Audit

### 10.1 Evidence scope

- Source baseline: PR #94 at `2b8b29070b9fc51230e008a0ae70f8acefe5994f`.
- Review method: read-only source, JUnit XML and assertion inspection.
- Scope: legacy `com.tricore.dxos.request` prototype and its Spring Security boundary.
- These findings do not establish runtime exploitation, external deployment exposure or production acceptance.
- Existing BL03-T01–T12 verdicts in Section 6 remain unchanged.
- Findings are review candidates, not independent reviewer approvals.

### 10.2 Findings register

| ID | Related case | Source evidence | Observed discrepancy | Status | Review owner |
| --- | --- | --- | --- | --- | --- |
| F89-01 | T05 | `request/service/RequestService.java:28-35` | `list()` calls `repository.findAll()` without observed caller or tenant scoping | SOURCE_VERIFIED; exposure unverified | Application |
| F89-02 | T07 | `request/controller/RequestWorkflowController.java:43-45`; `request/service/RequestWorkflowService.java:52-57` | `confirm(UUID id)` has no observed requester-specific eligibility check | SOURCE_VERIFIED; contract review pending | Application |
| F89-03 | T04 | `request/service/RequestWorkflowService.java:68-76` | History retrieval uses request ID without observed resource ACL enforcement | SOURCE_VERIFIED; HTTP ACL not verified | Application |
| F89-04 | T06 | `request/dto/RequestResponse.java:9-16` | Static DTO mapping; no observed caller-dependent classified projection | POLICY_PENDING | Application |
| F89-05 | T08 | `request/service/RequestWorkflowService.java:28-83` | Transactional writes are present, but no eligibility/revocation check at the guarded write boundary was observed | CONTRACT_PENDING; race evidence missing | Application / Workflow-Data |
| F89-06 | T11 | `request/dto/RequestHistoryResponse.java:9-14`; `request/service/RequestWorkflowService.java:68-72` | Static timeline mapping; event-level visibility policy not observed | POLICY_PENDING | Application |
| F89-07 | Exposure mode | `DxosApplication.java:6`; `request/controller/RequestController.java:19-20`; `request/controller/RequestWorkflowController.java:19-20`; `infrastructure/identity/security/SecurityConfig.java:42-45` | Default component scan includes unconditionally annotated legacy Request controllers, while GW-ADR-06 specifies legacy default OFF | DESIGN_IMPLEMENTATION_GAP; runtime not verified | Application / Architecture |

Paths above are relative to `backend/src/main/java/com/tricore/dxos/` unless otherwise specified.

### 10.3 Positive evidence and limits

- Spring Security uses authenticated access for non-health requests. JWT verification is not equivalent to resource authorization.
- Request controller and service tests demonstrate functional DTO, lifecycle and history behavior.
- `RequestWorkflowTransactionTest` verifies mocked transaction ordering and rollback behavior, not revocation-at-commit.
- Core Workflow policy and persistence tests must not be presented as integrated legacy Request security acceptance.
- Real Keycloak, real PostgreSQL concurrency and combined Request/Portal E2E remain unverified in this audit.

### 10.4 Architecture decision references

- `docs/02-core/gateway/gateway-architecture.md:88-118`: GW-ADR-04 through GW-ADR-06.
- `docs/02-core/workflow/workflow-architecture.md:229-250`: verification policy, risk register and review gates.
- `docs/01-architecture/dependency-rules.md:40-59`: legacy Request classification and EXISTING/TARGET/EXCEPTION/BLOCKER distinction.

GW-ADR-06 lists default and demo modes with legacy Request OFF. The mode table is a proposed exposure design, not proof of implementation. A route missing from `RequestMapping` is distinct from a resource-hidden 404.

### 10.5 Required independent acceptance evidence

| Finding | Required next evidence |
| --- | --- |
| F89-01 | Tenant/resource-scoped listing contract, negative tests, counts and pagination checks |
| F89-02 | Approved requester/actor policy, wrong-actor denial and no-mutation assertions |
| F89-03 | Authorized history read, hidden/missing parity and no protected-data disclosure |
| F89-04 | Approved D1/D2/D3 field classification and sanitized list/detail projections |
| F89-05 | Ratified revocation/eligibility contract and transactional race/fault evidence |
| F89-06 | Approved event visibility policy and protected timeline negative E2E |
| F89-07 | Default/demo/legacy-test `RequestMapping` registry checks and conflict-mode startup validation |

Each reviewer must record the exact reviewed SHA and a PASS, CONDITIONAL or BLOCKED decision with evidence. The matrix author cannot independently approve these findings.

### 10.6 Disposition

- Source review completed for the stated PR #94 baseline.
- Runtime and external exposure: NOT VERIFIED.
- Contract/policy gaps: unresolved.
- HD-01 P0-01/P0-04: unchanged and BLOCKED.
- PR #100: documentation-only Draft; no production code changes or merge authorization.
- Overall Issue #89 disposition remains CONDITIONAL — DOCUMENTATION DRAFT.
