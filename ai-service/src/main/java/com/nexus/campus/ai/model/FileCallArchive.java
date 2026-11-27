package com.nexus.campus.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSONL archive: one line per provider attempt, so a disputed verdict can be
 * reconstructed with the exact text the model saw and returned.
 */
@Slf4j
public class FileCallArchive implements CallArchive {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path path;

    public FileCallArchive(Path path) {
        this.path = path;
    }

    @Override
    public synchronized void record(Record record) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = MAPPER.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("[AI-ARCHIVE] Failed to append attempt to {}: {}", path, e.getMessage());
        }
    }
}
