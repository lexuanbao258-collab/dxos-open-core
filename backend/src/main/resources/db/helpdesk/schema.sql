-- Helpdesk Java-authorization candidate; disposable setup only, outside Flyway.
-- Reuses policy tables and constraints from the unchanged V5 physical candidate.
CREATE TABLE "${flyway:defaultSchema}".helpdesk_actors (
    tenant_id TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    submitter BOOLEAN NOT NULL DEFAULT false,
    coordinator BOOLEAN NOT NULL DEFAULT false,
    worker BOOLEAN NOT NULL DEFAULT false,
    -- Offline-provisioned label from a trusted personnel/identity directory; never accept via Portal.
    -- NULL means the provisioner has not supplied a verified display name.
    display_name TEXT CHECK (display_name IS NULL OR length(btrim(display_name)) BETWEEN 1 AND 200),
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

REVOKE ALL ON ALL TABLES IN SCHEMA "${flyway:defaultSchema}" FROM PUBLIC;
REVOKE ALL ON FUNCTION "${flyway:defaultSchema}".helpdesk_policy_lock() FROM PUBLIC;
