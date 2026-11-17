package com.nexus.campus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The exhibit is for a visitor with no account. This test deliberately sends no
 * Authorization header and asserts the public read surface end to end.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Signed-out GET /api/v1/demo/chaos-scenarios returns the five traces")
    void chaosScenariosArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/demo/chaos-scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].id", is("fix-challenge")))
                .andExpect(jsonPath("$.data[1].id", is("repaired-json")))
                .andExpect(jsonPath("$.data[2].id", is("empty-placeholder")))
                .andExpect(jsonPath("$.data[3].id", is("prompt-injection")))
                .andExpect(jsonPath("$.data[4].id", is("model-down")))
                .andExpect(jsonPath("$.data[0].steps[0].mode", is("REAL")))
                .andExpect(jsonPath("$.data[0].steps[2].mode", is("INJECTED")))
                .andExpect(jsonPath("$.data[3].steps[2].mode", is("INJECTED")))
                .andExpect(jsonPath("$.data[4].steps[4].mode", is("POLICY")))
                .andExpect(jsonPath("$.data[0].modelAttempts", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[4].failClosed", is(true)))
                .andExpect(jsonPath("$.data[0].promptPreview", startsWith("---BEGIN META")))
                .andExpect(jsonPath("$.data[0].fixedCode", containsString("if (divisor == 0)")))
                .andExpect(jsonPath("$.data[0].afterScore", is(9)))
                .andExpect(jsonPath("$.data[0].counterfactual").doesNotExist())
                .andExpect(jsonPath("$.data[1].counterfactual.id", is("cf-repaired")))
                .andExpect(jsonPath("$.data[3].counterfactual.score", is(10)));
    }
}
