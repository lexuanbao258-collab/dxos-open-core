-- ISOLATED D3 CANDIDATE ONLY. No Flyway/HTTP wiring or production grants.
-- Core derives domain proposals; SQL rechecks live ACL and full origin CAS under the final guard.
-- Option B trusts verified backend actor/authority assertions. The owning JDBC call confirms COMMIT.
CREATE FUNCTION "${flyway:defaultSchema}".wf_port_load_definition(p_phase TEXT,p_actor_id TEXT,p_operation TEXT,p_authority_present BOOLEAN,p_instance_id TEXT,
p_resource_type TEXT,p_resource_id TEXT,p_definition_id TEXT,p_definition_version BIGINT)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $d3$
DECLARE
    v_resource_type TEXT; v_resource_id TEXT; v_tenant TEXT;
    v_owner TEXT; v_lifecycle TEXT; v_member BOOLEAN; v_visible BOOLEAN; v_grant BOOLEAN;
    v_instance RECORD; v_definition JSONB;

BEGIN
    IF p_actor_id IS NULL OR btrim(p_actor_id)='' OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id)='' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF p_phase IS NULL OR p_phase NOT IN ('CLASSIFY','MATERIALIZE')
       OR p_operation IS NULL OR p_operation NOT IN ('CREATE','ACTIVATE','TRANSITION')
       OR (p_phase='CLASSIFY' AND p_operation<>'CREATE')
       OR p_definition_id IS NULL OR btrim(p_definition_id)='' OR p_definition_version IS NULL OR p_definition_version<0
       OR (p_operation='CREATE' AND (p_resource_type IS NULL OR btrim(p_resource_type)='' OR p_resource_id IS NULL OR btrim(p_resource_id)=''))
       OR (p_operation<>'CREATE' AND (p_resource_type IS NOT NULL OR p_resource_id IS NOT NULL))
       THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF p_operation='CREATE' THEN

    SELECT r.tenant_id INTO v_tenant FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=p_resource_type AND r.resource_id=p_resource_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards g WHERE g.tenant_id=v_tenant FOR SHARE;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT r.owner_actor_id,r.lifecycle INTO v_owner,v_lifecycle FROM "${flyway:defaultSchema}".resource_registry r
      WHERE r.resource_type=p_resource_type AND r.resource_id=p_resource_id AND r.tenant_id=v_tenant;
    IF NOT FOUND THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF v_lifecycle <> 'ACTIVE' THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT m.active INTO v_member FROM "${flyway:defaultSchema}".tenant_memberships m
      WHERE m.tenant_id=v_tenant AND m.actor_id=p_actor_id;
    IF NOT FOUND OR NOT v_member THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=p_resource_type AND a.resource_id=p_resource_id
        AND a.actor_id=p_actor_id AND a.granted) INTO v_visible;
    IF v_owner <> p_actor_id AND NOT v_visible THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
      WHERE a.tenant_id=v_tenant AND a.resource_type=p_resource_type AND a.resource_id=p_resource_id
        AND a.actor_id=p_actor_id AND a.operation='workflow:create' AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;

      IF p_phase='CLASSIFY' THEN
        v_definition:=jsonb_build_object('actor_id',p_actor_id,'instance_id',p_instance_id,
          'resource_type',p_resource_type,'resource_id',p_resource_id,'tenant_id',v_tenant,
          'resource_active',true,'member_active',true,'visible',true,'requested_grant',true);
        IF octet_length(convert_to(v_definition::TEXT,'UTF8'))>65536 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT,NULL::JSONB; RETURN; END IF;
        RETURN QUERY SELECT 'FACTS'::TEXT,v_definition; RETURN;
      END IF;
      IF NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_bindings b WHERE b.tenant_id=v_tenant
        AND b.resource_type=p_resource_type AND b.definition_id=p_definition_id AND b.definition_version=p_definition_version AND b.active)
        THEN RETURN QUERY SELECT 'HIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    ELSE
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
        AND a.actor_id=p_actor_id AND a.operation='workflow:'||lower(p_operation) AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id FOR SHARE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;

      IF v_instance.definition_id IS DISTINCT FROM p_definition_id
         OR v_instance.definition_version IS DISTINCT FROM p_definition_version THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    END IF;
    SELECT jsonb_build_object('definition_id',d.definition_id,'definition_version',d.definition_version,
      'initial_state',d.initial_state,
      'states',COALESCE((SELECT jsonb_agg(s.state_id ORDER BY s.state_id) FROM "${flyway:defaultSchema}".workflow_definition_states s
        WHERE s.definition_id=d.definition_id AND s.definition_version=d.definition_version),'[]'::JSONB),
      'transitions',COALESCE((SELECT jsonb_agg(jsonb_build_object('transition_id',t.transition_id,
        'source_state',t.source_state,'target_state',t.target_state) ORDER BY t.transition_id)
        FROM "${flyway:defaultSchema}".workflow_definition_transitions t
        WHERE t.definition_id=d.definition_id AND t.definition_version=d.definition_version),'[]'::JSONB))
      INTO v_definition FROM "${flyway:defaultSchema}".workflow_definitions d WHERE d.definition_id=p_definition_id AND d.definition_version=p_definition_version;
    IF NOT FOUND THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','DEFINITION_NOT_FOUND'); RETURN; END IF;

    IF octet_length(convert_to(v_definition::TEXT,'UTF8'))>65536 THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    RETURN QUERY SELECT 'DEFINITION'::TEXT,v_definition;
END;
$d3$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_load_definition(TEXT,TEXT,TEXT,BOOLEAN,TEXT,TEXT,TEXT,TEXT,BIGINT) FROM PUBLIC;
CREATE FUNCTION "${flyway:defaultSchema}".wf_port_load_instance(p_actor_id TEXT,p_operation TEXT,p_authority_present BOOLEAN,p_instance_id TEXT)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $d3$
DECLARE
    v_resource_type TEXT; v_resource_id TEXT; v_tenant TEXT;
    v_owner TEXT; v_lifecycle TEXT; v_member BOOLEAN; v_visible BOOLEAN; v_grant BOOLEAN;
    v_instance RECORD; v_definition JSONB;

BEGIN
    IF p_actor_id IS NULL OR btrim(p_actor_id)='' OR p_authority_present IS NULL
       OR p_instance_id IS NULL OR btrim(p_instance_id)='' THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    IF p_operation IS NULL OR p_operation NOT IN ('READ','ACTIVATE','TRANSITION') THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
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
        AND a.actor_id=p_actor_id AND a.operation='workflow:'||lower(p_operation) AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id FOR SHARE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT jsonb_build_object('definition_id',d.definition_id,'definition_version',d.definition_version,
      'initial_state',d.initial_state,
      'states',COALESCE((SELECT jsonb_agg(s.state_id ORDER BY s.state_id) FROM "${flyway:defaultSchema}".workflow_definition_states s
        WHERE s.definition_id=d.definition_id AND s.definition_version=d.definition_version),'[]'::JSONB),
      'transitions',COALESCE((SELECT jsonb_agg(jsonb_build_object('transition_id',t.transition_id,
        'source_state',t.source_state,'target_state',t.target_state) ORDER BY t.transition_id)
        FROM "${flyway:defaultSchema}".workflow_definition_transitions t
        WHERE t.definition_id=d.definition_id AND t.definition_version=d.definition_version),'[]'::JSONB))
      INTO v_definition FROM "${flyway:defaultSchema}".workflow_definitions d WHERE d.definition_id=v_instance.definition_id AND d.definition_version=v_instance.definition_version;
    IF NOT FOUND THEN RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT, jsonb_build_object('code','DEFINITION_NOT_FOUND'); RETURN; END IF;
    RETURN QUERY SELECT 'SNAPSHOT'::TEXT,jsonb_build_object('instance',to_jsonb(v_instance),'definition',v_definition);
END;
$d3$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_load_instance(TEXT,TEXT,BOOLEAN,TEXT) FROM PUBLIC;
CREATE FUNCTION "${flyway:defaultSchema}".wf_port_load_history(p_actor_id TEXT,p_authority_present BOOLEAN,p_instance_id TEXT)
RETURNS TABLE(outcome TEXT, body JSONB)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
AS $d3$
DECLARE
    v_resource_type TEXT; v_resource_id TEXT; v_tenant TEXT;
    v_owner TEXT; v_lifecycle TEXT; v_member BOOLEAN; v_visible BOOLEAN; v_grant BOOLEAN;
    v_instance RECORD; v_definition JSONB;

BEGIN
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
        AND a.actor_id=p_actor_id AND a.operation='workflow:read' AND a.granted) INTO v_grant;
    IF NOT v_grant OR NOT p_authority_present THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT, NULL::JSONB; RETURN; END IF;
    SELECT i.* INTO v_instance FROM "${flyway:defaultSchema}".workflow_instances i WHERE i.instance_id=p_instance_id FOR SHARE;
    IF NOT FOUND OR v_instance.resource_type IS DISTINCT FROM v_resource_type
       OR v_instance.resource_id IS DISTINCT FROM v_resource_id THEN RETURN QUERY SELECT 'UNAVAILABLE'::TEXT, NULL::JSONB; RETURN; END IF;
    RETURN QUERY SELECT 'HISTORY'::TEXT,COALESCE(jsonb_agg(to_jsonb(h) ORDER BY h.resulting_runtime_version),'[]'::JSONB)
      FROM "${flyway:defaultSchema}".workflow_transition_history h WHERE h.instance_id=p_instance_id;
END;
$d3$;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".wf_port_load_history(TEXT,BOOLEAN,TEXT) FROM PUBLIC;
