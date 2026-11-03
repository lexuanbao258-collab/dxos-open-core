-- Offline disposable setup: replace both placeholders with validated schema/role identifiers.
-- Java owns authorization; the runtime has data access, not schema or role administration.
GRANT USAGE ON SCHEMA "${flyway:defaultSchema}" TO "${helpdesk:runtimeRole}";
GRANT SELECT ON "${flyway:defaultSchema}".tenant_guards,
    "${flyway:defaultSchema}".tenant_memberships, "${flyway:defaultSchema}".resource_registry,
    "${flyway:defaultSchema}".resource_acl, "${flyway:defaultSchema}".workflow_definition_bindings,
    "${flyway:defaultSchema}".helpdesk_actors, "${flyway:defaultSchema}".helpdesk_requests,
    "${flyway:defaultSchema}".workflow_definitions, "${flyway:defaultSchema}".workflow_definition_states,
    "${flyway:defaultSchema}".workflow_definition_transitions, "${flyway:defaultSchema}".workflow_instances,
    "${flyway:defaultSchema}".workflow_transition_history TO "${helpdesk:runtimeRole}";
-- PostgreSQL row locks require UPDATE on at least one column of the locked table.
GRANT UPDATE (acl_revision) ON "${flyway:defaultSchema}".tenant_guards TO "${helpdesk:runtimeRole}";
GRANT INSERT ON "${flyway:defaultSchema}".resource_registry, "${flyway:defaultSchema}".resource_acl,
    "${flyway:defaultSchema}".helpdesk_requests, "${flyway:defaultSchema}".workflow_instances,
    "${flyway:defaultSchema}".workflow_transition_history TO "${helpdesk:runtimeRole}";
GRANT UPDATE (granted) ON "${flyway:defaultSchema}".resource_acl TO "${helpdesk:runtimeRole}";
GRANT UPDATE (assignee_id, assignment_version, resolution) ON "${flyway:defaultSchema}".helpdesk_requests TO "${helpdesk:runtimeRole}";
GRANT UPDATE (current_state, runtime_version, lifecycle, updated_at_seconds, updated_at_nanos)
    ON "${flyway:defaultSchema}".workflow_instances TO "${helpdesk:runtimeRole}";
