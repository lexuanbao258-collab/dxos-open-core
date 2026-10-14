-- Generic Workflow persistence. Request prototype tables (V1/V2) remain untouched.
-- Instants are represented as epoch seconds + nanoseconds to retain exact Java Instant values.
-- Definitions are immutable once provisioned; provisioner/adapter must reject same-key content changes.
CREATE TABLE "${flyway:defaultSchema}".workflow_definitions (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL CHECK (definition_version >= 0),
    initial_state TEXT NOT NULL CHECK (length(trim(initial_state)) > 0),
    provisioning_transaction BIGINT NOT NULL DEFAULT pg_catalog.txid_current(),
    PRIMARY KEY (definition_id, definition_version),
    CHECK (length(trim(definition_id)) > 0)
);

CREATE TABLE "${flyway:defaultSchema}".workflow_definition_states (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    state_id TEXT NOT NULL CHECK (length(trim(state_id)) > 0),
    PRIMARY KEY (definition_id, definition_version, state_id),
    FOREIGN KEY (definition_id, definition_version)
        REFERENCES "${flyway:defaultSchema}".workflow_definitions(definition_id, definition_version)
        ON DELETE RESTRICT
);

CREATE TABLE "${flyway:defaultSchema}".workflow_definition_transitions (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    transition_id TEXT NOT NULL CHECK (length(trim(transition_id)) > 0),
    source_state TEXT NOT NULL,
    target_state TEXT NOT NULL,
    PRIMARY KEY (definition_id, definition_version, transition_id),
    FOREIGN KEY (definition_id, definition_version, source_state)
        REFERENCES "${flyway:defaultSchema}".workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (definition_id, definition_version, target_state)
        REFERENCES "${flyway:defaultSchema}".workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT
);

ALTER TABLE "${flyway:defaultSchema}".workflow_definitions ADD CONSTRAINT workflow_initial_state_membership
    FOREIGN KEY (definition_id, definition_version, initial_state)
    REFERENCES "${flyway:defaultSchema}".workflow_definition_states(definition_id, definition_version, state_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE "${flyway:defaultSchema}".workflow_instances (
    instance_id TEXT PRIMARY KEY CHECK (length(trim(instance_id)) > 0),
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    resource_type TEXT NOT NULL CHECK (length(trim(resource_type)) > 0),
    resource_id TEXT NOT NULL CHECK (length(trim(resource_id)) > 0),
    current_state TEXT NOT NULL,
    runtime_version BIGINT NOT NULL DEFAULT 0 CHECK (runtime_version >= 0),
    lifecycle TEXT NOT NULL CHECK (lifecycle IN ('NOT_STARTED', 'ACTIVE', 'COMPLETED')),
    created_at_seconds BIGINT NOT NULL CHECK (created_at_seconds BETWEEN -31557014167219200 AND 31556889864403199),
    created_at_nanos INTEGER NOT NULL CHECK (created_at_nanos BETWEEN 0 AND 999999999),
    updated_at_seconds BIGINT NOT NULL CHECK (updated_at_seconds BETWEEN -31557014167219200 AND 31556889864403199),
    updated_at_nanos INTEGER NOT NULL CHECK (updated_at_nanos BETWEEN 0 AND 999999999),
    FOREIGN KEY (definition_id, definition_version, current_state)
        REFERENCES "${flyway:defaultSchema}".workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT,
    CHECK ((updated_at_seconds, updated_at_nanos) >= (created_at_seconds, created_at_nanos))
);

CREATE INDEX workflow_instances_resource_idx
    ON "${flyway:defaultSchema}".workflow_instances(resource_type, resource_id);

CREATE TABLE "${flyway:defaultSchema}".workflow_transition_history (
    instance_id TEXT NOT NULL REFERENCES "${flyway:defaultSchema}".workflow_instances(instance_id) ON DELETE RESTRICT,
    resulting_runtime_version BIGINT NOT NULL CHECK (resulting_runtime_version > 0),
    transition_id TEXT NOT NULL CHECK (length(trim(transition_id)) > 0),
    source_state TEXT NOT NULL CHECK (length(trim(source_state)) > 0),
    target_state TEXT NOT NULL CHECK (length(trim(target_state)) > 0),
    actor_id TEXT NOT NULL CHECK (length(trim(actor_id)) > 0),
    occurred_at_seconds BIGINT NOT NULL CHECK (occurred_at_seconds BETWEEN -31557014167219200 AND 31556889864403199),
    occurred_at_nanos INTEGER NOT NULL CHECK (occurred_at_nanos BETWEEN 0 AND 999999999),
    PRIMARY KEY (instance_id, resulting_runtime_version)
);

-- Prevent accidental modification of provisioned definitions. This also protects pinned
-- version semantics when a future administrator has SQL access.
CREATE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    RAISE EXCEPTION 'workflow definition content is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER workflow_definitions_immutable
    BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_definitions
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_states_immutable
    BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_definition_states
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_transitions_immutable
    BEFORE UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_definition_transitions
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();

-- Definition content is inserted in one transaction and sealed after commit.
-- Owners/superusers can disable triggers; runtime roles must not own these tables.
CREATE TRIGGER workflow_definitions_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".workflow_definitions
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_states_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".workflow_definition_states
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_transitions_no_truncate BEFORE TRUNCATE ON "${flyway:defaultSchema}".workflow_definition_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change();

CREATE FUNCTION "${flyway:defaultSchema}".require_workflow_definition_provisioning() RETURNS trigger LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF TG_TABLE_NAME = 'workflow_definitions' THEN
        NEW.provisioning_transaction := pg_catalog.txid_current();
        RETURN NEW;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM "${flyway:defaultSchema}".workflow_definitions
        WHERE definition_id = NEW.definition_id AND definition_version = NEW.definition_version
          AND provisioning_transaction = pg_catalog.txid_current()
    ) THEN
        RAISE EXCEPTION 'workflow definition content is sealed' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER workflow_definition_states_sealed BEFORE INSERT ON "${flyway:defaultSchema}".workflow_definition_states
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".require_workflow_definition_provisioning();
CREATE TRIGGER workflow_definition_transitions_sealed BEFORE INSERT ON "${flyway:defaultSchema}".workflow_definition_transitions
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".require_workflow_definition_provisioning();

CREATE TRIGGER workflow_definitions_provisioning BEFORE INSERT ON "${flyway:defaultSchema}".workflow_definitions
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".require_workflow_definition_provisioning();

REVOKE ALL ON "${flyway:defaultSchema}".workflow_definitions, "${flyway:defaultSchema}".workflow_definition_states, "${flyway:defaultSchema}".workflow_definition_transitions,
    "${flyway:defaultSchema}".workflow_instances, "${flyway:defaultSchema}".workflow_transition_history FROM PUBLIC;

REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".reject_workflow_definition_change(),
    "${flyway:defaultSchema}".require_workflow_definition_provisioning() FROM PUBLIC;

-- Supabase can grant API roles access through default privileges; these tables are backend-only.
DO $$
DECLARE api_role TEXT;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = api_role) THEN
            EXECUTE pg_catalog.format('REVOKE ALL ON "${flyway:defaultSchema}".workflow_definitions, "${flyway:defaultSchema}".workflow_definition_states,
                "${flyway:defaultSchema}".workflow_definition_transitions, "${flyway:defaultSchema}".workflow_instances, "${flyway:defaultSchema}".workflow_transition_history FROM %I', api_role);
            EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION
                "${flyway:defaultSchema}".reject_workflow_definition_change(),
                "${flyway:defaultSchema}".require_workflow_definition_provisioning() FROM %I', api_role);
        END IF;
    END LOOP;
END;
$$;
