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
 * <p>An inbound {@code X-Trace-Id} is adopted only behind a trusted proxy
 * ({@code TRUST_FORWARDED_HEADERS}): on the public side of nginx that flag is
 * off, and honouring a client-chosen id would hand whoever sends it the content
 * of every log line for that request.</p>
 */
@Configuration
public class TraceIdConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(
            @Value("${campus.security.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(trustForwardedHeaders));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE - 1);
        registration.setName("traceIdFilter");
        return registration;
    }
}
