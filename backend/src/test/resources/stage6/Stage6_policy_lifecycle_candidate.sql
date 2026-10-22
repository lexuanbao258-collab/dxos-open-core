-- TEST ONLY: apply after V3 and V5_acl_physical_candidate in an approved disposable PG17 schema.
-- No production migration, roles, GRANT, SECURITY DEFINER or runtime wf_port_* entrypoint.
-- INVOKER by default. A disposable installer may separately enable the two exact signatures as
-- DEFINER with fixed approval metadata/digest in stage6.offline_approval and restricted LOGIN grants.
-- pg_proc is public: never store approved targets, members, events or reasons in proconfig.
-- Caller GUCs are not approval: the installer MUST pin this setting on each DEFINER function.
-- session_user is SQL LOGIN provenance, never verified OIDC/admin proof.
-- shortcut: one synthetic approval per function installation; real fixture provenance needs D2 review.
-- shortcut: 64 KiB audit budget for this candidate; review tenant-scale limits before adoption.
ALTER TABLE "${flyway:defaultSchema}".workflow_policy_audit
    ADD CONSTRAINT s6_lifecycle_audit_shape CHECK (
        action IN ('tenant-bootstrap', 'membership-deactivate', 'membership-reactivate', 'acl-grant', 'acl-revoke')
        AND reason_or_approval_ref IS NOT NULL AND length(btrim(reason_or_approval_ref)) BETWEEN 1 AND 256
        AND correlation_ref IS NOT NULL AND length(btrim(correlation_ref)) BETWEEN 1 AND 256
        AND octet_length(change_set::text) <= 65536);

CREATE FUNCTION "${flyway:defaultSchema}".stage6_policy_membership_candidate(
    p_tenant text, p_member text, p_active boolean, p_replacement text,
    p_expected_revision bigint, p_event uuid, p_reason text, p_correlation text)
RETURNS text LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog, pg_temp
AS $policy$
DECLARE
    revision bigint;
    was_active boolean;
    owned record;
    grant_row record;
    changes jsonb := '[]'::jsonb;
    approval jsonb;
BEGIN
    IF current_user <> (
        SELECT pg_get_userbyid(nspowner) FROM pg_namespace
        WHERE oid = '"${flyway:defaultSchema}"'::regnamespace) THEN
        RAISE EXCEPTION 'policy candidate denied' USING ERRCODE = '42501';
    END IF;
    IF current_user <> session_user THEN
        IF current_setting('transaction_isolation') <> 'read committed' THEN
            RAISE EXCEPTION 'offline policy requires READ COMMITTED' USING ERRCODE = '25001';
        END IF;
        SELECT substr(config, length('stage6.offline_approval=') + 1)::jsonb INTO approval
        FROM pg_proc p CROSS JOIN LATERAL unnest(p.proconfig) config
        WHERE p.oid = '"${flyway:defaultSchema}".stage6_policy_membership_candidate(text,text,boolean,text,bigint,uuid,text,text)'::regprocedure
          AND config LIKE 'stage6.offline_approval=%';
        IF (approval->>'operator_ref' LIKE 'test-only/%'
            AND approval->>'fixture_digest' ~ '^[0-9a-f]{64}$'
            AND (approval->>'not_before')::timestamptz <= statement_timestamp()
            AND (approval->>'expires_at')::timestamptz > statement_timestamp()
            AND approval->>'manifest_digest' = encode(sha256(convert_to(
                ((approval - 'manifest_digest') || jsonb_build_object(
                    'login', session_user, 'database', current_database(), 'schema', '${flyway:defaultSchema}',
                    'action', 'membership', 'tenant', p_tenant, 'member', p_member, 'active', p_active,
                    'replacement', p_replacement, 'revision', p_expected_revision,
                    'event', p_event, 'reason', p_reason, 'correlation', p_correlation))::text, 'UTF8')), 'hex')) IS NOT TRUE THEN
            RAISE EXCEPTION 'offline approval denied' USING ERRCODE = '42501';
        END IF;
    END IF;
    SELECT acl_revision INTO revision FROM "${flyway:defaultSchema}".tenant_guards
        WHERE tenant_id = p_tenant FOR UPDATE;
    IF NOT FOUND OR p_expected_revision IS NULL OR revision <> p_expected_revision THEN
        RAISE EXCEPTION 'policy revision conflict' USING ERRCODE = '40001';
    END IF;
    IF approval IS NOT NULL AND (approval->>'expires_at')::timestamptz <= clock_timestamp() THEN
        RAISE EXCEPTION 'offline approval expired while waiting' USING ERRCODE = '42501';
    END IF;
    IF approval IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_proc p CROSS JOIN LATERAL unnest(p.proconfig) config
        WHERE p.oid = '"${flyway:defaultSchema}".stage6_policy_membership_candidate(text,text,boolean,text,bigint,uuid,text,text)'::regprocedure
          AND config LIKE 'stage6.offline_approval=%'
          AND substr(config, length('stage6.offline_approval=') + 1)::jsonb = approval) THEN
        RAISE EXCEPTION 'offline approval changed while waiting' USING ERRCODE = '42501';
    END IF;
    IF p_active IS NULL THEN
        RAISE EXCEPTION 'invalid membership action' USING ERRCODE = '22023';
    END IF;
    SELECT active INTO was_active FROM "${flyway:defaultSchema}".tenant_memberships
        WHERE tenant_id = p_tenant AND actor_id = p_member;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'invalid membership target' USING ERRCODE = '23514';
    END IF;
    IF was_active = p_active THEN RETURN 'NO_CHANGE'; END IF;
    IF p_active AND p_replacement IS NOT NULL THEN
        RAISE EXCEPTION 'reactivation cannot change owner' USING ERRCODE = '23514';
    END IF;
    IF NOT p_active THEN
        FOR owned IN SELECT resource_type, resource_id, owner_actor_id
            FROM "${flyway:defaultSchema}".resource_registry
            WHERE tenant_id = p_tenant AND owner_actor_id = p_member AND lifecycle = 'ACTIVE'
            ORDER BY resource_type, resource_id FOR UPDATE
        LOOP
            IF p_replacement IS NULL OR p_replacement = p_member OR NOT EXISTS (
                SELECT 1 FROM "${flyway:defaultSchema}".tenant_memberships
                WHERE tenant_id = p_tenant AND actor_id = p_replacement AND active) THEN
                RAISE EXCEPTION 'active same-tenant replacement required' USING ERRCODE = '23514';
            END IF;
            changes := changes || jsonb_build_array(jsonb_build_object(
                'relation', 'resource_registry', 'tenant_id', p_tenant,
                'resource_type', owned.resource_type, 'resource_id', owned.resource_id,
                'before', jsonb_build_object('owner_actor_id', owned.owner_actor_id),
                'after', jsonb_build_object('owner_actor_id', p_replacement)));
            UPDATE "${flyway:defaultSchema}".resource_registry SET owner_actor_id = p_replacement
                WHERE resource_type = owned.resource_type AND resource_id = owned.resource_id;
        END LOOP;
        FOR grant_row IN SELECT resource_type, resource_id, operation
            FROM "${flyway:defaultSchema}".resource_acl
            WHERE tenant_id = p_tenant AND actor_id = p_member AND granted
            ORDER BY resource_type, resource_id, operation
        LOOP
            changes := changes || jsonb_build_array(jsonb_build_object(
                'relation', 'resource_acl', 'tenant_id', p_tenant, 'actor_id', p_member,
                'resource_type', grant_row.resource_type, 'resource_id', grant_row.resource_id,
                'operation', grant_row.operation,
                'before', jsonb_build_object('granted', true), 'after', jsonb_build_object('granted', false)));
        END LOOP;
        UPDATE "${flyway:defaultSchema}".resource_acl SET granted = false
            WHERE tenant_id = p_tenant AND actor_id = p_member AND granted;
    END IF;
    UPDATE "${flyway:defaultSchema}".tenant_memberships SET active = p_active
        WHERE tenant_id = p_tenant AND actor_id = p_member;
    changes := changes || jsonb_build_array(jsonb_build_object(
        'relation', 'tenant_memberships', 'tenant_id', p_tenant, 'actor_id', p_member,
        'before', jsonb_build_object('active', was_active), 'after', jsonb_build_object('active', p_active)));
    UPDATE "${flyway:defaultSchema}".tenant_guards SET acl_revision = revision + 1 WHERE tenant_id = p_tenant;
    INSERT INTO "${flyway:defaultSchema}".workflow_policy_audit
        (audit_event_id, tenant_id, previous_acl_revision, resulting_acl_revision,
         actor_id, actor_source_ref, action, change_set, reason_or_approval_ref, correlation_ref, manifest_digest)
    VALUES (p_event, p_tenant, revision, revision + 1, session_user,
        coalesce(approval->>'operator_ref', 'disposable-owner-session'),
        CASE WHEN p_active THEN 'membership-reactivate' ELSE 'membership-deactivate' END,
        changes, p_reason, p_correlation, approval->>'manifest_digest');
    RETURN 'CHANGED';
END;
$policy$;

CREATE FUNCTION "${flyway:defaultSchema}".stage6_policy_acl_candidate(
    p_tenant text, p_type text, p_resource text, p_member text, p_operation text, p_granted boolean,
    p_expected_revision bigint, p_event uuid, p_reason text, p_correlation text)
RETURNS text LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog, pg_temp
AS $policy$
DECLARE
    revision bigint;
    old_grant boolean;
    approval jsonb;
BEGIN
    IF current_user <> (
        SELECT pg_get_userbyid(nspowner) FROM pg_namespace
        WHERE oid = '"${flyway:defaultSchema}"'::regnamespace) THEN
        RAISE EXCEPTION 'policy candidate denied' USING ERRCODE = '42501';
    END IF;
    IF current_user <> session_user THEN
        IF current_setting('transaction_isolation') <> 'read committed' THEN
            RAISE EXCEPTION 'offline policy requires READ COMMITTED' USING ERRCODE = '25001';
        END IF;
        SELECT substr(config, length('stage6.offline_approval=') + 1)::jsonb INTO approval
        FROM pg_proc p CROSS JOIN LATERAL unnest(p.proconfig) config
        WHERE p.oid = '"${flyway:defaultSchema}".stage6_policy_acl_candidate(text,text,text,text,text,boolean,bigint,uuid,text,text)'::regprocedure
          AND config LIKE 'stage6.offline_approval=%';
        IF (approval->>'operator_ref' LIKE 'test-only/%'
            AND approval->>'fixture_digest' ~ '^[0-9a-f]{64}$'
            AND (approval->>'not_before')::timestamptz <= statement_timestamp()
            AND (approval->>'expires_at')::timestamptz > statement_timestamp()
            AND approval->>'manifest_digest' = encode(sha256(convert_to(
                ((approval - 'manifest_digest') || jsonb_build_object(
                    'login', session_user, 'database', current_database(), 'schema', '${flyway:defaultSchema}',
                    'action', 'acl', 'tenant', p_tenant, 'type', p_type, 'resource', p_resource,
                    'member', p_member, 'operation', p_operation, 'granted', p_granted,
                    'revision', p_expected_revision, 'event', p_event, 'reason', p_reason, 'correlation', p_correlation))::text, 'UTF8')), 'hex')) IS NOT TRUE THEN
            RAISE EXCEPTION 'offline approval denied' USING ERRCODE = '42501';
        END IF;
    END IF;
    SELECT acl_revision INTO revision FROM "${flyway:defaultSchema}".tenant_guards
        WHERE tenant_id = p_tenant FOR UPDATE;
    IF NOT FOUND OR p_expected_revision IS NULL OR revision <> p_expected_revision THEN
        RAISE EXCEPTION 'policy revision conflict' USING ERRCODE = '40001';
    END IF;
    IF approval IS NOT NULL AND (approval->>'expires_at')::timestamptz <= clock_timestamp() THEN
        RAISE EXCEPTION 'offline approval expired while waiting' USING ERRCODE = '42501';
    END IF;
    IF approval IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_proc p CROSS JOIN LATERAL unnest(p.proconfig) config
        WHERE p.oid = '"${flyway:defaultSchema}".stage6_policy_acl_candidate(text,text,text,text,text,boolean,bigint,uuid,text,text)'::regprocedure
          AND config LIKE 'stage6.offline_approval=%'
          AND substr(config, length('stage6.offline_approval=') + 1)::jsonb = approval) THEN
        RAISE EXCEPTION 'offline approval changed while waiting' USING ERRCODE = '42501';
    END IF;
    IF p_granted IS NULL OR p_operation IS NULL OR p_operation NOT IN
        ('workflow:create', 'workflow:read', 'workflow:activate', 'workflow:transition') THEN
        RAISE EXCEPTION 'invalid grant action' USING ERRCODE = '22023';
    END IF;
    PERFORM 1 FROM "${flyway:defaultSchema}".resource_registry
        WHERE tenant_id = p_tenant AND resource_type = p_type AND resource_id = p_resource
          AND (NOT p_granted OR lifecycle = 'ACTIVE') FOR UPDATE;
    IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM "${flyway:defaultSchema}".tenant_memberships
        WHERE tenant_id = p_tenant AND actor_id = p_member AND (NOT p_granted OR active)) THEN
        RAISE EXCEPTION 'invalid grant target' USING ERRCODE = '23514';
    END IF;
    SELECT granted INTO old_grant FROM "${flyway:defaultSchema}".resource_acl
        WHERE tenant_id = p_tenant AND resource_type = p_type AND resource_id = p_resource
          AND actor_id = p_member AND operation = p_operation;
    IF old_grant = p_granted OR (NOT FOUND AND NOT p_granted) THEN RETURN 'NO_CHANGE'; END IF;
    INSERT INTO "${flyway:defaultSchema}".resource_acl
        (tenant_id, resource_type, resource_id, actor_id, operation, granted)
        VALUES (p_tenant, p_type, p_resource, p_member, p_operation, p_granted)
        ON CONFLICT (tenant_id, resource_type, resource_id, actor_id, operation)
        DO UPDATE SET granted = EXCLUDED.granted;
    UPDATE "${flyway:defaultSchema}".tenant_guards SET acl_revision = revision + 1 WHERE tenant_id = p_tenant;
    INSERT INTO "${flyway:defaultSchema}".workflow_policy_audit
        (audit_event_id, tenant_id, previous_acl_revision, resulting_acl_revision,
         actor_id, actor_source_ref, action, change_set, reason_or_approval_ref, correlation_ref, manifest_digest)
    VALUES (p_event, p_tenant, revision, revision + 1, session_user,
        coalesce(approval->>'operator_ref', 'disposable-owner-session'),
        CASE WHEN p_granted THEN 'acl-grant' ELSE 'acl-revoke' END,
        jsonb_build_array(jsonb_build_object('relation', 'resource_acl', 'tenant_id', p_tenant,
            'resource_type', p_type, 'resource_id', p_resource, 'actor_id', p_member, 'operation', p_operation,
            'before', jsonb_build_object('granted', old_grant), 'after', jsonb_build_object('granted', p_granted))),
        p_reason, p_correlation, approval->>'manifest_digest');
    RETURN 'CHANGED';
END;
$policy$;

-- Deferred checks permit the multirow operation but reject an invalid final owner/member/grant state.
CREATE FUNCTION "${flyway:defaultSchema}".stage6_policy_lifecycle_check()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog, pg_temp
AS $policy$
BEGIN
    IF EXISTS (SELECT 1 FROM "${flyway:defaultSchema}".resource_registry r
        JOIN "${flyway:defaultSchema}".tenant_memberships m
          ON m.tenant_id = r.tenant_id AND m.actor_id = r.owner_actor_id
        WHERE r.tenant_id = NEW.tenant_id AND r.lifecycle = 'ACTIVE' AND NOT m.active)
    OR EXISTS (SELECT 1 FROM "${flyway:defaultSchema}".resource_acl a
        JOIN "${flyway:defaultSchema}".tenant_memberships m
          ON m.tenant_id = a.tenant_id AND m.actor_id = a.actor_id
        WHERE a.tenant_id = NEW.tenant_id AND a.granted AND NOT m.active) THEN
        RAISE EXCEPTION 'invalid policy lifecycle state' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$policy$;
CREATE CONSTRAINT TRIGGER s6_member_lifecycle AFTER INSERT OR UPDATE ON "${flyway:defaultSchema}".tenant_memberships
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_lifecycle_check();
CREATE CONSTRAINT TRIGGER s6_owner_lifecycle AFTER INSERT OR UPDATE ON "${flyway:defaultSchema}".resource_registry
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_lifecycle_check();
CREATE CONSTRAINT TRIGGER s6_grant_lifecycle AFTER INSERT OR UPDATE ON "${flyway:defaultSchema}".resource_acl
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
    EXECUTE FUNCTION "${flyway:defaultSchema}".stage6_policy_lifecycle_check();

REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".stage6_policy_membership_candidate(text,text,boolean,text,bigint,uuid,text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".stage6_policy_acl_candidate(text,text,text,text,text,boolean,bigint,uuid,text,text) FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".stage6_policy_lifecycle_check() FROM PUBLIC;
