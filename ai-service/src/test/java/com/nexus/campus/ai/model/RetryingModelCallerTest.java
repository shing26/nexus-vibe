package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The service is the only retry layer. These tests pin the seam the
 * application relies on: one HTTP call can become several provider attempts
 * here, and the application never sees the ladder.
 */
class RetryingModelCallerTest {

    /** Three 30 s attempts plus backoff, with headroom. */
    private static final long BUDGET_MILLIS = 120_000L;

    private final AiServiceProperties properties = new AiServiceProperties();
    private RecordingGateway gateway;
    private RetryingModelCaller caller;

    @BeforeEach
    void setUp() {
        properties.getLlm().setMaxAttempts(3);
        properties.getLlm().setRetryBackoffMs(0);
        gateway = new RecordingGateway();
        caller = new RetryingModelCaller(gateway, properties);
    }

    @Test
    void twoFailuresThenSuccessIsThreeProviderCallsAndOneResult() {
        gateway.failures.add(new AiServiceException("model_unavailable", true, 502, "down"));
        gateway.failures.add(new AiServiceException("model_timeout", true, 504, "slow"));

        ModelGateway.ModelReply reply = caller.complete("s", "u", 0.2, BUDGET_MILLIS);

        assertEquals("ok", reply.content());
        assertEquals(3, gateway.calls);
    }

    @Test
    void alwaysFailingStopsAtTheConfiguredBudget() {
        gateway.alwaysFail = new AiServiceException("model_unavailable", true, 502, "down");

        assertThrows(AiServiceException.class, () -> caller.complete("s", "u", 0.2, BUDGET_MILLIS));
        assertEquals(3, gateway.calls);
    }

    @Test
    void aNonRetryableFailureIsNotRetried() {
        gateway.alwaysFail = new AiServiceException("model_rejected", false, 400, "bad request");

        assertThrows(AiServiceException.class, () -> caller.complete("s", "u", 0.2, BUDGET_MILLIS));
        assertEquals(1, gateway.calls);
    }

    @Test
    void aBudgetShorterThanOneAttemptMakesNoProviderCall() {
        AiServiceException error = assertThrows(AiServiceException.class,
                () -> caller.complete("s", "u", 0.2, 1_000L));

        assertEquals("model_timeout", error.getCode());
        assertEquals(0, gateway.calls);
    }

    @Test
    void anExhaustedBudgetMakesNoProviderCall() {
        AiServiceException error = assertThrows(AiServiceException.class,
                () -> caller.complete("s", "u", 0.2, 0L));

        assertEquals("model_timeout", error.getCode());
        assertEquals(0, gateway.calls);
    }

    private static final class RecordingGateway implements ModelGateway {

        private final List<AiServiceException> failures = new ArrayList<>();
        private AiServiceException alwaysFail;
        private int calls;

        @Override
        public ModelReply complete(String system, String user, double temperature) {
            calls++;
            if (alwaysFail != null) {
                throw alwaysFail;
            }
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            return new ModelReply("ok", 1, 1);
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}
