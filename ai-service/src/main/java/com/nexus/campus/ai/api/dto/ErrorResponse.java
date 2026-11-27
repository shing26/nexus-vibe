package com.nexus.campus.ai.api.dto;

public record ErrorResponse(String code, String message, boolean retryable) {
}
