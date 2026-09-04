package com.nexus.campus.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Servlet {@link Filter} that wraps every {@link HttpServletRequest} inside an
 * {@link XssHttpServletRequestWrapper} so that URL parameters and headers are
 * HTML-escaped and JSON request bodies are whitelist-sanitized (jsoup)
 * <em>before</em> the application code sees them.
 *
 * <p>Paths listed in the {@code exclude-path} init-parameter are skipped
 * (static resources, H2 console, etc.).</p>
 */
public class XssFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(XssFilter.class);

    private final List<String> excludedPaths = new ArrayList<>();

    @Override
    public void init(FilterConfig filterConfig) {
        String raw = filterConfig.getInitParameter("exclude-path");
        if (raw != null && !raw.isEmpty()) {
            String[] parts = raw.split(",");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    excludedPaths.add(trimmed);
                }
            }
        }
        log.info("[NEXUS-XSS] Filter initialized. Excluded paths: {}", excludedPaths);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Bypass trusted paths
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Attach XSS header to response so clients know we sanitise
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            if (!httpResp.containsHeader("X-XSS-Protection")) {
                httpResp.setHeader("X-XSS-Protection", "1; mode=block");
            }
        }

        chain.doFilter(new XssHttpServletRequestWrapper(httpRequest), response);
    }

    @Override
    public void destroy() {
        log.info("[NEXUS-XSS] Filter destroyed.");
    }

    private boolean isExcluded(String path) {
        for (String prefix : excludedPaths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
