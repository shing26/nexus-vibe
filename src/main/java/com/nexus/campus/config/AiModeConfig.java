package com.nexus.campus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.campus.agent.AiServiceClient;
import com.nexus.campus.agent.LlmClient;
import com.nexus.campus.agent.LlmHealthProbe;
import com.nexus.campus.agent.LlmSafetyClassifier;
import com.nexus.campus.agent.SafetyClassifier;
import com.nexus.campus.agent.ServiceSafetyClassifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Publishes exactly one model-call path per mode. The health probe is
 * {@code @Primary} because {@link LlmClient} and {@link AiServiceClient} both
 * implement the interface; the selected one is the only one the publish gate
 * and reconcile task see.
 */
@Configuration
public class AiModeConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "direct", matchIfMissing = true)
    public LlmHealthProbe directHealthProbe(LlmClient llmClient) {
        return llmClient;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "service")
    public LlmHealthProbe serviceHealthProbe(AiServiceClient client) {
        return client;
    }

    @Bean
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "direct", matchIfMissing = true)
    public SafetyClassifier llmSafetyClassifier(LlmClient llmClient) {
        return new LlmSafetyClassifier(llmClient);
    }

    @Bean
    @ConditionalOnProperty(name = "campus.ai.mode", havingValue = "service")
    public SafetyClassifier serviceSafetyClassifier(AiServiceClient client,
                                                    ObjectMapper objectMapper) {
        return new ServiceSafetyClassifier(client, objectMapper);
    }
}
