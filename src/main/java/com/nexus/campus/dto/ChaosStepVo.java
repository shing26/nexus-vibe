package com.nexus.campus.dto;

/**
 * One row of a Chaos Theatre trace.
 *
 * <p>{@code mode} is the honesty label: {@code REAL} is a production code path,
 * {@code INJECTED} is the scripted model answer, and {@code POLICY} is a documented
 * behaviour the exhibit renders without writing a post. The UI must never collapse
 * these into one colour.</p>
 */
public record ChaosStepVo(
        String id,
        String title,
        String detail,
        String mode,
        String outcome,
        String payload,
        long offsetMs) {
}
