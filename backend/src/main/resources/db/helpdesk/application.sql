-- Opt-in disposable Application candidate; deliberately outside Flyway locations.
-- Requires V3 and the existing Stage 6 guarded SQL functions. No LIVE migration.
CREATE TABLE "${flyway:defaultSchema}".helpdesk_actors (
    tenant_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    submitter BOOLEAN NOT NULL DEFAULT false,
    coordinator BOOLEAN NOT NULL DEFAULT false,
    worker BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (tenant_id, actor_id),
    FOREIGN KEY (tenant_id, actor_id) REFERENCES "${flyway:defaultSchema}".tenant_memberships
);
CREATE TABLE "${flyway:defaultSchema}".helpdesk_requests (
    request_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    requester_id TEXT NOT NULL,
    instance_id TEXT NOT NULL UNIQUE REFERENCES "${flyway:defaultSchema}".workflow_instances
        DEFERRABLE INITIALLY DEFERRED,
    title TEXT NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    description TEXT NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 4000),
    assignee_id TEXT,
    assignment_version BIGINT NOT NULL DEFAULT 0 CHECK (assignment_version >= 0),
    resolution TEXT CHECK (length(btrim(resolution)) BETWEEN 1 AND 4000),
    FOREIGN KEY (tenant_id, requester_id) REFERENCES "${flyway:defaultSchema}".tenant_memberships,
    FOREIGN KEY (tenant_id, assignee_id) REFERENCES "${flyway:defaultSchema}".tenant_memberships
);

-- Offline policy edits and Application writes share the same permanent tenant lock.
CREATE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $app$
BEGIN
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards
        WHERE tenant_id=COALESCE(NEW.tenant_id,OLD.tenant_id) FOR UPDATE;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$app$;
CREATE TRIGGER helpdesk_actor_lock BEFORE INSERT OR UPDATE OR DELETE ON "${flyway:defaultSchema}".helpdesk_actors
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock();
CREATE TRIGGER helpdesk_membership_lock BEFORE INSERT OR UPDATE OR DELETE ON "${flyway:defaultSchema}".tenant_memberships
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock();
CREATE TRIGGER helpdesk_acl_lock BEFORE INSERT OR UPDATE OR DELETE ON "${flyway:defaultSchema}".resource_acl
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock();
CREATE TRIGGER helpdesk_registry_lock BEFORE INSERT OR UPDATE OR DELETE ON "${flyway:defaultSchema}".resource_registry
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock();
CREATE TRIGGER helpdesk_binding_lock BEFORE INSERT OR UPDATE OR DELETE ON "${flyway:defaultSchema}".workflow_definition_bindings
    FOR EACH ROW EXECUTE FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock();

CREATE FUNCTION "${flyway:defaultSchema}".helpdesk_open(
    p_actor TEXT,p_authorized BOOLEAN,p_tenant TEXT,p_id UUID,p_instance TEXT,p_title TEXT,p_description TEXT)
RETURNS TABLE(outcome TEXT,body JSONB) LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $app$
BEGIN
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards WHERE tenant_id=p_tenant FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".tenant_memberships
        WHERE tenant_id=p_tenant AND actor_id=p_actor AND active) THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT,NULL::JSONB; RETURN;
    END IF;
    IF p_authorized IS DISTINCT FROM true OR NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".helpdesk_actors
        WHERE tenant_id=p_tenant AND actor_id=p_actor AND submitter) THEN
        RETURN QUERY SELECT 'FORBIDDEN'::TEXT,NULL::JSONB; RETURN;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".workflow_definition_bindings
        WHERE tenant_id=p_tenant AND resource_type='mvp_request' AND definition_id='it-helpdesk'
        AND definition_version=1 AND active) THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT,NULL::JSONB; RETURN;
    END IF;
    INSERT INTO "${flyway:defaultSchema}".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)
        VALUES('mvp_request',p_id::TEXT,p_tenant,p_actor,'ACTIVE');
    INSERT INTO "${flyway:defaultSchema}".resource_acl
        SELECT p_tenant,'mvp_request',p_id::TEXT,p_actor,op,true
        FROM unnest(ARRAY['workflow:create','workflow:activate','workflow:read','workflow:transition']) op;
    -- Coordinators are explicitly provisioned within this tenant, never inferred from JWT roles.
    INSERT INTO "${flyway:defaultSchema}".resource_acl
        SELECT p_tenant,'mvp_request',p_id::TEXT,a.actor_id,op,true
        FROM "${flyway:defaultSchema}".helpdesk_actors a
        JOIN "${flyway:defaultSchema}".tenant_memberships m USING(tenant_id,actor_id)
        CROSS JOIN unnest(ARRAY['workflow:read','workflow:transition']) op
        WHERE a.tenant_id=p_tenant AND a.coordinator AND m.active
        ON CONFLICT DO NOTHING;
    INSERT INTO "${flyway:defaultSchema}".helpdesk_requests(request_id,tenant_id,requester_id,instance_id,title,description)
        VALUES(p_id,p_tenant,p_actor,p_instance,p_title,p_description);
    RETURN QUERY SELECT 'OK'::TEXT,'{}'::JSONB;
END;
$app$;

CREATE FUNCTION "${flyway:defaultSchema}".helpdesk_request(
    p_actor TEXT,p_read BOOLEAN,p_transition BOOLEAN,p_id UUID,p_command TEXT,
    p_assignee TEXT,p_assignment_version BIGINT,p_resolution TEXT)
RETURNS TABLE(outcome TEXT,body JSONB) LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $app$
DECLARE
    v_tenant TEXT; v_instance TEXT; v_outcome TEXT; v_workflow JSONB;
    v_request "${flyway:defaultSchema}".helpdesk_requests%ROWTYPE;
    v_coordinator BOOLEAN; v_worker BOOLEAN; v_transition BOOLEAN; v_assignee_eligible BOOLEAN;
BEGIN
    IF p_command IS NULL OR p_command NOT IN ('read','triage','assign','start_work','resolve','confirm') THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT,NULL::JSONB; RETURN;
    END IF;
    SELECT tenant_id INTO v_tenant FROM "${flyway:defaultSchema}".helpdesk_requests WHERE request_id=p_id;
    IF NOT FOUND THEN RETURN QUERY SELECT 'HIDDEN'::TEXT,NULL::JSONB; RETURN; END IF;
    IF p_command='read' THEN
        PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards WHERE tenant_id=v_tenant FOR SHARE;
    ELSE
        PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards WHERE tenant_id=v_tenant FOR UPDATE;
    END IF;
    SELECT instance_id INTO v_instance FROM "${flyway:defaultSchema}".helpdesk_requests WHERE request_id=p_id;
    SELECT w.outcome,w.body INTO v_outcome,v_workflow FROM "${flyway:defaultSchema}".wf_port_load_instance(
        p_actor,CASE WHEN p_command='read' THEN 'READ' ELSE 'TRANSITION' END,
        CASE WHEN p_command='read' THEN p_read ELSE p_transition END,v_instance) w;
    IF v_outcome IS DISTINCT FROM 'SNAPSHOT' THEN
        RETURN QUERY SELECT v_outcome,v_workflow; RETURN;
    END IF;
    -- Writes also require permission to materialize the returned business projection.
    IF p_read IS DISTINCT FROM true OR NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl
        WHERE tenant_id=v_tenant AND resource_type='mvp_request' AND resource_id=p_id::TEXT
        AND actor_id=p_actor AND operation='workflow:read' AND granted) THEN
        RETURN QUERY SELECT 'FORBIDDEN'::TEXT,NULL::JSONB; RETURN;
    END IF;
    IF v_workflow->'instance'->>'resource_type' IS DISTINCT FROM 'mvp_request'
        OR v_workflow->'instance'->>'resource_id' IS DISTINCT FROM p_id::TEXT
        OR v_workflow->'instance'->>'definition_id' IS DISTINCT FROM 'it-helpdesk'
        OR v_workflow->'instance'->>'definition_version' IS DISTINCT FROM '1' THEN
        RETURN QUERY SELECT 'UNAVAILABLE'::TEXT,NULL::JSONB; RETURN;
    END IF;
    SELECT * INTO v_request FROM "${flyway:defaultSchema}".helpdesk_requests WHERE request_id=p_id;
    SELECT COALESCE(bool_or(coordinator),false),COALESCE(bool_or(worker),false)
        INTO v_coordinator,v_worker FROM "${flyway:defaultSchema}".helpdesk_actors
        WHERE tenant_id=v_tenant AND actor_id=p_actor;
    SELECT p_transition IS TRUE AND EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl
        WHERE tenant_id=v_tenant AND resource_type='mvp_request' AND resource_id=p_id::TEXT
        AND actor_id=p_actor AND operation='workflow:transition' AND granted) INTO v_transition;
    SELECT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".helpdesk_actors a
        JOIN "${flyway:defaultSchema}".tenant_memberships m USING(tenant_id,actor_id)
        WHERE a.tenant_id=v_tenant AND a.actor_id=v_request.assignee_id AND a.worker AND m.active)
        INTO v_assignee_eligible;
    IF p_command<>'read' THEN
        IF (p_command IN ('triage','assign') AND NOT v_coordinator)
            OR (p_command IN ('start_work','resolve') AND (p_actor IS DISTINCT FROM v_request.assignee_id OR NOT v_worker))
            OR (p_command='confirm' AND p_actor IS DISTINCT FROM v_request.requester_id) THEN
            RETURN QUERY SELECT 'FORBIDDEN'::TEXT,NULL::JSONB; RETURN;
        END IF;
        IF p_command='assign' THEN
            IF v_workflow->'instance'->>'current_state' IS DISTINCT FROM 'TRIAGED' THEN
                RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT,jsonb_build_object('code','TRANSITION_NOT_ALLOWED'); RETURN;
            END IF;
            IF p_assignment_version IS NULL OR p_assignment_version<>v_request.assignment_version THEN
                RETURN QUERY SELECT 'DOMAIN_ERROR'::TEXT,jsonb_build_object('code','VERSION_CONFLICT'); RETURN;
            END IF;
            IF NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".helpdesk_actors a
                JOIN "${flyway:defaultSchema}".tenant_memberships m USING(tenant_id,actor_id)
                WHERE a.tenant_id=v_tenant AND a.actor_id=p_assignee AND a.worker AND m.active) THEN
                RETURN QUERY SELECT 'FORBIDDEN'::TEXT,NULL::JSONB; RETURN;
            END IF;
            UPDATE "${flyway:defaultSchema}".resource_acl SET granted=false
                WHERE tenant_id=v_tenant AND resource_type='mvp_request' AND resource_id=p_id::TEXT
                AND actor_id=v_request.assignee_id AND actor_id<>v_request.requester_id
                AND NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".helpdesk_actors a
                    WHERE a.tenant_id=v_tenant AND a.actor_id=v_request.assignee_id AND a.coordinator);
            INSERT INTO "${flyway:defaultSchema}".resource_acl
                SELECT v_tenant,'mvp_request',p_id::TEXT,p_assignee,op,true
                FROM unnest(ARRAY['workflow:read','workflow:transition']) op
                ON CONFLICT(tenant_id,resource_type,resource_id,actor_id,operation) DO UPDATE SET granted=true;
            UPDATE "${flyway:defaultSchema}".helpdesk_requests SET assignee_id=p_assignee,
                assignment_version=assignment_version+1 WHERE request_id=p_id RETURNING * INTO v_request;
        ELSIF p_command='resolve' THEN
            UPDATE "${flyway:defaultSchema}".helpdesk_requests SET resolution=p_resolution
                WHERE request_id=p_id RETURNING * INTO v_request;
        ELSIF p_command='confirm' AND v_request.resolution IS NULL THEN
            RETURN QUERY SELECT 'UNAVAILABLE'::TEXT,NULL::JSONB; RETURN;
        END IF;
    END IF;
    RETURN QUERY SELECT 'OK'::TEXT,jsonb_build_object('request',to_jsonb(v_request),'workflow',v_workflow,
        'coordinator',v_coordinator,'worker',v_worker,'transition',v_transition,'assigneeEligible',v_assignee_eligible);
END;
$app$;

CREATE FUNCTION "${flyway:defaultSchema}".helpdesk_list(p_actor TEXT,p_authorized BOOLEAN,p_tenant TEXT,p_after UUID,p_limit INTEGER)
RETURNS TABLE(outcome TEXT,body JSONB) LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $app$
BEGIN
    IF p_authorized IS DISTINCT FROM true THEN RETURN QUERY SELECT 'FORBIDDEN'::TEXT,NULL::JSONB; RETURN; END IF;
    PERFORM 1 FROM "${flyway:defaultSchema}".tenant_guards WHERE tenant_id=p_tenant FOR SHARE;
    IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".tenant_memberships
        WHERE tenant_id=p_tenant AND actor_id=p_actor AND active) THEN
        RETURN QUERY SELECT 'HIDDEN'::TEXT,NULL::JSONB; RETURN;
    END IF;
    RETURN QUERY SELECT 'OK'::TEXT,COALESCE(jsonb_agg(r.request_id ORDER BY r.request_id),'[]'::JSONB) FROM (
        SELECT h.request_id FROM "${flyway:defaultSchema}".helpdesk_requests h
        JOIN "${flyway:defaultSchema}".resource_registry g ON g.resource_type='mvp_request' AND g.resource_id=h.request_id::TEXT
            AND g.tenant_id=h.tenant_id AND g.lifecycle='ACTIVE'
        WHERE h.tenant_id=p_tenant AND (p_after IS NULL OR h.request_id>p_after)
        AND EXISTS(SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a WHERE a.tenant_id=h.tenant_id
            AND a.resource_type='mvp_request' AND a.resource_id=h.request_id::TEXT AND a.actor_id=p_actor
            AND a.operation='workflow:read' AND a.granted)
        ORDER BY h.request_id LIMIT LEAST(GREATEST(p_limit,1),101)
    ) r;
END;
$app$;
REVOKE ALL ON ALL TABLES IN SCHEMA "${flyway:defaultSchema}" FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock() FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".helpdesk_open(TEXT,BOOLEAN,TEXT,UUID,TEXT,TEXT,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".helpdesk_request(TEXT,BOOLEAN,BOOLEAN,UUID,TEXT,TEXT,BIGINT,TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".helpdesk_list(TEXT,BOOLEAN,TEXT,UUID,INTEGER) FROM PUBLIC;
