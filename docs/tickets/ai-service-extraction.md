# AI service extraction (P0)

Status: **verified and deployed on the local/demo stack as `20261127-02` — 15 PASS, #3 PARTIAL (A8 limitation); PR #63.**
Contract: `plans/ai-service-extraction.md`. Decision: ADR-0024.

Verification record (independent verifier, not the implementer):

- Test-bearing tree: `b7f4210` (9 code/config/test files). This ticket update is the
  docs-only follow-up. Earlier independent verification bound `c18ead7`; the only later
  code delta is the health probe from 1 s to 750 ms per phase.
- `mvn -B test` → 601 tests, 0 failures, coverage floor met.
- `mvn -B -f ai-service/pom.xml test` → 40 tests, 0 failures.
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
| 3 | **PARTIAL.** `ReviewParityTest` — nine fixtures, including the eight named cases; layered evidence: direct-vs-typed/renderer parity plus `ReviewServiceTest` mapping. Prompt-text drift is disclosed, not measured (A8) |
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
  auth and trace filters, Dockerfile, OpenAPI, 40 tests.
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
