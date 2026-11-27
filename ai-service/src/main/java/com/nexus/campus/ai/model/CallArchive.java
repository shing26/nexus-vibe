package com.nexus.campus.ai.model;

/**
 * One provider attempt as it crossed the wire. Implementations must never throw: an
 * archive that is down is a loss of evidence, not a failed review.
 */
public interface CallArchive {

    void record(Record record);

    CallArchive NOOP = record -> {
    };

    record Record(
            String timestamp,
            String requestId,
            String traceId,
            String kind,
            int attempt,
            String model,
            double temperature,
            long latencyMs,
            String outcome,
            String errorCode,
            int promptTokens,
            int completionTokens,
            String system,
            String user,
            String response) {
    }
}
