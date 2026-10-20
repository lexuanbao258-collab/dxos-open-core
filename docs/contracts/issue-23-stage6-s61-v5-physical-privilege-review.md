# Issue #23 S6.1 — V5 physical design, SQL privileges and actor-proof review baseline

**REVIEW CANDIDATE ONLY. NOT executable SQL, Flyway V5, role provisioning or production approval.** This accompanies the S6.1 transaction contract and the owner-approved [DEC-VIS / DEC-CREATE](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6280476807). Existing Workflow V3, V4, Core API and identity mapping remain unchanged.

## DB1: Five-table physical model (proposed, not migrated)

Use the existing private Workflow Flyway schema (not public), subject to schema-guard/manifest and PostgreSQL 17 collation compatibility.

| Table | PK / core fields | Required FK, CHECK, UNIQUE and immutability |
|---|---|---|
| tenant_guards | tenant_id TEXT PK, acl_revision BIGINT default 0 | Nonblank tenant; nonnegative monotonic revision. Permanent row: never DELETE, reinsert, reuse or move tenant ID. Every authorized read/write holds FOR SHARE, every membership/ACL/owner/binding/tombstone mutator locks same guard FOR UPDATE. |
| tenant_memberships | (tenant_id,actor_id) PK, active BOOLEAN NOT NULL | tenant_id FK to tenant_guards ON UPDATE/DELETE RESTRICT; actor_id exact canonical OIDC identity, nonblank. Inactive membership invalidates all grants even if rows persist. |
| resource_registry | (resource_type,resource_id) **global** PK; tenant_id,owner_actor_id,lifecycle,created_at,tombstoned_at | Tenant FK RESTRICT; UNIQUE(tenant_id,resource_type,resource_id) for ACL FK. Nonblank IDs/owner; ACTIVE iff tombstoned_at null, TOMBSTONED iff timestamp present. Tenant/key immutable; never delete/re-register or resurrect a tombstone. Owner field is not authority or an operation grant. |
| resource_acl | (tenant_id,resource_type,resource_id,actor_id,operation) PK; granted BOOLEAN NOT NULL | Composite FK to registry and membership (tenant_id,actor_id); only the four exact values workflow:create, workflow:activate, workflow:transition, workflow:read. Positive grant never overrides inactive membership/tombstone or missing JWT operation authority. |
| workflow_definition_bindings | (tenant_id,resource_type,definition_id,definition_version) PK; active BOOLEAN NOT NULL | Tenant FK + exact (definition_id,definition_version) FK to V3 workflow_definitions, RESTRICT. Nonblank resource/definition IDs; nonnegative version. Exact active binding mandatory only for new CREATE; cannot alias or silently pick latest version. |

**Constraints requiring independent physical sign-off:** CHECK/FK alone cannot prevent deleting/recreating guard, resource key/tenant UPDATE, resource tombstone reversal or SQL-based ACL bypass. Design reviewed immutability triggers AND role-restricted narrow mutation functions. Determine FK collation/operator-class behavior against existing V3 definitions before selecting COLLATE "C"; check Unicode identity equality, indexes, and V1–V4 pinned manifests. Do not change V1–V4. Proposed indexes only after EXPLAIN/representative disposable PG17 benchmark.

**Legacy V3:** Workflow instances persist globally keyed instance ID and resource_type/resource_id with no tenant_id. An unmatched or ambiguous resource registry mapping is HIDDEN. Global (resource_type,resource_id) uniqueness across actual consumers must be established before DDL. Legacy data backfill needs a reviewed authoritative mapping manifest, controlled operator reconciliation and FK validation evidence. Never infer a tenant or owner from HTTP payload, assignee or old instance; never auto-backfill dirty rows.

## DB2: Mandatory trusted policy mutation APIs

Only enumerated, separately authenticated policy-admin/provisioner SQL APIs may create tenants, register/tombstone resources, grant/revoke membership/ACL, change owner within same tenant, activate/deactivate exact definition binding, and provision immutable definitions. Every such API MUST:
1. Begin a checked physical READ COMMITTED JDBC transaction and lock the permanent tenant guard row FOR UPDATE **before** any protected dependent row or FK lock.
2. Recheck admin/provisioner authorization and immutable target; mutate only its bound tenant; increment acl_revision and write audit record on same transaction.
3. COMMIT or confirmed ROLLBACK while holding the guard; no savepoint rollback releasing guard then continuing without reacquisition and a full policy recheck.
4. Reject cross-tenant transfer, key reuse, tombstone resurrection and unreviewed generic SQL executor. Explicitly review reactivation to prevent stale grants reviving unintentionally. Multi-tenant mutations excluded from MVP unless deterministic sorted-guard locking is reviewed.

Guard/row lock order for all operations and admin jobs: tenant guard -> resource -> instance -> history, including FK and trigger locks.

## SQL-A: Effective role/privilege matrix (proposed)

| SQL role | V3/V5 direct protected tables | Schema/DDL/role escalation | Permitted functions | Signer/verifier material |
|---|---|---|---|---|
| dxos_wf_owner (NOLOGIN) | Owner internally | Owner only, never an online login | Owns audited definer/private helpers | DB verification key custody only under reviewed owner/private mechanism |
| dxos_wf_migrator (offline) | Controlled deployment | Approved additive migration only; never in HTTP pool | Controlled install only | No request signer |
| dxos_wf_runtime (LOGIN) | **NO** protected SELECT/INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER/MAINTAIN | **NO** schema CREATE, ALTER, DISABLE TRIGGER, superuser/BYPASSRLS, owner inheritance or SET ROLE | EXECUTE only the **six** reviewed wf_port_* exact signatures | **NO** key, private helper, signer secret |
| dxos_wf_policy_admin | **NO** direct table DML/SELECT | **NO** owner/migrator escalation | EXECUTE only audited wf_policy_* with guard/mutation proof | Dedicated admin authentication |
| dxos_wf_provisioner | **NO** direct protected DML/SELECT | **NO** owner/migrator escalation | EXECUTE only audited wf_provision_* | Dedicated provisioner authentication |
| PUBLIC, Supabase API roles, unrelated Request roles | **NO** | **NO** | **NO** Workflow protected EXECUTE | **NO** |

SQL audit MUST verify effective permissions including inherited role membership, role SET privilege, default PUBLIC function EXECUTE, schema USAGE/CREATE, all current V3 and planned V5 tables, exact overloaded function signatures, pg_auth_members, pg_proc, pg_default_acl and test negative SQL as actual runtime login. ALTER/TRIGGER disable derive from ownership, not simply a table GRANT.

SECURITY DEFINER functions must be owned by a non-login role; pin search_path to pg_catalog,pg_temp; fully qualify protected references; ban untrusted dynamic SQL; REVOKE PUBLIC EXECUTE **in the creation transaction**, then GRANT narrow EXECUTE per signature. Review globally set function creator default privileges and existing grants. Current PostgresWorkflowPersistence uses direct V3 SQL: REVOKE/switchover before the guarded adapter is ready would break the backend. Do not perform it.

## SQL-B: Caller proof design (P0 — NOT approved)

Keep ADR-0024's conditional **HMAC-SHA-256** candidate for the explicit stronger threat: stolen runtime SQL credentials alone must not forge another principal or read/mutate protected rows. An actor string, boolean, arbitrary JWT parameter or GUC is not DB-verifiable. The proof is created by a trusted signer only *after* CurrentPrincipal.requireVerified(), with key custody separate from runtime DB password.

Exact proposed typed binary envelope includes domain/version/kid/deployment id; strict UTF-8 verified issuer+subject and canonical oidc identity, exact authority + high-level operation + one of six port actions, bound instance/resource/exact definition/transition and full canonical proposal digest, the **actual same JDBC backend PID and transaction XID**, issuance/expiration and >=128-bit nonce. Bind/verify typed absence explicitly. One checked physical transaction per port method has a new proof; a preliminary read proof is never reused by a final write. SQL must derive actor/target/proposal binding independently from typed inputs, check MAC/replay/expiry/transaction identity before protected materialization and recheck expiry/current ACL after taking guard.

**Independent P0 review required:** precise versioned length-prefixed field encoding/order and strict malformed UTF-16 rejection, cross-language Java/PostgreSQL non-ASCII test vectors, signer and DB-held secret isolation, key rotation/revocation, timing-aware MAC check, expiry under lock waits, replay/nonce policy, transaction pooler backend affinity, key/actor logging audit and negative SQL tests using ONLY stolen-runtime-role capabilities. Previous PR #36 HMAC and V3 parity fixtures are separate, test-only demonstrations; do not claim combined protection. If the team elects a weaker trusted-backend threat model, write a separate explicit security ADR and disclose lost DB-credential compromise protection before implementation.

## Disposition

**Design baseline provided for independent review.** Physical FK/collation, privilege effectiveness, immutable mutation enforcement, SQL HMAC/key custody and real PostgreSQL 17 evidence are **BLOCKED**. S6.2 execution cannot begin from this file alone. No Flyway V5, live SQL, shared staging/Supabase, production HTTP or PR merge is authorized.
