package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Fails fast while the provider is known to be down, instead of queueing attempts that
 * will consume the caller's whole budget. One half-open probe decides whether to close.
 *
 * <p>Every state transition runs under one monitor. The alternative — CAS on an
 * in-flight flag plus a separate expiry — has two failure modes that were reproduced in
 * review: two callers can both reclaim an expired probe, and an abandoned probe can
 * report success or failure after a newer probe has started, overwriting its state. The
 * caller receives a permit; only the current permit may report. A probe that never
 * reports is replaced after its lease, so an escaped {@link Error} cannot wedge the
 * breaker.</p>
 */
@Component
public class ProviderCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final long probeLeaseMillis;

    private int consecutiveFailures;
    private long openUntil;
    /** Monotonic, never reset: a permit value must not be reused after a close. */
    private long nextPermit;
    /** The permit allowed to report, or {@code 0} when no probe is in flight. */
    private long currentPermit;
    private long probeDeadline;

    public ProviderCircuitBreaker(AiServiceProperties properties, MeterRegistry meters) {
        this.failureThreshold = properties.getLlm().getBreakerFailures();
        this.openMillis = properties.getLlm().getBreakerOpenSeconds() * 1000L;
        this.probeLeaseMillis = properties.getLlm().getBreakerProbeSeconds() * 1000L;
        Gauge.builder("ai.provider.circuit.open", this, breaker -> breaker.isOpen() ? 1 : 0)
                .description("1 while the provider circuit is open")
                .register(meters);
    }

    /**
     * @return {@code 0} when the circuit is closed and the call is ordinary, or a
     *         positive permit identifying the single half-open probe in flight
     */
    public synchronized long beforeCall() {
        long now = System.currentTimeMillis();
        if (openUntil == 0) {
            return 0;
        }
        if (now < openUntil) {
            throw openError();
        }
        if (currentPermit != 0 && now < probeDeadline) {
            throw openError();
        }
        currentPermit = ++nextPermit;
        probeDeadline = now + probeLeaseMillis;
        return currentPermit;
    }

    public synchronized void onSuccess(long permit) {
        if (!owns(permit)) {
            return;
        }
        consecutiveFailures = 0;
        openUntil = 0;
        currentPermit = 0;
        probeDeadline = 0;
    }

    public synchronized void onFailure(long permit) {
        if (!owns(permit)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (permit != 0) {
            currentPermit = 0;
            probeDeadline = 0;
            consecutiveFailures++;
            openUntil = now + openMillis;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntil = now + openMillis;
        }
    }

    public synchronized boolean isOpen() {
        return (openUntil > 0 && System.currentTimeMillis() < openUntil) || currentPermit != 0;
    }

    synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * A closed-state permit (0) stops being current the moment the circuit opens: the
     * success or failure belongs to an attempt that began before the decision.
     */
    private boolean owns(long permit) {
        return permit != 0
                ? permit == currentPermit
                : currentPermit == 0 && openUntil == 0;
    }

    private static AiServiceException openError() {
        return new AiServiceException("model_unavailable", false, 503,
                "provider circuit is open");
    }
}
