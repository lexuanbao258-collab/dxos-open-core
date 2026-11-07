<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors

SPDX-License-Identifier: Apache-2.0
-->

# R29 — Helpdesk six-use-case application contract (pre-integration)

**Status:** CANDIDATE / INTEGRATION BLOCKED BY R27 SERVICE SPLIT + R28 CORE OPENAPI.  
**Owner:** Ánh (Application). **Review:** Yến (Core/API), Bảo (architecture/release).

This branch adds one **transport-neutral sealed Java command contract** for exactly six Helpdesk operations (`Create`, `Triage`, `Assign`, `Start`, `Resolve`, `Confirm`) and unit tests of the command-value validation. It **does not** move the legacy service, change production routes, implement persistence/idempotency or enable LIVE traffic. Keeping it separate avoids prematurely coupling application logic to the old monolithic infrastructure class.

## Baseline checked

- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java` on the audited source has 380 lines and owns JDBC transactions, authorization, data mutations and workflow calls.
- `gateway/helpdesk/HelpdeskController.java` currently delegates `create`, `assign` and string-based `transition(id, action, version, resolution)` for the remaining four commands.
- The current `create(title,description)` generates a fresh UUID and workflow ID, **without an idempotency key**; the command record's mandatory key is *not* a duplicate-prevention implementation.
- The existing `transition` looks up definition `it-helpdesk` version **1** directly; version-as-configuration requires a reviewed runtime/contract decision.
- Current state is read through persisted `WorkflowInstance` inside `HelpdeskService`. R29 must read authoritative state from Core after the split, never infer it from a local enum or cache.
- `assign` validates a distinct `expectedAssignmentVersion` (business assignment version) rather than workflow runtime version. Preserve the distinction in OpenAPI and handler behavior.

## Compatibility and migration map

| Typed use-case | Current HTTP route | Legacy handler | Required future dependency |
| --- | --- | --- | --- |
| `Create(title, description, idempotencyKey)` | `POST /api/v1/mvp/requests` | `HelpdeskService.create(title, description)` | Tenant/principal authority; durable scoped idempotency ledger + atomic business/instance create; unknown-commit reconciliation |
| `Triage(requestId, expectedVersion)` | `POST /{id}/triage` | `transition(..., "triage", ..., null)` | Core workflow transition, authorization and optimistic version |
| `Assign(requestId, assigneeId, expectedAssignmentVersion)` | `POST /{id}/assign` | `assign(...)` | Qualified assignee, ACL mutation and atomic assignment version handling |
| `Start(requestId, expectedVersion)` | `POST /{id}/start` | `transition(..., "start_work", ..., null)` | Core `start_work` transition, current actor authority |
| `Resolve(requestId, expectedVersion, resolution)` | `POST /{id}/resolve` | `transition(..., "resolve", ..., resolution)` | Atomic resolution write with Core workflow transition |
| `Confirm(requestId, expectedVersion)` | `POST /{id}/confirm` | `transition(..., "confirm", ..., null)` | Requester-only confirmation; authoritative RESOLVED state and resolution |

**Important boundaries:** These records validate only null, blank and basic length/version constraints (aligned with the current HTTP payload for title/description/assignee/resolution). They never check eligibility, tenant, ACL, definition binding, actor, state or transaction outcome. Those checks **must** happen server-side through independently reviewed Core and business ports before writes; no caller should bypass old protection by directly activating records. Key maximum length 256 is a **candidate internal limit**, pending R28 contract agreement.

## Implementation gates

1. **R27 service split:** agree destination Java module, Core client interface, authentication token relay, tenant isolation, schema ownership, no cross-schema JOIN, and controlled Flyway migrations. Do not move 380 legacy lines into the new service unchanged.
2. **R28 OpenAPI:** decide exact Core workflow create/read/transition interfaces, idempotency-key transport and retry policy, expectedVersion semantics, errors including `COMMIT_OUTCOME_UNKNOWN` HTTP 503, and definition-version configuration.
3. **Six application handlers:** wire the six command types through an application facade and ports only after R27/R28. Retain negative tests for 404 visibility parity, denied mutation no-write, tenant boundary, assignment ACL, eligibility, terminal state, definition binding, stale version and unknown commit.
4. **Create idempotency acceptance:** same tenant + authenticated caller + idempotency key + same payload cannot create two business tickets/two Core workflow instances, including concurrent calls, rollback and uncertain outcomes. A mismatched payload under an existing key must not silently return the prior result. Reconcile uncertain commits; no blind retry.
5. **R29 Done:** two-service CI is green, contract tests pass, all six use-case integration tests pass in real PostgreSQL/Core environment, definition version is configurable, authoritative state is Core-derived, no Helpdesk business mutations reside in `infrastructure`, and independent approval is recorded.

**Current verdict:** `TYPED_COMMAND_CONTRACT_CANDIDATE / UNIT_TESTS_NOT_YET_CI_VERIFIED / PRODUCTION_INTEGRATION_BLOCKED`.
