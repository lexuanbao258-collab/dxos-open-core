package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.application.authorization.WorkflowOperation;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.workflow.*;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Private-operation D3 candidate. Actor/authority are trusted-backend assertions (Option B). */
final class Stage6WorkflowPersistence implements WorkflowPersistencePort {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private static final String SNAPSHOT_KEYS = "instance_id definition_id definition_version resource_type resource_id "
            + "current_state runtime_version lifecycle created_at_seconds created_at_nanos updated_at_seconds updated_at_nanos";
    private static final String RECORD_KEYS = "instance_id transition_id source_state target_state actor_id "
            + "resulting_runtime_version occurred_at_seconds occurred_at_nanos";
    private final PostgresWorkflowPersistence owner;
    private final String schema;
    private final WorkflowOperation operation;
    private final String actor;
    private final boolean authority;
    private final String instanceId;
    private final ResourceReference resource;
    private final String definitionId;
    private final Long definitionVersion;
    private final String transitionId;
    private final Long expectedVersion;

    Stage6WorkflowPersistence(PostgresWorkflowPersistence owner, String schema, CurrentPrincipal principal,
            WorkflowOperation operation, String instanceId, ResourceReference resource, String definitionId,
            Long definitionVersion, String transitionId, Long expectedVersion) {
        var verified = Objects.requireNonNull(principal.requireVerified(), "verified principal");
        this.owner = owner;
        this.schema = schema;
        this.operation = Objects.requireNonNull(operation, "operation");
        this.actor = verified.subjectId();
        this.authority = verified.authorities().stream().anyMatch(a -> a.satisfies(operation.requiredAuthority()));
        text(instanceId);
        if (operation == WorkflowOperation.CREATE) {
            Objects.requireNonNull(resource, "resource");
            text(definitionId);
            if (definitionVersion == null || definitionVersion < 0) throw new IllegalArgumentException("definitionVersion");
        } else if (resource != null || definitionId != null || definitionVersion != null) {
            throw new IllegalArgumentException("Existing-instance scope has no caller-selected resource/definition");
        }
        if (operation == WorkflowOperation.TRANSITION) {
            text(transitionId);
            if (expectedVersion == null || expectedVersion < 0) throw new IllegalArgumentException("expectedVersion");
        } else if (transitionId != null || expectedVersion != null) {
            throw new IllegalArgumentException("Transition scope required");
        }
        this.instanceId = instanceId;
        this.resource = resource;
        this.definitionId = definitionId;
        this.definitionVersion = definitionVersion;
        this.transitionId = transitionId;
        this.expectedVersion = expectedVersion;
    }

    @Override public WorkflowDefinition loadDefinition(String id, long version) {
        allowed(WorkflowOperation.CREATE, WorkflowOperation.ACTIVATE, WorkflowOperation.TRANSITION);
        text(id);
        if (version < 0) throw new IllegalArgumentException("definitionVersion");
        if (operation == WorkflowOperation.CREATE) {
            if (!definitionId.equals(id) || definitionVersion != version) throw unavailable();
            definitionCall("CLASSIFY", id, version, "FACTS", facts -> {
                keys(facts, "actor_id instance_id resource_type resource_id tenant_id resource_active member_active visible requested_grant");
                if (!actor.equals(string(facts, "actor_id")) || !instanceId.equals(string(facts, "instance_id"))
                        || !resource.resourceType().equals(string(facts, "resource_type"))
                        || !resource.resourceId().equals(string(facts, "resource_id"))) throw unavailable();
                string(facts, "tenant_id");
                for (String key : List.of("resource_active", "member_active", "visible", "requested_grant")) {
                    if (!facts.path(key).isBoolean() || !facts.get(key).booleanValue()) throw unavailable();
                }
                return true;
            });
        }
        return definitionCall("MATERIALIZE", id, version, "DEFINITION", body -> {
            WorkflowDefinition result = definition(body);
            if (!id.equals(result.definitionId()) || version != result.definitionVersion()) throw unavailable();
            return result;
        });
    }

    private <T> T definitionCall(String phase, String id, long version, String outcome, Function<JsonNode,T> decode) {
        return call(false, "load_definition", outcome, decode, phase, actor, operation.name(), authority, instanceId,
                resource == null ? null : resource.resourceType(), resource == null ? null : resource.resourceId(), id, version);
    }

    @Override public WorkflowInstance loadInstance(String id) {
        allowed(WorkflowOperation.READ, WorkflowOperation.ACTIVATE, WorkflowOperation.TRANSITION);
        target(id);
        return call(false, "load_instance", "SNAPSHOT", body -> {
            // The authorized envelope carries the exact definition needed by Core.restore; never a synthetic definition.
            keys(body, "instance definition");
            WorkflowInstance result = restore(body.get("instance"), definition(body.get("definition")));
            target(result.instanceId());
            return result;
        }, actor, operation.name(), authority, instanceId);
    }

    @Override public List<TransitionRecord> loadHistory(String id) {
        allowed(WorkflowOperation.READ);
        target(id);
        return call(false, "load_history", "HISTORY", body -> {
            if (!body.isArray()) throw unavailable();
            List<TransitionRecord> result = new ArrayList<>();
            for (JsonNode value : body) {
                TransitionRecord record = record(value);
                target(record.instanceId());
                if (record.resultingRuntimeVersion() != result.size() + 1L) throw unavailable();
                result.add(record);
            }
            return List.copyOf(result);
        }, actor, authority, instanceId);
    }

    @Override public WorkflowInstance createInstance(WorkflowInstance initial) {
        allowed(WorkflowOperation.CREATE);
        Objects.requireNonNull(initial, "initial");
        target(initial.instanceId());
        if (!resource.equals(initial.resourceReference()) || !definitionId.equals(initial.definitionId())
                || definitionVersion != initial.definitionVersion()) throw unavailable();
        var canonical = WorkflowInstance.notStarted(instanceId, loadDefinition(definitionId, definitionVersion),
                resource, initial.createdAt());
        PostgresWorkflowPersistence.requireSnapshot(initial, canonical);
        return writeSnapshot("create_instance", canonical, snapshot(initial));
    }

    @Override public WorkflowInstance commitActivation(WorkflowInstance expected, WorkflowInstance activated) {
        allowed(WorkflowOperation.ACTIVATE);
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(activated, "activated");
        target(expected.instanceId());
        target(activated.instanceId());
        WorkflowInstance persisted = loadInstance(instanceId);
        if (persisted.runtimeVersion() != expected.runtimeVersion() || persisted.lifecycle() != expected.lifecycle())
            throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
        if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED)
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
        PostgresWorkflowPersistence.requireSnapshot(persisted, expected);
        if (persisted.lifecycle() != WorkflowLifecycle.NOT_STARTED || activated.updatedAt().isBefore(expected.updatedAt()))
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        WorkflowInstance canonical = persisted.activate(loadDefinition(persisted.definitionId(), persisted.definitionVersion()),
                activated.updatedAt());
        PostgresWorkflowPersistence.requireSnapshot(activated, canonical);
        return writeSnapshot("commit_activation", canonical, snapshot(expected), snapshot(activated));
    }

    @Override public TransitionResult commitTransition(TransitionResult proposal) {
        allowed(WorkflowOperation.TRANSITION);
        Objects.requireNonNull(proposal, "proposal");
        target(proposal.previousInstance().instanceId());
        TransitionRecord record = proposal.record();
        if (!actor.equals(record.actorReference().actorId()) || !transitionId.equals(record.transitionId())
                || expectedVersion != proposal.expectedVersion()) throw unavailable();
        WorkflowInstance persisted = loadInstance(instanceId);
        if (persisted.runtimeVersion() != proposal.expectedVersion())
            throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
        if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED)
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
        PostgresWorkflowPersistence.requireSnapshot(persisted, proposal.previousInstance());
        TransitionResult canonical = persisted.transition(loadDefinition(persisted.definitionId(), persisted.definitionVersion()),
                new TransitionCommand(instanceId, transitionId, new ActorReference(actor), expectedVersion), record.occurredAt());
        PostgresWorkflowPersistence.requireSnapshot(proposal.instance(), canonical.instance());
        if (!record.equals(canonical.record())) throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        return call(true, "commit_transition", "TRANSITION_RESULT", body -> {
            keys(body, "previous_instance instance record");
            if (!body.get("previous_instance").equals(snapshot(canonical.previousInstance()))
                    || !body.get("instance").equals(snapshot(canonical.instance()))
                    || !body.get("record").equals(record(canonical.record()))) throw unavailable();
            return canonical;
        }, actor, authority, snapshot(proposal.previousInstance()), snapshot(proposal.instance()), record(record));
    }

    private WorkflowInstance writeSnapshot(String function, WorkflowInstance canonical, JsonNode... snapshots) {
        Object[] arguments = new Object[snapshots.length + 2];
        arguments[0] = actor;
        arguments[1] = authority;
        System.arraycopy(snapshots, 0, arguments, 2, snapshots.length);
        return call(true, function, "SNAPSHOT", body -> {
            if (!body.equals(snapshot(canonical))) throw unavailable();
            return canonical;
        }, arguments);
    }

    private <T> T call(boolean write, String function, String success, Function<JsonNode,T> decode, Object... arguments) {
        return owner.guardedTransaction(write, connection -> {
            String placeholders = String.join(",", Collections.nCopies(arguments.length, "?"));
            try (var statement = connection.prepareStatement("SELECT outcome, body FROM " + schema + ".wf_port_"
                    + function + "(" + placeholders + ")")) {
                for (int i = 0; i < arguments.length; i++) {
                    Object value = arguments[i];
                    if (value instanceof JsonNode json) statement.setObject(i + 1, json.toString(), Types.OTHER);
                    else statement.setObject(i + 1, value);
                }
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw unavailable();
                    String outcome = rows.getString(1);
                    String encoded = rows.getString(2);
                    if (rows.next() || outcome == null) throw unavailable();
                    if (Set.of("HIDDEN", "FORBIDDEN", "UNAVAILABLE").contains(outcome)) {
                        if (encoded != null) throw unavailable();
                        throw new WorkflowAccessException(WorkflowAccessDecision.valueOf(outcome));
                    }
                    if (encoded == null) throw unavailable();
                    if (function.equals("load_definition") && encoded.getBytes(StandardCharsets.UTF_8).length > 65_536)
                        throw unavailable();
                    JsonNode body;
                    try { body = JSON.readTree(encoded); }
                    catch (java.io.IOException invalid) { throw unavailable(); }
                    if ("DOMAIN_ERROR".equals(outcome)) {
                        keys(body, "code");
                        WorkflowErrorCode code;
                        try { code = WorkflowErrorCode.valueOf(string(body, "code")); }
                        catch (IllegalArgumentException invalid) { throw unavailable(); }
                        boolean applicable = switch (function) {
                            case "load_definition" -> "MATERIALIZE".equals(arguments[0]) && code == WorkflowErrorCode.DEFINITION_NOT_FOUND;
                            case "load_instance" -> code == WorkflowErrorCode.DEFINITION_NOT_FOUND;
                            case "create_instance" -> Set.of(WorkflowErrorCode.INSTANCE_ALREADY_EXISTS,
                                    WorkflowErrorCode.DEFINITION_NOT_FOUND, WorkflowErrorCode.TRANSITION_NOT_ALLOWED).contains(code);
                            case "commit_activation" -> Set.of(WorkflowErrorCode.INSTANCE_NOT_FOUND, WorkflowErrorCode.DEFINITION_NOT_FOUND,
                                    WorkflowErrorCode.VERSION_CONFLICT, WorkflowErrorCode.INSTANCE_COMPLETED, WorkflowErrorCode.TRANSITION_NOT_ALLOWED).contains(code);
                            case "commit_transition" -> Set.of(WorkflowErrorCode.INSTANCE_NOT_FOUND, WorkflowErrorCode.DEFINITION_NOT_FOUND,
                                    WorkflowErrorCode.VERSION_CONFLICT, WorkflowErrorCode.INSTANCE_COMPLETED,
                                    WorkflowErrorCode.TRANSITION_NOT_ALLOWED, WorkflowErrorCode.INVALID_TRANSITION).contains(code);
                            default -> false;
                        };
                        if (!applicable) throw unavailable();
                        throw new WorkflowException(code);
                    }
                    if (!success.equals(outcome)) throw unavailable();
                    try { return decode.apply(body); }
                    catch (IllegalArgumentException | NullPointerException invalid) { throw unavailable(); }
                }
            }
        });
    }

    private void allowed(WorkflowOperation... permitted) {
        if (!Arrays.asList(permitted).contains(operation)) throw unavailable();
    }
    private void target(String id) { text(id); if (!instanceId.equals(id)) throw unavailable(); }
    private static void text(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("blank identifier"); }
    private static WorkflowAccessException unavailable() { return new WorkflowAccessException(WorkflowAccessDecision.UNAVAILABLE); }
    private static void keys(JsonNode node, String fields) {
        if (node == null || !node.isObject()) throw unavailable();
        Set<String> expected = Set.of(fields.split(" "));
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw unavailable();
    }
    private static String string(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw unavailable();
        return value.textValue();
    }
    private static long number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) throw unavailable();
        return value.longValue();
    }
    private static Instant instant(JsonNode node, String prefix) {
        long nanos = number(node, prefix + "_nanos");
        if (nanos < 0 || nanos > 999_999_999) throw unavailable();
        return Instant.ofEpochSecond(number(node, prefix + "_seconds"), nanos);
    }
    static WorkflowDefinition definition(JsonNode node) {
        keys(node, "definition_id definition_version initial_state states transitions");
        if (!node.get("states").isArray() || !node.get("transitions").isArray()) throw unavailable();
        Set<String> states = new HashSet<>();
        for (JsonNode value : node.get("states")) {
            if (!value.isTextual() || value.textValue().isBlank() || !states.add(value.textValue())) throw unavailable();
        }
        Set<WorkflowTransition> transitions = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode value : node.get("transitions")) {
            keys(value, "transition_id source_state target_state");
            if (!ids.add(string(value, "transition_id"))) throw unavailable();
            transitions.add(new WorkflowTransition(string(value, "transition_id"), string(value, "source_state"), string(value, "target_state")));
        }
        return new WorkflowDefinition(string(node, "definition_id"), number(node, "definition_version"),
                string(node, "initial_state"), states, transitions);
    }
    static ObjectNode snapshot(WorkflowInstance instance) {
        return JSON.createObjectNode().put("instance_id", instance.instanceId()).put("definition_id", instance.definitionId())
                .put("definition_version", instance.definitionVersion()).put("resource_type", instance.resourceReference().resourceType())
                .put("resource_id", instance.resourceReference().resourceId()).put("current_state", instance.currentState())
                .put("runtime_version", instance.runtimeVersion()).put("lifecycle", instance.lifecycle().name())
                .put("created_at_seconds", instance.createdAt().getEpochSecond()).put("created_at_nanos", (long) instance.createdAt().getNano())
                .put("updated_at_seconds", instance.updatedAt().getEpochSecond()).put("updated_at_nanos", (long) instance.updatedAt().getNano());
    }
    static WorkflowInstance restore(JsonNode node, WorkflowDefinition definition) {
        keys(node, SNAPSHOT_KEYS);
        if (!definition.definitionId().equals(string(node, "definition_id")) || definition.definitionVersion() != number(node, "definition_version"))
            throw unavailable();
        return WorkflowInstance.restore(string(node, "instance_id"), definition,
                new ResourceReference(string(node, "resource_type"), string(node, "resource_id")), string(node, "current_state"),
                number(node, "runtime_version"), WorkflowLifecycle.valueOf(string(node, "lifecycle")), instant(node, "created_at"), instant(node, "updated_at"));
    }
    static ObjectNode record(TransitionRecord record) {
        return JSON.createObjectNode().put("instance_id", record.instanceId()).put("transition_id", record.transitionId())
                .put("source_state", record.sourceState()).put("target_state", record.targetState()).put("actor_id", record.actorReference().actorId())
                .put("resulting_runtime_version", record.resultingRuntimeVersion()).put("occurred_at_seconds", record.occurredAt().getEpochSecond())
                .put("occurred_at_nanos", (long) record.occurredAt().getNano());
    }
    private static TransitionRecord record(JsonNode node) {
        keys(node, RECORD_KEYS);
        return new TransitionRecord(string(node, "instance_id"), string(node, "transition_id"), string(node, "source_state"), string(node, "target_state"),
                new ActorReference(string(node, "actor_id")), number(node, "resulting_runtime_version"), instant(node, "occurred_at"));
    }
}
