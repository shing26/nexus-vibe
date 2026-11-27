package com.nexus.campus.agent;

import com.nexus.campus.config.CampusAiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client is the only place the application talks HTTP. A real local server
 * (not a mocked RestClient) keeps the assertions honest about headers,
 * timeouts, status mapping and the one-call-per-operation rule.
 */
class AiServiceClientTest {

    private HttpServer server;
    private AiServiceClient client;
    private CampusAiProperties properties;
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastTrace = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicBoolean providerReady = new AtomicBoolean(true);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::dispatch);
        server.start();

        properties = new CampusAiProperties();
        properties.getService().setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getService().setApiKey("test-key");
        properties.getService().setConnectTimeout(Duration.ofSeconds(2));
        properties.getService().setReadTimeout(Duration.ofSeconds(2));
        client = new AiServiceClient(properties, meters, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        server.stop(0);
    }

    @Test
    @DisplayName("Review request carries trace and key headers and maps the typed response")
    void reviewShouldMapTypedResponseAndHeaders() {
        MDC.put("traceId", "trace-123");

        AiServiceClient.ReviewResponse response = client.review(request());

        assertEquals(8, response.score());
        assertEquals("low", response.severity());
        assertEquals("solid code", response.quality());
        assertEquals("no findings", response.security());
        assertEquals("extract a method", response.suggestions());
        assertTrue(response.approved());
        assertEquals("result-json", response.resultJson());
        assertEquals("test-model", response.model());
        assertEquals(120, response.usage().promptTokens());
        assertEquals(40, response.usage().completionTokens());
        assertEquals(1234L, response.latencyMs());
        assertFalse(response.repaired());

        assertEquals("trace-123", lastTrace.get());
        assertEquals("test-key", lastKey.get());
        assertTrue(lastBody.get().contains("\"kind\":\"code\""));
        assertEquals(1.0, meters.get("ai.service.requests")
                .tag("operation", "review").tag("outcome", "success").counter().count());
    }

    @Test
    @DisplayName("Safety request maps the typed response")
    void safetyShouldMapTypedResponse() {
        AiServiceClient.SafetyResponse response = client.safety(
                new AiServiceClient.SafetyRequest("req-2", "trace-2", "hello", "comment"));

        assertEquals("safe", response.classification());
        assertEquals(0.91, response.confidence());
        assertEquals(List.of("ordinary discussion"), response.reasons());
        assertEquals("safety-json", response.resultJson());
    }

    @Test
    @DisplayName("A 502 error body becomes a retryable AiServiceException")
    void badGatewayShouldBeRetryable() {
        simulatePath("/v1/reviews", 502,
                "{\"code\":\"model_unavailable\",\"message\":\"provider down\",\"retryable\":true}");

        AiServiceClient.AiServiceException error = assertThrows(
                AiServiceClient.AiServiceException.class, () -> client.review(request()));

        assertEquals("model_unavailable", error.getCode());
        assertEquals(502, error.getStatus());
        assertTrue(error.isRetryable());
        assertEquals(1.0, meters.get("ai.service.requests")
                .tag("operation", "review").tag("outcome", "error").counter().count());
    }

    @Test
    @DisplayName("A 401 is mapped as non-retryable, so the application does not loop on a bad key")
    void unauthorizedShouldNotBeRetryable() {
        simulatePath("/v1/reviews", 401,
                "{\"code\":\"unauthorized\",\"message\":\"invalid service key\",\"retryable\":false}");

        AiServiceClient.AiServiceException error = assertThrows(
                AiServiceClient.AiServiceException.class, () -> client.review(request()));

        assertEquals("unauthorized", error.getCode(),
                "status=" + error.getStatus() + " message=" + error.getMessage());
        assertEquals(401, error.getStatus());
        assertFalse(error.isRetryable());
    }

    @Test
    @DisplayName("A hung service surfaces as ai_service_timeout after the read timeout")
    void readTimeoutShouldSurfaceAsTimeout() {
        properties.getService().setReadTimeout(Duration.ofMillis(150));
        client = new AiServiceClient(properties, meters, RestClient.builder());
        delayMillis = 1_000;

        long started = System.nanoTime();
        AiServiceClient.AiServiceException error = assertThrows(
                AiServiceClient.AiServiceException.class, () -> client.review(request()));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertEquals("ai_service_timeout", error.getCode());
        assertEquals(504, error.getStatus());
        assertTrue(error.isRetryable());
        assertTrue(elapsedMs < 1_500, "timeout must not wait for the delayed handler");
    }

    @Test
    @DisplayName("A 200 with no body is an invalid response, not a null verdict")
    void emptyBodyShouldBeUnavailable() {
        simulatePath("/v1/reviews", 200, "");

        AiServiceClient.AiServiceException error = assertThrows(
                AiServiceClient.AiServiceException.class, () -> client.review(request()));

        assertEquals("ai_service_invalid_response", error.getCode(), error.getMessage());
        assertEquals(502, error.getStatus());
        assertFalse(error.isRetryable());
    }

    @Test
    @DisplayName("A 200 whose body is not the contract is an invalid response")
    void malformedJsonShouldBeAnInvalidResponse() {
        simulatePath("/v1/reviews", 200, "not json");

        AiServiceClient.AiServiceException error = assertThrows(
                AiServiceClient.AiServiceException.class, () -> client.review(request()));

        assertEquals("ai_service_invalid_response", error.getCode());
        assertEquals(502, error.getStatus());
        assertFalse(error.isRetryable());
    }

    @Test
    @DisplayName("/readyz decides health; a 503 is false and a later 200 is true")
    void readyzShouldDriveHealth() {
        providerReady.set(false);
        assertFalse(client.isHealthy());

        providerReady.set(true);
        assertTrue(client.isHealthy());
    }

    @Test
    @DisplayName("A blank key is not sent at all")
    void blankKeyShouldNotBeSent() {
        properties.getService().setApiKey("");
        AiServiceClient unkeyed = new AiServiceClient(properties, meters, RestClient.builder());

        unkeyed.review(request());

        assertNull(lastKey.get());
    }

    private AiServiceClient.ReviewRequest request() {
        return new AiServiceClient.ReviewRequest("req-1", "trace-1", 42L, "code", "Title",
                "```java\nint x = 1;\n```", List.of("int x = 1;"),
                new AiServiceClient.Budget(12000, 60000));
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastTrace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
        lastKey.set(exchange.getRequestHeaders().getFirst("X-AI-Service-Key"));

        switch (path) {
            case "/readyz" -> respond(exchange, providerReady.get() ? 200 : 503,
                    providerReady.get() ? "{\"status\":\"ready\"}" : "{\"status\":\"not_ready\"}");
            case "/v1/reviews" -> {
                if (overriddenStatus != 0) {
                    respond(exchange, overriddenStatus, overriddenBody);
                } else if (delayMillis > 0) {
                    sleep(delayMillis);
                    respond(exchange, 200, reviewJson());
                } else {
                    respond(exchange, 200, reviewJson());
                }
            }
            case "/v1/safety" -> respond(exchange, 200,
                    "{\"classification\":\"safe\",\"confidence\":0.91,"
                    + "\"reasons\":[\"ordinary discussion\"],\"resultJson\":\"safety-json\","
                    + "\"model\":\"test-model\",\"latencyMs\":12}");
            default -> respond(exchange, 404, "{\"code\":\"not_found\",\"retryable\":false}");
        }
    }

    private volatile int overriddenStatus;
    private volatile String overriddenBody;
    private volatile long delayMillis;

    private void simulatePath(String expectedPath, int status, String body) {
        if (!expectedPath.equals("/v1/reviews")) {
            throw new IllegalArgumentException(expectedPath);
        }
        overriddenStatus = status;
        overriddenBody = body;
    }

    private static String reviewJson() {
        return "{\"score\":8,\"severity\":\"low\",\"quality\":\"solid code\","
               + "\"security\":\"no findings\",\"suggestions\":\"extract a method\","
               + "\"approved\":true,\"resultJson\":\"result-json\",\"model\":\"test-model\","
               + "\"usage\":{\"promptTokens\":120,\"completionTokens\":40},"
               + "\"latencyMs\":1234,\"repaired\":false}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
