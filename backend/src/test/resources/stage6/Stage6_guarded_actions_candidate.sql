-- STAGE 6.7 ISOLATED TEST-ONLY SQL CANDIDATE. Not a Flyway migration.
-- Two PROVISIONAL signatures for the already proposed six-port ACTIVATE/TRANSITION identities;
-- not additional SQL entrypoints, no finalized D3 parameter/return approval, no HTTP wiring/grants.
-- Verified backend must supply canonical actor and exact operation authority, not the caller.
-- Each call must own a READ COMMITTED transaction and return only after confirmed COMMIT.
-- Cross-transaction policy mutators MUST use tenant_guards FOR UPDATE.
-- Under ADR-0025 Option B, stolen backend DB credentials can still forge actor claims.
-- These SQL fixtures alone DO NOT certify SECURITY DEFINER privileges, login-role grants,
-- production atomicity across business data, Yến's R1-R10 or E2E readiness.

CREATE FUNCTION "${flyway:defaultSchema}".wf_port_commit_activation(
    p_actor_id TEXT, p_authority_present BOOLEAN, p_instance_id TEXT,
    p_expected_version BIGINT, p_when_seconds BIGINT, p_when_nanos INTEGER
)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $stage6_activate$
DECLARE
    v_resource_type TEXT;
    v_resource_id TEXT;
    v_tenant TEXT;
    v_owner TEXT;
    v_resource_lifecycle TEXT;
    v_member_active BOOLEAN;
    v_has_some_grant BOOLEAN;
    v_has_action_grant BOOLEAN;
    v_instance "${flyway:defaultSchema}".workflow_instances%ROWTYPE;
BEGIN

    IF p_actor_id IS NULL OR btrim(p_actor_id) = ''
       OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id) = ''
       OR p_expected_version IS NULL OR p_expected_version < 0
       OR p_when_seconds IS NULL
       OR p_when_seconds NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR p_when_nanos IS NULL OR p_when_nanos NOT BETWEEN 0 AND 999999999 THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- The first locator contains no protected state/history/definition payload.
    SELECT i.resource_type, i.resource_id INTO v_resource_type, v_resource_id
      FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id = p_instance_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT r.tenant_id INTO v_tenant
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type AND r.resource_id = v_resource_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- Policy mutators must acquire this SAME permanent guard FOR UPDATE.
    -- READ COMMITTED provides fresh data after a revoke releases this lock.
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g
     WHERE g.tenant_id = v_tenant FOR SHARE;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT r.owner_actor_id, r.lifecycle INTO v_owner, v_resource_lifecycle
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type
       AND r.resource_id = v_resource_id AND r.tenant_id = v_tenant;
    IF NOT FOUND OR v_resource_lifecycle <> 'ACTIVE' THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT m.active INTO v_member_active
      FROM "${flyway:defaultSchema}".tenant_memberships m
     WHERE m.tenant_id = v_tenant AND m.actor_id = p_actor_id;
    IF NOT FOUND OR NOT v_member_active THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT EXISTS(
        SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
         WHERE a.tenant_id = v_tenant
           AND a.resource_type = v_resource_type AND a.resource_id = v_resource_id
           AND a.actor_id = p_actor_id AND a.granted
    ) INTO v_has_some_grant;
    IF v_owner <> p_actor_id AND NOT v_has_some_grant THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT EXISTS(
        SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
         WHERE a.tenant_id = v_tenant
           AND a.resource_type = v_resource_type AND a.resource_id = v_resource_id
           AND a.actor_id = p_actor_id AND a.operation = 'workflow:activate' AND a.granted
    ) INTO v_has_action_grant;
    IF NOT v_has_action_grant OR NOT p_authority_present THEN
        RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- No instance lock or protected payload is materialized before authorization.
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i
     WHERE i.instance_id = p_instance_id FOR UPDATE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;
    IF v_instance.runtime_version IS DISTINCT FROM p_expected_version THEN
        RETURN QUERY SELECT 'CONFLICT'::TEXT, NULL::JSONB; RETURN;
    END IF;
    IF (p_when_seconds,p_when_nanos) <
       (v_instance.updated_at_seconds,v_instance.updated_at_nanos) THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;

    IF v_instance.lifecycle <> 'NOT_STARTED' OR v_instance.runtime_version <> 0 THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;
    -- An initial terminal state cannot be activated in Workflow Core.
    IF NOT EXISTS (
        SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_transitions t
         WHERE t.definition_id = v_instance.definition_id
           AND t.definition_version = v_instance.definition_version
           AND t.source_state = v_instance.current_state
    ) THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;

    UPDATE "${flyway:defaultSchema}".workflow_instances i
       SET lifecycle = 'ACTIVE',
           updated_at_seconds = p_when_seconds,
           updated_at_nanos = p_when_nanos
     WHERE i.instance_id = p_instance_id RETURNING i.* INTO v_instance;
    RETURN QUERY SELECT 'SNAPSHOT'::TEXT, to_jsonb(v_instance); RETURN;
END;
$stage6_activate$;

CREATE FUNCTION "${flyway:defaultSchema}".wf_port_commit_transition(
    p_actor_id TEXT, p_authority_present BOOLEAN, p_instance_id TEXT,
    p_expected_version BIGINT, p_transition_id TEXT,
    p_when_seconds BIGINT, p_when_nanos INTEGER
)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $stage6_transition$
DECLARE
    v_resource_type TEXT;
    v_resource_id TEXT;
    v_tenant TEXT;
    v_owner TEXT;
    v_resource_lifecycle TEXT;
    v_member_active BOOLEAN;
    v_has_some_grant BOOLEAN;
    v_has_action_grant BOOLEAN;
    v_instance "${flyway:defaultSchema}".workflow_instances%ROWTYPE;
    v_source TEXT;
    v_target TEXT;
    v_new_lifecycle TEXT;
BEGIN

    IF p_actor_id IS NULL OR btrim(p_actor_id) = ''
       OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id) = ''
       OR p_expected_version IS NULL OR p_expected_version < 0
       OR p_when_seconds IS NULL
       OR p_when_seconds NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR p_when_nanos IS NULL OR p_when_nanos NOT BETWEEN 0 AND 999999999
       OR p_transition_id IS NULL OR btrim(p_transition_id) = '' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- The first locator contains no protected state/history/definition payload.
    SELECT i.resource_type, i.resource_id INTO v_resource_type, v_resource_id
      FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id = p_instance_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT r.tenant_id INTO v_tenant
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type AND r.resource_id = v_resource_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- Policy mutators must acquire this SAME permanent guard FOR UPDATE.
    -- READ COMMITTED provides fresh data after a revoke releases this lock.
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g
     WHERE g.tenant_id = v_tenant FOR SHARE;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT r.owner_actor_id, r.lifecycle INTO v_owner, v_resource_lifecycle
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type
       AND r.resource_id = v_resource_id AND r.tenant_id = v_tenant;
    IF NOT FOUND OR v_resource_lifecycle <> 'ACTIVE' THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT m.active INTO v_member_active
      FROM "${flyway:defaultSchema}".tenant_memberships m
     WHERE m.tenant_id = v_tenant AND m.actor_id = p_actor_id;
    IF NOT FOUND OR NOT v_member_active THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT EXISTS(
        SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
         WHERE a.tenant_id = v_tenant
           AND a.resource_type = v_resource_type AND a.resource_id = v_resource_id
           AND a.actor_id = p_actor_id AND a.granted
    ) INTO v_has_some_grant;
    IF v_owner <> p_actor_id AND NOT v_has_some_grant THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT EXISTS(
        SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
         WHERE a.tenant_id = v_tenant
           AND a.resource_type = v_resource_type AND a.resource_id = v_resource_id
           AND a.actor_id = p_actor_id AND a.operation = 'workflow:transition' AND a.granted
    ) INTO v_has_action_grant;
    IF NOT v_has_action_grant OR NOT p_authority_present THEN
        RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- No instance lock or protected payload is materialized before authorization.
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i
     WHERE i.instance_id = p_instance_id FOR UPDATE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;
    IF v_instance.runtime_version IS DISTINCT FROM p_expected_version THEN
        RETURN QUERY SELECT 'CONFLICT'::TEXT, NULL::JSONB; RETURN;
    END IF;
    IF (p_when_seconds,p_when_nanos) <
       (v_instance.updated_at_seconds,v_instance.updated_at_nanos) THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;

    IF v_instance.lifecycle <> 'ACTIVE' OR v_instance.runtime_version = 9223372036854775807 THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT t.source_state, t.target_state INTO v_source, v_target
      FROM "${flyway:defaultSchema}".workflow_definition_transitions t
     WHERE t.definition_id = v_instance.definition_id
       AND t.definition_version = v_instance.definition_version
       AND t.transition_id = p_transition_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'INVALID_TRANSITION'::TEXT, NULL::JSONB; RETURN;
    END IF;
    IF v_source <> v_instance.current_state THEN
        RETURN QUERY SELECT 'REJECTED'::TEXT, NULL::JSONB; RETURN;
    END IF;
    v_new_lifecycle := CASE WHEN EXISTS (
        SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_transitions t
         WHERE t.definition_id = v_instance.definition_id
           AND t.definition_version = v_instance.definition_version
           AND t.source_state = v_target
    ) THEN 'ACTIVE' ELSE 'COMPLETED' END;

    -- Snapshot update and matching history insertion share the SAME DB transaction.
    UPDATE "${flyway:defaultSchema}".workflow_instances i
       SET current_state = v_target, runtime_version = v_instance.runtime_version + 1,
           lifecycle = v_new_lifecycle,
           updated_at_seconds = p_when_seconds, updated_at_nanos = p_when_nanos
     WHERE i.instance_id = p_instance_id RETURNING i.* INTO v_instance;
    INSERT INTO "${flyway:defaultSchema}".workflow_transition_history (
        instance_id, resulting_runtime_version, transition_id,
        source_state, target_state, actor_id, occurred_at_seconds, occurred_at_nanos
    ) VALUES (
        p_instance_id, v_instance.runtime_version, p_transition_id,
        v_source, v_target, p_actor_id, p_when_seconds, p_when_nanos
    );
    RETURN QUERY SELECT 'SNAPSHOT'::TEXT, jsonb_build_object(
        'instance', to_jsonb(v_instance),
        'record', jsonb_build_object(
            'instance_id', p_instance_id, 'transition_id', p_transition_id,
            'source_state', v_source, 'target_state', v_target,
            'actor_id', p_actor_id, 'resulting_runtime_version', v_instance.runtime_version,
            'occurred_at_seconds', p_when_seconds, 'occurred_at_nanos', p_when_nanos
        )); RETURN;
END;
$stage6_transition$;

REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_commit_activation(
    TEXT,BOOLEAN,TEXT,BIGINT,BIGINT,INTEGER) FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_commit_transition(
    TEXT,BOOLEAN,TEXT,BIGINT,TEXT,BIGINT,INTEGER) FROM PUBLIC;
