# AI service extraction (P0)

Status: **verified on `codex/ai-service-extraction` — PASS on acceptance 1–16; draft PR #62.**
Contract: `plans/ai-service-extraction.md`. Decision: ADR-0024.

Verification record (independent subagent, not the implementer):

- Test-bearing tree: `cfd7b57` (all 81 changed files). Later commits are docs-only; the
  verified code and tests are unchanged. Branch `codex/ai-service-extraction`.
- `mvn -B test` → 601 tests, 0 failures, coverage floor met.
- `mvn -B -f ai-service/pom.xml test` → 37 tests, 0 failures.
- Frontend `vitest --maxWorkers=2` 162/162, `lint` and `build` clean; `frontend/` unchanged.
- `git status --short` after the commit: only the unrelated README/INDEX/CHANGELOG work and
  its assets remain; nothing from this ticket is unstaged.
- Both compose renders checked: base `AI_MODE=service`, `ai` no ports / 512 MiB, `app`
  key empty, `ai` key non-empty; direct overlay `AI_MODE=direct` with the key re-injected
  and no `depends_on: ai`.
- Committed as `cfd7b57` and opened as draft PR #62 against `master`.
- CI on PR #62: backend, frontend, alert bridge, inference service and Docker image
  build all pass (runs `45938341261` / `45938341279`; docs-only follow-up
  `45938937849` / `45938937803`).

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
| 3 | `ReviewParityTest` — nine fixtures, including the eight named cases; layered evidence: direct-vs-typed/renderer parity plus `ReviewServiceTest` mapping. Prompt-text drift is disclosed, not measured (A8) |
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
  auth and trace filters, Dockerfile, OpenAPI, 37 tests.
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
