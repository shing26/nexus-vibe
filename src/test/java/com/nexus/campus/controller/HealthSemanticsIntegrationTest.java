package com.nexus.campus.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the two-level health contract end to end.
 *
 * <p>The LLM and Elasticsearch endpoints are pointed at a closed loopback port rather than left
 * at their dev defaults so the degraded half of the contract holds on any machine: a developer
 * running Ollama or Elasticsearch locally would otherwise flip these assertions, and a test that
 * passes only on a machine with no local services is not a contract.</p>
 */
@SpringBootTest(properties = {
        "campus.ai.llm.endpoint=http://127.0.0.1:1/v1",
        "campus.es.uri=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
class HealthSemanticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/health answers 200 with DEGRADED and no component detail")
    void degradedStillServesAndStaysCompact() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                // Group names are advertised so an operator can find /actuator/health/deps; the
                // components behind them are not, and that is the part a public probe must carry
                // nothing of.
                .andExpect(jsonPath("$.groups").value(hasItem("deps")))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("/actuator/health/deps names every dependency with its own detail")
    void depsGroupExplainsWhatIsDegraded() throws Exception {
        mockMvc.perform(get("/actuator/health/deps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                // Real storage is the line that must not move: it stays UP here and would be DOWN
                // for real, which is what the healthcheck is allowed to act on.
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.details.mode").value("disabled"))
                .andExpect(jsonPath("$.components.llm.status").value("DEGRADED"))
                .andExpect(jsonPath("$.components.elasticsearch.status").value("DEGRADED"));
    }

    @Test
    @DisplayName("The metrics API stays off the exposure allowlist")
    void metricsApiIsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
