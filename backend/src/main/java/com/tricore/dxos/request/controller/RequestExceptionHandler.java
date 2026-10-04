package com.tricore.dxos.request.controller;

import com.tricore.dxos.common.error.ApiError;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.InvalidRequestTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(basePackageClasses = RequestController.class)
public class RequestExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(400, "VALIDATION_FAILED",
                "Request input is invalid", errors));
    }

    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<ApiError> notFound(RequestNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, "REQUEST_NOT_FOUND", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody() {
        return ResponseEntity.badRequest().body(new ApiError(400, "INVALID_BODY",
                "Body must be valid JSON containing only fields accepted by this endpoint", Map.of()));
    }

    @ExceptionHandler(InvalidRequestTransitionException.class)
    public ResponseEntity<ApiError> invalidTransition(InvalidRequestTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409,
                "INVALID_REQUEST_TRANSITION", exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidIdentifier() {
        return ResponseEntity.badRequest().body(new ApiError(400, "INVALID_ID",
                "Request id must be a UUID", Map.of()));
    }
}
