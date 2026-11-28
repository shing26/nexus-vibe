package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nexus.campus.ai.model.OutputSchema;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The schemas the provider is asked to enforce. They mirror the fields the application
 * already validates, so the wire format cannot drift from the contract by accident.
 */
public final class ReviewSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public static final OutputSchema CODE_REVIEW = new OutputSchema(
            "code_review", review(
                    "overall code quality",
                    "security vulnerabilities, risks or concerns found",
                    "Risk of the most serious finding, classified before score: critical = "
                            + "RCE, injection, auth bypass or committed production secret; high "
                            + "= XSS, SSRF, hardcoded credential, path traversal, shared-state "
                            + "race, out-of-bounds write or unsafe deserialization; medium = "
                            + "null dereference, resource leak, swallowed error, mutable default, "
                            + "regex backtracking, serial I/O or loose equality; low = style, "
                            + "naming, missing tests or none"),
            true);
    public static final OutputSchema PROMPT_REVIEW = new OutputSchema(
            "prompt_review", review(
                    "overall prompt quality",
                    "ways the prompt could be misused or leak context",
                    "Risk of the most serious finding, classified before score: critical = "
                            + "executes untrusted instructions, exfiltrates secrets or bypasses "
                            + "authorization; high = easy jailbreak, context leak or untrusted "
                            + "input into a sensitive action; medium = task exists but input or "
                            + "output contract is missing or ambiguous, or no failure rule; low "
                            + "= style, wording or no finding"),
            true);
    public static final OutputSchema SAFETY = new OutputSchema(
            "safety_classification", safety(), true);

    private ReviewSchema() {
    }

    public static OutputSchema forKind(String kind) {
        return "prompt".equals(kind) ? PROMPT_REVIEW : CODE_REVIEW;
    }

    /** Stable digest of the schemas, used by the prompt version and the dedup key. */
    public static String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(MAPPER.writeValueAsBytes(List.of(CODE_REVIEW, PROMPT_REVIEW, SAFETY)));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("cannot fingerprint the review schemas", e);
        }
    }

    private static Map<String, Object> review(String qualityDescription, String securityDescription,
                                              String severityDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("severity", Map.of(
                "type", "string", "enum", List.of("low", "medium", "high", "critical"),
                "description", severityDescription));
        properties.put("score", Map.of(
                "type", "integer", "minimum", 0, "maximum", 10,
                "description", "Overall quality: 9-10 clean, 7-8 low-risk only, 5-6 one "
                        + "medium finding, 3-4 high or several medium, 0-2 critical. A high "
                        + "or critical finding caps score at 4; a medium finding caps it at 7. "
                        + "Higher is better; this is not a risk score"));
        properties.put("codeQuality", Map.of(
                "type", "string",
                "description", "Multi-sentence observations about " + qualityDescription));
        properties.put("securityConcerns", Map.of(
                "type", "string",
                "description", "Multi-sentence description of " + securityDescription
                        + "; state explicitly if none"));
        properties.put("optimizationSuggestions", Map.of(
                "type", "string",
                "description", "Multi-sentence concrete suggestions for improvement"));
        properties.put("evidence", Map.of(
                "type", "array",
                "maxItems", 5,
                "description", "Up to five short verbatim quotes copied from the submitted "
                        + "content; empty when no quote supports the verdict",
                "items", Map.of(
                        "type", "string",
                        "description", "At least 10 characters, copied exactly from the content")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required",
                List.of("severity", "score", "codeQuality", "securityConcerns",
                        "optimizationSuggestions", "evidence"));
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> safety() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("classification", Map.of(
                "type", "string",
                "enum", List.of("safe", "prompt_injection", "harmful", "spam")));
        properties.put("confidence", Map.of(
                "type", "number", "minimum", 0, "maximum", 1));
        properties.put("reason", Map.of(
                "type", "string", "description", "One sentence explaining the classification"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("classification", "confidence", "reason"));
        schema.put("properties", properties);
        return schema;
    }
}
