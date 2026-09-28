package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI pipeline's numeric settings decide whether a bad deploy degrades
 * (lease-seconds too small, max-attempts at zero) or bills/serves anyway
 * (timeout at zero). Binding them through {@link CampusAiProperties} means an
 * illegal value is refused while the context is still starting, by name.
 *
 * <p>This replaces the {@code AiConfigValidatorTest} an earlier round on this
 * branch drafted; that validator never reached a commit, so neither the class
 * nor its test can be checked out (see ADR-0013). The assertions cover the same
 * illegal values, but they now exercise Spring's own binding and validation
 * instead of a hand-written {@code BeanFactoryPostProcessor}.</p>
 *
 * <p><strong>Known limit, pinned below.</strong> Spring Boot 3.3 validates each
 * {@code @Valid} nested group as it finishes binding, so the failure reports
 * every violation <em>within one group</em> and then stops. The old
 * {@code BeanFactoryPostProcessor} collected violations across the whole tree
 * before throwing. That is the one guarantee this migration gives up, and
 * {@link #violationsAcrossGroupsFailOneGroupAtATime()} exists so it cannot be
 * forgotten.</p>
 */
class CampusAiPropertiesTest {

    @EnableConfigurationProperties(CampusAiProperties.class)
    static class BindingConfig {
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(BindingConfig.class)
                .withPropertyValues(
                        "campus.ai.llm.endpoint=http://localhost:11434/v1",
                        "campus.ai.llm.model=qwen2.5:7b");
    }

    private void assertStartupNames(String property, String value) {
        context().withPropertyValues(property + "=" + value).run(ctx -> {
            assertThat(ctx).as("context must fail to start").hasFailed();
            assertThat(ctx.getStartupFailure())
                    .as("startup failure must name " + property)
                    .hasStackTraceContaining(property);
        });
    }

    @Test
    @DisplayName("A non-positive LLM timeout fails binding and names campus.ai.llm.timeout")
    void zeroTimeoutFailsStartup() {
        assertStartupNames("campus.ai.llm.timeout", "0s");
    }

    @Test
    @DisplayName("A non-positive review lease fails binding by name")
    void zeroLeaseSecondsFailsStartup() {
        assertStartupNames("campus.ai.review.lease-seconds", "0");
    }

    @Test
    @DisplayName("max-attempts below one fails binding by name")
    void zeroMaxAttemptsFailsStartup() {
        assertStartupNames("campus.ai.review.max-attempts", "0");
    }

    @Test
    @DisplayName("A non-positive context budget fails binding by name")
    void zeroMaxContextTokensFailsStartup() {
        assertStartupNames("campus.ai.review.max-context-tokens", "0");
    }

    @Test
    @DisplayName("A non-positive reconcile window fails binding by name")
    void zeroStaleMinutesFailsStartup() {
        assertStartupNames("campus.ai.reconcile.stale-minutes", "0");
    }

    @Test
    @DisplayName("A non-positive breaker failure threshold fails binding by name")
    void zeroBreakerFailureThresholdFailsStartup() {
        assertStartupNames("campus.ai.llm.breaker.failure-threshold", "0");
    }

    @Test
    @DisplayName("A non-positive breaker open window fails binding by name")
    void zeroBreakerOpenSecondsFailsStartup() {
        assertStartupNames("campus.ai.llm.breaker.open-seconds", "0");
    }

    @Test
    @DisplayName("A blank model fails binding by name")
    void blankModelFailsStartup() {
        context().withPropertyValues("campus.ai.llm.model= ").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("model");
        });
    }

    @Test
    @DisplayName("A malformed endpoint fails binding by name")
    void malformedEndpointFailsStartup() {
        assertStartupNames("campus.ai.llm.endpoint", "not-a-url");
    }

    @Test
    @DisplayName("A non-numeric value is rejected as the property it belongs to")
    void nonNumericValueFailsStartup() {
        assertStartupNames("campus.ai.llm.breaker.open-seconds", "soon");
    }

    @Test
    @DisplayName("One bad group reports every offending property in it, not just the first")
    void everyViolationInOneGroupIsReportedAtOnce() {
        context()
                .withPropertyValues(
                        "campus.ai.llm.breaker.failure-threshold=0",
                        "campus.ai.llm.breaker.open-seconds=0")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    Throwable failure = ctx.getStartupFailure();
                    assertThat(failure)
                            .as("one binding failure must name every offending field in the group")
                            .hasStackTraceContaining("failureThreshold")
                            .hasStackTraceContaining("openSeconds");
                });
    }

    @Test
    @DisplayName("Violations in two different groups fail one group at a time - the known limit")
    void violationsAcrossGroupsFailOneGroupAtATime() {
        context()
                .withPropertyValues(
                        "campus.ai.review.max-attempts=0",
                        "campus.ai.llm.breaker.open-seconds=0")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    // Both values are refused, but by a per-group validation pass:
                    // whichever group binds first throws and the other is never
                    // reported in the same message. Asserting the shape rather
                    // than the group keeps the test from pinning binder ordering.
                    assertThat(ctx.getStartupFailure())
                            .as("still a validation refusal, not a crash")
                            .hasStackTraceContaining("BindValidationException")
                            .hasStackTraceContaining("Binding validation errors on campus.ai.");
                });
    }

    @Test
    @DisplayName("A valid AI configuration binds cleanly")
    void validConfigurationBinds() {
        context().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(CampusAiProperties.class).getLlm().getModel()).isEqualTo("qwen2.5:7b");
        });
    }

    @Test
    @DisplayName("A Compose service host with an underscore is a valid endpoint")
    void underscoreHostIsAccepted() {
        context().withPropertyValues("campus.ai.llm.endpoint=http://llm_service:8000/v1")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("An unset campus.ai block still yields the defaults the @Value annotations used to carry")
    void defaultsMatchTheFormerValueAnnotations() {
        CampusAiProperties properties = new CampusAiProperties();

        assertThat(properties.getReview().isEnabled()).isTrue();
        assertThat(properties.getReview().getLeaseSeconds()).isEqualTo(30);
        assertThat(properties.getReview().getMaxAttempts()).isEqualTo(5);
        assertThat(properties.getReview().getMaxContextTokens()).isEqualTo(12000);
        assertThat(properties.getReview().getOwnerId()).isEmpty();
        assertThat(properties.getSafety().isEnabled()).isTrue();
        assertThat(properties.getReconcile().getStaleMinutes()).isEqualTo(10);
        assertThat(properties.getArchive().isEnabled()).isFalse();
        assertThat(properties.getArchive().getPath()).isEqualTo("scratch/llm-archive.jsonl");
        assertThat(properties.getLlm().getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getLlm().getBreaker().getFailureThreshold()).isEqualTo(3);
        assertThat(properties.getLlm().getBreaker().getOpenSeconds()).isEqualTo(60);
        assertThat(properties.getLlm().getResponseFormat()).isEqualTo("json_schema");
        assertThat(properties.getLlm().isThinkingDisabled()).isTrue();
    }
}
