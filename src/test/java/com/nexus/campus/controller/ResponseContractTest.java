package com.nexus.campus.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend types this envelope, so it is a contract rather than an
 * implementation detail — and it had already drifted once, with the client
 * declaring a {@code success} field the server never sent and nothing reading
 * it to notice.
 *
 * <p>{@code traceId} is the one field allowed to appear, and only on a server
 * failure; {@link TraceIdPropagationTest} pins that side. This test fails if the
 * envelope changes shape again without saying so here.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/data.sql", "/test-users.sql"})
class ResponseContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode bodyOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertEnvelope(JsonNode body) {
        assertThat(body.has("code")).as("code is present: %s", body).isTrue();
        assertThat(body.get("code").isInt()).isTrue();
        assertThat(body.has("message")).as("message is present").isTrue();
        assertThat(body.get("message").isTextual()).isTrue();
        // Present even when empty: the client reads data unconditionally.
        assertThat(body.has("data")).as("data key is present even when null").isTrue();
        assertThat(body.has("success")).as("there is no success field").isFalse();
        assertThat(body.has("traceId")).as("a non-failure carries no traceId").isFalse();
    }

    @Test
    @DisplayName("Representative endpoints all answer with code, message and data")
    void successfulResponsesShareOneEnvelope() throws Exception {
        assertEnvelope(bodyOf(get("/api/v1/posts")));
        assertEnvelope(bodyOf(get("/api/v1/posts").param("page", "1").param("size", "5")));
        assertEnvelope(bodyOf(get("/api/v1/channels")));
        assertEnvelope(bodyOf(get("/api/v1/agent-logs/ticker")));
    }

    @Test
    @DisplayName("A business failure keeps the envelope and adds nothing")
    void failureResponsesKeepTheSameKeys() throws Exception {
        // Missing id: still an ApiResponse, still three keys, still no trace id.
        JsonNode body = bodyOf(get("/api/v1/posts/999999999"));

        assertEnvelope(body);
        assertThat(body.get("code").asInt()).isEqualTo(404);
        assertThat(body.get("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("The envelope has exactly three fields on a success")
    void noUndeclaredFieldsLeakIn() throws Exception {
        JsonNode body = bodyOf(get("/api/v1/channels"));

        List<String> fields = new java.util.ArrayList<>();
        for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
            fields.add(it.next());
        }
        assertThat(fields).containsExactlyInAnyOrder("code", "message", "data");
    }
}
