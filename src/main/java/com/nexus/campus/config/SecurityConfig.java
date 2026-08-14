package com.nexus.campus.config;

import com.nexus.campus.security.JwtAuthFilter;
import com.nexus.campus.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers the {@link JwtAuthFilter} as a servlet filter with an order
 * lower than the XSS filter (HIGHEST_PRECEDENCE + 1) so that:
 *
 * <ol>
 *   <li>XSS filter runs first and wraps the request</li>
 *   <li>JWT auth filter runs next and sets user attributes</li>
 *   <li>Controller receives the sanitised, authenticated request</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new JwtAuthFilter(jwtUtil));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.setName("jwtAuthFilter");
        registration.setEnabled(true);

        log.debug("[NEXUS-AUTH] JwtAuthFilter registered with order={}", Ordered.HIGHEST_PRECEDENCE + 1);

        return registration;
    }
}
