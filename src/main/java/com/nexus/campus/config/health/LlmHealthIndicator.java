package com.nexus.campus.config.health;

import com.nexus.campus.agent.LlmHealthCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the LLM as a dependency without pretending it is a requirement.
 *
 * <p>The review pipeline is self-healing: a post whose review fails parks in {@code pending-llm}
 * and the reconcile task retries it. So an unreachable model must never take the container out of
 * the load balancer - it goes to DEGRADED, which ADR-0007 maps to HTTP 200 and the alert rules
 * watch. Probing goes through {@link LlmHealthCache} so a scrape cannot stack up real requests
 * against a slow endpoint, and a dead endpoint costs one cached connect timeout per window.</p>
 */
@Component
public class LlmHealthIndicator implements HealthIndicator {

    private final LlmHealthCache llmHealthCache;
    private final boolean reviewEnabled;
    private final boolean safetyEnabled;

    public LlmHealthIndicator(LlmHealthCache llmHealthCache,
                              @Value("${campus.ai.review.enabled:true}") boolean reviewEnabled,
                              @Value("${campus.ai.safety.enabled:true}") boolean safetyEnabled) {
        this.llmHealthCache = llmHealthCache;
        this.reviewEnabled = reviewEnabled;
        this.safetyEnabled = safetyEnabled;
    }

    @Override
    public Health health() {
        if (!reviewEnabled && !safetyEnabled) {
            // No consumer means no dependency: reporting DEGRADED here would page someone over a
            // feature that is switched off on purpose.
            return Health.up().withDetail("mode", "disabled").build();
        }
        if (llmHealthCache.isHealthy()) {
            return Health.up().build();
        }
        return DependencyHealth.degraded("LLM probe failed or circuit breaker is open");
    }
}
