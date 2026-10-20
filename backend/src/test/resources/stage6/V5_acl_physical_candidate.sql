-- Stage 6 OLP MVP: isolated PostgreSQL 17 physical-schema CANDIDATE.
-- TEST RESOURCE ONLY: NOT a Flyway migration, not applied by the application.
-- Execute only on an isolated disposable database after the D2 security/DDL review.
-- Requires the existing V3 workflow_definitions relation, with its original collation.
-- No changes to Keycloak, verified OIDC, CurrentPrincipal, JWT, or Identity tables.
-- No roles, grants, security-definer functions, policies, or HTTP exposure here.
-- IMPORTANT: constraints below alone do NOT implement immutability, guarded mutations,
-- append-only auditing, runtime EXECUTE-only, or the final ACL-before-COMMIT check.
-- The complete Stage 6.2 gate stays BLOCKED until those are reviewed and implemented.

CREATE TABLE "${flyway:defaultSchema}".tenant_guards (
    tenant_id TEXT PRIMARY KEY CHECK (length(btrim(tenant_id)) > 0),
    acl_revision BIGINT NOT NULL DEFAULT 0 CHECK (acl_revision >= 0)
);

CREATE TABLE "${flyway:defaultSchema}".tenant_memberships (
    tenant_id TEXT NOT NULL REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    actor_id TEXT NOT NULL CHECK (length(btrim(actor_id)) > 0),
    active BOOLEAN NOT NULL,
    PRIMARY KEY (tenant_id, actor_id)
);

CREATE TABLE "${flyway:defaultSchema}".resource_registry (
    resource_type TEXT NOT NULL CHECK (length(btrim(resource_type)) > 0),
    resource_id TEXT NOT NULL CHECK (length(btrim(resource_id)) > 0),
    tenant_id TEXT NOT NULL REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    owner_actor_id TEXT NOT NULL CHECK (length(btrim(owner_actor_id)) > 0),
    lifecycle TEXT NOT NULL CHECK (lifecycle IN ('ACTIVE', 'TOMBSTONED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    tombstoned_at TIMESTAMPTZ,
    PRIMARY KEY (resource_type, resource_id),
    UNIQUE (tenant_id, resource_type, resource_id),
    FOREIGN KEY (tenant_id, owner_actor_id)
        REFERENCES "${flyway:defaultSchema}".tenant_memberships(tenant_id, actor_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CHECK ((lifecycle = 'ACTIVE' AND tombstoned_at IS NULL)
        OR (lifecycle = 'TOMBSTONED' AND tombstoned_at IS NOT NULL)),
    CHECK (tombstoned_at IS NULL OR tombstoned_at >= created_at)
);

CREATE TABLE "${flyway:defaultSchema}".resource_acl (
    tenant_id TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN (
        'workflow:create', 'workflow:activate', 'workflow:transition', 'workflow:read'
    )),
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
    tenant_id TEXT NOT NULL REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    resource_type TEXT NOT NULL CHECK (length(btrim(resource_type)) > 0),
    definition_id TEXT NOT NULL CHECK (length(btrim(definition_id)) > 0),
    definition_version BIGINT NOT NULL CHECK (definition_version >= 0),
    active BOOLEAN NOT NULL,
    PRIMARY KEY (tenant_id, resource_type, definition_id, definition_version),
    FOREIGN KEY (definition_id, definition_version)
        REFERENCES "${flyway:defaultSchema}".workflow_definitions(definition_id, definition_version)
        ON UPDATE RESTRICT ON DELETE RESTRICT
);

-- Policy-admin audit, distinct from V3 Workflow transition_history.
CREATE TABLE "${flyway:defaultSchema}".workflow_policy_audit (
    audit_event_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL REFERENCES "${flyway:defaultSchema}".tenant_guards(tenant_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    previous_acl_revision BIGINT,
    resulting_acl_revision BIGINT NOT NULL CHECK (resulting_acl_revision >= 0),
    actor_id TEXT NOT NULL CHECK (length(btrim(actor_id)) > 0),
    actor_source_ref TEXT NOT NULL CHECK (length(btrim(actor_source_ref)) > 0),
    action TEXT NOT NULL CHECK (length(btrim(action)) > 0),
    change_set JSONB NOT NULL CHECK (jsonb_typeof(change_set) = 'array'),
    reason_or_approval_ref TEXT,
    correlation_ref TEXT,
    manifest_digest TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, resulting_acl_revision),
    CHECK ((previous_acl_revision IS NULL AND resulting_acl_revision = 0)
        OR (previous_acl_revision IS NOT NULL
            AND previous_acl_revision >= 0
            AND resulting_acl_revision = previous_acl_revision + 1))
);

-- OPEN physical review obligations (not claimed here):
-- * immutable tenant/resource/owner identity, no DELETE/reuse/resurrection;
-- * active owner membership invariant under concurrent deactivation/reassignment;
-- * permanent guard FOR SHARE/FOR UPDATE discipline, privilege/SECURITY DEFINER;
-- * atomic policy + acl_revision + append-only audit, and confirmed COMMIT;
-- * current V3 collations/FK compatibility, actual LOGIN-role negative tests;
-- * synthetic tenant fixture provenance, empty V3 inventory and fenced writer cutover.
