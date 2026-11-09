package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The degradation surface end to end: the real health group, the real servlet filter chain and
 * the real envelope.
 *
 * <p>Same condition as {@link HealthSemanticsIntegrationTest} - LLM and Elasticsearch pointed at
 * a closed loopback port - so the degraded half holds on any machine, including one where the
 * developer happens to be running Ollama or Elasticsearch. Requests carry no Authorization
 * header: B3 is about the visitor who is not signed in, and {@code JwtAuthFilter} is the only
 * thing that could turn this into a 401.</p>
 */
@SpringBootTest(properties = {
        "campus.ai.llm.endpoint=http://127.0.0.1:1/v1",
        "campus.es.uri=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
class CapabilityStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult anonymousGet() throws Exception {
        return mockMvc.perform(get("/api/v1/status/capabilities"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("An anonymous visitor gets 200 with search-degraded when Elasticsearch is down")
    void anonymousVisitorSeesSearchDegraded() throws Exception {
        JsonNode body = objectMapper.readTree(anonymousGet().getResponse().getContentAsString());

        assertThat(body.get("code").asInt()).isEqualTo(200);
        JsonNode data = body.get("data");
        assertThat(data.isArray()).isTrue();

        JsonNode search = null;
        for (JsonNode entry : data) {
            if ("search".equals(entry.get("key").asText())) {
                search = entry;
            }
        }
        assertThat(search).as("search must be reported once Elasticsearch is unreachable").isNotNull();
        assertThat(search.get("impact").asText())
                .containsIgnoringCase("search results may be incomplete");
        assertThat(search.get("impact").asText()).doesNotContainIgnoringCase("elasticsearch");
    }

    @Test
    @DisplayName("The same MockMvc chain still 401s a protected GET, so the 200 is the allowlist")
    void filterChainIsActive() throws Exception {
        // Negative control for the test above: JwtAuthFilter really is in this chain, and the
        // anonymous 200 comes from the single /api/v1/status entry in isPublicGet rather than
        // from the filter not running at all.
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The response is a shaped model, never the deps document")
    void responseCarriesNoDependencyDetail() throws Exception {
        String raw = anonymousGet().getResponse().getContentAsString();

        for (String forbidden : List.of(
                "127.0.0.1", "localhost", "9200", "http://", "https://",
                "\"reason\"", "\"details\"", "\"components\"", "\"status\"",
                "Elasticsearch", "probe")) {
            assertThat(raw)
                    .as("raw dependency detail must not reach the public surface: %s", forbidden)
                    .doesNotContain(forbidden);
        }

        JsonNode data = objectMapper.readTree(raw).get("data");
        List<String> keys = new ArrayList<>();
        for (JsonNode entry : data) {
            assertThat(fieldNamesOf(entry))
                    .as("each entry is exactly the allow-listed pair")
                    .containsExactlyInAnyOrder("key", "impact");
            keys.add(entry.get("key").asText());
        }
        assertThat(keys).isSubsetOf("search", "ai-review", "cache");
    }

    @Test
    @DisplayName("The public health answers do not change when this surface is read")
    void healthAnswersStayByteForByte() throws Exception {
        String topBefore = bodyOf("/actuator/health");
        String servableBefore = bodyOf("/actuator/health/servable");

        // B6: servable still answers the visitor question while the aggregate answers the
        // dependency question, under the same condition HealthSemanticsIntegrationTest uses.
        assertThat(servableBefore).isEqualTo("{\"status\":\"UP\"}");
        // Pinned as the exact bytes this endpoint answers today. The custom DEGRADED status
        // carries a description, so it appears beside the status; nothing else does.
        assertThat(topBefore).isEqualTo(
                "{\"description\":\"dependency unavailable, still serving\",\"status\":\"DEGRADED\","
                        + "\"groups\":[\"deps\",\"servable\"]}");

        mockMvc.perform(get("/api/v1/status/capabilities")).andExpect(status().isOk());

        assertThat(bodyOf("/actuator/health")).isEqualTo(topBefore);
        assertThat(bodyOf("/actuator/health/servable")).isEqualTo(servableBefore);
    }

    private String bodyOf(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
