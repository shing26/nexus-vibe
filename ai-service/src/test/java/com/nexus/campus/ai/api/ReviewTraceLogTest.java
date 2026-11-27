package com.nexus.campus.ai.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.service.ReviewService;
import com.nexus.campus.ai.service.SafetyService;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Acceptance #9, service half: the {@code X-Trace-Id} header copied into MDC by
 * {@link TraceIdFilter} is the trace recorded on the review's log line. The
 * request body deliberately carries no {@code traceId}, so the only way the log
 * can show one is the header → MDC path.
 */
class ReviewTraceLogTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void theHeaderReachesTheReviewLogLine() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ReviewController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ReviewService reviews = mock(ReviewService.class);
            when(reviews.review(any())).thenReturn(new ReviewResponse(
                    8, "low", "readable", "no issues", "add a test", true,
                    "{}", "test-model", new ReviewResponse.Usage(1, 1), 1, false));
            ReviewController controller = new ReviewController(reviews, mock(SafetyService.class));

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(TraceIdFilter.TRACE_HEADER, "trace-abc");
            MockFilterChain chain = new MockFilterChain() {
                @Override
                public void doFilter(ServletRequest req, ServletResponse res) {
                    controller.review(new ReviewRequest(
                            "req-1", null, 42L, "code", "title", "```java\nint x = 1;\n```",
                            List.of("int x = 1;"), new ReviewRequest.Budget(1000, 30000)));
                }
            };

            new TraceIdFilter().doFilter(request, new MockHttpServletResponse(), chain);

            ILoggingEvent event = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("review request"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(event.getFormattedMessage().contains("trace=trace-abc"),
                    () -> "log line was: " + event.getFormattedMessage());
            assertEquals("trace-abc", event.getMDCPropertyMap().get(TraceIdFilter.MDC_KEY));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
