package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SpringAiModelGatewayTest {

    @Test
    void healthProbeUsesTheProviderRootPlusV1Models() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestedPath = new AtomicReference<>();
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            AiServiceProperties properties = new AiServiceProperties();
            properties.getLlm().setBaseUrl(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            SpringAiModelGateway gateway = new SpringAiModelGateway(
                    mock(ChatClient.class), properties, RestClient.builder());

            assertTrue(gateway.isHealthy());
            assertEquals("/v1/models", requestedPath.get());
        } finally {
            server.stop(0);
        }
    }
}
