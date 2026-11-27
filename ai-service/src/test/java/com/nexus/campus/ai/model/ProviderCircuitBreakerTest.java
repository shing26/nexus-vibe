package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCircuitBreakerTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private ProviderCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        properties.getLlm().setBreakerFailures(2);
        properties.getLlm().setBreakerOpenSeconds(1);
        breaker = new ProviderCircuitBreaker(properties, new SimpleMeterRegistry());
    }

    @Test
    void opensAfterTheThresholdAndFailsFast() {
        breaker.beforeCall();
        breaker.onFailure();
        breaker.beforeCall();
        breaker.onFailure();

        assertTrue(breaker.isOpen());
        AiServiceException error = assertThrows(AiServiceException.class, breaker::beforeCall);
        assertEquals("model_unavailable", error.getCode());
        assertEquals(503, error.getStatus());
    }

    @Test
    void successResetsTheFailureCount() {
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();

        assertFalse(breaker.isOpen());
        breaker.beforeCall();
    }

    @Test
    void oneProbeIsAllowedAfterTheWindowAndSuccessClosesIt() throws InterruptedException {
        breaker.onFailure();
        breaker.onFailure();
        Thread.sleep(1_100L);

        breaker.beforeCall();
        assertThrows(AiServiceException.class, breaker::beforeCall);

        breaker.onSuccess();
        assertFalse(breaker.isOpen());
        breaker.beforeCall();
    }

    @Test
    void aFailedProbeReopensTheWindow() throws InterruptedException {
        breaker.onFailure();
        breaker.onFailure();
        Thread.sleep(1_100L);

        breaker.beforeCall();
        breaker.onFailure();

        assertTrue(breaker.isOpen());
        assertThrows(AiServiceException.class, breaker::beforeCall);
    }
}
