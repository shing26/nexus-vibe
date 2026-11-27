package com.nexus.campus.ai.model;

public interface ModelGateway {

    /**
     * @param schema provider-enforced output schema, or {@code null} when structured
     *               output is disabled
     */
    ModelReply complete(String system, String user, double temperature, OutputSchema schema);

    boolean isHealthy();

    record ModelReply(String content, int promptTokens, int completionTokens) {
    }
}
