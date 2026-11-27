package com.nexus.campus.config;

import com.nexus.campus.agent.CodeBlockReviewPolicy;
import com.nexus.campus.agent.CodeReviewReviewer;
import com.nexus.campus.agent.DefaultReviewPolicy;
import com.nexus.campus.agent.AiServiceClient;
import com.nexus.campus.agent.PromptReviewPolicy;
import com.nexus.campus.agent.PromptReviewReviewer;
import com.nexus.campus.agent.ReviewPolicy;
import com.nexus.campus.agent.Reviewer;
import com.nexus.campus.agent.ServiceReviewer;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.agent.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the two ADR-0012 seams. The trigger rule is replaced by defining
 * any {@link ReviewPolicy} bean; a reviewer is replaced by defining a
 * {@link Reviewer} bean with the same name. The conditions check the bean
 * name only, and the factory methods declare their concrete reviewer types:
 * the default {@link DefaultReviewPolicy} wires the code reviewer through
 * {@link CodeReviewReviewer}, and a factory method returning the interface
 * would leave that by-type injection nothing to match.
 */
@Configuration
public class ReviewPolicyConfig {

    @Bean
    @ConditionalOnMissingBean(ReviewPolicy.class)
    public ReviewPolicy reviewPolicy(LlmClient llmClient, CampusAiProperties aiProperties) {
        return new DefaultReviewPolicy(
                new CodeBlockReviewPolicy(new CodeReviewReviewer(llmClient, aiProperties)),
                new PromptReviewPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(name = "codeReviewReviewer")
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "direct", matchIfMissing = true)
    public CodeReviewReviewer codeReviewReviewer(LlmClient llmClient, CampusAiProperties aiProperties) {
        return new CodeReviewReviewer(llmClient, aiProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptReviewReviewer")
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "direct", matchIfMissing = true)
    public PromptReviewReviewer promptReviewReviewer(LlmClient llmClient, CampusAiProperties aiProperties) {
        return new PromptReviewReviewer(llmClient, aiProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "serviceReviewer")
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "service")
    public ServiceReviewer serviceReviewer(AiServiceClient client, CampusAiProperties aiProperties) {
        return new ServiceReviewer(client, aiProperties);
    }
}
