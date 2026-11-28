# AI service extraction (P0)

Status: **verified and deployed on the local/demo stack as `20261127-02` — 16 PASS; PR #63.**
Contract: `plans/ai-service-extraction.md`. Decision: ADR-0024.

Verification record (independent verifier, not the implementer):

- Test-bearing tree: `b7f4210` (9 code/config/test files). This ticket update is the
  docs-only follow-up. Earlier independent verification bound `c18ead7`; the only later
  code delta is the health probe from 1 s to 750 ms per phase.
- `mvn -B test` → 602 tests, 0 failures, coverage floor met.
- `mvn -B -f ai-service/pom.xml test` → 67 tests, 0 failures (after increment A of ADR-0025).
- `git status --short` before and after verification: empty.
- Both compose renders checked: base `AI_MODE=service`, `ai` no ports / 512 MiB, `app`
  key empty, `ai` key non-empty and matching the app's service key; `AI_LLM_ENDPOINT`
  falls back to `LLM_ENDPOINT`. A hosted simulation
  (`https://api.deepseek.com/v1` + `deepseek-chat`) preserved the hosted endpoint and
  model. Direct overlay renders `AI_MODE=direct` with the key re-injected; `app.depends_on`
  keeps db/redis/elasticsearch/ollama and excludes `ai`.
- Endpoint normalization and the real `/v1/models` probe are pinned by
  `AiServicePropertiesTest` and `SpringAiModelGatewayTest`. The probe's connect+read
  worst case is 1.5 s, below `AiServiceClient`'s 2 s `/readyz` read timeout.
- CI on PR #63: backend, frontend, alert bridge, inference service and image-change
  workflows all pass (runs `46006974613` / `46006974649`).

## Deployment smoke (2026-11-27)

Release `20261127-02` was built from `master` `3f78308` and recreated on the local/demo
stack: `nexus-app`, `nexus-ai` and `nexus-web` all use the `20261127-02` image tag;
`nexus-app` and `nexus-ai` report healthy. The app's `/actuator/health/deps` reports
`llm: UP`, and `app -> ai:8000/readyz` returns `{"status":"ready"}`.

Smoke evidence, measured after pre-warming the local `qwen2.5:3b` model:

- `POST /v1/reviews` returned HTTP 200 with `model=qwen2.5:3b`, `score=2`,
  `severity=low`, `approved=false`, `repaired=false`, 288 prompt tokens / 98 completion
  tokens, and 14.6 s provider latency (15.4 s wall time).
- The same endpoint without `X-AI-Service-Key` returned HTTP 401.
- `https://qualifier-discuss-marry.ngrok-free.dev/` returned 200 with the SPA document;
  `/api/posts` returned 401 and `/actuator/health` returned 200, so the public front
  door and auth boundary are live. The public checks send
  `ngrok-skip-browser-warning: true`; without it ngrok answers 200 with its own
  warning page, which would look like a working site.
- Runtime config: `AI_MODE=service`, `SPRING_PROFILES_ACTIVE=prod`,
  `AI_LLM_ENDPOINT=http://ollama:11434/v1`, `AI_LLM_TIMEOUT=30s`, app provider key empty,
  `CAMPUS_AI_MENTION_ENABLED=false`.

This smoke uses the local Ollama provider, not the hosted production model. Cold start
was measured rather than assumed: after `ollama stop qwen2.5:3b`, a call with the
application's real budget (`AI_SERVICE_READ_TIMEOUT` 120 s minus 5 s) exhausted the
first 30 s provider attempt, the retry succeeded, and the request returned 200 with a
45 s wall time (44.5 s provider latency). A warm call with the same payload returned
200 in 12 s.

So cold start is slow but not a failure under the current three-attempt budget: two
attempts (90.5 s with backoff) fit inside 115 s. It stays a P2 because it spends the
retry and takes 45 s; raising `AI_LLM_TIMEOUT` to 45 s with one attempt, or pinning the
model with `OLLAMA_KEEP_ALIVE=-1` plus a startup warm-up, are the two ways to remove
that wait. Neither is applied here: the first is an `.env` change that would diverge
from the committed 30 s default, and the second needs a compose change and a PR.

## Prompt-drift measurement (2026-11-28)

Acceptance #3 was PARTIAL because the parity test stubs the model response and no test
compared the prompts the two modes actually send. `PromptTextCorpusTest` now renders
both sides from `benchmark/parity/review-fixtures.json` (eight fixtures) with nonces
normalized to `NONCE`, and `scripts/prompt_parity.py` measures the diff against
`benchmark/parity/prompt-drift-baseline.json`. The full method and the numbers are in
`docs/research/prompt-parity-2026-11-28.md`; `.github/workflows/prompt-parity.yml`
runs the same comparison and fails if drift grows past 0.005.

Mean line similarity is 0.9417. Seven of eight fixtures differ only in the system
prompt's schema sentence, and their user messages are byte-identical. `multi-block`
is the one semantic difference: direct mode drops the third 14,400-character block at
the 9,400-token filter budget, while the service truncates each block at 42,000
characters and sends all three (1,826 vs 2,729 lines). Criterion #3 is PASS on that
basis; the payload difference is pinned by the baseline and left as a follow-up,
because making the service enforce a total budget is a behaviour change.

## Increment A — capability parity (2026-11-28)

ADR-0025's first increment restores what the extraction dropped and stops duplicate
spend:

- **Provider-enforced output.** `AI_LLM_RESPONSE_FORMAT` selects `json_schema`
  (default), `json_object` or `none`. The service owns the code, prompt and safety
  schemas and sends them as `response_format`; the application request contract did not
  change. Ollama accepted `json_schema` on this stack.
- **Content-addressed deduplication.** The key is a SHA-256 of kind, title, content,
  snippets, model and prompt version; `requestId` is not used because the application
  mints a new trace id per dispatch. Concurrent duplicates coalesce, valid responses are
  cached for 10 minutes, and errors or invalid verdicts are never cached.
- **Circuit breaker.** Five consecutive provider failures open the breaker for 30
  seconds; the next call fails fast with 503 and `model_unavailable` instead of consuming
  the caller's budget. A single half-open probe closes it on success; a probe that never
  reports back is reclaimed after `AI_LLM_BREAKER_PROBE_SECONDS` (default 35), so an
  escaped `Error` cannot leave the service answering 503 forever. Only the current
  probe permit may report, so a stale attempt cannot close or reopen the circuit.
- **Call archive.** One JSONL line per provider attempt when `AI_CALL_ARCHIVE` names a
  file; an unwritable path is logged and does not fail the review.

Live evidence on the local stack (temporary container built from the branch jar, pointed
at `ollama:11434`): the first `POST /v1/reviews` took 24.4 s and returned `score=5`,
`severity=medium`, `repaired=false`; the identical second call took **17 ms**, and
`/metrics` showed `ai.provider.calls{outcome="success"}=1` with
`ai.review.dedup{outcome="hit"}=1` and `{outcome="miss"}=1`. `ai-service` now has 67
tests, 0 failures.

## Increment B — measurement (2026-11-28)

Increment A made the service deeper; increment B makes its quality measurable:

- **Prompt version.** `PromptFactory.version()` is the first 12 hex characters of a
  SHA-256 over the static templates and schemas. It is returned as the
  `X-Prompt-Version` response header on `/v1/reviews` and `/v1/safety`, logged on every
  request, and written into call-archive records. A new literal-hash assertion in
  `PromptFactoryTest` (`4507b4663c31`) fails on any template edit until the change is
  deliberate.
- **Golden set.** `benchmark/eval/review-cases.json` has 20 cases (defects, clean code,
  prompt quality, four injected languages) with an expected score band, defensible
  severities and a rationale; `benchmark/eval/safety-cases.json` has 12 cases across
  `post`/`comment` and the four classes.
- **Harness.** `scripts/ai_eval.py` posts each case, reports score/severity/class
  agreement, invalid rate, repair rate, nearest-rank p50/p95 latency, token totals and
  evidence counts, writes JSON, and turns a run into a gate with
  `--baseline <file> --tolerance 0.05` (agreement may not drop, `invalidRate` may not
  rise), plus a per-case check: more than one case that passed in the baseline may
  not stop passing (`--case-regressions`), and a missing case or section is a
  failure. A truncated baseline cannot switch a gate off. `--fail-below <ratio>`
  is the absolute floor. `scripts/test_ai_eval.py` covers corpus validation, the
  maths, both gate directions, the constant-`4/medium` case and an end-to-end
  stub-server run: **38 tests**, no model.
- **CI.** `.github/workflows/ai-eval.yml` runs those 38 tests. It does not call a model,
  because pull requests have no provider credential; the baseline is a local/nightly
  act.

Live baseline on the same local stack (`qwen2.5:3b`, prompt version `4507b4663c31`,
temporary container on port 18001):

| metric | review (20) | safety (12) |
|---|---|---|
| invalid | 0 | 0 |
| agreement | score 0.85, severity 0.40 | class 0.9167 |
| repair rate | 0 | n/a |
| p50 / p95 | 15.0 s / 27.8 s | 7.8 s / 21.1 s |
| tokens | 6,032 / 2,171 | n/a |

The honest reading is in `docs/research/ai-service-eval-2026-11-28.md`: the model never
returned `high` or `critical`, so severity agreement is 0.40 even though score agreement
is 0.85; `injection-forged-delimiter` was the one safety miss. The baseline is committed
as `benchmark/eval/baseline-2026-11-28.json`. The harness tests are now **38 Python
tests**, in addition to the 69 ai-service and 602 application tests.

## Increment C — evidence anchoring (2026-11-28)

The third increment makes the model's claims checkable:

- **Verifier.** `EvidenceExtractor` matches each quote against the submitted
  content, then each snippet, after collapsing whitespace runs to one space. It
  rejects blank, shorter-than-10-character, duplicate and unmatched quotes,
  caps the accepted set at five, and returns the source's own text with 1-based
  `startLine`/`endLine`.
- **Contract.** `ReviewResponse` carries `evidence: [{quote,startLine,endLine}]`
  and `evidenceRejected: int`; `resultJson` carries the same verified objects so
  they persist with the verdict. The application `AiServiceClient` record and
  the OpenAPI document are updated, and the two contract tests pin the names.
- **Policy.** `AI_EVIDENCE_REQUIRED` defaults to `false`. When true, an
  otherwise-valid verdict with no accepted quote is returned with severity
  `unknown`, which the application's `ReviewValidity` already rejects. Evidence
  is not rendered into the comment body.

Acceptance evidence: `EvidenceExtractorTest` covers a real quote on the correct
line, a reflowed multi-line quote, fabricated/short/duplicate rejection, snippet
matching and the five-quote cap; `ReviewServiceTest` covers the required-empty
downgrade and acceptance on the repair pass. The live run
(`benchmark/eval/baseline-2026-11-28-evidence.json`, prompt `eb5421e62edc`)
accepted 27 quotes, rejected 29, had 15/20 cases with evidence and one 504
timeout; the analysis is in
`docs/research/ai-service-evidence-2026-11-28.md`. `ai-service` is now **83
tests**.

## Increment D0/D1 — warm start and quality recovery (2026-11-28)

ADR-0026 and frozen contract `plans/ai-service-depth-2.md`. D0 and D1 are
separate PRs; D2/D3 are not opened in this round.

**D0 — warm provider.** `docker-compose.yml` sets `OLLAMA_KEEP_ALIVE=-1` and
adds the `ollama-warmup` sidecar; `.env.example` documents both controls. The
rendered configuration was verified without a daemon:

```powershell
docker compose -p nexus-vibe --project-directory D:\Nexus-Campus `
  -f D:\Nexus-Campus-deploy\docker-compose.yml config --format json
```

It renders `OLLAMA_KEEP_ALIVE: "-1"`, `ollama-warmup` with
`OLLAMA_HOST=http://ollama:11434`, `LLM_MODEL=qwen2.5:3b`,
`WARMUP_POLL_SECONDS=60`, a 128 MiB limit, and the `ollama ps`/`ollama run`
loop. **The live acceptance is blocked, not passed**: Docker Desktop on this
machine crashes while starting its `dockerInference` and
`docker-secrets-engine` unix sockets (`The file cannot be accessed by the
system`). A directory rename plus a full stop/start did not recover it. The
container-level check (`ollama stop`, restart warmup, wait for `warming`, three
**distinct** 200s, the Ollama-restart race observation, six-minute idle check)
remains for a host where Docker starts. Distinct payloads keep the content cache
from making a replay look warm.

**D1 — quality recovery.** Prompt `c2992c58aed9` met the frozen targets:
score 0.90 (target 0.85), severity 0.90 (target 0.55), 17/20 cases with
accepted evidence (target 15/20), invalid 0.00 (target <= 0.05), and 1
case regression against C (target <= 1). The run is
`benchmark/eval/baseline-2026-11-28-quality.json`; the method, the eight prompt
iterations (including three rejected band-alignment runs), and the two remaining
case misses are in
`docs/research/ai-service-quality-2026-11-28.md`. The service suite is **83
tests**. Evidence enforcement stays off because 3/20 cases still have no accepted
quote; that is D2's call.

The accepted prompt has a known textual debt: its score bands say medium 5-6 and
high 3-4, while examples use medium 4 and high 2. The later alignment runs either
missed the score target or exceeded the case-regression gate, so v5 is kept as the
measured baseline rather than replacing it with a better-looking aggregate.

## Scope

Move the model-facing half of the AI pipeline into an independent Java 21 / Spring Boot
service under `ai-service/`, and route review and safety through it when
`campus.ai.mode=service`. Java keeps the trigger rule, lease, attempt ladder,
`ReviewValidity`, persistence, comment rendering, fail-closed handling and
reconciliation. The direct path stays available and remains the code default.

In scope:

- `POST /v1/reviews`, `POST /v1/safety`, `/healthz`, `/readyz`, `/metrics`.
- Spring AI 1.1.8 on Boot 3.5.15; independent Maven build and CI job.
- `ServiceReviewer`, `SafetyClassifier` (direct and service), `LlmHealthProbe`, and the
  shared `VerdictRenderer`.
- Compose `ai` service (internal only, 512 MiB cap), Prometheus target, `.env.example`
  entries, and a direct-mode overlay for rollback.
- OpenAPI 3.1 document and a contract test on each side.

Out of scope: retrieval/RAG, streaming, chat, model training, frontend changes, schema
changes, a shared contract module, and flipping the production default in this ticket.

## Acceptance

The numbered acceptance criteria live in `plans/ai-service-extraction.md`. The
implemented test mapping:

| Criterion | Evidence |
|---|---|
| 1 | `mvn -B test` (direct mode, no test removed) |
| 2 | `AiModeConfigTest` — one `ServiceReviewer`, `AiServiceClient` probe, zero `LlmClient` interaction |
| 3 | `ReviewParityTest` — nine fixtures, including the eight named cases; layered evidence: direct-vs-typed/renderer parity plus `ReviewServiceTest` mapping. Prompt-text drift is now measured on eight prompt-producing fixtures by `docs/research/prompt-parity-2026-11-28.md`: mean line similarity 0.9417, seven fixtures differ only in the system schema sentence, `multi-block` is the one payload difference (direct drops the third block, the service sends it) |
| 4, 5 | `AiServiceModeIntegrationTest` — down → FAILED + `unavailable` + notice, then reconcile re-publishes, attempt 2 succeeds at REVIEWED with score; `AiServiceFailureMappingTest` pins provider-down as one FAILED transition |
| 6 | `AiSafetyCheckListenerTest`, `AiCommentSafetyCheckListenerTest`, `ServiceSafetyClassifierTest` |
| 7 | `ServiceReviewerTest` — `malformedResponseShouldBecomeAnInvalidVerdict` (non-null invalid verdict, severity `unknown`) and `nonsenseVerdictShouldBeRejectedByTheJavaGate`, both rejected by `ReviewValidity`; `AiServiceClientTest.malformedJsonShouldBeAnInvalidResponse` pins the 502 mapping |
| 8 | `ai-service/OpenApiContractTest` — `theCommittedContractPassesSemanticValidation` (swagger-parser messages empty) plus `everyDtoFieldMatchesTheCommittedSchema`; `AiServiceContractTest.recordsShouldMatchTheCommittedSchemas` pins the application side |
| 9 | `ReviewTraceLogTest` captures the service log line (`trace=trace-abc` + MDC) from the filter header; `AiServiceModeIntegrationTest.traceIdTravelsToTheServiceAndIntoTheJavaLog` captures the received header and the application log event's MDC with the same id |
| 10 | `AiServiceClientTest` delayed stub; 120 s read timeout vs 240 s lease, and the service enforces `budget.timeoutMs` so it returns before the application deadline (A8) |
| 11, 13, 16 | `docker compose config` rendering (no `ports` on `ai`, 512 MiB, empty `LLM_API_KEY` on `app`) |
| 12 | No frontend files changed |
| 14 | `ServiceReviewerTest` (one app call), `RetryingModelCallerTest` (provider retries stay in the service; budget exhaustion returns one failure instead of hanging) |
| 15 | `AiModeConfigTest` (unset = direct); compose default `service`; the direct overlay renders `AI_MODE=direct` with the key re-injected and drops `depends_on: ai` (A8) |

## Files

- `ai-service/**` — application, prompts, provider adapter, retry, repair, controllers,
  auth and trace filters, Dockerfile, OpenAPI, 67 tests.
- `src/main/java/com/nexus/campus/agent/{AiServiceClient,ServiceReviewer,SafetyClassifier,LlmSafetyClassifier,ServiceSafetyClassifier,LlmHealthProbe,VerdictRenderer}.java`
- `src/main/java/com/nexus/campus/config/{AiModeConfig,CampusAiProperties,ReviewPolicyConfig}.java`
- `src/main/resources/application.yml`, `application-prod.yml`
- `docker-compose.yml`, `docker-compose.direct.yml`,
  `docker/observability/prometheus/prometheus.yml`, `ai-service/Dockerfile`,
  `.env.example`, `.github/workflows/ai-service.yml`
- Tests: the new classes listed above, plus the two listener tests and the reconcile /
  health / scheduling tests whose fixture types changed.
- `src/test/java/com/nexus/campus/agent/AiServiceModeIntegrationTest.java` — the
  reconciler-to-service chain named by acceptance #4 (A6).
- `ai-service/src/test/java/com/nexus/campus/ai/api/ReviewTraceLogTest.java` — the
  service-side half of acceptance #9 (A7).
- `ai-service/src/test/java/com/nexus/campus/ai/config/AiServicePropertiesTest.java` and
  `model/SpringAiModelGatewayTest.java` — provider-root normalization and the real
  `/v1/models` health path.

`README.md`, `docs/INDEX.md` and `CHANGELOG.md` are deliberately not updated here: they
hold unrelated uncommitted work. Update them in the commit that lands that work, using
the counts from the final verification run.

## Rejected

- **Spring AI or LangChain4j in the same JVM.** Keeps one process, but the demonstration
  remains a library call rather than a service boundary.
- **Python/FastAPI.** Stronger model ecosystem, weaker Java-depth story and a second
  toolchain; rejected in favour of the Java boundary.
- **An OpenAI-compatible facade.** Cheapest migration, but Java would keep prompt
  assembly and the contract would stay untyped.
- **A shared contract module in P0.** Couples the two release cycles before the contract
  has survived a round unchanged.
- **Retrying the HTTP call in Java.** Nested ladders multiply latency and spend; the
  lease/reconciliation loop is the application's retry.
- **RAG in the same change.** Needs a recall@5 baseline and a separate plan first.
- **Publishing the `ai` port or routing nginx to it.** The service has no user-facing
  API and no authentication model beyond the shared key.
