package com.nexus.campus.agent;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the archive contract at the client boundary: a successful call stores
 * the exact request JSON and the exact response JSON, and an archive that
 * throws does not change what the caller gets back. The reviewer's requirement
 * was that archiving is a side channel, never a dependency.
 */
class LlmClientArchiveTest {

    private static final String CANNED_RESPONSE =
            "{\"choices\":[{\"message\":{\"content\":\"archived answer\"}}]}";

    private HttpServer server;
    private final AtomicReference<String> receivedRequest = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            receivedRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = CANNED_RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private LlmClient client(String endpoint, LlmCallArchive archive) {
        return new LlmClient(endpoint, "", "test-model", Duration.ofSeconds(2),
                5, 60L, "none", false, archive, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("A successful call archives the raw request and the raw response")
    void successArchivesRawRequestAndResponse() {
        List<LlmCallRecord> records = new ArrayList<>();
        LlmClient client = client(endpoint(), records::add);

        String answer = client.chatCompletion("system prompt", "user body");

        assertThat(answer).isEqualTo("archived answer");
        assertThat(records).hasSize(1);
        LlmCallRecord record = records.get(0);
        assertThat(record.operation()).isEqualTo("chat completion");
        assertThat(record.outcome()).isEqualTo("success");
        assertThat(record.requestJson())
                .contains("system prompt")
                .contains("user body")
                .contains("test-model");
        assertThat(record.responseJson()).contains("archived answer");
        assertThat(receivedRequest.get()).isEqualTo(record.requestJson());
    }

    @Test
    @DisplayName("A broken archive cannot break the LLM answer")
    void archiveFailureDoesNotAffectTheCall() {
        LlmCallArchive exploding = record -> {
            throw new IllegalStateException("disk full");
        };

        String answer = client(endpoint(), exploding).chatCompletion("system", "user");

        assertThat(answer).isEqualTo("archived answer");
    }

    @Test
    @DisplayName("A failed attempt archives what was sent, with no response to show")
    void failureArchivesTheRequest() {
        List<LlmCallRecord> records = new ArrayList<>();
        // Nothing listens on port 1, so the retry ladder fails fast.
        LlmClient client = client("http://127.0.0.1:1/v1", records::add);

        assertThat(client.chatCompletion("system", "user")).isNull();

        assertThat(records).isNotEmpty();
        assertThat(records).allSatisfy(record -> {
            assertThat(record.outcome()).isEqualTo("failure");
            assertThat(record.requestJson()).contains("user");
            assertThat(record.responseJson()).isNull();
            assertThat(record.error()).isNotBlank();
        });
    }

    /**
     * A rejected request is the case the archive exists for: the provider
     * answered, and its answer is the only thing that says why. A 4xx arrives as
     * a thrown exception rather than a return value, so the body has to be
     * pulled back off the exception - otherwise it survives only as prose
     * inside {@code error}, and the raw JSON is gone.
     */
    @Test
    @DisplayName("A rejected request archives the error body the provider actually sent")
    void rejectedRequestArchivesTheErrorBody() throws IOException {
        String errorBody = "{\"error\":{\"message\":\"invalid api key\"}}";
        HttpServer rejecting = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        rejecting.createContext("/v1/chat/completions", exchange -> {
            byte[] body = errorBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        rejecting.start();

        try {
            List<LlmCallRecord> records = new ArrayList<>();
            String endpoint = "http://127.0.0.1:" + rejecting.getAddress().getPort() + "/v1";

            assertThat(client(endpoint, records::add).chatCompletion("system", "user")).isNull();

            assertThat(records).hasSize(1);
            LlmCallRecord record = records.get(0);
            assertThat(record.outcome()).isEqualTo("failure");
            assertThat(record.responseJson())
                    .as("the provider's error body is what the archive is for")
                    .contains("invalid api key");
        } finally {
            rejecting.stop(0);
        }
    }
}
