package com.nexus.campus.ai.api;

import com.nexus.campus.ai.api.dto.ErrorResponse;
import com.nexus.campus.ai.model.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ErrorResponse> aiService(AiServiceException error) {
        log.warn("AI service error {}: {}", error.getCode(), error.getMessage());
        return ResponseEntity.status(error.getStatus())
                .body(new ErrorResponse(error.getCode(), error.getMessage(), error.isRetryable()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("invalid_request", message, false));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error) {
        log.error("unexpected AI service failure", error);
        return ResponseEntity.status(500)
                .body(new ErrorResponse("internal_error", "unexpected failure", false));
    }
}
