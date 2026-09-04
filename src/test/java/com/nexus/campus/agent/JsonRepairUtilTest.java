package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repair-parsing samples: the four artifact classes observed from local
 * models (fences, truncation, trailing commas, and their combinations).
 */
class JsonRepairUtilTest {

    @Test
    @DisplayName("Clean JSON parses directly")
    void cleanJson() {
        assertTrue(JsonRepairUtil.parse("{\"score\":8}").ok());
    }

    @Test
    @DisplayName("Markdown code fences are stripped")
    void fencedJson() {
        String raw = "Here is my analysis:\n```json\n{\"score\":7,\"severity\":\"low\"}\n```\nHope that helps!";
        JsonNode node = JsonRepairUtil.parse(raw).node();
        assertNotNull(node);
        assertEquals(7, node.get("score").asInt());
    }

    @Test
    @DisplayName("Trailing commas are stripped")
    void trailingComma() {
        JsonNode node = JsonRepairUtil.parse("{\"score\":6,\"severity\":\"low\",}").node();
        assertNotNull(node);
        assertEquals(6, node.get("score").asInt());
    }

    @Test
    @DisplayName("Truncated JSON (max tokens) is completed and keeps full fields")
    void truncatedJson() {
        String raw = "{\"score\":8,\"severity\":\"low\",\"codeQuality\":\"Solid structure";
        JsonNode node = JsonRepairUtil.parse(raw).node();
        assertNotNull(node);
        assertEquals(8, node.get("score").asInt());
        assertEquals("low", node.get("severity").asText());
        assertEquals("Solid structure", node.get("codeQuality").asText());
    }

    @Test
    @DisplayName("Truncated JSON inside a nested array is completed")
    void truncatedNested() {
        String raw = "{\"score\":5,\"suggestions\":[\"add tests\",\"extract method";
        JsonNode node = JsonRepairUtil.parse(raw).node();
        assertNotNull(node);
        assertEquals(5, node.get("score").asInt());
        assertEquals(2, node.get("suggestions").size());
    }

    @Test
    @DisplayName("Fence + truncation combined still parses")
    void fencedAndTruncated() {
        String raw = "```json\n{\"score\":9,\"severity\":\"low\",\"codeQuality\":\"Excellent";
        JsonNode node = JsonRepairUtil.parse(raw).node();
        assertNotNull(node);
        assertEquals(9, node.get("score").asInt());
    }

    @Test
    @DisplayName("Dangling partial string is closed, preserving the partial value")
    void danglingPartialValue() {
        String raw = "{\"score\":7,\"severity\":\"lo";
        JsonNode node = JsonRepairUtil.parse(raw).node();
        assertNotNull(node);
        assertEquals(7, node.get("score").asInt());
        // the partial string is closed rather than dropped — more data survives
        assertEquals("lo", node.get("severity").asText());
    }

    @Test
    @DisplayName("Empty and non-JSON input produce positional errors")
    void garbageInput() {
        assertFalse(JsonRepairUtil.parse(null).ok());
        assertFalse(JsonRepairUtil.parse("   ").ok());
        JsonRepairUtil.ParseResult check = JsonRepairUtil.parse("no json here at all");
        assertFalse(check.ok());
        String msg = check.error() == null ? "" : check.error();
        assertTrue(msg.contains("no json") || msg.contains("length") || msg.contains("malformed"));
    }

    @Test
    @DisplayName("Structurally unrepairable input yields a positional error")
    void unrepairableInputYieldsError() {
        // ']' mismatches the open '{' — no repair stage can fix this
        String raw = "{\"a\":1]";
        JsonRepairUtil.ParseResult check = JsonRepairUtil.parse(raw);
        assertFalse(check.ok());
        String msg = check.error() == null ? "" : check.error();
        assertTrue(msg.contains("unmatched"), "error should describe the mismatch: " + msg);
    }

    @Test
    @DisplayName("Fully repaired truncation no longer produces an error")
    void truncatedNowRepairs() {
        String raw = "{\"score\":8,\"items\":[{\"a\":1";
        JsonRepairUtil.ParseResult check = JsonRepairUtil.parse(raw);
        assertTrue(check.ok(), "truncated object should now be repairable");
        assertEquals(8, check.node().get("score").asInt());
    }
}
