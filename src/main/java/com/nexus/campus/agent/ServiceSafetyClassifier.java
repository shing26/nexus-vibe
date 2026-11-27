package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Service-mode safety call. The response is converted back to the shape the
 * listeners already parse, so their per-class policy does not change.
 */
@Slf4j
public class ServiceSafetyClassifier implements SafetyClassifier {

    private final AiServiceClient client;
    private final ObjectMapper objectMapper;

    public ServiceSafetyClassifier(AiServiceClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode classify(String content, Surface surface) {
        String traceId = MDC.get("traceId");
        String requestId = (traceId == null || traceId.isBlank() ? "no-trace" : traceId)
                + ":safety:" + (surface == Surface.COMMENT ? "comment" : "post")
                + ":" + System.nanoTime();
        try {
            AiServiceClient.SafetyResponse response = client.safety(
                    new AiServiceClient.SafetyRequest(
                            requestId,
                            traceId,
                            PromptIsolation.neutralize(content),
                            surface == Surface.COMMENT ? "comment" : "post"));
            ObjectNode node = objectMapper.createObjectNode();
            node.put("classification", response.classification());
            node.put("confidence", response.confidence());
            node.put("reason", response.reasons() == null || response.reasons().isEmpty()
                    ? "" : response.reasons().get(0));
            return node;
        } catch (AiServiceClient.AiServiceException e) {
            log.warn("AI service safety check failed ({}): {}", e.getCode(), e.getMessage());
            return null;
        }
    }
}
