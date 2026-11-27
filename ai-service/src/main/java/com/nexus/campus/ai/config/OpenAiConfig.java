package com.nexus.campus.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiConfig {

    @Bean
    public OpenAiApi openAiApi(AiServiceProperties properties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getLlm().getTimeout());
        factory.setReadTimeout(properties.getLlm().getTimeout());
        return OpenAiApi.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .apiKey(properties.getLlm().getApiKey())
                .restClientBuilder(restClientBuilder.requestFactory(factory))
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi api, AiServiceProperties properties) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.getLlm().getModel())
                        .temperature(0.2)
                        .build())
                .build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }
}
