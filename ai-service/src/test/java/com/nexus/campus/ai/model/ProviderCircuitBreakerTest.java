package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCircuitBreakerTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private ProviderCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        properties.getLlm().setBreakerFailures(2);
        properties.getLlm().setBreakerOpenSeconds(1);
        properties.getLlm().setBreakerProbeSeconds(1);
        breaker = new ProviderCircuitBreaker(properties, new SimpleMeterRegistry());
    }

    @Test
    void opensAfterTheThresholdAndFailsFast() {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());

        assertTrue(breaker.isOpen());
        AiServiceException error = assertThrows(AiServiceException.class, breaker::beforeCall);
        assertEquals("model_unavailable", error.getCode());
        assertEquals(503, error.getStatus());
    }

    @Test
    void successResetsTheFailureCount() {
        breaker.onFailure(breaker.beforeCall());
        breaker.onSuccess(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());

        assertFalse(breaker.isOpen());
        assertEquals(0L, breaker.beforeCall());
    }

    @Test
    void oneProbeIsAllowedAfterTheWindowAndSuccessClosesIt() throws InterruptedException {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);

        long probe = breaker.beforeCall();
        assertNotEquals(0L, probe);
        assertThrows(AiServiceException.class, breaker::beforeCall);

        breaker.onSuccess(probe);
        assertFalse(breaker.isOpen());
        assertEquals(0L, breaker.beforeCall());
    }

    @Test
    void aFailedProbeReopensTheWindow() throws InterruptedException {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);

        long probe = breaker.beforeCall();
        breaker.onFailure(probe);

        assertTrue(breaker.isOpen());
        assertThrows(AiServiceException.class, breaker::beforeCall);
    }

    @Test
    void aProbeThatNeverReportsIsReclaimedWithANewPermit() throws InterruptedException {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);

        long first = breaker.beforeCall();
        assertTrue(breaker.isOpen(), "a half-open probe still reports the circuit as not closed");

        Thread.sleep(1_100L);
        long second = breaker.beforeCall();

        assertNotEquals(first, second);
    }

    @Test
    void onlyOneProbeIsGrantedWhenTheWindowExpires() throws Exception {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);

        int callers = 16;
        CyclicBarrier barrier = new CyclicBarrier(callers);
        AtomicInteger granted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(2, TimeUnit.SECONDS);
                        breaker.beforeCall();
                        granted.incrementAndGet();
                    } catch (AiServiceException e) {
                        // expected for every caller that is not the single probe
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, granted.get());
    }

    @Test
    void aStaleProbeCannotCloseTheNewProbe() throws InterruptedException {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);

        long abandoned = breaker.beforeCall();
        Thread.sleep(1_100L);
        long current = breaker.beforeCall();

        breaker.onSuccess(abandoned);
        assertTrue(breaker.isOpen(), "the abandoned probe must not close the circuit");
        assertThrows(AiServiceException.class, breaker::beforeCall);

        breaker.onSuccess(current);
        assertFalse(breaker.isOpen());
    }

    @Test
    void permitValuesAreNotReusedAfterACloseAndReopen() throws InterruptedException {
        breaker.onFailure(breaker.beforeCall());
        breaker.onFailure(breaker.beforeCall());
        Thread.sleep(1_100L);
        long abandoned = breaker.beforeCall();

        Thread.sleep(1_100L);
        long second = breaker.beforeCall();
        breaker.onFailure(second);

        Thread.sleep(1_100L);
        long current = breaker.beforeCall();
        assertTrue(current > second, "a permit value must never be reused");

        breaker.onSuccess(abandoned);
        assertTrue(breaker.isOpen(), "the abandoned permit must not close the circuit");
        assertThrows(AiServiceException.class, breaker::beforeCall);

        breaker.onSuccess(current);
        assertFalse(breaker.isOpen());
    }
}
