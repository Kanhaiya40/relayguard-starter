package com.relayguard.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> statusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason();
        String code = switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> reason != null && reason.startsWith("Idempotency-Key")
                    ? "IDEMPOTENCY_CONFLICT" : "CONFLICT";
            case BAD_REQUEST -> "INVALID_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            default -> "REQUEST_FAILED";
        };
        String message = switch (status) {
            case NOT_FOUND -> "Requested resource was not found";
            case CONFLICT -> "Request conflicts with current state";
            case BAD_REQUEST -> "Request is invalid";
            case UNAUTHORIZED -> "Authentication failed";
            case FORBIDDEN -> "Access is forbidden";
            default -> "Request could not be completed";
        };
        return error(status, code, message);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, status.value()));
    }
}
