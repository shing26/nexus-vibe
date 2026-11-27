package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The only retry ladder in the AI call path. The application makes one HTTP call and
 * leaves its own retry to the lease/reconciliation loop.
 *
 * <p>Every attempt goes through the circuit breaker and is archived when a path is
 * configured. A breaker-open failure never reaches the provider and is not retried.</p>
 */
@Slf4j
@Component
public class RetryingModelCaller {

    private final ModelGateway gateway;
    private final AiServiceProperties properties;
    private final ProviderCircuitBreaker breaker;
    private final CallArchive archive;
    private final Counter success;
    private final Counter error;

    public RetryingModelCaller(ModelGateway gateway,
                               AiServiceProperties properties,
                               ProviderCircuitBreaker breaker,
                               CallArchive archive,
                               MeterRegistry meters) {
        this.gateway = gateway;
        this.properties = properties;
        this.breaker = breaker;
        this.archive = archive;
        this.success = Counter.builder("ai.provider.calls").tag("outcome", "success").register(meters);
        this.error = Counter.builder("ai.provider.calls").tag("outcome", "error").register(meters);
    }

    /**
     * Runs the provider ladder inside a caller-supplied budget. The budget is
     * the application's deadline minus a safety margin: starting an attempt
     * that cannot finish before it would make the application time out and
     * re-dispatch the same review, which is exactly the duplication the
     * boundary is meant to prevent.
     */
    public ModelGateway.ModelReply complete(String system, String user, double temperature,
                                            OutputSchema schema, CallContext context, long budgetMillis) {
        int maxAttempts = properties.getLlm().getMaxAttempts();
        long perAttemptMillis = properties.getLlm().getTimeout().toMillis();
        long deadline = System.nanoTime() + Math.max(0L, budgetMillis) * 1_000_000L;
        for (int attempt = 1; ; attempt++) {
            long remainingMillis = remainingMillis(deadline);
            if (remainingMillis < perAttemptMillis) {
                throw new AiServiceException("model_timeout", false, 504,
                        "inference budget cannot fit attempt " + attempt);
            }
            breaker.beforeCall();
            long started = System.nanoTime();
            try {
                ModelGateway.ModelReply reply = gateway.complete(system, user, temperature, schema);
                breaker.onSuccess();
                success.increment();
                record(context, attempt, temperature, started, "success", null, reply,
                        system, user);
                return reply;
            } catch (AiServiceException e) {
                breaker.onFailure();
                error.increment();
                record(context, attempt, temperature, started, "error", e.getCode(), null,
                        system, user);
                if (!e.isRetryable() || attempt >= maxAttempts) {
                    throw e;
                }
                log.warn("Provider attempt {}/{} failed ({}), retrying",
                        attempt, maxAttempts, e.getCode());
                long backoff = properties.getLlm().getRetryBackoffMs() * attempt;
                if (remainingMillis(deadline) <= backoff + perAttemptMillis) {
                    throw new AiServiceException("model_timeout", false, 504,
                            "inference budget cannot fit another provider attempt");
                }
                sleep(backoff);
            } catch (RuntimeException e) {
                breaker.onFailure();
                error.increment();
                record(context, attempt, temperature, started, "error", "unexpected", null,
                        system, user);
                throw e;
            }
        }
    }

    private void record(CallContext context, int attempt, double temperature, long started,
                        String outcome, String errorCode, ModelGateway.ModelReply reply,
                        String system, String user) {
        try {
            archive.record(new CallArchive.Record(
                    Instant.now().toString(),
                    context.requestId(),
                    context.traceId(),
                    context.kind(),
                    attempt,
                    properties.getLlm().getModel(),
                    temperature,
                    (System.nanoTime() - started) / 1_000_000L,
                    outcome,
                    errorCode,
                    reply == null ? 0 : reply.promptTokens(),
                    reply == null ? 0 : reply.completionTokens(),
                    system,
                    user,
                    reply == null ? null : reply.content()));
        } catch (Exception e) {
            log.warn("AI archive write failed for {}: {}", context.requestId(), e.getMessage());
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

    private static long remainingMillis(long deadline) {
        return Math.max(0L, (deadline - System.nanoTime() + 999_999L) / 1_000_000L);
    }

    public record CallContext(String requestId, String traceId, String kind) {
    }
}
