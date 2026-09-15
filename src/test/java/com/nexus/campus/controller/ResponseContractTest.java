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
import org.springframework.test.web.servlet.ResultMatcher;

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

    /**
     * Takes the status it expects. It used to insist on 200 for every request,
     * the failure case included, which is how this file ended up pinning the very
     * bug it exists to catch: a missing post answered 200 with a body claiming
     * 404, and this helper asserted the 200 while the test asserted the 404.
     */
    private JsonNode bodyOf(MockHttpServletRequestBuilder request, ResultMatcher expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(expectedStatus).andReturn();
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
        assertEnvelope(bodyOf(get("/api/v1/posts"), status().isOk()));
        assertEnvelope(bodyOf(get("/api/v1/posts").param("page", "1").param("size", "5"), status().isOk()));
        assertEnvelope(bodyOf(get("/api/v1/channels"), status().isOk()));
        assertEnvelope(bodyOf(get("/api/v1/agent-logs/ticker"), status().isOk()));
    }

    @Test
    @DisplayName("A 4xx keeps the envelope, carries no trace id, and agrees with the status")
    void failureResponsesKeepTheSameKeys() throws Exception {
        JsonNode body = bodyOf(get("/api/v1/posts/999999999"), status().isNotFound());

        assertEnvelope(body);
        // The number the body claims and the status the transport carried are the
        // same number, which is now true by construction rather than by luck.
        assertThat(body.get("code").asInt())
                .as("code mirrors the HTTP status it travelled on")
                .isEqualTo(404);
        assertThat(body.get("data").isNull()).isTrue();
    }

    @Test
    @DisplayName("The envelope has exactly three fields on a success")
    void noUndeclaredFieldsLeakIn() throws Exception {
        JsonNode body = bodyOf(get("/api/v1/channels"), status().isOk());

        List<String> fields = new java.util.ArrayList<>();
        for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
            fields.add(it.next());
        }
        assertThat(fields).containsExactlyInAnyOrder("code", "message", "data");
    }
}
