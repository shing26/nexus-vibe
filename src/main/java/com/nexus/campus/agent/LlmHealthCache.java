package com.nexus.campus.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared cached LLM health probe.
 * <p>
 * Wraps {@link LlmClient#isHealthy()} with a 5-minute cache so callers on
 * request threads (e.g. the publish-time safety gate) never block on a real
 * probe more than once per window — the probe itself costs one connect
 * timeout against a dead endpoint. The reconcile task and the safety gate
 * share this single cache, so one probe result gates both paths.
 */
@Slf4j
@Component
public class LlmHealthCache {

    private static final long HEALTH_CACHE_MILLIS = 5 * 60 * 1000;

    private final LlmClient llmClient;

    private volatile long lastHealthCheckAt;
    private volatile boolean lastHealthy;

    public LlmHealthCache(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * @return the cached health verdict, refreshing it when the window has
     * expired. The very first call in a fresh JVM probes for real.
     */
    public boolean isHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt > HEALTH_CACHE_MILLIS) {
            lastHealthy = llmClient.isHealthy();
            lastHealthCheckAt = now;
        }
        return lastHealthy;
    }
}
