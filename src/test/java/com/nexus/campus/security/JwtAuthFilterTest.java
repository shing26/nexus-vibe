package com.nexus.campus.security;

import com.nexus.campus.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private FilterChain chain;

    @Test
    @DisplayName("OPTIONS preflight reaches Spring CORS without an Authorization header")
    void optionsPreflightIsPassedThrough() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/posts/1/like");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Protected non-preflight requests still require a valid token")
    void protectedRequestStillRequiresToken() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts/1/like");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }
}
