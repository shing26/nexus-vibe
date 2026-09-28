package com.nexus.campus.config;

import com.nexus.campus.agent.CodeBlockReviewPolicy;
import com.nexus.campus.agent.AiReviewService;
import com.nexus.campus.agent.ReviewPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the default review gate. A fork defines its own
 * {@link ReviewPolicy} bean and this one steps aside (ADR-0012).
 */
@Configuration
public class ReviewPolicyConfig {

    @Bean
    @ConditionalOnMissingBean(ReviewPolicy.class)
    public ReviewPolicy reviewPolicy(AiReviewService aiReviewService) {
        return new CodeBlockReviewPolicy(aiReviewService);
    }
}
