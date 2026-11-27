package com.nexus.campus.ai.service;

import com.nexus.campus.ai.api.dto.SafetyRequest;
import com.nexus.campus.ai.api.dto.SafetyResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.AiServiceException;
import com.nexus.campus.ai.model.ModelGateway;
import com.nexus.campus.ai.model.RetryingModelCaller;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafetyServiceTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private FakeGateway gateway;
    private SafetyService service;

    @BeforeEach
    void setUp() {
        properties.getLlm().setMaxAttempts(1);
        properties.getLlm().setModel("test-model");
        gateway = new FakeGateway();
        service = new SafetyService(
                new PromptFactory(properties),
                new RetryingModelCaller(gateway, properties),
                properties,
                new SimpleMeterRegistry());
        service.registerMetrics();
    }

    @Test
    void returnsAValidClassification() {
        gateway.replies.add(reply("{\"classification\":\"safe\",\"confidence\":0.9,\"reason\":\"normal discussion\"}"));

        SafetyResponse response = service.classify(request());

        assertEquals("safe", response.classification());
        assertEquals(1, response.reasons().size());
    }

    @Test
    void retriesOnceWhenTheClassificationIsUnknown() {
        gateway.replies.add(reply("{\"classification\":\"maybe\",\"confidence\":0.2,\"reason\":\"?\"}"));
        gateway.replies.add(reply("{\"classification\":\"spam\",\"confidence\":0.8,\"reason\":\"advertisement\"}"));

        SafetyResponse response = service.classify(request());

        assertEquals("spam", response.classification());
    }

    @Test
    void throwsWhenBothPassesAreInvalid() {
        gateway.replies.add(reply("{\"classification\":\"maybe\"}"));
        gateway.replies.add(reply("{\"classification\":\"still-maybe\"}"));

        assertThrows(AiServiceException.class, () -> service.classify(request()));
    }

    private static SafetyRequest request() {
        return new SafetyRequest("req-1", "trace-1", "hello", "post");
    }

    private static ModelGateway.ModelReply reply(String content) {
        return new ModelGateway.ModelReply(content, 5, 3);
    }

    private static final class FakeGateway implements ModelGateway {
        private final Deque<ModelReply> replies = new ArrayDeque<>();

        @Override
        public ModelReply complete(String system, String user, double temperature) {
            return replies.poll();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}
