package com.nexus.campus.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application and the AI service are separate Maven builds on purpose, but
 * the JSON names are one contract. If either side renames or drops a field,
 * this test fails against the committed OpenAPI file even when both sides
 * still compile.
 */
class AiServiceContractTest {

    private static final Path OPENAPI = Path.of("ai-service", "openapi", "ai-service.yaml");
    private static final Pattern PROPERTY = Pattern.compile("^ {8}([A-Za-z][A-Za-z0-9]*):.*$");

    @Test
    @DisplayName("the committed document is OpenAPI 3.1")
    void documentShouldDeclareOpenApi31() throws IOException {
        String yaml = Files.readString(OPENAPI);

        assertTrue(yaml.contains("openapi: 3.1.0"), "contract must stay on OpenAPI 3.1");
    }

    @Test
    @DisplayName("every application request/response record matches the OpenAPI property names")
    void recordsShouldMatchTheCommittedSchemas() throws IOException {
        String yaml = Files.readString(OPENAPI);

        assertSchema(yaml, AiServiceClient.ReviewRequest.class);
        assertSchema(yaml, AiServiceClient.Budget.class);
        assertSchema(yaml, AiServiceClient.ReviewResponse.class);
        assertSchema(yaml, AiServiceClient.Usage.class);
        assertSchema(yaml, AiServiceClient.SafetyRequest.class);
        assertSchema(yaml, AiServiceClient.SafetyResponse.class);
    }

    @Test
    @DisplayName("the response documents the fields the listener mapping depends on")
    void safetyResponseKeepsTheListenerFields() throws IOException {
        Set<String> properties = schemaProperties(Files.readString(OPENAPI), "SafetyResponse");

        assertTrue(properties.contains("classification"));
        assertTrue(properties.contains("confidence"));
        assertTrue(properties.contains("reasons"));
    }

    private static void assertSchema(String yaml, Class<? extends Record> type) {
        Set<String> expected = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expected, schemaProperties(yaml, type.getSimpleName()),
                type.getSimpleName() + " drifted from ai-service/openapi/ai-service.yaml");
    }

    private static Set<String> schemaProperties(String yaml, String schema) {
        List<String> lines = yaml.lines().toList();
        String header = "    " + schema + ":";
        int start = lines.indexOf(header);
        assertTrue(start >= 0, "missing schema " + schema);

        Set<String> properties = new LinkedHashSet<>();
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || (line.startsWith("    ") && !line.startsWith("     "))) {
                break;
            }
            Matcher matcher = PROPERTY.matcher(line);
            if (matcher.matches()) {
                properties.add(matcher.group(1));
            }
        }
        return properties;
    }
}
