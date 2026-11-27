package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.service.ReviewSchema;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The service is the only retry layer. These tests pin the seam the
 * application relies on: one HTTP call can become several provider attempts
 * here, and the application never sees the ladder.
 */
class RetryingModelCallerTest {

    /** Three 30 s attempts plus backoff, with headroom. */
    private static final long BUDGET_MILLIS = 120_000L;
    private static final RetryingModelCaller.CallContext CONTEXT =
            new RetryingModelCaller.CallContext("req-1", "trace-1", "code");

    private final AiServiceProperties properties = new AiServiceProperties();
    private RecordingGateway gateway;
    private RecordingArchive archive;
    private RetryingModelCaller caller;

    @BeforeEach
    void setUp() {
        properties.getLlm().setMaxAttempts(3);
        properties.getLlm().setRetryBackoffMs(0);
        properties.getLlm().setBreakerFailures(100);
        gateway = new RecordingGateway();
        archive = new RecordingArchive();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        caller = new RetryingModelCaller(gateway, properties,
                new ProviderCircuitBreaker(properties, meters), archive, meters);
    }

    @Test
    void twoFailuresThenSuccessIsThreeProviderCallsAndOneResult() {
        gateway.failures.add(new AiServiceException("model_unavailable", true, 502, "down"));
        gateway.failures.add(new AiServiceException("model_timeout", true, 504, "slow"));

        ModelGateway.ModelReply reply = complete();

        assertEquals("ok", reply.content());
        assertEquals(3, gateway.calls);
        assertEquals(List.of("error", "error", "success"),
                archive.records.stream().map(CallArchive.Record::outcome).toList());
    }

    @Test
    void alwaysFailingStopsAtTheConfiguredBudget() {
        gateway.alwaysFail = new AiServiceException("model_unavailable", true, 502, "down");

        assertThrows(AiServiceException.class, this::complete);
        assertEquals(3, gateway.calls);
    }

    @Test
    void aNonRetryableFailureIsNotRetried() {
        gateway.alwaysFail = new AiServiceException("model_rejected", false, 400, "bad request");

        assertThrows(AiServiceException.class, this::complete);
        assertEquals(1, gateway.calls);
    }

    @Test
    void aBudgetShorterThanOneAttemptMakesNoProviderCall() {
        AiServiceException error = assertThrows(AiServiceException.class,
                () -> caller.complete("s", "u", 0.2, ReviewSchema.CODE_REVIEW, CONTEXT, 1_000L));

        assertEquals("model_timeout", error.getCode());
        assertEquals(0, gateway.calls);
    }

    @Test
    void anExhaustedBudgetMakesNoProviderCall() {
        AiServiceException error = assertThrows(AiServiceException.class,
                () -> caller.complete("s", "u", 0.2, ReviewSchema.CODE_REVIEW, CONTEXT, 0L));

        assertEquals("model_timeout", error.getCode());
        assertEquals(0, gateway.calls);
    }

    @Test
    void theOutputSchemaReachesTheGateway() {
        complete();

        assertSame(ReviewSchema.CODE_REVIEW, gateway.schemas.get(0));
    }

    @Test
    void anOpenBreakerStopsBeforeTheNextAttempt() {
        properties.getLlm().setMaxAttempts(5);
        properties.getLlm().setBreakerFailures(2);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        caller = new RetryingModelCaller(gateway, properties,
                new ProviderCircuitBreaker(properties, meters), archive, meters);
        gateway.alwaysFail = new AiServiceException("model_unavailable", true, 502, "down");

        AiServiceException error = assertThrows(AiServiceException.class, this::complete);

        assertEquals("model_unavailable", error.getCode());
        assertEquals(503, error.getStatus());
        assertEquals(2, gateway.calls);
    }

    @Test
    void anArchiveFailureDoesNotFailTheReview() {
        archive.fail = true;

        assertEquals("ok", complete().content());
    }

    private ModelGateway.ModelReply complete() {
        return caller.complete("s", "u", 0.2, ReviewSchema.CODE_REVIEW, CONTEXT, BUDGET_MILLIS);
    }

    private static final class RecordingGateway implements ModelGateway {

        private final List<AiServiceException> failures = new ArrayList<>();
        private final List<OutputSchema> schemas = new ArrayList<>();
        private AiServiceException alwaysFail;
        private int calls;

        @Override
        public ModelReply complete(String system, String user, double temperature, OutputSchema schema) {
            calls++;
            schemas.add(schema);
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

    private static final class RecordingArchive implements CallArchive {

        private final List<Record> records = new ArrayList<>();
        private boolean fail;

        @Override
        public void record(Record record) {
            if (fail) {
                throw new IllegalStateException("disk full");
            }
            records.add(record);
        }
    }
}
