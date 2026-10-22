-- ISOLATED D3 CANDIDATE ONLY. No Flyway/HTTP wiring or production grants.
-- Core derives domain proposals; SQL rechecks live ACL and full origin CAS under the final guard.
-- Option B trusts verified backend actor/authority assertions. The owning JDBC call confirms COMMIT.
CREATE FUNCTION "${flyway:defaultSchema}".wf_port_commit_activation(p_actor_id TEXT,p_authority_present BOOLEAN,p_expected JSONB,p_activated JSONB)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $d3$
DECLARE
    v_resource_type TEXT; v_resource_id TEXT; v_tenant TEXT;
    v_owner TEXT; v_lifecycle TEXT; v_member BOOLEAN; v_visible BOOLEAN; v_grant BOOLEAN;
    v_instance RECORD; v_definition JSONB;
    p_instance_id TEXT;
BEGIN
    IF jsonb_typeof(p_expected) IS DISTINCT FROM 'object' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF NOT (p_expected ?& ARRAY['instance_id','definition_id','definition_version','resource_type','resource_id','current_state','runtime_version','lifecycle','created_at_seconds','created_at_nanos','updated_at_seconds','updated_at_nanos'])
       OR (SELECT count(*) FROM jsonb_object_keys(p_expected)) <> 12
       OR (jsonb_typeof(p_expected->'instance_id') IS DISTINCT FROM 'string' OR btrim(p_expected->>'instance_id') = '')
       OR (jsonb_typeof(p_expected->'definition_id') IS DISTINCT FROM 'string' OR btrim(p_expected->>'definition_id') = '')
       OR (jsonb_typeof(p_expected->'resource_type') IS DISTINCT FROM 'string' OR btrim(p_expected->>'resource_type') = '')
       OR (jsonb_typeof(p_expected->'resource_id') IS DISTINCT FROM 'string' OR btrim(p_expected->>'resource_id') = '')
       OR (jsonb_typeof(p_expected->'current_state') IS DISTINCT FROM 'string' OR btrim(p_expected->>'current_state') = '')
       OR (jsonb_typeof(p_expected->'lifecycle') IS DISTINCT FROM 'string' OR btrim(p_expected->>'lifecycle') = '')
       OR (jsonb_typeof(p_expected->'definition_version') IS DISTINCT FROM 'number' OR (p_expected->>'definition_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_expected->'runtime_version') IS DISTINCT FROM 'number' OR (p_expected->>'runtime_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_expected->'created_at_seconds') IS DISTINCT FROM 'number' OR (p_expected->>'created_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_expected->'created_at_nanos') IS DISTINCT FROM 'number' OR (p_expected->>'created_at_nanos') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_expected->'updated_at_seconds') IS DISTINCT FROM 'number' OR (p_expected->>'updated_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_expected->'updated_at_nanos') IS DISTINCT FROM 'number' OR (p_expected->>'updated_at_nanos') !~ '^-?[0-9]+$')
    THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF (p_expected->>'definition_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_expected->>'runtime_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_expected->>'created_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_expected->>'created_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999
       OR (p_expected->>'updated_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_expected->>'updated_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF jsonb_typeof(p_activated) IS DISTINCT FROM 'object' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF NOT (p_activated ?& ARRAY['instance_id','definition_id','definition_version','resource_type','resource_id','current_state','runtime_version','lifecycle','created_at_seconds','created_at_nanos','updated_at_seconds','updated_at_nanos'])
       OR (SELECT count(*) FROM jsonb_object_keys(p_activated)) <> 12
       OR (jsonb_typeof(p_activated->'instance_id') IS DISTINCT FROM 'string' OR btrim(p_activated->>'instance_id') = '')
       OR (jsonb_typeof(p_activated->'definition_id') IS DISTINCT FROM 'string' OR btrim(p_activated->>'definition_id') = '')
       OR (jsonb_typeof(p_activated->'resource_type') IS DISTINCT FROM 'string' OR btrim(p_activated->>'resource_type') = '')
       OR (jsonb_typeof(p_activated->'resource_id') IS DISTINCT FROM 'string' OR btrim(p_activated->>'resource_id') = '')
       OR (jsonb_typeof(p_activated->'current_state') IS DISTINCT FROM 'string' OR btrim(p_activated->>'current_state') = '')
       OR (jsonb_typeof(p_activated->'lifecycle') IS DISTINCT FROM 'string' OR btrim(p_activated->>'lifecycle') = '')
       OR (jsonb_typeof(p_activated->'definition_version') IS DISTINCT FROM 'number' OR (p_activated->>'definition_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_activated->'runtime_version') IS DISTINCT FROM 'number' OR (p_activated->>'runtime_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_activated->'created_at_seconds') IS DISTINCT FROM 'number' OR (p_activated->>'created_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_activated->'created_at_nanos') IS DISTINCT FROM 'number' OR (p_activated->>'created_at_nanos') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_activated->'updated_at_seconds') IS DISTINCT FROM 'number' OR (p_activated->>'updated_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_activated->'updated_at_nanos') IS DISTINCT FROM 'number' OR (p_activated->>'updated_at_nanos') !~ '^-?[0-9]+$')
    THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF (p_activated->>'definition_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_activated->>'runtime_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_activated->>'created_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_activated->>'created_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999
       OR (p_activated->>'updated_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_activated->>'updated_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    p_instance_id:=p_expected->>'instance_id';
    IF p_actor_id IS NULL OR btrim(p_actor_id)='' OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id)='' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.resource_type,i.resource_id INTO v_resource_type,v_resource_id
      FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;

    SELECT r.tenant_id INTO v_tenant FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=v_resource_type AND r.resource_id=v_resource_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g WHERE g.tenant_id=v_tenant FOR SHARE;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT r.owner_actor_id,r.lifecycle INTO v_owner,v_lifecycle FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=v_resource_type AND r.resource_id=v_resource_id AND r.tenant_id=v_tenant;
    IF NOT FOUND THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF v_lifecycle <> 'ACTIVE' THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT m.active INTO v_member FROM "${flyway:defaultSchema}".tenant_memberships m
      WHERE m.tenant_id=v_tenant AND m.actor_id=p_actor_id;
    IF NOT FOUND OR NOT v_member THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=v_resource_type AND a.resource_id=v_resource_id
        AND a.actor_id=p_actor_id AND a.granted) INTO v_visible;
    IF v_owner <> p_actor_id AND NOT v_visible THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=v_resource_type AND a.resource_id=v_resource_id
        AND a.actor_id=p_actor_id AND a.operation='workflow:activate' AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id FOR UPDATE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF v_instance.runtime_version IS DISTINCT FROM (p_expected->>'runtime_version')::BIGINT
       OR v_instance.lifecycle IS DISTINCT FROM p_expected->>'lifecycle' THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','VERSION_CONFLICT'); RETURN; END IF;
    IF v_instance.lifecycle='COMPLETED' THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','INSTANCE_COMPLETED'); RETURN; END IF;
    IF to_jsonb(v_instance) IS DISTINCT FROM p_expected THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','TRANSITION_NOT_ALLOWED'); RETURN; END IF;
    IF v_instance.lifecycle<>'NOT_STARTED' OR v_instance.runtime_version<>0
       OR (p_activated - ARRAY['lifecycle','updated_at_seconds','updated_at_nanos'])
          IS DISTINCT FROM (p_expected - ARRAY['lifecycle','updated_at_seconds','updated_at_nanos'])
       OR p_activated->>'lifecycle'<>'ACTIVE'
       OR ((p_activated->>'updated_at_seconds')::BIGINT,(p_activated->>'updated_at_nanos')::INTEGER)
          < (v_instance.updated_at_seconds,v_instance.updated_at_nanos)
       THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','TRANSITION_NOT_ALLOWED'); RETURN; END IF;
    UPDATE "${flyway:defaultSchema}".workflow_instances i SET lifecycle='ACTIVE',
      updated_at_seconds=(p_activated->>'updated_at_seconds')::BIGINT,
      updated_at_nanos=(p_activated->>'updated_at_nanos')::INTEGER
      WHERE i.instance_id=p_instance_id RETURNING i.* INTO v_instance;
    RETURN QUERY SELECT 'SNAPSHOT'::TEXT,to_jsonb(v_instance);
END;
$d3$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_commit_activation(TEXT,BOOLEAN,JSONB,JSONB) FROM PUBLIC;

CREATE FUNCTION "${flyway:defaultSchema}".wf_port_commit_transition(p_actor_id TEXT,p_authority_present BOOLEAN,p_previous JSONB,p_next JSONB,p_record JSONB)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $d3$
DECLARE
    v_resource_type TEXT; v_resource_id TEXT; v_tenant TEXT;
    v_owner TEXT; v_lifecycle TEXT; v_member BOOLEAN; v_visible BOOLEAN; v_grant BOOLEAN;
    v_instance RECORD; v_definition JSONB;
    p_instance_id TEXT; v_record JSONB;
BEGIN
    IF jsonb_typeof(p_previous) IS DISTINCT FROM 'object' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF NOT (p_previous ?& ARRAY['instance_id','definition_id','definition_version','resource_type','resource_id','current_state','runtime_version','lifecycle','created_at_seconds','created_at_nanos','updated_at_seconds','updated_at_nanos'])
       OR (SELECT count(*) FROM jsonb_object_keys(p_previous)) <> 12
       OR (jsonb_typeof(p_previous->'instance_id') IS DISTINCT FROM 'string' OR btrim(p_previous->>'instance_id') = '')
       OR (jsonb_typeof(p_previous->'definition_id') IS DISTINCT FROM 'string' OR btrim(p_previous->>'definition_id') = '')
       OR (jsonb_typeof(p_previous->'resource_type') IS DISTINCT FROM 'string' OR btrim(p_previous->>'resource_type') = '')
       OR (jsonb_typeof(p_previous->'resource_id') IS DISTINCT FROM 'string' OR btrim(p_previous->>'resource_id') = '')
       OR (jsonb_typeof(p_previous->'current_state') IS DISTINCT FROM 'string' OR btrim(p_previous->>'current_state') = '')
       OR (jsonb_typeof(p_previous->'lifecycle') IS DISTINCT FROM 'string' OR btrim(p_previous->>'lifecycle') = '')
       OR (jsonb_typeof(p_previous->'definition_version') IS DISTINCT FROM 'number' OR (p_previous->>'definition_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_previous->'runtime_version') IS DISTINCT FROM 'number' OR (p_previous->>'runtime_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_previous->'created_at_seconds') IS DISTINCT FROM 'number' OR (p_previous->>'created_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_previous->'created_at_nanos') IS DISTINCT FROM 'number' OR (p_previous->>'created_at_nanos') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_previous->'updated_at_seconds') IS DISTINCT FROM 'number' OR (p_previous->>'updated_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_previous->'updated_at_nanos') IS DISTINCT FROM 'number' OR (p_previous->>'updated_at_nanos') !~ '^-?[0-9]+$')
    THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF (p_previous->>'definition_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_previous->>'runtime_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_previous->>'created_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_previous->>'created_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999
       OR (p_previous->>'updated_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_previous->>'updated_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF jsonb_typeof(p_next) IS DISTINCT FROM 'object' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF NOT (p_next ?& ARRAY['instance_id','definition_id','definition_version','resource_type','resource_id','current_state','runtime_version','lifecycle','created_at_seconds','created_at_nanos','updated_at_seconds','updated_at_nanos'])
       OR (SELECT count(*) FROM jsonb_object_keys(p_next)) <> 12
       OR (jsonb_typeof(p_next->'instance_id') IS DISTINCT FROM 'string' OR btrim(p_next->>'instance_id') = '')
       OR (jsonb_typeof(p_next->'definition_id') IS DISTINCT FROM 'string' OR btrim(p_next->>'definition_id') = '')
       OR (jsonb_typeof(p_next->'resource_type') IS DISTINCT FROM 'string' OR btrim(p_next->>'resource_type') = '')
       OR (jsonb_typeof(p_next->'resource_id') IS DISTINCT FROM 'string' OR btrim(p_next->>'resource_id') = '')
       OR (jsonb_typeof(p_next->'current_state') IS DISTINCT FROM 'string' OR btrim(p_next->>'current_state') = '')
       OR (jsonb_typeof(p_next->'lifecycle') IS DISTINCT FROM 'string' OR btrim(p_next->>'lifecycle') = '')
       OR (jsonb_typeof(p_next->'definition_version') IS DISTINCT FROM 'number' OR (p_next->>'definition_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_next->'runtime_version') IS DISTINCT FROM 'number' OR (p_next->>'runtime_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_next->'created_at_seconds') IS DISTINCT FROM 'number' OR (p_next->>'created_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_next->'created_at_nanos') IS DISTINCT FROM 'number' OR (p_next->>'created_at_nanos') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_next->'updated_at_seconds') IS DISTINCT FROM 'number' OR (p_next->>'updated_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_next->'updated_at_nanos') IS DISTINCT FROM 'number' OR (p_next->>'updated_at_nanos') !~ '^-?[0-9]+$')
    THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF (p_next->>'definition_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_next->>'runtime_version')::NUMERIC NOT BETWEEN 0 AND 9223372036854775807
       OR (p_next->>'created_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_next->>'created_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999
       OR (p_next->>'updated_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_next->>'updated_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF jsonb_typeof(p_record) IS DISTINCT FROM 'object' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF NOT (p_record ?& ARRAY['instance_id','transition_id','source_state','target_state','actor_id','resulting_runtime_version','occurred_at_seconds','occurred_at_nanos'])
       OR (SELECT count(*) FROM jsonb_object_keys(p_record)) <> 8
       OR (jsonb_typeof(p_record->'instance_id') IS DISTINCT FROM 'string' OR btrim(p_record->>'instance_id') = '')
       OR (jsonb_typeof(p_record->'transition_id') IS DISTINCT FROM 'string' OR btrim(p_record->>'transition_id') = '')
       OR (jsonb_typeof(p_record->'source_state') IS DISTINCT FROM 'string' OR btrim(p_record->>'source_state') = '')
       OR (jsonb_typeof(p_record->'target_state') IS DISTINCT FROM 'string' OR btrim(p_record->>'target_state') = '')
       OR (jsonb_typeof(p_record->'actor_id') IS DISTINCT FROM 'string' OR btrim(p_record->>'actor_id') = '')
       OR (jsonb_typeof(p_record->'resulting_runtime_version') IS DISTINCT FROM 'number' OR (p_record->>'resulting_runtime_version') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_record->'occurred_at_seconds') IS DISTINCT FROM 'number' OR (p_record->>'occurred_at_seconds') !~ '^-?[0-9]+$')
       OR (jsonb_typeof(p_record->'occurred_at_nanos') IS DISTINCT FROM 'number' OR (p_record->>'occurred_at_nanos') !~ '^-?[0-9]+$')
    THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF (p_record->>'resulting_runtime_version')::NUMERIC NOT BETWEEN 1 AND 9223372036854775807
       OR (p_record->>'occurred_at_seconds')::NUMERIC NOT BETWEEN -31557014167219200 AND 31556889864403199
       OR (p_record->>'occurred_at_nanos')::NUMERIC NOT BETWEEN 0 AND 999999999 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    p_instance_id:=p_previous->>'instance_id';
    IF p_actor_id IS NULL OR btrim(p_actor_id)='' OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id)='' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.resource_type,i.resource_id INTO v_resource_type,v_resource_id
      FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;

    SELECT r.tenant_id INTO v_tenant FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=v_resource_type AND r.resource_id=v_resource_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g WHERE g.tenant_id=v_tenant FOR SHARE;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT r.owner_actor_id,r.lifecycle INTO v_owner,v_lifecycle FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=v_resource_type AND r.resource_id=v_resource_id AND r.tenant_id=v_tenant;
    IF NOT FOUND THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF v_lifecycle <> 'ACTIVE' THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT m.active INTO v_member FROM "${flyway:defaultSchema}".tenant_memberships m
      WHERE m.tenant_id=v_tenant AND m.actor_id=p_actor_id;
    IF NOT FOUND OR NOT v_member THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=v_resource_type AND a.resource_id=v_resource_id
        AND a.actor_id=p_actor_id AND a.granted) INTO v_visible;
    IF v_owner <> p_actor_id AND NOT v_visible THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=v_resource_type AND a.resource_id=v_resource_id
        AND a.actor_id=p_actor_id AND a.operation='workflow:transition' AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id FOR UPDATE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF v_instance.runtime_version IS DISTINCT FROM (p_previous->>'runtime_version')::BIGINT THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','VERSION_CONFLICT'); RETURN; END IF;
    IF v_instance.lifecycle='COMPLETED' THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','INSTANCE_COMPLETED'); RETURN; END IF;
    IF to_jsonb(v_instance) IS DISTINCT FROM p_previous THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','TRANSITION_NOT_ALLOWED'); RETURN; END IF;
    IF v_instance.lifecycle<>'ACTIVE' OR v_instance.runtime_version=9223372036854775807
       OR (p_next - ARRAY['current_state','runtime_version','lifecycle','updated_at_seconds','updated_at_nanos'])
          IS DISTINCT FROM (p_previous - ARRAY['current_state','runtime_version','lifecycle','updated_at_seconds','updated_at_nanos'])
       OR (p_next->>'runtime_version')::BIGINT<>v_instance.runtime_version+1
       OR p_next->>'lifecycle' NOT IN ('ACTIVE','COMPLETED')
       OR ((p_next->>'updated_at_seconds')::BIGINT,(p_next->>'updated_at_nanos')::INTEGER)
          < (v_instance.updated_at_seconds,v_instance.updated_at_nanos)
       OR p_record IS DISTINCT FROM jsonb_build_object('instance_id',p_instance_id,'transition_id',p_record->>'transition_id',
          'source_state',v_instance.current_state,'target_state',p_next->>'current_state','actor_id',p_actor_id,
          'resulting_runtime_version',v_instance.runtime_version+1,'occurred_at_seconds',p_next->'updated_at_seconds',
          'occurred_at_nanos',p_next->'updated_at_nanos')
       OR NOT EXISTS (SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_transitions t
           WHERE t.definition_id=v_instance.definition_id AND t.definition_version=v_instance.definition_version
             AND t.transition_id=p_record->>'transition_id' AND t.source_state=v_instance.current_state
             AND t.target_state=p_next->>'current_state')
       OR ((p_next->>'lifecycle'='COMPLETED') IS DISTINCT FROM
           (NOT EXISTS (SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_transitions t
               WHERE t.definition_id=v_instance.definition_id AND t.definition_version=v_instance.definition_version
                 AND t.source_state=p_next->>'current_state')))
       THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','TRANSITION_NOT_ALLOWED'); RETURN; END IF;
    UPDATE "${flyway:defaultSchema}".workflow_instances i SET current_state=p_next->>'current_state',
      runtime_version=(p_next->>'runtime_version')::BIGINT,lifecycle=p_next->>'lifecycle',
      updated_at_seconds=(p_next->>'updated_at_seconds')::BIGINT,
      updated_at_nanos=(p_next->>'updated_at_nanos')::INTEGER WHERE i.instance_id=p_instance_id RETURNING i.* INTO v_instance;

    INSERT INTO "${flyway:defaultSchema}".workflow_transition_history AS h
      (instance_id,resulting_runtime_version,transition_id,source_state,target_state,actor_id,occurred_at_seconds,occurred_at_nanos)
      VALUES(p_instance_id,v_instance.runtime_version,p_record->>'transition_id',p_record->>'source_state',
        p_record->>'target_state',p_actor_id,v_instance.updated_at_seconds,v_instance.updated_at_nanos)
      RETURNING to_jsonb(h) INTO v_record;
    RETURN QUERY SELECT 'TRANSITION_RESULT'::TEXT,jsonb_build_object('previous_instance',p_previous,'instance',to_jsonb(v_instance),'record',v_record);
END;
$d3$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_commit_transition(TEXT,BOOLEAN,JSONB,JSONB,JSONB) FROM PUBLIC;
