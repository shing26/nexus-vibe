package com.nexus.campus.ai.model;

/**
 * The service's only error shape. {@code retryable} is advisory for the caller's logs;
 * the caller does not retry the HTTP call (retry ownership is the service's).
 */
public class AiServiceException extends RuntimeException {

    private final String code;
    private final boolean retryable;
    private final int status;

    public AiServiceException(String code, boolean retryable, int status, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getStatus() {
        return status;
    }
}
