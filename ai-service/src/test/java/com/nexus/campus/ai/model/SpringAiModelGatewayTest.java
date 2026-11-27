package com.nexus.campus.ai.model;

import com.nexus.campus.ai.config.AiServiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.web.client.RestClient;
import com.nexus.campus.ai.service.ReviewSchema;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void theDefaultModeSendsAStrictProviderSchema() {
        AiServiceProperties properties = new AiServiceProperties();
        SpringAiModelGateway gateway = new SpringAiModelGateway(
                mock(ChatClient.class), properties, RestClient.builder());

        OpenAiChatOptions options = gateway.options(0.2, ReviewSchema.CODE_REVIEW);

        assertEquals("qwen2.5:3b", options.getModel());
        assertEquals(0.2, options.getTemperature());
        ResponseFormat format = options.getResponseFormat();
        assertNotNull(format);
        assertEquals(ResponseFormat.Type.JSON_SCHEMA, format.getType());
        assertEquals("code_review", format.getJsonSchema().getName());
        assertEquals(true, format.getJsonSchema().getStrict());
    }

    @Test
    void everyKindCarriesItsOwnSchemaNameAndStrictFlag() {
        AiServiceProperties properties = new AiServiceProperties();
        SpringAiModelGateway gateway = new SpringAiModelGateway(
                mock(ChatClient.class), properties, RestClient.builder());

        assertSchema(gateway, 0.2, ReviewSchema.CODE_REVIEW, "code_review");
        assertSchema(gateway, 0.2, ReviewSchema.PROMPT_REVIEW, "prompt_review");
        assertSchema(gateway, 0.0, ReviewSchema.SAFETY, "safety_classification");
    }

    @Test
    void jsonObjectModeDropsTheSchemaAndNoneDisablesTheFormat() {
        AiServiceProperties properties = new AiServiceProperties();
        SpringAiModelGateway gateway = new SpringAiModelGateway(
                mock(ChatClient.class), properties, RestClient.builder());

        properties.getLlm().setResponseFormat("json_object");
        OpenAiChatOptions jsonObject = gateway.options(0.2, ReviewSchema.CODE_REVIEW);
        assertEquals(ResponseFormat.Type.JSON_OBJECT, jsonObject.getResponseFormat().getType());

        properties.getLlm().setResponseFormat("none");
        assertNull(gateway.options(0.2, ReviewSchema.CODE_REVIEW).getResponseFormat());
    }

    private static void assertSchema(SpringAiModelGateway gateway, double temperature,
                                     com.nexus.campus.ai.model.OutputSchema schema,
                                     String expectedName) {
        ResponseFormat format = gateway.options(temperature, schema).getResponseFormat();
        assertNotNull(format);
        assertEquals(ResponseFormat.Type.JSON_SCHEMA, format.getType());
        assertEquals(expectedName, format.getJsonSchema().getName());
        assertEquals(true, format.getJsonSchema().getStrict());
    }
}
