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
        private String baseUrl = "http://localhost:11434/v1";

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
