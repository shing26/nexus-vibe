package com.nexus.campus.config;

import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * The single place that decides what HTTP status a failed request carries.
 * Every handler returns a {@link ResponseEntity} instead of annotating the
 * method, so the status on the line and the {@code code} in the body come from
 * the same expression. Controllers never set statuses: they throw
 * {@link BusinessException}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * An expected failure whose status the thrower chose. Its message was
     * written for this audience, so it reaches the client untouched.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getStatus();
        if (status.is4xxClientError()) {
            log.warn("Business rejection {}: {}", status.value(), e.getMessage());
        } else {
            log.error("Business failure {}: {}", status.value(), e.getMessage());
        }
        return failure(status, e.getMessage());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        log.debug("Static resource not found: {}", e.getResourcePath());
        return failure(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    /**
     * A supported path hit with the wrong verb (e.g. PUT /users/me) is a
     * client mistake, not a server error — it must not fall through to the
     * catch-all handler and surface as a 500.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.debug("Method not allowed: {}", e.getMessage());
        return failure(HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed. Supported: " + e.getSupportedHttpMethods());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return failure(HttpStatus.BAD_REQUEST, "Validation failed: " + message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return failure(HttpStatus.BAD_REQUEST, "Missing required parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return failure(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return failure(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.");
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestBinding(ServletRequestBindingException e) {
        return failure(HttpStatus.BAD_REQUEST, "Required request attribute is missing.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the size limit.");
    }

    /**
     * An {@link IllegalArgumentException} that is not a {@link BusinessException} is
     * an argument somebody rejected without saying how, so it stays a 400 — but the
     * message is no longer repeated back. It used to be, which meant anything a
     * library threw with an internal string in it reached the client: a JDBC
     * constraint name, an upstream response body, a filesystem path.
     *
     * <p>{@link IllegalStateException} is not handled here any more. Once every
     * known refusal had been given a status of its own, an ISE left on a request
     * path was by definition unmapped, and unmapped means the 500 side, where the
     * full stack is logged and the client is told nothing.</p>
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessError(RuntimeException e) {
        log.warn("Unmapped argument failure: {}", e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, "Request could not be processed. Check the submitted values.");
    }

    /**
     * Unknown runtime exceptions must not leak internal details (messages,
     * stack traces, SQL fragments) to the client. Log the full stack server-side.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e) {
        log.error("Unexpected runtime exception", e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Internal system error. Contact system administrator.");
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
