package com.nexus.campus.config;

import com.nexus.campus.agent.CodeBlockReviewPolicy;
import com.nexus.campus.agent.CodeReviewReviewer;
import com.nexus.campus.agent.DefaultReviewPolicy;
import com.nexus.campus.agent.PromptReviewPolicy;
import com.nexus.campus.agent.PromptReviewReviewer;
import com.nexus.campus.agent.ReviewPolicy;
import com.nexus.campus.agent.Reviewer;
import com.nexus.campus.config.CampusAiProperties;
import com.nexus.campus.agent.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    public ReviewPolicy reviewPolicy(CodeReviewReviewer codeReviewer) {
        return new DefaultReviewPolicy(
                new CodeBlockReviewPolicy(codeReviewer), new PromptReviewPolicy());
    }

    @Bean
    @ConditionalOnMissingBean(name = "codeReviewReviewer")
    public CodeReviewReviewer codeReviewReviewer(LlmClient llmClient, CampusAiProperties aiProperties) {
        return new CodeReviewReviewer(llmClient, aiProperties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptReviewReviewer")
    public PromptReviewReviewer promptReviewReviewer(LlmClient llmClient, CampusAiProperties aiProperties) {
        return new PromptReviewReviewer(llmClient, aiProperties);
    }
}
