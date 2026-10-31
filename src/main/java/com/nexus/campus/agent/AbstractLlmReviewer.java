package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import lombok.extern.slf4j.Slf4j;

/**
 * Template for LLM reviewers: one structured call, then the pipeline's shared
 * retry ladder — a schema-valid but semantically empty result retries once
 * with a reinforced prompt, an unparseable result retries once with
 * self-correction, and anything else degrades to the caller. Subclasses own
 * only what differs: the request (system + user, built together so per-request
 * nonce delimiters match), the schema, and how a verdict renders.
 */
@Slf4j
public abstract class AbstractLlmReviewer implements Reviewer {

    protected static final double REVIEW_TEMPERATURE = 0.2;
    /** Self-correction pass: near-deterministic, focused on syntax only. */
    protected static final double REPAIR_TEMPERATURE = 0.1;

    /** Rough token estimation: ~3.5 chars per token, conservative for code. */
    protected static final double CHARS_PER_TOKEN = 3.5;

    protected final LlmClient llmClient;
    protected final CampusAiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected AbstractLlmReviewer(LlmClient llmClient, CampusAiProperties aiProperties) {
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    /**
     * The system and user messages of one request, built together. Per-request
     * nonce delimiters are generated once inside the implementation and appear
     * in both halves — a system prompt declaring boundaries the user content
     * does not use would tell the model to trust the wrong walls.
     */
    protected record RequestParts(String system, String user) {
    }

    @Override
    public final Verdict review(VibePost post) {
        JsonNode schema = schema();
        RequestParts request = buildRequest(post);

        // First pass: raw text -> repair-parse. The repair pipeline fixes
        // fences, trailing commas, and max-token truncation before parsing.
        String raw = llmClient.sendStructuredRequest(
                request.system(), request.user(), schemaName(), schema, REVIEW_TEMPERATURE);
        JsonRepairUtil.ParseResult parsed = JsonRepairUtil.parse(raw);
        Verdict verdict = parsed.ok() && parsed.node() != null ? parse(parsed.node(), raw) : null;
        String resultJson = parsed.node() == null ? null : parsed.node().toString();

        if (verdict == null || !ReviewValidity.isValid(verdict)) {
            // One retry before degrading. When the first pass was parseable
            // but semantically empty, use the reinforced prompt; when it was
            // UNPARSEABLE, upgrade the retry to self-correction — the model
            // gets its own broken output plus the parser's positional error.
            String retryPrompt;
            String retryUser;
            if (parsed.ok()) {
                log.info("Review result from {} failed validation, retrying with reinforced prompt", identity());
                retryPrompt = request.system() + "\n\n" + reinforcement();
                retryUser = request.user();
            } else {
                log.info("Review output from {} unparseable ({}), retrying with self-correction",
                        identity(), parsed.error());
                retryPrompt = buildRepairSystemPrompt();
                retryUser = buildRepairUserContent(parsed.error(), raw);
            }
            String retryRaw = llmClient.sendStructuredRequest(
                    retryPrompt, retryUser, schemaName(), schema, REPAIR_TEMPERATURE);
            JsonRepairUtil.ParseResult retryParsed = JsonRepairUtil.parse(retryRaw);
            verdict = retryParsed.ok() && retryParsed.node() != null
                    ? parse(retryParsed.node(), retryRaw) : null;
            resultJson = retryParsed.node() == null ? null : retryParsed.node().toString();
        }

        if (verdict == null) {
            log.warn("LLM unavailable for {}; verdict marked for reconciliation", identity());
            return null;
        }
        return new Verdict(verdict.score, verdict.severity, verdict.quality, verdict.security,
                verdict.suggestions, verdict.approved, renderBody(verdict), resultJson);
    }

    /** The reviewer's own JSON-schema request name, e.g. {@code code_review}. */
    protected abstract String schemaName();

    protected abstract JsonNode schema();

    protected abstract RequestParts buildRequest(VibePost post);

    /**
     * Appended to the request's system prompt for the one retry after a
     * schema-valid but semantically empty response.
     */
    protected abstract String reinforcement();

    /** Maps a parsed JSON object to a verdict; never returns null for a valid node. */
    protected abstract Verdict parse(JsonNode resultJson, String rawOutput);

    protected abstract String renderBody(Verdict verdict);

    /**
     * Common approval rule across reviewers: a passing score with no critical
     * finding. Reviewers with a different bar set {@code approved} in their
     * parse instead.
     */
    protected static boolean defaultApproved(int score, String severity) {
        return score >= 5 && !"critical".equals(severity);
    }

    protected int clampScore(JsonNode node) {
        return Math.max(0, Math.min(10, node.path("score").asInt(0)));
    }

    protected ObjectMapper getMapper() {
        return objectMapper;
    }

    /** Shared token-budget ceiling for a user message, in characters. */
    protected int maxUserContentChars() {
        int tokens = aiProperties.getReview().getMaxContextTokens();
        return (int) (tokens * CHARS_PER_TOKEN);
    }

    private String buildRepairSystemPrompt() {
        return "You fix malformed JSON. You will receive a broken JSON object and a parser error.\n"
                + "Return ONLY the corrected JSON object with the same fields and values.\n"
                + "Close any unterminated strings or brackets, remove trailing commas, and drop "
                + "incomplete key/value pairs at the end if they cannot be completed meaningfully.\n"
                + "Do not invent new fields. Do not change existing values.";
    }

    private String buildRepairUserContent(String error, String rawOutput) {
        return "Parser error: " + error + "\n\nBroken JSON:\n"
                + (rawOutput == null ? "(empty)" : rawOutput);
    }

    /**
     * Starts a strict schema object so each reviewer declares only its
     * required fields and properties.
     */
    protected JsonNode objectSchema() {
        return objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
    }
}
