package com.nexus.campus.ai.health;

import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.ModelGateway;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Cached provider probe. `/readyz` must not call the model on every scrape, and the
 * application's own five-minute cache sits in front of this one.
 */
@Component
public class ProviderHealthProbe {

    private final ModelGateway gateway;
    private final long cacheMillis;

    private volatile long checkedAt;
    private volatile boolean healthy;

    public ProviderHealthProbe(ModelGateway gateway, AiServiceProperties properties) {
        this.gateway = gateway;
        this.cacheMillis = TimeUnit.SECONDS.toMillis(properties.getLlm().getHealthCacheSeconds());
    }

    public boolean isHealthy() {
        long now = System.currentTimeMillis();
        // A zero window means "probe every call". Using > alone can skip the
        // second call when both land in the same millisecond, which made the
        // recovery test timing-dependent.
        if (cacheMillis <= 0 || now - checkedAt > cacheMillis) {
            healthy = gateway.isHealthy();
            checkedAt = now;
        }
        return healthy;
    }
}
