package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Slf4j
@Component
public class SpringAiModelGateway implements ModelGateway {

    private final ChatClient chatClient;
    private final String model;
    private final RestClient healthClient;

    public SpringAiModelGateway(ChatClient chatClient,
                                AiServiceProperties properties,
                                RestClient.Builder restClientBuilder) {
        this.chatClient = chatClient;
        this.model = properties.getLlm().getModel();
        this.healthClient = restClientBuilder
                .baseUrl(properties.getLlm().getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Authorization", "Bearer " + properties.getLlm().getApiKey())
                .requestFactory(requestFactory(Duration.ofSeconds(5)))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public ModelReply complete(String system, String user, double temperature) {
        try {
            ChatResponse response = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .build())
                    .call()
                    .chatResponse();

            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                throw new AiServiceException("model_unavailable", true, 502,
                        "provider returned no completion");
            }

            String text = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int promptTokens = usage == null ? 0 : usage.getPromptTokens();
            int completionTokens = usage == null ? 0 : usage.getCompletionTokens();
            return new ModelReply(text, promptTokens, completionTokens);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /**
     * A cheap liveness check: the OpenAI-compatible {@code /models} endpoint, not a
     * completion. It must not spend tokens on every scrape.
     */
    @Override
    public boolean isHealthy() {
        try {
            healthClient.get().uri("/models").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.debug("Provider health probe failed: {}", e.getMessage());
            return false;
        }
    }

    private AiServiceException translate(Exception error) {
        RestClientResponseException http = find(error, RestClientResponseException.class);
        if (http != null) {
            int status = http.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            return new AiServiceException(
                    retryable ? "model_unavailable" : "model_rejected",
                    retryable,
                    retryable ? 502 : 400,
                    "provider rejected the request: HTTP " + status);
        }
        if (find(error, SocketTimeoutException.class) != null
                || find(error, IOException.class) != null) {
            return new AiServiceException("model_timeout", true, 504,
                    "provider did not answer in time");
        }
        log.warn("Unexpected provider failure: {}", error.toString());
        return new AiServiceException("model_unavailable", true, 502,
                "provider call failed: " + error.getMessage());
    }

    private static <T extends Throwable> T find(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
