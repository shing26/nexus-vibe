# AI service extraction — contract draft

Status: **frozen, 2026-11-27.** Base commit `c7bb34ae042a543d2ecc3bc70a40fb6ca2167948`
(`c7bb34a`). Implementation branch: `codex/ai-service-extraction`. The working tree also
holds unrelated uncommitted work in `README.md`, `docs/INDEX.md`, `CHANGELOG.md` and
`docs/tickets/readme-visual-front-door.md`; this contract must not revert or absorb it.
Any file outside the Allowed paths requires a contract amendment before it is edited.

## Goal

Move the model-facing half of the AI pipeline behind a separate Java 21 / Spring Boot
service and have the application process call it end to end, **without changing
user-facing behaviour and without moving failure policy out of the application**.

One-line success test: with `campus.ai.mode=service`, a post containing a fenced code
block receives the same structured verdict shape and the same rendered AI comment as in
`direct`; with the AI service stopped, the post lands in the existing `FAILED` /
`pending-llm` path and reconciliation completes the review when the service returns.

## Non-goals

- No database schema change, no migration, no new public API.
- No frontend change in P0.
- No streaming, chat or model training.
- No vector store, embedding index or retrieval; a separate plan and ADR are required.
- No removal of the direct path; it stays the default for unit tests and the rollback.

## Terms

- **AI Service (inference service)**: the separate Spring Boot process at `ai-service/`.
  Owns model-facing concerns. Never owns business state.
- **Policy owner**: the Spring Boot process. Owns trigger, lease, validity, fail-closed,
  reconciliation and persistence.
- **Direct mode**: Java calls the OpenAI-compatible provider through `LlmClient` (today's
  path).
- **Service mode**: Java calls the AI service through `AiServiceClient`.
- **Inference concern**: anything whose correctness depends on the model or provider —
  prompt text, response format, repair, provider retries, token accounting.
- **Policy concern**: anything that is a product rule — what is reviewed, when it fails
  closed, what a verdict means.
- Avoid: "AI layer" (too vague to assign ownership), "microservice" (describes a
  deployment shape, not a boundary).

## Architecture

```
React SPA
   |
Spring Boot (Java)          policy owner: trigger, lease, validity, fail-closed,
   |                        reconciliation, persistence, comment rendering
   | HTTP/JSON, internal network only
AI service (Java/Spring)    inference owner: prompts, provider quirks, repair,
   |                        provider retries, token/latency
   |
Ollama (local) / hosted OpenAI-compatible model (prod)
```

Compose gains `ai` (container `nexus-ai`, no host port). Java reaches it at
`AI_SERVICE_URL=http://ai:8000`. `nginx` gets no route.

## Boundary

| Concern | Owner after extraction |
|---|---|
| Trigger rule (`ReviewPolicy`) | Java |
| Lease, attempts, terminal states | Java |
| CodeSnippet extraction, `PromptIsolation`, context truncation | Java |
| Prompt templates (roles, schema, examples) | AI service |
| Provider adapter and provider quirks (`response_format`, thinking, NIM) | AI service |
| Provider retries | AI service — exactly one layer |
| Reinforced retry on an invalid verdict | AI service |
| JSON repair and first-pass schema check | AI service |
| `ReviewValidity` (second pass, cannot be bypassed) | Java |
| Persistence, comment rendering, score writeback | Java |
| Fail-closed, `pending-llm`, reconciliation | Java |
| Trace propagation | Java sends `X-Trace-Id`; both sides log it |
| Health verdict used by the publish gate | Java selects one probe by mode |
| Model credential in production service mode | AI service only |

Two rules keep the thesis intact:

1. The AI service never writes post status and gets no database credentials.
2. A response is data, not a decision. Java re-runs `ReviewValidity` and records an
   invalid verdict as invalid, even when the service returned HTTP 200.

## API contract (P0)

OpenAPI 3.1 committed at `ai-service/openapi/ai-service.yaml`. JSON, UTF-8. All endpoints
require `X-Trace-Id` and accept an optional `X-AI-Service-Key`; the key is mandatory in
production.

### `POST /v1/reviews`

Request:

```json
{
  "requestId": "0123456789abcdef:2",
  "traceId": "0123456789abcdef",
  "postId": 123,
  "kind": "code",
  "title": "Why this loop skips the last item",
  "content": "<already isolated and truncated by Java>",
  "snippets": ["for (int i = 0; i < n - 1; i++) { ... }"],
  "budget": { "maxContextTokens": 12000, "timeoutMs": 60000 }
}
```

Response 200:

```json
{
  "score": 82,
  "severity": "low",
  "quality": "...",
  "security": "...",
  "suggestions": "...",
  "approved": true,
  "resultJson": "{...}",
  "model": "deepseek-chat",
  "usage": { "promptTokens": 1200, "completionTokens": 400 },
  "latencyMs": 3400,
  "repaired": false
}
```

Errors use `{ "code": "...", "message": "...", "retryable": true|false }`:

| Status | Code | Retryable |
|---|---|---|
| 400 | `invalid_request` | false |
| 401 | `unauthorized` | false |
| 503 | `not_ready` | true |
| 502 | `model_unavailable` | true |
| 504 | `model_timeout` | true |

`kind` is `code` or `prompt`. Java chooses it from the same rules the two default
reviewers use today; the service does not re-derive the trigger.

### `POST /v1/safety`

Request: `{ requestId, traceId, text, surface }`, where `surface` is `post` or `comment`.
Response: `{ classification, confidence, reasons[], resultJson, model, latencyMs }` with
`classification` in `safe | prompt_injection | harmful | spam`.

The response must map onto the exact JSON shape the two listeners parse today. A parity
test pins the mapping; the listeners' per-class handling does not change.

### `GET /healthz`, `GET /readyz`, `GET /metrics`

- `/healthz`: process alive, no dependency call.
- `/readyz`: provider configuration present and a cached provider probe (short timeout,
  60-second cache). Java's existing five-minute cache still sits in front of it.
- `/metrics`: Prometheus text for request count, duration, provider failures, tokens.

### Outside this contract

`POST /v1/embed` and `POST /v1/rag/query` are not part of this contract. Adding them
requires a new plan that starts with a retrieval baseline, not with an endpoint.

## Java integration

- `AiServiceClient` — `RestClient` with connect/read timeouts from
  `campus.ai.service.*`, `X-Trace-Id`, optional key, and mapping to
  `AiServiceException(retryable)`.
- `ServiceReviewer implements Reviewer` — `claims()` mirrors the two default reviewers,
  `identity()` returns `ai-service`, `review()` builds `Reviewer.Verdict` from the typed
  response and returns `null` only for availability failures, so `AiReviewService`'s
  existing `FAILED` path runs unchanged.
- `SafetyClassifier` — `LlmSafetyClassifier` (direct) and `ServiceSafetyClassifier`
  (service). `AiSafetyCheckListener` and `AiCommentSafetyCheckListener` depend on the
  interface instead of `LlmClient`.
- `LlmHealthProbe` — `LlmClient` and `AiServiceClient` implement it; `LlmHealthCache`
  depends on the interface. This is what makes "model down" and "AI service down" the
  same user-visible state and keeps the publish gate and reconcile task honest in both
  modes.
- `VerdictRenderer` — extracted from `AbstractLlmReviewer` so direct and service modes
  render identical comment bodies from the same fields.
- `campus.ai.mode=direct|service` with `direct` as the default. `AiModeConfig` and
  `ReviewPolicyConfig` publish exactly one reviewer set and one health probe per mode.
- Contract DTOs are duplicated on purpose in P0, so the two builds stay independent. A
  shared contract module is a P2 option, not a P0 shortcut.

## Phases

### P0 — the call works end to end

1. Scaffold `ai-service/` as an independent Maven/Spring Boot project with Spring AI
   1.1.x, `/v1/reviews`, `/v1/safety`, health and metrics.
2. Add the Java seams and the mode switch; keep direct mode green throughout.
3. Add compose wiring, `.env.example` entries, and a CI job for the AI service's Maven
   tests plus contract validation.
4. Prove parity, failure mapping and trace propagation with tests.
5. Record the architecture in README and ADR-0024.

### Out of contract — retrieval

Retrieval is deliberately not part of this plan. When it is argued for, the first ticket
is a 20-post golden set with expected relevant chunks and a recall@5 baseline for the
existing Elasticsearch BM25 index. Embeddings and a second index are justified only by
that measurement, and the review prompt does not change until the eval shows a gain.

### P2 — hardening

- Extract `LlmClient`'s circuit breaker into a shared component; existing tests pin the
  behaviour-preserving refactor.
- Load-test the Java path with the AI service in the loop and publish P95/P99.
- Dashboards and alerts for the new service.
- Verify the model credential is absent from the application container in production
  service mode, and document the explicit rollback step.

## Rollout

- `application.yml` keeps `campus.ai.mode=direct` as the default.
- `docker-compose.yml` uses `AI_MODE=${AI_MODE:-service}` so local and demo runs show the
  boundary without a second compose file.
- Production starts on the direct overlay (`docker-compose.direct.yml`) until P0 passes
  verification. The flip is `AI_MODE=service`, `AI_SERVICE_PROFILE=prod` and a non-blank
  `AI_SERVICE_KEY` in `.env`, plus `docker compose up -d app`; rollback is the overlay
  command. The base compose blanks the app's provider key, so a lone `AI_MODE=direct` is
  not a valid rollback (A5). Production also sets `CAMPUS_AI_MENTION_ENABLED=false` for
  the flip: mention replies are a chat surface outside P0 and have no service endpoint
  yet (A8).
- No code change is involved in either direction.

## Deployment and health

- `ai` joins `nexus-net` only: no `ports:` entry and no nginx route.
- `mem_limit: 512m` with `-XX:MaxRAMPercentage=70`. Production may stop the idle Ollama
  container when the hosted provider is the only one in use.
- `/healthz` is process-only and never calls a dependency. `/readyz` returns 503 when the
  cached provider probe fails; it never performs a model call per scrape.
- `AiServiceClient.isHealthy()` calls `/readyz`; the existing `LlmHealthCache` five-minute
  cache stays in front, so the publish gate and reconcile task keep one verdict.
- `X-AI-Service-Key` is optional locally and required in production. A missing or wrong
  key is 401 and is not retried.
- `/metrics` is scraped by Prometheus over the internal network; it is not public.

## Allowed paths

New:

- `ai-service/**` (independent Maven project, tests, `Dockerfile`, `openapi/`)
- `.github/workflows/ai-service.yml`
- `src/main/java/com/nexus/campus/agent/AiServiceClient.java`
- `src/main/java/com/nexus/campus/agent/ServiceReviewer.java`
- `src/main/java/com/nexus/campus/agent/SafetyClassifier.java`
- `src/main/java/com/nexus/campus/agent/LlmSafetyClassifier.java`
- `src/main/java/com/nexus/campus/agent/ServiceSafetyClassifier.java`
- `src/main/java/com/nexus/campus/agent/LlmHealthProbe.java`
- `src/main/java/com/nexus/campus/agent/VerdictRenderer.java`
- `src/main/java/com/nexus/campus/config/AiModeConfig.java`
- `src/test/java/com/nexus/campus/agent/AiServiceClientTest.java`
- `src/test/java/com/nexus/campus/agent/ServiceReviewerTest.java`
- `src/test/java/com/nexus/campus/agent/ServiceSafetyClassifierTest.java`
- `src/test/java/com/nexus/campus/agent/LlmSafetyClassifierTest.java`
- `src/test/java/com/nexus/campus/config/AiModeConfigTest.java`
- `plans/ai-service-extraction.md` (this contract)
- `docs/adr/0024-ai-inference-moves-behind-a-service.md`
- `docs/tickets/ai-service-extraction.md`
- `docker-compose.direct.yml`

Modified:

- `CONTEXT.md` (the `AI Inference Service` and `Policy Owner` terms; see A8)
- `src/main/java/com/nexus/campus/agent/LlmClient.java` (implements `LlmHealthProbe`)
- `src/main/java/com/nexus/campus/agent/LlmHealthCache.java` (depends on the interface)
- `src/main/java/com/nexus/campus/agent/PromptIsolation.java` (idempotent neutralization,
  so the Java hop and the service hop cannot double-mark; see A4)
- `src/main/java/com/nexus/campus/agent/AbstractLlmReviewer.java` (uses `VerdictRenderer`)
- `src/main/java/com/nexus/campus/agent/CodeReviewReviewer.java` (uses `VerdictRenderer`)
- `src/main/java/com/nexus/campus/agent/PromptReviewReviewer.java` (uses `VerdictRenderer`)
- `src/main/java/com/nexus/campus/agent/AiSafetyCheckListener.java`
- `src/main/java/com/nexus/campus/agent/AiCommentSafetyCheckListener.java`
- `src/main/java/com/nexus/campus/config/CampusAiProperties.java`
- `src/main/java/com/nexus/campus/config/ReviewPolicyConfig.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `docker-compose.yml`
- `docker/observability/prometheus/prometheus.yml`
- `.env.example`
- `docs/INDEX.md` (ADR/plan rows only, after the unrelated dirty work lands)
- `README.md` (architecture line and test counts only, same condition)
- `CHANGELOG.md` (same condition)
- Tests whose constructor signatures change: `AiSafetyCheckListenerTest`,
  `AiCommentSafetyCheckListenerTest`, `CodeReviewReviewerTest`,
  `PromptReviewReviewerTest`, `LlmHealthIndicatorTest`, `SchedulingGateTest`,
  `CommentAgentEventPublisherTest`, `PostAgentEventPublisherTest`,
  `AiReviewReconcileTaskTest`, and the integration tests that build these beans.

Any file outside this list requires a contract amendment before it is edited.

## Acceptance criteria (P0)

1. `mvn -B test` passes in direct mode with no test deleted, skipped or weakened.
2. In service mode, exactly one `Reviewer` bean is published (`ServiceReviewer`), the
   health probe is `AiServiceClient`, and a review makes zero calls to `LlmClient`
   (asserted on its metric or a failing stub).
3. Parity on a fixture set of at least eight posts (buggy code, clean code, prompt post,
   empty body, oversized body, injection, malformed model output, provider timeout):
   `score`, `severity` and `approved` match direct mode, and invalid cases are rejected
   by the same validity rule.
4. AI service stopped: the post ends `FAILED` with an `unavailable` review log and the
   existing author notification; after the service returns, reconciliation completes it.
5. AI service up, provider down: the same `FAILED` path runs, and `/readyz` reports not
   ready within its cache window.
6. Safety parity: post path still fails closed to `PENDING_REVIEW` + `pending-llm`; comment
   path still leaves the comment visible and checks later (ADR-0017).
7. HTTP 200 with malformed JSON or a schema-valid but nonsensical verdict is recorded as
   invalid by `ReviewValidity`; no exception escapes and no score is written.
8. The OpenAPI file validates; the application-side contract test fails if a response
   field is renamed or removed; the AI service's own contract test fails on the same
   change.
9. The request's `X-Trace-Id` appears in the AI service log line and in the Java log line
   for the same review.
10. The Java read timeout is shorter than the review lease (240 s) with margin; a hung
    service cannot hold a lease past expiry (tested with a delayed stub).
11. `docker compose config` shows no published port for `ai`, and `nginx` has no route to
    it.
12. Frontend `npm run test`, `npm run lint` and `npm run build` are unchanged (no
    frontend edits in P0).
13. In production service mode, `docker compose config` shows no `LLM_API_KEY` under
    `app` and shows it under `ai`.
14. A provider that fails twice then succeeds produces exactly one application-side call
    and one recorded verdict; a provider that always fails produces one `FAILED`
    transition, not three.
15. With `AI_MODE` unset the application runs direct; with `AI_MODE=service` it runs
    service; the compose default and the production `.env` value are both exercised.
16. `docker compose config` shows `mem_limit: 512m` and no `ports:` for `ai`; `/readyz`
    returns 503 when the provider probe fails and 200 when it recovers, within the cache
    window.

## Verification commands

```powershell
# Java, direct mode is the default
mvn -B test

# AI service (independent Maven project)
mvn -f ai-service/pom.xml test

# Contract test against a stub service
cd ..
mvn -B -Dtest=AiServiceContractTest test

# Compose smoke. Local image builds fail on the package mirrors in this environment;
# CI is the authority for the Docker build.
docker compose up -d db redis elasticsearch ollama ai app
curl.exe -s http://localhost:8080/actuator/health/servable
docker compose stop ai
# publish a code post through the existing integration helper, assert FAILED/pending
docker compose start ai
# wait for the reconciliation window, assert the review completed
```

The automated equivalents are `AiServiceModeIntegrationTest` and
`AiServiceFailureMappingTest`; the manual compose sequence is the demo path, not the
evidence path.

## Forbidden

- Moving the lease, attempt ladder, fail-closed policy or reconciliation into the AI
  service.
- Giving the AI service database credentials or any write path to post status.
- Changing a public API, response shape or database schema in P0.
- Deleting, skipping or weakening a test to make the new path pass.
- Nested retry ladders (provider retries in the AI service plus the Java retry ladder).
- Adding a Python runtime or local model weights in P0.
- Injecting `LLM_API_KEY` into the application container in production service mode.
- Publishing the `ai` port or adding an nginx route.
- Committing `.env`, model keys or the service key.
- Flipping the default mode before parity evidence and independent verification.
- Adding a vector store or RAG in P0.
- Upgrading the application to Spring Boot 3.5 or 4.x as part of this contract; the
  application stays on 3.3.5.
- Reverting or absorbing the unrelated uncommitted README/INDEX/CHANGELOG work.
- Letting the AI service declare its own verdict valid; `ReviewValidity` stays in Java.

## Open decisions

Each decision is the user's; the recommendation is a starting point, not a default that
can be assumed.

Resolved:

- **D1 — B, inference owner (2026-11-26).** The AI service owns prompt templates,
  provider adapters and quirks, JSON repair and provider retries; Java keeps the trigger,
  lease, `ReviewValidity`, fail-closed, reconciliation and persistence.
- **D2 — B, Java service (2026-11-26).** `ai-service/` is an independent Java 21 /
  Spring Boot project, not a Python process. The trade is Java depth and one toolchain
  for Python's model ecosystem and a cross-language contract.
- **D2b — Spring AI 1.1.8 (2026-11-26).** The Boot 3.5 line keeps the service one major
  version away from the application; Spring AI 2.1.0's Boot 4.2 dependency is out of
  scope, and LangChain4j's Spring starter is still beta.
- **D2c — independent Maven project (2026-11-26).** `ai-service/pom.xml` builds on its
  own; the root application is not converted to a multi-module reactor, so its source
  layout, Dockerfile and CI paths stay untouched.
- **D3 — typed contract (2026-11-26).** The application calls `/v1/reviews` and
  `/v1/safety` through `ServiceReviewer` and `SafetyClassifier`; it does not point
  `LLM_ENDPOINT` at an OpenAI-compatible facade. `ReviewValidity` still re-checks every
  response on the application side.
- **D4 — AI service holds the key (2026-11-26).** In production service mode only the
  `ai` container receives `LLM_API_KEY`; the application does not. Rolling back to direct
  mode is an explicit compose change plus restart.
- **D5 — retries live in the service (2026-11-27).** The AI service owns provider retries
  and the reinforced-prompt retry; the application makes exactly one HTTP call per review
  attempt and leaves the rest to the lease/reconciliation ladder.
- **D6 — staged rollout (2026-11-27).** Code default is `direct`; compose default is
  `service`; production starts on the direct overlay and flips after parity plus
  independent verification. The flip is `.env` `AI_MODE=service` plus restart; rollback
  is the overlay, because the base compose blanks the app key (A5).
- **D7 — retrieval deferred (2026-11-27).** This contract covers review and safety only.
  RAG needs a separate plan whose first ticket is a recall@5 baseline, not an endpoint.
- **D8 — committed contract plus two tests (2026-11-27).** The YAML is the source of
  truth; `swagger-parser` validates it in the AI service, and the application's stub
  contract test pins the DTO mapping. No codegen.
- **D9 — internal service with split health (2026-11-27).** No host port; `/healthz` is
  process-only; `/readyz` is a cached provider probe consumed by `AiServiceClient` and
  `LlmHealthCache`; the shared key is mandatory in production; the service is capped at
  512 MB.
- **D10 — Java renders the comment (2026-11-27).** The AI service returns fields and
  `resultJson`; `VerdictRenderer` turns them into `commentBody` on the application side,
  so direct and service modes share one presentation path.

| ID | Decision | Recommendation | Blocks |
|---|---|---|---|
| D1 ✅ | What the service owns | **Resolved: inference owner; policy stays in Java** | Everything |
| D2 ✅ | Runtime and language | **Resolved: Java 21 / Spring Boot, independent Maven project** | P0 scaffold |
| D2b ✅ | Java AI framework | **Resolved: Spring AI 1.1.8 on Boot 3.5.x** | P0 scaffold |
| D2c ✅ | Build layout | **Resolved: independent `ai-service/pom.xml`; no reactor** | P0 scaffold and CI |
| D3 ✅ | Java integration seam | **Resolved: typed contract + `ServiceReviewer` / `SafetyClassifier`; direct mode kept** | Java diff |
| D4 ✅ | Provider and credential placement | **Resolved: same OpenAI-compatible provider; key only in the AI service in prod service mode** | Compose and security |
| D5 ✅ | Retry ownership | **Resolved: provider and repair retries in the service; lease/reconciliation in Java; no nesting** | Failure tests |
| D6 ✅ | Default mode and rollout | **Resolved: code default `direct`; compose default `service`; production flips after sign-off** | Wiring and CI |
| D7 ✅ | Retrieval scope | **Resolved: out of this contract; separate plan starting from a recall@5 baseline** | Scope |
| D8 ✅ | Contract tooling | **Resolved: committed OpenAPI 3.1, `swagger-parser` in the service, stub contract test in the application; no codegen** | CI and tests |
| D9 ✅ | Deployment and health | **Resolved: internal-only, split health, key mandatory in prod, 512 MB cap** | Compose and ops |
| D10 ✅ | Comment rendering | **Resolved: Java renders `commentBody` from typed fields; the service returns data only** | Parity tests |

## Rejected alternatives

- **OpenAI-compatible facade.** Point `LLM_ENDPOINT` at the new service and leave Java
  untouched. It is the cheapest demo and the weakest architecture: no typed contract, no
  service-owned prompts, and nothing to verify across the boundary.
- **Spring AI in the same JVM.** A single process and no second deployment, but it
  demonstrates a library rather than a service boundary.
- **A Python/FastAPI service.** The stronger model ecosystem and a genuine cross-language
  contract, at the cost of a second toolchain and CI runtime. Rejected in favour of Java
  depth.
- **Spring AI 2.1.0.** Its starters depend on Spring Boot 4.2.0, which would turn a
  service extraction into an application-wide upgrade.
- **LangChain4j.** A credible alternative, but its Spring Boot starter is still published
  as a beta and this round needs only structured review and safety calls.
- **Move everything, including the lease and fail-closed.** Simpler mental model for a
  generic microservice, but it deletes the project's thesis: the unreliable dependency
  would own the policy for its own failure.
- **A vector database in P0.** Adds infrastructure and an index lifecycle before there is
  a retrieval eval to justify either.

## Freeze record

- Base commit: `c7bb34ae042a543d2ecc3bc70a40fb6ca2167948` (`c7bb34a`)
- Frozen at: 2026-11-27 (Asia/Shanghai)
- Verifier: `verifier-ai-service` (independent subagent; not the implementer)
- Implementation branch: `codex/ai-service-extraction`

`git status --short` at freeze:

```text
 M CHANGELOG.md
 M CONTEXT.md
 M README.md
 M docs/INDEX.md
 M docs/tickets/readme-visual-front-door.md
?? .claude/
?? .impeccable/
?? benchmark/migrations/results/fix-escaped-content-2026-11-15.md
?? docs/adr/0024-ai-inference-moves-behind-a-service.md
?? docs/assets/readme/banner-light.svg
?? docs/assets/readme/creations-dark.png
?? docs/assets/readme/creations-light.png
?? docs/assets/readme/home-dark.png
?? docs/assets/readme/home-light.png
?? docs/assets/readme/logo.svg
?? docs/assets/readme/profile-dark.png
?? docs/assets/readme/profile-light.png
?? docs/assets/readme/review-dark.png
?? docs/assets/readme/review-light.png
?? docs/assets/readme/showcase-dark.png
?? docs/assets/readme/showcase-light.png
?? docs/assets/readme/theatre-dark.png
?? docs/assets/readme/theatre-light.png
?? docs/tickets/readme-forge-front-door.md
?? plans/ai-service-extraction.md
```

The `README.md`, `docs/INDEX.md`, `CHANGELOG.md` and ticket modifications, and the
untracked assets, belong to other work. Only `CONTEXT.md`, ADR-0024 and this plan are part
of this freeze.

## Contract amendments

### A1 — 2026-11-27 — renderer extraction touches the two default reviewers

`VerdictRenderer` has to be shared by direct and service modes; today the rendering lives
in `CodeReviewReviewer.renderBody` and `PromptReviewReviewer.renderBody`. Leaving those
untouched would create a second copy and make the parity test meaningless. Added to
Allowed paths: `CodeReviewReviewer.java`, `PromptReviewReviewer.java`, and
`LlmSafetyClassifierTest.java` (the prompt-isolation assertions move with the prompt).

### A2 — 2026-11-27 — explicit direct-mode override and scrape config

D4 requires the production application container to stop receiving `LLM_API_KEY`. Compose
cannot conditionally omit an inherited environment entry, so the key is removed from the
base `app` service and a separate `docker-compose.direct.yml` re-injects it for rollback.
The new service's `/metrics` also needs a Prometheus target. Added to Allowed paths:
`docker-compose.direct.yml`, `docker/observability/prometheus/prometheus.yml`.

### A3 — 2026-11-27 — tests the acceptance criteria already name

Acceptance #3, #4, #5 and #8 name an application-side contract test, a parity fixture
set and the automated equivalents `AiServiceModeIntegrationTest` and
`AiServiceFailureMappingTest`, but the Allowed-paths list omitted their files. Added:
`src/test/java/com/nexus/campus/agent/AiServiceContractTest.java`,
`src/test/java/com/nexus/campus/agent/ReviewParityTest.java`, and
`src/test/java/com/nexus/campus/agent/AiServiceFailureMappingTest.java`.

Acceptance #13 says "no `LLM_API_KEY` under `app`". `env_file: .env` plus compose's
environment-merge semantics means the only literal form is `LLM_API_KEY: ""` on `app`.
Read the criterion as "no non-empty key reaches the application container"; the compose
config assertion checks the rendered value is empty, not that the key is absent from the
YAML block.

### A4 — 2026-11-27 — isolation runs on both sides of the hop

The boundary table says `PromptIsolation` and context truncation stay in Java, and the
service also applies both while assembling prompts. That is intentional defense in depth,
not a moved policy:

- Java neutralizes delimiter-like lines before sending (security policy) and extracts the
  code blocks that decide `claims()` (trigger policy).
- The service owns prompt assembly, so it generates the per-request nonce delimiters and
  applies the per-block character budget it was given.
- `PromptIsolation.neutralize` is now idempotent, so the second application cannot append
  a second `[neutralized:]` marker. `LlmSafetyClassifierTest` pins that property.

Java still runs `ReviewValidity` on the response; the service's `ReviewValidator` only
decides whether to spend its own reinforced retry.

### A5 — 2026-11-27 — direct rollback is the overlay, not a lone `AI_MODE`

The base compose file blanks `LLM_API_KEY` on `app` to satisfy D4. A bare
`AI_MODE=direct` in `.env` would therefore put the application in direct mode with no
provider key. Rollback to direct is the one command recorded in A2:

```text
docker compose -f docker-compose.yml -f docker-compose.direct.yml up -d app
```

The overlay sets `AI_MODE=direct` and re-injects `${LLM_API_KEY}` together, so the two
values cannot drift apart. The production `.env` keeps `AI_MODE` unset (compose default
`service`) until the flip is verified; the branch does not change the live value.

### A6 — 2026-11-27 — the reconcile-completes-it test now exists

Acceptance #4 names `AiServiceModeIntegrationTest`, but earlier rounds only had
`AiServiceFailureMappingTest`, which calls `reviewPost` directly and therefore proves the
failure mapping without the reconciliation sweep. Added
`src/test/java/com/nexus/campus/agent/AiServiceModeIntegrationTest.java`: the service is
down for the first attempt (FAILED + `unavailable` row + author notice), then the service
recovers and `AiReviewReconcileTask` re-publishes the FAILED post; the listener claims
attempt 2, the service-backed reviewer scores it, and the post reaches REVIEWED with that
score. No Spring context is involved; the publisher forwards synchronously so the
assertion is the state transition. Added to Allowed paths:
`src/test/java/com/nexus/campus/agent/AiServiceModeIntegrationTest.java`.

### A7 — 2026-11-27 — criterion 9 gets captured log evidence on both sides

Acceptance #9 previously rested on separate header and MDC unit tests, so no command
output showed a trace id in a log line. Added two tests:

- `ai-service/src/test/java/com/nexus/campus/ai/api/ReviewTraceLogTest.java` drives the
  real `TraceIdFilter` with `X-Trace-Id: trace-abc`, calls the controller with a body
  that carries no `traceId`, and captures the `ReviewController` log event: the message
  contains `trace=trace-abc` and the event's MDC map carries `traceId=trace-abc`.
- `AiServiceModeIntegrationTest.traceIdTravelsToTheServiceAndIntoTheJavaLog` points a real
  `AiServiceClient` at a stub HTTP server, sets the same MDC id, runs one review through
  `AiReviewService`, and asserts both the received `X-Trace-Id` header and the MDC of the
  captured application log event equal `trace-abc`.

The two tests live in different Maven projects, so they share the literal id rather than a
process; that is the seam-level equivalent of the demo path. Added to Allowed paths:
`ai-service/src/test/java/com/nexus/campus/ai/api/ReviewTraceLogTest.java`.

### A8 — 2026-11-27 — review fixes before merge

The two-axis review of PR #62 found five gaps. Four are closed here; the fifth is
recorded as a limitation rather than silently claimed.

- **Rollback no longer waits for `ai`.** The overlay drops the base file's
  `depends_on: ai` with `!reset`, so the documented
  `docker compose -f docker-compose.yml -f docker-compose.direct.yml up -d app`
  starts the application even when `ai` is the unhealthy dependency being rolled back
  from.
- **A blank service key now fails startup by default.** `ai` defaults to
  `SPRING_PROFILES_ACTIVE=prod` instead of `local`, `.env.example` ships a non-blank
  local/demo key shared by both containers, and the production flip names
  `AI_SERVICE_PROFILE=prod` and a real `AI_SERVICE_KEY`. A copied environment can no
  longer ship an unauthenticated `/v1/*` on `nexus-net`.
- **The application deadline exceeds the service's retry budget.** The read timeout
  moves from 65 s to 120 s (still below the 240 s lease). The service now enforces the
  request's `budget.timeoutMs` (falling back to
  `maxAttempts * llm.timeout + backoffs`) and refuses to start an attempt that cannot
  finish before it — including the repair pass and the first attempt — so a slow
  provider returns one failure to the application instead of triggering a duplicate
  review.
- **`CONTEXT.md` is on the Allowed paths.** It was already modified for the two new
  terms; the list now says so.
- **Mention replies are explicitly out of P0.** `@ai-agent` is a chat surface; the
  service exposes review and safety only. In production service mode the application has
  no provider key, so the flip requires `CAMPUS_AI_MENTION_ENABLED=false` until a chat
  endpoint is argued for in a separate plan. The local/demo stack still runs against
  keyless Ollama, so mentions stay enabled there. The switch is listed in
  `.env.example` so a copied environment does not miss it.
- **Parity evidence is layered, not a single live comparison.** The application test
  compares direct mode against the typed service response and the shared renderer; the
  service's own `ReviewServiceTest` pins model JSON to response fields. Prompt-text drift
  between `AbstractLlmReviewer` and `PromptFactory` is still not measured by a test, and
  is recorded as a limitation rather than claimed as parity.

## Freeze checklist

- [x] D1–D10 (including D2b and D2c) answered and recorded here.
- [x] Allowed paths final; no TBD entries.
- [x] Base commit and `git status --short` recorded at freeze time.
- [x] A named verifier who did not implement the change.
- [x] ADR-0024 moved from Proposed to Accepted.
- [ ] `docs/INDEX.md` and `README.md` rows added after the unrelated dirty work lands.
