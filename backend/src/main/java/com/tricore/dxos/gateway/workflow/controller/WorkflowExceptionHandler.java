package com.tricore.dxos.gateway.workflow.controller;

import com.tricore.dxos.common.error.ApiError;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = WorkflowController.class)
public class WorkflowExceptionHandler {
    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ApiError> workflow(WorkflowException exception) {
        WorkflowErrorCode code = exception.code();
        int status = switch (code) {
            case INSTANCE_ALREADY_EXISTS, VERSION_CONFLICT, TRANSITION_NOT_ALLOWED, INSTANCE_COMPLETED -> 409;
            case DEFINITION_NOT_FOUND, INSTANCE_NOT_FOUND -> 404;
            case INVALID_TRANSITION -> 422;
            case OPERATION_FAILURE, COMMIT_OUTCOME_UNKNOWN -> 500;
        };
        String message = switch (code) {
            case INSTANCE_ALREADY_EXISTS -> "Workflow instance already exists";
            case VERSION_CONFLICT -> "Workflow version conflict";
            case DEFINITION_NOT_FOUND -> "Workflow definition version not found";
            case INSTANCE_NOT_FOUND -> "Workflow instance not found";
            case INVALID_TRANSITION -> "Unknown workflow transition";
            case TRANSITION_NOT_ALLOWED -> "Workflow operation is not allowed in the current state";
            case INSTANCE_COMPLETED -> "Workflow instance is completed";
            case OPERATION_FAILURE -> "Workflow operation failed";
            case COMMIT_OUTCOME_UNKNOWN ->
                    "Commit outcome is unknown; the write may have committed. Reconcile authoritative instance/history; do not automatically retry";
        };
        return error(status, code.name(), message, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(400, "VALIDATION_FAILED", "Workflow input is invalid", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception) {
        return exception.isForReturnValue() ? internalFailure()
                : error(400, "VALIDATION_FAILED", "Workflow input is invalid", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidBody() {
        return error(400, "INVALID_BODY", "JSON fields are not accepted by this endpoint", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType() {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "Workflow input requires application/json", Map.of());
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> missingTrustedActor() {
        return error(401, "AUTHENTICATION_REQUIRED", "A verified workflow actor is required", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalFailure() {
        return error(500, "INTERNAL_ERROR", "Workflow operation failed", Map.of());
    }

    private ResponseEntity<ApiError> error(int status, String code, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(new ApiError(status, code, message, errors));
    }
}
