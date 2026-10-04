package com.tricore.dxos.request.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignRequestDto(@NotBlank @Size(max = 100) String assigneeId) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected assignment field");
    }
}
