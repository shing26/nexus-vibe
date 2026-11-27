package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The only retry ladder in the AI call path. The application makes one HTTP call and
 * leaves its own retry to the lease/reconciliation loop.
 */
@Slf4j
@Component
public class RetryingModelCaller {

    private final ModelGateway gateway;
    private final AiServiceProperties properties;

    public RetryingModelCaller(ModelGateway gateway, AiServiceProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public ModelGateway.ModelReply complete(String system, String user, double temperature) {
        int maxAttempts = properties.getLlm().getMaxAttempts();
        for (int attempt = 1; ; attempt++) {
            try {
                return gateway.complete(system, user, temperature);
            } catch (AiServiceException e) {
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("Provider attempt {}/{} failed ({}), retrying",
                        attempt, maxAttempts, e.getCode());
                sleep(properties.getLlm().getRetryBackoffMs() * attempt);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("model_timeout", false, 504,
                    "interrupted while backing off");
        }
    }
}
