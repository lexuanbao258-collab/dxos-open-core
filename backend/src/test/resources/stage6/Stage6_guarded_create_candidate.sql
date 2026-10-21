-- STAGE 6.3 ISOLATED CANDIDATE ONLY. Not a Flyway migration or production grant.
-- Exactly the proposed CREATE runtime SQL identity; no helper entrypoint or overload.
-- ADR-0025 Option B: the verified Spring backend MUST supply actor/authority and
-- target-bound proposal. Stolen runtime DB credentials + connectivity can impersonate.
-- A guarded JDBC port MUST own one READ COMMITTED transaction, confirm durable COMMIT
-- and transport HIDDEN/FORBIDDEN/UNAVAILABLE without exposing SQL details.
-- SECURITY DEFINER owner, runtime EXECUTE-only grant and SQL caller isolation are
-- NOT established by this disposable-DB test resource. NO production enablement.

CREATE FUNCTION "${flyway:defaultSchema}".wf_port_create_instance(
    p_actor_id TEXT,
    p_authority_present BOOLEAN,
    p_initial JSONB
)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $stage6_create$
DECLARE
    v_tenant TEXT;
    v_owner TEXT;
    v_lifecycle TEXT;
    v_member_active BOOLEAN;
    v_visible BOOLEAN;
    v_create_grant BOOLEAN;
    v_binding_active BOOLEAN;
    v_initial_state TEXT;
    v_inserted INTEGER;
    v_key_count BIGINT;
    v_instance_id TEXT;
    v_resource_type TEXT;
    v_resource_id TEXT;
    v_definition_id TEXT;
    v_definition_version BIGINT;
    v_created_seconds BIGINT;
    v_created_nanos INTEGER;
    v_updated_seconds BIGINT;
    v_updated_nanos INTEGER;
BEGIN
    -- Structural checks have no protected read-set.
    IF p_actor_id IS NULL OR btrim(p_actor_id) = ''
       OR p_authority_present IS NULL
       OR p_initial IS NULL OR jsonb_typeof(p_initial) <> 'object' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    IF NOT (p_initial ?& ARRAY[
        'instance_id','definition_id','definition_version','resource_type','resource_id',
        'current_state','runtime_version','lifecycle','created_at_seconds','created_at_nanos',
        'updated_at_seconds','updated_at_nanos'
    ]) THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT count(*) INTO v_key_count FROM jsonb_object_keys(p_initial);
    IF v_key_count <> 12 OR
       jsonb_typeof(p_initial->'instance_id') <> 'string' OR
       jsonb_typeof(p_initial->'definition_id') <> 'string' OR
       jsonb_typeof(p_initial->'resource_type') <> 'string' OR
       jsonb_typeof(p_initial->'resource_id') <> 'string' OR
       jsonb_typeof(p_initial->'current_state') <> 'string' OR
       jsonb_typeof(p_initial->'lifecycle') <> 'string' OR
       jsonb_typeof(p_initial->'definition_version') <> 'number' OR
       jsonb_typeof(p_initial->'runtime_version') <> 'number' OR
       jsonb_typeof(p_initial->'created_at_seconds') <> 'number' OR
       jsonb_typeof(p_initial->'created_at_nanos') <> 'number' OR
       jsonb_typeof(p_initial->'updated_at_seconds') <> 'number' OR
       jsonb_typeof(p_initial->'updated_at_nanos') <> 'number' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    v_instance_id := p_initial->>'instance_id';
    v_resource_type := p_initial->>'resource_type';
    v_resource_id := p_initial->>'resource_id';
    v_definition_id := p_initial->>'definition_id';
    -- Exact integral text syntax; casts still fail closed on overflow.
    IF v_instance_id !~ '^wf_[A-Za-z0-9_-]{32}$'
       OR v_resource_type = '' OR v_resource_id = '' OR v_definition_id = ''
       OR p_initial->>'definition_version' !~ '^(0|[1-9][0-9]*)$'
       OR p_initial->>'runtime_version' <> '0'
       OR p_initial->>'created_at_seconds' !~ '^-?[0-9]+$'
       OR p_initial->>'updated_at_seconds' !~ '^-?[0-9]+$'
       OR p_initial->>'created_at_nanos' !~ '^[0-9]+$'
       OR p_initial->>'updated_at_nanos' !~ '^[0-9]+$'
       OR p_initial->>'lifecycle' <> 'NOT_STARTED' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    v_definition_version := (p_initial->>'definition_version')::BIGINT;
    v_created_seconds := (p_initial->>'created_at_seconds')::BIGINT;
    v_created_nanos := (p_initial->>'created_at_nanos')::INTEGER;
    v_updated_seconds := (p_initial->>'updated_at_seconds')::BIGINT;
    v_updated_nanos := (p_initial->>'updated_at_nanos')::INTEGER;
    IF v_created_seconds IS DISTINCT FROM v_updated_seconds
       OR v_created_nanos IS DISTINCT FROM v_updated_nanos
       OR v_created_nanos NOT BETWEEN 0 AND 999999999
       OR v_definition_version < 0 THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- CLASSIFY minimum tenant locator: NO definition, binding, instance or history.
    SELECT r.tenant_id INTO v_tenant
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type AND r.resource_id = v_resource_id;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- Same permanent guard lock every policy mutator MUST use FOR UPDATE.
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g
      WHERE g.tenant_id = v_tenant FOR SHARE;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- Fresh READ COMMITTED classification, AFTER waiting for any earlier revoke.
    SELECT r.owner_actor_id, r.lifecycle INTO v_owner, v_lifecycle
      FROM "${flyway:defaultSchema}".resource_registry r
     WHERE r.resource_type = v_resource_type
       AND r.resource_id = v_resource_id AND r.tenant_id = v_tenant;
    IF NOT FOUND OR v_lifecycle <> 'ACTIVE' THEN
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
       WHERE a.tenant_id = v_tenant AND a.resource_type = v_resource_type
         AND a.resource_id = v_resource_id AND a.actor_id = p_actor_id
         AND a.granted
    ) INTO v_visible;
    IF v_owner <> p_actor_id AND NOT v_visible THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    SELECT EXISTS(
      SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
       WHERE a.tenant_id = v_tenant AND a.resource_type = v_resource_type
         AND a.resource_id = v_resource_id AND a.actor_id = p_actor_id
         AND a.operation = 'workflow:create' AND a.granted
    ) INTO v_create_grant;
    IF NOT v_create_grant OR NOT p_authority_present THEN
        RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- Exact CREATE binding and definition only AFTER the live grant+authority check.
    SELECT EXISTS(
      SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_bindings b
       WHERE b.tenant_id = v_tenant AND b.resource_type = v_resource_type
         AND b.definition_id = v_definition_id
         AND b.definition_version = v_definition_version AND b.active
    ) INTO v_binding_active;
    IF NOT v_binding_active THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN;
    END IF;
    SELECT d.initial_state INTO v_initial_state
      FROM "${flyway:defaultSchema}".workflow_definitions d
     WHERE d.definition_id = v_definition_id
       AND d.definition_version = v_definition_version;
    IF NOT FOUND OR v_initial_state IS DISTINCT FROM p_initial->>'current_state' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN;
    END IF;

    -- One write in the SAME SQL transaction as the held tenant guard. No collision SELECT.
    INSERT INTO "${flyway:defaultSchema}".workflow_instances
      (instance_id,definition_id,definition_version,resource_type,resource_id,
       current_state,runtime_version,lifecycle,
       created_at_seconds,created_at_nanos,updated_at_seconds,updated_at_nanos)
    VALUES
      (v_instance_id,v_definition_id,v_definition_version,v_resource_type,v_resource_id,
       v_initial_state,0,'NOT_STARTED',
       v_created_seconds,v_created_nanos,v_updated_seconds,v_updated_nanos)
    ON CONFLICT (instance_id) DO NOTHING;
    GET DIAGNOSTICS v_inserted = ROW_COUNT;
    IF v_inserted <> 1 THEN
        RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','INSTANCE_ALREADY_EXISTS'); RETURN;
    END IF;
    RETURN QUERY SELECT 'SNAPSHOT'::TEXT, p_initial; RETURN;
END;
$stage6_create$;

-- This candidate file is deliberately loaded only inside isolated test transactions.
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_create_instance(TEXT,BOOLEAN,JSONB) FROM PUBLIC;
