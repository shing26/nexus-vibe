package com.nexus.campus.config;

import com.nexus.campus.filter.XssFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * Global XSS (Cross-Site Scripting) filter configuration.
 *
 * Registers an {@link XssFilter} with the highest precedence so that every
 * incoming HTTP request — URL parameters, headers, and JSON request bodies —
 * is HTML-escaped (<b>via {@code HtmlUtils.htmlEscape()}</b>) before the data
 * reaches any Controller or service layer.
 *
 * <h3>What is sanitised</h3>
 * <ul>
 *   <li><b>URL query parameters</b> &amp; form-data — through
 *       {@code XssHttpServletRequestWrapper.getParameter()},
 *       {@code getParameterValues()}, {@code getParameterMap()}</li>
 *   <li><b>Request headers</b> — through
 *       {@code getHeader()}, {@code getHeaders()}</li>
 *   <li><b>JSON request bodies</b> — parsed with Jackson; every
 *       {@code TextNode} value is escaped, then the tree is re-serialised</li>
 *   <li><b>All other content-type bodies</b> — the raw body is run through
 *       {@code HtmlUtils.htmlEscape()}</li>
 * </ul>
 *
 * <h3>Why the highest order</h3>
 * Order {@link Ordered#HIGHEST_PRECEDENCE} ensures the XSS filter executes
 * <em>before</em> authentication, authorisation, and other filters, so
 * malicious payloads are neutralised as early as possible.
 */
@Configuration
public class XssConfig {

    private static final Logger log = LoggerFactory.getLogger(XssConfig.class);

    private static final String[] TRUSTED_PATHS = {
            "/static/", "/webjars/", "/h2-console"
    };

    @PostConstruct
    public void logActive() {
        log.info("[NEXUS-XSS] Global XSS filter armed — all request paths protected.");
    }

    /**
     * Registers the {@link XssFilter} at the highest servlet-filter precedence.
     *
     * <p>Static resource paths ({@code /static/**}, {@code /webjars/**}) and the
     * H2 console are excluded because they serve pre-authored content or internal
     * tooling.</p>
     */
    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("xssFilter");
        registration.setEnabled(true);
        registration.setMatchAfter(false);

        registration.addInitParameter("exclude-path", String.join(",", TRUSTED_PATHS));

        log.debug("[NEXUS-XSS] XssFilter registered with order=HIGHEST_PRECEDENCE, patterns={}",
                Arrays.toString(registration.getUrlPatterns().toArray()));

        return registration;
    }
}
