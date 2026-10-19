# Issue #23 — Stage 5 Tenant/Resource ACL application candidate

**Status:** REVIEW CANDIDATE, not a released SQL-backed ACL. **Base:** Stage 4 / PR #48 HEAD `b3bd87dd62c432df33163538ce382e14f3c1a19c`. **Owner:** Bảo. **Security reviewer:** Yến. This document concerns Stage 5 decision semantics only, not PostgreSQL approval.

## 1. Reuse existing contracts, avoid new frameworks

The Stage 3 `AuthorizedWorkflowService` obtains the verified actor using `CurrentPrincipal.requireVerified()`, makes an authoritative Workflow definition/instance lookup, calls `WorkflowAuthorization` and delegates to Core `WorkflowRuntime` only after a positive decision. Stage 4 pins exact role names for CREATE, ACTIVATE, TRANSITION, READ and HISTORY.

The Stage 5 candidate adds:
- `WorkflowAclSnapshotReader`: provider-neutral source-of-truth boundary for *one resource + one verified actor*, returning an immutable self-identified snapshot or an explicitly missing result.
- `TenantResourceWorkflowAccessPolicy`: pure production Java evaluation logic implementing the **existing** `WorkflowAccessPolicy`. No Spring registration, no database implementation and no Controller wiring.
- Real-policy + Stage-3-service isolated tests; no allow-all fallback.

This reader is **not an ACL database, not proof of an authorized SQL session, and not revocation safety**. Future infrastructure must only create a snapshot from authoritative persistent records, never client values or token-only claims.

## 2. Already-approved product rules (Issue #23, 2026-10-16)

1. Resource owner (with active membership) may know that the resource exists, but ownership **never** implicitly grants an operation. Exact OIDC Workflow authority AND explicit resource-level operation ACL grant are required.
2. CREATE also needs the exact active binding `(tenant, resource type, definition id, definition version)`. Revoking it blocks **new CREATE only**; existing instances are governed by their own persisted binding and current resource ACL.
3. Cross-tenant direct transfer is unsupported in MVP v1; tombstoned resource IDs are never reused and ordinary Workflow reads/writes are denied.

## 3. Additional Stage 5 semantics for review — NOT YET OWNER-RATIFIED

- Candidate visibility: active tenant membership AND (resource owner OR at least one currently valid explicit Workflow resource grant). A member who is neither owner nor a grantee gets **HIDDEN**. A visible active member without the requested grant gets **FORBIDDEN**.
- Candidate CREATE binding visibility: even when CREATE grant exists, a missing/inactive/not-exact definition binding yields **HIDDEN** to avoid existence disclosure; this is **not yet final HTTP policy**. A future reviewer might require a different public classification and matching negative tests.
- Missing/unmapped legacy V3, foreign tenant, inactive membership, tombstone or registry absence -> **HIDDEN**, never a silently assigned tenant.
- Source unavailable, null result, exception or resource/actor mismatch -> **UNAVAILABLE**. `WorkflowAuthorization` sanitizes source failures and checks exact authority **after** the resource policy decision.
- A positive operation grant does not grant another operation. HISTORY and SNAPSHOT require the same READ grant.

## 4. Future authoritative PostgreSQL source (BLOCKED)

Source-of-truth candidates reviewed in PR #36, **not approved or present as production V5**: `tenant_guards`, `tenant_memberships`, `resource_registry`, `resource_acl`, `workflow_definition_bindings`. Global identity `(resource_type, resource_id)` must map to one immutable tenant; exact V3 compatibility/backfill plan required. Producer must derive tenant from registry, membership and grants from privileged DB, and bind the actor/resource echo to the requested scope. A test fake or a user-supplied tenant is not authoritative.

No `@Component` annotation on this policy or source implementation. **Do not register Stage 3 service for HTTP**. The current `PostgresWorkflowPersistence` uses direct table access and per-port connections, not guarded V5 entry functions. Revocation between this application policy check and final write is possible.

## 5. Stage 6 and later blockers (NOT CLAIMED)

ADR-0023 Option A needs each write port to do final post-lock ACL check + durable state/history COMMIT on the same physical JDBC transaction. Tenant guard `FOR SHARE`, all membership/ACL/ownership/binding/tombstone mutators `FOR UPDATE`, coherent guarded READ/HISTORY and fresh actor proof for each transaction are still pending. ADR-0024 C3 secured CREATE/server-owned ID must avoid `WorkflowRuntime.createInstance()` hidden ID probe. SQL runtime roles/privileges, true JWT/JWKS, error and `COMMIT_OUTCOME_UNKNOWN` mapping remain blockers.

**No Flyway V5, V1–V4 changes, staging/Supabase/Keycloak writes, SQL admin role changes, production Workflow HTTP, or PR merge in Stage 5.**

## 6. Acceptance and evidence rubric

- Positive: exact grant + active membership + resource visibility + token authority; CREATE additionally exact binding.
- Negative: other tenant, no membership, owner-only, visible wrong grant, tombstone, missing/unmapped V3, incorrect/absent definition binding, wrong actor/target returned by source, null/throwing source, wrong authority and no delegation.
- Run JDK21 selected tests; capture JUnit XML `tests/failures/errors/skipped`, exact HEAD and `git diff --check`. Mocked authoritative facts are **not** database/cryptographic evidence.
- Independent reviewer should conclude **Stage-5 application-policy-only PASS** or **CHANGES REQUIRED**, and separately record whether proposed visibility/CREATE classification is ratified.

**Open design decisions:** owner/security ratification of non-owner visibility and missing CREATE-binding response, resource ID global uniqueness, explicit grant-revoke lifecycle, V3 legacy reconciliation, DB/cryptography approval and physical proof of concurrency. Any unresolved P0 keeps HTTP off.
