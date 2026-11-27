package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fails fast while the provider is known to be down, instead of queueing attempts that
 * will consume the caller's whole budget. One half-open probe decides whether to close.
 */
@Component
public class ProviderCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final long probeLeaseMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();
    private final AtomicLong probeDeadline = new AtomicLong();
    private final AtomicBoolean probeInFlight = new AtomicBoolean();

    public ProviderCircuitBreaker(AiServiceProperties properties, MeterRegistry meters) {
        this.failureThreshold = properties.getLlm().getBreakerFailures();
        this.openMillis = properties.getLlm().getBreakerOpenSeconds() * 1000L;
        this.probeLeaseMillis = properties.getLlm().getBreakerProbeSeconds() * 1000L;
        Gauge.builder("ai.provider.circuit.open", this, breaker -> breaker.isOpen() ? 1 : 0)
                .description("1 while the provider circuit is open")
                .register(meters);
    }

    public void beforeCall() {
        long until = openUntil.get();
        if (until == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < until) {
            throw openError();
        }
        long lease = probeDeadline.get();
        if (lease != 0 && now >= lease) {
            // The probe never reported back (an Error, a killed thread, a hung caller).
            // Reclaim it so the breaker cannot stay half-open forever.
            probeInFlight.set(false);
            probeDeadline.set(0);
        }
        if (!probeInFlight.compareAndSet(false, true)) {
            throw openError();
        }
        probeDeadline.set(now + probeLeaseMillis);
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        openUntil.set(0);
        probeInFlight.set(false);
        probeDeadline.set(0);
    }

    public void onFailure() {
        probeInFlight.set(false);
        probeDeadline.set(0);
        int failures = consecutiveFailures.incrementAndGet();
        if (openUntil.get() > 0 || failures >= failureThreshold) {
            openUntil.set(System.currentTimeMillis() + openMillis);
        }
    }

    public boolean isOpen() {
        long until = openUntil.get();
        return (until > 0 && System.currentTimeMillis() < until) || probeInFlight.get();
    }

    int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    private static AiServiceException openError() {
        return new AiServiceException("model_unavailable", false, 503,
                "provider circuit is open");
    }
}
