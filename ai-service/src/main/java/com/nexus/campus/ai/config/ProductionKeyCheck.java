package com.nexus.campus.ai.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * D9 says the shared key is optional locally and required in production.
 * Failing at startup is the only safe shape: a production service that accepts
 * unauthenticated inference calls is reachable by anything on the compose
 * network, and a warning in the log would not stop that.
 */
@Component
public class ProductionKeyCheck implements InitializingBean {

    private final boolean production;
    private final String apiKey;

    public ProductionKeyCheck(@Value("${spring.profiles.active:}") String profiles,
                              @Value("${ai-service.service.api-key:}") String apiKey) {
        this.production = profiles != null
                && ("," + profiles.replace(" ", "") + ",").contains(",prod,");
        this.apiKey = apiKey;
    }

    @Override
    public void afterPropertiesSet() {
        if (production && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException(
                    "AI_SERVICE_KEY must be non-empty when SPRING_PROFILES_ACTIVE includes 'prod'");
        }
    }
}
