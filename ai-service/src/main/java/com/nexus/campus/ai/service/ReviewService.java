package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.AiServiceException;
import com.nexus.campus.ai.model.ModelGateway;
import com.nexus.campus.ai.model.RetryingModelCaller;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ReviewService {

    private static final double REVIEW_TEMPERATURE = 0.2;
    private static final double REPAIR_TEMPERATURE = 0.1;

    private final PromptFactory prompts;
    private final RetryingModelCaller caller;
    private final AiServiceProperties properties;
    private final MeterRegistry meters;

    private Counter success;
    private Counter invalid;
    private Counter failed;
    private Timer duration;

    public ReviewService(PromptFactory prompts,
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
        success = Counter.builder("ai.review.requests").tag("outcome", "success").register(meters);
        invalid = Counter.builder("ai.review.requests").tag("outcome", "invalid").register(meters);
        failed = Counter.builder("ai.review.requests").tag("outcome", "error").register(meters);
        duration = Timer.builder("ai.review.duration").register(meters);
    }

    public ReviewResponse review(ReviewRequest request) {
        long started = System.nanoTime();
        PromptFactory.Prompt prompt = prompts.review(request);
        boolean repaired = false;

        ModelGateway.ModelReply reply = caller.complete(
                prompt.system(), prompt.user(), REVIEW_TEMPERATURE);
        JsonRepair.ParseResult parsed = JsonRepair.parse(reply.content());
        ReviewResponse response = parsed.ok() ? map(parsed.node(), reply, prompt.kind()) : null;

        if (response == null || !ReviewValidator.isValid(
                response.score(), response.severity(), response.quality(), response.suggestions())) {
            repaired = true;
            String retrySystem;
            String retryUser;
            if (parsed.ok()) {
                retrySystem = prompt.system() + "\n\n" + prompts.reinforcement(prompt.kind());
                retryUser = prompt.user();
            } else {
                retrySystem = prompts.repairSystemPrompt();
                retryUser = prompts.repairUserContent(parsed.error(), reply.content());
            }
            reply = caller.complete(retrySystem, retryUser, REPAIR_TEMPERATURE);
            parsed = JsonRepair.parse(reply.content());
            response = parsed.ok() ? map(parsed.node(), reply, prompt.kind()) : null;
        }

        if (response == null) {
            failed.increment();
            throw new AiServiceException("invalid_model_output", false, 502,
                    "model output was not parseable after repair");
        }

        boolean valid = ReviewValidator.isValid(
                response.score(), response.severity(), response.quality(), response.suggestions());
        if (valid) {
            success.increment();
        } else {
            invalid.increment();
        }
        duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        return new ReviewResponse(
                response.score(), response.severity(), response.quality(), response.security(),
                response.suggestions(), response.approved(), response.resultJson(),
                response.model(), response.usage(),
                Math.max(0L, (System.nanoTime() - started) / 1_000_000L),
                repaired);
    }

    private ReviewResponse map(JsonNode node, ModelGateway.ModelReply reply, String kind) {
        int score = Math.max(0, Math.min(10, node.path("score").asInt(0)));
        String severity = node.path("severity").asText("unknown");
        String quality = node.path("codeQuality").asText("");
        String security = node.path("securityConcerns").asText("");
        String suggestions = node.path("optimizationSuggestions").asText("");
        boolean approved = score >= 5 && !"critical".equals(severity);
        return new ReviewResponse(score, severity, quality, security, suggestions, approved,
                node.toString(), properties.getLlm().getModel(),
                new ReviewResponse.Usage(reply.promptTokens(), reply.completionTokens()),
                0L, false);
    }
}
