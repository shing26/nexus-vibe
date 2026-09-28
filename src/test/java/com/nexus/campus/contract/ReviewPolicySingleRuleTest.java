package com.nexus.campus.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0012 publishes exactly one trigger rule. The three call sites that used
 * to restate it are gone; this scan fails if one comes back, the same way
 * {@code NoEntityInControllerTest} fails when an entity leaks into a handler.
 */
class ReviewPolicySingleRuleTest {

    private static final List<Path> CALL_SITES_TO_PROTECT = List.of(
            Paths.get("src", "main", "java", "com", "nexus", "campus", "agent", "AiReviewEventListener.java"),
            Paths.get("src", "main", "java", "com", "nexus", "campus", "service", "impl", "VibePostServiceImpl.java"));

    @Test
    @DisplayName("The code-block trigger rule lives only behind ReviewPolicy")
    void triggerRuleIsNotRestatedAtTheCallSites() throws IOException {
        assertThat(CALL_SITES_TO_PROTECT).allSatisfy(path -> assertThat(Files.exists(path)).isTrue());

        List<String> offenders = new ArrayList<>();
        for (Path path : CALL_SITES_TO_PROTECT) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("detectCodeBlocks")) {
                    offenders.add(path.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("ReviewPolicy is a real bean with a replaceable default")
    void reviewPolicyDefaultIsBeanWired() throws IOException {
        Path config = Paths.get("src", "main", "java", "com", "nexus", "campus",
                "config", "ReviewPolicyConfig.java");
        String source = Files.readString(config, StandardCharsets.UTF_8);

        assertThat(source).contains("ConditionalOnMissingBean");
        assertThat(source).contains("ReviewPolicy");

        try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java"))) {
            List<String> namedImplementations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return path + "\n" + Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .filter(sourceText -> sourceText.contains("implements ReviewPolicy"))
                    .toList();
            assertThat(namedImplementations).hasSize(1);
        }
    }
}
