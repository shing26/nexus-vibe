package com.nexus.campus.dto;

/**
 * One row of a Chaos Theatre trace.
 *
 * <p>{@code mode} is the honesty label: {@code REAL} is a production code path,
 * {@code INJECTED} is the scripted model answer, and {@code POLICY} is a documented
 * behaviour the exhibit renders without writing a post. The UI must never collapse
 * these into one colour.</p>
 *
 * <p>There is deliberately no offset field. The rows are an ordered account of what
 * the pipeline did, not a stopwatch: the only timing this exhibit may claim is the
 * scenario's measured {@code durationMs}. An earlier version carried a hardcoded
 * per-step offset, which put "+1700 ms" beside a measured "4 ms" and made the
 * whole page look fabricated.</p>
 */
public record ChaosStepVo(
        String id,
        String title,
        String detail,
        String mode,
        String outcome,
        String payload) {
}
