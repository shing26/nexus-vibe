package com.nexus.campus.dto;

/**
 * One capability that is running degraded, phrased for a visitor.
 *
 * <p>Two fields, and that is a decision rather than an omission. The server has a much richer
 * document behind this (the actuator {@code deps} group: component names, status codes, probe
 * reasons, endpoints), and ADR-0007 keeps all of it closed at the edge. This record is the
 * shaped read model that replaces it: {@code key} is a stable identifier from a closed
 * vocabulary so the client can key dismissal off it, and {@code impact} is a sentence a person
 * can act on. Nothing here is copied from a dependency's own health document; the mapping from
 * component to capability lives in
 * {@code com.nexus.campus.config.health.DegradedCapabilityService}.</p>
 *
 * <p>Adding a field here is a contract change: {@code CapabilityStatusDtoContractTest} fails
 * until the new field is named on its allow-list, and that list is where the "never expose raw
 * dependency detail" rule is enforced for this endpoint.</p>
 */
public record CapabilityStatusVo(String key, String impact) {
}
