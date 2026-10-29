---
title: DX-OS Open-Core — Identity Authentication Flow
status: PROPOSED — REVIEW REQUIRED
id: G4-F05
version: 1.0-review-draft
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
owner: TRICORE — to assign
reviewers: Identity-Security / Portal-BFF / Gateway / Application — to confirm
---

# Identity Authentication Flow — Verified Boundary, OIDC and BFF Migration

> **Status semantics:** **OBSERVED** means inspected code/contract at the pinned source commit; **CANDIDATE** means separate in-progress integration work, not assumed merged; **PROPOSED** means team-selected target design, not a deployment claim; **UNVERIFIED** means no fresh execution evidence in this documentation task. The 18 F05 choices are selected authoring directions, not a formal ADR or Gate approval.

## 1. Objective, non-goals and core contract boundary

DX-OS authenticates a caller at a **trusted runtime boundary**, normalizes identity into provider-neutral Core values, then performs **Application-owned resource authorization**. The goal is to keep Helpdesk/Facility consumers unchanged when an OIDC provider is replaced **subject to identity, authority, trust and compatibility conformance**. This is a Spring Boot modular monolith with logically separated capabilities, not four Identity microservices.

**Non-goals:** building a credential store/password verifier in Identity Core; inventing `login()`, `refreshToken()` or `verifyJwt()` public Core methods; accepting HTTP headers as proof of identity; asserting BFF deployment, refresh rotation, logout or CSRF controls already work; mapping HTTP statuses in Core; placing Helpdesk ownership decisions inside Identity.

**Distinct contracts:** (1) Core Java values (`AuthenticatedPrincipal`, `Authority`, `AuthenticationContext`, `IdentityException`), (2) the Application-owned `CurrentPrincipal.requireVerified()` port, (3) the Spring Security OIDC infrastructure adapter, and (4) independently versioned HTTP/session contracts of a Portal/BFF or Gateway. Identity Core does **not** directly verify JWTs.

## 2. Source-evidenced implementation snapshot

| Boundary | Evidence at pinned SHA | Status and constraint |
| --- | --- | --- |
| `SecurityConfig.java` | `/api/v1/health` permitted, other requests authenticated; OAuth2 Resource Server JWT; stateless session, request cache off; `.csrf(...disable())`; customized `JwtDecoder` with issuer discovery/JWKS and validators | **OBSERVED backend**. Disabling CSRF for stateless Bearer API is not permission to disable it in a cookie-authenticated BFF. |
| `CurrentPrincipal.java` | Application-owned `AuthenticatedPrincipal requireVerified()` port | **OBSERVED**. It requires trusted provenance; constructing the record is not verification. |
| `SpringSecurityCurrentPrincipal.java` | Requires authenticated `JwtAuthenticationToken`, nonblank issuer and subject; canonicalizes `(iss, sub)` and maps resource-client-scoped roles | **OBSERVED**. Accepts only four exact Workflow authority strings, ignores unknown roles. |
| `docs/contracts/identity-core.md` | Provider-independent values, sanitized errors, token validation and historical test/evidence descriptions | **OBSERVED documentation**, not rerun test proof. |
| Browser / Portal / BFF OIDC client | Authorization Code + PKCE S256, server-side token handling and secured cookie session chosen as target | **CANDIDATE/PROPOSED**. The four inspected sources do not establish an integrated BFF. |

### Specific backend JWT validation evidence

`SecurityConfig` creates a Nimbus decoder using issuer discovery and a JWT processor; `JwtValidators.createDefaultWithIssuer(issuer)` is combined with `AudienceValidator`, `AccessTokenTypeValidator`, `SubjectValidator`, and `ExpirationValidator`. A raw signed JWT subject-type guard refuses a non-string raw `sub` before Nimbus string coercion. The Identity contract describes issuer/JWKS signature, timestamps including expiry and `nbf` as applicable, audience, and **JWT payload claim** `typ == "Bearer"`. Do not confuse that claim with the JOSE header `typ` (the recorded local Keycloak example used `JWT` in the JOSE header). The access-token response's `token_type` is another distinct value.

The current backend filter chain authenticates Bearer JWTs, not browser cookie sessions. A session-bearing BFF should **not** forward an unchecked identity header to this API. It must present a valid, audience-appropriate Bearer credential to the independently validating backend, unless a separately reviewed, authenticated service-to-service trust protocol replaces this boundary.

## 3. Threat, actor and trust boundary inventory

| Actor or component | Trust assumption | Security obligation |
| --- | --- | --- |
| Browser | Untrusted client; scripts/extensions and requests can be compromised | Never be sole source of verified identity; no exposed refresh tokens in selected BFF design |
| Portal/BFF (**target**) | Confidential or appropriately protected OAuth client; session custodian | State, PKCE, exact redirect URI, secure server-side tokens, session integrity, CSRF on cookie-authenticated mutations |
| OIDC IdP (Keycloak baseline) | External issuer whose signature/metadata are verified | Issue suitable signed claims; keys can rotate; downtime must fail closed |
| Backend resource server (**observed**) | Independently verifies Bearer token | Issuer, signature/JWKS, audience, expiry/not-before, payload token type and subject validation |
| CurrentPrincipal adapter (**observed**) | Consumes verified Spring Security context | Reject missing/unverified JWT auth; canonical subject and allowlisted authorities only |
| Application authorization | Receives verified principal and contract values | Exact capability authority plus ticket/facility resource ownership/state/eligibility check |

**Never trust:** `X-User`, `X-Role`, `X-Authorities`, arbitrary decoded JWT payload, a caller-created `AuthenticationContext`, `AuthenticatedPrincipal(authenticated=true)`, or BFF identity claims without an authenticated handoff.

## 4. Flow A — Target browser login (PROPOSED; not present as proven end-to-end behavior)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Browser
    participant BFF as Portal BFF (proposed)
    participant IdP as OIDC IdP
    User->>Browser: Click Sign in
    Browser->>BFF: GET /login (illustrative, not contracted endpoint)
    BFF->>BFF: Create state, PKCE verifier/challenge, nonce if required
    BFF-->>Browser: Redirect with authorization request and exact redirect_uri
    Browser->>IdP: Authorization request (code + S256)
    IdP-->>Browser: Redirect with code and state
    Browser->>BFF: Callback with code/state
    BFF->>BFF: Verify state, callback binding and expiry
    BFF->>IdP: Exchange code using PKCE verifier and client authentication as applicable
    IdP-->>BFF: Token response
    BFF->>BFF: Validate token response, establish server-side session
    BFF-->>Browser: HttpOnly Secure session cookie; redirect to Portal
```

**MUST in target design:** authorization code flow with PKCE S256, unpredictable per-login `state`, exact registered redirect URI (no wildcard redirect), correlation and one-time use of callback state/verifier, secure client authentication where applicable; validate OIDC ID token and nonce according to the chosen client type and flow. Do not confuse ID-token validation at the BFF with access-token validation at the backend. Cookie `HttpOnly`, `Secure`, narrowly scoped domain/path and an appropriate `SameSite` setting must be chosen with deployment redirect behavior and CSRF threat model in mind. The BFF must not place tokens in query strings, browser storage, logs or HTML.

## 5. Flow B — Authenticated API request (backend boundary OBSERVED; BFF relay PROPOSED)

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant BFF as Portal BFF (proposed)
    participant API as Spring Security resource server (observed)
    participant Port as CurrentPrincipal adapter (observed)
    participant App as Authorized Application use case
    Browser->>BFF: Request with server-managed session cookie
    BFF->>BFF: Verify session; enforce CSRF for mutation
    BFF->>API: Authorization Bearer access token (proposed relay)
    API->>API: Verify signed JWT + issuer/audience/time/payload typ
    alt invalid or missing Bearer token
      API-->>BFF: Safe HTTP 401
    else verified JWT
      API->>Port: requireVerified()
      Port->>Port: Canonicalize issuer+subject, exact role allowlist
      Port-->>App: AuthenticatedPrincipal (neutral Core value)
      App->>App: Check capability authority and resource ACL/state
      alt not eligible
        App-->>API: Denied response per Application contract
      else authorized
        App-->>API: Use-case result
      end
      API-->>BFF: Safe HTTP response
    end
    BFF-->>Browser: Response without leaked access token
```

The target BFF session **does not** change the observed backend's stateless Bearer resource-server contract. The BFF's route, token transport, CSRF and session implementation need their own HTTP specification and integration tests; the diagram uses illustrative calls only. Invalid authentication generally produces 401 at the backend entry point; a valid principal missing permission requires a separately tested 403/404 policy to avoid leaking resource existence. Do not claim all Application authorization responses have already been fixed.

## 6. Flow C — Failure, refresh and logout (PROPOSED except backend 401 boundary)

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant BFF as Portal BFF (proposed)
    participant IdP as OIDC IdP
    participant API as Backend API
    Browser->>BFF: Mutating request with cookie
    alt missing/invalid CSRF token
      BFF-->>Browser: Reject mutation; no backend call
    else session expired/revoked
      BFF-->>Browser: Session invalid; require new login
    else valid session, access token expired
      BFF->>IdP: Attempt server-side refresh (if supported)
      alt refresh denied/replayed
        BFF->>BFF: Invalidate local session and tokens
        BFF-->>Browser: Authentication required
      else refresh accepted
        BFF->>BFF: Rotate/stash tokens safely; update session
        BFF->>API: Fresh Bearer token on authorized request
        API-->>BFF: Outcome after independent JWT checks
      end
    end
    Browser->>BFF: Logout (explicit, CSRF-protected as applicable)
    BFF->>BFF: Invalidate local session, cookie and stored tokens
    opt IdP supports compliant logout/revocation
      BFF->>IdP: Perform supported logout/revocation action
    end
    BFF-->>Browser: Logged out; prior session reuse rejected
```

**Selected target, not observed behavior:** server-side refresh handling with applicable rotation/revocation, failed-refresh fail-closed behavior, server-side session expiry, session ID rotation at authentication and privilege change, idle/absolute lifetimes, logout local invalidation, supported IdP logout, and rejection of stale session IDs. Browser cookie-authenticated POST/PUT/PATCH/DELETE must have actual CSRF protection; OAuth `state` does **not** replace application request CSRF controls. `SameSite` is defense in depth, not necessarily a complete CSRF defense.

## 7. Token verification and identity mapping

**Observed adapter canonicalization:**

```text
canonicalSubjectId = "oidc:" + base64url_no_padding(strict_utf8(issuer))
                     + "." + base64url_no_padding(strict_utf8(subject))
```

Issuer and subject are not trimmed, lowercased, email-matched or normalized. An IdP swap that changes either can change the canonical identity. **Mandatory migration decision:** explicit mapping of old to new accounts and resource ownership, authority policy, audit provenance, conflict detection and rollback. Do not silently join accounts on email/display name.

**Observed authority mapping:** from `resource_access.<configured_resourceClientId>.roles`, preserve only `workflow:create`, `workflow:activate`, `workflow:transition`, `workflow:read`. Missing, malformed, unknown, wrong-case or wrong-client roles do not add privileges. The adapter validates that role items are strings; malformed roles result in no mapped Workflow authorities. The Application then enforces ticket- or facility-specific eligibility separately. Identity Core's `Authority.satisfies` is an exact case-sensitive string equality, not wildcard or hierarchy.

## 8. Safe errors and protocol boundaries

| Condition | Owner and expected treatment | Evidence state |
| --- | --- | --- |
| No/invalid Bearer token at backend | Security entry point denies with safe HTTP 401 / `AUTHENTICATION_REQUIRED` per existing Identity contract | **OBSERVED documented**; not retested here |
| Signed token wrong issuer, audience, expiry or payload `typ` | Backend token decoder must reject before authenticated principal creation | **OBSERVED validator design**; fresh negative tests needed |
| Missing/not a verified JWT in `CurrentPrincipal` | Adapter fails closed (currently `IllegalStateException` for selected cases) | **OBSERVED**; normalized cross-boundary mapping unresolved |
| Valid identity, missing capability authority | Authorized use case denies | **PROPOSED integration contract**; exact HTTP 403/404 policy to review |
| Resource not owned or unavailable to caller | Application resource policy denies without enumeration leak | **PROPOSED**; preserve 403/404 policy per contract |
| Invalid BFF state, PKCE, cookie, CSRF or logout session | BFF rejects and avoids forwarding privileged request | **PROPOSED**, no current BFF PASS |
| IdP unreachable / refresh revoked | Fail closed; sanitized user-facing message and audit; no token leakage | **PROPOSED** |

Current normalized Identity capability codes are `AUTHENTICATION_FAILED`, `INVALID_AUTHENTICATION_CONTEXT`, `AUTHENTICATION_EXPIRED`, `ACCESS_DENIED`, `IDENTITY_NOT_FOUND`, `IDENTITY_PROVIDER_UNAVAILABLE`. No dedicated missing-context code is currently established; do not introduce one by documentation fiat. HTTP status mapping is not defined by the Core value types.

## 9. OIDC provider substitution and compatibility protocol

**Scope of swap:** an alternate standards-compatible OIDC provider and BFF authorization client must preserve the *Application-facing* trusted principal and capability authority meanings. Same OAuth protocol alone is insufficient to guarantee equivalence of claim shape, scopes, roles, consent, identity persistence, sign-out or session behavior.

| Compatibility dimension | Baseline | Candidate acceptance condition |
| --- | --- | --- |
| Discovery and JWKS | Issuer/JWKS in decoder | Metadata, signature, key rotation and failure tested in actual IdP |
| Audience and token type | Configured audience and strict payload token type | Correct API target, correct access token shape, invalid tokens rejected |
| Principal continuity | Canonical `(iss, sub)` | Identity migration explicitly reviewed or stable mapping demonstrated |
| Role mapping | Resource-client-scoped exact allowlist | Claims normalized to the same precise authorities; extra roles not escalated |
| Port boundary | `CurrentPrincipal.requireVerified()` | No Application business-code edits; adapter/composition/config changes isolated |
| Session security | BFF target only | Login, refresh, cookies, CSRF, logout tests run under candidate provider |
| Error handling | Sanitized normalized Identity types | No provider-specific SDK exception/token leaks across contract |

**Demo steps:** (D0) pin exact git SHA, JDK/dependency/config (redacted), user/resource fixtures and Application code checksum; (D1) execute baseline positive/negative backend tests; (D2) introduce alternate provider adapter/config and approved identity migration, record diff and verify zero business/domain logic edits; (D3) rerun identical authority, malformed-token, issuer/audience/expiry, forged-header and resource ACL tests; (D4) exercise real login/callback/session/CSRF/refresh/logout on both providers; (D5) record JUnit evidence, screenshots/traces with secrets removed and independent reviewer disposition. If semantic compatibility is unachievable, mark **BLOCKED / requires ADR**, not PASS.

A mock decoder/unit fixture proves code behavior, **not live IdP replacement**. A JWT accepted by the backend does not automatically prove BFF cookies/CSRF/session/logout work.

## 10. Acceptance matrix: claim → test → evidence → reviewer

| ID | Claim | Required test / failure oracle | Evidence status |
| --- | --- | --- | --- |
| ID-AF-01 | Valid backend access token produces verified principal | Real discovery/JWKS signed JWT, stable `(iss,sub)` and allowed roles | NOT VERIFIED in this session |
| ID-AF-02 | Wrong signature/issuer/audience/time/type rejected | Individually forged, wrong issuer, aud, expired, future `nbf`, wrong payload `typ` | NOT VERIFIED in this session |
| ID-AF-03 | No forged header can authenticate | Anonymous Bearer request with `X-User`, `X-Role`, `X-Authorities` | NOT VERIFIED in this session |
| ID-AF-04 | Authority allowlist is exact | Unknown, wrong-case, wrong-client, malformed role structure | NOT VERIFIED in this session |
| ID-AF-05 | Resource ACL remains effective | Valid token with capability role but unauthorized ticket/facility resource | NOT VERIFIED in this session |
| ID-AF-06 | OIDC login resists CSRF/code injection | Wrong/replayed state, PKCE mismatch, redirect mismatch, nonce where applicable | PROPOSED / NOT VERIFIED |
| ID-AF-07 | Session cookies and mutations are protected | Cookie flags, lifecycle, CSRF-negative POST/PUT, session fixation | PROPOSED / NOT VERIFIED |
| ID-AF-08 | Refresh/logout fail closed | Revoked refresh, rotation/reuse, logout and stale session rejection | PROPOSED / NOT VERIFIED |
| ID-AF-09 | IdP substitution preserves Core semantics | Baseline/candidate same tests, account migration, no Application code change | PROPOSED / NOT VERIFIED |
| ID-AF-10 | Evidence is reproducible | SHA + command + results + masked environment + reviewer | NOT VERIFIED |

Tests mentioned historically in `docs/contracts/identity-core.md` are descriptions of prior evidence, **not test results generated or revalidated by this documentation pass**. Test identifiers here are *review requirements*, not invented existing tests.

## 11. Risks, dependencies and open decisions

| ID | Risk / question | Required owner disposition |
| --- | --- | --- |
| AF-OD-01 | BFF candidate has no independently verified integrated runtime status in inspected source set | Portal/BFF owner to pin actual PR HEAD/merge evidence |
| AF-OD-02 | Secure BFF token/session store and cryptographic key lifecycle undefined | Security + Deployment review |
| AF-OD-03 | CSRF/session cookie behavior across actual domains/HTTPS ingress | BFF/Ingress E2E review |
| AF-OD-04 | Refresh rotation, revocation, browser logout vs IdP logout variants | Identity/Security review |
| AF-OD-05 | Provider migration and canonical identity continuity across issuer change | Identity + Data ownership review |
| AF-OD-06 | Adapter `IllegalStateException` vs normalized `IdentityException` mapping | Identity/Gateway consumer contract review |
| AF-OD-07 | Resource-level 403/404 decision, audit redaction and enumeration | Application/Gateway review |
| AF-OD-08 | Whether additional IdP tenants/realms/audiences are supported | Architecture/security ADR if expanded |

**Explicit exclusions:** no assertion that BFF is merged, that PKCE/refresh/logout/CSRF currently pass, that a second IdP is integrated, that an alternative trust protocol is implemented, or that full OLP Gate criteria are met.

## 12. Independent review and Gate protocol

- **G4-D:** document correctness and selected decisions versus observed source — **NOT REVIEWED**.
- **G4-S:** exact-SHA Java/source and HTTP contract conformance — **NOT VERIFIED**.
- **G4-C:** baseline and alternative IdP/adapter contract tests — **NOT VERIFIED**.
- **G4-I:** real browser/BFF/IdP/backend integration security and failure tests — **NOT VERIFIED**.
- **G4-R:** Helpdesk and Facility authorization reuse plus reviewer acceptance — **NOT VERIFIED**.
- **G4 Gate:** authorized independent decision — **NOT REVIEWED**.

Approval must be evidenced with reviewer, date, scope, source SHA, test commands/artifacts and open blockers. A written diagram or selected design option is not a formal Gate result.

## 13. Pinned source references

- [`SecurityConfig.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfig.java)
- [`CurrentPrincipal.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/application/identity/CurrentPrincipal.java)
- [`SpringSecurityCurrentPrincipal.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SpringSecurityCurrentPrincipal.java)
- [`Identity Core contract`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/identity-core.md)

**Document lifecycle: DRAFT / PROPOSED / NOT INDEPENDENTLY REVIEWED.**
