package com.nexus.campus.config;

import com.nexus.campus.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.debug("Static resource not found: {}", e.getResourcePath());
        return ApiResponse.error(404, "Resource not found.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.error(400, "Validation failed: " + message);
    }

    /**
     * Expected business errors (validation, permission, state) carry a safe,
     * user-facing message and map to HTTP 400.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessError(RuntimeException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * Unknown runtime exceptions must not leak internal details (messages,
     * stack traces, SQL fragments) to the client. Log the full stack server-side.
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleRuntime(RuntimeException e) {
        log.error("Unexpected runtime exception", e);
        return ApiResponse.error(500, "Internal server error.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return ApiResponse.error(500, "Internal system error. Contact system administrator.");
    }
}
