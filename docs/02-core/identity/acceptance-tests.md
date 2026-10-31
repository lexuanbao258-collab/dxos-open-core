---
title: DX-OS Open-Core — Identity Core Acceptance Tests
document_id: G4-F08
status: PROPOSED — REVIEW REQUIRED
version: 1.0-review-draft
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
owner: TRICORE — to assign
reviewers: Identity-Security / Gateway / Application / Independent G4 — to confirm
---

# Identity Core — Acceptance Tests & Evidence Plan

> **Scope:** all eight Identity Core documentation artifacts and their runtime trust boundary. **All test case execution statuses below are NOT RUN / NOT RE-VERIFIED in this document-authoring session**. Historical test result descriptions in the repository are attributed as such, never promoted into fresh CI, E2E or Gate PASS.

## 1. Acceptance philosophy and evidence classification

**Core's product claim:** stable provider-neutral identity values and Application-facing `CurrentPrincipal.requireVerified()` boundary let verified OIDC infrastructure adapters be replaced *conditionally* without modifying Helpdesk/Facility business logic. This is not proved by interfaces or isolated mock tests alone.

Separate five layers:
- **D — Design:** documented architecture, decisions, threat model and compatibility definition.
- **S — Source:** same exact source SHA, Java signatures, dependency direction and negative cases.
- **C — Conformance:** identical suite for baseline and candidate provider adapters, including errors and trust.
- **I — Integration:** actual IdP/JWKS/BFF/API and resource authorization; failures and recovery.
- **R — Reuse / independent Gate:** both Applications, unchanged business source, independent evidence review.

Use **OBSERVED** only for source facts at commit; **HISTORICAL** for reports recounted in canonical docs; **CANDIDATE** for separate PRs; **PROPOSED** for future tests and expectations; **VERIFIED** only with executed artifacts. `NOT RUN` does not mean fail; `BLOCKED` indicates missing prerequisite.

## 2. System-under-test boundaries and prerequisites

| Layer | Test target | Preconditions |
| --- | --- | --- |
| Core | Eight `core.identity` Java record/enum/exception types | JDK 21, test runtime, source pinned |
| Application port | `application.identity.CurrentPrincipal.requireVerified()` | Trusted verified-context fixture and negative cases |
| Infrastructure | `SpringSecurityCurrentPrincipal`, decoder/security filters | OIDC client config; redacted test credentials |
| Real OIDC | Keycloak test realm/production decoder | Local disposable issuer, valid audience/JWKS and controlled tokens |
| Portal/BFF | Cookie-backed OIDC Authorization Code+PKCE | Actual implementation and HTTPS ingress; **candidate, not presumed available** |
| Authorized APIs | Helpdesk/Facility resource operations | Disposable PostgreSQL/test data, owner/assignee fixtures and audit proof |
| Alternate provider | Compatible OIDC IdP | Explicit approved identity/role linking and controlled migration |
| External security | Network and Gateway route restrictions | Deployable topology and direct-route negative tests |

No sensitive tokens, secrets, signing keys or PII shall be committed to evidence.

## 3. Acceptance test catalog

**Rule:** every row has a defined observable result. The suggested existing test names are coverage pointers, not claims of current execution or perfect equivalence.

| ID | Scope | Scenario | Expected observable result | Test mechanism / source lead | Reviewer | Execution |
| --- | --- | --- | --- | --- | --- | --- |
| ID-AT-01 | Core | Principal construction and defensive set copy | Bad subject/null set/false flag rejected, optional name allowed, no mutable authorities | Core identity JUnit tests | Core maintainer | NOT RUN |
| ID-AT-02 | Core | Opaque AuthenticationContext | Blank rejected, arbitrary nonblank does not establish trust | Core identity JUnit tests | Identity maintainer | NOT RUN |
| ID-AT-03 | Core | Exact authority semantics | Case, wildcard, whitespace and wrong authority do not match | Core identity JUnit tests | Identity/Security | NOT RUN |
| ID-AT-04 | Core | AuthorizationDecision and error codes | Only paired reason/outcome accepted, exceptions sanitized and six codes retained | Core identity JUnit tests | Identity/Security | NOT RUN |
| ID-AT-05 | Adapter | Verified principal provenance | Missing, non-JWT, blank iss/sub denied; manual principal not accepted by HTTP | SpringSecurityCurrentPrincipalTest + filter chain | Identity/Security | NOT RUN |
| ID-AT-06 | Adapter | Canonical subject | Same issuer+subject stable; changed issuer/subject changes ID; strict UTF-8, no normalization | SpringSecurityCurrentPrincipalTest | Identity/Security | NOT RUN |
| ID-AT-07 | Adapter | Scoped authorities | Only exact four workflow values from configured client mapped; unknown/wrong client never grant | SpringSecurityCurrentPrincipalTest | Identity/Security | NOT RUN |
| ID-AT-08 | JWT | Signature, JWKS and issuer | Valid signed access token accepted; tampered signature and wrong issuer rejected | Real OIDC decoder integration | Independent Security | NOT RUN |
| ID-AT-09 | JWT | Audience, lifetime, type, raw subject | Wrong aud, expired/not-before, payload typ mismatch, numeric/missing sub rejected | Validator unit tests + real decoder negatives | Independent Security | NOT RUN |
| ID-AT-10 | HTTP | Unauthenticated and header spoofing | Protected API returns safe 401; X-User/X-Role cannot authenticate | SecurityConfigTest + HTTP negative cases | Gateway/Security | NOT RUN |
| ID-AT-11 | Authorization | Valid authority but forbidden resource | Application ACL denies and no Workflow mutation or false success audit | Helpdesk HTTP+DB integration | Application reviewer | NOT RUN |
| ID-AT-12 | Authorization | Direct restricted route bypass | Generic mutation endpoint unavailable or independently protected | Gateway/backend route integration | Gateway reviewer | NOT RUN |
| ID-AT-13 | BFF candidate | PKCE/state/redirect and session | Invalid state/code verifier/redirect rejected; secure session fixation defense | Browser/BFF OIDC E2E | Portal/Security | NOT RUN |
| ID-AT-14 | BFF candidate | CSRF and cookie policy | Cookie-authenticated state changes without CSRF defense denied; Secure/HttpOnly/SameSite verified | Browser/session integration | Portal/Security | NOT RUN |
| ID-AT-15 | BFF candidate | Refresh, logout, replay | Old session rejected after logout; expiry/refresh handling documented and exercised | BFF integration + fault injection | Portal/Security | NOT RUN |
| ID-AT-16 | Provider swap | Alternate OIDC provider compatibility | Same consumer logic; verified provenance, role mapping and error semantics; identity migration approved | Same suite against two real providers + source diff | Independent reviewer | NOT RUN |
| ID-AT-17 | Availability | JWKS key rotation and IdP outage | No acceptance of unverified tokens; bounded behavior and recovery evidenced | IdP fault/rotation integration | Operations/Security | NOT RUN |
| ID-AT-18 | Reuse | Helpdesk and Facility isolation | Both Applications reuse Identity without Core changes; unauthorized business actions denied | Two-App E2E with exact SHA | Independent G4 reviewer | NOT RUN |
| ID-AT-19 | Operations | Sensitive-data leakage | No secrets, raw access/refresh tokens or provider internals in logs/errors | Redaction/log inspection | Operations/Security | NOT RUN |
| ID-AT-20 | Quality Gate | Architecture and source conformance | No provider SDK imports in Core; test results, SHA and reviewer logged separately | Dependency scan + independent evidence review | G4 Gate reviewer | NOT RUN |

## 4. Mandatory negative-test matrix

| Failure injection | Security invariant and expected assertion | Evidence requirements |
| --- | --- | --- |
| Unsigned, tampered or wrong-signature JWT | 401; no authenticated principal or side effect | Decoder report and HTTP response |
| Correctly signed JWT for wrong issuer/audience | Reject before Application use case | Issuer/audience validator logs and negative assertions |
| Expired, future `nbf`, incorrect **payload** `typ`, non-string raw `sub` | Reject even when signature is valid | Token fixture metadata, decoder result |
| Forged `X-User`, `X-Role`, manually constructed principal | Cannot establish request authentication | Filter-chain test and safe 401 |
| Correct workflow authority, forbidden Helpdesk ticket | Denied; no update, history insert or successful audit | Pre/post DB snapshots and API result |
| Role from wrong resource client or unexpected case/value | No capability elevation | Claims fixtures and verified authority set |
| BFF cookie without CSRF protection | State-changing request rejected (once candidate BFF exists) | Browser session test; not applicable to stateless Bearer-only chain |
| Expired session, reused logged-out cookie | Rejected; no token sent to backend | BFF logs and session-store proof |
| IdP outage or JWKS rotation | No verification bypass; valid cache use, if configured, is bounded and reviewed | Fault logs, config, recovery result |
| Direct Backend/Workflow mutating path | Same authentication/authorization and ACL enforced or path disabled | Direct route tests and DB no-change assertion |
| IdP swap reuses email across accounts | No silent subject merge or privilege leakage | Migration map and conflict test |

## 5. Concrete execution instructions (examples, not executed)

All commands must be run against a **disposable** environment with the source pinned and environment recorded. The backend is Gradle-based and `build.gradle` configures JUnit Platform. Confirm actual class names/paths before using selectors; zero selected tests is NOT a pass.

```powershell
git rev-parse HEAD
git status --short --branch
cd backend
.\gradlew.bat test --tests "*SpringSecurityCurrentPrincipalTest" --tests "*SecurityConfigTest" --rerun-tasks
.\gradlew.bat test --tests "*AudienceValidatorTest" --tests "*TokenTypeValidatorTest" --rerun-tasks
```

On Linux/macOS use `./gradlew`. Run the real OIDC integration test only after providing a disposable, correctly configured Keycloak/issuer and confirming the test's environment requirements and selection rules. The following is a **candidate test selector**, not evidence of execution:

```powershell
.\gradlew.bat test --tests "*OidcKeycloakIntegrationTest" --rerun-tasks
```

A successful command must be accompanied by nonempty test selection, XML/HTML reports, environment/version record and actual exit code. Do not execute resource-mutating tests against production.

**Required evidence record format:**

```text
Test ID:
Repository commit SHA:
Branch / clean status:
Environment: JDK, Gradle, OS, IdP/adapter versions:
Target and configuration (no secrets):
Command and exit code:
JUnit tests run / skipped / failed / errors:
Observed HTTP/status or principal/authority result:
Negative side-effect proof:
Log/screenshot/JUnit XML path:
Known limitations / blockers:
Reviewer and reviewed date:
Disposition: PASS | FAIL | BLOCKED | NOT RUN
```

## 6. Historical evidence inventory (not fresh test results)

The existing `docs/contracts/identity-core.md` reports pure Core JUnit tests for values/errors and `SpringSecurityCurrentPrincipalTest` using synthetic JWT fixtures. It cites earlier counts: `TokenTypeValidatorTest` 4/4; `AudienceValidatorTest` 4/4; `IssuerAndTimestampValidatorTest` 2/2; `SecurityConfigTest` 5/5; `SpringSecurityCurrentPrincipalTest` 19/19. It also reports a previous **local** `OidcKeycloakIntegrationTest` run with 3 tests, 0 skipped/failures/errors. These are **HISTORICAL DOCUMENTED RESULTS**, not independently run or revalidated here. They cannot establish a real two-provider swap, full Gateway/Facility E2E, BFF session/CSRF security or production readiness.

At the pinned SHA, `backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipalTest.java` was inspected, along with `backend/build.gradle`. Test class locations for every row, especially cross-Application and BFF tests, require source discovery; do not equate proposed names with existing files.

## 7. Traceability: prior design documents → test groups

| Identity document | Primary acceptance IDs |
| --- | --- |
| `README.md` | ID-AT-18, 20; overall ownership and proof |
| `architecture.md` | ID-AT-05, 08, 11, 16, 18, 20 |
| `domain-model.md` | ID-AT-01–07 |
| `contracts.md` | ID-AT-01–09, 16, 20 |
| `authentication-flow.md` | ID-AT-05–10, 13–15, 17 |
| `authorization-model.md` | ID-AT-03, 07, 11, 12, 18 |
| `security-boundaries.md` | ID-AT-08–17, 19 |
| `acceptance-tests.md` | ID-AT-01–20 and evidence ledger |

For `security-boundaries.md`, map proposed `SEC-01`–`SEC-12` to corresponding `ID-AT` cases and retain threat IDs `TH-01`–`TH-12` in the independent security evidence register. Shared coverage does not allow skipping a threat's separate expected result.

## 8. Provider substitution acceptance protocol

**Baseline A:** pin exact SHA, capture the Application consumer source hash, record configured issuer/audience/resource-client ID and execute signed-JWT positive/negative suites.

**Candidate B:** introduce alternative OIDC implementation only behind the same trusted `CurrentPrincipal` boundary; do not modify generic Identity values or Helpdesk/Facility authorization logic. If `iss` or `sub` changes, require reviewed linking/migration and audit continuity.

**Comparison:** run identical fixtures/test semantics, compare canonical identity continuity, exact authority allowlist, safe error behavior, invalid claim rejection and resource ACL. Count tests, compare immutable consumer diff, and examine revoked/logout session behavior within documented bounds.

**Acceptance constraint:** mock-only second adapter establishes limited conformance, **not** real provider replacement; two real IdPs plus integration/negative evidence are needed for a live swap claim. Failure to preserve security semantics means BLOCKED, not an excuse to loosen Core contracts.

## 9. G4-F08 review blockers and final criteria

| Gate | Required result | Status in this package |
| --- | --- | --- |
| G4-D | Eight Identity docs internally consistent, reviewed | NOT REVIEWED |
| G4-S | Exact-SHA source/contract conformance and dependency checks | NOT VERIFIED |
| G4-C | Contract suite run on baseline and alternative adapter | NOT VERIFIED |
| G4-I | Real OIDC, BFF where applicable, Application ACL, negative tests | NOT VERIFIED |
| G4-R | Helpdesk + Facility reuse without Core modification, independent review | NOT VERIFIED |
| G4 Identity Gate | Authorized sign-off or explicitly qualified conditional decision | NOT REVIEWED |

**P0 blockers:** accepted forged identity/header, JWT validation bypass, role escalation on wrong client, ACL bypass or direct mutating route, misleading history/commit outcome, unprotected cookie BFF state changes, leaking secrets, or claiming provider interchangeability with no test.

## 10. Open decisions and follow-up actions

| ID | Needed owner decision |
| --- | --- |
| AT-OD-01 | Approved current test inventory and exact Gradle selections (class/path discovery) |
| AT-OD-02 | Local disposable real-Keycloak fixture and key lifecycle |
| AT-OD-03 | Portal/BFF candidate implementation and CSRF/session evidence scope |
| AT-OD-04 | API 401/403/404 policy with resource-hiding exceptions |
| AT-OD-05 | Approval of identity linking/migration and alternate IdP target |
| AT-OD-06 | Facility golden-path acceptance and Application-owned ACL fixture |
| AT-OD-07 | Backend route exposure, Gateway bypass and private-network proof |
| AT-OD-08 | Independent reviewer roster and final Gate authority |

**Current document status:** `DRAFT / NOT REVIEWED`. No test, E2E run, live provider swap, or formal approval occurred when authoring these files.

## 11. Pinned source references

- [Canonical Identity contract](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/identity-core.md)
- [Backend Gradle configuration](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/build.gradle)
- [CurrentPrincipal port](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/application/identity/CurrentPrincipal.java)
- [Spring Security adapter](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipal.java)
- [Spring Security configuration](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfig.java)
- [Principal test file](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/test/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipalTest.java)
