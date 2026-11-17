# Chaos Theatre A — a public exhibit of the unreliable-LLM pipeline

Status: **frozen contract, 2026-11-17.** Base commit `a38fe0a` (master).
Branch: `codex/chaos-theater-a`. Worktree: `.worktrees/chaos-theater-a`.

## Goal

Make the project's thesis playable without an account and without spending a model
call: a signed-out visitor opens `/theatre`, picks one of four real failure scenarios,
and watches the production review pipeline react — repair, validate, retry, or
fail closed.

The exhibit is a **precomputed trace**, not a live model call. At startup the backend
runs the real `CodeReviewReviewer` through the real `LlmClient` over HTTP against a
local, scripted OpenAI-compatible server. The model output is injected; the HTTP
request, JSON repair, semantic validity gate, retry ladder and fail-closed policy are
the same code paths production uses. The UI must label every step as `REAL`,
`INJECTED`, or `POLICY`.

## Non-goals

- No anonymous POST endpoint and no visitor-supplied snippet in this slice. The first
  slice is an exhibit, not a public review service.
- No external LLM, no network dependency, no per-visitor cost.
- No database table, column, or migration. No post is created or modified.
- No new runtime dependency.
- No redesign of the forum. The theatre is an additional signed-out entry surface.

## Allowed paths

- `plans/chaos-theater-a.md` (this contract)
- `docs/tickets/chaos-theater-a.md`
- `docs/INDEX.md`
- `README.md` (link and test-count updates only)
- `src/main/java/com/nexus/campus/agent/ChaosLlmServer.java`
- `src/main/java/com/nexus/campus/agent/ChaosTraceService.java`
- `src/main/java/com/nexus/campus/dto/ChaosScenarioVo.java`
- `src/main/java/com/nexus/campus/dto/ChaosStepVo.java`
- `src/main/java/com/nexus/campus/controller/DemoController.java`
- `src/main/java/com/nexus/campus/security/JwtAuthFilter.java` (one public-GET prefix only)
- `src/test/java/com/nexus/campus/agent/ChaosTraceServiceTest.java`
- `src/test/java/com/nexus/campus/controller/DemoControllerIntegrationTest.java`
- `frontend/src/pages/TheatrePage.tsx`
- `frontend/src/pages/TheatrePage.test.tsx`
- `frontend/src/App.tsx`
- `frontend/src/pages/HomePage.tsx`
- `frontend/src/components/layout/Navbar.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

Any file outside this list requires a contract amendment before it is edited.

## API contract

`GET /api/v1/demo/chaos-scenarios` — public, no token, no side effects.

Returns `ApiResponse<List<ChaosScenarioVo>>` with exactly four scenarios, in this
order:

1. `repaired-json`
2. `empty-placeholder`
3. `prompt-injection`
4. `model-down`

`ChaosScenarioVo` fields:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable scenario id from the list above |
| `title` | string | Short exhibit label |
| `subtitle` | string | One-line description of what is about to happen |
| `lesson` | string | What the trace proves, in one sentence |
| `sampleCode` | string | The code block fed to the reviewer |
| `promptPreview` | string | The user message captured from the real HTTP request, truncated to 4000 chars |
| `rawOutput` | string | The scripted model's first raw response, truncated to 4000 chars |
| `repairedJson` | string or null | `JsonRepairUtil` output for the first raw response, truncated to 4000 chars |
| `score` | integer or null | Final verdict score, null when the scenario fails closed |
| `severity` | string or null | Final verdict severity, null when the scenario fails closed |
| `approved` | boolean or null | Final verdict approval, null when the scenario fails closed |
| `modelAttempts` | integer | HTTP requests the scripted server actually observed |
| `durationMs` | long | Wall-clock time of the real reviewer run |
| `failClosed` | boolean | True only for `model-down` |
| `steps` | array | Ordered `ChaosStepVo` rows |
| `adr` | string | Repository-relative ADR link relevant to the lesson |

`ChaosStepVo` fields:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable step id (`input`, `prompt`, `model`, `repair`, `validate`, `retry`, `verdict`, `fail-closed`, `reconcile`) |
| `title` | string | Short label |
| `detail` | string | What happened, in user-readable language |
| `mode` | string | `REAL`, `INJECTED`, or `POLICY` |
| `outcome` | string | `OK`, `WARN`, `FAIL`, or `INFO` |
| `payload` | string or null | Raw text/code shown in the expandable panel |
| `offsetMs` | long | Relative time within the trace |

## Scenario semantics

The backend must generate these by running `CodeReviewReviewer.review(VibePost)` with a
real `LlmClient` whose base URL points at a local `ChaosLlmServer`. No scenario may be
constructed by writing a trace DTO by hand.

### `repaired-json`

- Scripted first response is markdown-fenced JSON with a trailing comma and a truncated
  closing brace.
- `JsonRepairUtil.parse(rawOutput)` must succeed after repair.
- The final verdict must be valid, with a non-null score and severity.
- `modelAttempts` must be `1`.
- Steps must include `repair` with `repairedJson`, and `verdict`.

### `empty-placeholder`

- Scripted first response is schema-valid but semantically empty (uppercase placeholder
  fields, all below the validity gate's substance threshold).
- The real `ReviewValidity` gate must reject it; the reviewer must retry once with the
  reinforced prompt.
- The scripted second response is a substantive valid review.
- The final verdict must be valid; `modelAttempts` must be `2`.
- Steps must include `validate` (`FAIL`), `retry`, `validate` (`OK`), `verdict`.

### `prompt-injection`

- The sample code contains a comment instructing the model to ignore its system prompt
  and return a perfect score.
- The captured `promptPreview` must contain the per-request nonce delimiters
  (`---BEGIN CODE ...---` / `---END CODE ...---`) and the injection text inside them.
- The final review must state that the comment was treated as data and not followed;
  score must be at most 4.
- `modelAttempts` must be `1`.
- Steps must include `prompt` (`REAL`) and `verdict`.

### `model-down`

- The scripted server delays past the client timeout. The real `LlmClient` retries
  transport failures and opens its circuit breaker; the reviewer's retry then fails
  fast.
- `review(...)` must return `null`; `failClosed` must be `true`; `score`, `severity`
  and `approved` must be null.
- `modelAttempts` must be at least `3`.
- Steps must include `model` (`FAIL`, `INJECTED`), `fail-closed` (`POLICY`), and
  `reconcile` (`POLICY`).

## Acceptance criteria

1. A signed-out `GET /api/v1/demo/chaos-scenarios` returns HTTP 200 with exactly the
   four scenario ids in the order above. No token is required; no post or log row is
   written.
2. Every scenario has at least four steps. Every step has a valid `mode`. Each scenario
   has at least one `REAL` step and one `INJECTED` step; `model-down` also has a
   `POLICY` step.
3. The four scenario-specific semantics above hold, asserted by backend tests.
4. `/theatre` renders signed-out, lists the four scenarios, and can replay the selected
   trace step by step. The page shows a persistent disclosure: model output is
   scripted; repair, validation, retry and fail-closed are production code paths; no
   post is created.
5. Every replay step displays its `REAL` / `INJECTED` / `POLICY` badge. The UI never
   says or implies that the scripted model call is live.
6. The page works at 375px, honours `prefers-reduced-motion`, and exposes keyboard
   controls for scenario selection and replay.
7. The signed-out home page has a visible link to `/theatre`. The navbar may carry the
   same link for signed-in readers.
8. New backend tests cover all four scenarios and the public endpoint. New frontend
   tests cover loading, success, error, scenario switching, replay, and the disclosure.
   No existing test is deleted or weakened.
9. `mvn -B test`, `npm run test`, `npm run lint` and `npm run build` pass. The README
   test counts are updated if the counts change.

## Verification commands

Run from the worktree root:

```bash
mvn -B test -Dtest=ChaosTraceServiceTest,DemoControllerIntegrationTest
mvn -B test
cd frontend && npm run test
cd frontend && npm run lint
cd frontend && npm run build
```

Optional live probe after starting the app on the dev profile:

```bash
curl -sS http://localhost:8081/api/v1/demo/chaos-scenarios
```

The independent Verifier must re-run the commands and bind the evidence to the HEAD
SHA plus `git status --short`. Implementer logs and self-assessment are not evidence.

## Forbidden

- Hand-writing a trace fixture and serving it as if the pipeline produced it.
- Calling a real external model, or adding any network dependency to the demo path.
- Adding an anonymous POST endpoint or accepting visitor-supplied code in this slice.
- Changing `ReviewValidity`, `JsonRepairUtil`, `LlmClient` semantics to make a scenario
  pass; the demo may only call them.
- Adding a database table, column, migration, or new runtime dependency.
- Deleting, skipping, or weakening an existing test or assertion.
- UI copy that presents scripted output as live, or implies a post was published.
- Editing files outside the allowed paths without a contract amendment.

## Rejected alternatives

- **An anonymous `POST /demo/review` endpoint.** It makes the exhibit interactive, but
  it also creates a public cost and abuse surface, needs rate limiting and snippet
  quotas, and can fail when the model is down. That belongs to a later slice; this one
  must work offline and deterministically.
- **Static JSON fixtures shipped in the frontend.** They would be cheaper, but the claim
  "the real pipeline produced this" would be false, and the project's whole discipline
  is that evidence must be reproducible.
- **A live model call on page load.** ADR-0010 already rejected re-running the showcase
  review at startup for the same reasons: cost, non-determinism, and a page that fails
  exactly when the model is down.
- **A new chart/animation library.** The existing design tokens, `ReviewStepStrip` and
  motion primitives are enough; a dependency would be a cost with no new claim.
