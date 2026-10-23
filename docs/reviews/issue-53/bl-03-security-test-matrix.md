# BL-03 — Security Test Matrix v0.1

| Metadata | Value |
| --- | --- |
| Project | DX-OS Open-Core — TRICORE |
| Reviewer / Security Test Owner | Yến |
| Status | PROPOSED — Pending Bảo/Ánh review |
| Source baseline | `origin/main` at `e56b8f2b3e540b84e01a6b92289bc79161b29baf` |
| Related | Issue #53; PR #33, #46, #51, #54, #60 |
| Remediation proposal | Secure Vertical Slice over Authorized Application Orchestration |
| Execution status | **NOT EXECUTED** |

## 1. Purpose and Scope

BL-03 specifies negative security tests for the fixed-lifecycle **Request prototype**: verified identity, exact operation authority, resource and tenant authorization, field and history visibility, protected writes, and failure handling. It is a review and acceptance specification, not an execution report. The target conceptual path is:

`Portal → Identity/Gateway → Authorized Application Orchestration → Generic Workflow Core → Authorized Response DTO`

This integration is **PROPOSED**, not currently LIVE-approved. Generic Workflow Core is not the subject of the Request source findings. Separate PR evidence for verified JWT-to-canonical-principal mapping, exact operation authority, and a Workflow HTTP error bridge (HIDDEN → 404, FORBIDDEN → 403, UNAVAILABLE → sanitized 503) may inform future coverage review; those branches are not treated here as one merged, verified LIVE implementation.

At the inspected baseline, `RequestService.list()` uses unscoped `repository.findAll()` and `get()` maps an ID lookup directly to a response. `RequestWorkflowService` loads Request records for assign/start/resolve/confirm/close without an observed actor-specific authorization check, and `history()` reads history without an observed history-specific ACL guard. `RequestResponse.from()` maps the current Request fields without proposed D1/D2/D3 filtering. The current DTO does **not** establish that D2/D3 leakage has occurred. The proposed tests must prove or disprove the integrated behavior when an authorized application path exists.

## 2. Security Test Fixtures

All identifiers below name **proposed isolated test fixtures**, not existing database records or approved role policy. Create them only in a controlled test environment after the authoritative ACL and API contracts are agreed.

| Fixture | Proposed meaning |
| --- | --- |
| USER_A | Verified requester in Tenant A |
| USER_B | Verified requester in Tenant B |
| TECH_A | Assigned technician for the applicable fixture |
| MANAGER_A | Actor with explicitly approved permissions for the applicable operation |
| UNRELATED_A | Authenticated actor without access to the resource |
| REQ_A1 | Tenant A Request resource |
| REQ_B1 | Tenant B Request resource |
| REQ_HIDDEN | Existing Request hidden from the test principal |
| REQ_VISIBLE_DENIED | Visible Request for which the test operation is forbidden |
| REQ_NONEXISTENT | ID absent from authoritative persistence |
| D1_SAMPLE | Authorized shareable test data |
| D2_SAMPLE | Internal test data |
| D3_SAMPLE | Sensitive test data |

Fixture invariants:

- Authentication does not imply resource or operation authorization. A business role is not identical to a JWT authority.
- Original requester, ACL resource owner, and assignee are distinct concepts; fixture setup must record each independently.
- `workflow:read` does not imply `workflow:transition`. D1 Shareable is not public without the resource ACL.
- Use isolated tenants, deterministic IDs and data markers, and cleanup confined to the test environment. Capture authoritative pre- and post-state without exposing protected values in shared logs.

## 3. Finding-to-Test Traceability

| Finding | Source evidence and risk | Test IDs | Coverage relationship |
| --- | --- | --- | --- |
| SEC-F01 | `RequestService.list()` calls `repository.findAll()`; cross-resource disclosure | BL03-T05 | Direct source finding |
| SEC-F02 | `RequestService.get()` loads by ID without observed resource authorization; IDOR/BOLA | BL03-T02, BL03-T03 | T03 directly tests hidden ID lookup; T02 tests the related operation-authority gate |
| SEC-F03 | `RequestWorkflowService.history()` has no observed history-specific ACL guard; unauthorized history access | BL03-T04, BL03-T11 | Direct history access and protected timeline coverage |
| SEC-F04 | Request assign/start/resolve/confirm/close paths lack observed actor-specific authorization; unauthorized business actions | BL03-T02, BL03-T07, BL03-T08, BL03-T09, BL03-T10 | T07–T08 directly test actor authorization; T02, T09–T10 are related gate/failure controls |
| SEC-F05 | `RequestResponse.from()` lacks proposed D1/D2/D3 filtering; potential field disclosure, **not demonstrated D2/D3 leakage** | BL03-T06, BL03-T11 | Response filtering and related timeline projection |
| SEC-F06 | History endpoint has no confirmed event visibility policy; unauthorized history visibility | BL03-T04, BL03-T11 | Direct history-policy coverage |
| Cross-cutting | Verified authentication and uncertain commit outcome requirements; not additional Request source findings | BL03-T01, BL03-T12 | Integration and recovery requirements |

Source references: `backend/src/main/java/com/tricore/dxos/request/service/RequestService.java`, `RequestWorkflowService.java`, `dto/RequestResponse.java`, and `dto/RequestHistoryResponse.java`; authorization expectations are documented in `docs/contracts/workflow-authorization.md` and `docs/contracts/workflow-core.md` at the stated baseline.

## 4. Security Test Matrix

Initial status describes specification readiness or dependency, **not a test result**. Every row remains **NOT EXECUTED** until an evidence record proves execution. HTTP contracts labeled proposed below require review before acceptance.

| Test ID | Scenario | Expected Result | Test Level | Priority | Owner | Initial Status | Related Finding |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BL03-T01 | Missing, invalid, expired or otherwise untrusted JWT | 401; no protected lookup result or side effect | Security filter integration | P0 | Yến | COVERAGE REVIEW | Cross-cutting |
| BL03-T02 | Missing exact operation authority | Exact authority required; read authority and altered strings cannot grant transition | Authorization unit + HTTP integration | P0 | Yến | COVERAGE REVIEW | SEC-F02, SEC-F04 |
| BL03-T03 | Hidden resource IDOR/BOLA | Hidden and nonexistent IDs yield indistinguishable 404 status/body | HTTP + persistence integration | P0 | Yến | BLOCKED | SEC-F02 |
| BL03-T04 | Unauthorized history access | Visible forbidden HISTORY → 403; hidden HISTORY → 404 | HTTP + persistence integration | P0 | Yến | POLICY OPEN | SEC-F03, SEC-F06 |
| BL03-T05 | Cross-tenant Request listing | ACL filters rows before pagination and counts | Repository/service + HTTP integration | P0 | Yến | BLOCKED | SEC-F01 |
| BL03-T06 | D1/D2/D3 response filtering | D1-authorized consumer receives no unauthorized D2/D3 fields or values | DTO contract + HTTP integration | P0 | Yến | POLICY OPEN | SEC-F05 |
| BL03-T07 | Unauthorized Request confirmation | Wrong actor denied; state, version, history and business facts unchanged | Service + persistence integration | P0 | Yến | POLICY OPEN | SEC-F04 |
| BL03-T08 | Assignment revoked before write | Final authoritative write-boundary check prevents unauthorized commit | Transaction/race integration | P0 | Yến | BLOCKED | SEC-F04 |
| BL03-T09 | Concurrent writes with stale expectedVersion | At most one successful state/history write; loser gets approved version conflict | PostgreSQL concurrency integration | P0 | Yến | BLOCKED | SEC-F04 |
| BL03-T10 | Authorization backend unavailable | Fail closed; candidate Gateway bridge maps UNAVAILABLE to sanitized 503 | Policy fault + HTTP integration | P0 | Yến | COVERAGE REVIEW | SEC-F04 |
| BL03-T11 | Protected history and timeline data leakage | No unauthorized internal/sensitive Workflow or business metadata | Projection contract + end-to-end | P0 | Yến/Ánh | POLICY OPEN | SEC-F03, SEC-F05, SEC-F06 |
| BL03-T12 | COMMIT_OUTCOME_UNKNOWN reconciliation | No blind retry or false success; authorized reconciliation retains UNKNOWN if ambiguous | Fault injection + persistence integration | P0 | Yến/Bảo | BLOCKED | Cross-cutting |

## 5. Detailed Test Specifications

### BL03-T01 — Untrusted JWT

- **Requirement / related finding:** A missing, invalid, expired, or otherwise untrusted JWT cannot establish a verified principal; cross-cutting identity gate.
- **Preconditions / actor and fixture:** Protected Request and history routes are connected to verified Identity. Send no token, malformed token, expired token, and a token failing configured issuer/audience/signature trust checks; target REQ_A1. Do not log token contents.
- **Steps:** Invoke list, detail, history, and a write for each token condition; compare authoritative Request and history snapshots before and after.
- **Expected HTTP / decision:** 401 for every invalid-authentication case. Authentication stops before a resource authorization decision; no caller-supplied actor header substitutes for verification.
- **Disclosure / side effects:** No protected lookup result, existence detail, Request mutation, version change, or history insertion.
- **Level / required evidence:** Security filter and HTTP integration; response status/body, redacted filter-chain trace, and before/after authoritative state/history. **Dependency:** merged Identity-to-Request routing and agreed OIDC test configuration; separate PR evidence requires coverage review. **Status:** NOT EXECUTED.

### BL03-T02 — Exact operation authority

- **Requirement / related finding:** SEC-F02 and SEC-F04; operation authority is exact and separate from resource permission.
- **Preconditions / actor and fixture:** USER_A has a policy-visible REQ_A1 and `workflow:read` but no `workflow:transition`; create isolated variants carrying wildcard, case-changed, or whitespace-altered authority strings. The approved Request-to-Workflow operation mapping is required.
- **Steps:** Attempt an authorized read and a transition with each authority set; repeat with exact transition authority while resource policy denies the action.
- **Expected HTTP / decision:** Read may pass its own ACL; transition is FORBIDDEN and **proposed** 403 when exact authority is absent or resource policy denies it. `workflow:read` never grants `workflow:transition`; wildcard/case/whitespace substitutes fail. Hidden-resource precedence remains 404 under the approved bridge.
- **Disclosure / side effects:** Denial returns no protected transition result and makes no Request/Workflow state, version, history, or business-fact change.
- **Level / required evidence:** Authorization unit cases plus Request HTTP integration; exact input authority set, policy decision, sanitized response, and before/after state/history. **Dependency:** integrated verified principal, exact authority mapping, resource policy, and approved HTTP contract. **Status:** NOT EXECUTED.

### BL03-T03 — Hidden resource IDOR/BOLA

- **Requirement / related finding:** SEC-F02; ID possession must not disclose a hidden Request or its existence.
- **Preconditions / actor and fixture:** UNRELATED_A is authenticated; REQ_HIDDEN exists but is hidden from that actor; REQ_NONEXISTENT is absent. Both IDs must use the same request shape.
- **Steps:** Fetch detail for each ID through the authorized application endpoint; compare status and complete normalized response body, then inspect protected-read and audit evidence.
- **Expected HTTP / decision:** Both return indistinguishable 404 status/body. Policy returns HIDDEN for hidden and nonexistent resources; internal lookup may support the policy but cannot expose a protected result.
- **Disclosure / side effects:** No title, fields, existence marker, history, or tenant signal is returned; no state/history writes.
- **Level / required evidence:** HTTP plus authoritative persistence integration; paired redacted responses, policy decisions, and no-write evidence. **Dependency:** production ACL provider, hidden/nonexistent response bridge, and Request orchestration. **Status:** NOT EXECUTED.

### BL03-T04 — Unauthorized history access

- **Requirement / related finding:** SEC-F03 and SEC-F06; HISTORY needs resource and event visibility authorization.
- **Preconditions / actor and fixture:** An authenticated actor has REQ_VISIBLE_DENIED visible but lacks HISTORY permission; REQ_HIDDEN is hidden. The precise history operation permission and event policy are **OPEN**.
- **Steps:** Request history for both fixtures through the application endpoint; compare with an allowed history control after policy ratification.
- **Expected HTTP / decision:** Visible-but-forbidden HISTORY → 403/FORBIDDEN; hidden HISTORY → 404/HIDDEN. The response contract is **PROPOSED** pending API approval.
- **Disclosure / side effects:** Neither denial returns events, counts, actor IDs, transition labels, or metadata; reads produce no state/history writes.
- **Level / required evidence:** HTTP and persistence integration; policy fixture, redacted response, authorized control, and no-write evidence. **Dependency:** approved history ACL and event visibility classification, integrated endpoint. **Status:** NOT EXECUTED.

### BL03-T05 — Cross-tenant Request listing

- **Requirement / related finding:** SEC-F01; list visibility comes from authoritative tenant/resource ACL before pagination and response counts.
- **Preconditions / actor and fixture:** USER_A may read REQ_A1 but cannot read REQ_B1; include enough interleaved Tenant A/B records to expose post-pagination filtering. Current Request list API is unpaginated; paging/count behavior is a **PROPOSED** future API requirement.
- **Steps:** List as USER_A across page boundaries or the approved equivalent; compare returned IDs, page size, total/count metadata, and ordering against an authoritative permitted-ID set. Repeat as USER_B.
- **Expected HTTP / decision:** Authenticated successful list under approved contract; every included row is ACL-allowed, with filtering before limit/offset and counts. A denied row cannot consume a visible page slot or inflate a count.
- **Disclosure / side effects:** No cross-tenant IDs, fields, metadata, or inferred counts; no writes.
- **Level / required evidence:** Repository/service query test plus HTTP integration; fixture ACL, SQL/query or service trace, full paged responses and expected permitted set. **Dependency:** scoped list design, pagination/count contract, and production ACL provider. **Status:** NOT EXECUTED.

### BL03-T06 — D1/D2/D3 response filtering

- **Requirement / related finding:** SEC-F05; output is an authorized projection of classified data, not a direct entity dump.
- **Preconditions / actor and fixture:** Populate proposed D1_SAMPLE, D2_SAMPLE, and D3_SAMPLE in a future classified response fixture; USER_A has resource access and only D1 visibility. Field taxonomy, permissions, and DTO mapping are **OPEN**; the current Request DTO does not prove D2/D3 leakage.
- **Steps:** Fetch detail and any approved list projection as USER_A; inspect serialized JSON, nested objects, metadata, error bodies, and logs. Compare with a separately authorized D2/D3 control only after policy approval.
- **Expected HTTP / decision:** 200 for allowed D1 resource read under the **PROPOSED** contract; classification policy permits D1 and denies unauthorized D2/D3 exposure.
- **Disclosure / side effects:** No unauthorized D2/D3 values **or fields**, including placeholders that reveal sensitive field presence; no writes.
- **Level / required evidence:** DTO contract and HTTP serialization integration; classification map, fixture markers, exact redacted payloads, and reviewer comparison. **Dependency:** TRICORE D1/D2/D3 policy and authorized DTO contract. **Status:** NOT EXECUTED.

### BL03-T07 — Unauthorized Request confirmation

- **Requirement / related finding:** SEC-F04; CONFIRM requires the approved actor-specific permission, not merely a valid transition state.
- **Preconditions / actor and fixture:** REQ_A1 is RESOLVED with recorded state, version, history, requester, owner, assignee, and resolution. Use USER_B or UNRELATED_A as a wrong actor. The authoritative requester and facility-acceptance versus close policy are **OPEN**.
- **Steps:** Submit CONFIRM as the wrong actor, then independently read Request and history using an authorized observer.
- **Expected HTTP / decision:** FORBIDDEN with **proposed** 403 when visible, or HIDDEN/404 if the actor cannot see the resource; exact result follows the approved policy. No successful CONFIRM response.
- **Disclosure / side effects:** Denial reveals no protected Request facts. State, version, history count/content, and business facts remain unchanged.
- **Level / required evidence:** Service and persistence integration; actor/policy decision, response, and transactionally consistent before/after Request and history. **Dependency:** ratified CONFIRM actor policy and integrated authorization. **Status:** NOT EXECUTED.

### BL03-T08 — Assignment revoked before write

- **Requirement / related finding:** SEC-F04; authorization must survive the gap between an initial check and the final authoritative write boundary.
- **Preconditions / actor and fixture:** TECH_A initially has an applicable assignment on REQ_A1. A controlled barrier pauses the transition after its preliminary allow decision; revoke the authoritative assignment before release. The final guard mechanism and revocation semantics are **OPEN**.
- **Steps:** Start the transition, pause, revoke assignment in the authoritative ACL source, release the operation, then inspect Request, Workflow state/history, and authorization revision evidence.
- **Expected HTTP / decision:** Final check denies the now-unauthorized operation; exact 403/409 response is **OPEN** pending the consistency contract, with no success acknowledgement.
- **Disclosure / side effects:** No protected result or unauthorized commit; no Request/Workflow state, version, history, or business-fact change attributable to the denied write.
- **Level / required evidence:** Transaction/race integration against authoritative ACL and persistence; deterministic barrier trace, revocation proof, final decision, response, and before/after state/history. **Dependency:** Bảo's final transaction guard and cross-boundary consistency design. **Status:** NOT EXECUTED.

### BL03-T09 — Concurrent stale expectedVersion writes

- **Requirement / related finding:** SEC-F04 related write-integrity control; concurrent transitions must preserve one authoritative outcome.
- **Preconditions / actor and fixture:** Two separately authorized operations target the same eligible REQ_A1/Workflow instance and submit the same current expectedVersion. Version-bearing application API and Request/Workflow consistency behavior are **OPEN**.
- **Steps:** Synchronize two writes at the persistence boundary; let them race, then independently read authoritative state, version, history, and business facts.
- **Expected HTTP / decision:** Both callers pass their own authorization; at most one write succeeds. The loser receives a version conflict under the approved contract (current Workflow HTTP handler uses 409 for VERSION_CONFLICT, but integrated Request mapping is **OPEN**).
- **Disclosure / side effects:** No duplicate successful state/history transition and no partial Request/Workflow business update; losing operation adds no history row.
- **Level / required evidence:** PostgreSQL concurrency integration; request versions, response pair, transaction/commit traces, and final state/history counts. **Dependency:** integrated version contract and consistency design. **Status:** NOT EXECUTED.

### BL03-T10 — Authorization backend unavailable

- **Requirement / related finding:** SEC-F04 related fail-closed control; lack of a trustworthy ACL decision cannot become ALLOWED.
- **Preconditions / actor and fixture:** USER_A has a verified identity; inject timeout, exception, null/unknown result, and unavailable response from the authoritative policy source for REQ_A1. The source authorization seam treats null/provider exceptions as UNAVAILABLE; production bridge coverage needs review.
- **Steps:** Attempt read and write through the integrated endpoint under each failure; restore the backend and run an authorized control.
- **Expected HTTP / decision:** UNAVAILABLE, never ALLOWED. The separate Gateway error-bridge candidate maps UNAVAILABLE to sanitized 503; adoption for this integration is **PROPOSED**.
- **Disclosure / side effects:** No protected payload, provider details, stack trace, or write side effect while authorization is unavailable.
- **Level / required evidence:** Policy fault injection plus HTTP integration; injected fault, sanitized response, decision trace, no-write state/history, and recovery control. **Dependency:** production ACL provider and approved error bridge; separate PR evidence is not merged proof. **Status:** NOT EXECUTED.

### BL03-T11 — Protected history and timeline data leakage

- **Requirement / related finding:** SEC-F03, SEC-F05, SEC-F06; application history/timeline projections must enforce both resource ACL and per-event/field visibility.
- **Preconditions / actor and fixture:** REQ_A1 has D1_SAMPLE and proposed D2_SAMPLE/D3_SAMPLE in business/Workflow metadata; USER_A has only the approved D1/history scope. Event classification and Portal timeline mapping are **OPEN**.
- **Steps:** Read backend history and Portal timeline as USER_A; inspect event titles, status changes, actor IDs, comments, raw metadata, nested data, counts, timestamps, and error paths. Compare against an authorized control after policy approval.
- **Expected HTTP / decision:** Allowed history may return a filtered timeline under a **PROPOSED** API; disallowed history follows T04's 403/404 split. Each included event/field requires an affirmative visibility decision.
- **Disclosure / side effects:** No unauthorized internal/sensitive Workflow or business metadata, even through timeline summaries or indirect response fields; no writes.
- **Level / required evidence:** Projection contract, HTTP, and Portal-to-backend end-to-end review; classification map, redacted payload and rendered timeline captures, and policy decisions. **Dependency:** TRICORE event/field policy and Ánh's DTO/timeline contract. **Status:** NOT EXECUTED.

### BL03-T12 — COMMIT_OUTCOME_UNKNOWN reconciliation

- **Requirement / related finding:** Cross-cutting Workflow persistence/recovery contract; an uncertain commit cannot be treated as confirmed failure or success.
- **Preconditions / actor and fixture:** An authorized write on REQ_A1 has a stable operation reference and expectedVersion. Inject a failure at the commit acknowledgement boundary so persistence may have committed or rolled back. Reconciliation identity, API response, and Request/Workflow consistency contract are **OPEN**.
- **Steps:** Execute once; record COMMIT_OUTCOME_UNKNOWN; perform **authorized** independent reads of authoritative state and history. Exercise committed, uncommitted, and still-ambiguous outcomes without blindly replaying the original write.
- **Expected HTTP / decision:** No false success or automatic retry. Current Workflow handler represents COMMIT_OUTCOME_UNKNOWN as 500; the integrated response contract remains **OPEN**. Reconciliation reads must pass verified identity and resource/history ACL.
- **Disclosure / side effects:** Reconciliation exposes only authorized data; final classification follows authoritative evidence, retaining UNKNOWN when ambiguous. Exactly the original committed write's effects, if any, may appear; no retry-generated duplicate state/history.
- **Level / required evidence:** Commit-boundary fault injection with PostgreSQL and independent authoritative reads; failure location, response, operation reference, authorized reconciliation trace, and final state/history. **Dependency:** Bảo's consistency/recovery design and an integrated persistence fault harness. **Status:** NOT EXECUTED.

## 6. Evidence Record Template

Create one copy per executed test and variant. Empty cells mean evidence has not been gathered; a specification alone is never PASS.

| Field | Record |
| --- | --- |
| Test ID | `BL03-T__` |
| Requirement / finding |  |
| Exact source commit SHA |  |
| Branch / environment |  |
| JDK, PostgreSQL, OIDC configuration (as applicable) |  |
| Exact executed command |  |
| Fixture setup and isolation |  |
| Expected behavior |  |
| Actual behavior |  |
| Result (`PASS` / `FAIL` / `BLOCKED` / `NOT EXECUTED`) | `NOT EXECUTED` |
| Logs, XML, CI link, or code reference |  |
| Reviewer and date |  |
| Known limitations |  |

## 7. Acceptance Gates

| Gate | Acceptance evidence required | Current approval / evidence |
| --- | --- | --- |
| G1 — Verified Identity and exact authority | Real filter-chain authentication failures and canonical-principal/exact-authority cases, including T01–T02 | **OPEN** — separate PR evidence needs integrated coverage review |
| G2 — Resource and operation authorization | Authoritative hidden/forbidden, list ACL, actor checks, and fail-closed outcomes from T03–T05, T07, T10 | **BLOCKED** — production ACL/orchestration and policy approval pending |
| G3 — D1/D2/D3 visibility and protected history | Approved classification plus serialized detail/history/timeline results from T04, T06, T11 | **OPEN** — field/event policy and DTO contract pending |
| G4 — Authorized writes, revocation and concurrency | Final-boundary revocation, one-winner version race, and uncertain-commit reconciliation from T07–T09, T12 | **BLOCKED** — transaction guard and consistency/recovery contract pending |
| G5 — Portal-to-backend-to-Generic-Workflow integration | Traceable end-to-end authorized flows and negative cases across the proposed path, with evidence on one exact merged commit | **BLOCKED** — no merged, verified LIVE slice established here |

No gate is approved or evidenced by this document. A separate PR's positive source evidence is a coverage input, not integrated acceptance.

## 8. Ownership and Open Decisions

| Owner | Responsibility |
| --- | --- |
| Bảo | Authoritative ACL source, backend orchestration, final transaction guard, and cross-boundary consistency |
| Ánh | Portal list/detail/timeline requirements and authorized DTO mapping |
| Yến | Security test specifications, evidence matrix, and negative security review |
| TRICORE | Business/security policy ratification and LIVE acceptance |

Decisions still **OPEN**: facility acceptance versus a separate close action; IT close actor; authoritative requester and assignee sources; D1/D2/D3 classification for history events and fields; authorized list/detail/history/transition API contracts and error bodies; and application/Workflow consistency, revocation, and recovery after uncertain commits. Bảo and Ánh should review the applicable contracts before implementation claims or test acceptance. These items are not approved by assigning owners.

## 9. Review Verdict

**TEST SPECIFICATION PREPARED — NOT VERIFIED.**

This document does not claim new implementation, test execution, merged security integration, Generic Workflow Core authorization defects, LIVE approval, or security sign-off. Every test requires execution evidence on an identified commit and environment before it can support an acceptance gate.
