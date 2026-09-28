package com.nexus.campus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring binds YAML paths positionally, so a mis-indented key is not a startup
 * failure - it is a property that silently stops resolving and falls back to
 * its default. This test reads the two files and asserts the paths the AI
 * pipeline actually binds, which is the failure mode a context-load test
 * cannot catch.
 */
class AiConfigPathsTest {

    @Test
    @DisplayName("Default profile binds review and safety under campus.ai, not inside each other")
    void defaultProfileBindsAiPaths() throws IOException {
        Map<String, Object> root = read("application.yml");

        assertThat(path(root, "campus", "ai", "review", "enabled")).isEqualTo(true);
        assertThat(path(root, "campus", "ai", "review", "lease-seconds")).isEqualTo(240);
        assertThat(path(root, "campus", "ai", "review", "max-attempts")).isEqualTo(5);
        assertThat(path(root, "campus", "ai", "review", "max-context-tokens")).isEqualTo(12000);
        assertThat(path(root, "campus", "ai", "safety", "enabled")).isEqualTo(true);
        assertThat(path(root, "campus", "ai", "archive", "enabled")).isEqualTo("${LLM_ARCHIVE_ENABLED:false}");
        assertThat(path(root, "campus", "ai", "archive", "path"))
                .isEqualTo("${LLM_ARCHIVE_PATH:scratch/llm-archive.jsonl}");
    }

    @Test
    @DisplayName("Prod profile exposes independent env-backed review and safety switches")
    void prodProfileBindsAiPaths() throws IOException {
        Map<String, Object> root = read("application-prod.yml");

        assertThat(path(root, "campus", "ai", "review", "enabled")).isEqualTo("${AI_REVIEW_ENABLED:true}");
        assertThat(path(root, "campus", "ai", "review", "lease-seconds")).isEqualTo("${AI_LEASE_SECONDS:240}");
        assertThat(path(root, "campus", "ai", "review", "max-attempts")).isEqualTo("${AI_MAX_ATTEMPTS:5}");
        assertThat(path(root, "campus", "ai", "review", "max-context-tokens"))
                .isEqualTo("${AI_MAX_CONTEXT_TOKENS:12000}");
        assertThat(path(root, "campus", "ai", "safety", "enabled")).isEqualTo("${AI_SAFETY_ENABLED:true}");
        assertThat(path(root, "campus", "ai", "archive", "enabled"))
                .isEqualTo("${LLM_ARCHIVE_ENABLED:false}");
        assertThat(path(root, "campus", "ai", "archive", "path"))
                .isEqualTo("${LLM_ARCHIVE_PATH:scratch/llm-archive.jsonl}");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String name) throws IOException {
        try (InputStream in = AiConfigPathsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("classpath resource " + name).isNotNull();
            Map<String, Object> merged = new LinkedHashMap<>();
            Yaml yaml = new Yaml();
            for (Object document : yaml.loadAll(in)) {
                if (document instanceof Map<?, ?> map) {
                    merged.putAll((Map<String, Object>) map);
                }
            }
            return merged;
        }
    }

    private static Object path(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            assertThat(current).as("path segment " + key).isInstanceOf(Map.class);
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }
}
