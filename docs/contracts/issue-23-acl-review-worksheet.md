# Issue #23 — PostgreSQL ACL / guarded Workflow design review worksheet

> **DRAFT / REVIEW REQUIRED — NOT executable migration or implemented authorization.**
> Bảo coordinates Gateway, ACL/transaction design and integration. Yến supplies reviewed verified OIDC principal/authorities. Ánh focuses on Portal #39 and can review API/Data compatibility, but is **not** the owner of ACL DDL after the 2026-10-17 handoff.
> Companion proposal: [issue-23-acl-transaction-proposal.md](issue-23-acl-transaction-proposal.md).
> No change to Flyway V1–V4, Workflow Core, cloud Supabase, existing privileges, or Spring production HTTP registration.

## Baseline checked on 2026-10-17

- Main `PostgresWorkflowPersistence.transaction(write, work)` independently obtains and commits/closes its own JDBC Connection. Writes use READ COMMITTED; reads use REPEATABLE READ. Blind outer Spring `@Transactional` and pre-checks in an application facade **cannot** guarantee authorization/revoke ordering.
- After a confirmed rollback, this method currently retains only `WorkflowException`; an unrelated `WorkflowAccessException` becomes `OPERATION_FAILURE`. If commit may have occurred (or rollback fails), `COMMIT_OUTCOME_UNKNOWN` must take precedence and must **not** be automatically retried.
- Flyway V3 `workflow_instances` stores `instance_id`, `definition_id/version`, `resource_type`, `resource_id`, but **no tenant_id**. It has an index on `(resource_type, resource_id)`; a separate authoritative registry must resolve the resource to a unique tenant without trusting client-provided tenant/owner.
- `WorkflowAuthorization` on main is a pure policy/authority boundary, **not** an OIDC verifier or lock-bearing transaction coordinator. `WorkflowController` has no Spring controller registration and still accepts caller-selected `instanceId`; Draft PR #36 adds a safe ID generator but does not use it in HTTP create.

## Product security policy — already approved by Issue #23 owner

1. Ownership may confer resource **visibility**, not implicit Workflow operation grants. Each operation needs exact verified OIDC authority + explicit authoritative resource ACL.
2. A create requires active exact tenant/resource-type/definition-ID/version binding as well as a resource CREATE grant; revoking the binding stops *new* creates but does not automatically revoke permissions on existing instances.
3. No direct cross-tenant resource transfer in v1. A tombstoned resource stays tombstoned, ID never reused, and regular Workflow read/write endpoints reject it.

These are product choices, **not** approval of a physical schema or SQL implementation.

## Candidate private ACL structures — design only, no DDL in this file

| Candidate table/entity | Proposed keys and invariants requiring database review |
| --- | --- |
| `tenant_guard` | One never-recreated guard row per tenant; key `tenant_id`, monotonic ACL revision, immutable tenant identity; mutation acquires `FOR UPDATE`, guarded Workflow operation acquires compatible `FOR SHARE`. Review provision/delete/recovery privileges and guard lifecycle. |
| `tenant_memberships` | Foreign key to tenant/guard and trusted canonical `actor_id`, explicit active/revoked state. Tenant member alone has no Workflow action permissions. |
| `resource_registry` | Unique global `(resource_type, resource_id)` as required by current Workflow instance references; immutable bound `tenant_id`, authoritative owner, lifecycle/tombstone, and FK to guard. No recycled resource identity. |
| `resource_acl` | Explicit resource + canonical actor + exact operation grant (`workflow:create`, `workflow:activate`, `workflow:transition`, `workflow:read`); constrain duplicates and grant lifecycle; do not infer permissions from ownership, token scope alone, or request body. Membership also required. |
| `workflow_definition_bindings` | Exact active mapping `(tenant_id, resource_type, definition_id, definition_version)`; validate existence of immutable Workflow definition, and forbid alias/fallback version matching. |
| Workflow V3 tables | Keep bytes/checksums unchanged. The authoritative `resource_registry` bridges existing `workflow_instances(resource_type,resource_id)` to a tenant. Determine whether **additive, forward-only** constraints or immutable registry guards are needed; never allow historical instances to silently rebind to a different tenant. |

**Open integrity questions for explicit review:** exact schema name and privilege owner; actor identifier collation/length; membership/ACL key shape and revoke representation; cross-schema definition FK and permissions; consistency of resource ownership vs visibility; immutable global resource identity; all admin/job mutation entry points; allowed SQL roles; indexes and foreign-key/deferred rules; handling existing Workflow instances with no registry entry (fail closed by default).

## Transaction and lock protocol — must be reviewed before implementation

- **Mutating membership, ACL, owner, definition binding or resource lifecycle**: locate authoritative tenant guard without trusting a user-chosen tenant; lock that tenant row `FOR UPDATE` **before** other resource/instance rows; change policy data and increment a revision; COMMIT. DB permissions must prohibit any unguarded mutation channel, including maintenance jobs and trusted admin operations not following the protocol.
- **Workflow CREATE / ACTIVATE / TRANSITION**: in one *actual* JDBC Connection/transaction owned by the persistence/coordinator, resolve authoritative tenant/resource; lock same tenant guard `FOR SHARE` and retain through commit; **after the lock**, verify membership, active resource/binding as applicable, explicit resource ACL, verified actor identity/exact authority; load/mutate/persist Workflow and commit. Global lock order: tenant guard, then resource, then workflow instance. No external authorization pre-check suffices.
- **READ / HISTORY**: hide data until authoritative tenant/resource visibility and permission are established; ensure policy and protected materialization are from a coherent guarded read/snapshot. A stale repeatable-read snapshot established **before** waiting for a revoke lock may violate ordering: define isolation and snapshot acquisition (candidate READ COMMITTED with policy read after guard lock, or an explicitly demonstrated equivalent).
- **Cross-resource/tenant operations**: v1 transfer prohibited; define ordering for administrative multi-tenant actions, or reject such actions until concurrency proof exists.
- **Commit/error bridge**: HIDDEN and missing resolve identically at public boundary (404); visible but forbidden (403); unavailable policy/db (sanitized non-success, no fallback); missing/invalid token (401). Preserve specific classified authorization errors *only after known rollback*. Unknown commit/rollback outcome supersedes them; never auto-retry uncertain writes. Use a typed conversion owned by application/infrastructure; do not let non-Workflow exceptions be swallowed into `OPERATION_FAILURE` silently.

**Hard implementation blocker:** `WorkflowRuntime` currently calls separate persistence methods which each open transactions. A future `AuthorizedWorkflowService` must not pretend a transaction exists around these calls. Choose a narrow transaction-capable adapter/coordinator that keeps authoritative policy checks, any required read and durable Workflow write on one actual connection without changing public Core port semantics. Review error propagation and snapshot materialization before touching code.

## Test plan using isolated/disposable PostgreSQL (never shared Supabase)

1. DDL migration idempotency/fail-closed privileges; database-enforced invariants for duplicate/tombstone/tenant uniqueness; no direct privilege bypass.
2. Exact Workflow authorities + resource ACL + membership and binding; no owner-by-default grant.
3. 401/403/404 indistinguishability and no protected snapshot/history materialization for hidden/cross-tenant/tombstone callers.
4. **Two JDBC connections + deterministic barriers**: revoke wins vs guarded write wins; repeat for membership, binding, owner/resource tombstone; test both reads/history and writes.
5. Rollback/lock-timeout/deadlock and uncertain-commit injection: typed sanitized decisions; no automatic retries; no history/instance corruption.
6. Simultaneous create/opaque ID collision/lost response: no duplicate-ID oracle and no caller-chosen `instanceId`.
7. Spring Security filter chain with genuine OIDC/JWKS after Yến's Identity PR approval; forged headers/body identity never accepted, Request regressions intact.

## Implementation sequence and explicit sign-off gates

- **Review gate A:** Bảo proposes authoritative schema, FK/unique constraints, guard/mutator privileges. Yến reviews authorization and actor provenance; Data/PostgreSQL reviewer independently checks locking/DDL. Mark approved explicitly on #23 before any V5+ migration.
- **Review gate B:** Choose same-connection transaction coordinator interface and typed failure semantics, including commit unknown and guarded read. Independently review the design before editing existing persistence logic.
- **Code gate C:** Add additive, forward-only migration in a dedicated issue/PR and disposable PostgreSQL tests **only after A/B approval**. Do not edit V1–V4 or migrate shared Supabase.
- **Integration gate D:** Implement `AuthorizedWorkflowService`, use PR #36 server-generated IDs, integrate Yến's verified `CurrentPrincipal` once ready, run all negative/race tests; only then consider registering secured Workflow HTTP. Keep #22/#23 OPEN until evidence and review.

### Operator baseline evidence

Windows PowerShell on checkout `bf28ead8007ffe32181988c4bb5c5670bf56402e`:
`./gradlew.bat test --tests '*WorkflowInstanceIdGeneratorTest' --tests '*WorkflowHttpExposureTest' --rerun-tasks --no-build-cache --no-daemon --console=plain` → **BUILD SUCCESSFUL in 34s; 4 actionable tasks, 4 executed** (operator-reported; no GitHub CI claim). This validates foundation behavior, **not** ACL/IdP/DB concurrency.
