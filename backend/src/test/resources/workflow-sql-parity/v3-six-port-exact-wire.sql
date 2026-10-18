-- TEST-ONLY V3 six-port SQL parity fixture: NEVER a Flyway migration.
-- Replaces __WORKFLOW_SCHEMA__ ONLY with a regex-verified random isolated schema.
-- Reuses real V3/V4 tables; SECURITY INVOKER, no runtime GRANT, no production actor-proof.
-- The Java Core port remains the authoritative domain implementation.
-- SQL equality/validation here is an experimental parity checkpoint, NOT release-approved V5.
CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_load_definition(p_id TEXT, p_version BIGINT)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE d __WORKFLOW_SCHEMA__.workflow_definitions%ROWTYPE;
BEGIN
  SELECT * INTO d FROM __WORKFLOW_SCHEMA__.workflow_definitions
    WHERE definition_id=p_id AND definition_version=p_version;
  IF NOT FOUND THEN RAISE EXCEPTION 'DEFINITION_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  RETURN jsonb_build_object(
    'definition_id',d.definition_id,'definition_version',d.definition_version,
    'initial_state',d.initial_state,
    'states',(SELECT coalesce(jsonb_agg(s.state_id ORDER BY s.state_id),'[]'::jsonb)
              FROM __WORKFLOW_SCHEMA__.workflow_definition_states s
              WHERE s.definition_id=p_id AND s.definition_version=p_version),
    'transitions',(SELECT coalesce(jsonb_agg(jsonb_build_object(
                     'transition_id',t.transition_id,'source_state',t.source_state,
                     'target_state',t.target_state) ORDER BY t.transition_id),'[]'::jsonb)
                   FROM __WORKFLOW_SCHEMA__.workflow_definition_transitions t
                   WHERE t.definition_id=p_id AND t.definition_version=p_version)
  );
END $port$;

CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_load_instance(p_id TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE data JSONB;
BEGIN
  SELECT to_jsonb(i) INTO data FROM __WORKFLOW_SCHEMA__.workflow_instances i WHERE instance_id=p_id;
  IF data IS NULL THEN RAISE EXCEPTION 'INSTANCE_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  RETURN data;
END $port$;

CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_load_history(p_id TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE data JSONB;
BEGIN
  PERFORM 1 FROM __WORKFLOW_SCHEMA__.workflow_instances WHERE instance_id=p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'INSTANCE_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(h) ORDER BY h.resulting_runtime_version),'[]'::jsonb)
    INTO data FROM __WORKFLOW_SCHEMA__.workflow_transition_history h WHERE h.instance_id=p_id;
  RETURN data;
END $port$;

CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_create_instance(
  p_id TEXT,p_definition TEXT,p_version BIGINT,p_resource_type TEXT,p_resource_id TEXT,
  p_seconds BIGINT,p_nanos INTEGER)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE v_initial TEXT; data JSONB;
BEGIN
  -- Core's existing port duplicate-before-definition precedence; no hidden row is returned.
  PERFORM 1 FROM __WORKFLOW_SCHEMA__.workflow_instances WHERE instance_id=p_id;
  IF FOUND THEN RAISE EXCEPTION 'INSTANCE_ALREADY_EXISTS' USING ERRCODE='23505'; END IF;
  SELECT initial_state INTO v_initial FROM __WORKFLOW_SCHEMA__.workflow_definitions
    WHERE definition_id=p_definition AND definition_version=p_version;
  IF NOT FOUND THEN RAISE EXCEPTION 'DEFINITION_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  INSERT INTO __WORKFLOW_SCHEMA__.workflow_instances (
    instance_id,definition_id,definition_version,resource_type,resource_id,
    current_state,runtime_version,lifecycle,created_at_seconds,created_at_nanos,
    updated_at_seconds,updated_at_nanos
  ) VALUES (p_id,p_definition,p_version,p_resource_type,p_resource_id,
    v_initial,0,'NOT_STARTED',p_seconds,p_nanos,p_seconds,p_nanos)
  ON CONFLICT (instance_id) DO NOTHING;
  IF NOT FOUND THEN RAISE EXCEPTION 'INSTANCE_ALREADY_EXISTS' USING ERRCODE='23505'; END IF;
  SELECT to_jsonb(i) INTO data FROM __WORKFLOW_SCHEMA__.workflow_instances i WHERE i.instance_id=p_id;
  RETURN data;
END $port$;

CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_commit_activation(
  p_expected JSONB,p_proposed JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE old_row __WORKFLOW_SCHEMA__.workflow_instances%ROWTYPE; new_row JSONB;
BEGIN
  SELECT * INTO old_row FROM __WORKFLOW_SCHEMA__.workflow_instances
    WHERE instance_id=p_expected->>'instance_id' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'INSTANCE_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  IF old_row.runtime_version <> (p_expected->>'runtime_version')::BIGINT
        OR old_row.lifecycle <> p_expected->>'lifecycle' THEN
    RAISE EXCEPTION 'VERSION_CONFLICT' USING ERRCODE='P0001';
  END IF;
  IF old_row.lifecycle='COMPLETED' THEN RAISE EXCEPTION 'INSTANCE_COMPLETED' USING ERRCODE='P0001'; END IF;
  IF to_jsonb(old_row) IS DISTINCT FROM p_expected OR old_row.lifecycle <> 'NOT_STARTED' THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  IF (p_proposed->>'lifecycle') <> 'ACTIVE'
      OR (p_proposed->>'updated_at_seconds')::BIGINT <
              old_row.updated_at_seconds
      OR ((p_proposed->>'updated_at_seconds')::BIGINT=old_row.updated_at_seconds
          AND (p_proposed->>'updated_at_nanos')::INTEGER<old_row.updated_at_nanos)
      OR NOT EXISTS (SELECT 1 FROM __WORKFLOW_SCHEMA__.workflow_definition_transitions t
        WHERE t.definition_id=old_row.definition_id AND t.definition_version=old_row.definition_version
          AND t.source_state=old_row.current_state) THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  new_row := to_jsonb(old_row) || jsonb_build_object(
    'lifecycle','ACTIVE','updated_at_seconds',p_proposed->'updated_at_seconds',
    'updated_at_nanos',p_proposed->'updated_at_nanos');
  IF p_proposed IS DISTINCT FROM new_row THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  UPDATE __WORKFLOW_SCHEMA__.workflow_instances
    SET lifecycle='ACTIVE',updated_at_seconds=(p_proposed->>'updated_at_seconds')::BIGINT,
        updated_at_nanos=(p_proposed->>'updated_at_nanos')::INTEGER
    WHERE instance_id=old_row.instance_id;
  RETURN new_row;
END $port$;

CREATE FUNCTION __WORKFLOW_SCHEMA__.wf_port_commit_transition(
  p_expected JSONB,p_proposed JSONB,p_record JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog, pg_temp AS $port$
DECLARE old_row __WORKFLOW_SCHEMA__.workflow_instances%ROWTYPE;
        v_target TEXT; v_lifecycle TEXT; new_row JSONB; inserted JSONB;
        v_expected_version BIGINT; v_seconds BIGINT; v_nanos INTEGER;
BEGIN
  SELECT * INTO old_row FROM __WORKFLOW_SCHEMA__.workflow_instances
    WHERE instance_id=p_expected->>'instance_id' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'INSTANCE_NOT_FOUND' USING ERRCODE='P0002'; END IF;
  v_expected_version := (p_expected->>'runtime_version')::BIGINT;
  IF old_row.runtime_version <> v_expected_version THEN
    RAISE EXCEPTION 'VERSION_CONFLICT' USING ERRCODE='P0001';
  END IF;
  IF old_row.lifecycle='COMPLETED' THEN RAISE EXCEPTION 'INSTANCE_COMPLETED' USING ERRCODE='P0001'; END IF;
  IF to_jsonb(old_row) IS DISTINCT FROM p_expected THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  IF old_row.lifecycle <> 'ACTIVE' OR v_expected_version=9223372036854775807 THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  SELECT t.target_state INTO v_target FROM __WORKFLOW_SCHEMA__.workflow_definition_transitions t
    WHERE t.definition_id=old_row.definition_id AND t.definition_version=old_row.definition_version
       AND t.transition_id=p_record->>'transition_id';
  IF NOT FOUND THEN RAISE EXCEPTION 'INVALID_TRANSITION' USING ERRCODE='P0001'; END IF;
  IF NOT EXISTS (SELECT 1 FROM __WORKFLOW_SCHEMA__.workflow_definition_transitions t
        WHERE t.definition_id=old_row.definition_id AND t.definition_version=old_row.definition_version
          AND t.transition_id=p_record->>'transition_id'
          AND t.source_state=old_row.current_state) THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  v_seconds := (p_record->>'occurred_at_seconds')::BIGINT;
  v_nanos := (p_record->>'occurred_at_nanos')::INTEGER;
  IF (v_seconds,v_nanos)<(old_row.updated_at_seconds,old_row.updated_at_nanos) THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  v_lifecycle := CASE WHEN EXISTS (
      SELECT 1 FROM __WORKFLOW_SCHEMA__.workflow_definition_transitions t
        WHERE t.definition_id=old_row.definition_id AND t.definition_version=old_row.definition_version
          AND t.source_state=v_target) THEN 'ACTIVE' ELSE 'COMPLETED' END;
  new_row := to_jsonb(old_row) || jsonb_build_object(
      'current_state',v_target,'runtime_version',v_expected_version+1,
      'lifecycle',v_lifecycle,'updated_at_seconds',v_seconds,'updated_at_nanos',v_nanos);
  IF p_proposed IS DISTINCT FROM new_row
      OR p_record->>'instance_id' IS DISTINCT FROM old_row.instance_id
      OR p_record->>'source_state' IS DISTINCT FROM old_row.current_state
      OR p_record->>'target_state' IS DISTINCT FROM v_target
      OR (p_record->>'resulting_runtime_version')::BIGINT IS DISTINCT FROM (v_expected_version+1)
      OR p_record->>'actor_id' IS NULL OR p_record->>'actor_id'='' THEN
    RAISE EXCEPTION 'TRANSITION_NOT_ALLOWED' USING ERRCODE='P0001';
  END IF;
  UPDATE __WORKFLOW_SCHEMA__.workflow_instances
     SET current_state=v_target,runtime_version=v_expected_version+1,lifecycle=v_lifecycle,
         updated_at_seconds=v_seconds,updated_at_nanos=v_nanos
     WHERE instance_id=old_row.instance_id;
  INSERT INTO __WORKFLOW_SCHEMA__.workflow_transition_history (
      instance_id,resulting_runtime_version,transition_id,source_state,target_state,
      actor_id,occurred_at_seconds,occurred_at_nanos)
  VALUES (old_row.instance_id,v_expected_version+1,p_record->>'transition_id',
      old_row.current_state,v_target,p_record->>'actor_id',v_seconds,v_nanos);
  SELECT to_jsonb(h) INTO inserted FROM __WORKFLOW_SCHEMA__.workflow_transition_history h
     WHERE h.instance_id=old_row.instance_id AND h.resulting_runtime_version=v_expected_version+1;
  RETURN jsonb_build_object('instance',new_row,'record',inserted);
END $port$;

-- Do not leave PostgreSQL's default PUBLIC EXECUTE on these test-only helpers.
REVOKE ALL ON FUNCTION
 __WORKFLOW_SCHEMA__.wf_port_load_definition(TEXT,BIGINT),
 __WORKFLOW_SCHEMA__.wf_port_load_instance(TEXT),
 __WORKFLOW_SCHEMA__.wf_port_load_history(TEXT),
 __WORKFLOW_SCHEMA__.wf_port_create_instance(TEXT,TEXT,BIGINT,TEXT,TEXT,BIGINT,INTEGER),
 __WORKFLOW_SCHEMA__.wf_port_commit_activation(JSONB,JSONB),
 __WORKFLOW_SCHEMA__.wf_port_commit_transition(JSONB,JSONB,JSONB)
FROM PUBLIC;
