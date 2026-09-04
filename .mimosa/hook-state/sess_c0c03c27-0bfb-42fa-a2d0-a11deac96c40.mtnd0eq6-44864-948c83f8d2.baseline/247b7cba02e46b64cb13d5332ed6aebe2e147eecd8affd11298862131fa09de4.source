package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.CommentCreateRequest;
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
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtUtil.generateToken(2L, "shing", "USER");
    }

    @Test
    @DisplayName("GET /api/v1/comments/post/{postId} should return comments with author names")
    void getComments_shouldReturnListWithAuthors() throws Exception {
        mockMvc.perform(get("/api/v1/comments/post/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", is(not(empty()))))
                .andExpect(jsonPath("$.data[0].authorName", notNullValue()))
                .andExpect(jsonPath("$.data[0].content", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/comments should create a comment and appear in the list")
    void createComment_withValidToken_shouldSucceed() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("Integration comment from automated delivery test.");

        mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.content", containsString("automated delivery test")));

        mockMvc.perform(get("/api/v1/comments/post/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.content == 'Integration comment from automated delivery test.')]")
                        .exists());
    }

    @Test
    @DisplayName("POST /api/v1/comments without JWT should return 401")
    void createComment_withoutToken_shouldReturn401() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("This comment must not be created.");

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} should let the author delete their own comment")
    void deleteComment_author_shouldSucceed() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("Comment to delete by its author.");

        MvcResult createResult = mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String commentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} should reject a non-author non-admin user")
    void deleteComment_nonAuthor_shouldReturn400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("Comment owned by shing.");

        MvcResult createResult = mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String commentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        String otherUserToken = jwtUtil.generateToken(3L, "alice", "USER");
        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("author or an admin")));
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} should let an admin delete any comment")
    void deleteComment_admin_shouldSucceed() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("Comment to delete by admin.");

        MvcResult createResult = mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andReturn();
        String commentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        String adminToken = jwtUtil.generateToken(1L, "admin", "ADMIN");
        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }
}
