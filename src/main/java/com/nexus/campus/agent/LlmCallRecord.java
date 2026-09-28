package com.nexus.campus.agent;

/**
 * One provider attempt as it crossed the wire.
 *
 * <p>The request is the exact JSON body, before any framework conversion; the
 * response is the exact body, or null when the attempt failed before a
 * response arrived. Kept as strings so the archive is a faithful copy rather
 * than a re-serialisation of a parsed model.</p>
 */
public record LlmCallRecord(
        String timestamp,
        String operation,
        String model,
        String requestJson,
        String responseJson,
        String outcome,
        String error) {
}
