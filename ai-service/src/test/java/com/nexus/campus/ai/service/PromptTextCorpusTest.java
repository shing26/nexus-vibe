package com.nexus.campus.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.config.AiServiceProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the service half of the prompt-drift corpus that
 * {@code scripts/prompt_parity.py} compares against the direct path's half.
 *
 * <p>The request is built the way {@code ServiceReviewer} builds it
 * (neutralized title and content, fenced blocks sent as snippets), so the
 * comparison measures the two real payloads rather than a synthetic call.</p>
 */
class PromptTextCorpusTest {

    private static final Pattern NONCE = Pattern.compile(
            "(---(?:BEGIN|END) (?:CODE|PROMPT|META) )[0-9a-f]{8}(---)");
    private static final Pattern FENCED_CODE = Pattern.compile(
            "```[a-zA-Z]*\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    @Test
    void writesNormalizedServicePromptsForTheSharedFixtures() throws IOException {
        List<Fixture> fixtures = loadFixtures();
        assertTrue(fixtures.size() >= 8,
                "acceptance #3 names at least eight fixtures; found " + fixtures.size());

        AiServiceProperties properties = new AiServiceProperties();
        properties.getService().setMaxContextTokens(12000);
        PromptFactory factory = new PromptFactory(properties);

        Path out = Path.of(System.getProperty("parity.out", "target/prompt-parity/service"));
        Files.createDirectories(out);

        for (Fixture fixture : fixtures) {
            boolean promptKind = "prompt".equals(fixture.kind);
            ReviewRequest request = new ReviewRequest(
                    "corpus:" + fixture.name,
                    "corpus",
                    1L,
                    fixture.kind,
                    PromptIsolation.neutralize(fixture.title),
                    PromptIsolation.neutralize(fixture.content),
                    promptKind ? List.of() : extractCodeBlocks(fixture.content),
                    new ReviewRequest.Budget(12000, 115_000));

            PromptFactory.Prompt prompt = factory.review(request);
            String system = normalize(prompt.system());
            String user = normalize(prompt.user());
            assertFalse(system.isBlank(), fixture.name + ": system prompt");
            assertFalse(user.isBlank(), fixture.name + ": user prompt");
            assertFalse(NONCE.matcher(system).find(), fixture.name + ": unnormalized system nonce");
            assertFalse(NONCE.matcher(user).find(), fixture.name + ": unnormalized user nonce");

            Files.writeString(out.resolve(fixture.name + ".txt"),
                    "### SYSTEM\n" + system + "\n### USER\n" + user + "\n",
                    StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String text) {
        return NONCE.matcher(text).replaceAll("$1NONCE$2");
    }

    private static List<String> extractCodeBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return blocks;
        }
        Matcher matcher = FENCED_CODE.matcher(content);
        while (matcher.find()) {
            blocks.add(matcher.group(1).trim());
        }
        return blocks;
    }

    private record Fixture(String name, String kind, String title, String content) {
    }

    private static List<Fixture> loadFixtures() throws IOException {
        Path path = Path.of("..", "benchmark", "parity", "review-fixtures.json");
        if (!Files.exists(path)) {
            path = Path.of("benchmark", "parity", "review-fixtures.json");
        }
        JsonNode root = new ObjectMapper()
                .readTree(Files.readString(path, StandardCharsets.UTF_8));
        List<Fixture> fixtures = new ArrayList<>();
        for (JsonNode node : root) {
            String content = node.path("content").asText();
            int repeatCount = node.path("repeatCount").asInt(0);
            if (repeatCount > 0) {
                content = content.replace("{{REPEAT}}",
                        node.path("repeatText").asText().repeat(repeatCount));
            }
            fixtures.add(new Fixture(
                    node.path("name").asText(),
                    node.path("kind").asText(),
                    node.path("title").asText(),
                    content));
        }
        return fixtures;
    }
}
