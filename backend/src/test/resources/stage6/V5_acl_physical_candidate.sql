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
    change_set JSONB NOT NULL CHECK (jsonb_typeof(change_set) = 'array' AND jsonb_array_length(change_set) > 0),
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

-- Physical immutability/append-only enforcement, still a TEST-ONLY D2 candidate.
-- This is ONE PRIVATE TRIGGER implementation, not a seventh wf_port_* SQL entrypoint.
-- SECURITY INVOKER and no PUBLIC EXECUTE: runtime SELECT/DML remains denied
-- by the separately reviewed role/grant design, not by these triggers alone.
CREATE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $stage6$
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'protected Stage 6 policy relations cannot be truncated' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'protected Stage 6 policy identities cannot be deleted' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF TG_TABLE_NAME = 'workflow_policy_audit' THEN
            RAISE EXCEPTION 'policy audit is append-only' USING ERRCODE = '23514';
        ELSIF TG_TABLE_NAME = 'tenant_guards' THEN
            IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
                    OR NEW.acl_revision < OLD.acl_revision THEN
                RAISE EXCEPTION 'immutable tenant key or decreasing ACL revision' USING ERRCODE = '23514';
            END IF;
        ELSIF TG_TABLE_NAME = 'tenant_memberships' THEN
            IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
                    OR NEW.actor_id IS DISTINCT FROM OLD.actor_id THEN
                RAISE EXCEPTION 'membership identity is immutable' USING ERRCODE = '23514';
            END IF;
        ELSIF TG_TABLE_NAME = 'resource_registry' THEN
            IF NEW.resource_type IS DISTINCT FROM OLD.resource_type
                    OR NEW.resource_id IS DISTINCT FROM OLD.resource_id
                    OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
                    OR NEW.created_at IS DISTINCT FROM OLD.created_at
                    OR (OLD.lifecycle = 'TOMBSTONED' AND
                        (NEW.lifecycle <> 'TOMBSTONED'
                         OR NEW.tombstoned_at IS DISTINCT FROM OLD.tombstoned_at)) THEN
                RAISE EXCEPTION 'immutable resource key, tenant or tombstone' USING ERRCODE = '23514';
            END IF;
        ELSIF TG_TABLE_NAME = 'resource_acl' THEN
            IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
                    OR NEW.resource_type IS DISTINCT FROM OLD.resource_type
                    OR NEW.resource_id IS DISTINCT FROM OLD.resource_id
                    OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
                    OR NEW.operation IS DISTINCT FROM OLD.operation THEN
                RAISE EXCEPTION 'ACL identity is immutable' USING ERRCODE = '23514';
            END IF;
        ELSIF TG_TABLE_NAME = 'workflow_definition_bindings' THEN
            IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
                    OR NEW.resource_type IS DISTINCT FROM OLD.resource_type
                    OR NEW.definition_id IS DISTINCT FROM OLD.definition_id
                    OR NEW.definition_version IS DISTINCT FROM OLD.definition_version THEN
                RAISE EXCEPTION 'definition binding identity is immutable' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$stage6$;

-- Keep identity rows durable. Admin lifecycle changes use UPDATE through guarded
-- policy operations later; no DELETE/TRUNCATE or primary-key rewrites here.
CREATE TRIGGER s6_guards_immutable BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".tenant_guards
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_members_immutable BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".tenant_memberships
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_registry_immutable BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".resource_registry
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_acl_immutable BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".resource_acl
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_bindings_immutable BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_definition_bindings
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_audit_append_only BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_policy_audit
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();

CREATE TRIGGER s6_guards_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".tenant_guards
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_members_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".tenant_memberships
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_registry_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".resource_registry
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_acl_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".resource_acl
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_bindings_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".workflow_definition_bindings
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();
CREATE TRIGGER s6_audit_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".workflow_policy_audit
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard();

REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".stage6_policy_immutability_guard() FROM PUBLIC;
REVOKE ALL ON
    "${flyway:defaultSchema}".tenant_guards,
    "${flyway:defaultSchema}".tenant_memberships,
    "${flyway:defaultSchema}".resource_registry,
    "${flyway:defaultSchema}".resource_acl,
    "${flyway:defaultSchema}".workflow_definition_bindings,
    "${flyway:defaultSchema}".workflow_policy_audit
FROM PUBLIC;

-- OPEN physical review obligations (not claimed here):
-- * immutable tenant/resource/owner identity, no DELETE/reuse/resurrection;
-- * active owner membership invariant under concurrent deactivation/reassignment;
-- * permanent guard FOR SHARE/FOR UPDATE discipline, privilege/SECURITY DEFINER;
-- * atomic policy + acl_revision + append-only audit, and confirmed COMMIT;
-- * current V3 collations/FK compatibility, actual LOGIN-role negative tests;
-- * synthetic tenant fixture provenance, empty V3 inventory and fenced writer cutover.
