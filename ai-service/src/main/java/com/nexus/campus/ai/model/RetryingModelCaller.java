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

    /**
     * Runs the provider ladder inside a caller-supplied budget. The budget is
     * the application's deadline minus a safety margin: starting an attempt
     * that cannot finish before it would make the application time out and
     * re-dispatch the same review, which is exactly the duplication the
     * boundary is meant to prevent.
     */
    public ModelGateway.ModelReply complete(String system, String user, double temperature,
                                            long budgetMillis) {
        int maxAttempts = properties.getLlm().getMaxAttempts();
        long perAttemptMillis = properties.getLlm().getTimeout().toMillis();
        long deadline = System.nanoTime() + Math.max(0L, budgetMillis) * 1_000_000L;
        for (int attempt = 1; ; attempt++) {
            if (System.nanoTime() >= deadline) {
                throw new AiServiceException("model_timeout", false, 504,
                        "inference budget exhausted before attempt " + attempt);
            }
            try {
                return gateway.complete(system, user, temperature);
            } catch (AiServiceException e) {
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("Provider attempt {}/{} failed ({}), retrying",
                        attempt, maxAttempts, e.getCode());
                long backoff = properties.getLlm().getRetryBackoffMs() * attempt;
                long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= backoff + perAttemptMillis) {
                    throw new AiServiceException("model_timeout", false, 504,
                            "inference budget cannot fit another provider attempt");
                }
                sleep(backoff);
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
