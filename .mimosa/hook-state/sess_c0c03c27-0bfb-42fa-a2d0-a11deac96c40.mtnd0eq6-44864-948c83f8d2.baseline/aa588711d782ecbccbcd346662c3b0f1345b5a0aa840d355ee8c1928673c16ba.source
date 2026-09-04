package com.nexus.campus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nexus.campus.config.RateLimitInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Core Spring MVC configuration.
 *
 * <p>Static-resource serving is declared here. The XSS filter is registered
 * separately in {@link XssConfig} at the highest servlet-filter precedence.</p>
 */
@Configuration
@EnableScheduling
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Value("${campus.upload.dir:src/main/resources/static/uploads}")
    private String uploadDir;

    @PostConstruct
    void initUploadDir() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (Exception e) {
            log.warn("Could not create upload directory: {}", e.getMessage());
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register",
                                 "/api/v1/posts/**", "/api/v1/comments/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + Paths.get(uploadDir).toAbsolutePath().toString().replace("\\", "/") + "/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
