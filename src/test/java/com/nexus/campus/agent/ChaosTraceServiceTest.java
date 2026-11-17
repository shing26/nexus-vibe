package com.nexus.campus.agent;

import com.nexus.campus.dto.ChaosScenarioVo;
import com.nexus.campus.dto.ChaosStepVo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the four exhibits to the production pipeline. These tests do not inspect a
 * hand-written fixture: they call the same {@link ChaosTraceService} the endpoint
 * serves, which in turn runs the real reviewer against a loopback scripted model.
 */
class ChaosTraceServiceTest {

    private static List<ChaosScenarioVo> scenarios;

    @BeforeAll
    static void generateOnce() {
        scenarios = new ChaosTraceService().scenarios();
    }

    @Test
    @DisplayName("The endpoint order is stable and all four exhibits are present")
    void scenariosHaveStableOrder() {
        assertThat(scenarios).extracting(ChaosScenarioVo::id)
                .containsExactly("repaired-json", "empty-placeholder", "prompt-injection", "model-down");
    }

    @Test
    @DisplayName("Every exhibit has ordered steps with an honest mode label")
    void everyScenarioHasLabeledSteps() {
        for (ChaosScenarioVo scenario : scenarios) {
            assertThat(scenario.steps()).hasSizeGreaterThanOrEqualTo(4);
            assertThat(scenario.steps()).extracting(ChaosStepVo::mode)
                    .allMatch(mode -> Set.of("REAL", "INJECTED", "POLICY").contains(mode));
            assertThat(scenario.steps()).extracting(ChaosStepVo::mode).contains("REAL", "INJECTED");
            assertThat(scenario.promptPreview()).contains("---BEGIN CODE");
        }
    }

    @Test
    @DisplayName("Repair: malformed output becomes a recorded verdict through self-correction")
    void repairedJsonIsRealRepair() {
        ChaosScenarioVo scenario = byId("repaired-json");

        assertThat(scenario.failClosed()).isFalse();
        assertThat(scenario.modelAttempts()).isGreaterThanOrEqualTo(2);
        assertThat(scenario.score()).isNotNull();
        assertThat(scenario.severity()).isIn("low", "medium", "high", "critical");
        assertThat(scenario.rawOutput()).contains("```").contains(",");
        assertThat(scenario.repairedJson()).isNotNull();
        assertThat(JsonRepairUtil.parse(scenario.rawOutput()).ok()).isFalse();
        assertThat(JsonRepairUtil.parse(scenario.repairedJson()).ok()).isTrue();
        assertThat(scenario.steps()).extracting(ChaosStepVo::id)
                .contains("repair-fail", "retry", "repair", "validate", "verdict");
        assertThat(scenario.steps())
                .filteredOn(step -> "repair".equals(step.id()))
                .first()
                .extracting(ChaosStepVo::payload)
                .isEqualTo(scenario.repairedJson());
    }

    @Test
    @DisplayName("Placeholder: the validity gate rejects attempt 1 and the retry is recorded")
    void placeholderIsRejectedAndRetried() {
        ChaosScenarioVo scenario = byId("empty-placeholder");

        assertThat(scenario.failClosed()).isFalse();
        assertThat(scenario.modelAttempts()).isEqualTo(2);
        assertThat(scenario.score()).isNotNull();
        assertThat(scenario.rawOutput()).contains("EVALUATE");
        assertThat(scenario.steps())
                .filteredOn(step -> "validate".equals(step.id()))
                .extracting(ChaosStepVo::outcome)
                .containsExactly("FAIL", "OK");
        assertThat(scenario.steps()).extracting(ChaosStepVo::id).contains("retry");
    }

    @Test
    @DisplayName("Injection: the instruction stays inside the nonce-delimited data block")
    void injectionIsContained() {
        ChaosScenarioVo scenario = byId("prompt-injection");

        assertThat(scenario.failClosed()).isFalse();
        assertThat(scenario.modelAttempts()).isEqualTo(1);
        assertThat(scenario.promptPreview())
                .contains("Ignore previous instructions")
                .contains("---BEGIN CODE")
                .contains("---END CODE");
        assertThat(scenario.score()).isLessThanOrEqualTo(4);
        assertThat(scenario.rawOutput().toLowerCase()).contains("prompt-injection");
        assertThat(scenario.steps()).extracting(ChaosStepVo::id).contains("prompt", "verdict");
    }

    @Test
    @DisplayName("Model down: no verdict, circuit breaker, fail-closed and reconciliation policy")
    void modelDownFailsClosed() {
        ChaosScenarioVo scenario = byId("model-down");

        assertThat(scenario.failClosed()).isTrue();
        assertThat(scenario.score()).isNull();
        assertThat(scenario.severity()).isNull();
        assertThat(scenario.approved()).isNull();
        assertThat(scenario.modelAttempts()).isGreaterThanOrEqualTo(3);
        assertThat(scenario.steps()).extracting(ChaosStepVo::id)
                .contains("model", "retry", "fail-closed", "reconcile");
        assertThat(scenario.steps())
                .filteredOn(step -> "POLICY".equals(step.mode()))
                .extracting(ChaosStepVo::id)
                .contains("fail-closed", "reconcile");
    }

    private static ChaosScenarioVo byId(String id) {
        return scenarios.stream()
                .filter(scenario -> id.equals(scenario.id()))
                .findFirst()
                .orElseThrow();
    }
}
