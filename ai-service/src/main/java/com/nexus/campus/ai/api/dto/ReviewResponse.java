package com.nexus.campus.ai.api.dto;

import java.util.List;

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
        boolean repaired,
        List<Evidence> evidence,
        int evidenceRejected) {

    public record Usage(int promptTokens, int completionTokens) {
    }

    public record Evidence(String quote, int startLine, int endLine) {
    }
}
