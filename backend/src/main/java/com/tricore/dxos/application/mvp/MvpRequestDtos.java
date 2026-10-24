package com.tricore.dxos.application.mvp;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.tricore.dxos.gateway.workflow.dto.WorkflowVersionDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Draft IT Helpdesk v0.1 HTTP wire types (Issue #53).
 * These DTOs are a contract candidate, NOT an authorization boundary.
 * All response fields/actions MUST be projected server-side after resource authorization.
 * Do not expose unfiltered workflow events, internal notes or client-provided actor IDs.
 */
public final class MvpRequestDtos {
    private MvpRequestDtos() {}

    public record Create(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String description,
            @NotBlank String requestType) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected IT request field");
        }
    }

    /** Workflow state changes require an exact current runtime version. */
    public record VersionedAction(
            @NotNull @PositiveOrZero
            @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected Workflow action field");
        }
    }

    public record Resolve(
            @NotNull @PositiveOrZero
            @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion,
            @NotBlank String resolution) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected resolve field");
        }
    }

    /** Assignment version is independent of Workflow runtime version. */
    public record Assign(
            @NotBlank String assigneeId,
            @NotNull @PositiveOrZero
            @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedAssignmentVersion) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected assignment field");
        }
    }

    public record Person(String id, String displayName) {}

    /** Minimal, resource-filtered list projection. Never use prototype findAll(). */
    public record Summary(UUID id, String title, String requestType, String status,
                          long version, Person assignee, Instant updatedAt) {}

    /**
     * authorized projection. eligibleAssignees is null for callers without assign permission,
     * [] for permitted callers when there are no eligible candidates.
     */
    public record Detail(
            UUID id, String title, String description, String requestType,
            String workflowInstanceId, String status, long version, long assignmentVersion,
            Person requester, Person assignee, String resolution,
            Set<String> allowedActions, List<Person> eligibleAssignees) {
        public Detail {
            allowedActions = Set.copyOf(java.util.Objects.requireNonNull(allowedActions, "allowedActions"));
            if (eligibleAssignees != null) {
                eligibleAssignees = List.copyOf(eligibleAssignees);
            }
        }
    }

    /** Only fields explicitly approved for this viewer may be present. */
    public record HistoryEvent(
            String id, Instant at, String action, String fromState, String toState,
            String actorLabel, String note) {}
}
