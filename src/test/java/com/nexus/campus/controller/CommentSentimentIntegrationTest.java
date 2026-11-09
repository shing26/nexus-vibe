package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.dto.CommentCreateRequest;
import com.nexus.campus.enums.CommentSentiment;
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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read side of the sentiment split (A7-5): every comment row the API
 * returns carries a {@code sentiment}, so the list can filter client-side
 * without a second request. Rows that predate the column come back as 0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentSentimentIntegrationTest {

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
    @DisplayName("GET /api/v1/comments/post/{postId} returns sentiment on every row")
    void listReturnsSentimentPerRow() throws Exception {
        mockMvc.perform(get("/api/v1/comments/post/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                // Every comment seeded before the column existed is "other".
                .andExpect(jsonPath("$.data[*].sentiment",
                        everyItem(is(CommentSentiment.OTHER.getCode()))));
    }

    @Test
    @DisplayName("a newly posted comment is classified once and returned with its sentiment")
    void createdCommentCarriesItsSentiment() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setPostId(1L);
        request.setContent("Great walkthrough, thanks for sharing.");

        MvcResult createResult = mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentiment", is(CommentSentiment.PRAISE.getCode())))
                .andReturn();
        String commentId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/comments/post/1"))
                .andExpect(status().isOk())
                // Exactly one row with that id, and its sentiment is praise.
                .andExpect(jsonPath("$.data[?(@.id == " + commentId + ")].sentiment",
                        is(java.util.List.of(CommentSentiment.PRAISE.getCode()))));
    }
}
