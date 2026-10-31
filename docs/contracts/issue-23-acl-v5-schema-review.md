# Issue #23 — Proposed V5+ ACL schema and JDBC guard contract (REVIEW ONLY)

> **Status: CANDIDATE DESIGN, NOT APPROVED DDL OR MIGRATION.**
> This file contains no executable SQL. No Flyway migration or database command is authorized by it.
> Companion: [ACL review worksheet](issue-23-acl-review-worksheet.md) and
> [transaction design](issue-23-acl-transaction-proposal.md).
> Owner: Bảo (Gateway + PostgreSQL ACL design/integration); security reviewer: Yến.
> Seek an independent PostgreSQL/concurrency review before authorizing implementation.
> Ánh concentrates on Portal #39; do not silently reassign DDL ownership to her.

## Existing invariants to preserve

- Flyway V1–V4 bytes/checksums are immutable. V3 Workflow instances use `instance_id` as
  global key and store only `resource_type` + `resource_id`, **not tenant_id**.
- Current `PostgresWorkflowPersistence` opens its own JDBC connection and transaction
  for each port method; CREATE/ACTIVATE/TRANSITION must not be preceded by a detached
  "authorization precheck" that can race with revocation.
- Existing Workflow instance IDs from caller input remain unsafe at the HTTP create boundary.
  Draft PR #36 adds a generator but has not wired it. Keep `WorkflowController` unregistered.
- Verified caller identity must come from Yến's OIDC issuer+subject contract.
  Resource owner has *visibility* only; exact operation authority + explicit resource grant
  + active tenant membership remain mandatory.

## Candidate relations (names/constraints for reviewer decision, NOT implementation)

Assume one **private schema**, owned by the migrator and separate from application
runtime users. Choose actual schema name and naming convention in a reviewed migration.

| Candidate relation | Proposed columns / keys | Non-negotiable invariant |
| --- | --- | --- |
| `tenant_guards` | `tenant_id TEXT PRIMARY KEY`, `acl_revision BIGINT NOT NULL DEFAULT 0 CHECK >= 0`; optional immutable creation metadata | Guard row cannot be deleted/recreated while any resource, instance or policy references its tenant. `tenant_id` cannot be reused for a different tenant. All policy writers lock this row first. |
| `tenant_memberships` | `(tenant_id, actor_id) PRIMARY KEY`, `active BOOLEAN NOT NULL`, revision/audit data; FK tenant guard | Membership is verified canonical OIDC actor, never a trusted client header. Membership alone grants **no** Workflow operation. Mutators obey tenant guard locking. |
| `resource_registry` | `(resource_type,resource_id) PRIMARY KEY`, `tenant_id NOT NULL`, `owner_actor_id`, `lifecycle` with ACTIVE/TOMBSTONED, creation/tombstone metadata; FK tenant guard; additional UNIQUE `(tenant_id,resource_type,resource_id)` | Global resource key immutable; tenant binding immutable (no cross-tenant transfer). Tombstones persist; resource IDs must not be reused. Owner visibility requires active membership, not an implicit operation grant. |
| `resource_acl` | `(tenant_id,resource_type,resource_id,actor_id,operation) PRIMARY KEY`, `granted BOOLEAN NOT NULL`; composite FK to resource registry and tenant membership | Exact operation set: `workflow:create`, `workflow:activate`, `workflow:transition`, `workflow:read`. No wildcard. Permission grant/revoke always locks tenant guard; disabled membership overrides any grant. |
| `workflow_definition_bindings` | `(tenant_id,resource_type,definition_id,definition_version) PRIMARY KEY`, `active BOOLEAN NOT NULL`; tenant guard FK and exact immutable Workflow definition FK if supported by schema privileges | Binding checked for **new CREATE** only; revoking binding does not automatically revoke existing instance actions. Exact version matching; no alias. |

**FK and lifecycle review needed:** owner actor may be a former member, so do not
blindly require active membership by FK; confirm whether a historical owner record
must remain after membership revocation. Composite resource FK must prevent tenant
mismatch without trusting `tenant_id` from the HTTP body. Reject blank/ambiguous
IDs consistently with existing Core contracts. Review `TEXT` identifiers and
collation/canonical-actor comparisons before final SQL.

**Historical data compatibility:** an already existing V3 Workflow instance may
have a resource with no entry in the new registry. Fail closed, never infer its
tenant from client input or automatically invent ownership. If adding a forward FK
from V3 instances to resource registry, separately plan owner-reviewed
reconciliation and additive constraint validation; do not mutate old migrations
or silently touch shared Supabase.

## One-transaction guarded workflow protocol (candidate)

1. **CREATE:** take trusted verified principal, exact authority, resource ref and
   definition ID/version from the request. Resolve resource registry to an immutable
   tenant *without releasing any protected existence details*, begin JDBC transaction,
   lock tenant guard `FOR SHARE`, re-read resource and membership/ACL/active binding
   **after acquiring the guard**. Only then load exact definition, generate server
   instance ID internally, write instance and COMMIT on the **same JDBC connection**.
2. **ACTIVATE / TRANSITION:** within the same transaction, obtain the minimal
   instance->resource projection for tenant lookup (never return it to caller);
   lock guard `FOR SHARE` **before** locking Workflow instance row; re-fetch
   authoritative snapshot/resource binding and re-check membership, operation
   grant and active resource. Commit Workflow state/history atomically on this
   connection. The resource-to-tenant binding must be immutable; revalidate
   it after the guard lock.
3. **READ / HISTORY:** use a coherent guarded read/snapshot: locate candidate
   tenant privately, acquire guard then re-check visibility/permission and
   materialize the instance/history without letting hidden data escape. Avoid
   pre-guard `REPEATABLE READ` snapshots which could precede a revocation;
   evaluate `READ COMMITTED` and refresh queries after locking with concurrency
   tests. No caller-visible operation before authorization.
4. **Policy mutations:** all membership, ACL, owner, definition binding, tombstone
   and related admin/job changes lock the tenant guard `FOR UPDATE` first,
   then lock child rows in a deterministic order, bump `acl_revision` and COMMIT.
   Revoke finishing first must deny subsequent guarded operations; guarded write
   holding lock first must finish before revoke can commit.
5. **SQL privilege enforcement:** enumerate *every* mutation channel, including
   provisioning and maintenance jobs. Forbid runtime bypass/unguarded changes
   through grants, vetted DB functions or another reviewed DB boundary.
   A Backend role with unrestricted direct access to protected tables can bypass
   the HTTP ACL if compromised; document and mitigate this rather than claiming
   DB-enforced ACL from HTTP checks alone.

**Architecture blocker:** Core `WorkflowRuntime` currently calls separate
`WorkflowPersistencePort` methods; the adapter itself commits. Before adding
`AuthorizedWorkflowService`, design a **narrow guarded transaction coordinator**
which can invoke the existing domain operation while sharing one physical JDBC
connection, or a reviewed equivalent that preserves Core contracts, port semantics,
snapshot and history atomicity. An outer Spring `@Transactional` alone is NOT enough.

## Failure and HTTP contract (proposed; must test)

| Condition | External behavior |
| --- | --- |
| Missing/invalid OIDC | 401 via real Security filter |
| Hidden or nonexistent resource/instance | Indistinguishable safe 404 (status/body/timing leakage review) |
| Visible resource, explicit operation denied | 403 |
| Policy unavailable/null/exception | Sanitized non-success, fail closed |
| Confirmed rollback after authorization denial | Preserve typed HIDDEN/FORBIDDEN/UNAVAILABLE, without translating to generic `OPERATION_FAILURE` |
| Connection/COMMIT or rollback outcome uncertain | `COMMIT_OUTCOME_UNKNOWN` takes precedence; **no automatic retry** |
| Hidden duplicate ID / concurrent CREATE conflict | No existence oracle; server-owned opaque ID; explicit idempotency/lost-response design |

The current `PostgresWorkflowPersistence.transaction()` converts non-Workflow
runtime exceptions (including `WorkflowAccessException`) into
`OPERATION_FAILURE` after rollback. Specify a typed bridge/change in a separate
reviewed code PR; do not simply throw an ACL exception inside its callback.

## Required review answers before V5 migration

1. Who independently approves physical PostgreSQL DDL, role privileges and
   full list of guarded policy mutation entry points?
2. Will the guard be a permanent row in `tenant_guards`, or an equivalent
   immutable lockable tenant record? How will provisioning and tenant deletion work?
3. How will the adapter preserve **one physical JDBC transaction** for
   authorization, Workflow write, commit and history while keeping Core interfaces?
4. How are tenant lookup, row ordering, read snapshot and revoke races proved
   correct under `READ COMMITTED` (and tested with two connections)?
5. How are historical V3 instances with no authoritative registry entry handled?
6. How are typed authorization outcomes retained on confirmed rollback, and unknown
   commit/rollback classified correctly?
7. What explicit privilege/RLS/function constraints prevent unguarded SQL policy
   writes or protected data reads via a compromised application DB role?

**Next after approvals only:** create a *separate*, additive V5+ migration
implementation and disposable-local PostgreSQL tests for constraints, guard locks,
revoke races, read/history consistency, fail-closed semantics and uncertain commits.
Never apply draft SQL to the team's shared Supabase database or enable production
Workflow HTTP routes before Identity/Gateway security acceptance.
