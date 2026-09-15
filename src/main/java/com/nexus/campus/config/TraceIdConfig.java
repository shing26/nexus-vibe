package com.nexus.campus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link TraceIdFilter} outside the XSS and JWT filters, one slot
 * ahead of both, so the id is already in MDC when they log.
 *
 * <p>An inbound {@code X-Trace-Id} is adopted only when
 * {@code campus.trace.trust-inbound-header} says so, which defaults to off. It is
 * deliberately not the {@code campus.security.trust-forwarded-headers} switch:
 * that one answers "can I believe a client IP here" and is on in production,
 * because nginx is in front of the app. Reusing it for trace ids meant a public
 * deployment was accepting client-chosen ids while its own rationale claimed the
 * opposite. Trusting a header is a per-header decision.</p>
 */
@Configuration
public class TraceIdConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(
            @Value("${campus.trace.trust-inbound-header:false}") boolean trustInboundHeader) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(trustInboundHeader));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE - 1);
        registration.setName("traceIdFilter");
        return registration;
    }
}
