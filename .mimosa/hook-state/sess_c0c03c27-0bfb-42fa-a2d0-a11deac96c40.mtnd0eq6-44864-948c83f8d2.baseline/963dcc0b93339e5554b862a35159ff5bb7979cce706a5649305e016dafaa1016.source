package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.dto.PostUpdateRequest;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for {@link PostController} and channel API.
 *
 * <p>Tests post listing, detail retrieval, creation (with JWT auth),
 * liking, and the channel slug API. Uses the seed data from data.sql.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class PostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String authToken;
    private static final String POSTS_URL = "/api/v1/posts";
    private static final String CHANNELS_URL = "/api/v1/channels";

    @BeforeEach
    void setUp() {
        authToken = jwtUtil.generateToken(2L, "testuser", "USER");
    }

    @Test
    @DisplayName("GET /api/v1/posts should return list of active posts")
    void getPosts_shouldReturnPostList() throws Exception {
        mockMvc.perform(get(POSTS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list", is(not(empty()))))
                .andExpect(jsonPath("$.data.list[0].title", notNullValue()))
                .andExpect(jsonPath("$.data.page", is(1)))
                .andExpect(jsonPath("$.data.size", is(10)));
    }

    @Test
    @DisplayName("GET /api/v1/posts with categoryId should filter by category")
    void getPostsByCategory_shouldReturnFilteredList() throws Exception {
        mockMvc.perform(get(POSTS_URL)
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list", is(not(empty()))));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id} should return post detail")
    void getPostDetail_shouldReturnPost() throws Exception {
        long existingPostId = 1L;

        mockMvc.perform(get(POSTS_URL + "/" + existingPostId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.id", is(notNullValue())))
                .andExpect(jsonPath("$.data.title", notNullValue()))
                .andExpect(jsonPath("$.data.authorName", is("shing")))
                .andExpect(jsonPath("$.data.categoryName", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id} should return 404 for nonexistent post")
    void getPostDetail_nonexistent_shouldReturn404() throws Exception {
        mockMvc.perform(get(POSTS_URL + "/999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)));
    }

    @Test
    @DisplayName("POST /api/v1/posts with valid JWT should create a post")
    void createPost_withValidToken_shouldSucceed() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Integration Test Post");
        request.setContent("This is a post created during integration testing with clean content.");
        request.setCategoryId(2);
        request.setTags(null);

        mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.postId", notNullValue()))
                .andExpect(jsonPath("$.data.status", is(1)));
    }

    @Test
    @DisplayName("POST /api/v1/posts without JWT should return 401")
    void createPost_withoutToken_shouldReturn401() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Unauthorized Post");
        request.setContent("This should not be created.");
        request.setCategoryId(1);
        request.setTags(null);

        mockMvc.perform(post(POSTS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/like with valid JWT should increment likes")
    void likePost_withValidToken_shouldSucceed() throws Exception {
        long existingPostId = 3L;

        mockMvc.perform(post(POSTS_URL + "/" + existingPostId + "/like")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.postId", is(Long.toString(existingPostId))))
                .andExpect(jsonPath("$.data.currentLikes", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/like without JWT should return 401")
    void likePost_withoutToken_shouldReturn401() throws Exception {
        long existingPostId = 3L;

        mockMvc.perform(post(POSTS_URL + "/" + existingPostId + "/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.message", containsString("Authentication required")));
    }

    @Test
    @DisplayName("GET /api/v1/channels should return all 7 channels with slugs")
    void getAllChannels_shouldReturnAllChannels() throws Exception {
        mockMvc.perform(get(CHANNELS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].slug", is("announcements")))
                .andExpect(jsonPath("$.data[1].slug", is("prompts")))
                .andExpect(jsonPath("$.data[2].slug", is("showcase")))
                .andExpect(jsonPath("$.data[3].slug", is("agents")))
                .andExpect(jsonPath("$.data[4].slug", is("vibe-coding")))
                .andExpect(jsonPath("$.data[5].slug", is("debug")))
                .andExpect(jsonPath("$.data[6].slug", is("resources")));
    }

    @Test
    @DisplayName("GET /api/v1/posts?channelSlug=prompts should filter by channel slug")
    void getPosts_byChannelSlug_shouldReturnFilteredList() throws Exception {
        mockMvc.perform(get(POSTS_URL)
                        .param("channelSlug", "prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list", notNullValue()))
                .andExpect(jsonPath("$.data.list[*].postType", everyItem(is("prompt"))));
    }

    @Test
    @DisplayName("GET /api/v1/posts?channelSlug=prompts&type=prompt should return only prompt templates")
    void getPosts_promptsChannelWithPromptType_shouldReturnOnlyPrompts() throws Exception {
        mockMvc.perform(get(POSTS_URL)
                        .param("channelSlug", "prompts")
                        .param("type", "prompt")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list[*].postType", everyItem(is("prompt"))));
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} should reject moving a post to announcements for a regular user")
    void updatePost_regularUserMovingToAnnouncements_shouldReturn400() throws Exception {
        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCleanPostRequest("Announcement Guard Post"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        PostUpdateRequest updateRequest = new PostUpdateRequest();
        updateRequest.setTitle("Announcement Guard Post");
        updateRequest.setContent("Still clean content.");
        updateRequest.setCategoryId(1);

        mockMvc.perform(put(POSTS_URL + "/" + postId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("只有管理员才能在公告频道发帖")));
    }

    @Test
    @DisplayName("POST /api/v1/posts should reject titles longer than 150 characters")
    void createPost_titleTooLong_shouldReturn400() throws Exception {
        PostCreateRequest request = createCleanPostRequest("T".repeat(151));

        mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("Title must not exceed 150 characters")));
    }

    @Test
    @DisplayName("GET /api/v1/channels/stats should return a post count for every active channel")
    void getChannelStats_shouldReturnCounts() throws Exception {
        mockMvc.perform(get(CHANNELS_URL + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].slug", is("announcements")))
                .andExpect(jsonPath("$.data[0].postCount", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/posts?channelSlug=nonexistent should return empty result")
    void getPosts_byNonexistentChannelSlug_shouldReturnEmpty() throws Exception {
        mockMvc.perform(get(POSTS_URL)
                        .param("channelSlug", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.list", is(empty())));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/fork should clone a prompt template")
    void forkPrompt_withValidToken_shouldSucceed() throws Exception {
        mockMvc.perform(post(POSTS_URL + "/100/fork")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.postId", notNullValue()))
                .andExpect(jsonPath("$.data.forkedFromId", is("100")));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/fork without JWT should return 401")
    void forkPrompt_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post(POSTS_URL + "/100/fork"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id}/versions should return version history")
    void getPromptVersions_shouldReturnHistory() throws Exception {
        mockMvc.perform(get(POSTS_URL + "/100/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[0].version", notNullValue()))
                .andExpect(jsonPath("$.data[0].changeNote", notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} on a prompt template should append a version")
    void updatePromptTemplate_shouldAppendVersion() throws Exception {
        PostCreateRequest createRequest = new PostCreateRequest();
        createRequest.setTitle("Versioned Controller Prompt");
        createRequest.setContent("Create a versioned controller from this prompt.");
        createRequest.setCategoryId(2);
        createRequest.setPostType("prompt");
        createRequest.setPromptMetadata("{\"role\":\"architect\",\"recommendedModel\":\"gpt-4o\",\"temperature\":0.5,\"variables\":[]}");

        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        PostUpdateRequest updateRequest = new PostUpdateRequest();
        updateRequest.setTitle("Versioned Controller Prompt v2");
        updateRequest.setContent("Updated controller prompt body.");
        updateRequest.setChangeNote("Tighten instructions");

        mockMvc.perform(put(POSTS_URL + "/" + postId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        mockMvc.perform(get(POSTS_URL + "/" + postId + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].version", is(2)));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{id}/versions/{version}/restore should roll back content")
    void restorePromptVersion_shouldRollBackContent() throws Exception {
        mockMvc.perform(post(POSTS_URL + "/101/versions/1/restore")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} should remove the author's own post")
    void deletePost_ownPost_shouldSucceed() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Deletable Integration Post");
        request.setContent("This post is created only to be deleted by its author.");
        request.setCategoryId(2);
        request.setTags(null);

        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        mockMvc.perform(delete(POSTS_URL + "/" + postId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        mockMvc.perform(get(POSTS_URL + "/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} should reject a non-author")
    void deletePost_otherUsersPost_shouldReturn400() throws Exception {
        String otherUserToken = jwtUtil.generateToken(3L, "alice", "USER");

        mockMvc.perform(delete(POSTS_URL + "/1")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("author")));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard should expose admin statistics")
    void getAdminDashboard_shouldReturnStats() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin", "ADMIN");

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.totalPosts", notNullValue()))
                .andExpect(jsonPath("$.data.pendingAudits", notNullValue()))
                .andExpect(jsonPath("$.data.todayPosts", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard should reject non-admin users")
    void getAdminDashboard_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    @DisplayName("POST /api/v1/admin/search/reindex should rebuild the ES index for admin")
    void reindexSearch_admin_shouldReturnCounts() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.reindexed", notNullValue()))
                .andExpect(jsonPath("$.data.esAvailable", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/admin/search/reindex should reject non-admin users")
    void reindexSearch_nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    @DisplayName("Pin/unpin requires ADMIN; a regular user gets 403")
    void pinPost_requiresAdminRole() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin", "ADMIN");

        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCleanPostRequest("Pinnable Post"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        mockMvc.perform(post(POSTS_URL + "/" + postId + "/pin")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(403)));

        mockMvc.perform(post(POSTS_URL + "/" + postId + "/pin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("Pending-audit posts are hidden from detail and user post list")
    void pendingAuditPost_shouldBeHidden() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Hidden Pending Post " + System.currentTimeMillis());
        request.setContent("该内容包含赌博关键词，应进入审核队列。");
        request.setCategoryId(2);

        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.status", is(2)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        mockMvc.perform(get(POSTS_URL + "/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)));

        mockMvc.perform(get(POSTS_URL).param("userId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[?(@.title == '" + request.getTitle() + "')]")
                        .doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} should allow an admin to delete another user's post")
    void deletePost_admin_shouldSucceed() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin", "ADMIN");
        MvcResult createResult = mockMvc.perform(post(POSTS_URL)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCleanPostRequest("Admin Deletable Post"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("postId").asText();

        mockMvc.perform(delete(POSTS_URL + "/" + postId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        mockMvc.perform(get(POSTS_URL + "/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)));
    }

    private PostCreateRequest createCleanPostRequest(String title) {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle(title);
        request.setContent("Clean content for permission integration tests.");
        request.setCategoryId(2);
        request.setTags(null);
        return request;
    }
}
