package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Black-box findings fixed on 2026-09-09: oversized profile fields used to
 * surface as MySQL-truncation 500s (now 400 via @Size), and a wrong verb on
 * a supported path fell through to the catch-all handler as a 500 (now 405).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/data.sql", "/test-users.sql"})
class UserProfileGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // No live LLM in tests; keep the publish-time safety gate healthy.
    @MockBean
    private com.nexus.campus.agent.LlmClient llmClient;

    private String token;

    @BeforeEach
    void setUp() {
        token = jwtUtil.generateToken(2L, "testuser", "USER");
        org.mockito.Mockito.when(llmClient.isHealthy()).thenReturn(true);
    }

    @Test
    @DisplayName("151-char nickname returns 400 validation error, not a 500 truncation")
    void oversizedNicknameShouldBe400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("nickname", "n".repeat(151));
        mockMvc.perform(put("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", containsString("50")));
    }

    @Test
    @DisplayName("Within-limit profile update still succeeds (no regression to partial update)")
    void validProfileUpdateStillWorks() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("nickname", "Renamed Tester");
        body.put("bio", "hello");
        mockMvc.perform(put("/api/v1/users/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));
    }

    @Test
    @DisplayName("Wrong verb on a supported path returns 405, not a 500")
    void wrongVerbShouldBe405() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is(405)));
    }
}
