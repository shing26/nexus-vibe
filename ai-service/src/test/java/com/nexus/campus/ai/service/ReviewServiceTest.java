package com.nexus.campus.ai.service;

import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.AiServiceException;
import com.nexus.campus.ai.model.CallArchive;
import com.nexus.campus.ai.model.ModelGateway;
import com.nexus.campus.ai.model.OutputSchema;
import com.nexus.campus.ai.model.ProviderCircuitBreaker;
import com.nexus.campus.ai.model.RetryingModelCaller;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private FakeGateway gateway;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        properties.getLlm().setMaxAttempts(1);
        properties.getLlm().setModel("test-model");
        properties.getService().setMaxContextTokens(12000);
        properties.getReview().setCacheTtl(Duration.ZERO);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        gateway = new FakeGateway();
        service = new ReviewService(
                new PromptFactory(properties),
                new RetryingModelCaller(gateway, properties,
                        new ProviderCircuitBreaker(properties, meters), CallArchive.NOOP, meters),
                properties,
                new ReviewDeduplicator(properties, meters),
                meters);
        service.registerMetrics();
    }

    @Test
    void returnsAValidVerdictOnTheFirstPass() {
        gateway.replies.add(reply(validReview()));

        ReviewResponse response = service.review(request());

        assertEquals(8, response.score());
        assertEquals("low", response.severity());
        assertTrue(response.approved());
        assertEquals(1, gateway.systems.size());
    }

    @Test
    void retriesOnceWithReinforcementWhenTheVerdictIsEmpty() {
        gateway.replies.add(reply("{\"score\":8,\"severity\":\"low\","
                + "\"codeQuality\":\"\",\"securityConcerns\":\"none\","
                + "\"optimizationSuggestions\":\"\"}"));
        gateway.replies.add(reply(validReview()));

        ReviewResponse response = service.review(request());

        assertEquals(8, response.score());
        assertTrue(response.repaired());
        assertEquals(2, gateway.systems.size());
        assertTrue(gateway.systems.get(1).contains("STRICT OUTPUT REQUIREMENTS"));
    }

    @Test
    void throwsWhenBothPassesAreUnparseable() {
        gateway.replies.add(reply("not json"));
        gateway.replies.add(reply("still not json"));

        assertThrows(AiServiceException.class, () -> service.review(request()));
    }

    @Test
    void verifiesEvidenceAndStoresItInTheResultJson() {
        gateway.replies.add(reply(validReviewWithEvidence("int x = 1;")));

        ReviewResponse response = service.review(request());

        assertEquals(1, response.evidence().size());
        assertTrue(response.resultJson().contains("\"startLine\":2"),
                () -> "resultJson was: " + response.resultJson());
        assertEquals(0, response.evidenceRejected());
    }

    @Test
    void requiredEvidenceTriggersARepairPassAndThenAnInvalidVerdict() {
        properties.getReview().setEvidenceRequired(true);
        gateway.replies.add(reply(validReview()));
        gateway.replies.add(reply(validReview()));

        ReviewResponse response = service.review(request());

        assertTrue(response.repaired());
        assertEquals("unknown", response.severity());
        assertEquals(2, gateway.systems.size());
        assertTrue(response.evidence().isEmpty());
        assertTrue(response.resultJson().contains("\"severity\":\"unknown\""),
                () -> "resultJson was: " + response.resultJson());
    }

    @Test
    void requiredEvidenceAcceptsAQuoteOnTheRepairPass() {
        properties.getReview().setEvidenceRequired(true);
        gateway.replies.add(reply(validReview()));
        gateway.replies.add(reply(validReviewWithEvidence("int x = 1;")));

        ReviewResponse response = service.review(request());

        assertTrue(response.repaired());
        assertEquals("low", response.severity());
        assertEquals(1, response.evidence().size());
    }

    private ReviewRequest request() {
        return new ReviewRequest("req-1", "trace-1", 42L, "code", "Calculator",
                "```java\nint x = 1;\n```", List.of("int x = 1;"),
                new ReviewRequest.Budget(12000, 60000));
    }

    private static String validReview() {
        return "{\"score\":8,\"severity\":\"low\","
                + "\"codeQuality\":\"Clean structure and readable naming throughout the implementation\","
                + "\"securityConcerns\":\"No injection or unsafe cast was found in this code\","
                + "\"optimizationSuggestions\":\"Extract the division logic into a helper and add unit tests\"}";
    }

    private static String validReviewWithEvidence(String quote) {
        return "{\"score\":8,\"severity\":\"low\","
                + "\"codeQuality\":\"Clean structure and readable naming throughout the implementation\","
                + "\"securityConcerns\":\"No injection or unsafe cast was found in this code\","
                + "\"optimizationSuggestions\":\"Extract the division logic into a helper and add unit tests\","
                + "\"evidence\":[\"" + quote + "\"]}";
    }

    private static ModelGateway.ModelReply reply(String content) {
        return new ModelGateway.ModelReply(content, 10, 5);
    }

    private static final class FakeGateway implements ModelGateway {
        private final Deque<ModelReply> replies = new ArrayDeque<>();
        private final List<String> systems = new ArrayList<>();

        @Override
        public ModelReply complete(String system, String user, double temperature, OutputSchema schema) {
            systems.add(system);
            return replies.poll();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}
