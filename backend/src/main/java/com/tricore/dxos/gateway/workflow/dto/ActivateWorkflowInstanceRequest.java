// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.gateway.workflow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Activation accepts an absent body or an empty JSON object, never client-controlled state. */
public record ActivateWorkflowInstanceRequest() {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected workflow field");
    }
}
