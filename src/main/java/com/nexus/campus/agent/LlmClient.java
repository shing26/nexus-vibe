package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Component
public class LlmClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public LlmClient(
            @Value("${campus.ai.llm.endpoint}") String endpoint,
            @Value("${campus.ai.llm.api-key:}") String apiKey,
            @Value("${campus.ai.llm.model}") String model,
            @Value("${campus.ai.llm.timeout}") Duration timeout) {
        this.model = model;
        this.objectMapper = new ObjectMapper();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /**
     * Sends a chat completion request to the OpenAI-compatible API with
     * exponential-backoff retries for transient failures.
     *
     * @param systemPrompt system-level instruction
     * @param userContent  user message content
     * @return the assistant's response text, or null on failure
     */
    public String chatCompletion(String systemPrompt, String userContent) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userContent);

        String text = withRetry(() -> postChatCompletion(requestBody), "chat completion");
        if (text == null) {
            log.warn("LLM response missing expected content");
        }
        return text;
    }

    /**
     * Sends a chat completion with Structured Outputs (JSON Schema enforcement).
     * The model is instructed to return valid JSON matching the provided schema.
     *
     * @param systemPrompt system-level instruction
     * @param userContent  user message content
     * @param schemaName   name for the JSON schema
     * @param jsonSchema   a JsonNode representing the JSON Schema definition
     * @return the assistant's response as a JsonNode (containing the parsed JSON), or null on failure
     */
    public JsonNode chatCompletionStructured(String systemPrompt, String userContent,
                                              String schemaName, JsonNode jsonSchema) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        // Add response_format for structured outputs
        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchemaWrapper = responseFormat.putObject("json_schema");
        jsonSchemaWrapper.put("name", schemaName);
        jsonSchemaWrapper.put("strict", true);
        jsonSchemaWrapper.set("schema", jsonSchema);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userContent);

        String contentJson = withRetry(() -> postChatCompletion(requestBody), "structured completion");
        if (contentJson == null) {
            log.warn("LLM structured completion failed, falling back to plain completion");
            return parseFallback(chatCompletion(systemPrompt, userContent));
        }

        try {
            // The content is a JSON string; parse it
            return objectMapper.readTree(contentJson);
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON response: {}", e.getMessage());
            return parseFallback(chatCompletion(systemPrompt, userContent));
        }
    }

    /**
     * Tries to interpret a plain completion as a JSON object (whole string,
     * then first {...} span) when structured outputs are unavailable.
     */
    private JsonNode parseFallback(String text) {
        if (text == null) return null;

        String trimmed = text.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {
            // fall through to brace extraction
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(trimmed.substring(start, end + 1));
            } catch (Exception ignored) {
                // fall through
            }
        }
        log.warn("Failed to parse plain completion as JSON: {}", trimmed);
        return null;
    }

    /**
     * POSTs a chat completion body and returns the assistant's content string.
     * Throws on transport/empty-response failures so the caller can retry.
     */
    private String postChatCompletion(ObjectNode requestBody) throws Exception {
        String json = objectMapper.writeValueAsString(requestBody);

        String response = restClient.post()
                .uri("/chat/completions")
                .body(json)
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM response was empty");
        }

        JsonNode root = objectMapper.readTree(response);
        String text = root.path("choices").path(0).path("message").path("content").asText(null);
        if (text == null) {
            throw new IllegalStateException("LLM response missing expected content path: " + response);
        }
        return text;
    }

    /**
     * Runs a fallible LLM call up to {@link #MAX_ATTEMPTS} times with
     * exponential backoff (500ms, 1s, ...). Returns null once attempts are
     * exhausted, matching the fail-open contract of the AI agents.
     */
    private <T> T withRetry(CheckedSupplier<T> action, String operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                log.warn("LLM {} failed (attempt {}/{}): {}", operation, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt >= MAX_ATTEMPTS) {
                    return null;
                }
                try {
                    Thread.sleep(INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
