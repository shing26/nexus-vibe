# Chaos Theatre A2 — fix-challenge, counterfactuals and the verdict payoff

Status: **done — Verifier PASS (2026-11-18, HEAD `f27748b`, clean tree).**
Contract: `plans/chaos-theater-a2.md` (frozen before implementation). Branch:
`codex/chaos-theater-a2`, stacked on `codex/chaos-theater-a`. The independent
Verifier re-ran all five commands itself: 555 Java tests / 0 failures with the
JaCoCo floor met, 140 frontend tests / 25 files, oxlint clean, production build
clean, and the live signed-out endpoint returned the five traces in contract
order with the 3→9 after-state, the cf-injection 10/low run and the model-down
fail-closed state all matching. Merge still requires written user sign-off.

## Scope

Slice A proved the pipeline can be replayed honestly; users called it too plain
("素"). A2 adds the payoff layer without touching the honesty rules:

1. **`fix-challenge`** — a fifth scenario, first in the list. The same buggy
   `divide` is reviewed twice against one scripted server: without the zero
   guard the verdict is 3/high; the visitor clicks "apply the fix" and the same
   pipeline re-scores the guarded snippet at 9/low, approved. The server branches
   on the real prompt content (`if (divisor == 0)`), so the branch is a property
   of the pipeline input, not of the UI state.
2. **Counterfactuals** — each of the other four scenarios carries a real
   pipeline run answering "what if the model had behaved": `cf-repaired`,
   `cf-placeholder`, `cf-alive`, and `cf-injection`, where the model obeys the
   injection and returns score 10 with substantive fields — which passes
   `ReviewValidity` legally. The lesson stays honest: the gate checks substance,
   not truth; the nonce delimiters are the defense.
3. **Verdict payoff** — an SVG score dial with a count-up (disabled under
   `prefers-reduced-motion`) and severity stamps: approved, needs-attention, and
   the red `UNPUBLISHED · 已入对账队列` fail-closed stamp.
4. **Evidence** — JSON syntax highlighting in payload panels; the
   `repaired-json` raw output carries a red trailing-comma and missing-brace
   marker, shown side by side with the repaired object.
5. **Chinese-first narration** — step titles come from a shared i18n pool,
   details are keyed per scenario (`details.<scenarioId>.<stepId>`) and per
   counterfactual (`cf.<cfId>.*`); the server's English strings are fallbacks.
   Scenario cards are localised too, with lucide icons and duration/attempts
   chips.
6. **Replay feel** — progress bar, current-step pulse (off under reduced
   motion), per-step `offsetMs` chips.

## Acceptance

- Backend: 555 tests, 0 failures (was 553); the JaCoCo line floor still holds.
  New tests pin the five-scenario order, the 3→9 fix semantics with the
  guard-string branch, the counterfactual runs (1 attempt each, valid verdicts),
  and the injection-obeyed score-10 verdict.
- Frontend: 140 tests / 25 files, 0 failures (was 136); `oxlint` clean;
  production build passes.
- The endpoint still requires no token, writes nothing, and calls no external
  model; `fix-challenge`'s `counterfactual` is null by contract.

## Files

`plans/chaos-theater-a2.md`, `docs/tickets/chaos-theater-a2.md`,
`docs/INDEX.md`, `README.md` (counts only),
`src/main/java/com/nexus/campus/agent/ChaosLlmServer.java`,
`src/main/java/com/nexus/campus/agent/ChaosTraceService.java`,
`src/main/java/com/nexus/campus/dto/ChaosScenarioVo.java`,
`src/main/java/com/nexus/campus/dto/ChaosCounterfactualVo.java` (new),
`src/main/java/com/nexus/campus/controller/DemoController.java` (javadoc count),
`src/test/java/com/nexus/campus/agent/ChaosTraceServiceTest.java`,
`src/test/java/com/nexus/campus/controller/DemoControllerIntegrationTest.java`,
`frontend/src/pages/TheatrePage.tsx`, `frontend/src/pages/TheatrePage.test.tsx`,
`frontend/src/i18n/locales/zh.json`, `frontend/src/i18n/locales/en.json`.

## Rejected

- **A visitor-editable code box** for fix-challenge: needs sanitisation, rate
  limiting and an abuse story; the guard-string branch keeps the lesson
  deterministic and free. Deferred beyond A2.
- **Marking counterfactuals `POLICY`**: they are real runs with injected
  answers; collapsing the labels would lie about what executed.
- **A chart library for the dial**: one SVG arc; a dependency buys nothing.
- **Localising the server `lesson` field**: it is the server fallback by
  design; the lesson line stays English this slice while the step narration —
  where the complaint was — is fully Chinese.
