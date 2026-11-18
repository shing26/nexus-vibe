package com.nexus.campus.dto;

import java.util.List;

/**
 * The "what if the model had behaved" answer for one failure scenario. Like the
 * failure trace it mirrors, it is produced by a real reviewer run against the
 * loopback scripted server, never written by hand.
 */
public record ChaosCounterfactualVo(
        String id,
        String label,
        String summary,
        int modelAttempts,
        long durationMs,
        Integer score,
        String severity,
        boolean failClosed,
        List<ChaosStepVo> steps) {
}
