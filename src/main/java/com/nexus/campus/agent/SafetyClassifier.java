package com.nexus.campus.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The model call behind both safety listeners. Direct mode calls the provider;
 * service mode calls the inference service. The listeners keep the four-class
 * handling policy either way.
 */
public interface SafetyClassifier {

    JsonNode classify(String content, Surface surface);

    enum Surface {
        POST,
        COMMENT
    }
}
