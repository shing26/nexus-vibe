# Chaos Theatre A2 — fix-challenge, counterfactuals and a verdict payoff

Status: **frozen contract, 2026-11-18.** Base commit `3df9da2`
(`codex/chaos-theater-a` HEAD). Branch: `codex/chaos-theater-a2`,
worktree `.worktrees/chaos-theater-a`.

## Goal

Slice A proved the pipeline can be replayed honestly. Slice A2 makes it feel
earned: a fifth scenario where the visitor applies the fix and watches the score
move, a counterfactual answer for every "what if the model had behaved" question,
a verdict dial that pays off the replay, and Chinese-first step narration.

Still no account, no real model, no visitor input, no post write.

## Allowed paths

- `plans/chaos-theater-a2.md` (this contract)
- `docs/tickets/chaos-theater-a2.md`
- `docs/INDEX.md`
- `README.md` (test-count updates only)
- `src/main/java/com/nexus/campus/agent/ChaosLlmServer.java`
- `src/main/java/com/nexus/campus/agent/ChaosTraceService.java`
- `src/main/java/com/nexus/campus/dto/ChaosScenarioVo.java`
- `src/main/java/com/nexus/campus/dto/ChaosCounterfactualVo.java` (new)
- `src/main/java/com/nexus/campus/controller/DemoController.java` (javadoc count only)
- `src/test/java/com/nexus/campus/agent/ChaosTraceServiceTest.java`
- `src/test/java/com/nexus/campus/controller/DemoControllerIntegrationTest.java`
- `frontend/src/pages/TheatrePage.tsx`
- `frontend/src/pages/TheatrePage.test.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

Any file outside this list requires a contract amendment before it is edited.

## API contract

`GET /api/v1/demo/chaos-scenarios` — public, unchanged endpoint. Now returns
exactly five scenarios in this order:

1. `fix-challenge`
2. `repaired-json`
3. `empty-placeholder`
4. `prompt-injection`
5. `model-down`

`ChaosScenarioVo` keeps every Slice A field and gains nullable additions:

| Field | Type | Meaning |
|---|---|---|
| `fixedCode` | string or null | The patched snippet; non-null only for `fix-challenge` |
| `afterScore` | integer or null | Verdict score of the re-review after the fix |
| `afterSeverity` | string or null | Severity of the after-fix verdict |
| `afterApproved` | boolean or null | Approval of the after-fix verdict |
| `afterSteps` | array or null | Ordered `ChaosStepVo` rows of the after-fix replay |
| `counterfactual` | object or null | The "what if the model had behaved" answer; null for `fix-challenge` |

`ChaosCounterfactualVo` fields:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | `cf-repaired`, `cf-placeholder`, `cf-injection`, or `cf-alive` |
| `label` | string | Short English label (server fallback; UI localises) |
| `summary` | string | One-sentence English summary |
| `modelAttempts` | integer | HTTP requests the counterfactual server observed |
| `durationMs` | long | Wall-clock time of the counterfactual reviewer run |
| `score` | integer | The counterfactual verdict score |
| `severity` | string | The counterfactual verdict severity |
| `failClosed` | boolean | Always false in this slice |
| `steps` | array | Ordered `ChaosStepVo` rows (`input`, `model`, `validate`, `verdict`) |

## Scenario semantics

All traces still come from running `CodeReviewReviewer.review(VibePost)` against
a loopback `ChaosLlmServer` with a real `LlmClient`. No trace may be hand-written.

### `fix-challenge` (new, first in list)

- Sample code is the same buggy `divide` snippet. The scripted server inspects
  the user message: without the guard `if (divisor == 0)` it answers a
  substantive score-3/high review; with the guard it answers score-9/low.
- The scenario's primary run is the buggy code: `score=3`, `severity=high`,
  `approved=false`.
- The service then re-reviews the fixed snippet (contains the guard) through the
  same server; the after-state is `afterScore=9`, `afterSeverity=low`,
  `afterApproved=true`, `afterSteps` non-empty.
- `modelAttempts` counts both runs (2).
- The counterfactual field is null: the fix is the visitor's own action, not a
  model hypothetical.

### Counterfactual runs

- `cf-repaired` (`repaired-json`): the model returns clean valid JSON first; 1
  attempt, valid verdict.
- `cf-placeholder` (`empty-placeholder`): the first answer is already the
  substantive review; 1 attempt, valid verdict.
- `cf-injection` (`prompt-injection`): the model *obeys* the injection and
  returns score-10/low with substantive fields, so it passes `ReviewValidity`
  deliberately. This is the honest lesson: the gate checks substance, not
  truth; the nonce delimiters are the real defense.
- `cf-alive` (`model-down`): the model is reachable and returns a valid review;
  1 attempt, valid verdict, no fail-closed.

## UI contract

1. Scenario cards: lucide icon, duration and attempts chips, active state.
2. Replay: progress bar, current-step pulse, per-step `offsetMs` chips;
   `prefers-reduced-motion` renders everything instantly.
3. Verdict payoff: SVG score dial with count-up (disabled under reduced motion)
   and a severity stamp. `model-down` gets the fail-closed stamp; `fix-challenge`
   after-state gets the approved stamp.
4. `fix-challenge` exposes an "apply the fix" control that swaps the buggy
   verdict dial for the after-fix dial and replays `afterSteps`.
5. Each non-`fix-challenge` scenario exposes a counterfactual toggle revealing
   the CF dial, summary and steps alongside the failure trace.
6. Payload panels syntax-highlight JSON; `repaired-json` marks the trailing
   comma and missing brace in the raw output and shows broken vs repaired
   side by side.
7. Step titles/details resolve through i18n keys (`steps.<id>` shared pool,
   `<scenarioId>.<id>` details, `cf.<cfId>.*`), falling back to the server's
   English strings; Chinese is the primary experience.
8. The Slice A disclosure and honesty badges stay; nothing presents scripted
   output as live.

## Acceptance criteria

1. The endpoint returns the five scenarios in the order above, signed-out, with
   the new fields present (`fix-challenge` non-null after-state; the other four
   non-null counterfactual; `fix-challenge` counterfactual null).
2. Backend tests pin: fix-challenge 3→9 semantics, the guard-string branch,
   CF semantics including the injection-obeyed score-10 verdict, and stable
   ordering. No existing assertion is deleted or weakened.
3. Frontend tests cover: the five-card list, fix-challenge apply-fix toggle,
   counterfactual toggle, dial/stamp payoff, payload highlighting presence,
   loading/error paths, and the disclosure.
4. The page works at 375px, honours `prefers-reduced-motion`, and stays
   keyboard-operable.
5. `mvn -B test`, `npm run test -- --pool=threads`, `npm run lint` and
   `npm run build` pass; README counts updated in the same change.

## Verification commands

```bash
mvn -B test -Dtest=ChaosTraceServiceTest,DemoControllerIntegrationTest
mvn -B test
cd frontend && npm run test -- --pool=threads
cd frontend && npm run lint
cd frontend && npm run build
```

The independent Verifier re-runs these and binds evidence to HEAD SHA plus
`git status --short`. Implementer logs are not evidence.

## Forbidden

Same as Slice A: no hand-written trace served as pipeline output, no real
external model, no anonymous POST or visitor-supplied code, no schema change,
no weakening of `ReviewValidity` / `JsonRepairUtil` / `LlmClient`, no new
runtime dependency, no deleting or weakening tests, no UI copy implying a live
model or a published post, no edits outside the allowed paths.

## Rejected alternatives

- **A visitor-editable code box for fix-challenge.** It would make the fix real,
  but it needs sanitisation, rate limiting and an abuse story; the scripted
  guard-string branch keeps the same lesson deterministic and free.
- **Marking the counterfactual as `POLICY`.** It is a real pipeline run with an
  injected answer, so `INJECTED`/`REAL` labels stay; collapsing them would lie
  about what ran.
- **A third-party chart library for the dial.** The score dial is one SVG arc;
  a dependency buys nothing the design tokens cannot draw.
