package com.nexus.campus.ai.service;

import com.nexus.campus.ai.model.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSchemaTest {

    @Test
    void reviewSchemasEnumerateTheSameFieldsTheApplicationValidates() {
        for (OutputSchema schema : List.of(ReviewSchema.CODE_REVIEW, ReviewSchema.PROMPT_REVIEW)) {
            assertEquals(false, schema.schema().get("additionalProperties"));
            assertEquals(
                    List.of("severity", "score", "codeQuality", "securityConcerns",
                            "optimizationSuggestions", "evidence"),
                    schema.schema().get("required"));
            Map<?, ?> properties = (Map<?, ?>) schema.schema().get("properties");
            assertTrue(properties.containsKey("score"));
            assertTrue(properties.containsKey("evidence"));
            assertTrue(((Map<?, ?>) properties.get("score")).get("description").toString()
                    .contains("Higher is better; this is not a risk score"));
            assertTrue(((Map<?, ?>) properties.get("severity")).get("description").toString()
                    .contains("Risk of the most serious finding"));
            assertTrue(schema.strict());
        }
        assertTrue(((Map<?, ?>) ((Map<?, ?>) ReviewSchema.PROMPT_REVIEW.schema()
                .get("properties")).get("severity")).get("description").toString()
                .contains("executes untrusted instructions"));
    }

    @Test
    void safetyUsesTheFourClassesTheServiceAccepts() {
        Map<?, ?> properties = (Map<?, ?>) ReviewSchema.SAFETY.schema().get("properties");
        Map<?, ?> classification = (Map<?, ?>) properties.get("classification");

        assertEquals(List.of("safe", "prompt_injection", "harmful", "spam"), classification.get("enum"));
    }

    @Test
    void theFingerprintIsStableAndNonBlank() {
        assertEquals(12, ReviewSchema.fingerprint().length());
        assertEquals(ReviewSchema.fingerprint(), ReviewSchema.fingerprint());
        assertFalse(ReviewSchema.fingerprint().isBlank());
    }
}
