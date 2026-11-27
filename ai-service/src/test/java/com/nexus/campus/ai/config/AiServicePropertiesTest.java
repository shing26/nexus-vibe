package com.nexus.campus.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiServicePropertiesTest {

    @Test
    void stripsTheDirectPathV1SuffixFromTheProviderRoot() {
        AiServiceProperties properties = new AiServiceProperties();

        properties.getLlm().setBaseUrl("https://api.deepseek.com/v1/");

        assertEquals("https://api.deepseek.com", properties.getLlm().getBaseUrl());
    }

    @Test
    void keepsAProviderRootWithoutV1() {
        AiServiceProperties properties = new AiServiceProperties();

        properties.getLlm().setBaseUrl("http://ollama:11434");

        assertEquals("http://ollama:11434", properties.getLlm().getBaseUrl());
    }
}
