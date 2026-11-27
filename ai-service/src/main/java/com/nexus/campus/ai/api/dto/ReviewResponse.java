package com.nexus.campus.ai.api.dto;

public record ReviewResponse(
        int score,
        String severity,
        String quality,
        String security,
        String suggestions,
        boolean approved,
        String resultJson,
        String model,
        Usage usage,
        long latencyMs,
        boolean repaired) {

    public record Usage(int promptTokens, int completionTokens) {
    }
}
