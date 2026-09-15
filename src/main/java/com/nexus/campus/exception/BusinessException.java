package com.nexus.campus.exception;

import org.springframework.http.HttpStatus;

/**
 * An expected, explainable failure on a request path: not found, not allowed,
 * wrong state. The controller throws it and
 * {@link com.nexus.campus.config.GlobalExceptionHandler} is the only place that
 * turns it into a response, so the HTTP status and the envelope's {@code code}
 * cannot drift apart — they both come out of this one {@link HttpStatus}.
 *
 * <p>{@code safeMessage} is a promise rather than a convention: it reaches the
 * client verbatim. Anything that might contain a SQL fragment, an upstream
 * response body or a filesystem path belongs in the log, not here.</p>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String safeMessage) {
        super(safeMessage);
        if (status == null) {
            throw new IllegalArgumentException("BusinessException requires an HttpStatus");
        }
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
