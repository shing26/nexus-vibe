package com.nexus.campus.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.util.ShowcasePostId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Puts one already-reviewed post in front of a visitor who has not signed in.
 *
 * <p>Every other form of proof in this repository is behind a login or behind git:
 * the drill report, the load test, the ADRs. The product's actual claim — an LLM
 * review that fails closed, repairs malformed output and writes back a score — is
 * only observable by registering and publishing a post containing a code block.
 * With seeding off there was nothing to look at, so the most persuasive artifact
 * this project has was invisible to the person it was built for.</p>
 *
 * <p>The review this writes is a <em>recording</em>. The snippet was published once
 * through the ordinary publish path against the deployed stack, the pipeline wrote
 * back the comment and the {@code ai_review_log} row, and both were copied out
 * verbatim into {@code src/main/resources/showcase/}. Nothing here is hand-written
 * prose pretending to be model output: an earlier revision did exactly that, and it
 * shipped a landing page whose three findings fields rendered empty because the
 * fixture used {@code quality}/{@code security}/{@code suggestions} while
 * {@code AiReviewDetailService} reads {@code codeQuality}/{@code securityConcerns}/
 * {@code optimizationSuggestions}. Copying the real row is what makes that class of
 * mistake impossible rather than merely fixed.</p>
 *
 * <p>This is deliberately not {@code DEMO_SEED_ENABLED}. That switch means "fill the
 * site with sample users and content", and turning it on for a production deployment
 * would recreate exactly the ghost-author problem ADR-0008 removed. This seeder
 * writes <em>one</em> post, authored by an account that really exists, and only when
 * {@code campus.showcase.post-id} is set.</p>
 *
 * <p>It is insert-only and idempotent by primary key: a second start, a restart, or a
 * deleted showcase post followed by a restart all behave predictably. It never
 * updates a row it did not create, so a reviewer who edits the seed post keeps their
 * edit. That also means re-recording the fixture does not rewrite an already-seeded
 * deployment - delete the three rows first, which is what the drill does.</p>
 */
@Component
@Order(400)
public class ShowcasePostSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ShowcasePostSeeder.class);

    /** Distinctive and outside the range the snowflake generator issues. */
    static final long SHOWCASE_POST_ID = ShowcasePostId.DEFAULT;
    static final long SHOWCASE_COMMENT_ID = 900000000000000002L;
    static final long SHOWCASE_REVIEW_LOG_ID = 900000000000000003L;
    static final long AI_AGENT_USER_ID = 999L;

    static final String COMMENT_RESOURCE = "/showcase/recorded-review-comment.md";
    static final String RESULT_RESOURCE = "/showcase/recorded-review-result.json";

    /** Exactly what {@code AiReviewService.formatReviewComment} produced for this snippet. */
    static final String SHOWCASE_REVIEW_COMMENT = read(COMMENT_RESOURCE);

    /** Exactly the {@code result_json} column the same run wrote. */
    static final String SHOWCASE_REVIEW_RESULT_JSON = read(RESULT_RESOURCE);

    /**
     * Score and severity are read back out of the recording rather than declared next to it,
     * so the card, the comment and the log row cannot drift into disagreeing. The test suite
     * pins the remaining direction: the comment has to say the same number the JSON does.
     */
    private static final JsonNode RECORDING = parse(SHOWCASE_REVIEW_RESULT_JSON);
    static final int SHOWCASE_SCORE = RECORDING.path("score").asInt();
    static final String SHOWCASE_SEVERITY = RECORDING.path("severity").asText();

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final String datasourceUrl;
    private final String authorUsername;

    public ShowcasePostSeeder(JdbcTemplate jdbcTemplate,
                              @Value("${campus.showcase.post-id:}") String showcasePostId,
                              @Value("${spring.datasource.url:}") String datasourceUrl,
                              @Value("${campus.showcase.author-username:admin}") String authorUsername) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = ShowcasePostId.isConfigured(showcasePostId);
        this.datasourceUrl = datasourceUrl;
        this.authorUsername = authorUsername;
    }

    @Override
    public void run(String... args) {
        seed();
    }

    void seed() {
        if (!enabled) {
            log.debug("[SHOWCASE] No showcase post configured, nothing to seed.");
            return;
        }
        if (!datasourceUrl.startsWith("jdbc:mysql")) {
            log.debug("[SHOWCASE] Non-MySQL datasource ({}), skipping.", datasourceUrl);
            return;
        }
        if (exists("SELECT COUNT(*) FROM vibe_post WHERE id = ?", SHOWCASE_POST_ID)) {
            log.debug("[SHOWCASE] Showcase post {} already present.", SHOWCASE_POST_ID);
            return;
        }
        Long authorId = lookupAuthorId();
        if (authorId == null) {
            log.warn("[SHOWCASE] Author '{}' does not exist, so the showcase post is skipped. "
                    + "The landing page entry only renders when the post is present.",
                    authorUsername);
            return;
        }
        insertPost(authorId);
        insertReviewComment();
        insertReviewLog();
        log.info("[SHOWCASE] Seeded showcase post {} for author '{}' with the recorded {}/10 "
                        + "severity {} review.",
                SHOWCASE_POST_ID, authorUsername, SHOWCASE_SCORE, SHOWCASE_SEVERITY);
    }

    private Long lookupAuthorId() {
        var ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ? LIMIT 1", Long.class, authorUsername);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean exists(String sql, Long argument) {
        Integer count = argument == null
                ? jdbcTemplate.queryForObject(sql, Integer.class)
                : jdbcTemplate.queryForObject(sql, Integer.class, argument);
        return count != null && count > 0;
    }

    private void insertPost(Long authorId) {
        jdbcTemplate.update("""
                INSERT INTO vibe_post (id, user_id, category_id, title, content, summary,
                                       view_count, like_count, comment_count, status, is_pinned,
                                       code_snippets, ai_reviewed, ai_review_score, token_count,
                                       post_type, create_time)
                VALUES (?, ?, 6, ?, ?, ?, 0, 0, 1, 1, 0, ?, 1, ?, 0, 'post', NOW())
                """,
                SHOWCASE_POST_ID,
                authorId,
                "Showcase: reviewing an unbounded cache with a stampede window",
                SHOWCASE_CONTENT,
                "The post that the review below is about: an in-process cache that never expires, "
                        + "refreshes everything at once, and is not safe when a key misses.",
                SHOWCASE_CODE_SNIPPETS,
                SHOWCASE_SCORE);
    }

    private void insertReviewComment() {
        jdbcTemplate.update("""
                INSERT INTO vibe_comment (id, post_id, user_id, parent_id, target_id, content, status, create_time)
                VALUES (?, ?, ?, 0, 0, ?, 1, NOW())
                """,
                SHOWCASE_COMMENT_ID, SHOWCASE_POST_ID, AI_AGENT_USER_ID, SHOWCASE_REVIEW_COMMENT);
    }

    private void insertReviewLog() {
        jdbcTemplate.update("""
                INSERT INTO ai_review_log (id, post_id, reviewer, result_json, severity, is_approved, created_at)
                VALUES (?, ?, 'code-review-agent', ?, ?, 0, NOW())
                """,
                SHOWCASE_REVIEW_LOG_ID, SHOWCASE_POST_ID, SHOWCASE_REVIEW_RESULT_JSON, SHOWCASE_SEVERITY);
    }

    static final String SHOWCASE_CODE_SNIPPETS =
            "[{\"language\":\"java\",\"code\":\"Map<String, Value> cache = new HashMap<>();\\n\\n"
                    + "Value get(String key) {\\n"
                    + "    Value hit = cache.get(key);\\n"
                    + "    if (hit == null) {\\n"
                    + "        hit = load(key);\\n"
                    + "        cache.put(key, hit);\\n"
                    + "    }\\n"
                    + "    return hit;\\n"
                    + "}\\n\\n"
                    + "void refreshAll() {\\n"
                    + "    cache.clear();\\n"
                    + "    for (String key : registry.keys()) {\\n"
                    + "        cache.put(key, load(key));\\n"
                    + "    }\\n"
                    + "}\"}]";

    static final String SHOWCASE_CONTENT = """
            This cache started as a five-line optimisation and now holds every key the service \
            has ever been asked for. It never expires anything, it reloads the entire world on a \
            timer, and it has no opinion about two callers asking for the same missing key at the \
            same time.

            ```java
            Map<String, Value> cache = new HashMap<>();

            Value get(String key) {
                Value hit = cache.get(key);
                if (hit == null) {
                    hit = load(key);
                    cache.put(key, hit);
                }
                return hit;
            }

            void refreshAll() {
                cache.clear();
                for (String key : registry.keys()) {
                    cache.put(key, load(key));
                }
            }
            ```

            The review below is a recording, not a reconstruction: this snippet was published once \
            through the ordinary publish path and the pipeline wrote back the comment and the \
            review-log row shown here, both copied out verbatim. It is seeded so that a visitor who \
            has not signed in can read a real review before producing one. What it proves is what \
            the pipeline emitted for this snippet; it does not prove the model is reachable right \
            now - publish a post and see for yourself.
            """;

    private static String read(String resource) {
        try (InputStream stream = ShowcasePostSeeder.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Showcase recording missing from the classpath: "
                        + resource + ". It ships in src/main/resources/showcase/, so its absence "
                        + "means the build is incomplete rather than the deployment misconfigured.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Showcase recording could not be read: " + resource, e);
        }
    }

    private static JsonNode parse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("Showcase recording is not valid JSON.", e);
        }
    }
}
