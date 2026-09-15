package com.nexus.campus.contract;

import com.nexus.campus.config.GlobalExceptionHandler;
import com.nexus.campus.exception.BusinessException;
import com.nexus.campus.util.TraceIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What each class of failure is allowed to say, on a controller with nothing else
 * in the way. The full application context cannot express these cases - it has no
 * endpoint that throws an arbitrary internal exception on demand - so this builds
 * the dispatcher by hand around {@link GlobalExceptionHandler} and probes it.
 *
 * <p>Two properties matter. A {@link BusinessException} carries the status its
 * thrower chose, because that is the whole reason it exists. Anything else that
 * fails on a request path carries a status <em>and no message of its own</em>:
 * before this, an {@code IllegalArgumentException} from any library at any depth
 * was repeated back to the client verbatim, and the one place that could have
 * caught a JDBC constraint name leaking had decided 400s were always safe to
 * echo.</p>
 */
class ErrorSemanticsTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // The standalone dispatcher has no filter chain, so nothing would put a
        // trace id in MDC and every 5xx body would arrive without one. ApiResponse
        // deliberately reports only an id the request actually carries - inventing
        // one here would print a number matching no log line - so the test sets up
        // the condition TraceIdFilter sets up in the running app.
        MDC.put(TraceIds.MDC_KEY, TraceIds.newTraceId());
    }

    @AfterEach
    void tearDown() {
        MDC.remove(TraceIds.MDC_KEY);
    }

    @Test
    @DisplayName("A BusinessException answers with the status its thrower chose")
    void businessExceptionCarriesItsOwnStatus() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                // The message was written for this audience, so it goes through.
                .andExpect(jsonPath("$.message").value("Template is not active."));
    }

    @Test
    @DisplayName("A 4xx business failure carries no trace id")
    void clientFailureCarriesNoTraceId() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    @DisplayName("A 5xx business failure names itself with a trace id")
    void serverFailureCarriesTraceId() throws Exception {
        mockMvc.perform(get("/probe/server-side"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("An unmapped IllegalArgumentException stays a 400 and keeps its detail in the log")
    void unmappedArgumentFailureLeaksNothing() throws Exception {
        mockMvc.perform(get("/probe/internal-iae"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request could not be processed. Check the submitted values."));
    }

    @Test
    @DisplayName("An unmapped IllegalStateException is no longer a 400 that quotes the server")
    void unmappedStateFailureBecomesA500() throws Exception {
        mockMvc.perform(get("/probe/internal-ise"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("An unexpected runtime failure tells the client nothing internal")
    void unexpectedFailureStaysGeneric() throws Exception {
        mockMvc.perform(get("/probe/explosion"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error."))
                .andExpect(jsonPath("$.traceId").exists());
    }

    /** Six shapes of failure, one endpoint each. */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/conflict")
        void conflict() {
            throw BusinessException.conflict("Template is not active.");
        }

        @GetMapping("/probe/server-side")
        void serverSide() {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "File upload failed.");
        }

        @GetMapping("/probe/internal-iae")
        void internalIae() {
            throw new IllegalArgumentException(
                    "Duplicate entry 'shing' for key 'sys_user.uk_username'");
        }

        @GetMapping("/probe/internal-ise")
        void internalIse() {
            throw new IllegalStateException(
                    "LLM response missing expected content path: {\"error\":\"quota exceeded\"}");
        }

        @GetMapping("/probe/explosion")
        void explosion() {
            throw new RuntimeException("connection reset by peer at /var/lib/nexus/secrets");
        }
    }
}
