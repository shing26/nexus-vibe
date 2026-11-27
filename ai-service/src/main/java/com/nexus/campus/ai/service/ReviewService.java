package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PromptFactory prompts;
    private final RetryingModelCaller caller;
    private final AiServiceProperties properties;
    private final ReviewDeduplicator dedup;
    private final MeterRegistry meters;

    private Counter success;
    private Counter invalid;
    private Counter failed;
    private Timer duration;

    public ReviewService(PromptFactory prompts,
                         RetryingModelCaller caller,
                         AiServiceProperties properties,
                         ReviewDeduplicator dedup,
                         MeterRegistry meters) {
        this.prompts = prompts;
        this.caller = caller;
        this.properties = properties;
        this.dedup = dedup;
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
        String key = ReviewDeduplicator.key(request, properties.getLlm().getModel(), prompts.version());
        return dedup.run(key, () -> compute(request));
    }

    private ReviewResponse compute(ReviewRequest request) {
        long started = System.nanoTime();
        PromptFactory.Prompt prompt = prompts.review(request);
        boolean repaired = false;
        long budgetMillis = request.budget() != null && request.budget().timeoutMs() > 0
                ? request.budget().timeoutMs()
                : properties.getLlm().maxBudgetMillis();
        long deadline = started + budgetMillis * 1_000_000L;

        ModelGateway.ModelReply reply = caller.complete(
                prompt.system(), prompt.user(), REVIEW_TEMPERATURE,
                ReviewSchema.forKind(prompt.kind()), context(request),
                remainingMillis(deadline));
        JsonRepair.ParseResult parsed = JsonRepair.parse(reply.content());
        ReviewResponse response = parsed.ok()
                ? map(parsed.node(), reply, prompt.kind(), request)
                : null;

        if (response == null || !valid(response)) {
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
            reply = caller.complete(retrySystem, retryUser, REPAIR_TEMPERATURE,
                    ReviewSchema.forKind(prompt.kind()), context(request),
                    remainingMillis(deadline));
            parsed = JsonRepair.parse(reply.content());
            response = parsed.ok() ? map(parsed.node(), reply, prompt.kind(), request) : null;
        }

        if (response == null) {
            failed.increment();
            throw new AiServiceException("invalid_model_output", false, 502,
                    "model output was not parseable after repair");
        }

        boolean valid = valid(response);
        if (valid) {
            success.increment();
        } else {
            invalid.increment();
            response = invalidEvidenceVerdict(response);
        }
        duration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        return new ReviewResponse(
                response.score(), response.severity(), response.quality(), response.security(),
                response.suggestions(), response.approved(), response.resultJson(),
                response.model(), response.usage(),
                Math.max(0L, (System.nanoTime() - started) / 1_000_000L),
                repaired, response.evidence(), response.evidenceRejected());
    }

    private RetryingModelCaller.CallContext context(ReviewRequest request) {
        return new RetryingModelCaller.CallContext(
                request.requestId(), request.traceId(), request.kind(), prompts.version());
    }

    private boolean valid(ReviewResponse response) {
        return ReviewValidator.isValid(
                response.score(), response.severity(), response.quality(), response.suggestions())
                && (!properties.getReview().isEvidenceRequired() || !response.evidence().isEmpty());
    }

    /**
     * Evidence-required is a service-side policy the application cannot see. A
     * verdict that is otherwise well-formed therefore travels as severity
     * {@code unknown}, which the application's own gate already rejects.
     */
    private ReviewResponse invalidEvidenceVerdict(ReviewResponse response) {
        if (!properties.getReview().isEvidenceRequired() || !response.evidence().isEmpty()) {
            return response;
        }
        if (!ReviewValidator.isValid(
                response.score(), response.severity(), response.quality(), response.suggestions())) {
            return response;
        }
        return new ReviewResponse(
                response.score(), "unknown", response.quality(), response.security(),
                response.suggestions(), false, invalidResultJson(response.resultJson()),
                response.model(),
                response.usage(), response.latencyMs(), response.repaired(),
                response.evidence(), response.evidenceRejected());
    }

    private static String invalidResultJson(String resultJson) {
        try {
            JsonNode node = JSON.readTree(resultJson);
            if (node instanceof ObjectNode object) {
                object.put("severity", "unknown");
                return object.toString();
            }
        } catch (Exception ignored) {
            // Keep the original text; the response-level severity still rejects it.
        }
        return resultJson;
    }

    private ReviewResponse map(JsonNode node, ModelGateway.ModelReply reply, String kind,
                               ReviewRequest request) {
        int score = Math.max(0, Math.min(10, node.path("score").asInt(0)));
        String severity = node.path("severity").asText("unknown");
        String quality = node.path("codeQuality").asText("");
        String security = node.path("securityConcerns").asText("");
        String suggestions = node.path("optimizationSuggestions").asText("");
        boolean approved = score >= 5 && !"critical".equals(severity);
        EvidenceExtractor.Result evidence = EvidenceExtractor.extract(
                node, request.content(), request.snippets());
        return new ReviewResponse(score, severity, quality, security, suggestions, approved,
                withEvidence(node, evidence), properties.getLlm().getModel(),
                new ReviewResponse.Usage(reply.promptTokens(), reply.completionTokens()),
                0L, false, evidence.accepted(), evidence.rejected());
    }

    private static String withEvidence(JsonNode node, EvidenceExtractor.Result evidence) {
        if (!(node instanceof ObjectNode object)) {
            return node.toString();
        }
        ObjectNode copy = object.deepCopy();
        ArrayNode quotes = copy.putArray("evidence");
        for (ReviewResponse.Evidence item : evidence.accepted()) {
            ObjectNode quote = quotes.addObject();
            quote.put("quote", item.quote());
            quote.put("startLine", item.startLine());
            quote.put("endLine", item.endLine());
        }
        copy.put("evidenceRejected", evidence.rejected());
        return copy.toString();
    }

    private static long remainingMillis(long deadline) {
        return Math.max(1L, (deadline - System.nanoTime() + 999_999L) / 1_000_000L);
    }
}
