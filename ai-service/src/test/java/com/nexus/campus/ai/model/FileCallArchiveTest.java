package com.nexus.campus.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileCallArchiveTest {

    @Test
    void writesOneJsonLinePerAttempt(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("calls.jsonl");
        FileCallArchive archive = new FileCallArchive(file);

        archive.record(record("success", 12, 3));
        archive.record(record("error", 0, 0));

        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        JsonNode first = new ObjectMapper().readTree(lines.get(0));
        assertEquals("req-1", first.path("requestId").asText());
        assertEquals("success", first.path("outcome").asText());
        assertEquals(12, first.path("promptTokens").asInt());
        assertEquals("system text", first.path("system").asText());
    }

    @Test
    void anUnwritablePathIsSwallowed(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "x");
        FileCallArchive archive = new FileCallArchive(file.resolve("calls.jsonl"));

        assertDoesNotThrow(() -> archive.record(record("success", 1, 1)));
    }

    private static CallArchive.Record record(String outcome, int promptTokens, int completionTokens) {
        return new CallArchive.Record("2026-11-28T00:00:00Z", "req-1", "trace-1", "code", 1,
                "qwen2.5:3b", 0.2, 5, outcome, null, promptTokens, completionTokens,
                "system text", "user text", "response text");
    }
}
