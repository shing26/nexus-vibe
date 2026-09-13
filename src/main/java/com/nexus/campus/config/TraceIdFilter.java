package com.nexus.campus.config;

import com.nexus.campus.util.TraceIds;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * Gives every request one trace id before anything else looks at it, and echoes
 * it back so the id survives all the way to the person reporting the bug.
 *
 * <p>The header is written before the chain runs on purpose: the paths that need
 * an id most are the ones that answer without ever reaching a controller, and a
 * filter that sets the header afterwards would be silent exactly when the
 * response is a 401, a 404 or a container error page.</p>
 */
public class TraceIdFilter implements Filter {

    private final boolean trustForwardedHeaders;

    public TraceIdFilter(boolean trustForwardedHeaders) {
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String inbound = trustForwardedHeaders
                ? ((HttpServletRequest) request).getHeader(TraceIds.HEADER)
                : null;
        String traceId = TraceIds.orGenerate(inbound);

        MDC.put(TraceIds.MDC_KEY, traceId);
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(TraceIds.HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
