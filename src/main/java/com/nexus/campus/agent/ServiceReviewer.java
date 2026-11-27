package com.nexus.campus.agent;

import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The service-mode implementation of the ADR-0012 reviewer seam. It produces
 * data only: the lease, validity gate, persistence and rendering stay in core,
 * exactly as they do for the direct reviewers.
 */
@Slf4j
public class ServiceReviewer implements Reviewer {

    private static final Pattern FENCED_CODE = Pattern.compile(
            "```[a-zA-Z]*\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    private final AiServiceClient client;
    private final CampusAiProperties properties;

    public ServiceReviewer(AiServiceClient client, CampusAiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String identity() {
        return "ai-service";
    }

    @Override
    public boolean claims(VibePost post) {
        if (post == null || post.getContent() == null || post.getContent().isBlank()) {
            return false;
        }
        if ("prompt".equals(post.getPostType())) {
            return true;
        }
        return !extractCodeBlocks(post.getContent()).isEmpty();
    }

    @Override
    public Verdict review(VibePost post) {
        boolean prompt = "prompt".equals(post.getPostType());
        String traceId = MDC.get("traceId");
        String requestId = (traceId == null || traceId.isBlank() ? "no-trace" : traceId)
                + ":" + post.getId() + ":" + (prompt ? "prompt" : "code");

        AiServiceClient.ReviewRequest request = new AiServiceClient.ReviewRequest(
                requestId,
                traceId,
                post.getId(),
                prompt ? "prompt" : "code",
                PromptIsolation.neutralize(post.getTitle()),
                PromptIsolation.neutralize(post.getContent()),
                prompt ? List.of() : extractCodeBlocks(post.getContent()),
                new AiServiceClient.Budget(
                        properties.getReview().getMaxContextTokens(),
                        (int) Math.max(1000,
                                properties.getService().getReadTimeout().toMillis() - 5000)));

        try {
            AiServiceClient.ReviewResponse response = client.review(request);
            Verdict fields = new Verdict(
                    response.score(), response.severity(), response.quality(),
                    response.security(), response.suggestions(), response.approved(),
                    null, response.resultJson());
            String body = prompt
                    ? VerdictRenderer.renderPrompt(fields)
                    : VerdictRenderer.renderCode(fields);
            return new Verdict(fields.score, fields.severity, fields.quality, fields.security,
                    fields.suggestions, fields.approved, body, response.resultJson());
        } catch (AiServiceClient.AiServiceException e) {
            if ("ai_service_invalid_response".equals(e.getCode())) {
                log.warn("AI service returned an unreadable review for post {}: {}",
                        post.getId(), e.getMessage());
                // Data, not a decision: ReviewValidity rejects this and the
                // caller records the invalid verdict rather than a transport
                // failure.
                return new Verdict(0, "unknown", "", "", "", false, null, null);
            }
            log.warn("AI service review failed for post {} ({}): {}",
                    post.getId(), e.getCode(), e.getMessage());
            return null;
        }
    }

    private static List<String> extractCodeBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = FENCED_CODE.matcher(content);
        while (matcher.find()) {
            blocks.add(matcher.group(1).trim());
        }
        return blocks;
    }
}
