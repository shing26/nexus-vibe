package com.nexus.campus.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private RateLimitInterceptor interceptor;
    private StringWriter responseWriter;
    private final String clientIp = "192.168.1.100";

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new RateLimitInterceptor();
        // Inject Redis mock since @Autowired(required = false) won't pick up @Mock
        ReflectionTestUtils.setField(interceptor, "redisTemplate", redisTemplate);
        interceptor.init();

        when(request.getHeader("X-Forwarded-For")).thenReturn(clientIp);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(clientIp);
        when(request.getMethod()).thenReturn("POST");

        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // 1-10 requests pass, 11th blocked
    @Test
    @DisplayName("First 10 POST requests from same IP to /api/v1/posts should pass")
    void firstTenRequestsShouldPass() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        for (int i = 1; i <= 10; i++) {
            boolean result = interceptor.preHandle(request, response, null);
            assertTrue(result, "Request #" + i + " should be allowed");
        }

        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("11th POST request from same IP to /api/v1/posts should be rate-limited")
    void eleventhRequestShouldBeBlocked() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        // First 10 calls: Lua returns 1 (allowed)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 1L);

        // 10 allowed requests
        for (int i = 1; i <= 10; i++) {
            assertTrue(interceptor.preHandle(request, response, null));
        }

        // 11th request — blocked
        boolean result = interceptor.preHandle(request, response, null);
        assertFalse(result, "11th request should be blocked");
        verify(response).setStatus(429);
        verify(response).setContentType("application/json;charset=UTF-8");
    }

    @Test
    @DisplayName("Rate limit response should contain 429 JSON message")
    void rateLimitResponseBody() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 1L);

        for (int i = 1; i <= 11; i++) {
            interceptor.preHandle(request, response, null);
        }

        String body = responseWriter.toString();
        assertTrue(body.contains("\"code\":429"), "Response should contain 429 code");
        assertTrue(body.contains("Too many requests"), "Response should contain rate limit message");
    }

    @Test
    @DisplayName("GET requests should not be rate-limited")
    void getRequestsNotRateLimited() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");
        when(request.getMethod()).thenReturn("GET");

        for (int i = 1; i <= 15; i++) {
            boolean result = interceptor.preHandle(request, response, null);
            assertTrue(result, "GET request should always pass");
        }

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Admin paths are exempt from rate limiting")
    void adminPathsExempt() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/admin/dashboard");
        when(request.getMethod()).thenReturn("POST");

        for (int i = 1; i <= 15; i++) {
            boolean result = interceptor.preHandle(request, response, null);
            assertTrue(result, "Admin POST should always pass");
        }

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("Different IPs should not interfere with each other")
    void differentIpsIndependent() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        // First IP: 11 requests (first 10 pass, 11th blocked)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 1L);

        for (int i = 1; i <= 11; i++) {
            interceptor.preHandle(request, response, null);
        }

        // Different IP should be allowed (Lua returns 1 for first call)
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        when(request2.getHeader("X-Forwarded-For")).thenReturn("192.168.1.200");
        when(request2.getRequestURI()).thenReturn("/api/v1/posts");
        when(request2.getMethod()).thenReturn("POST");

        boolean result = interceptor.preHandle(request2, response, null);
        assertTrue(result, "Different IP should not be rate-limited");
    }

    @Test
    @DisplayName("X-Real-IP set by nginx wins over a spoofed X-Forwarded-For")
    void xRealIpWinsOverSpoofedXForwardedFor() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");
        when(request.getHeader("X-Real-IP")).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 7.7.7.7");

        ArgumentCaptor<List<String>> keyCaptor = ArgumentCaptor.forClass((Class) List.class);
        when(redisTemplate.execute(any(RedisScript.class), keyCaptor.capture(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("rate:limit:ip:192.168.1.100:/api/v1/posts", keyCaptor.getValue().get(0));
    }

    @Test
    @DisplayName("Without X-Real-IP the right-most X-Forwarded-For segment is used")
    void rightmostXForwardedForSegmentWins() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 192.168.1.100");

        ArgumentCaptor<List<String>> keyCaptor = ArgumentCaptor.forClass((Class) List.class);
        when(redisTemplate.execute(any(RedisScript.class), keyCaptor.capture(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("rate:limit:ip:192.168.1.100:/api/v1/posts", keyCaptor.getValue().get(0));
    }

    @Test
    @DisplayName("Login and register POST requests are rate-limited")
    void authPathsAreRateLimited() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        ArgumentCaptor<List<String>> keyCaptor = ArgumentCaptor.forClass((Class) List.class);
        when(redisTemplate.execute(any(RedisScript.class), keyCaptor.capture(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        assertEquals("rate:limit:ip:192.168.1.100:/api/v1/auth/login", keyCaptor.getValue().get(0));
    }

    @Test
    @DisplayName("Rate limiting is bypassed when Redis is unavailable")
    void redisUnavailable() throws Exception {
        interceptor = new RateLimitInterceptor();
        // redisTemplate stays null

        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        for (int i = 1; i <= 15; i++) {
            boolean result = interceptor.preHandle(request, response, null);
            assertTrue(result, "Request should pass when Redis unavailable");
        }
    }

    @Test
    @DisplayName("Exception from Redis should bypass rate limiting gracefully")
    void redisExceptionBypasses() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/posts");

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        boolean result = interceptor.preHandle(request, response, null);
        assertTrue(result, "Request should pass when Redis throws");
        verify(response, never()).setStatus(anyInt());
    }
}
