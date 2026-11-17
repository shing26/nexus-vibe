package com.nexus.campus.dto;

import java.util.List;

/**
 * A precomputed, reproducible trace of one failure scenario in the review pipeline.
 * See {@code plans/chaos-theater-a.md} for the frozen contract.
 */
public record ChaosScenarioVo(
        String id,
        String title,
        String subtitle,
        String lesson,
        String sampleCode,
        String promptPreview,
        String rawOutput,
        String repairedJson,
        Integer score,
        String severity,
        Boolean approved,
        int modelAttempts,
        long durationMs,
        boolean failClosed,
        List<ChaosStepVo> steps,
        String adr,
        String fixedCode,
        Integer afterScore,
        String afterSeverity,
        Boolean afterApproved,
        List<ChaosStepVo> afterSteps,
        ChaosCounterfactualVo counterfactual) {
}
