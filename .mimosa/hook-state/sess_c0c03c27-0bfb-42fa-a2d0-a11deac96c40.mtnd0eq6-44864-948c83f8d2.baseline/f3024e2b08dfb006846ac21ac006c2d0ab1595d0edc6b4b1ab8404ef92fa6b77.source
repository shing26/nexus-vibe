package com.nexus.campus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class UserProfileSummaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET user summary should return seeded shing stats and merged activity")
    void getUserSummary_shouldReturnStatsAndActivity() throws Exception {
        mockMvc.perform(get("/api/v1/users/2/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.id", is("2")))
                .andExpect(jsonPath("$.data.username", is("shing")))
                .andExpect(jsonPath("$.data.nickname", is("shing")))
                .andExpect(jsonPath("$.data.role", is("USER")))
                .andExpect(jsonPath("$.data.stats.posts", is(2)))
                .andExpect(jsonPath("$.data.stats.comments", is(1)))
                .andExpect(jsonPath("$.data.stats.likesReceived", is(120)))
                .andExpect(jsonPath("$.data.stats.avgAiScore", is(7.0)))
                .andExpect(jsonPath("$.data.stats.forks", is(0)))
                .andExpect(jsonPath("$.data.stats.versions", is(1)))
                .andExpect(jsonPath("$.data.recentActivity", hasSize(4)))
                .andExpect(jsonPath("$.data.recentActivity[0].type", is("POST")))
                .andExpect(jsonPath("$.data.recentActivity[0].postId", is("1")))
                .andExpect(jsonPath("$.data.recentActivity[1].type", is("COMMENT")))
                .andExpect(jsonPath("$.data.recentActivity[1].postId", is("2")))
                .andExpect(jsonPath("$.data.recentActivity[2].type", is("POST")))
                .andExpect(jsonPath("$.data.recentActivity[2].postId", is("101")))
                .andExpect(jsonPath("$.data.recentActivity[3].type", is("VERSION")))
                .andExpect(jsonPath("$.data.recentActivity[3].postId", is("101")))
                .andExpect(jsonPath("$.data.recentActivity[1].title", is("Thanks for sharing! The benchmark results are really insight...")))
                .andExpect(jsonPath("$.data.recentActivity[0].createdAt", notNullValue()));
    }

    @Test
    @DisplayName("GET user summary should return 404-style null for a missing user")
    void getUserSummary_missingUser_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999999/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.data", nullValue()));
    }
}
