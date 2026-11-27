package com.nexus.campus.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Every {@code ai-service.*} setting. The model endpoint and credential live here rather
 * than in {@code spring.ai.*} because this service builds its own {@code OpenAiApi}; the
 * application's direct path keeps using {@code campus.ai.llm.*}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ai-service")
public class AiServiceProperties {

    @Valid
    private final Llm llm = new Llm();

    @Valid
    private final Service service = new Service();

    @Getter
    @Setter
    public static class Llm {

        @NotBlank
        /**
         * Provider root, without a trailing {@code /v1}: Spring AI appends
         * {@code /v1/chat/completions} itself. The application's direct
         * {@code LlmClient} wants the {@code /v1} form, so the two processes
         * cannot share the same value.
         */
        private String baseUrl = "http://localhost:11434";

        private String apiKey = "ollama";

        @NotBlank
        private String model = "qwen2.5:3b";

        private Duration timeout = Duration.ofSeconds(30);

        @Positive
        private int maxAttempts = 3;

        @Positive
        private long retryBackoffMs = 500;

        @Positive
        private long healthCacheSeconds = 60;

        @AssertTrue(message = "ai-service.llm.timeout must be greater than zero")
        public boolean isTimeoutPositive() {
            return timeout != null && !timeout.isZero() && !timeout.isNegative();
        }

        /**
         * Worst-case wall time for one logical call: every attempt consumes
         * the per-attempt timeout, plus the linear backoff between attempts,
         * plus a small margin so scheduling/class-loading time cannot make the
         * first attempt look too expensive to start. The application's read
         * timeout must exceed this or the least retryable provider outage
         * turns into a duplicate review.
         */
        public long maxBudgetMillis() {
            long perAttempt = timeout.toMillis();
            long backoffs = retryBackoffMs * (long) maxAttempts * (maxAttempts - 1) / 2;
            return perAttempt * maxAttempts + backoffs + 1_000L;
        }
    }

    @Getter
    @Setter
    public static class Service {

        /** Empty means "no shared key", which is the local default. */
        private String apiKey = "";

        @Positive
        private int maxContextTokens = 12000;
    }
}
