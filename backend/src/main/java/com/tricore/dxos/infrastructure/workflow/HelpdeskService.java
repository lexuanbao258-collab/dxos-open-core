package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tricore.dxos.application.authorization.*;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.*;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Java authorizes each command inside the transaction that commits its business and Workflow facts. */
public final class HelpdeskService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PostgresWorkflowPersistence persistence;
    private final CurrentPrincipal principal;
    private final String schema;
    private final String tenant;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public HelpdeskService(PostgresWorkflowPersistence persistence, CurrentPrincipal principal,
                           String schema, String tenant, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence);
        this.principal = Objects.requireNonNull(principal);
        this.schema = new WorkflowSchema(schema).quoted();
        this.tenant = Objects.requireNonNull(tenant);
        this.clock = Objects.requireNonNull(clock);
    }

    public record AssigneeOption(String id, String displayName) {}
    public record RequestView(UUID id, String title, String description, String requestType,
            String requesterId, String assigneeId, String resolution, long assignmentVersion,
            String workflowInstanceId, String state, long version, List<String> allowedActions,
            @JsonInclude(JsonInclude.Include.NON_NULL) String requesterDisplayName,
            @JsonInclude(JsonInclude.Include.NON_NULL) String assigneeDisplayName,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<AssigneeOption> eligibleAssignees) {}
    public record Page(List<RequestView> items, UUID nextCursor) {}
    public record HistoryView(String transitionId, String sourceState, String targetState,
            long version, Instant occurredAt) {}

    public RequestView create(String title, String description) {
        var actor = principal.requireVerified();
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String instanceId = "wf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return persistence.guardedTransaction(true, connection -> {
            lockTenant(connection, true);
            requireMember(connection, actor.subjectId());
            if (!actorFacts(connection, actor.subjectId()).path("submitter").asBoolean()) throw forbidden();
            var resource = new ResourceReference("mvp_request", id.toString());
            var definition = persistence.loadDefinition(connection, "it-helpdesk", 1);
            var creation = new WorkflowAclSnapshotReader.ResourceAclSnapshot(resource, actor.subjectId(), tenant,
                    true, true, true, EnumSet.allOf(WorkflowOperation.class), bindings(connection));
            var authorization = new WorkflowAuthorization(new TenantResourceWorkflowAccessPolicy(
                    (target, verifiedActor) -> Optional.of(creation)));
            authorization.authorizeCreation(actor, definition, resource);
            var initial = WorkflowInstance.notStarted(instanceId, definition,
                    resource, clock.instant());
            authorization.authorizeInstance(actor, initial, WorkflowOperation.ACTIVATE);
            authorization.authorizeInstance(actor, initial, WorkflowOperation.READ);
            execute(connection, "INSERT INTO %s.resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) VALUES('mvp_request',?,?,?,'ACTIVE')",
                    id.toString(), tenant, actor.subjectId());
            for (WorkflowOperation operation : WorkflowOperation.values()) grant(connection, id, actor.subjectId(), operation);
            try (var statement = prepare(connection, "SELECT a.actor_id,a.coordinator,m.active FROM %s.helpdesk_actors a JOIN %s.tenant_memberships m USING(tenant_id,actor_id) WHERE a.tenant_id=?", tenant);
                 var rows = statement.executeQuery()) {
                while (rows.next()) if (rows.getBoolean("coordinator") && rows.getBoolean("active")) {
                    grant(connection, id, rows.getString("actor_id"), WorkflowOperation.READ);
                    grant(connection, id, rows.getString("actor_id"), WorkflowOperation.TRANSITION);
                }
            }
            execute(connection, "INSERT INTO %s.helpdesk_requests(request_id,tenant_id,requester_id,instance_id,title,description) VALUES(?,?,?,?,?,?)",
                    id, tenant, actor.subjectId(), instanceId, title, description);
            persistence.createInstance(connection, initial);
            var active = initial.activate(definition, clock.instant());
            persistence.commitActivation(connection, initial, active);
            return view(request(connection, actor, id, "read", null, null, null), actor, false);
        });
    }

    public RequestView get(UUID id) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(false, c -> view(request(c, actor, id, "read", null, null, null), actor, true));
    }

    public Page list(UUID after, int limit) {
        var actor = principal.requireVerified();
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return persistence.guardedTransaction(false, c -> {
            if (!has(actor, WorkflowOperation.READ)) throw forbidden();
            lockTenant(c, false);
            requireMember(c, actor.subjectId());
            List<RequestView> items = new ArrayList<>();
            try (var statement = prepare(c, "SELECT request_id FROM %s.helpdesk_requests WHERE tenant_id=? AND (?::uuid IS NULL OR request_id>?) ORDER BY request_id",
                    tenant, after, after); var rows = statement.executeQuery()) {
                while (rows.next() && items.size() <= limit) {
                    try {
                        items.add(view(request(c, actor, rows.getObject(1, UUID.class), "read", null, null, null), actor, false));
                    } catch (WorkflowAccessException denied) {
                        if (denied.decision() != WorkflowAccessDecision.HIDDEN && denied.decision() != WorkflowAccessDecision.FORBIDDEN) throw denied;
                    }
                }
            }
            boolean more = items.size() > limit;
            if (more) items.removeLast();
            return new Page(List.copyOf(items), more ? items.getLast().id() : null);
        });
    }

    public List<HistoryView> history(UUID id) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(false, c -> {
            var facts = request(c, actor, id, "read", null, null, null);
            var records = persistence.loadHistory(c, facts.instance().instanceId());
            List<HistoryView> result = new ArrayList<>();
            for (TransitionRecord record : records) {
                result.add(new HistoryView(record.transitionId(), record.sourceState(), record.targetState(),
                        record.resultingRuntimeVersion(), record.occurredAt()));
            }
            return List.copyOf(result);
        });
    }

    public RequestView assign(UUID id, String assignee, long expectedAssignmentVersion) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(true, c -> {
            request(c, actor, id, "assign", assignee, expectedAssignmentVersion, null);
            return view(request(c, actor, id, "read", null, null, null), actor, false);
        });
    }

    public RequestView transition(UUID id, String action, long expectedVersion, String resolution) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(true, c -> {
            if (!Set.of("triage", "start_work", "resolve", "confirm").contains(action)) throw unavailable();
            var facts = request(c, actor, id, action, null, null, resolution);
            var definition = persistence.loadDefinition(c, "it-helpdesk", 1);
            var instance = facts.instance();
            var result = instance.transition(definition, new TransitionCommand(instance.instanceId(), action,
                    new ActorReference(actor.subjectId()), expectedVersion), clock.instant());
            persistence.commitTransition(c, result);
            return view(request(c, actor, id, "read", null, null, null), actor, false);
        });
    }

    private record AuthorizedRequest(JsonNode facts, WorkflowInstance instance) {}

    private AuthorizedRequest request(Connection c, AuthenticatedPrincipal actor, UUID id, String command,
                             String assignee, Long assignmentVersion, String resolution) throws SQLException {
        boolean write = !command.equals("read");
        lockTenant(c, write);
        String instanceId;
        try (var statement = prepare(c, "SELECT instance_id FROM %s.helpdesk_requests WHERE request_id=? AND tenant_id=?", id, tenant);
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw hidden();
            instanceId = rows.getString(1);
        }
        var instance = persistence.loadInstance(c, instanceId);
        var resource = new ResourceReference("mvp_request", id.toString());
        if (!instance.resourceReference().equals(resource) || !instance.definitionId().equals("it-helpdesk")
                || instance.definitionVersion() != 1) throw unavailable();
        var acl = snapshot(c, resource, actor.subjectId());
        var authorization = new WorkflowAuthorization(new TenantResourceWorkflowAccessPolicy((target, verifiedActor) -> acl));
        authorization.authorizeInstance(actor, instance, write ? WorkflowOperation.TRANSITION : WorkflowOperation.READ);
        authorization.authorizeInstance(actor, instance, WorkflowOperation.READ);
        JsonNode business = business(c, id);
        JsonNode roles = actorFacts(c, actor.subjectId());
        boolean coordinator = roles.path("coordinator").asBoolean();
        boolean worker = roles.path("worker").asBoolean();
        if (write) {
            if ((Set.of("triage", "assign").contains(command) && !coordinator)
                    || (Set.of("start_work", "resolve").contains(command)
                        && (!actor.subjectId().equals(nullable(business, "assignee_id")) || !worker))
                    || (command.equals("confirm") && (!roles.path("submitter").asBoolean()
                        || !actor.subjectId().equals(business.path("requester_id").asText())))) throw forbidden();
            if (command.equals("assign")) {
                if (!instance.currentState().equals("TRIAGED")) throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
                if (assignmentVersion == null || assignmentVersion != business.path("assignment_version").longValue())
                    throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
                if (!eligible(c, assignee)) throw forbidden();
                String previous = nullable(business, "assignee_id");
                if (previous != null && !previous.equals(business.path("requester_id").asText())
                        && !actorFacts(c, previous).path("coordinator").asBoolean()) {
                    execute(c, "UPDATE %s.resource_acl SET granted=false WHERE tenant_id=? AND resource_type='mvp_request' AND resource_id=? AND actor_id=?",
                            tenant, id.toString(), previous);
                }
                grant(c, id, assignee, WorkflowOperation.READ);
                grant(c, id, assignee, WorkflowOperation.TRANSITION);
                execute(c, "UPDATE %s.helpdesk_requests SET assignee_id=?,assignment_version=assignment_version+1 WHERE request_id=? AND tenant_id=?",
                        assignee, id, tenant);
            } else if (command.equals("resolve")) {
                execute(c, "UPDATE %s.helpdesk_requests SET resolution=? WHERE request_id=? AND tenant_id=?", resolution, id, tenant);
            } else if (command.equals("confirm") && nullable(business, "resolution") == null) throw unavailable();
            business = business(c, id);
        }
        ObjectNode facts = JSON.createObjectNode();
        facts.set("request", business);
        facts.put("coordinator", coordinator).put("worker", worker).put("submitter", roles.path("submitter").asBoolean())
                .put("transition", has(actor, WorkflowOperation.TRANSITION) && acl.orElseThrow().grants().contains(WorkflowOperation.TRANSITION))
                .put("assigneeEligible", eligible(c, nullable(business, "assignee_id")));
        facts.put("requesterDisplayName", nullable(actorFacts(c, business.path("requester_id").asText()), "display_name"));
        facts.put("assigneeDisplayName", nullable(actorFacts(c, nullable(business, "assignee_id")), "display_name"));
        if (!write && coordinator && facts.path("transition").asBoolean() && instance.currentState().equals("TRIAGED")) {
            var candidates = facts.putArray("eligibleAssignees");
            try (var statement = prepare(c, "SELECT a.actor_id,a.display_name,a.worker,m.active FROM %s.helpdesk_actors a JOIN %s.tenant_memberships m USING(tenant_id,actor_id) WHERE a.tenant_id=? ORDER BY a.actor_id", tenant);
                 var rows = statement.executeQuery()) {
                while (rows.next()) if (rows.getBoolean("worker") && rows.getBoolean("active"))
                    candidates.addObject().put("id", rows.getString("actor_id")).put("displayName", rows.getString("display_name"));
            }
        }
        return new AuthorizedRequest(facts, instance);
    }

    private RequestView view(AuthorizedRequest authorized, AuthenticatedPrincipal actor, boolean includeDetailFields) {
        JsonNode facts = authorized.facts();
        JsonNode request = facts.path("request");
        var instance = authorized.instance();
        String state = instance.currentState();
        List<String> actions = new ArrayList<>();
        if (facts.path("transition").asBoolean()) {
            if (facts.path("coordinator").asBoolean()) {
                if (state.equals("SUBMITTED")) actions.add("triage");
                if (state.equals("TRIAGED")) actions.add("assign");
            }
            if (actor.subjectId().equals(nullable(request, "assignee_id")) && facts.path("worker").asBoolean()
                    && facts.path("assigneeEligible").asBoolean()) {
                if (state.equals("TRIAGED")) actions.add("start");
                if (state.equals("IN_PROGRESS")) actions.add("resolve");
            }
            if (state.equals("RESOLVED") && facts.path("submitter").asBoolean()
                    && actor.subjectId().equals(request.path("requester_id").asText())
                    && nullable(request, "resolution") != null) actions.add("confirm");
        }
        List<AssigneeOption> eligible = null;
        if (includeDetailFields && actions.contains("assign")) {
            JsonNode candidates = facts.path("eligibleAssignees");
            if (!candidates.isArray()) throw unavailable();
            List<AssigneeOption> options = new ArrayList<>();
            for (JsonNode candidate : candidates) {
                JsonNode id = candidate.path("id");
                JsonNode name = candidate.path("displayName");
                if (!id.isTextual() || id.asText().isBlank()
                        || (!name.isNull() && !name.isTextual())) throw unavailable();
                options.add(new AssigneeOption(id.asText(), name.isNull() ? null : name.asText()));
            }
            eligible = List.copyOf(options);
        }
        return new RequestView(UUID.fromString(request.path("request_id").asText()), request.path("title").asText(),
                request.path("description").asText(), "IT_HELPDESK", request.path("requester_id").asText(),
                nullable(request, "assignee_id"), nullable(request, "resolution"), request.path("assignment_version").longValue(),
                instance.instanceId(), state, instance.runtimeVersion(), List.copyOf(actions),
                includeDetailFields ? nullable(facts, "requesterDisplayName") : null,
                includeDetailFields ? nullable(facts, "assigneeDisplayName") : null, eligible);
    }

    private static String nullable(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }
    private static boolean has(AuthenticatedPrincipal actor, WorkflowOperation operation) {
        return actor.authorities().stream().anyMatch(a -> a.satisfies(operation.requiredAuthority()));
    }

    private void lockTenant(Connection c, boolean write) throws SQLException {
        // Policy-edit triggers take this same lock; READ COMMITTED reloads facts after a waiting revoke commits.
        try (var statement = prepare(c, "SELECT tenant_id FROM %s.tenant_guards WHERE tenant_id=?" + (write ? " FOR UPDATE" : " FOR SHARE"), tenant);
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw hidden();
        }
    }

    private void requireMember(Connection c, String actor) throws SQLException {
        try (var statement = prepare(c, "SELECT active FROM %s.tenant_memberships WHERE tenant_id=? AND actor_id=?", tenant, actor);
             var rows = statement.executeQuery()) {
            if (!rows.next() || !rows.getBoolean(1)) throw hidden();
        }
    }

    private Optional<WorkflowAclSnapshotReader.ResourceAclSnapshot> snapshot(Connection c, ResourceReference resource, String actor) throws SQLException {
        String owner;
        boolean active;
        boolean member;
        try (var statement = prepare(c, "SELECT r.owner_actor_id,r.lifecycle,m.active FROM %s.resource_registry r LEFT JOIN %s.tenant_memberships m ON m.tenant_id=r.tenant_id AND m.actor_id=? WHERE r.tenant_id=? AND r.resource_type=? AND r.resource_id=?",
                actor, tenant, resource.resourceType(), resource.resourceId()); var rows = statement.executeQuery()) {
            if (!rows.next()) return Optional.empty();
            owner = rows.getString(1);
            active = rows.getString(2).equals("ACTIVE");
            member = rows.getBoolean(3);
        }
        Set<WorkflowOperation> grants = EnumSet.noneOf(WorkflowOperation.class);
        try (var statement = prepare(c, "SELECT operation,granted FROM %s.resource_acl WHERE tenant_id=? AND resource_type=? AND resource_id=? AND actor_id=?",
                tenant, resource.resourceType(), resource.resourceId(), actor); var rows = statement.executeQuery()) {
            while (rows.next()) if (rows.getBoolean("granted")) {
                String operation = rows.getString("operation");
                grants.add(Arrays.stream(WorkflowOperation.values()).filter(value -> value.requiredAuthority().value().equals(operation))
                        .findFirst().orElseThrow(HelpdeskService::unavailable));
            }
        }
        return Optional.of(new WorkflowAclSnapshotReader.ResourceAclSnapshot(resource, actor, tenant,
                active, member, actor.equals(owner), grants, bindings(c)));
    }

    private Set<WorkflowAclSnapshotReader.DefinitionBinding> bindings(Connection c) throws SQLException {
        Set<WorkflowAclSnapshotReader.DefinitionBinding> result = new HashSet<>();
        try (var statement = prepare(c, "SELECT definition_id,definition_version,active FROM %s.workflow_definition_bindings WHERE tenant_id=? AND resource_type='mvp_request'", tenant);
             var rows = statement.executeQuery()) {
            while (rows.next()) if (rows.getBoolean("active")) result.add(new WorkflowAclSnapshotReader.DefinitionBinding(rows.getString(1), rows.getLong(2)));
        }
        return Set.copyOf(result);
    }

    private JsonNode business(Connection c, UUID id) throws SQLException {
        return jsonRow(c, "SELECT to_jsonb(r) FROM %s.helpdesk_requests r WHERE tenant_id=? AND request_id=?", tenant, id);
    }

    private JsonNode actorFacts(Connection c, String actor) throws SQLException {
        return jsonRow(c, "SELECT to_jsonb(a) FROM %s.helpdesk_actors a WHERE tenant_id=? AND actor_id=?", tenant, actor);
    }

    private JsonNode jsonRow(Connection c, String sql, Object... arguments) throws SQLException {
        try (var statement = prepare(c, sql, arguments); var rows = statement.executeQuery()) {
            if (!rows.next()) return JSON.createObjectNode();
            try { return JSON.readTree(rows.getString(1)); }
            catch (java.io.IOException invalid) { throw unavailable(); }
        }
    }

    private boolean eligible(Connection c, String actor) throws SQLException {
        try (var statement = prepare(c, "SELECT a.worker,m.active FROM %s.helpdesk_actors a JOIN %s.tenant_memberships m USING(tenant_id,actor_id) WHERE a.tenant_id=? AND a.actor_id=?", tenant, actor);
             var rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1) && rows.getBoolean(2);
        }
    }

    private void grant(Connection c, UUID id, String actor, WorkflowOperation operation) throws SQLException {
        execute(c, "INSERT INTO %s.resource_acl(tenant_id,resource_type,resource_id,actor_id,operation,granted) VALUES(?,'mvp_request',?,?,?,true) ON CONFLICT(tenant_id,resource_type,resource_id,actor_id,operation) DO UPDATE SET granted=true",
                tenant, id.toString(), actor, operation.requiredAuthority().value());
    }

    private PreparedStatement prepare(Connection c, String sql, Object... arguments) throws SQLException {
        var statement = c.prepareStatement(sql.replace("%s", schema));
        try {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try { statement.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void execute(Connection c, String sql, Object... arguments) throws SQLException {
        try (var statement = prepare(c, sql, arguments)) { statement.executeUpdate(); }
    }

    private static WorkflowAccessException hidden() {
        return new WorkflowAccessException(WorkflowAccessDecision.HIDDEN);
    }
    private static WorkflowAccessException forbidden() {
        return new WorkflowAccessException(WorkflowAccessDecision.FORBIDDEN);
    }
    private static WorkflowAccessException unavailable() {
        return new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE);
    }
}
