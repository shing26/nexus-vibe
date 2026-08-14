package com.nexus.campus.controller;

import com.nexus.campus.agent.AiReviewLog;
import com.nexus.campus.agent.AiReviewLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class AiLogControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiReviewLogMapper aiReviewLogMapper;

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
}
