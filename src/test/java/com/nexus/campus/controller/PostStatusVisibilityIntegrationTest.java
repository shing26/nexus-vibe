package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.repository.AiReviewLogRepository;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The author's own profile used to hide the posts the product itself was holding: the
 * repository filtered {@code status = 1}, so a pending or rejected post simply vanished
 * from the person who wrote it. This test pins the replacement contract (A1-A4, A6, A8 in
 * {@code docs/plans/fe-3-post-status-visibility.md}): the owner sees the held rows with
 * their state, and nobody else sees anything new.
 *
 * <p>Fixtures: seed user 2 (shing) owns post 1 (active, and already carrying a
 * safety-check log) and post 101 (active). Three held posts are inserted here because the
 * seed data has no rejected row; post 1's existing safety log is the case that proves an
 * active row does not borrow a reason.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class PostStatusVisibilityIntegrationTest {

    private static final String POSTS_URL = "/api/v1/posts";

    private static final long OWNER_ID = 2L;
    private static final long OTHER_USER_ID = 3L;

    private static final long PENDING_POST_ID = 201L;
    private static final long REJECTED_SPAM_POST_ID = 202L;
    private static final long REJECTED_HARMFUL_POST_ID = 203L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private VibePostRepository postRepository;

    @Autowired
    private AiReviewLogRepository reviewLogRepository;

    @BeforeEach
    void insertHeldPosts() {
        LocalDateTime now = LocalDateTime.now();

        insertHeldPost(PENDING_POST_ID, "Held pending post", 2, now.minusMinutes(10));
        insertHeldPost(REJECTED_SPAM_POST_ID, "Held rejected spam post", 3, now.minusMinutes(20));
        insertHeldPost(REJECTED_HARMFUL_POST_ID, "Held rejected harmful post", 3, now.minusMinutes(30));

        insertSafetyLog(REJECTED_SPAM_POST_ID, "spam detected in generated content");
        insertSafetyLog(REJECTED_HARMFUL_POST_ID, "harmful content detected");
        // The pending row deliberately has no safety log: it must not borrow a neighbour's.
    }

    // ---------------------------------------------------------------- A1

    @Test
    @DisplayName("A1: the owner's own listing contains active, pending and rejected rows")
    void ownerListing_containsHeldRowsWithTheirStatus() throws Exception {
        JsonNode data = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");

        assertThat(ids(data)).contains(1L, 101L, PENDING_POST_ID, REJECTED_SPAM_POST_ID, REJECTED_HARMFUL_POST_ID);
        assertThat(statuses(data)).containsExactlyInAnyOrder(1, 1, 2, 3, 3);
        assertThat(statusOf(data, PENDING_POST_ID)).isEqualTo(2);
        assertThat(statusOf(data, REJECTED_SPAM_POST_ID)).isEqualTo(3);
        assertThat(statusOf(data, REJECTED_HARMFUL_POST_ID)).isEqualTo(3);
    }

    // ---------------------------------------------------------------- A2

    @Test
    @DisplayName("A2: an anonymous reader of the same URL gets only active rows")
    void anonymousListing_onlyActiveRows() throws Exception {
        JsonNode data = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20"))
                .get("data");

        assertThat(ids(data)).containsExactly(1L, 101L);
        assertThat(statuses(data)).containsOnly(1);
        assertThat(total(data)).isEqualTo(2);
    }

    @Test
    @DisplayName("A2: another signed-in user gets the same active-only page the old filter returned")
    void otherUserListing_matchesThePublicPage() throws Exception {
        JsonNode anonymous = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20"))
                .get("data");
        JsonNode asOtherUser = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20")
                .header("Authorization", bearer(OTHER_USER_ID)))
                .get("data");

        assertThat(ids(asOtherUser)).containsExactly(1L, 101L);
        assertThat(statuses(asOtherUser)).containsOnly(1);
        assertThat(ids(asOtherUser)).isEqualTo(ids(anonymous));
        assertThat(total(asOtherUser)).isEqualTo(total(anonymous));
    }

    // ---------------------------------------------------------------- A3

    @Test
    @DisplayName("A3: each rejected row carries the reason from its own latest safety log")
    void rejectedRows_carryTheirOwnReason() throws Exception {
        JsonNode data = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");

        assertThat(reasonOf(data, REJECTED_SPAM_POST_ID)).isEqualTo("Spam");
        assertThat(reasonOf(data, REJECTED_HARMFUL_POST_ID)).isEqualTo("Harmful content");
        assertThat(reasonOf(data, PENDING_POST_ID)).isNull();
    }

    // ---------------------------------------------------------------- A4

    @Test
    @DisplayName("A4: no non-owner row carries a reason")
    void nonOwnerRows_carryNoReason() throws Exception {
        JsonNode anonymous = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20"))
                .get("data");
        JsonNode asOtherUser = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20")
                .header("Authorization", bearer(OTHER_USER_ID)))
                .get("data");

        assertEveryRowHasNullReason(anonymous);
        assertEveryRowHasNullReason(asOtherUser);
    }

    @Test
    @DisplayName("A4: active rows carry no reason even in the owner's own view")
    void ownerActiveRows_carryNoReason() throws Exception {
        JsonNode data = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("size", "20")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");

        // Post 1 has a safety log ('Prompt injection') and is active: status 1 never explains
        // itself, or every post whose publish gate logged a check would look rejected.
        assertThat(reasonOf(data, 1L)).isNull();
        assertThat(reasonOf(data, 101L)).isNull();
    }

    // ---------------------------------------------------------------- A6

    @Test
    @DisplayName("A6: feed, category, search and hot listing still exclude held rows")
    void publicSurfaces_doNotGainHeldRows() throws Exception {
        JsonNode feed = body(get(POSTS_URL).param("size", "50")).get("data");
        JsonNode category = body(get(POSTS_URL).param("categoryId", "4").param("size", "50")).get("data");
        JsonNode search = body(get(POSTS_URL).param("keyword", "Held").param("size", "50")).get("data");
        JsonNode hot = body(get(POSTS_URL).param("hot", "true").param("size", "50")).get("data");

        for (JsonNode data : List.of(feed, category, search, hot)) {
            assertThat(ids(data)).doesNotContain(
                    PENDING_POST_ID, REJECTED_SPAM_POST_ID, REJECTED_HARMFUL_POST_ID);
            assertEveryRowHasNullReason(data);
        }
        assertThat(ids(category)).contains(1L);
        assertThat(ids(search)).isEmpty();
        assertThat(statuses(feed)).containsOnly(1);
        assertThat(statuses(hot)).containsOnly(1);
    }

    // ---------------------------------------------------------------- A8

    @Test
    @DisplayName("A8: the owner's total counts held rows, so page 2 is reachable")
    void ownerPagination_countsHeldRows() throws Exception {
        JsonNode page1 = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("page", "1")
                .param("size", "2")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");
        JsonNode page2 = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("page", "2")
                .param("size", "2")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");
        JsonNode page3 = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("page", "3")
                .param("size", "2")
                .header("Authorization", bearer(OWNER_ID)))
                .get("data");

        assertThat(total(page1)).isEqualTo(5);
        assertThat(page1.get("pages").asInt()).isEqualTo(3);
        assertThat(ids(page1)).hasSize(2);
        assertThat(ids(page2)).hasSize(2);
        assertThat(ids(page2)).doesNotContainAnyElementsOf(ids(page1));
        assertThat(ids(page3)).hasSize(1);

        Set<Long> acrossPages = new LinkedHashSet<>(ids(page1));
        acrossPages.addAll(ids(page2));
        acrossPages.addAll(ids(page3));
        assertThat(acrossPages).containsExactlyInAnyOrder(
                1L, 101L, PENDING_POST_ID, REJECTED_SPAM_POST_ID, REJECTED_HARMFUL_POST_ID);
    }

    @Test
    @DisplayName("A8: the public total stays at the active count")
    void publicPagination_countsActiveRowsOnly() throws Exception {
        JsonNode data = body(get(POSTS_URL)
                .param("userId", String.valueOf(OWNER_ID))
                .param("page", "1")
                .param("size", "2"))
                .get("data");

        assertThat(total(data)).isEqualTo(2);
        assertThat(data.get("pages").asInt()).isEqualTo(1);
        assertThat(ids(data)).containsExactly(1L, 101L);
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private void insertHeldPost(long id, String title, int postStatus, LocalDateTime createTime) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setUserId(OWNER_ID);
        post.setCategoryId(4);
        post.setTitle(title);
        post.setContent("Held by the review pipeline for the status-visibility test.");
        post.setSummary("Held by the review pipeline.");
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(postStatus);
        post.setIsPinned(0);
        post.setCreateTime(createTime);
        postRepository.insert(post);
    }

    private void insertSafetyLog(long postId, String resultJson) {
        AiReviewLog log = new AiReviewLog();
        log.setPostId(postId);
        log.setReviewer("safety-check-agent");
        log.setResultJson(resultJson);
        log.setSeverity("high");
        log.setIsApproved(0);
        reviewLogRepository.insert(log);
    }

    private String bearer(long userId) {
        return "Bearer " + jwtUtil.generateToken(userId, "user" + userId, "USER");
    }

    private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).as("envelope code: %s", body).isEqualTo(200);
        return body;
    }

    private List<Long> ids(JsonNode data) {
        List<Long> ids = new ArrayList<>();
        data.get("list").forEach(row -> ids.add(Long.parseLong(row.get("id").asText())));
        return ids;
    }

    private List<Integer> statuses(JsonNode data) {
        List<Integer> statuses = new ArrayList<>();
        data.get("list").forEach(row -> statuses.add(row.get("status").asInt()));
        return statuses;
    }

    private long total(JsonNode data) {
        // Long is serialised as a string so snowflake ids survive JavaScript.
        return Long.parseLong(data.get("total").asText());
    }

    private Integer statusOf(JsonNode data, long postId) {
        JsonNode row = rowOf(data, postId);
        assertThat(row).as("post %s is in the listing", postId).isNotNull();
        return row.get("status").asInt();
    }

    private String reasonOf(JsonNode data, long postId) {
        JsonNode row = rowOf(data, postId);
        assertThat(row).as("post %s is in the listing", postId).isNotNull();
        assertThat(row.has("rejectionReason"))
                .as("the nullable field is declared on every row, so its absence is not the assertion")
                .isTrue();
        JsonNode reason = row.get("rejectionReason");
        return reason.isNull() ? null : reason.asText();
    }

    private JsonNode rowOf(JsonNode data, long postId) {
        for (JsonNode row : data.get("list")) {
            if (Long.parseLong(row.get("id").asText()) == postId) {
                return row;
            }
        }
        return null;
    }

    private void assertEveryRowHasNullReason(JsonNode data) {
        for (JsonNode row : data.get("list")) {
            assertThat(row.has("rejectionReason"))
                    .as("row %s declares rejectionReason", row.get("id").asText())
                    .isTrue();
            assertThat(row.get("rejectionReason").isNull())
                    .as("row %s leaks no rejection reason", row.get("id").asText())
                    .isTrue();
        }
    }
}
