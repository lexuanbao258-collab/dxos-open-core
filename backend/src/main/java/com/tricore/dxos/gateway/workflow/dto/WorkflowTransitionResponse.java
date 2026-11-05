// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.gateway.workflow.dto;

import com.tricore.dxos.core.workflow.TransitionResult;

public record WorkflowTransitionResponse(WorkflowInstanceResponse instance, WorkflowHistoryResponse record) {
    public static WorkflowTransitionResponse from(TransitionResult result) {
        return new WorkflowTransitionResponse(
                WorkflowInstanceResponse.from(result.instance()), WorkflowHistoryResponse.from(result.record()));
    }
}
