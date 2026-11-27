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
            "code_review", review("overall code quality", "security vulnerabilities, risks or concerns found"),
            true);
    public static final OutputSchema PROMPT_REVIEW = new OutputSchema(
            "prompt_review", review("overall prompt quality", "ways the prompt could be misused or leak context"),
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

    private static Map<String, Object> review(String qualityDescription, String securityDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("score", Map.of(
                "type", "integer", "minimum", 0, "maximum", 10,
                "description", "Overall score from 0 (worst) to 10 (best)"));
        properties.put("severity", Map.of(
                "type", "string", "enum", List.of("low", "medium", "high", "critical")));
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

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required",
                List.of("score", "severity", "codeQuality", "securityConcerns", "optimizationSuggestions"));
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
