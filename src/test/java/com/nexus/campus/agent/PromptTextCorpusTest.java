package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Renders the direct-mode half of the prompt-drift corpus that
 * {@code scripts/prompt_parity.py} compares against the AI service's half.
 *
 * <p>Both halves read {@code benchmark/parity/review-fixtures.json}, so the
 * comparison is over the same inputs. Per-request nonces are replaced with
 * {@code NONCE} before writing: they are random by design and would otherwise
 * make every fixture differ. The output goes to {@code target/}, not the
 * repository; the committed artifact is the drift report, not the prompts.</p>
 */
class PromptTextCorpusTest {

    private static final Pattern NONCE = Pattern.compile(
            "(---(?:BEGIN|END) (?:CODE|PROMPT|META) )[0-9a-f]{8}(---)");

    @Test
    void writesNormalizedDirectPromptsForTheSharedFixtures() throws IOException {
        List<Fixture> fixtures = loadFixtures();
        assertTrue(fixtures.size() >= 8,
                "acceptance #3 names at least eight fixtures; found " + fixtures.size());

        CampusAiProperties properties = new CampusAiProperties();
        LlmClient unusedClient = mock(LlmClient.class);
        CodeReviewReviewer code = new CodeReviewReviewer(unusedClient, properties);
        PromptReviewReviewer prompt = new PromptReviewReviewer(unusedClient, properties);

        Path out = Path.of(System.getProperty("parity.out", "target/prompt-parity/direct"));
        Files.createDirectories(out);

        for (Fixture fixture : fixtures) {
            VibePost post = new VibePost();
            post.setId(1L);
            post.setPostType(fixture.kind);
            post.setTitle(fixture.title);
            post.setContent(fixture.content);

            AbstractLlmReviewer.RequestParts parts = "prompt".equals(fixture.kind)
                    ? prompt.buildRequest(post)
                    : code.buildRequest(post);

            String system = normalize(parts.system());
            String user = normalize(parts.user());
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

    private record Fixture(String name, String kind, String title, String content) {
    }

    private static List<Fixture> loadFixtures() throws IOException {
        Path path = Path.of("benchmark", "parity", "review-fixtures.json");
        if (!Files.exists(path)) {
            path = Path.of("..").resolve(path);
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
