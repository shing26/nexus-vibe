package com.nexus.campus.ai.api;

import com.nexus.campus.ai.config.AiServiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceAuthFilterTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private AiServiceAuthFilter filter;

    @BeforeEach
    void setUp() {
        properties.getService().setApiKey("shared-secret");
        filter = new AiServiceAuthFilter(properties);
    }

    @Test
    void aMissingKeyIs401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/v1/reviews"),
                response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("unauthorized"));
    }

    @Test
    void aWrongKeyIs401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/reviews");
        request.addHeader("X-AI-Service-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void theConfiguredKeyPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/reviews");
        request.addHeader("X-AI-Service-Key", "shared-secret");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(request, chain.getRequest());
    }

    @Test
    void healthAndMetricsStayOpenForTheHealthcheckAndPrometheus() throws Exception {
        MockFilterChain health = new MockFilterChain();
        MockHttpServletRequest healthRequest = new MockHttpServletRequest("GET", "/readyz");

        filter.doFilter(healthRequest, new MockHttpServletResponse(), health);

        assertEquals(healthRequest, health.getRequest());
    }
}
