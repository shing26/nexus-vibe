package com.nexus.campus.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SafetyRequest(
        @NotBlank String requestId,
        String traceId,
        @NotBlank String text,
        @NotBlank @Pattern(regexp = "post|comment") String surface) {
}
