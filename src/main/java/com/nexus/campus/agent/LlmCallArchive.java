package com.nexus.campus.agent;

import com.nexus.campus.config.CampusAiProperties;

/**
 * Side channel for the raw LLM request/response of every provider attempt.
 *
 * <p>Implementations must not throw: an archive that is down is a loss of
 * evidence, not a reason to lose the answer. {@link #NOOP} is the default when
 * {@link CampusAiProperties.Archive#isEnabled()} is false.</p>
 */
@FunctionalInterface
public interface LlmCallArchive {

    LlmCallArchive NOOP = record -> {
    };

    void record(LlmCallRecord record);
}
