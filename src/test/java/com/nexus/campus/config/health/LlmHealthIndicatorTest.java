package com.nexus.campus.config.health;

import com.nexus.campus.agent.LlmHealthCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The LLM is the one dependency this app is expected to lose and keep serving. These tests pin the
 * vocabulary of that case: a dead model is DEGRADED, never DOWN, and never says which endpoint
 * was unreachable.
 */
class LlmHealthIndicatorTest {

    @Test
    @DisplayName("A failing probe degrades the component instead of taking the instance down")
    void probeFailureDegrades() {
        LlmHealthCache cache = mock(LlmHealthCache.class);
        when(cache.isHealthy()).thenReturn(false);

        Health health = new LlmHealthIndicator(cache, true, false).health();

        assertThat(health.getStatus()).isEqualTo(DependencyHealth.DEGRADED);
        assertThat(health.getDetails()).containsEntry("reason", "LLM probe failed or circuit breaker is open");
        assertThat(health.getDetails()).doesNotContainKey("endpoint");
    }

    @Test
    @DisplayName("A passing probe reports UP")
    void probeSuccessIsUp() {
        LlmHealthCache cache = mock(LlmHealthCache.class);
        when(cache.isHealthy()).thenReturn(true);

        assertThat(new LlmHealthIndicator(cache, true, false).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("With no AI feature enabled the LLM is not a dependency at all")
    void disabledPipelineNeverProbes() {
        LlmHealthCache cache = mock(LlmHealthCache.class);

        Health health = new LlmHealthIndicator(cache, false, false).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "disabled");
        verifyNoInteractions(cache);
    }
}
