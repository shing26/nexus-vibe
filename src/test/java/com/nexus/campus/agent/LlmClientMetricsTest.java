package com.nexus.campus.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the LLM metrics that the Grafana alert rules are written against.
 *
 * <p>The endpoint is port 1 on loopback, so every attempt fails with a connection refusal as fast
 * as the OS answers. That makes the retry ladder observable instead of flaky: a dead provider must
 * produce one {@code failure} sample per logical call, one duration sample per attempt, and once the
 * breaker opens, fail-fast calls that touch neither.</p>
 *
 * <p>The scrape assertion is the reason this uses a real {@link PrometheusMeterRegistry}: Micrometer
 * names carry dots, Prometheus names carry underscores, and counters gain {@code _total} on the
 * way out. Alert rules live on the far side of that translation, so a rename in code would break
 * them silently. A {@code SimpleMeterRegistry} cannot catch that.</p>
 */
class LlmClientMetricsTest {

    /** Nothing listens here; connection refusal is immediate and needs no timeout wait. */
    private static final String DEAD_ENDPOINT = "http://127.0.0.1:1/v1";

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private LlmClient client(int breakerFailureThreshold) {
        return new LlmClient(DEAD_ENDPOINT, "", "test-model", Duration.ofMillis(300),
                breakerFailureThreshold, 600L, "none", false, registry);
    }

    private double outcome(String outcome) {
        // find(), not get(): an outcome that never happened has no meter at all, and "no samples"
        // is exactly what the assertions below want to state.
        Counter meter = registry.find("llm.chat.completions").tag("outcome", outcome).counter();
        return meter == null ? 0.0 : meter.count();
    }

    private long attempts() {
        Timer meter = registry.find("llm.chat.completion.duration").timer();
        return meter == null ? 0L : meter.count();
    }

    private double breakerOpen() {
        return registry.get("llm.circuit.breaker.open").gauge().value();
    }

    @Test
    @DisplayName("A failed call counts once, while every attempt feeds the duration histogram")
    void failedCallCountsOncePerAttemptTimed() {
        // Threshold above the single call's failure count, so the breaker stays shut.
        LlmClient client = client(9);

        assertNull(client.chatCompletion("system", "user"));

        assertEquals(1.0, outcome("failure"), "one logical call");
        assertEquals(0.0, outcome("circuit_open"));
        assertThat(attempts()).isGreaterThanOrEqualTo(3);
        assertEquals(0.0, breakerOpen(), "breaker still closed");
    }

    @Test
    @DisplayName("An open breaker fails fast and is gauged, without touching the provider")
    void openBreakerFailsFastAndIsGauged() {
        LlmClient client = client(1);

        assertNull(client.chatCompletion("system", "user"));
        assertEquals(1.0, outcome("failure"));
        assertEquals(1.0, breakerOpen(), "threshold of 1 opens the breaker");
        long attemptsAfterOpen = attempts();

        assertNull(client.chatCompletion("system", "user"));
        assertEquals(1.0, outcome("circuit_open"));
        assertEquals(1.0, outcome("failure"), "the fail-fast call must not double-count a failure");
        assertEquals(attemptsAfterOpen, attempts(), "a fail-fast call performs no HTTP attempt");
    }

    @Test
    @DisplayName("The scrape renders the exact Prometheus names the alert rules query")
    void scrapeRendersAlertedMetricNames() {
        LlmClient client = client(1);
        assertNull(client.chatCompletion("system", "user"));

        String scrape = registry.scrape();
        assertThat(scrape)
                .contains("llm_chat_completions_total{outcome=\"failure\"")
                .contains("llm_chat_completion_duration_seconds_bucket{le=\"0.5\"")
                .contains("llm_chat_completion_duration_seconds_bucket{le=\"30.0\"")
                .contains("llm_circuit_breaker_open 1.0");
    }
}
