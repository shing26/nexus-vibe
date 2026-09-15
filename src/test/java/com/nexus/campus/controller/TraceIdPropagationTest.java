package com.nexus.campus.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.dto.ApiResponse;
import com.nexus.campus.dto.PostCreateRequest;
import com.nexus.campus.util.JwtUtil;
import com.nexus.campus.util.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A trace id earns its place on the hops a request actually takes: the request
 * thread, the agent pool that reviews the post, and the failure that answers as a
 * 500. Each has to name itself with the same string, or the id is decoration.
 *
 * <p>The LLM is mocked only to hold the publish-time safety gate open (ADR-0004
 * fail-closes when it looks unhealthy). The review then runs against a stub and
 * fails, which is what puts a log line on the agent pool under the id.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/data.sql", "/test-users.sql"})
class TraceIdPropagationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private LlmClient llmClient;

    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void attachRootAppender() {
        when(llmClient.isHealthy()).thenReturn(true);
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        events = new ListAppender<>();
        events.start();
        rootLogger.addAppender(events);
    }

    @AfterEach
    void detachRootAppender() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(events);
    }

    private boolean loggedUnder(String traceId, String threadPrefix, String loggerName) {
        return events.list.stream().anyMatch(e -> e.getThreadName().startsWith(threadPrefix)
                && (loggerName == null || loggerName.equals(e.getLoggerName()))
                && traceId.equals(e.getMDCPropertyMap().get(TraceIds.MDC_KEY)));
    }

    @Test
    @DisplayName("A post with a code block logs one traceId on the request thread and on the agent pool")
    void traceIdCrossesTheAsyncHandoff() throws Exception {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("Trace probe post");
        request.setContent("Review this please:\n\n```java\nSystem.out.println(1);\n```\n");
        request.setCategoryId(2);

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(2L, "shing", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceIds.HEADER);
        assertThat(traceId).isNotNull().matches("^[0-9a-f]{16}$");
        // The aspect logs around every controller call, so this line is the request thread's own.
        assertThat(loggedUnder(traceId, "main", "com.nexus.campus.aspect.LogAspect")).isTrue();

        // The handoff is genuinely asynchronous: give the pool a window, but no more.
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline && !loggedUnder(traceId, "agent-llm-", null)) {
            Thread.sleep(100L);
        }
        assertThat(loggedUnder(traceId, "agent-llm-", null))
                .as("an agent-llm-* thread logged under the request's traceId %s", traceId)
                .isTrue();
    }

    @Test
    @DisplayName("A 5xx body carries the header's id; a 2xx body carries none at all")
    void failureResponsesNameThemselves() throws Exception {
        MvcResult ok = mockMvc.perform(get("/trace-probe/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andReturn();
        String okTrace = ok.getResponse().getHeader(TraceIds.HEADER);
        // The controller read its own MDC, so the id is live inside the application and
        // not merely stamped on the wire on the way out.
        assertThat(JsonPath.parse(ok.getResponse().getContentAsString()).read("$.data", String.class))
                .isEqualTo(okTrace);

        MvcResult boom = mockMvc.perform(get("/trace-probe/boom"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String boomTrace = boom.getResponse().getHeader(TraceIds.HEADER);
        assertThat(boomTrace).matches("^[0-9a-f]{16}$");
        assertThat(JsonPath.parse(boom.getResponse().getContentAsString()).read("$.traceId", String.class))
                .isEqualTo(boomTrace);
    }

    @TestConfiguration
    static class TraceProbeConfig {

        @Bean
        TraceProbeController traceProbeController() {
            return new TraceProbeController();
        }
    }

    @RestController
    static class TraceProbeController {

        @GetMapping("/trace-probe/ok")
        ApiResponse<String> ok() {
            return ApiResponse.success(TraceIds.current());
        }

        @GetMapping("/trace-probe/boom")
        ApiResponse<Void> boom() {
            throw new RuntimeException("trace probe failure");
        }
    }
}
