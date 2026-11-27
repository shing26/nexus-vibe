package com.nexus.campus.ai.config;

import com.nexus.campus.ai.model.CallArchive;
import com.nexus.campus.ai.model.FileCallArchive;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ArchiveConfig {

    /**
     * Blank path disables the archive. The file is opt-in because the operator owns
     * rotation, and a container without a writable mount should still start.
     */
    @Bean
    public CallArchive callArchive(AiServiceProperties properties) {
        String path = properties.getLlm().getArchive();
        return path == null || path.isBlank()
                ? CallArchive.NOOP
                : new FileCallArchive(Path.of(path.trim()));
    }
}
