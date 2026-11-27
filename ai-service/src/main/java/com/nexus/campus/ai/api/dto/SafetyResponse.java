package com.nexus.campus.ai.api.dto;

import java.util.List;

public record SafetyResponse(
        String classification,
        double confidence,
        List<String> reasons,
        String resultJson,
        String model,
        long latencyMs) {
}
