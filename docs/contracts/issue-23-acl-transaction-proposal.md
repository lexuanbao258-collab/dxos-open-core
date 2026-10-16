# Issue #23 Phase 2 — Resource/Tenant ACL transaction design (PROPOSAL)

**Status: PROPOSAL; not an approved security contract or executable migration.**
Owners: Bảo (application / Gateway), Ánh (PostgreSQL schema and transactional infrastructure), Yến (verified Identity).

## Proposed decisions for team sign-off

1. **Resource owner:** An active resource owner with valid tenant membership may see resource existence. Ownership alone does **not** grant any Workflow CREATE / ACTIVATE / TRANSITION / READ access. Every operation requires both the exact verified OIDC authority and explicit authoritative resource permission. Admin APIs may grant the owner needed permissions, but cannot infer grants from a client-supplied owner field.
2. **Definition binding:** An exact binding is maintained for `(tenant_id, resource_type, definition_id, definition_version)`, separately from resource ACL. CREATE requires an active binding **and** resource-level CREATE permission. Revoking a binding prevents subsequent creates; it does not itself revoke permissions for existing instances.
3. **Resource transfer and deletion (v1):** Direct cross-tenant transfer is unsupported. Tombstoned resources reject normal Workflow operations, including snapshot/history reads, and identifiers are not reused. Administrative audit/recovery is a separate future contract; never silently resurrect resource IDs.

**Approval needed:** Ánh and Yến must confirm these are the intended business/security policies before implementing migration or production ACL. This proposal does not claim that the schema exists.

## Database design review checklist (Ánh to supply exact DDL)

- Private schema and named tables for `tenants`, `tenant_memberships`, `resource_registry`, `resource_acl`, and `workflow_definition_bindings`. Provide complete keys, foreign keys, tenant isolation constraints, unique `(resource_type, resource_id)` identity, immutable/tombstone lifecycle, and indexes.
- Designate an unremovable *tenant guard row* for each tenant. ACL/membership/owner/binding/resource mutations acquire `FOR UPDATE` on the guard, make changes, increment a revision, and commit. Never delete/recreate the guard during active operation.
- Guarded Workflow writes acquire `FOR SHARE` on the same guard before final ACL reads and retain it through the Workflow commit/rollback. Every mutation path (SQL admin tools, jobs, services, provisioning) must obey the same protocol or direct writes must be blocked by database privileges / trusted enforcement.
- Lock ordering must be globally deterministic (tenant guard before resource/instance row) for all paths. No cross-tenant transfer in v1 simplifies ordering. Document the `READ COMMITTED` visibility/recheck behavior, timeouts and deadlock classification.
- Revocation semantics: revoke committed before final guard/check denies write; guarded write taking the lock first commits/rolls back before revoke can commit.
- Resource visibility and absence must map to indistinguishable external 404; existing visible resource lacking permission maps to 403. Policy failure/null/unavailable must deny without leaking details. A token operation authority is necessary but never sufficient.
- READ and HISTORY need authorized read boundaries which resolve binding/visibility and materialize protected data under a consistent guard/snapshot; no protected object or history may escape before that check.
- No direct runtime role ability to bypass ACL via privileged SQL access. Review least privilege and the relationship to existing private Workflow persistence schema, triggers, migrations and tests.

## Transaction coordinator interface to design (not implemented)

The existing `PostgresWorkflowPersistence` opens its own JDBC connection, controls commit and preserves `COMMIT_OUTCOME_UNKNOWN`. An outer Spring `@Transactional` or a pre-check in `AuthorizedWorkflowService` does not close revocation TOCTOU.

Ánh and Bảo should choose a narrow infrastructure/application coordinator so that:
- The **final** ACL check and CREATE / ACTIVATE / TRANSITION durable write happen on the **same actual JDBC connection and transaction** and hold the ACL guard through commit.
- Checked instance binding comes from persisted authoritative data, not client-supplied instance/ownership. Exact immutable definition version is loaded authoritatively.
- The public Core `WorkflowPersistencePort` signatures, snapshot/version comparison, durable commit and history atomicity remain unchanged.
- On confirmed rollback, distinct sanitized HIDDEN/FORBIDDEN/UNAVAILABLE decisions survive. **Do not** simply throw `WorkflowAccessException` through the existing `transaction()` callback: it currently maps non-`WorkflowException` runtime errors to OPERATION_FAILURE.
- After any unknown COMMIT or failed rollback where commitment cannot be established, **COMMIT_OUTCOME_UNKNOWN** takes precedence over a pre-commit authorization failure; never blindly retry.
- Preserve existing Workflow errors and error precedence without exposing hidden resource existence; specifically test create duplicate-ID/concurrent insert probes.

**Implementation blocker:** This is not an API spec. Ánh must submit and obtain review of proposed DDL, scoped guard interface, transaction/error classification and complete ACL mutation routes before implementation.

## Application/HTTP plan (Bảo; not yet implemented)

- Integrate Yến's reviewed `CurrentPrincipal.requireVerified()` from Spring Security only after Identity PR #33 is corrected and reviewed. Derive canonical actor from verified issuer+subject; never accept principal, actor, tenant, role or ACL from request headers/body.
- Build `AuthorizedWorkflowService` for create, activate, transition, snapshot and history; use the transaction coordinator for privileged operations. Maintain `WorkflowAuthorization`'s operation-level authority gate.
- Replace caller-controlled HTTP create `instanceId` with the application-generated opaque ID (`WorkflowInstanceIdGenerator`). Reject unexpected `instanceId` in HTTP request; do not merely ignore it. Define explicit lost-response idempotency behavior separately. Do not expose duplicate-ID results for inaccessible workflow IDs.
- Keep `WorkflowController` unregistered, and `WorkflowHttpExposureTest` passing, until actual JWT/filter-chain, ACL, PostgreSQL revocation-race and HTTP error tests all pass.

## Required acceptance tests (real disposable PostgreSQL, two connections/barriers)

1. Exact authorities and resource ACL (CREATE / ACTIVATE / TRANSITION / READ and HISTORY).
2. No-tenant, no-membership, hidden/nonexistent, visible-forbidden, tombstone and cross-tenant cases; no unauthorized read/materialization/write.
3. Revocation-wins and guarded-write-wins races, plus ownership, membership, binding grant/revoke and resource tombstone races.
4. Collisions and simultaneous CREATE, no hidden instance ID existence oracle; lost response, rollback failure and uncertain commit without automatic retry.
5. Actual read/history consistent materialization and fail-closed policy/provider failures, lock timeout/deadlock/serialization outcomes.
6. Real Keycloak JWT/JWKS + Spring Security 401/403 mapping, forged headers, CSRF/stateless, Request endpoint regression and JDK 21 full tests/build.

Do not migrate shared Supabase, edit Workflow Core or enable the production Workflow HTTP API from this proposal.
