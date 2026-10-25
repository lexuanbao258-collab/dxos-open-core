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
| SEC-F05 | `RequestResponse.from()` lacks proposed D1/D2/D3 filtering; potential field disclosure, **not demonstrated D2/D3 leakage** | BL03-T06, BL03-T11 | Inventory and classify actual payload fields before testing response and timeline projections |
| SEC-F06 | History endpoint has no confirmed event visibility policy; unauthorized history visibility | BL03-T04, BL03-T11 | Direct history-policy coverage |
| Cross-cutting | Verified authentication and uncertain commit outcome requirements; not additional Request source findings | BL03-T01, BL03-T12 | Integration and recovery requirements |

Source references: `backend/src/main/java/com/tricore/dxos/request/service/RequestService.java`, `RequestWorkflowService.java`, `dto/RequestResponse.java`, and `dto/RequestHistoryResponse.java`; authorization expectations are documented in `docs/contracts/workflow-authorization.md` and `docs/contracts/workflow-core.md` at the stated baseline.

## 4. Security Test Matrix

Security priority describes the importance of a control; execution readiness describes whether its prerequisites are approved and available. Neither is a test result. Every row remains **NOT EXECUTED** until evidence for that exact case and variant proves execution. HTTP contracts labeled proposed below require review before acceptance.

| Test ID | Scenario | Expected Result | Test Level | Security Priority | Owner | Execution Readiness | Execution Result | Related Finding |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BL03-T01 | Missing, invalid, expired or otherwise untrusted JWT | 401; no protected lookup result or side effect | Security filter integration | P0 | Yến | COVERAGE REVIEW | NOT EXECUTED | Cross-cutting |
| BL03-T02 | Missing exact operation authority | Exact authority required; read authority and altered strings cannot grant transition | Authorization unit + HTTP integration | P0 | Yến | COVERAGE REVIEW | NOT EXECUTED | SEC-F02, SEC-F04 |
| BL03-T03 | Hidden resource IDOR/BOLA | Hidden and nonexistent IDs yield indistinguishable 404 status/body | HTTP + persistence integration | P0 | Yến | BLOCKED | NOT EXECUTED | SEC-F02 |
| BL03-T04 | Unauthorized history access | Verified `workflow:read` plus approved resource/history ACL; visible forbidden HISTORY → 403, hidden HISTORY → 404 | HTTP + persistence integration | P0 | Yến | POLICY OPEN | NOT EXECUTED | SEC-F03, SEC-F06 |
| BL03-T05 | Cross-tenant Request listing | Authoritative tenant/resource ACL filters every row; pagination and counts checked if approved | Repository/service + HTTP integration | P0 | Yến | BLOCKED | NOT EXECUTED | SEC-F01 |
| BL03-T06 | Classified list/detail response projection | Inventory actual payloads and approve D1/D2/D3 field classes; then verify no unauthorized fields or values | DTO contract + HTTP integration | P0 | Yến | POLICY OPEN | NOT EXECUTED | SEC-F05 |
| BL03-T07 | Unauthorized Request confirmation | Wrong actor denied under approved requester/owner/assignee policy; state, version, history and business facts unchanged | Service + persistence integration | P0 | Yến | POLICY OPEN | NOT EXECUTED | SEC-F04 |
| BL03-T08 | Eligibility or ACL grant revoked before write; two independent variants | Each variant's final authoritative write-boundary check prevents unauthorized commit | Transaction/race integration | P0 | Yến | BLOCKED | NOT EXECUTED | SEC-F04 |
| BL03-T09 | Concurrent writes with stale expectedVersion | At most one successful state/history write; loser gets approved version conflict | PostgreSQL concurrency integration | P0 | Yến | BLOCKED | NOT EXECUTED | SEC-F04 |
| BL03-T10 | Authorization backend unavailable | Fail closed; candidate Gateway bridge maps UNAVAILABLE to sanitized 503 | Policy fault + HTTP integration | P0 | Yến | COVERAGE REVIEW | NOT EXECUTED | SEC-F04 |
| BL03-T11 | Protected history and timeline projection | Inventory actual payloads and approve D1/D2/D3 event/field classes; then verify authorized projection | Projection contract + end-to-end | P0 | Yến/Ánh | POLICY OPEN | NOT EXECUTED | SEC-F03, SEC-F05, SEC-F06 |
| BL03-T12 | COMMIT_OUTCOME_UNKNOWN reconciliation | Operation reference is no receipt or idempotency key; authorized reads retain UNKNOWN if unresolved | Fault injection + persistence integration | P0 | Yến/Bảo | BLOCKED | NOT EXECUTED | Cross-cutting |

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

- **Requirement / related finding:** SEC-F03 and SEC-F06; HISTORY requires verified `workflow:read` and separately approved resource/history ACL and event visibility. No additional JWT history authority is assumed.
- **Preconditions / actor and fixture:** Record the original requester, ACL resource owner, and assignee independently for REQ_VISIBLE_DENIED and REQ_HIDDEN. Give the test actor a verified principal and exact `workflow:read`; make REQ_VISIBLE_DENIED visible but history-forbidden by the approved ACL, and keep REQ_HIDDEN hidden. Resource/history and event policy remain **OPEN**.
- **Steps:** Request history for both fixtures through the application endpoint; compare with an allowed control that has verified `workflow:read` and approved resource/history access after policy ratification.
- **Expected HTTP / decision:** Visible-but-forbidden HISTORY → 403/FORBIDDEN; hidden HISTORY → 404/HIDDEN. The response contract is **PROPOSED** pending API approval.
- **Disclosure / side effects:** Neither denial returns events, counts, actor IDs, transition labels, or metadata; reads produce no state/history writes.
- **Level / required evidence:** HTTP and persistence integration; verified authority, distinct requester/owner/assignee fixture facts, resource/history policy decisions, redacted response, authorized control, and no-write evidence. **Dependency:** approved history ACL and event visibility classification, integrated endpoint. **Status:** NOT EXECUTED.

### BL03-T05 — Cross-tenant Request listing

- **Requirement / related finding:** SEC-F01; every returned Request must pass authoritative tenant and resource ACL filtering. If pagination/counts are approved, filter the permitted set before page selection and derived counts.
- **Preconditions / actor and fixture:** USER_A may read REQ_A1 but cannot read REQ_B1; include both tenants and same-tenant resources with different ACL decisions. Current Request list API is unpaginated; pagination and counts remain **PROPOSED**, not a prerequisite for the mandatory list ACL test.
- **Steps:** List as USER_A and USER_B; compare every returned ID and field with each actor's authoritative permitted set. Only for an approved paginated API, add interleaved permitted/denied records and compare pages, ordering, and count metadata against the filtered set.
- **Expected HTTP / decision:** Every included row is tenant- and resource-ACL allowed under the approved list contract. If paging/counts exist, denied rows consume no visible page slot and inflate no count.
- **Disclosure / side effects:** No unauthorized cross-tenant or same-tenant IDs, fields, metadata, or, if supplied, inferred counts; no writes.
- **Level / required evidence:** Repository/service query test plus HTTP integration; fixture ACL, SQL/query or service trace, responses, and expected permitted set. Add page/count evidence only for an approved paginated API. **Dependency:** scoped list design and production ACL provider; page/count assertions additionally depend on an approved pagination contract. **Status:** NOT EXECUTED.

### BL03-T06 — D1/D2/D3 response filtering

- **Requirement / related finding:** SEC-F05; list and detail output must follow an approved classification and authorized projection. The source finding identifies potential disclosure, not confirmed D2/D3 leakage.
- **Preconditions / actor and fixture:** Inventory the actual serialized list and detail payloads, including nested fields and metadata, and obtain an approved D1/D2/D3 classification and visibility rule for every inventoried field. Only then populate classified sample markers and give USER_A resource access with D1 visibility. Inventory, taxonomy, permissions, and DTO mapping remain **OPEN**; the current Request DTO does not prove D2/D3 leakage.
- **Steps:** Capture the actual list and detail payload shapes and approved field map first. Then fetch detail and list as USER_A; inspect serialized JSON, nested objects, metadata, and error bodies against the map. Compare with a separately authorized D2/D3 control only after policy approval.
- **Expected HTTP / decision:** 200 for allowed D1 resource read under the **PROPOSED** contract; classification policy permits D1 and denies unauthorized D2/D3 exposure.
- **Disclosure / side effects:** No unauthorized D2/D3 values **or fields**, including placeholders that reveal sensitive field presence; no writes.
- **Level / required evidence:** DTO contract and HTTP serialization integration; actual list/detail payload inventory, approved field classification and access map, fixture markers, exact redacted payloads, and reviewer comparison. **Dependency:** TRICORE D1/D2/D3 policy and authorized DTO contract. **Status:** NOT EXECUTED.

### BL03-T07 — Unauthorized Request confirmation

- **Requirement / related finding:** SEC-F04; CONFIRM requires exact `workflow:transition` plus the approved resource and actor-specific permission, not merely a valid transition state or a requester/owner/assignee label.
- **Preconditions / actor and fixture:** REQ_A1 is RESOLVED with recorded state, version, history, original requester, ACL resource owner, assignee, and resolution; derive and record those three actors independently. Give USER_B or UNRELATED_A verified `workflow:transition` but deny CONFIRM under the approved resource/actor policy. The authoritative actor sources and facility-acceptance versus close policy are **OPEN**.
- **Steps:** Submit CONFIRM as the wrong actor, then independently read Request and history using an observer with verified `workflow:read` and approved resource/history ACL.
- **Expected HTTP / decision:** FORBIDDEN with **proposed** 403 when visible, or HIDDEN/404 if the actor cannot see the resource; exact result follows the approved policy. No successful CONFIRM response.
- **Disclosure / side effects:** Denial reveals no protected Request facts. State, version, history count/content, and business facts remain unchanged.
- **Level / required evidence:** Service and persistence integration; distinct requester/owner/assignee sources, verified authority, actor/resource policy decision, response, and transactionally consistent before/after Request and history from authorized reads. **Dependency:** ratified CONFIRM actor policy and integrated authorization. **Status:** NOT EXECUTED.

### BL03-T08 — Eligibility or ACL grant revoked before write

- **Requirement / related finding:** SEC-F04; a preliminary allow decision cannot authorize a write after its governing eligibility or ACL grant has been revoked. T08 remains one test ID with two independently specified variants; execute and record each separately.
- **Variant A — Application assignment or business eligibility lost:** TECH_A starts with an approved Application assignment and is eligible for the transition. Pause after preliminary allow, remove the assignment or eligibility in its authoritative Application source, then release. The final write-boundary guard must re-evaluate that business fact and deny the write. Record the Application source, before/after assignment or eligibility, final decision, response, and Request/Workflow state, version, history, and business facts.
- **Variant B — Authoritative ACL grant revoked:** Keep TECH_A's Application assignment and business eligibility intact, but initially grant the applicable resource/action permission in the authoritative ACL source. Pause after preliminary allow, revoke that ACL grant, then release. The final write-boundary guard must use the revoked ACL state and deny the write. Record the ACL source and revision, unchanged Application eligibility, final decision, response, and Request/Workflow state, version, history, and business facts.
- **Expected HTTP / decision:** Each variant independently denies the now-unauthorized operation without a success acknowledgement. The exact 403/409 response and final guard/revocation semantics are **OPEN** pending the approved consistency contract.
- **Disclosure / side effects:** Neither variant returns a protected result or permits an unauthorized commit; no Request/Workflow state, version, history, or business-fact change is attributable to the denied write.
- **Level / required evidence:** Deterministic transaction/race integration for both variants, with separate barrier traces, authoritative revocation proof, final decisions, responses, and before/after state/history. **Dependency:** Bảo's Application and ACL source definitions, final transaction guard, and cross-boundary consistency design. **Status:** Variant A NOT EXECUTED; Variant B NOT EXECUTED.

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

### BL03-T11 — Protected history and timeline projection

- **Requirement / related finding:** SEC-F03, SEC-F05, SEC-F06; history/timeline projections must enforce approved resource/history ACL and per-event/field visibility. Sensitive-data exposure is a risk to test, not a confirmed leak.
- **Preconditions / actor and fixture:** Inventory actual backend history and Portal timeline payloads, including event and nested field shapes, and obtain approved D1/D2/D3 classifications and visibility rules for the inventoried events and fields. Then use classified sample markers on REQ_A1; USER_A has verified `workflow:read` and only the approved resource/history and D1 scope. Inventory, event classification, and Portal timeline mapping are **OPEN**.
- **Steps:** Capture actual history/timeline payload shapes and the approved classification map first. Then read backend history and Portal timeline as USER_A; inspect event titles, status changes, actor IDs, comments, raw metadata, nested data, counts, timestamps, and error paths against that map. Compare against an authorized control after policy approval.
- **Expected HTTP / decision:** Allowed history may return a filtered timeline under a **PROPOSED** API; disallowed history follows T04's 403/404 split. Each included event/field requires an affirmative visibility decision.
- **Disclosure / side effects:** No unauthorized internal/sensitive Workflow or business metadata, even through timeline summaries or indirect response fields; no writes.
- **Level / required evidence:** Projection contract, HTTP, and Portal-to-backend end-to-end review; actual history/timeline payload inventory, approved event/field classification and access map, redacted payload and rendered timeline captures, and policy decisions. **Dependency:** TRICORE event/field policy and Ánh's DTO/timeline contract. **Status:** NOT EXECUTED.

### BL03-T12 — COMMIT_OUTCOME_UNKNOWN reconciliation

- **Requirement / related finding:** Cross-cutting Workflow persistence/recovery contract; an uncertain commit cannot be treated as confirmed failure or success. An operation reference is a correlation aid, neither an idempotency key nor a commit receipt.
- **Preconditions / actor and fixture:** An authorized write on REQ_A1 has a stable operation reference and expectedVersion. Inject a failure at the commit acknowledgement boundary so persistence may have committed or rolled back. Reconciliation identity, API response, and Request/Workflow consistency contract are **OPEN**; the reference alone proves no outcome or replay safety.
- **Steps:** Execute once; record COMMIT_OUTCOME_UNKNOWN; perform independent authoritative state and history reads through a verified `workflow:read` principal with approved resource/history ACL. Exercise committed, uncommitted, and still-ambiguous observations without blindly replaying the original write or inferring an outcome from the reference.
- **Expected HTTP / decision:** No false success or automatic retry. Current Workflow handler represents COMMIT_OUTCOME_UNKNOWN as 500; the integrated response contract remains **OPEN**. Reconciliation reads must pass verified identity and resource/history ACL.
- **Disclosure / side effects:** Reconciliation exposes only authorized data; classify an outcome only when authoritative read evidence resolves it and retain UNKNOWN otherwise. Exactly the original committed write's effects, if any, may appear; no retry-generated duplicate state/history.
- **Level / required evidence:** Commit-boundary fault injection with PostgreSQL and independent authoritative reads; failure location, response, operation reference, authorized reconciliation trace, and final state/history. **Dependency:** Bảo's consistency/recovery design and an integrated persistence fault harness. **Status:** NOT EXECUTED.

## 6. Evidence Record Template

Create one copy per executed test and variant. Empty cells mean evidence has not been gathered; a specification alone is never PASS.

| Field | Record |
| --- | --- |
| Test ID | `BL03-T__` |
| Variant / exact case (if applicable) |  |
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
| G2 — Resource and operation authorization | Authoritative hidden/forbidden, mandatory list tenant/resource ACL, actor checks, and fail-closed outcomes from T03–T05, T07, T10 | **BLOCKED** — production ACL/orchestration and policy approval pending |
| G3 — D1/D2/D3 visibility and protected history | Actual list/detail/history/timeline payload inventory, approved field/event classification, and serialized results from T04, T06, T11 | **OPEN** — payload inventory, field/event policy, and DTO contract pending |
| G4 — Authorized writes, revocation and concurrency | Both T08 final-boundary variants, one-winner version race, and authorized uncertain-commit reconciliation from T07–T09, T12 | **BLOCKED** — transaction guard and consistency/recovery contract pending |
| G5 — Portal-to-backend-to-Generic-Workflow integration | Traceable end-to-end authorized flows and negative cases across the proposed path, with evidence on one exact merged commit | **BLOCKED** — no merged, verified LIVE slice established here |

No gate is approved or evidenced by this document. A separate PR's positive source evidence is a coverage input, not integrated acceptance.

## 8. Ownership and Open Decisions

| Owner | Responsibility |
| --- | --- |
| Bảo | Authoritative ACL source, backend orchestration, final transaction guard, and cross-boundary consistency |
| Ánh | Portal list/detail/timeline requirements and authorized DTO mapping |
| Yến | Security test specifications, evidence matrix, and negative security review |
| TRICORE | Business/security policy ratification and LIVE acceptance |

Decisions still **OPEN**: facility acceptance versus a separate close action; IT close actor; authoritative original requester, ACL resource owner, and assignee sources and CONFIRM policy; approved resource/history ACL and event visibility without a new JWT history authority; actual list/detail/history/timeline payload inventory and D1/D2/D3 field/event classification; authorized list/detail/history/transition API contracts and error bodies, including whether pagination/counts exist; separate Application eligibility and authoritative ACL revocation semantics; and application/Workflow consistency, final guards, and recovery after uncertain commits. The operation reference does not settle the recovery contract. Bảo and Ánh should review the applicable contracts before implementation claims or test acceptance. These items are not approved by assigning owners.

## 9. Review Verdict

**TEST SPECIFICATION PREPARED — NOT VERIFIED.**

This document does not claim new implementation, test execution, merged security integration, Generic Workflow Core authorization defects, LIVE approval, or security sign-off. Every test requires execution evidence on an identified commit and environment before it can support an acceptance gate.
