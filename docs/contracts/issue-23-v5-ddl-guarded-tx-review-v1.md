# Issue #23 — V5 ACL DDL and guarded JDBC transaction: review draft v1

> **STATUS: REVIEW ONLY / CHANGES REQUIRED BEFORE MIGRATION OR IMPLEMENTATION.** All SQL here is an illustrative candidate, **NOT executable production Flyway V5**. Do not run it, alter V1–V4, touch shared Supabase/staging, merge Draft PR #36, or register Workflow HTTP routes. Requires independent PostgreSQL/security review.
>
> Baseline: `main` at `2115de355e8df4f0567e8e8421dae8465da9cea8`, draft #36 at `eda7e7ab548cb83b022e8e7bae8feb1a8f2292de`. Owner: Bảo (#23). Yến's [D1–D5 AGREE](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6249235587) covers Identity/Core boundaries **only**, explicitly **not** physical DDL, SQL privileges, locking or the coordinator.
>
> Companion documents: [V5 schema candidate](issue-23-acl-v5-schema-review.md), [transaction proposal](issue-23-acl-transaction-proposal.md), [review worksheet](issue-23-acl-review-worksheet.md), [acceptance plan](issue-23-acl-test-plan.md).

## 1. Immutable baseline and approved product policy

- Keep `core.workflow` provider-neutral and the public `WorkflowPersistencePort` signatures unchanged without a proven contract defect. Two V4 Workflow definitions prove reusable Core design, **not** authenticated HTTP E2E.
- Current V3 `workflow_instances` holds global `instance_id` and `(resource_type,resource_id)`, **not tenant_id**; there is no authoritative tenant/ACL registry. Existing historical instances without authoritative mappings must fail closed; no inferred tenant or automatic backfill.
- `WorkflowRuntime` makes several persistence-port calls per operation. `PostgresWorkflowPersistence` opens, commits and closes a **new JDBC Connection for each call**. Outer Spring `@Transactional` and a separate ACL precheck are insufficient to prevent revocation TOCTOU. Writes use `READ COMMITTED`; standalone reads use `REPEATABLE READ`.
- Existing `transaction()` converts non-`WorkflowException` runtime failures, including bare `WorkflowAccessException`, into `OPERATION_FAILURE` after rollback; write commit uncertainty/failed rollback must retain `COMMIT_OUTCOME_UNKNOWN`.
- Product-approved: resource owner may see existence with active membership but ownership grants no Workflow operation. **Exact verified OIDC authority AND explicit resource ACL grant AND active tenant membership** are needed for CREATE/ACTIVATE/TRANSITION/READ. CREATE also requires exact active tenant/resource-type/definition-id/version binding; revoking binding only stops future CREATE. No direct cross-tenant transfer; tombstones deny ordinary reads/history/writes and resource/tenant keys are not reused.
- Draft #36 adds 192-bit server ID generator and synthetic ACL tests, **not** authorized HTTP orchestration. Caller-supplied `instanceId` still exists in unregistered DTO/controller. Locally observed 127 focused PASS; full build 812 total, 700 PASS, 112 PostgreSQL suites SKIPPED: no live V5 ACL acceptance implied.

## 2. Physical data model (DDL skeleton — DO NOT EXECUTE)

**Candidate schema decision S1:** reuse existing validated *private* Workflow Flyway schema `"${flyway:defaultSchema}"` (the `dxos.workflow.schema` namespace), never `public`; migrator/table owner is separate from runtime. If reviewers demand a separate ACL schema, specify migration/FK/search-path/`WorkflowSchema` changes before implementation. Use case-sensitive exact identity bytes; `COLLATE "C"` is a candidate but must be compatibility-tested with existing V3/V4 keys and the actual PostgreSQL 17 cluster.

```sql
-- ILLUSTRATIVE STRUCTURE ONLY — NOT A READY MIGRATION.
CREATE TABLE "${flyway:defaultSchema}".tenant_guards (
  tenant_id TEXT COLLATE "C" PRIMARY KEY,
  acl_revision BIGINT NOT NULL DEFAULT 0 CHECK (acl_revision >= 0),
  CHECK (btrim(tenant_id) <> '')
);

CREATE TABLE "${flyway:defaultSchema}".tenant_memberships (
  tenant_id TEXT COLLATE "C" NOT NULL,
  actor_id TEXT COLLATE "C" NOT NULL,
  active BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, actor_id),
  FOREIGN KEY (tenant_id) REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CHECK (btrim(actor_id) <> '')
);

CREATE TABLE "${flyway:defaultSchema}".resource_registry (
  resource_type TEXT COLLATE "C" NOT NULL,
  resource_id TEXT COLLATE "C" NOT NULL,
  tenant_id TEXT COLLATE "C" NOT NULL,
  owner_actor_id TEXT COLLATE "C" NOT NULL,
  lifecycle TEXT COLLATE "C" NOT NULL DEFAULT 'ACTIVE'
    CHECK (lifecycle IN ('ACTIVE', 'TOMBSTONED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
  tombstoned_at TIMESTAMPTZ,
  PRIMARY KEY (resource_type, resource_id), -- globally unique for existing V3 key
  UNIQUE (tenant_id, resource_type, resource_id),
  FOREIGN KEY (tenant_id) REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CHECK (btrim(resource_type) <> '' AND btrim(resource_id) <> ''),
  CHECK (btrim(owner_actor_id) <> ''),
  CHECK (
    (lifecycle = 'ACTIVE' AND tombstoned_at IS NULL) OR
    (lifecycle = 'TOMBSTONED' AND tombstoned_at IS NOT NULL)
  )
);

CREATE TABLE "${flyway:defaultSchema}".resource_acl (
  tenant_id TEXT COLLATE "C" NOT NULL,
  resource_type TEXT COLLATE "C" NOT NULL,
  resource_id TEXT COLLATE "C" NOT NULL,
  actor_id TEXT COLLATE "C" NOT NULL,
  operation TEXT COLLATE "C" NOT NULL
    CHECK (operation IN ('workflow:create', 'workflow:activate',
                        'workflow:transition', 'workflow:read')),
  granted BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, resource_type, resource_id, actor_id, operation),
  FOREIGN KEY (tenant_id, resource_type, resource_id)
    REFERENCES "${flyway:defaultSchema}".resource_registry(tenant_id, resource_type, resource_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  FOREIGN KEY (tenant_id, actor_id)
    REFERENCES "${flyway:defaultSchema}".tenant_memberships(tenant_id, actor_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
);

CREATE TABLE "${flyway:defaultSchema}".workflow_definition_bindings (
  tenant_id TEXT COLLATE "C" NOT NULL,
  resource_type TEXT COLLATE "C" NOT NULL,
  definition_id TEXT COLLATE "C" NOT NULL,
  definition_version BIGINT NOT NULL CHECK (definition_version >= 0),
  active BOOLEAN NOT NULL,
  PRIMARY KEY (tenant_id, resource_type, definition_id, definition_version),
  FOREIGN KEY (tenant_id) REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  FOREIGN KEY (definition_id, definition_version)
    REFERENCES "${flyway:defaultSchema}".workflow_definitions(definition_id, definition_version)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CHECK (btrim(resource_type) <> '' AND btrim(definition_id) <> '')
);

-- Candidate indexes; verify actual plans and mutation overhead.
CREATE INDEX resource_registry_tenant_lifecycle_idx
  ON "${flyway:defaultSchema}".resource_registry(tenant_id, lifecycle);
CREATE INDEX resource_acl_granted_lookup_idx
  ON "${flyway:defaultSchema}".resource_acl(tenant_id, actor_id, operation, resource_type, resource_id)
  WHERE granted;
CREATE INDEX memberships_active_actor_idx
  ON "${flyway:defaultSchema}".tenant_memberships(actor_id, tenant_id) WHERE active;
```

**Important missing constraints/enforcement (BLOCKERS):**
- SQL CHECK/FK alone cannot prevent updating a registry tenant/key, restoring a TOMBSTONED resource, deleting/recreating a guard row with no children, or bypassing guard locks. Need reviewed immutability/tombstone triggers or narrowly scoped functions, plus privilege enforcement.
- `owner_actor_id` is historical metadata, **not** proof of active membership. Do not FK it to an *active* membership; formerly active owners may remain. `NOT NULL` and owner-reassignment semantics need sign-off.
- Resolve whether `granted=false` or deleted grants + append-only audit govern revocation, and whether deactivated memberships retain ACL rows. No grant becomes valid with a revoked membership.
- Collations and SQL identity equality must preserve Identity's canonical `oidc:<base64url(UTF8(issuer))>.<base64url(UTF8(subject))>` without re-normalization. A SQL actor field is **not** an OIDC/JWT verifier; canonical principal comes exclusively from `CurrentPrincipal.requireVerified()`.
- V3 lacks registry FK. Handle historical rows with explicit operator-reviewed reconciliation. An optional additive `NOT VALID` FK and later validation cannot be assumed to work on dirty data. If consumer resource IDs are tenant-local, they require an authoritative globally unique namespacing contract before this schema.
- Confirm FK compatibility to V3 `workflow_definitions` (types, collations, schema privileges), key lengths, mutation/reindex cost, private namespace ownership, no resource/tenant ID reuse and tombstone recovery policy.

## 3. SQL roles and trust threat model (P0)

**Proposed production topology:**
- `workflow_schema_owner`: non-login owner of private schema/tables/approved trusted functions and protective triggers; **never** application credentials.
- `workflow_migrator`: controlled deployment identity for migrations only; no application connections.
- `workflow_runtime`: minimal `USAGE` and permission to call only vetted guarded SQL APIs; no unguarded ACL/membership/registry/guard mutator DML, table ownership, schema DDL, policy trigger replacement, or migration role membership.
- `workflow_policy_admin`: separate auditable actor for *vetted guard-acquiring policy mutation functions*, not unrestricted direct table editing. Enumerate provisioning, maintenance jobs, admin tools, backfills, owner changes, grant/revoke, definition binding and tombstone.

**P0 gap to resolve:** current `PostgresWorkflowPersistence` directly SELECTs/INSERTs/UPDATEs Workflow tables. If the online DB role retains unrestricted direct access, a compromised SQL credential can bypass application ACL. Either (A) migrate protected read/write operations into audited `SECURITY DEFINER` functions that enforce scoped guard checks and revoke direct grants, **or** (B) propose a different DB enforcement boundary with an explicit supported threat model and independent review. Do not call B "database-enforced ACL" while direct bypass exists. Harden `SECURITY DEFINER` with fixed `search_path=pg_catalog,pg_temp`, fully qualified identifiers, parameterized values, `REVOKE EXECUTE FROM PUBLIC`, and narrowly audited EXECUTE grants. Session variables such as `SET LOCAL app.actor` do not prove JWT identity and may be spoofed by a compromised app DB role.

**Required negative evidence:** attempt forbidden ACL mutation/SELECT, Workflow table read/write, ALTER/TRUNCATE, trigger disable, function invocation, role inheritance and default PUBLIC grants with the **actual runtime role**; verify database denies every unapproved path.

## 4. Same-physical-connection guarded transaction (T1 candidate)

**Do not change the public Core port.** Refactor JDBC SQL internally into reusable connection-bound operations, shared by existing persistence and a new **GuardedWorkflowUnitOfWork**. The transaction owner opens/closes exactly one verified physical JDBC connection and alone calls `commit()/rollback()`. Its transaction-scoped `WorkflowPersistencePort` adapter never closes/commits the connection. `WorkflowRuntime` is constructed/invoked with that scoped port **inside** the protected transaction; exclude a direct unguarded runtime from production HTTP wiring.

```java
// DESIGN ONLY. The actual interfaces, exception types and lifecycle need review.
interface GuardedWorkflowUnitOfWork {
  <T> T write(VerifiedWorkflowCaller caller, WorkflowTarget target,
              WorkflowOperation operation, Function<WorkflowRuntime,T> command);
  <T> T read(VerifiedWorkflowCaller caller, WorkflowTarget target,
             Function<WorkflowRuntime,T> query);
}
```

**WRITE protocol (candidate linearization point: guard lock + durable commit):**
1. At trusted HTTP/application boundary obtain `CurrentPrincipal.requireVerified()`; do not accept actor/tenant/owner/authority from request body, headers or request attributes. CREATE rejects caller-selected `instanceId` and generates opaque 192-bit ID internally; no second JWT verifier.
2. Open **one checked JDBC Connection**; `READ COMMITTED`; `autoCommit=false`; maintain existing `synchronous_commit=on`, `fsync` and `full_page_writes` safeguards. Configure finite lock/statement timeouts as reviewed.
3. Retrieve **minimal internal** candidate instance/resource/tenant projection, not protected snapshot/history. Lock its permanent `tenant_guards` row `FOR SHARE` **before** protected instance rows, then **re-read** authoritative resource, immutable tenant binding, membership, owner/visibility, exact ACL grant and CREATE definition binding. A missing/changed/orphan mapping denies; do not externally reveal candidate data.
4. Invoke the existing `WorkflowRuntime` with **connection-scoped port**. All its multiple reads, exact definition lookup and write/history operations share this connection; prevent nested independent commits. Instance row `FOR UPDATE` occurs **after** guard acquisition. Preserve optimistic runtime-version checks.
5. Commit **once** in the coordinator and report success only after confirmed commit; keep guard until transaction end. On known pre-commit failure confirm rollback, then propagate sanitized typed denial or domain error. If write COMMIT was attempted and outcome may be unknown, or write rollback fails, `COMMIT_OUTCOME_UNKNOWN` wins; **never auto-retry**.
6. Error bridge must not pass bare `WorkflowAccessException` through existing `PostgresWorkflowPersistence.transaction()` generic catch: currently it becomes `OPERATION_FAILURE`. Define a typed rollback result accessible only after confirmed rollback.

**READ/HISTORY:** use `READ COMMITTED` and same-connection `FOR SHARE` guard, recheck membership/visibility/READ privilege after lock, then materialize requested snapshot/history while guard remains held. A stale `REPEATABLE READ` snapshot initialized before waiting for revoke must not leak prior permissions. Return only fully detached immutable results after transaction completion; never return protected data from the pre-lock projection.

**POLICY MUTATION:** begin, acquire the **same tenant guard** `FOR UPDATE`, then child row locks in deterministic `tenant guard -> resource registry -> workflow instance -> history` order, mutate membership/grants/owner/binding/tombstone, increment `acl_revision` and commit. Multi-tenant admin mutations should either be excluded from MVP or lock sorted tenant IDs. Document FK-induced locks as well. All administrative/jobs/provisioning paths must use this protocol or be denied at SQL level.

**Race invariants to prove using two physical JDBC connections:**
- Revoke `FOR UPDATE` wins + commits first -> subsequent guarded write/read must deny and not mutate or materialize history.
- Guarded operation `FOR SHARE` wins first -> revoke cannot commit before operation finishes; then revoke proceeds.
- Same interleavings for membership, ownership, definition-binding CREATE, resource tombstone, READ/HISTORY. No `Thread.sleep` timing-only tests; use deterministic barriers, finite deadlines and post-commit fresh-connection state checks.

## 5. Decision and HTTP mapping (review required)

| Situation | Proposed classification | External result |
| --- | --- | --- |
| Missing/invalid/expired/wrong issuer or audience/forged token | Authentication | `401` sanitized, no Runtime call |
| Missing, hidden, other tenant, inactive membership, tombstone | HIDDEN | `404` identical status/body/headers to nonexistent |
| Visible target, but wrong authority or no exact ACL grant | FORBIDDEN | `403` sanitized |
| Policy null/throws, guarded DB inaccessible or timeout | UNAVAILABLE | sanitized non-success (candidate `503`; approval needed) |
| Known rollback after access denial | HIDDEN/FORBIDDEN/UNAVAILABLE | preserve typed sanitized status |
| Commit outcome uncertain, or failed write rollback | COMMIT_OUTCOME_UNKNOWN | distinct safe outcome (HTTP **TBD**) and **no auto-retry** |
| Generated ID collision / parallel insert after known rollback | Internal collision | no hidden ID oracle or response with colliding ID |

**Visibility ambiguity requiring explicit product/security sign-off:** proposed visible iff actor has active tenant membership **and** is owner **or** holds at least one explicit granted operation on the resource. A member with neither owner status nor any grant gets HIDDEN. Visible actor missing this operation's grant/authority gets FORBIDDEN. This is **not** in the already-approved product policy and must be ratified, including CREATE definition-binding visibility semantics. Hidden vs missing responses must match; evaluate timing, caches, Location header and body-shape leaks separately.

**Lost response:** choose a scoped idempotency/reconciliation contract before enabling CREATE. A possible 500/503 on uncertain commit must not invite automatic retry of a write that may already have committed. Do not add a hidden idempotency table to V5 without separate review.

## 6. Acceptance matrix for future disposable PostgreSQL/JWKS runs

| Group | Required test observation |
| --- | --- |
| DDL/integrity | FK/UNIQUE/checks, immutable tenant/global resource key, owner revocation, no resurrection/tombstone ID reuse; V3 unmapped instance fails closed |
| SQL privileges | Actual runtime role cannot bypass protected Workflow/ACL tables or mutate policy unguarded, including inherited/default privileges |
| ACL logic | Exact operation token authority AND resource grant AND membership, no implicit owner privilege, correct CREATE binding and existing-instance behavior |
| Lock races | Two independent physical JDBC connections; revoke-first, guarded-write-first, owner/membership/binding/tombstone, read/history coherence |
| Errors/commit | Known rollback preserves typed decision; injected unknown commit/failed rollback retains `COMMIT_OUTCOME_UNKNOWN`; never blind retry |
| Instance ID | Reject user-supplied ID, generated ID collision/concurrent insert, hidden-vs-missing oracle and lost-response policy |
| HTTP/JWKS | Genuine Spring filter chain + real JWKS; forged/wrong JWT/header and tenant tests; 401/403/404, all five HTTP paths and Request regressions |

All PostgreSQL experiments occur **only in disposable PostgreSQL 17.x**; never staging/shared Supabase. JUnit must assert no unauthorized instance/history effects, not just an exception. Existing full backend build at old HEAD is **not** proof of this proposed architecture.

## 7. Explicit independent sign-off requested before implementation

- **DB1:** approve existing private schema vs separate ACL schema, collation and V3 FK compatibility, global resource namespace and orphan V3 reconciliation.
- **DB2:** approve actual DDL, immutability/tombstone enforcement, indexes, owner/grant lifecycle and provisioning/deletion procedures.
- **DB3 (P0):** approve full role/GRANT/REVOKE and trusted function/RLS model with **no unguarded runtime SQL bypass**, including every admin/job mutation route.
- **TX1 (P0):** approve transaction owner, scoped-port design, same physical JDBC connection, guard lock order and coherent READ/HISTORY.
- **TX2 (P0):** approve typed confirmed-rollback bridge, unknown-COMMIT precedence and injected-failure tests.
- **SEC1:** ratify exact visibility/403 vs hidden 404, especially definition binding and members without grants.
- **API1:** ratify generated instance ID, no collision existence oracle, lost-response/idempotency and uncertain-write HTTP contract.

**Review verdict for v1: CHANGES REQUIRED BEFORE IMPLEMENTATION**, not a rejection of the Core design. Yến's D1–D5 agreement is retained; she should re-review the real `CurrentPrincipal.requireVerified() -> AuthorizedWorkflowService` integration **after it exists**, not approve this physical SQL. Independent PostgreSQL/security review must explicitly resolve DB1–DB3, TX1–TX2, SEC1 and API1. Until then, #23 OPEN, #36 DRAFT, #22 HTTP routes OFF; no database migration, production access, PR merge or code release.
