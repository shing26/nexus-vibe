package com.nexus.campus.config;

import com.nexus.campus.agent.FileLlmCallArchive;
import com.nexus.campus.agent.LlmCallArchive;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Off by default: writing prompts and responses to disk is an operator choice,
 * not a baseline. Exactly one archive bean exists, chosen from the bound
 * {@link CampusAiProperties}, so {@code LlmClient} never has to null-check and
 * the property name is not repeated in a second annotation.
 */
@Configuration
public class LlmArchiveConfig {

    @Bean
    public LlmCallArchive llmCallArchive(CampusAiProperties properties) {
        CampusAiProperties.Archive archive = properties.getArchive();
        if (archive.isEnabled()) {
            return new FileLlmCallArchive(Path.of(archive.getPath()));
        }
        return LlmCallArchive.NOOP;
    }
}
