package com.nexus.campus.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Every {@code campus.ai.*} setting, bound and validated in one place.
 *
 * <p>The AI settings used to be read with {@code @Value} at eight call sites
 * and checked by a bespoke {@code BeanFactoryPostProcessor}. That worked, but
 * the defaults lived in the annotations, the rules lived in the validator, and
 * the property names lived in both. Here the field, its default and its
 * constraint sit on the same line, and a bad value fails binding before any AI
 * bean is constructed.</p>
 *
 * <p>Validation runs through {@link Validated}: an illegal value fails binding,
 * naming the property an operator has to edit, before any AI bean is
 * constructed. Boot 3.3 validates each nested group as it binds, so a single
 * failure lists every violation in the offending group and then stops - see
 * {@code CampusAiPropertiesTest} for the pinned limit. See ADR-0013.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "campus.ai")
public class CampusAiProperties {

    @Valid
    private final Review review = new Review();

    @Valid
    private final Safety safety = new Safety();

    @Valid
    private final Archive archive = new Archive();

    @Valid
    private final Reconcile reconcile = new Reconcile();

    @Valid
    private final Llm llm = new Llm();

    @Getter
    @Setter
    public static class Review {

        private boolean enabled = true;

        /**
         * How long a claimed review stays locked before reconciliation may
         * re-dispatch it. Must exceed the LLM worst case or the lease expires
         * mid-review and a duplicate is dispatched.
         */
        @Positive
        private long leaseSeconds = 30;

        @Positive
        private int maxAttempts = 5;

        /** Overrides the lease owner id; blank means "hostname:pid". */
        private String ownerId = "";

        @Positive
        private int maxContextTokens = 12000;
    }

    @Getter
    @Setter
    public static class Safety {

        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Archive {

        /** Off by default: the file carries raw prompts and responses. */
        private boolean enabled = false;

        private String path = "scratch/llm-archive.jsonl";
    }

    @Getter
    @Setter
    public static class Reconcile {

        @Positive
        private long staleMinutes = 10;
    }

    @Getter
    @Setter
    public static class Llm {

        @NotBlank
        @Pattern(
                regexp = "^https?://[^/\\s]+.*$",
                message = "campus.ai.llm.endpoint must be an absolute http(s) URL")
        private String endpoint;

        private String apiKey = "";

        @NotBlank(message = "campus.ai.llm.model must be set")
        private String model;

        /**
         * Spring Boot 3.3 has no {@code @DurationMin} (it arrived in 3.4), so
         * the duration rule is an {@link AssertTrue} whose message names the
         * property an operator has to edit.
         */
        private Duration timeout = Duration.ofSeconds(30);

        @Valid
        private final Breaker breaker = new Breaker();

        /** json_schema (native strict) | json_object (DeepSeek et al.) | none */
        private String responseFormat = "json_schema";

        private boolean thinkingDisabled = true;

        @AssertTrue(message = "campus.ai.llm.timeout must be greater than zero")
        public boolean isTimeoutPositive() {
            return timeout != null && !timeout.isZero() && !timeout.isNegative();
        }

        @Getter
        @Setter
        public static class Breaker {

            @Positive
            private int failureThreshold = 3;

            @Positive
            private long openSeconds = 60;
        }
    }
}
