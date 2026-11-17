# Chaos Theatre A — a public exhibit of the unreliable-LLM pipeline

Status: **implemented, awaiting independent verification** (2026-11-18).
Branch `codex/chaos-theater-a`, worktree `.worktrees/chaos-theater-a`, contract frozen
at `plans/chaos-theater-a.md` (base master `a38fe0a`).

## Scope

The project's thesis — treat the LLM as an unreliable dependency — was only observable by
registering and publishing a post. This slice makes it playable without an account and
without a model call: a signed-out visitor opens `/theatre`, picks one of four failure
scenarios, and watches the production review pipeline react. The model output is a
pre-generated script; the HTTP request, JSON repair, semantic gate, retry ladder and
fail-closed policy are the production code paths, run once at startup against a loopback
scripted server and cached.

- `ChaosLlmServer` (loopback, ephemeral port) speaks the OpenAI wire format and returns a
  scripted answer per scenario, including a self-correction pass for the
  unparseable-first-response case and hard failures for the dead-model case.
- `ChaosTraceService` builds each trace by running the real `CodeReviewReviewer` over a
  real `LlmClient` pointed at that server — no trace is hand-written.
- `GET /api/v1/demo/chaos-scenarios` (public GET, `JwtAuthFilter` prefix addition) serves
  the four cached traces; no POST, no visitor input, no post or log row written.
- `/theatre` renders the four scenarios with a persistent scripted-output disclosure,
  per-step `REAL`/`INJECTED`/`POLICY` badges, expandable raw payloads, a fail-closed chip
  and an ADR link per exhibit. The signed-out home page links to it.

## Acceptance (all measured 2026-11-18 on this machine)

- `mvn -B test` → **Tests run: 553, Failures: 0, Errors: 0, Skipped: 0** +
  `All coverage checks have been met` + `BUILD SUCCESS`. New: `ChaosTraceServiceTest`
  (6) and `DemoControllerIntegrationTest` (1, unauthenticated request).
- `cd frontend && npm run test` → **136 tests / 25 files passed** (with
  `--pool=threads`; see Environment note). `npm run lint` clean, `npm run build` clean.
- The four scenario semantics from the frozen contract hold: repaired-json (two model
  calls through self-correction, verdict recorded), empty-placeholder (gate rejects,
  reinforced retry passes), prompt-injection (nonce-delimited data block, score ≤ 4),
  model-down (verdict null, fail-closed true, ≥ 3 transport attempts, POLICY steps).
- Every step carries an honesty badge; the page never claims the scripted call is live.

## Deviations

1. **Deviation 1 (contract-amended before implementation):** the contract originally
   fixed `repaired-json` at one model call with parser-only repair. Testing against the
   real ladder showed the parser repairs each defect alone but cannot combine truncation
   with a trailing comma, so the pipeline's actual answer is a self-correction retry —
   itself worth exhibiting. The contract in `plans/chaos-theater-a.md` was amended
   (modelAttempts 2, `repair-fail`/`retry`/`repair` steps, `repairedJson` = the accepted
   JSON) before the scenario was finalised.
2. **Environment note (not a deviation):** on this Windows machine, `npm run test`'s
   default forks pool intermittently fails worker spawn (`errno -4094`) inside the
   worktree; `-- --pool=threads` passes cleanly (25 files / 136 tests). The same plain
   command passes on master's tree. CI (Linux) is unaffected.

## Files

- Contract: `plans/chaos-theater-a.md`.
- Backend new: `agent/ChaosLlmServer.java`, `agent/ChaosTraceService.java`,
  `dto/ChaosScenarioVo.java`, `dto/ChaosStepVo.java`, `controller/DemoController.java`;
  tests `agent/ChaosTraceServiceTest.java`, `controller/DemoControllerIntegrationTest.java`.
- Backend modified: `security/JwtAuthFilter.java` (one public-GET prefix, in allowed
  paths).
- Frontend new: `pages/TheatrePage.tsx` (+ exported `TheatreEntry`),
  `pages/TheatrePage.test.tsx`. Modified: `App.tsx` (public route), `pages/HomePage.tsx`
  (signed-out link), `i18n/locales/zh.json` + `en.json` (new `theatre` namespace).
- Docs: this ticket, `docs/INDEX.md`, README badge/Testing section and the `/theatre`
  link paragraph.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test -- --pool=threads
cd frontend && npm run lint
cd frontend && npm run build
curl -sS http://localhost:8081/api/v1/demo/chaos-scenarios   # dev profile, optional
```

The independent Verifier must re-run these and bind evidence to HEAD SHA +
`git status --short`; implementer logs are not evidence.

## Rejected

- **An anonymous `POST /demo/review` endpoint.** Interactive, but it creates a public
  cost and abuse surface and can fail with the model. Later slice, with rate limiting and
  budgets.
- **Static JSON fixtures in the frontend.** Cheaper, and the claim "the real pipeline
  produced this" would be false — the exact class of mistake ADR-0010 records.
- **A live model call on page load.** Cost, non-determinism, and a page that breaks when
  the model is down.
- **Hand-writing traces.** Forbidden by the contract; every scenario is generated by
  running the real reviewer, and the tests pin semantics, not prose.
