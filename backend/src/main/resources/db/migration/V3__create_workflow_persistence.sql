-- Generic Workflow persistence. Request prototype tables (V1/V2) remain untouched.
-- Instants are represented as epoch seconds + nanoseconds to retain exact Java Instant values.
-- Definitions are immutable once provisioned; provisioner/adapter must reject same-key content changes.
CREATE TABLE workflow_definitions (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL CHECK (definition_version >= 0),
    initial_state TEXT NOT NULL CHECK (length(trim(initial_state)) > 0),
    PRIMARY KEY (definition_id, definition_version),
    CHECK (length(trim(definition_id)) > 0)
);

CREATE TABLE workflow_definition_states (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    state_id TEXT NOT NULL CHECK (length(trim(state_id)) > 0),
    PRIMARY KEY (definition_id, definition_version, state_id),
    FOREIGN KEY (definition_id, definition_version)
        REFERENCES workflow_definitions(definition_id, definition_version)
        ON DELETE RESTRICT
);

CREATE TABLE workflow_definition_transitions (
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    transition_id TEXT NOT NULL CHECK (length(trim(transition_id)) > 0),
    source_state TEXT NOT NULL,
    target_state TEXT NOT NULL,
    PRIMARY KEY (definition_id, definition_version, transition_id),
    FOREIGN KEY (definition_id, definition_version, source_state)
        REFERENCES workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (definition_id, definition_version, target_state)
        REFERENCES workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT
);

CREATE TABLE workflow_instances (
    instance_id TEXT PRIMARY KEY CHECK (length(trim(instance_id)) > 0),
    definition_id TEXT NOT NULL,
    definition_version BIGINT NOT NULL,
    resource_type TEXT NOT NULL CHECK (length(trim(resource_type)) > 0),
    resource_id TEXT NOT NULL CHECK (length(trim(resource_id)) > 0),
    current_state TEXT NOT NULL,
    runtime_version BIGINT NOT NULL DEFAULT 0 CHECK (runtime_version >= 0),
    lifecycle TEXT NOT NULL CHECK (lifecycle IN ('NOT_STARTED', 'ACTIVE', 'COMPLETED')),
    created_at_seconds BIGINT NOT NULL,
    created_at_nanos INTEGER NOT NULL CHECK (created_at_nanos BETWEEN 0 AND 999999999),
    updated_at_seconds BIGINT NOT NULL,
    updated_at_nanos INTEGER NOT NULL CHECK (updated_at_nanos BETWEEN 0 AND 999999999),
    FOREIGN KEY (definition_id, definition_version, current_state)
        REFERENCES workflow_definition_states(definition_id, definition_version, state_id)
        ON DELETE RESTRICT,
    CHECK ((updated_at_seconds, updated_at_nanos) >= (created_at_seconds, created_at_nanos))
);

CREATE INDEX workflow_instances_resource_idx
    ON workflow_instances(resource_type, resource_id);

CREATE TABLE workflow_transition_history (
    instance_id TEXT NOT NULL REFERENCES workflow_instances(instance_id) ON DELETE RESTRICT,
    resulting_runtime_version BIGINT NOT NULL CHECK (resulting_runtime_version > 0),
    transition_id TEXT NOT NULL CHECK (length(trim(transition_id)) > 0),
    source_state TEXT NOT NULL CHECK (length(trim(source_state)) > 0),
    target_state TEXT NOT NULL CHECK (length(trim(target_state)) > 0),
    actor_id TEXT NOT NULL CHECK (length(trim(actor_id)) > 0),
    occurred_at_seconds BIGINT NOT NULL,
    occurred_at_nanos INTEGER NOT NULL CHECK (occurred_at_nanos BETWEEN 0 AND 999999999),
    PRIMARY KEY (instance_id, resulting_runtime_version)
);

-- Prevent accidental modification of provisioned definitions. This also protects pinned
-- version semantics when a future administrator has SQL access.
CREATE FUNCTION reject_workflow_definition_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'workflow definition content is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER workflow_definitions_immutable
    BEFORE UPDATE OR DELETE ON workflow_definitions
    FOR EACH ROW EXECUTE FUNCTION reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_states_immutable
    BEFORE UPDATE OR DELETE ON workflow_definition_states
    FOR EACH ROW EXECUTE FUNCTION reject_workflow_definition_change();
CREATE TRIGGER workflow_definition_transitions_immutable
    BEFORE UPDATE OR DELETE ON workflow_definition_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_workflow_definition_change();

-- Initial state membership is verified by the provisioning operation after inserting states;
-- no definition changes are permitted after provisioning completes.
