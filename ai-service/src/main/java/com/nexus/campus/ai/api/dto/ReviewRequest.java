package com.nexus.campus.ai.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record ReviewRequest(
        @NotBlank String requestId,
        String traceId,
        @NotNull Long postId,
        @NotBlank @Pattern(regexp = "code|prompt") String kind,
        String title,
        String content,
        List<String> snippets,
        @Valid Budget budget) {

    public record Budget(
            @Positive int maxContextTokens,
            @Positive int timeoutMs) {
    }
}
