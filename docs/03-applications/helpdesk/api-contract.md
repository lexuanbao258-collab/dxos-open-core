# IT Helpdesk — Application API Contract

**Status:** DRAFT — PROPOSED / LIVE_BLOCKED<br>
**Owner:** Application Team — Ánh<br>
**Project:** DX-OS Open-Core<br>
**Related:** Issue #22, #23, #24, #39, #53; PR #60, #63, #70, #71

**A02 scope evidence:** [Ánh's Application Owner review](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6551136261) acknowledges the current candidate **with conditions**. Final Business Contract ratification and LIVE acceptance remain pending. [Source reconciliation](../business-contract-review.md) records the inspected revisions and [business decisions](../business-decisions.md) retain all unresolved choices as OPEN.

## 1. Purpose

This document records the existing and candidate HTTP capabilities required by the IT Helpdesk application.

It describes the contract between the DX-OS Internal Service Portal and the Backend Application, while preserving the separation between Application-specific business operations and the reusable Workflow Core.

The document does not introduce new production endpoints or authorize LIVE integration.

Authoritative inspected Backend pin: **`fe55347908eed6a351ca9724be899138f7683081`**.

- [HelpdeskController — routes and input DTOs](https://github.com/lexuanbao258-collab/dxos-open-core/blob/fe55347908eed6a351ca9724be899138f7683081/backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java)
- [HelpdeskService — response models, authorization and business commands](https://github.com/lexuanbao258-collab/dxos-open-core/blob/fe55347908eed6a351ca9724be899138f7683081/backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java)
- [WorkflowExceptionHandler — candidate errors](https://github.com/lexuanbao258-collab/dxos-open-core/blob/fe55347908eed6a351ca9724be899138f7683081/backend/src/main/java/com/tricore/dxos/gateway/workflow/controller/WorkflowExceptionHandler.java)
- [Ánh's independent source/contract review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#pullrequestreview-5726515815): nine-route source mapping aligned; CHANGES_REQUESTED on Generic 404 compatibility. This is not an executed runtime verdict.

PR #63 is historical source lineage; #74 and #95 are newer overlapping candidates. #97 proposes a separate compatibility correction at `22bc977b894f91d44a836cb4c39bf4f0da566247`; it is not applied to #95 or approved for implementation acceptance. Final integrated Backend/BFF/Portal SHAs remain to be selected and ratified. See [reconciliation](../business-contract-review.md).

## 2. Architecture and Communication

The intended integration path is:

**React Portal → HTTPS Ingress → Portal BFF → Backend Application/Gateway → Workflow Core**

The Portal is an HTTP consumer.

The Backend must verify the authenticated principal, operation authority, resource visibility, and applicable authorization policy before returning protected data or executing operations.

The Portal must not connect directly to PostgreSQL, execute Workflow Core methods, or use privileged storage credentials.

Target path: **Portal → same-origin HTTPS → BFF → JWT-validating Backend/Gateway → authorized Application orchestration → provider-neutral Workflow Core**. Browser receives a protected server session, not OAuth bearer/refresh tokens. BFF is an allowlisted transport; Backend independently enforces actual actor/resource/operation policy. Portal has no direct database, Core-method or privileged object-storage access.

## 3. API Capability Classification

| Capability | Status | Notes |
|---|---|---|
| Health endpoint | ACTIVE | Health response only; does not prove business readiness |
| Existing Request API | PROTOTYPE | Separate from Generic Workflow; not approved for multi-user LIVE use |
| Generic Workflow HTTP | BLOCKED | Security and registration gates remain |
| PR #63 IT Helpdesk API | IMPLEMENTED CANDIDATE / LIVE_BLOCKED | Nine guarded Application routes |
| Portal BFF and OIDC | INTEGRATION PENDING | Real browser SSO and session acceptance incomplete |
| Facility business APIs | DEFERRED / POLICY OPEN | No approved full Facility contract |
| Attachment APIs | BLOCKED | Requires authorized Backend Storage API |

An implemented candidate is not equivalent to an approved LIVE contract.

## 4. IT Helpdesk Candidate Endpoints

**Base path:** `/api/v1/mvp/requests`

The following nine routes describe the PR #63 Backend candidate.

PR #63 is historical lineage; the inspected PR #95 source is pinned in §1. Helpdesk routes are default-off and final integrated source acceptance is pending. `{id}` is the Backend request UUID, not the opaque Workflow instance ID. POST bodies use `application/json`.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/mvp/requests` | Create and activate an IT Helpdesk request |
| GET | `/api/v1/mvp/requests` | List authorized requests |
| GET | `/api/v1/mvp/requests/{id}` | Retrieve an authorized request |
| POST | `/api/v1/mvp/requests/{id}/triage` | Triage a submitted request |
| POST | `/api/v1/mvp/requests/{id}/assign` | Assign an eligible technician |
| POST | `/api/v1/mvp/requests/{id}/start` | Start technical work |
| POST | `/api/v1/mvp/requests/{id}/resolve` | Record resolution |
| POST | `/api/v1/mvp/requests/{id}/confirm` | Confirm resolution |
| GET | `/api/v1/mvp/requests/{id}/history` | Retrieve authorized Workflow transition history |

These endpoints are not enabled for unrestricted production use.

## 5. Request Payloads

### Create Request

**POST `/api/v1/mvp/requests`**

Candidate request fields:

- `title`: Nonblank, maximum 200 characters.
- `description`: Nonblank, maximum 4000 characters.
- `requestType`: Exact value `IT_HELPDESK`.

The Backend generates the request and Workflow identifiers. Clients must not supply authoritative actor identity or Workflow state.

The candidate returns HTTP 201 with a `RequestView` when creation succeeds.

The response also includes Location `/api/v1/mvp/requests/{new-id}`. Initial Workflow state/version are `SUBMITTED` / 0 and `assignmentVersion` is 0. The other eight routes return HTTP 200 on permitted success; history returns an array and list returns a page.

### Triage

**POST `/api/v1/mvp/requests/{id}/triage`**

Required field: `expectedVersion`.

Only an eligible and authorized coordinator may execute this action.

### Assignment

**POST `/api/v1/mvp/requests/{id}/assign`**

Required fields:

- `assigneeId`
- `expectedAssignmentVersion`

Assignment is an Application command separate from the Workflow transition.

Assignment/reassignment is available only in `TRIAGED`; the target must be an active eligible tenant technician. A successful assignment increments `assignmentVersion` once.

It must not increment the Workflow version merely because assignment changes.

### Start Work

**POST `/api/v1/mvp/requests/{id}/start`**

Required field: `expectedVersion`.

The current eligible assignee may initiate the `start_work` Workflow transition when authorized.

### Resolve

**POST `/api/v1/mvp/requests/{id}/resolve`**

Required fields:

- `expectedVersion`
- `resolution`: Nonblank, maximum 4000 characters.

### Confirm

**POST `/api/v1/mvp/requests/{id}/confirm`**

Required field: `expectedVersion`.

The authorized original requester may confirm a resolved request.

The current candidate ends at `CONFIRMED`; no LIVE `CLOSED` endpoint is provided.

### Input constraints

| Field | Candidate wire type and constraint |
| --- | --- |
| title | Required nonblank string; maximum 200 characters |
| description | Required nonblank string; maximum 4000 characters |
| requestType | Required string; exact `IT_HELPDESK` |
| assigneeId | Required nonblank canonical actor string; maximum 1024 characters; active eligible technician in the same tenant |
| expectedVersion | Required non-null, nonnegative integer accepted as Java Long; exact Workflow version |
| expectedAssignmentVersion | Required non-null, nonnegative integer accepted as Java Long; exact Application assignment version |
| resolution | Required nonblank string; maximum 4000 characters |

The [candidate version deserializer](https://github.com/lexuanbao258-collab/dxos-open-core/blob/fe55347908eed6a351ca9724be899138f7683081/backend/src/main/java/com/tricore/dxos/gateway/workflow/dto/WorkflowVersionDeserializer.java) rejects string/float coercion for versions. POST records reject unknown JSON fields. Do not submit `requesterId`, tenant, display names, roles, state, priority, location or mock timeline facts as additional inputs. Backend derives requester and IDs; `assigneeId` selects a target and does not identify the caller.

## 6. Request and History Response Models

### RequestView

The Backend candidate provides these base fields:

- `id`
- `title`
- `description`
- `requestType`
- `requesterId`
- `assigneeId`
- `resolution`
- `assignmentVersion`
- `workflowInstanceId`
- `state`
- `version`
- `allowedActions`

The authorized detail response may also include:

- `requesterDisplayName`
- `assigneeDisplayName`
- `eligibleAssignees`, only for authorized assignment choices

Display names are optional and must not be treated as authentication evidence.

`allowedActions` is a server-derived UI hint. Every subsequent write must be authorized again by the Backend.

### HistoryView

The candidate transition-history response contains:

- `transitionId`
- `sourceState`
- `targetState`
- `version`
- `occurredAt`

History does not automatically include actor names, internal notes, security audit records or assignment audit entries.

The Portal must not invent fields or present missing evidence as recorded history.

### Response DTO inventory

| Model | Fields / shape | Nullability and projection |
| --- | --- | --- |
| RequestView — base | `id` UUID; `title`, `description`, `requestType`, `requesterId` strings; `assigneeId`, `resolution` nullable strings; `assignmentVersion` nonnegative integer; `workflowInstanceId`, `state` strings; `version` nonnegative integer; `allowedActions` string array | Base returned by create, list items, detail and five commands. Unassigned/unresolved fields can be null. Current source shape is not approval of final per-actor field visibility. |
| RequestView — optional detail | `requesterDisplayName`, `assigneeDisplayName` strings; `eligibleAssignees` array of `{id:string, displayName:string or null}` | GET detail only; null detail extensions are omitted. Names need not exist. Directory only when authorized `assign` is available; empty array is valid. Do not assume names/directory on create/list/mutation responses. |
| Page | `items: RequestView[]`, `nextCursor: UUID or null` | Empty items valid; null cursor means no next page. No count/status filter/full queue search is promised. |
| HistoryView[] | Each record: `transitionId`, `sourceState`, `targetState` strings; `version` integer; `occurredAt` serialized Instant | Minimal successful transition sequence. No creation/assignment event, actor identity, internal note, reason or security audit is promised. |

Display names are optional trusted labels, not identity evidence. The Portal must not derive an actor or author from a missing field. History access/projection and D1/D2/D3 policy remain BD-04 OPEN.

`allowedActions` values in the inspected candidate are `triage`, `assign`, `start`, `resolve`, `confirm`, computed from authorized current facts. It may be empty. `start` is the HTTP/UI action name; `start_work` is the Core transition key. Rendering a button is not permission to execute it later.

## 7. Listing and Filtering

**GET `/api/v1/mvp/requests`**

The candidate supports:

- `after`: UUID pagination cursor.
- `limit`: 1–100, default 50.

Response structure:

- `items`: Authorized request views.
- `nextCursor`: Cursor for the next page, when available.

The Backend must enforce resource-level visibility before returning list items.

The nine-route candidate does not establish a general server-side filter-by-status, total-count or full work-queue search contract.

The Portal must not assume those capabilities exist.

## 8. Authentication and Authorization

All protected operations must enforce:

1. Verified user identity.
2. Applicable exact operation authority.
3. Resource and tenant visibility.
4. Explicit action/resource authorization.
5. Business eligibility for the requested operation.
6. Final authorization checks at the relevant write boundary.

Expected denial categories include:

| Condition | Expected behavior |
|---|---|
| Missing or invalid authentication | 401 |
| Visible resource but forbidden operation | 403 |
| Hidden, cross-tenant or nonexistent resource | Indistinguishable 404 |
| Stale Workflow or assignment version | 409 conflict |
| Unknown write outcome | No false success or blind retry |

The final field-level and history-event visibility policies remain subject to review.

Frontend role selection and hidden buttons are not security enforcement.

Use the [business actor/resource/field matrix](business-rules.md#resource-field-and-history-visibility) and [canonical operation authorities](../../contracts/workflow-authorization.md). Exact read authority and resource permission apply to list/detail/history; mutations additionally need transition authority/resource grant and their specific actor predicate. Create combines create/activate/read under a reviewed exact definition binding. Administrator status grants no automatic business access.

The [HD-01 CREATE prerequisites](business-rules.md#3-request-creation-rules) require verified principal, trusted tenant, active membership, `submitter=true`, exact client-scoped `workflow:create` and authoritative tenant/resource-type CREATE permission before protected definition/binding lookup. Provisional ownership grants no authority; reviewed transaction/no-unauthorized-write evidence remains required. **Implementation BLOCKED, not PASS.**

Server enforces per-item visibility before returning list entries. An empty permitted page is not proof no other requests exist. Requester, current assignee and ACL resource owner are evaluated independently. History uses READ in current candidate source; do not invent a JWT HISTORY authority or claim that this settles history-specific business policy.

### Error and uncertain outcome contract

Candidate API error model is `{status:number, code:string, message:string, errors:object}`. Filter-chain authentication, BFF session/CSRF and MVC errors are different boundaries; do not assume identical 401/CSRF JSON without reviewed integration evidence.

| Condition | Candidate status/code or required behavior | Consumer handling / review limit |
| --- | --- | --- |
| Missing/invalid authentication | 401; MVC missing-trusted-actor maps AUTHENTICATION_REQUIRED | Deliberate login/session recovery; no write replay. Real OIDC/filter response shape must be verified separately. |
| Visible resource, forbidden operation/actor | 403 / FORBIDDEN | Explain denied access; no business/state/history writes |
| Hidden/cross-tenant/nonexistent Helpdesk resource | 404 / NOT_FOUND; sanitized message `Workflow resource not found`, errors `{}` | Indistinguishable detail/history and applicable POST error bodies/content type/bytes; do not disclose IDs/actors/grants. Exact negative evidence remains a gate. |
| Stale Workflow or assignment version | 409 / VERSION_CONFLICT | Fresh authorized read and explicit actor reconsideration; preserve both independent versions |
| Wrong state / completed instance | 409 / TRANSITION_NOT_ALLOWED or INSTANCE_COMPLETED | No invented transition or automatic retry |
| Unknown Core transition | 422 / INVALID_TRANSITION in shared mapper | No new reject/reopen/close HTTP route is implied |
| Bean/query/path validation | 400 / VALIDATION_FAILED | Show supported field errors; no claimed successful mutation |
| Invalid JSON / unknown POST fields | 400 / INVALID_BODY | Correct input; do not extend DTO silently |
| Wrong media type | 415 / UNSUPPORTED_MEDIA_TYPE | Use agreed JSON body |
| Untrustworthy policy decision/provider unavailable | 503 / AUTHORIZATION_UNAVAILABLE in access mapper | Fail closed; never convert unavailable policy into not-found or success |
| Ordinary operation/internal error | 500 / OPERATION_FAILURE or INTERNAL_ERROR | Sanitized failure; status 500 alone does not prove rollback |
| Commit acknowledgement uncertain | 500 / COMMIT_OUTCOME_UNKNOWN in Workflow mapper, or transport outcome unknown | Write may have committed; show UNKNOWN, no blind retry or false success/rollback |

Error status is not a substitute for authorization ordering: authenticate first; protect hidden existence before reporting operation authority/business eligibility; do not turn policy unavailability into a visibility decision. Malformed-input precedence, filter behavior and Generic CREATE/ACTIVATE/binding cases require the endpoint-specific owner decision in #97; this document does not choose a new mapper.

### Compatibility holds — #95 / #97

The current #95 shared handler also changes **Generic Workflow** missing-definition/instance codes/messages to Helpdesk's `NOT_FOUND`. [APP-P0-01 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#pullrequestreview-5726515815) requires compatibility correction. The [conditional API disposition](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#issuecomment-6554255746) preserves Generic `DEFINITION_NOT_FOUND` / `Workflow definition version not found` and `INSTANCE_NOT_FOUND` / `Workflow instance not found`; sanitized equivalence is scoped to Helpdesk.

[PR #97](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97) proposes that correction, but its blanket Generic HIDDEN mapping leaves CREATE/ACTIVATE/definition-binding cases unresolved. Ánh's separate [CREATE AMEND](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#pullrequestreview-5727851665) and [ACTIVATE AMEND](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#pullrequestreview-5727855334), [Yến's conditional nondisclosure direction](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6557395612), and [Bảo's reconciliation](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6557521952) are historical steps preceding the latest 2026-11-01 [Yến consolidated decision](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6558227330) / [Ánh API decision](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6558938294): **ACCEPT WITH CONDITIONS — API DESIGN ONLY; current #97 patch CHANGES REQUIRED / NOT ACCEPTED**. CREATE definition-binding parity is conditional on a verified definition-unavailable class preserving `DEFINITION_NOT_FOUND`; RESOURCE/TENANT `NOT_FOUND` requires a safe operation-specific missing comparator and full response parity. Instance-scoped operations preserve `INSTANCE_NOT_FOUND` parity. ACTIVATE binding/policy and ambiguous HIDDEN remain BLOCKED; there is no approved default 404/500/503 for ambiguous HIDDEN. Trusted provenance and actual authorization-path evidence are required; handler names, URLs and client fields cannot supply them. See the [latest operation boundaries and re-review gates](../business-contract-review.md#sec-97-01--latest-conditional-api-design-boundaries). Keep BD-08 OPEN, implementation acceptance BLOCKED and Generic production routes disabled. A02 records these conditional decisions without changing the wire API.

The Application Owner selected **legacy isolation only for the explicitly isolated disposable local candidate**, conditionally: flag absent/false retains legacy and disables MVP routes; flag true enables MVP and disables legacy controllers. Bảo must confirm rollout/consumer implications and all three flag regression cases; Yến must review exposure. Shared/staging/production flag changes or legacy removal remain unapproved. No runtime flag is changed here.

## 9. Portal Mapping Requirements

The Portal must map the Backend candidate explicitly:

| UI feature | Backend capability |
|---|---|
| My Requests | Authorized list |
| Request Detail | Authorized detail |
| Create Request | Create IT request |
| Coordinator Triage | Triage |
| Technician Assignment | Assign |
| Start Work | Start |
| Record Result | Resolve |
| Requester Confirmation | Confirm |
| Timeline | Authorized minimal transition history |

The existing Portal mock model and the Backend `RequestView` are different contracts.

Fields such as mock priority, location, display statuses and timeline notes must not be automatically submitted or assumed present in the candidate API.

Any additional business fields require an approved Application contract change.

The [independent #95 source review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#pullrequestreview-5726515815) reports BFF #70's `/bff/api/v1/mvp/requests` prefix versus #71's Backend-shaped `/api/v1/mvp/requests` client paths. A reviewed injected transport/reverse-proxy/session/CSRF composition and repinned consumer baseline are required; source route parity alone is not LIVE wiring.

## 10. Workflow and Transaction Boundaries

Workflow Core owns authoritative state, version and transition history.

Application owns IT-specific request facts, assignment and resolution data.

The PR #63 Backend candidate coordinates relevant business and Workflow writes through a guarded PostgreSQL transaction.

The inspected PR #95 source preserves that candidate transaction direction; independent verification at the final selected SHA remains required.

That implementation is not evidence of atomicity with external object storage or approval of all provisioning, recovery and LIVE integration policies.

If the client loses a write response and cannot establish the outcome from authorized evidence, the result must remain UNKNOWN pending reconciliation.

No general idempotency receipt API is promised.

### Unknown outcome procedure — proposal, BD-05 OPEN

Stop mutation replay. Preserve available request ID, attempted action and submitted expected version without recording credentials. Re-authenticate if required; obtain newly authorized detail/history observations and compare versions/result with the attempted action. A later state can reflect other work, so it is not a receipt for the original command.

For assignment, transition history cannot prove who changed assignment; for lost create, the client may lack the new ID and list similarity is not operation identity. Keep UNKNOWN and use an approved authorized operator investigation if attribution remains ambiguous. Recovery ownership, committed/uncommitted/still-unknown outcomes and safe resubmission criteria need Bảo/Yến/Ánh approval. No general idempotency key, operation receipt or reconciliation endpoint exists in these nine routes.

## 11. Unsupported or Deferred APIs

The first IT Helpdesk candidate does not provide an approved route for:

- Wrong-scope rejection.
- Requester dispute or rework.
- Unresolved incident escalation.
- Production `CLOSED` transition.
- Full Facility Maintenance processing.
- Protected attachment uploads or downloads.
- General server-side advanced search and filtering.

Missing routes must not be simulated as successful LIVE operations.

Related business decisions remain OPEN, BLOCKED or DEFERRED according to the reviewed source contract.

In particular, BD-01 preserves wrong-scope rejection as an unresolved proposed MUST; this route inventory does not approve its exclusion. See the [decision register](../business-decisions.md) for all eight OPEN decisions and deciding owners.

## 12. Integration and Acceptance Gates

Before enabling real Portal-to-Backend operations:

1. The applicable business/API contract must be reviewed.
2. BFF and OIDC browser login must be verified.
3. Backend resource authorization and negative tests must pass for the selected environment.
4. Application request/response mapping must be verified.
5. History visibility and sensitive-field policy must be accepted.
6. Transaction and uncertain-write recovery behavior must be reviewed.
7. End-to-end tests must pass on an identified source revision.

Until those conditions are met, Portal functionality remains MOCK or explicitly BLOCKED.

The [acceptance plan](acceptance-tests.md) retains independent exact-SHA execution, field/event negative tests and compatibility holds as separate gates. No runtime PASS is recorded by this contract.

## 13. References

- Issue #39 — Internal Service Portal
- Issue #53 — MVP Business/API Contract
- PR #60 — Draft Business/API Contract
- PR #63 — Helpdesk Backend Candidate
- PR #70 — Portal BFF Candidate
- PR #71 — Portal Integration Candidate
- PR #73 — Documentation Restructuring
- PR #74 / #95 — Backend source candidates (inspected #95 SHA in §1)
- PR #97 — Separate error-compatibility proposal; implementation acceptance pending
- [A02 source reconciliation and owner handoff](../business-contract-review.md)
- [Open business decisions](../business-decisions.md)

**Document status: DRAFT — REVIEW REQUIRED / LIVE_BLOCKED.**
