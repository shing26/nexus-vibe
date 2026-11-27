package com.nexus.campus.ai.model;

public interface ModelGateway {

    ModelReply complete(String system, String user, double temperature);

    boolean isHealthy();

    record ModelReply(String content, int promptTokens, int completionTokens) {
    }
}
