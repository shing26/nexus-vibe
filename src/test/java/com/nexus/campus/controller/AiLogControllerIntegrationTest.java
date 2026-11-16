package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.mapper.VibePostMapper;
import com.nexus.campus.service.UserProfileSummaryService;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class AiLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private VibePostMapper vibePostMapper;

    @Autowired
    private UserProfileSummaryService userProfileSummaryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET latest code review should return parsed detail from result_json")
    void getLatestCodeReview_shouldReturnParsedDetail() throws Exception {
        mockMvc.perform(get("/api/v1/agent-logs/post/100/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.postId", is("100")))
                .andExpect(jsonPath("$.data.reviewer", is("code-review-agent")))
                .andExpect(jsonPath("$.data.score", is(9)))
                .andExpect(jsonPath("$.data.severity", is("low")))
                .andExpect(jsonPath("$.data.isApproved", is(true)))
                .andExpect(jsonPath("$.data.codeQuality", is("")))
                .andExpect(jsonPath("$.data.securityConcerns", is("")))
                .andExpect(jsonPath("$.data.optimizationSuggestions", is("")))
                .andExpect(jsonPath("$.data.reviewedAt", notNullValue()));
    }

    @Test
    @DisplayName("GET latest code review should return null data when no code-review log exists")
    void getLatestCodeReview_withoutLog_shouldReturnNullData() throws Exception {
        mockMvc.perform(get("/api/v1/agent-logs/post/1/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    @DisplayName("GET latest code review should tolerate malformed legacy JSON without a 500")
    void getLatestCodeReview_malformedLegacyJson_shouldReturnDefaults() throws Exception {
        AiReviewLog log = new AiReviewLog();
        log.setPostId(4L);
        log.setReviewer("code-review-agent");
        log.setResultJson("not-json");
        log.setSeverity("low");
        log.setIsApproved(0);
        aiReviewLogMapper.insert(log);

        mockMvc.perform(get("/api/v1/agent-logs/post/4/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.postId", is("4")))
                .andExpect(jsonPath("$.data.score", nullValue()))
                .andExpect(jsonPath("$.data.severity", is("low")))
                .andExpect(jsonPath("$.data.isApproved", is(false)))
                .andExpect(jsonPath("$.data.codeQuality", is("")))
                .andExpect(jsonPath("$.data.securityConcerns", is("")))
                .andExpect(jsonPath("$.data.optimizationSuggestions", is("")));
    }

    @Test
    @DisplayName("GET latest code review exposes the optional durationMs instrument")
    void getLatestCodeReview_shouldExposeDurationWhenPresent() throws Exception {
        AiReviewLog log = new AiReviewLog();
        log.setPostId(3L);
        log.setReviewer("code-review-agent");
        log.setResultJson("{\"score\":8,\"severity\":\"low\",\"durationMs\":1234}");
        log.setSeverity("low");
        log.setIsApproved(1);
        aiReviewLogMapper.insert(log);

        mockMvc.perform(get("/api/v1/agent-logs/post/3/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMs", is(1234)));
    }

    @Test
    @DisplayName("GET agent log list without token should return 401")
    void listLogsWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/agent-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("GET agent log list with a regular user should return 403")
    void listLogsWithRegularUser_shouldReturn403() throws Exception {
        String token = jwtUtil.generateToken(2L, "shing", "USER");
        mockMvc.perform(get("/api/v1/agent-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    @DisplayName("GET agent log list with an admin should return logs")
    void listLogsWithAdmin_shouldReturnLogs() throws Exception {
        String token = jwtUtil.generateToken(1L, "admin", "ADMIN");
        mockMvc.perform(get("/api/v1/agent-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list", is(not(empty()))));
    }

    @Test
    @DisplayName("GET agent log list should expose review status derived from result_json")
    void listLogs_shouldExposeReviewStatus() throws Exception {
        AiReviewLog unavailableLog = new AiReviewLog();
        unavailableLog.setPostId(3L);
        unavailableLog.setReviewer("code-review-agent");
        unavailableLog.setResultJson(null);
        unavailableLog.setSeverity("unavailable");
        unavailableLog.setIsApproved(0);
        aiReviewLogMapper.insert(unavailableLog);

        String token = jwtUtil.generateToken(1L, "admin", "ADMIN");
        mockMvc.perform(get("/api/v1/agent-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list[0].status", is("unavailable")));
    }

    @Test
    @DisplayName("GET public agent ticker does not require authentication")
    void getTicker_withoutToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/agent-logs/ticker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("GET user review index is public and returns the latest review per post, newest first")
    void getUserReviewIndex_shouldReturnLatestReviewPerPostNewestFirst() throws Exception {
        // user 3 (alice) owns post 2 (seeded review, score 5) and post 102.
        // A newer review supersedes the seeded post-2 row; post 102 gets two
        // rows so only the newer one may surface.
        AiReviewLog post2Newest = insertReview(2L, "{\"score\":6,\"severity\":\"low\"}",
                LocalDateTime.now().minusHours(10));
        insertReview(102L, "{\"score\":7,\"severity\":\"low\"}",
                LocalDateTime.now().minusHours(6));
        AiReviewLog post102Newest = insertReview(102L, "{\"score\":8,\"severity\":\"medium\"}",
                LocalDateTime.now().minusHours(2));

        MvcResult result = mockMvc.perform(get("/api/v1/agent-logs/user/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].logId", is(String.valueOf(post102Newest.getId()))))
                .andExpect(jsonPath("$.data[0].postId", is("102")))
                .andExpect(jsonPath("$.data[0].postTitle", is("React Component Generator")))
                .andExpect(jsonPath("$.data[0].score", is(8)))
                .andExpect(jsonPath("$.data[0].severity", is("medium")))
                .andExpect(jsonPath("$.data[0].isApproved", is(true)))
                .andExpect(jsonPath("$.data[1].logId", is(String.valueOf(post2Newest.getId()))))
                .andExpect(jsonPath("$.data[1].postId", is("2")))
                .andExpect(jsonPath("$.data[1].score", is(6)))
                .andReturn();

        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertReviewedAtDescending(entries);
        assertThat(distinctPostIds(entries)).hasSize(2);
    }

    @Test
    @DisplayName("GET user review index skips posts that are not visible")
    void getUserReviewIndex_shouldSkipInvisiblePosts() throws Exception {
        // Post 5 is status 2 (pending audit), so its review never enters the
        // public index even though a code-review row exists.
        insertReview(5L, "{\"score\":1,\"severity\":\"high\"}", LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/v1/agent-logs/user/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].postId", is("2")))
                .andExpect(jsonPath("$.data[*].postId", not(hasItem("5"))));
    }

    @Test
    @DisplayName("GET user review index caps the index at 50 posts")
    void getUserReviewIndex_shouldCapAtFiftyEntries() throws Exception {
        List<Long> postIds = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            VibePost post = insertPost(6L, "Cap test post " + i, 1);
            postIds.add(post.getId());
            insertReview(post.getId(), "{\"score\":" + (i % 10) + ",\"severity\":\"low\"}",
                    LocalDateTime.now().minusMinutes(i));
        }

        MvcResult result = mockMvc.perform(get("/api/v1/agent-logs/user/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(50)))
                .andExpect(jsonPath("$.data[0].postId", is(String.valueOf(postIds.get(0)))))
                .andExpect(jsonPath("$.data[49].postId", is(String.valueOf(postIds.get(49)))))
                .andReturn();

        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(distinctPostIds(entries)).hasSize(50);
        assertThat(distinctPostIds(entries)).doesNotContain(postIds.get(50));
    }

    @Test
    @DisplayName("GET user review index returns an empty array for an unknown user")
    void getUserReviewIndex_unknownUser_shouldReturnEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/agent-logs/user/987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", empty()));
    }

    @Test
    @DisplayName("GET user review index is consistent with the profile summary's reviewed posts and average")
    void getUserReviewIndex_shouldMatchProfileSummaryAverage() throws Exception {
        // user 4 (bob) gets three visible reviewed posts and one invisible one.
        VibePost postA = insertPost(4L, "Consistency A", 1);
        VibePost postB = insertPost(4L, "Consistency B", 1);
        VibePost postC = insertPost(4L, "Consistency C", 1);
        VibePost hidden = insertPost(4L, "Consistency hidden", 2);
        insertReview(postA.getId(), "{\"score\":2,\"severity\":\"low\"}", LocalDateTime.now().minusHours(10));
        insertReview(postA.getId(), "{\"score\":9,\"severity\":\"low\"}", LocalDateTime.now().minusHours(6));
        insertReview(postB.getId(), "{\"score\":3,\"severity\":\"low\"}", LocalDateTime.now().minusHours(9));
        insertReview(postB.getId(), "{\"score\":6,\"severity\":\"low\"}", LocalDateTime.now().minusHours(4));
        insertReview(postC.getId(), "{\"score\":4,\"severity\":\"low\"}", LocalDateTime.now().minusHours(2));
        insertReview(hidden.getId(), "{\"score\":1,\"severity\":\"high\"}", LocalDateTime.now().minusHours(1));

        MvcResult result = mockMvc.perform(get("/api/v1/agent-logs/user/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].postId", is(String.valueOf(postC.getId()))))
                .andExpect(jsonPath("$.data[1].postId", is(String.valueOf(postB.getId()))))
                .andExpect(jsonPath("$.data[2].postId", is(String.valueOf(postA.getId()))))
                .andReturn();

        JsonNode entries = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int scored = 0;
        double total = 0;
        for (JsonNode entry : entries) {
            if (entry.hasNonNull("score")) {
                total += entry.path("score").asInt();
                scored++;
            }
        }

        // Independent recomputation from this endpoint's data.
        int reviewedPosts = distinctPostIds(entries).size();
        double indexAverage = total / scored;

        // What the profile itself reports from UserProfileSummaryService.
        Double profileAverage = userProfileSummaryService.getSummary(4L).getStats().getAvgAiScore();

        assertThat(reviewedPosts).isEqualTo(3);
        assertThat(scored).isEqualTo(reviewedPosts);
        assertThat(profileAverage).isNotNull();
        assertThat(indexAverage).isEqualTo(profileAverage);
    }

    private AiReviewLog insertReview(Long postId, String resultJson, LocalDateTime createdAt) {
        AiReviewLog log = new AiReviewLog();
        log.setPostId(postId);
        log.setReviewer("code-review-agent");
        log.setResultJson(resultJson);
        log.setSeverity("low");
        log.setIsApproved(1);
        log.setCreatedAt(createdAt);
        aiReviewLogMapper.insert(log);
        return log;
    }

    private VibePost insertPost(Long userId, String title, int status) {
        VibePost post = new VibePost();
        post.setUserId(userId);
        post.setCategoryId(2);
        post.setTitle(title);
        post.setContent("");
        post.setStatus(status);
        vibePostMapper.insert(post);
        return post;
    }

    private Set<Long> distinctPostIds(JsonNode entries) {
        Set<Long> postIds = new HashSet<>();
        for (JsonNode entry : entries) {
            postIds.add(entry.path("postId").asLong());
        }
        return postIds;
    }

    private void assertReviewedAtDescending(JsonNode entries) {
        LocalDateTime previous = null;
        for (JsonNode entry : entries) {
            LocalDateTime reviewedAt = LocalDateTime.parse(entry.path("reviewedAt").asText());
            if (previous != null) {
                assertThat(reviewedAt).isBeforeOrEqualTo(previous);
            }
            previous = reviewedAt;
        }
    }
}
