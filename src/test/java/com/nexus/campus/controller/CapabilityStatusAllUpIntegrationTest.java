package com.nexus.campus.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B1's other half: with every dependency up the endpoint says nothing.
 *
 * <p>Elasticsearch is the awkward one - {@code ElasticsearchHealthIndicator} makes a real HTTP
 * request, so "up" cannot be faked with a property. A loopback stub answering 200 is the honest
 * way to get a green probe without requiring a cluster on the machine. The LLM is switched off
 * through the same flags an operator would use, which its indicator reports as UP
 * ({@code mode=disabled}), and Redis is off in the test profile.</p>
 */
@SpringBootTest(properties = {
        "campus.ai.review.enabled=false",
        "campus.ai.safety.enabled=false"
})
@AutoConfigureMockMvc
class CapabilityStatusAllUpIntegrationTest {

    private static final HttpServer ELASTICSEARCH = startElasticsearchStub();

    @DynamicPropertySource
    static void elasticsearchUri(DynamicPropertyRegistry registry) {
        registry.add("campus.es.uri",
                () -> "http://127.0.0.1:" + ELASTICSEARCH.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        ELASTICSEARCH.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Everything up means an empty list, so the banner has nothing to render")
    void everythingUpIsSilent() throws Exception {
        mockMvc.perform(get("/api/v1/status/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static HttpServer startElasticsearchStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                // The indicator only looks at the status line: 200 means the cluster answered.
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
