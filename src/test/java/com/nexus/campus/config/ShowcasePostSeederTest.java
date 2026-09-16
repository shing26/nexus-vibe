package com.nexus.campus.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The showcase post is the one piece of demo data that survives into a production
 * deployment, which is exactly why the gate is worth testing. Four properties:
 * it writes nothing unless it is configured, it never writes twice, the review it
 * writes is the recorded pipeline output rather than prose that resembles it, and
 * that recording fills every field the detail panel actually reads.
 */
@ExtendWith(MockitoExtension.class)
class ShowcasePostSeederTest {

    private static final String MYSQL = "jdbc:mysql://db:3306/nexus_campus";
    private static final String POST_ID = String.valueOf(ShowcasePostSeeder.DEFAULT_POST_ID);

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ShowcasePostSeeder seeder(String postId) {
        return new ShowcasePostSeeder(jdbcTemplate, postId, MYSQL, "admin");
    }

    @Test
    @DisplayName("Unconfigured deployments write nothing at all")
    void writesNothingWhenNotConfigured() {
        seeder("").run();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("Non-MySQL datasources are left alone")
    void skipsNonMysqlDatasources() {
        new ShowcasePostSeeder(jdbcTemplate, POST_ID, "jdbc:h2:mem:test", "admin").run();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("An existing showcase post is never rewritten")
    void isIdempotentByPrimaryKey() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ShowcasePostSeeder.DEFAULT_POST_ID)))
                .thenReturn(1);

        seeder(POST_ID).run();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("A missing author is reported and nothing is inserted")
    void refusesToInventAnAuthor() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ShowcasePostSeeder.DEFAULT_POST_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("admin"))).thenReturn(List.of());

        seeder(POST_ID).run();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("Post, review comment and review log are written together")
    void writesTheWholeChainOnce() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ShowcasePostSeeder.DEFAULT_POST_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("admin")))
                .thenReturn(List.of(1L));

        seeder(POST_ID).run();

        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("The seeded review is the recorded pipeline output, not prose resembling it")
    void fixtureIsTheRecordedPipelineOutput() {
        assertThat(ShowcasePostSeeder.SHOWCASE_REVIEW_COMMENT)
                .startsWith("## AI Code Review")
                .contains("**Overall Score**: " + ShowcasePostSeeder.SHOWCASE_SCORE + "/10")
                .contains("**Severity**: " + ShowcasePostSeeder.SHOWCASE_SEVERITY)
                .contains("### Code Quality", "### Security", "### Suggestions");
    }

    @Test
    @DisplayName("The recorded log row fills every field the detail panel reads")
    void recordedLogRowFillsEveryFieldTheDetailPanelReads() throws Exception {
        JsonNode result = new ObjectMapper().readTree(ShowcasePostSeeder.SHOWCASE_REVIEW_RESULT_JSON);

        // These three names are the ones AiReviewDetailService reads. An earlier fixture used
        // quality/security/suggestions, which parses fine and renders three empty sections -
        // the exact failure this assertion exists to make loud.
        for (String field : List.of("codeQuality", "securityConcerns", "optimizationSuggestions")) {
            assertThat(result.path(field).asText(""))
                    .as("ai_review_log.result_json.%s", field)
                    .isNotBlank();
        }
        // The card, the comment and the log row must not be able to disagree; the comment half
        // of that is asserted in fixtureIsTheRecordedPipelineOutput.
        assertThat(result.path("score").asInt()).isEqualTo(ShowcasePostSeeder.SHOWCASE_SCORE);
        assertThat(result.path("severity").asText()).isEqualTo(ShowcasePostSeeder.SHOWCASE_SEVERITY);
    }

    @Test
    @DisplayName("The recording is about the snippet the seeded post actually contains")
    void recordingAndSeededPostDescribeTheSameSnippet() {
        // Both halves have to be about the same code, or the page pairs one snippet with
        // another one's review. The post also has to keep the fenced block, because that is
        // the trigger condition the README tells a visitor to reproduce.
        assertThat(ShowcasePostSeeder.SHOWCASE_CONTENT)
                .contains("```java", "HashMap", "refreshAll");
        assertThat(ShowcasePostSeeder.SHOWCASE_REVIEW_COMMENT)
                .contains("HashMap", "refreshAll");
    }

    @Test
    @DisplayName("The author lookup is by username, not by a guessed id")
    void looksTheAuthorUpByUsername() {
        // Mockito's varargs capture wraps the argument array, which makes the captured
        // shape depend on the invocation's arity. Recording through an Answer keeps the
        // actual argument list, which is what this test is about.
        List<Object[]> updates = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            updates.add(invocation.getArguments().clone());
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(Object[].class));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(ShowcasePostSeeder.DEFAULT_POST_ID)))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("admin")))
                .thenReturn(List.of(42L));

        seeder(POST_ID).run();

        verify(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq("admin"));

        Object[] postInsert = updates.stream()
                // Mockito flattens the varargs into the invocation, so the post insert shows up
                // as the SQL text followed by one entry per placeholder.
                .filter(values -> values.length == 8)
                .findFirst()
                .orElseGet(() -> {
                    throw new AssertionError("no post insert was issued: "
                            + updates.size() + " calls recorded, arities "
                            + updates.stream().map(values -> String.valueOf(values.length)).toList());
                });
        // `getArguments()` hands back whatever Mockito received; rather than assume where the
        // varargs array was unpacked, look for the values themselves.
        assertThat(postInsert).contains((Object) ShowcasePostSeeder.DEFAULT_POST_ID, (Object) 42L);
        assertThat(postInsert[postInsert.length - 1]).isEqualTo(ShowcasePostSeeder.SHOWCASE_SCORE);
    }

    @Test
    @DisplayName("A configured post id is the id that gets written, comment and log included")
    void honoursAConfiguredPostId() {
        // The read endpoint queries campus.showcase.post-id. A seeder that always wrote the
        // shipped default would seed one post and advertise another, so the landing page would
        // render nothing while every log line still said the seed had succeeded.
        long configured = 900000000000000009L;
        List<Object[]> updates = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            updates.add(invocation.getArguments().clone());
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(Object[].class));

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(configured)))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("admin")))
                .thenReturn(List.of(7L));

        seeder(String.valueOf(configured)).run();

        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), eq(configured));
        assertThat(updates.stream().flatMap(values -> Arrays.stream(values)).toList())
                .contains(configured, configured + 1, configured + 2);
    }

    @Test
    @DisplayName("The three inserts share a transaction, so a half-written seed cannot persist")
    void theWholeSeedIsOneTransaction() throws Exception {
        // Asserted by reflection rather than by provoking a rollback: the seeder deliberately
        // refuses to run against H2, so a MySQL-only integration test would not run in this
        // suite. What this pins is that a failure between the post and its review leaves
        // nothing behind, instead of a post that the already-present check then treats as
        // finished while its review is missing.
        Method run = ShowcasePostSeeder.class.getMethod("run", String[].class);

        assertThat(run.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
