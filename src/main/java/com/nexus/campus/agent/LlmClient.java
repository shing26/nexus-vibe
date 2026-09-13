package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LlmClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    /** json_schema (native strict) | json_object (DeepSeek et al.) | none */
    private final String responseFormat;
    /**
     * DeepSeek V4 models think by default (reasoning lands in
     * reasoning_content, content stays empty and temperature is ignored).
     * This pipeline needs deterministic bounded-latency JSON, not a hidden
     * reasoning chain, so thinking is disabled unless explicitly wanted.
     */
    private final boolean thinkingDisabled;

    private final int breakerFailureThreshold;
    private final long breakerOpenMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong();

    /**
     * {@code llm_chat_completions_total{outcome}} counts logical calls, i.e. one per withRetry
     * exit, so the success rate answers "did the caller get an answer". The duration histogram
     * records each provider attempt instead: three 11s timeouts are three 11s samples, which is
     * what makes a slow provider look slow rather than looking rare. {@link #isHealthy()} feeds
     * neither; a probe is not a completion.
     */
    private final MeterRegistry meterRegistry;
    private final Timer completionTimer;

    public LlmClient(
            @Value("${campus.ai.llm.endpoint}") String endpoint,
            @Value("${campus.ai.llm.api-key:}") String apiKey,
            @Value("${campus.ai.llm.model}") String model,
            @Value("${campus.ai.llm.timeout}") Duration timeout,
            @Value("${campus.ai.llm.breaker.failure-threshold:3}") int breakerFailureThreshold,
            @Value("${campus.ai.llm.breaker.open-seconds:60}") long breakerOpenSeconds,
            @Value("${campus.ai.llm.response-format:json_schema}") String responseFormat,
            @Value("${campus.ai.llm.thinking-disabled:true}") boolean thinkingDisabled,
            MeterRegistry meterRegistry) {
        this.model = model;
        this.breakerFailureThreshold = breakerFailureThreshold;
        this.breakerOpenMillis = breakerOpenSeconds * 1000;
        this.responseFormat = responseFormat;
        this.thinkingDisabled = thinkingDisabled;
        this.objectMapper = new ObjectMapper();
        this.meterRegistry = meterRegistry;
        // Buckets follow the timeout ladder rather than a percentile histogram: the default
        // campus.ai.llm.timeout is 30s, so anything past 30s is a timeout, not a slow answer.
        this.completionTimer = Timer.builder("llm.chat.completion.duration")
                .description("Duration of a single LLM completion attempt")
                .serviceLevelObjectives(
                        Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
                        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30))
                .register(meterRegistry);
        Gauge.builder("llm.circuit.breaker.open", this, LlmClient::circuitOpenGauge)
                .description("1 while the LLM circuit breaker is open")
                .register(meterRegistry);
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Content-Type", "application/json")
                // Some OpenAI-compatible providers (e.g. NVIDIA NIM, model
                // dependent) answer with Content-Type: application/octet-stream
                // unless explicitly told what we accept — without this, Spring's
                // String converter rejects the body and every call fails.
                .defaultHeader("Accept", "application/json")
                .requestFactory(ClientHttpRequestFactories.get(settings));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.restClient = builder.build();
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
        return chatCompletion(systemPrompt, userContent, null);
    }

    public String chatCompletion(String systemPrompt, String userContent, Double temperature) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
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
        return chatCompletionStructured(systemPrompt, userContent, schemaName, jsonSchema, null);
    }

    /**
     * Sends a structured-output request and returns the RAW assistant text
     * (JSON string as produced by the model, fences and truncation included).
     * Callers that own their parsing (repair pipeline) use this; null on
     * unavailability, same fail-soft contract as the other entry points.
     */
    public String sendStructuredRequest(String systemPrompt, String userContent,
                                        String schemaName, JsonNode jsonSchema, Double temperature) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
        applyResponseFormat(requestBody, schemaName, jsonSchema);
        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", withSchemaInPrompt(systemPrompt, jsonSchema));
        messages.addObject().put("role", "user").put("content", userContent);
        return withRetry(() -> postChatCompletion(requestBody), "structured completion");
    }

    /**
     * In json_object/none modes the provider never sees the schema (it is not
     * part of response_format), so it must travel in the prompt — DeepSeek's
     * json_object guide requires the format description in the prompt anyway.
     * In native json_schema mode the wire format already enforces it.
     */
    private String withSchemaInPrompt(String systemPrompt, JsonNode jsonSchema) {
        if ("json_schema".equalsIgnoreCase(responseFormat) || jsonSchema == null) {
            return systemPrompt;
        }
        return systemPrompt
                + "\n\nThe JSON object must exactly match this schema — field names, types and enum values are fixed:\n"
                + jsonSchema.toString();
    }

    /**
     * Applies the configured structured-output mode: json_schema (native,
     * strict — OpenAI, NIM, DashScope), json_object (DeepSeek: schema-less
     * but guaranteed-JSON, structure carried by the prompt + repair-parse),
     * or none (no response_format at all).
     */
    private void applyResponseFormat(ObjectNode requestBody, String schemaName, JsonNode jsonSchema) {
        if (thinkingDisabled) {
            requestBody.putObject("thinking").put("type", "disabled");
        }
        if ("none".equalsIgnoreCase(responseFormat)) {
            return;
        }
        ObjectNode responseFormatNode = requestBody.putObject("response_format");
        if ("json_object".equalsIgnoreCase(responseFormat)) {
            responseFormatNode.put("type", "json_object");
            return;
        }
        responseFormatNode.put("type", "json_schema");
        ObjectNode jsonSchemaWrapper = responseFormatNode.putObject("json_schema");
        jsonSchemaWrapper.put("name", schemaName);
        jsonSchemaWrapper.put("strict", true);
        jsonSchemaWrapper.set("schema", jsonSchema);
    }

    public JsonNode chatCompletionStructured(String systemPrompt, String userContent,
                                              String schemaName, JsonNode jsonSchema, Double temperature) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }

        // Add structured-output mode per configuration
        applyResponseFormat(requestBody, schemaName, jsonSchema);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", withSchemaInPrompt(systemPrompt, jsonSchema));
        messages.addObject().put("role", "user").put("content", userContent);

        String contentJson = withRetry(() -> postChatCompletion(requestBody), "structured completion");
        if (contentJson == null) {
            log.warn("LLM structured completion failed, falling back to plain completion");
            return parseFallback(chatCompletion(systemPrompt, userContent, temperature));
        }

        try {
            // The content is a JSON string; parse it
            return objectMapper.readTree(contentJson);
        } catch (Exception e) {
            log.warn("Failed to parse structured JSON response: {}", e.getMessage());
            return parseFallback(chatCompletion(systemPrompt, userContent, temperature));
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
     *
     * <p>Only transient failures are retried: 429, 5xx, timeouts and I/O errors.
     * Permanent 4xx rejections (bad key, malformed request) fail immediately.
     * When the circuit breaker is open, the call fails fast with null.</p>
     */
    private <T> T withRetry(CheckedSupplier<T> action, String operation) {
        if (isCircuitOpen()) {
            log.warn("LLM circuit breaker open, failing fast for {}", operation);
            countCompletion("circuit_open");
            return null;
        }
        for (int attempt = 1; ; attempt++) {
            try {
                T result = timedAttempt(action);
                recordSuccess();
                countCompletion("success");
                return result;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()
                        && e.getStatusCode().value() != 429) {
                    log.warn("LLM {} rejected ({}), not retrying: {}", operation, e.getStatusCode(), e.getMessage());
                    recordFailure();
                    countCompletion("failure");
                    return null;
                }
                log.warn("LLM {} failed (attempt {}/{}): {}", operation, attempt, MAX_ATTEMPTS, e.getMessage());
            } catch (Exception e) {
                log.warn("LLM {} failed (attempt {}/{}): {}", operation, attempt, MAX_ATTEMPTS, e.getMessage());
            }
            if (attempt >= MAX_ATTEMPTS || isCircuitOpen()) {
                recordFailure();
                countCompletion("failure");
                return null;
            }
            try {
                Thread.sleep(INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                recordFailure();
                countCompletion("failure");
                return null;
            }
        }
    }

    private <T> T timedAttempt(CheckedSupplier<T> action) throws Exception {
        long startedAt = System.nanoTime();
        try {
            return action.get();
        } finally {
            completionTimer.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }

    private void countCompletion(String outcome) {
        // register() returns the existing meter for the same name and tags, so the three outcomes
        // share one counter instance instead of needing three fields.
        Counter.builder("llm.chat.completions")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private double circuitOpenGauge() {
        return isCircuitOpen() ? 1.0 : 0.0;
    }

    private boolean isCircuitOpen() {
        return circuitOpenUntil.get() > System.currentTimeMillis();
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= breakerFailureThreshold) {
            long until = System.currentTimeMillis() + breakerOpenMillis;
            circuitOpenUntil.set(until);
            consecutiveFailures.set(0);
            log.warn("LLM circuit breaker opened for {}s after {} consecutive failed calls",
                     breakerOpenMillis / 1000, failures);
        }
    }

    /**
     * Health probe used by the reconciliation task: a minimal chat completion
     * with a single attempt — no retries, no backoff — so a dead endpoint
     * costs one connect timeout (~{@code campus.ai.llm.timeout}) instead of
     * the full retry ladder, which would stall the shared scheduler thread.
     * Breaker state is left untouched: probing must not open or reset the
     * circuit.
     *
     * @return true when the LLM answered; false when unavailable or breaker open
     */
    public boolean isHealthy() {
        if (isCircuitOpen()) {
            return false;
        }
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.0);
            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", "You are a health probe.");
            messages.addObject().put("role", "user").put("content", "Reply with exactly: OK");
            String reply = postChatCompletion(requestBody);
            return reply != null;
        } catch (Exception e) {
            log.debug("LLM health probe failed: {}", e.getMessage());
            return false;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
