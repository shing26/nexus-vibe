package com.nexus.campus.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRepairTest {

    @Test
    void parsesFencedJson() {
        JsonRepair.ParseResult result = JsonRepair.parse(
                "Here you go:\n```json\n{\"score\": 8, \"severity\": \"low\"}\n```");
        assertTrue(result.ok());
        assertEquals(8, result.node().path("score").asInt());
    }

    @Test
    void stripsTrailingComma() {
        JsonRepair.ParseResult result = JsonRepair.parse("{\"a\": 1, \"b\": 2,}");
        assertTrue(result.ok());
        assertEquals(2, result.node().path("b").asInt());
    }

    @Test
    void completesTruncatedObject() {
        JsonRepair.ParseResult result = JsonRepair.parse(
                "{\"score\": 8, \"severity\": \"low\", \"codeQuality\": \"a long");
        assertTrue(result.ok());
        assertEquals(8, result.node().path("score").asInt());
    }

    @Test
    void reportsEmptyOutput() {
        JsonRepair.ParseResult result = JsonRepair.parse("  ");
        assertFalse(result.ok());
        assertEquals("empty response", result.error());
    }
}
