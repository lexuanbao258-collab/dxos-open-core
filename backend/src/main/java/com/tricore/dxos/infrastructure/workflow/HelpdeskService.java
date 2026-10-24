package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.application.authorization.*;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.workflow.*;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Application commands own one confirmed JDBC commit, including business facts and guarded Workflow SQL. */
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

    public record RequestView(UUID id, String title, String description, String requestType,
            String requesterId, String assigneeId, String resolution, long assignmentVersion,
            String workflowInstanceId, String state, long version, List<String> allowedActions) {}
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
            call(connection, "helpdesk_open", "OK", actor.subjectId(),
                    has(actor, WorkflowOperation.CREATE) && has(actor, WorkflowOperation.ACTIVATE)
                            && has(actor, WorkflowOperation.READ), tenant, id, instanceId, title, description);
            var definition = Stage6WorkflowPersistence.definition(call(connection, "wf_port_load_definition", "DEFINITION",
                    "MATERIALIZE", actor.subjectId(), "CREATE", has(actor, WorkflowOperation.CREATE), instanceId,
                    "mvp_request", id.toString(), "it-helpdesk", 1L));
            var initial = WorkflowInstance.notStarted(instanceId, definition,
                    new ResourceReference("mvp_request", id.toString()), clock.instant());
            var active = initial.activate(definition, clock.instant());
            call(connection, "wf_port_create_instance", "SNAPSHOT", actor.subjectId(), has(actor, WorkflowOperation.CREATE),
                    Stage6WorkflowPersistence.snapshot(initial));
            call(connection, "wf_port_commit_activation", "SNAPSHOT", actor.subjectId(), has(actor, WorkflowOperation.ACTIVATE),
                    Stage6WorkflowPersistence.snapshot(initial), Stage6WorkflowPersistence.snapshot(active));
            return view(request(connection, actor, id, "read", null, null, null), actor);
        });
    }

    public RequestView get(UUID id) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(false, c -> view(request(c, actor, id, "read", null, null, null), actor));
    }

    public Page list(UUID after, int limit) {
        var actor = principal.requireVerified();
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return persistence.guardedTransaction(false, c -> {
            JsonNode ids = call(c, "helpdesk_list", "OK", actor.subjectId(), has(actor, WorkflowOperation.READ),
                    tenant, after, limit + 1);
            List<RequestView> items = new ArrayList<>();
            for (JsonNode id : ids) {
                if (items.size() == limit) break;
                items.add(view(request(c, actor, UUID.fromString(id.asText()), "read", null, null, null), actor));
            }
            return new Page(List.copyOf(items), ids.size() > limit ? items.getLast().id() : null);
        });
    }

    public List<HistoryView> history(UUID id) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(false, c -> {
            JsonNode facts = request(c, actor, id, "read", null, null, null);
            String instance = facts.path("request").path("instance_id").asText();
            JsonNode records = call(c, "wf_port_load_history", "HISTORY", actor.subjectId(), has(actor, WorkflowOperation.READ), instance);
            List<HistoryView> result = new ArrayList<>();
            for (JsonNode record : records) {
                result.add(new HistoryView(record.path("transition_id").asText(), record.path("source_state").asText(),
                        record.path("target_state").asText(), record.path("resulting_runtime_version").longValue(),
                        Instant.ofEpochSecond(record.path("occurred_at_seconds").longValue(), record.path("occurred_at_nanos").longValue())));
            }
            return List.copyOf(result);
        });
    }

    public RequestView assign(UUID id, String assignee, long expectedAssignmentVersion) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(true, c -> {
            request(c, actor, id, "assign", assignee, expectedAssignmentVersion, null);
            return view(request(c, actor, id, "read", null, null, null), actor);
        });
    }

    public RequestView transition(UUID id, String action, long expectedVersion, String resolution) {
        var actor = principal.requireVerified();
        return persistence.guardedTransaction(true, c -> {
            JsonNode facts = request(c, actor, id, action, null, null, resolution);
            JsonNode workflow = facts.path("workflow");
            var definition = Stage6WorkflowPersistence.definition(workflow.get("definition"));
            var instance = Stage6WorkflowPersistence.restore(workflow.get("instance"), definition);
            var result = instance.transition(definition, new TransitionCommand(instance.instanceId(), action,
                    new ActorReference(actor.subjectId()), expectedVersion), clock.instant());
            call(c, "wf_port_commit_transition", "TRANSITION_RESULT", actor.subjectId(), has(actor, WorkflowOperation.TRANSITION),
                    Stage6WorkflowPersistence.snapshot(result.previousInstance()), Stage6WorkflowPersistence.snapshot(result.instance()),
                    Stage6WorkflowPersistence.record(result.record()));
            return view(request(c, actor, id, "read", null, null, null), actor);
        });
    }

    private JsonNode request(Connection c, AuthenticatedPrincipal actor, UUID id, String command,
                             String assignee, Long assignmentVersion, String resolution) throws SQLException {
        return call(c, "helpdesk_request", "OK", actor.subjectId(), has(actor, WorkflowOperation.READ),
                has(actor, WorkflowOperation.TRANSITION), id, command, assignee, assignmentVersion, resolution);
    }

    private RequestView view(JsonNode facts, AuthenticatedPrincipal actor) {
        JsonNode request = facts.path("request");
        JsonNode snapshot = facts.path("workflow").path("instance");
        String state = snapshot.path("current_state").asText();
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
            if (state.equals("RESOLVED") && actor.subjectId().equals(request.path("requester_id").asText())
                    && nullable(request, "resolution") != null) actions.add("confirm");
        }
        return new RequestView(UUID.fromString(request.path("request_id").asText()), request.path("title").asText(),
                request.path("description").asText(), "IT_HELPDESK", request.path("requester_id").asText(),
                nullable(request, "assignee_id"), nullable(request, "resolution"), request.path("assignment_version").longValue(),
                snapshot.path("instance_id").asText(), state, snapshot.path("runtime_version").longValue(), List.copyOf(actions));
    }

    private static String nullable(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }
    private static boolean has(AuthenticatedPrincipal actor, WorkflowOperation operation) {
        return actor.authorities().stream().anyMatch(a -> a.satisfies(operation.requiredAuthority()));
    }

    private JsonNode call(Connection connection, String function, String success, Object... arguments) throws SQLException {
        String parameters = String.join(",", Collections.nCopies(arguments.length, "?"));
        try (var statement = connection.prepareStatement("SELECT outcome,body FROM " + schema + "." + function + "(" + parameters + ")")) {
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof JsonNode json) statement.setObject(i + 1, json.toString(), Types.OTHER);
                else statement.setObject(i + 1, arguments[i]);
            }
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw unavailable();
                String outcome = rows.getString(1);
                String encoded = rows.getString(2);
                if (rows.next()) throw unavailable();
                if (Set.of("HIDDEN", "FORBIDDEN", "UNAVAILABLE").contains(outcome)) {
                    throw new WorkflowAccessException(WorkflowAccessDecision.valueOf(outcome));
                }
                JsonNode body;
                try { body = JSON.readTree(encoded); }
                catch (java.io.IOException | IllegalArgumentException invalid) { throw unavailable(); }
                if ("DOMAIN_ERROR".equals(outcome)) {
                    throw new WorkflowException(WorkflowErrorCode.valueOf(body.path("code").asText()));
                }
                if (!success.equals(outcome) || body == null) throw unavailable();
                return body;
            }
        }
    }
    private static WorkflowAccessException unavailable() {
        return new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE);
    }
}
