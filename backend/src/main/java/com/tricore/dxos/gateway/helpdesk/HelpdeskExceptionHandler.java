// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

package com.tricore.dxos.gateway.helpdesk;

import com.tricore.dxos.common.error.ApiError;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.gateway.workflow.controller.WorkflowExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Helpdesk-only anti-enumeration policy. The Generic Workflow API retains
 * its existing distinct 404 error codes and messages.
 */
@RestControllerAdvice(assignableTypes = HelpdeskController.class)
public class HelpdeskExceptionHandler extends WorkflowExceptionHandler {
    @Override
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ApiError> workflow(WorkflowException exception) {
        WorkflowErrorCode code = exception.code();
        if (code == WorkflowErrorCode.DEFINITION_NOT_FOUND || code == WorkflowErrorCode.INSTANCE_NOT_FOUND) {
            return ResponseEntity.status(404)
                    .body(new ApiError(404, "NOT_FOUND", "Workflow resource not found", Map.of()));
        }
        return super.workflow(exception);
    }
}
