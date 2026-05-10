package com.msaas.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(error(ex.getStatus(), ex.getMessage(), ex.getStatus().name(), request, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        return ResponseEntity
                .badRequest()
                .body(error(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", request, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return ResponseEntity
                .badRequest()
                .body(error(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", request, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(error(HttpStatus.BAD_REQUEST, "Malformed request body", "MALFORMED_BODY", request, List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(error(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN", request, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected server error",
                        "INTERNAL_ERROR",
                        request,
                        List.of(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                ));
    }

    private ErrorResponse error(HttpStatus status, String message, String code, HttpServletRequest request, List<String> details) {
        return ErrorResponse.of(status.value(), message, code, request.getRequestURI(), requestId(request), details);
    }

    private String requestId(HttpServletRequest request) {
        String existing = request.getHeader("X-Request-Id");
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return UUID.randomUUID().toString();
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
