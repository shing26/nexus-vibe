package com.nexus.campus.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The archive is the "what did we actually send" record a disagreement is
 * settled with, so it is JSONL (one call per line, greppable, append-only) and
 * it never takes the LLM call down with it when the disk says no.
 */
class FileLlmCallArchiveTest {

    @TempDir
    Path tempDir;

    private LlmCallRecord record(String outcome, String response) {
        return new LlmCallRecord(
                "2026-09-19T12:00:00Z",
                "chat completion",
                "qwen2.5:7b",
                "{\"model\":\"qwen2.5:7b\",\"messages\":[{\"content\":\"raw request\"}]}",
                response,
                outcome,
                "failure".equals(outcome) ? "connection refused" : null);
    }

    @Test
    @DisplayName("One JSON line per call, raw request and raw response included")
    void writesOneJsonLinePerCall() throws IOException {
        Path file = tempDir.resolve("nested").resolve("llm-archive.jsonl");
        FileLlmCallArchive archive = new FileLlmCallArchive(file);

        archive.record(record("success", "{\"choices\":[{\"message\":{\"content\":\"raw response\"}}]}"));
        archive.record(record("failure", null));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0))
                .contains("\"operation\":\"chat completion\"")
                .contains("\"model\":\"qwen2.5:7b\"")
                .contains("raw request")
                .contains("raw response")
                .contains("\"outcome\":\"success\"");
        assertThat(lines.get(1))
                .contains("\"outcome\":\"failure\"")
                .contains("connection refused");
    }

    @Test
    @DisplayName("Appending to an existing file keeps earlier calls")
    void appendsWithoutRewriting() throws IOException {
        Path file = tempDir.resolve("llm-archive.jsonl");
        FileLlmCallArchive archive = new FileLlmCallArchive(file);

        archive.record(record("success", "first"));
        archive.record(record("success", "second"));

        assertThat(Files.readAllLines(file)).hasSize(2).allSatisfy(line -> assertThat(line).contains("success"));
    }

    @Test
    @DisplayName("An unwritable path is logged, not thrown, so the LLM call survives")
    void unwritablePathIsSwallowed() throws IOException {
        // A regular file where a directory needs to be: createDirectories fails.
        Path blocker = Files.createFile(tempDir.resolve("blocker"));
        FileLlmCallArchive archive = new FileLlmCallArchive(blocker.resolve("llm-archive.jsonl"));

        assertThatCode(() -> archive.record(record("success", "unreachable")))
                .doesNotThrowAnyException();
        assertThat(blocker).isRegularFile();
    }
}
