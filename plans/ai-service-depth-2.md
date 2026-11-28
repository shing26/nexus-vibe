# AI service depth 2 — warm start and review quality

Status: **frozen 2026-11-28**, base `master` `e96c706`, decision ADR-0026.
Two increments, each one branch and one PR, merged in order D0 → D1.

## Why this round exists

The 2026-11-28 live smoke (`docs/plans/pre-deployment-checklist.md`) and the two
committed baselines leave two facts that the next round has to answer:

- A warm review returns 200 in 53.1 s and an identical replay hits the content cache in
  0.03 s, but a cold model spends the first 60 s attempt on loading and the request only
  succeeds on the second attempt. With the application's 200 s read timeout the
  self-heal takes 89 s; without it the request is a 504.
- Increment C's evidence prompt cost quality: on the same 20 cases, `qwen2.5:3b` and
  temperature 0.2, C scored 0.70 / severity 0.25 / evidence 15/20 / invalid 0.05,
  while B scored 0.85 / severity 0.40 / evidence 0 / invalid 0. The evidence itself is
  useful, but the prompt that asks for it made the verdict worse.

## Goal

- **D0** removes the cold-start tax on the local stack without changing the review
  contract or the application.
- **D1** recovers B-level score/severity agreement while keeping C's evidence coverage,
  and records the new prompt as a new, reproducible baseline.

## Non-goals

- D2: enforcing `AI_EVIDENCE_REQUIRED=true`, counting `model_timeout` under the review
  error metric, and adding an `approved`/severity coherence rule.
- D3: rendering evidence in the review comment or the detail page.
- Switching models, RAG, embeddings, streaming, fine-tuning or agent/tool calling.
- Two-pass verdict/evidence extraction, direct-path prompt changes, API or schema field
  changes, database/frontend changes, or a new runtime dependency.

## D0 — warm provider

### Changes

- `docker-compose.yml`: set `OLLAMA_KEEP_ALIVE=-1` on the `ollama` service and add an
  `ollama-warmup` sidecar. The sidecar waits for `ollama` to be healthy, polls
  `ollama ps` once a minute, and runs `ollama run <model> OK` when the model is not
  loaded. It is local-only, a hosted-provider deployment omits it, and it has no
  review-path code.
- `.env.example`: document `OLLAMA_KEEP_ALIVE` and `WARMUP_POLL_SECONDS`.

### Acceptance

1. `docker compose config` renders `OLLAMA_KEEP_ALIVE: "-1"`, the `ollama-warmup`
   service, and its `OLLAMA_HOST`/`LLM_MODEL` environment.
2. The sidecar's `ollama ps` check is the only warmup path; it does not touch the
   review request, the breaker, the archive or the review metrics.
3. Live: `ollama stop qwen2.5:3b`, restart `ollama-warmup`, wait for the `warming`
   log and a non-empty `ollama ps`, then three consecutive `/v1/reviews` return 200,
   none is a 504, and the three-sample p95 is below 60 s.
4. After six minutes idle, `ollama ps` still lists the model (keep-alive effective).

## D1 — review quality recovery

### Changes

- `PromptFactory`: make `score` explicit as overall quality (10 = best, 0 = worst; not a
  risk score), add a severity rubric and score bands shared by code and prompt reviews,
  and keep the evidence instruction after the rubric. The model should classify the
  risk first, then quote the lines that support it.
- `ReviewSchema`: add the same rubric to the provider-enforced `severity` description and
  make the `score` description explicit.
- Tests: assert the rubric and score direction are present; update the literal prompt
  version fixture.
- Baselines: write `benchmark/eval/baseline-2026-11-28-quality.json`, regenerate
  `benchmark/parity/prompt-drift-baseline.json` for the intentional service-prompt
  change, and record both eval runs in
  `docs/research/ai-service-quality-2026-11-28.md`.

### Acceptance (20 review cases, `qwen2.5:3b`, temperature 0.2, local Ollama)

| metric | target | C baseline |
|---|---|---|
| score agreement | >= 0.85 | 0.70 |
| severity agreement | >= 0.55 | 0.25 |
| validWithEvidence / cases | >= 0.75 | 0.75 |
| invalid rate | <= 0.05 | 0.05 |
| case regressions vs C | <= 1 | - |

Two runs are recorded. The first run that meets every target becomes the new baseline;
if no run meets the targets, the PR is not merged and the result is recorded as a
rejected prompt.

## Allowed paths

- `ai-service/src/main/java/com/nexus/campus/ai/{model,service,config}/**`
- `ai-service/src/test/java/com/nexus/campus/ai/**`
- `ai-service/src/main/resources/application.yml`
- `docker-compose.yml`, `.env.example`
- `benchmark/eval/baseline-2026-11-28-quality.json`
- `benchmark/parity/prompt-drift-baseline.json`
- `docs/research/ai-service-quality-2026-11-28.md`
- `docs/research/prompt-parity-2026-11-28.md`
- `docs/tickets/ai-service-extraction.md`
- `docs/adr/0026-recover-review-quality-before-enforcing-evidence.md`
- `plans/ai-service-depth-2.md`
- `docs/INDEX.md`

## Forbidden

- Editing the direct-path prompts (`CodeReviewReviewer`, `PromptReviewReviewer`) or the
  application contract in this round.
- Changing the review request/response fields or the OpenAPI document.
- Weakening `scripts/test_ai_eval.py`, the prompt-parity gate or any existing assertion.
- Adding D2/D3 behavior, a new model, a second provider call, RAG, embeddings or a new
  datastore.
- Touching the unrelated dirty files in `D:\Nexus-Campus`.

## Verification

```bash
mvn -B -f ai-service/pom.xml test
mvn -B test
python scripts/test_ai_eval.py
python scripts/prompt_parity.py --check
docker compose -p nexus-vibe --project-directory D:\Nexus-Campus \
  -f D:\Nexus-Campus-deploy\docker-compose.yml config
```

The live D1 run is:

```bash
python scripts/ai_eval.py --base-url http://localhost:18002 --key <key> \
  --write-baseline benchmark/eval/baseline-2026-11-28-quality.json
```

The smoke commands and the two run summaries are committed with the PR. Test-bearing
commits are verified by an independent subagent, not by the implementer.
