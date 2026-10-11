package com.tricore.dxos.gateway.workflow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateWorkflowInstanceRequest(
        @NotBlank @Pattern(regexp = INSTANCE_ID_PATTERN) String instanceId,
        @NotBlank String definitionId,
        @NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long definitionVersion,
        @NotBlank String resourceType,
        @NotBlank String resourceId) {
    public static final String INSTANCE_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]*";

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected workflow field");
    }
}
