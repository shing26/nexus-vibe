package com.nexus.campus.ai.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesTheHeaderIntoTheLoggingContextAndClearsItAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_HEADER, "trace-42");
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(TraceIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("trace-42", seen.get());
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }

    @Test
    void aMissingHeaderLeavesTheContextEmpty() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>("unset");
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req,
                                 jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(TraceIdFilter.MDC_KEY));
            }
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertNull(seen.get());
    }
}
