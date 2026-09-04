package com.nexus.campus.controller;

import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import com.nexus.campus.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
                .andExpect(status().isOk())
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
}
