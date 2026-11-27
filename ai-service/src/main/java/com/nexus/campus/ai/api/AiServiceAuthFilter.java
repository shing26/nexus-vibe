package com.nexus.campus.ai.api;

import com.nexus.campus.ai.config.AiServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared-key gate for the two inference endpoints. Health and metrics stay open so the
 * compose healthcheck and Prometheus do not need the key.
 */
@Component
public class AiServiceAuthFilter extends OncePerRequestFilter {

    private final AiServiceProperties properties;

    public AiServiceAuthFilter(AiServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/healthz")
                || path.startsWith("/readyz")
                || path.startsWith("/metrics")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String configured = properties.getService().getApiKey();
        if (configured != null && !configured.isBlank()) {
            String supplied = request.getHeader("X-AI-Service-Key");
            if (supplied == null || !MessageDigest.isEqual(
                    configured.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"code\":\"unauthorized\",\"message\":\"invalid service key\",\"retryable\":false}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
