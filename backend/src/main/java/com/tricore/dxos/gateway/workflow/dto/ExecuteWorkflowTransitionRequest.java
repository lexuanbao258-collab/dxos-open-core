package com.tricore.dxos.gateway.workflow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ExecuteWorkflowTransitionRequest(
        @NotBlank String transitionId,
        @NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected workflow field");
    }
}
