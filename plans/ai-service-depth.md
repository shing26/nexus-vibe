# AI service depth — structured output, evaluation, evidence

Status: **frozen 2026-11-28**, base `master` `2ddf455`, decision ADR-0025.
Three increments, each one branch and one PR, merged in order A → B → C.

## Goal

Close the capability gap the extraction left behind and make the model-facing half of the
pipeline measurable. The service must be at least as strict as the direct path, must not
pay twice for the same content, and must produce evidence a reviewer can check.

## Non-goals

- No RAG, embeddings, vector store, agent/tool calling, streaming or fine-tuning.
- No frontend change, no database schema change, no comment-body change.
- No move of policy into the service: status transitions, approval, lease and
  reconciliation stay in Java.
- No shared Maven module between the two projects.

## Increment A — capability parity

### A1. Provider-enforced structured output

- `AI_LLM_RESPONSE_FORMAT` selects `json_schema` (default), `json_object` or `none`.
- The service owns the per-kind schema (code review, prompt review, safety) and sends it
  as `response_format`; the application request contract is unchanged.
- `none` must still parse through `JsonRepair`; the mode is observable in logs.

Acceptance: a gateway test captures the options and asserts the schema name, strict flag
and type for each kind; `none` sends no `response_format`.

### A2. Content-addressed idempotency

- Key: SHA-256 of `kind|title|content|snippets|model|promptVersion`.
- Concurrent duplicates coalesce on one in-flight completion; a completed valid response
  is cached for `AI_REVIEW_CACHE_TTL` (default `10m`), bounded by
  `AI_REVIEW_CACHE_MAX_ENTRIES` (default `500`).
- Errors and invalid verdicts are never cached. Cache eviction is LRU.
- Metrics: `ai.review.dedup{outcome=hit|coalesced|miss}`.

Acceptance: two concurrent identical requests invoke the gateway once; a repeat inside
the TTL invokes it zero times; different content invokes it twice; an invalid verdict is
not reused.

### A3. Provider circuit breaker

- Threshold `AI_LLM_BREAKER_FAILURES` (default `5`), open window
  `AI_LLM_BREAKER_OPEN_SECONDS` (default `30`).
- Open state fails fast with 503 and `model_unavailable`; one half-open probe is allowed
  after the window; success closes, failure re-opens.
- Gauge: `ai.provider.circuit.open`.

Acceptance: after the threshold, the gateway is not called until the window elapses; the
probe is single-flight; a success resets the breaker.

### A4. Provider call archive

- `AI_CALL_ARCHIVE` names a JSONL file; blank disables the archive.
- One line per provider attempt: timestamp, requestId, traceId, kind, attempt, model,
  temperature, latency, outcome, error code, token counts, prompt/response text.
- A write failure is logged and swallowed; it never fails a review.

Acceptance: a temp-file test asserts one line per attempt and that an unwritable path
does not change the review result.

## Increment B — measurement

### B1. Prompt version

- `promptVersion` = first 12 hex characters of SHA-256 over the static templates and
  schemas (nonces excluded). It appears in review and safety responses, log lines and
  archive records.

Acceptance: stable across two instances in one run; changes when a template constant
changes (asserted against a fixture hash).

### B2. Golden set and harness

- `benchmark/eval/review-cases.json`: at least 20 review cases with `kind`, title,
  content, expected score range, allowed severities and a short rationale.
- `benchmark/eval/safety-cases.json`: at least 12 cases with the expected class.
- `scripts/ai_eval.py` calls a running service, reports score agreement (inside range),
  severity agreement, invalid rate, repair rate, p50/p95 latency, token totals and
  evidence rejection, and writes JSON.
- `--baseline <file>` + `--fail-below <ratio>` fail the run on regression.

Acceptance: corpus validation and metric maths are covered by
`scripts/test_ai_eval.py`; a live run against the local stack produces
`benchmark/eval/baseline-2026-11-28.json` recording model and prompt version.

### B3. CI gate

- CI runs the corpus validation and metric tests; it does not call a model.
- The baseline run is documented and repeatable, not simulated.

## Increment C — evidence anchoring

### C1. Quote verification

- The review prompt asks for up to five short `evidence` quotes copied from the submitted
  content or snippets.
- The service locates each quote with whitespace-normalized matching, rejects duplicates,
  blank or sub-10-character quotes, and computes 1-based `startLine`/`endLine` from the
  source. Unmatched quotes are dropped and counted.
- Response fields: `evidence: [{quote,startLine,endLine}]`, `evidenceRejected: int`.
- A configured `AI_EVIDENCE_REQUIRED` (default `false`) makes an empty accepted set
  invalid; the default stays lenient until the eval baseline shows the local model can
  supply evidence reliably.

Acceptance: a real quote yields correct line numbers; a fabricated quote is rejected and
counted; duplicate and short quotes are rejected; `required=true` turns an empty set into
an invalid verdict.

### C2. Contract and measurement

- OpenAPI, both DTOs and the application-side contract test are updated; `resultJson`
  carries the evidence so it is persisted with the verdict.
- The eval baseline records `validWithEvidence` and `evidenceRejected` rates.

## Allowed paths

New:

- `plans/ai-service-depth.md`, `docs/adr/0025-deepen-the-inference-service.md`
- `docs/research/ai-service-eval-2026-11-28.md`
- `benchmark/eval/**`
- `scripts/ai_eval.py`, `scripts/test_ai_eval.py`
- `.github/workflows/ai-eval.yml`
- `ai-service/src/main/java/com/nexus/campus/ai/{model,service}/` new classes:
  `OutputSchema`, `ReviewSchema`, `ReviewDeduplicator`, `ProviderCircuitBreaker`,
  `CallArchive`, `FileCallArchive`, `EvidenceExtractor`
- `ai-service/src/main/java/com/nexus/campus/ai/config/ArchiveConfig.java`
- `ai-service/src/test/**` new and updated tests

Modified:

- `ai-service/src/main/resources/application.yml`
- `ai-service/src/main/java/com/nexus/campus/ai/config/AiServiceProperties.java`
- `ai-service/src/main/java/com/nexus/campus/ai/model/{ModelGateway,SpringAiModelGateway,RetryingModelCaller}.java`
- `ai-service/src/main/java/com/nexus/campus/ai/service/{PromptFactory,ReviewService,SafetyService,ReviewValidator}.java`
- `ai-service/src/main/java/com/nexus/campus/ai/api/dto/{ReviewResponse,SafetyResponse}.java`
- `ai-service/src/main/java/com/nexus/campus/ai/api/{ReviewController,MetricsController}.java`
- `ai-service/openapi/ai-service.yaml`
- `src/main/java/com/nexus/campus/agent/AiServiceClient.java`
- `src/test/java/com/nexus/campus/agent/{AiServiceClientTest,ReviewParityTest,ServiceReviewerTest}.java`
- `docker-compose.yml` (archive path + volume only, if enabled)
- `docs/tickets/ai-service-extraction.md`, `docs/INDEX.md`, `README.md`

## Progress

- **A — capability parity:** implemented on `codex/ai-service-depth-a`. 66 ai-service
  tests; live dedup evidence recorded in the ticket.
- **B — measurement:** pending.
- **C — evidence anchoring:** pending.

## Verification commands

```bash
mvn -B test
mvn -B -f ai-service/pom.xml test
python -m unittest discover -s scripts -p "test_*.py"
python scripts/ai_eval.py --base-url http://localhost:8080/ai --baseline benchmark/eval/baseline-2026-11-28.json
```

The eval command runs against a live service; its exact URL is recorded in the research
note. The three Maven/Python commands are the CI gates.

## Forbidden

- Weakening, skipping or deleting an existing test to make a new path pass.
- A request-schema field in the application contract (the service owns it).
- Caching failures, invalid verdicts or anything keyed on `requestId`.
- Shipping evidence into the rendered comment body in this round.
- Raising the JaCoCo floor without a measured run, or lowering it to accommodate new code.
- Touching the unrelated dirty work in the main worktree.

## Rejected alternatives

- **Prompt-only structured output.** The status quo; provider enforcement is stricter and
  cheaper than repair retries.
- **Redis-backed dedup.** A new datastore for a ten-minute window with one replica.
- **Keying dedup on `requestId`.** The application mints a new trace id per dispatch, so
  re-dispatches would never hit.
- **Treating evidence as a policy input.** Evidence is data; approval and status remain in
  Java and unchanged.
- **Running the model in CI.** No hosted credential on pull requests; a green check that
  calls nothing would be worse than an honest manual gate.
