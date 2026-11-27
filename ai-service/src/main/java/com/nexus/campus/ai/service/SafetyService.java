package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.campus.ai.api.dto.SafetyRequest;
import com.nexus.campus.ai.api.dto.SafetyResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.AiServiceException;
import com.nexus.campus.ai.model.ModelGateway;
import com.nexus.campus.ai.model.RetryingModelCaller;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SafetyService {

    private static final Set<String> CLASSES =
            Set.of("safe", "prompt_injection", "harmful", "spam");

    private final PromptFactory prompts;
    private final RetryingModelCaller caller;
    private final AiServiceProperties properties;
    private final MeterRegistry meters;
    private Counter success;
    private Counter failed;

    public SafetyService(PromptFactory prompts,
                         RetryingModelCaller caller,
                         AiServiceProperties properties,
                         MeterRegistry meters) {
        this.prompts = prompts;
        this.caller = caller;
        this.properties = properties;
        this.meters = meters;
    }

    @PostConstruct
    void registerMetrics() {
        success = Counter.builder("ai.safety.requests").tag("outcome", "success").register(meters);
        failed = Counter.builder("ai.safety.requests").tag("outcome", "error").register(meters);
    }

    public SafetyResponse classify(SafetyRequest request) {
        long started = System.nanoTime();
        PromptFactory.Prompt prompt = prompts.safety(request.text(), request.surface());
        ModelGateway.ModelReply reply = caller.complete(prompt.system(), prompt.user(), 0.0);
        JsonRepair.ParseResult parsed = JsonRepair.parse(reply.content());
        JsonNode node = parsed.ok() ? parsed.node() : null;

        if (parseClass(node) == null) {
            reply = caller.complete(prompt.system()
                    + "\n\nReturn only one of the four exact class names in the classification field.",
                    prompt.user(), 0.0);
            parsed = JsonRepair.parse(reply.content());
            node = parsed.ok() ? parsed.node() : null;
        }

        String classification = parseClass(node);
        if (classification == null) {
            failed.increment();
            throw new AiServiceException("invalid_model_output", false, 502,
                    "model output was not a valid safety classification");
        }

        success.increment();
        String reason = node.path("reason").asText("");
        double confidence = node.path("confidence").asDouble(0.0);
        return new SafetyResponse(classification, confidence,
                reason.isBlank() ? List.of() : List.of(reason),
                node.toString(), properties.getLlm().getModel(),
                Math.max(0L, (System.nanoTime() - started) / 1_000_000L));
    }

    private static String parseClass(JsonNode node) {
        if (node == null) {
            return null;
        }
        String value = node.path("classification").asText("")
                .trim().toLowerCase(Locale.ROOT);
        return CLASSES.contains(value) ? value : null;
    }
}
