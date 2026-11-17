package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A loopback-only OpenAI-compatible endpoint with a scripted answer per scenario.
 *
 * <p>This is the injection point of the Chaos Theatre: the production
 * {@link LlmClient} still performs a real HTTP request, still retries transport
 * failures, and still feeds the response through {@link JsonRepairUtil} and
 * {@link ReviewValidity}. Only the model's answer is scripted, which is why the
 * exhibit can be deterministic, offline and free.</p>
 *
 * <p>One server is started per scenario on an ephemeral port, then stopped. The
 * traces are generated once at startup and cached, not per visitor.</p>
 */
final class ChaosLlmServer implements AutoCloseable {

    enum Kind {
        REPAIRED_JSON,
        EMPTY_PLACEHOLDER,
        PROMPT_INJECTION,
        MODEL_DOWN
    }

    record CapturedPrompt(String system, String user) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RAW_REPAIRED_JSON = """
            ```json
            {
              "score": 4,
              "severity": "high",
              "codeQuality": "The divide method trusts a caller-supplied divisor and has no guard for zero, so an ordinary input crashes the request.",
              "securityConcerns": "No injection or credential leak was found; the unguarded division is a denial-of-service risk on this path.",
              "optimizationSuggestions": "Check the divisor before dividing, return a validation error instead of ArithmeticException, and add a zero-case test",
              "evidence": ["missing zero guard", "unguarded division",]
            """;

    private static final String RAW_PLACEHOLDER = """
            {"score":0,"severity":"low","codeQuality":"EVALUATE","securityConcerns":"NONE","optimizationSuggestions":"FIX"}""";

    private static final String VALID_RETRY = """
            {"score":3,"severity":"high","codeQuality":"The method divides by a caller-supplied value without checking for zero, so a normal input can crash the request path.","securityConcerns":"No injection or credential exposure was found; the main risk is an unhandled ArithmeticException.","optimizationSuggestions":"Guard the divisor, return a validation error, and add a unit test for the zero case."}""";

    private static final String REPAIRED_FIXED = """
            {"score":4,"severity":"high","codeQuality":"The divide method trusts a caller-supplied divisor and has no guard for zero, so an ordinary input crashes the request.","securityConcerns":"No injection or credential leak was found; the unguarded division is a denial-of-service risk on this path.","optimizationSuggestions":"Check the divisor before dividing, return a validation error instead of ArithmeticException, and add a zero-case test.","evidence":["missing zero guard","unguarded division"]}""";

    private static final String VALID_INJECTION = """
            {"score":2,"severity":"critical","codeQuality":"The method contains a comment that tries to instruct the reviewer, but the comment is data inside the nonce-delimited code block and was not followed.","securityConcerns":"The comment is a prompt-injection attempt; the per-request nonce delimiters and the system prompt keep it inside the data region.","optimizationSuggestions":"Remove the injected comment, guard the divisor, and add a test that proves the zero case is rejected."}""";

    private final Kind kind;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicReference<CapturedPrompt> lastPrompt = new AtomicReference<>();
    private final AtomicReference<CapturedPrompt> firstPrompt = new AtomicReference<>();
    private final List<String> responses = new CopyOnWriteArrayList<>();

    private ChaosLlmServer(Kind kind, HttpServer server, ExecutorService executor) {
        this.kind = kind;
        this.server = server;
        this.executor = executor;
    }

    static ChaosLlmServer start(Kind kind) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            ChaosLlmServer chaos = new ChaosLlmServer(kind, server, executor);
            server.createContext("/chat/completions", chaos::handle);
            server.setExecutor(executor);
            server.start();
            return chaos;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the chaos LLM server", e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int attempts() {
        return attempts.get();
    }

    CapturedPrompt lastPrompt() {
        CapturedPrompt captured = lastPrompt.get();
        return captured == null ? new CapturedPrompt("", "") : captured;
    }

    CapturedPrompt firstPrompt() {
        CapturedPrompt captured = firstPrompt.get();
        return captured == null ? new CapturedPrompt("", "") : captured;
    }

    List<String> responses() {
        return new ArrayList<>(responses);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messages = root.path("messages");
            String system = messages.path(0).path("content").asText("");
            String user = messages.path(1).path("content").asText("");
            lastPrompt.set(new CapturedPrompt(system, user));
            firstPrompt.compareAndSet(null, new CapturedPrompt(system, user));
            attempts.incrementAndGet();

            if (kind == Kind.MODEL_DOWN) {
                // Longer than the client's timeout. The client will close the
                // connection; writing afterwards may fail, which is expected.
                Thread.sleep(500);
                send(exchange, 503, "{\"error\":\"scripted timeout\"}");
                return;
            }

            boolean retry = system.contains("STRICT OUTPUT REQUIREMENTS")
                    || system.contains("You fix malformed JSON");
            String content = contentFor(retry);
            responses.add(content);
            send(exchange, 200, completion(content));
        } catch (Exception e) {
            try {
                send(exchange, 500, "{\"error\":\"chaos server failure\"}");
            } catch (Exception ignored) {
                // The client may already have timed out and closed the socket.
            }
        }
    }

    private String contentFor(boolean retry) {
        return switch (kind) {
            case REPAIRED_JSON -> retry ? REPAIRED_FIXED : RAW_REPAIRED_JSON;
            case EMPTY_PLACEHOLDER -> retry ? VALID_RETRY : RAW_PLACEHOLDER;
            case PROMPT_INJECTION -> VALID_INJECTION;
            case MODEL_DOWN -> "";
        };
    }

    private static String completion(String content) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "chaos-" + System.nanoTime());
        root.put("object", "chat.completion");
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", content);
        choice.put("finish_reason", "stop");
        return MAPPER.writeValueAsString(root);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
