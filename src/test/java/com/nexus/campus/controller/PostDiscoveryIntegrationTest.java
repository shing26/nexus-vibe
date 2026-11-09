package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.entity.VibePost;
import com.nexus.campus.repository.VibePostRepository;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Item 4 ({@code /posts/unanswered}) and item 6 ({@code sort=liked}) from
 * {@code docs/plans/fe-3-group-a-feed-copy.md}.
 *
 * <p>Seed data gives five active zero-comment posts (4, 102, 101, 100, 3) with
 * distinct timestamps, so "newest first" is observable without inserting a
 * second fixture set. The ordering tests insert their own rows because the seed
 * has no like-count ties; every insertion is covered by the test transaction and
 * rolls back with it.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class PostDiscoveryIntegrationTest {

    private static final String POSTS_URL = "/api/v1/posts";
    private static final String COMMENTS_URL = "/api/v1/comments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private VibePostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtUtil.generateToken(2L, "shing", "USER");
    }

    // ---------------------------------------------------------------- item 4

    @Test
    @DisplayName("A4-1/A4-2: unanswered is public, active-only, zero-comment and newest first")
    void unanswered_returnsActiveZeroCommentPostsNewestFirst() throws Exception {
        JsonNode data = dataArray(get(POSTS_URL + "/unanswered"));

        assertThat(ids(data)).containsExactly(4L, 102L, 101L, 100L, 3L);
        assertThat(data).hasSize(5);
        for (JsonNode row : data) {
            assertThat(row.get("status").asInt()).as("row %s is active", row.get("id").asText())
                    .isEqualTo(1);
            assertThat(row.get("commentCount").asInt())
                    .as("row %s has no comments", row.get("id").asText())
                    .isZero();
        }
    }

    @Test
    @DisplayName("A4-1: size caps the list; the hard ceiling is 20")
    void unanswered_honoursSizeAndHardCeiling() throws Exception {
        JsonNode two = dataArray(get(POSTS_URL + "/unanswered").param("size", "2"));
        assertThat(ids(two)).containsExactly(4L, 102L);

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 21; i++) {
            insertPost(500L + i, 4, 0, 0, 1, now.minusSeconds(i + 1));
        }

        JsonNode capped = dataArray(get(POSTS_URL + "/unanswered").param("size", "100"));
        assertThat(capped).hasSize(20);
        // The newly inserted rows are the newest, so the cap takes the top of the list.
        assertThat(ids(capped).get(0)).isEqualTo(500L);
    }

    @Test
    @DisplayName("A4-2: no unanswered posts is an empty list, not an error")
    void unanswered_emptyWhenEveryPostHasAComment() throws Exception {
        jdbcTemplate.update("UPDATE vibe_post SET comment_count = 1");

        JsonNode data = dataArray(get(POSTS_URL + "/unanswered"));

        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("A4-3: a post leaves the list once it receives a comment")
    void unanswered_postLeavesAfterFirstComment() throws Exception {
        assertThat(ids(dataArray(get(POSTS_URL + "/unanswered")))).contains(4L);

        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(4L);
        request.setContent("A first reply that takes this post off the waiting list.");
        mockMvc.perform(post(COMMENTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        JsonNode after = dataArray(get(POSTS_URL + "/unanswered"));
        assertThat(ids(after)).doesNotContain(4L);
        assertThat(after).hasSize(4);
    }

    // ---------------------------------------------------------------- item 6

    @Test
    @DisplayName("A6-1: sort=liked is a total order (likes, then time, then id) across pages")
    void likedSort_ordersByLikesThenTimeThenId() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // Three rows with identical likes and two with identical timestamps: only the
        // full tie-break chain produces a stable order.
        insertPost(301L, 4, 50, 1, 1, now.minusMinutes(10));
        insertPost(302L, 4, 50, 1, 1, now.minusMinutes(5));
        insertPost(303L, 4, 50, 1, 1, now.minusMinutes(5));
        // Held row with the highest like count: the status filter must win.
        insertPost(304L, 4, 999, 1, 2, now);

        JsonNode page1 = body(get(POSTS_URL)
                .param("channelSlug", "agents")
                .param("sort", "liked")
                .param("page", "1")
                .param("size", "2")).get("data");
        JsonNode page2 = body(get(POSTS_URL)
                .param("channelSlug", "agents")
                .param("sort", "liked")
                .param("page", "2")
                .param("size", "2")).get("data");
        JsonNode page3 = body(get(POSTS_URL)
                .param("channelSlug", "agents")
                .param("sort", "liked")
                .param("page", "3")
                .param("size", "2")).get("data");

        // 3:620, 1:85, then the tie group newest-first with the higher id first.
        assertThat(ids(page1)).containsExactly(3L, 1L);
        assertThat(ids(page2)).containsExactly(303L, 302L);
        assertThat(ids(page3)).containsExactly(301L);
        assertThat(ids(page1)).doesNotContain(304L);
        assertThat(page1.get("total").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("A6-1: the same ordering composes with categoryId as the scope")
    void likedSort_composesWithCategoryId() throws Exception {
        insertPost(301L, 4, 50, 1, 1, LocalDateTime.now().minusMinutes(10));
        insertPost(302L, 4, 51, 1, 1, LocalDateTime.now().minusMinutes(5));

        JsonNode data = body(get(POSTS_URL)
                .param("categoryId", "4")
                .param("sort", "liked")
                .param("size", "20")).get("data");

        assertThat(ids(data)).containsExactly(3L, 1L, 302L, 301L);
    }

    @Test
    @DisplayName("A6-2: sort=liked without a channel or category is a 400 that names the requirement")
    void likedSort_withoutScope_isRejected() throws Exception {
        mockMvc.perform(get(POSTS_URL).param("sort", "liked"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("channelSlug")))
                .andExpect(jsonPath("$.message", containsString("categoryId")));
    }

    @Test
    @DisplayName("A6-3: latest, hot and ai keep their previous ordering")
    void existingSorts_keepTheirBehaviour() throws Exception {
        // latest: the default category path, pinned first then newest.
        JsonNode latest = body(get(POSTS_URL)
                .param("categoryId", "4")
                .param("sort", "latest")
                .param("size", "20")).get("data");
        assertThat(ids(latest)).containsExactly(1L, 3L);

        // hot: pinned first, then likes descending.
        JsonNode hot = body(get(POSTS_URL)
                .param("categoryId", "4")
                .param("sort", "hot")
                .param("size", "20")).get("data");
        assertThat(ids(hot)).containsExactly(3L, 1L);

        // ai: reviewed rows only, score descending.
        JsonNode ai = body(get(POSTS_URL)
                .param("categoryId", "2")
                .param("sort", "ai")
                .param("size", "20")).get("data");
        assertThat(ids(ai)).containsExactly(100L, 101L);
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private void insertPost(long id, int categoryId, int likeCount, int commentCount,
                            int status, LocalDateTime createTime) {
        VibePost post = new VibePost();
        post.setId(id);
        post.setUserId(2L);
        post.setCategoryId(categoryId);
        post.setTitle("Discovery fixture " + id);
        post.setContent("Fixture body for the discovery ordering test.");
        post.setSummary("Fixture body.");
        post.setViewCount(0);
        post.setLikeCount(likeCount);
        post.setCommentCount(commentCount);
        post.setStatus(status);
        post.setIsPinned(0);
        post.setCreateTime(createTime);
        postRepository.insert(post);
    }

    /** A list endpoint's {@code data} array, after asserting the envelope was a success. */
    private JsonNode dataArray(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).as("envelope code: %s", body).isEqualTo(200);
        return body.get("data");
    }

    private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asInt()).as("envelope code: %s", body).isEqualTo(200);
        return body;
    }

    /** Ids from a page object ({@code data.list}) or from a bare array. */
    private List<Long> ids(JsonNode data) {
        JsonNode rows = data.isArray() ? data : data.get("list");
        List<Long> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(Long.parseLong(row.get("id").asText())));
        return ids;
    }
}
