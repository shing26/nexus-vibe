package com.nexus.campus.config;

import com.nexus.campus.util.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level contract for the outermost filter: the id exists before anything
 * else reads the request, and it is gone afterwards.
 */
class TraceIdFilterTest {

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    /** Run the filter over {@code request} and report the MDC id visible inside the chain. */
    private String traceInside(TraceIdFilter filter, MockHttpServletRequest request,
                               MockHttpServletResponse response) throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();
        MockFilterChain trackingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                duringRequest.set(MDC.get(TraceIds.MDC_KEY));
            }
        };
        filter.doFilter(request, response, trackingChain);
        return duringRequest.get();
    }

    @Test
    @DisplayName("MDC is set for the request, echoed on the header, and cleared after")
    void setsEchoesAndClears() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String during = traceInside(new TraceIdFilter(false), request, response);

        assertThat(during).matches("^[0-9a-f]{16}$");
        assertThat(response.getHeader(TraceIds.HEADER)).isEqualTo(during);
        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("An inbound id is ignored unless a trusted proxy is assumed")
    void inboundIdNeedsTrust() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        request.addHeader(TraceIds.HEADER, "deadbeefdeadbeef");
        String duringUntrusted = traceInside(new TraceIdFilter(false), request, new MockHttpServletResponse());

        assertThat(duringUntrusted).isNotEqualTo("deadbeefdeadbeef").matches("^[0-9a-f]{16}$");
    }

    @Test
    @DisplayName("Behind a trusted proxy an upstream id is adopted, a junk one is not")
    void trustedProxyAdoptsValidIdOnly() throws Exception {
        MockHttpServletRequest good = new MockHttpServletRequest("GET", "/api/v1/posts");
        good.addHeader(TraceIds.HEADER, "deadbeefdeadbeef");
        MockHttpServletResponse goodResponse = new MockHttpServletResponse();
        TraceIdFilter filter = new TraceIdFilter(true);

        filter.doFilter(good, goodResponse, new MockFilterChain());

        assertThat(goodResponse.getHeader(TraceIds.HEADER)).isEqualTo("deadbeefdeadbeef");

        MockHttpServletRequest junk = new MockHttpServletRequest("GET", "/api/v1/posts");
        junk.addHeader(TraceIds.HEADER, "<script>injected</script>");
        MockHttpServletResponse junkResponse = new MockHttpServletResponse();

        filter.doFilter(junk, junkResponse, new MockFilterChain());

        assertThat(junkResponse.getHeader(TraceIds.HEADER)).matches("^[0-9a-f]{16}$");
    }
}
