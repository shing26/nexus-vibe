package com.nexus.campus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.agent.AiServiceClient;
import com.nexus.campus.agent.CodeReviewReviewer;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.agent.LlmHealthProbe;
import com.nexus.campus.agent.LlmSafetyClassifier;
import com.nexus.campus.agent.PromptReviewReviewer;
import com.nexus.campus.agent.Reviewer;
import com.nexus.campus.agent.SafetyClassifier;
import com.nexus.campus.agent.ServiceReviewer;
import com.nexus.campus.agent.ServiceSafetyClassifier;
import com.nexus.campus.entity.VibePost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The mode switch must publish exactly one model-call path. A second live
 * path would mean a service-mode review could still call the provider from the
 * application, which is the credential boundary the ADR is about.
 */
class AiModeConfigTest {

    @Test
    @DisplayName("service mode publishes one service reviewer, the service probe and the service classifier")
    void serviceModeShouldPublishOnlyTheServicePath() {
        LlmClient llmClient = mock(LlmClient.class);
        AiServiceClient serviceClient = mock(AiServiceClient.class);

        try (AnnotationConfigApplicationContext context = context("service", llmClient, serviceClient)) {
            Map<String, Reviewer> reviewers = context.getBeansOfType(Reviewer.class);
            assertEquals(1, reviewers.size());
            assertInstanceOf(ServiceReviewer.class, reviewers.values().iterator().next());

            assertSame(serviceClient, context.getBean(LlmHealthProbe.class));
            assertInstanceOf(ServiceSafetyClassifier.class,
                    context.getBean(SafetyClassifier.class));

            when(serviceClient.review(any())).thenReturn(new AiServiceClient.ReviewResponse(
                    8, "low", "A sufficiently detailed quality analysis.",
                    "No security concerns were found.", "Extract a helper method.",
                    true, "{}", "test-model", new AiServiceClient.Usage(1, 1), 1, false,
                    List.of(), 0));
            VibePost post = new VibePost();
            post.setId(1L);
            post.setPostType("code");
            post.setContent("```java\nint x = 1;\n```");
            reviewers.values().iterator().next().review(post);

            verifyNoInteractions(llmClient);
        }
    }

    @Test
    @DisplayName("an unset mode stays direct: both reviewers, the direct probe and the direct classifier")
    void missingModeShouldStayDirect() {
        LlmClient llmClient = mock(LlmClient.class);
        AiServiceClient serviceClient = mock(AiServiceClient.class);

        try (AnnotationConfigApplicationContext context = context(null, llmClient, serviceClient)) {
            Map<String, Reviewer> reviewers = context.getBeansOfType(Reviewer.class);
            assertEquals(2, reviewers.size());
            assertInstanceOf(CodeReviewReviewer.class, reviewers.get("codeReviewReviewer"));
            assertInstanceOf(PromptReviewReviewer.class, reviewers.get("promptReviewReviewer"));

            assertSame(llmClient, context.getBean(LlmHealthProbe.class));
            assertInstanceOf(LlmSafetyClassifier.class,
                    context.getBean(SafetyClassifier.class));
            verifyNoInteractions(serviceClient);
        }
    }

    private static AnnotationConfigApplicationContext context(String mode,
                                                              LlmClient llmClient,
                                                              AiServiceClient serviceClient) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (mode != null) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("campus.ai.mode", mode)));
        }
        CampusAiProperties properties = new CampusAiProperties();
        properties.getLlm().setEndpoint("http://localhost:11434/v1");
        properties.getLlm().setModel("test-model");
        context.registerBean(CampusAiProperties.class, () -> properties);
        context.registerBean(LlmClient.class, () -> llmClient);
        context.registerBean(AiServiceClient.class, () -> serviceClient);
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.register(AiModeConfig.class, ReviewPolicyConfig.class);
        context.refresh();
        return context;
    }
}
