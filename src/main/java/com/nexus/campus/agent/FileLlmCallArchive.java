package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSONL archive: one line per provider attempt, so a call can be
 * replayed by grepping the timestamp or operation without loading the file.
 *
 * <p>Every failure is caught and logged. `synchronized` keeps concurrent
 * review threads from interleaving a partial line; the lock is per instance,
 * which is enough because Spring creates one bean.</p>
 */
@Slf4j
public class FileLlmCallArchive implements LlmCallArchive {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path path;

    public FileLlmCallArchive(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void record(LlmCallRecord record) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = MAPPER.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("[NEXUS-LLM-ARCHIVE] Failed to append record to {}: {}", path, e.getMessage());
        }
    }
}
