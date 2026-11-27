# 0025 — Deepen the inference service where it is thinner than the direct path

Status: **Accepted — 2026-11-28.** The frozen contract is
`plans/ai-service-depth.md`, based on `master` `2ddf455`.

## Context

ADR-0024 moved model-facing inference behind a service and deliberately kept policy in
Java. The extraction preserved the failure paths, but it did not carry three
model-facing capabilities the direct path already had:

1. **Structured output.** `LlmClient` sends a `response_format` selected by
   `AI_RESPONSE_FORMAT` (`json_schema`, `json_object` or `none`) and includes the JSON
   schema. `SpringAiModelGateway` sets only the model and temperature, and the service's
   request contract has no schema field. The service therefore relies on prompt text
   alone, which is strictly weaker than the path it replaced.
2. **A circuit breaker.** `LlmClient` fails fast after a failure threshold and exposes
   `llm.circuit.breaker.open`. The service retries, but a provider that keeps failing
   keeps occupying request threads.
3. **A call archive.** The direct path appends one JSONL line per provider attempt, so a
   disputed verdict can be reconstructed. The service logs, but keeps no faithful record
   of the request and response text.

Two further gaps are not regressions but missing depth:

4. **Nothing measures quality.** The parity suite compares shapes and verdicts on stubbed
   responses, and the prompt-drift corpus compares text. Neither says whether reviews
   got better or worse. There is no golden set, no prompt version and no regression gate
   for model-facing changes.
5. **Nothing verifies a review's claims.** A verdict is an assertion. The service accepts
   whatever the model says about the code, with no check that the quoted problem exists
   in the submitted text.

`requestId` is also logged but unused: a lease re-dispatch or reconciliation sweep
re-runs the same content and pays for it again.

## Decision

Three increments, each its own PR and contract update, each with a measured acceptance
criterion. No new runtime dependency, no new datastore, no RAG, no agent/tool calling, no
fine-tuning, no streaming.

1. **Restore capability parity (increment A).**
   - The service owns the output schema and sends `response_format` to the provider.
     `AI_LLM_RESPONSE_FORMAT` selects `json_schema` (default), `json_object` or `none`.
     The application's request contract does not change: the schema is a model-facing
     concern, and the service already owns prompt text.
   - Review results are deduplicated by a content key (kind, title, content, snippets,
     model, prompt version) with in-flight coalescing and a bounded TTL cache. Errors
     and invalid verdicts are never cached. `requestId` is not the key, because the
     application mints a new trace id per dispatch.
   - A provider circuit breaker (threshold, open window, single half-open probe) fails
     fast with 503 instead of queueing more doomed attempts.
   - A JSONL call archive records one line per provider attempt when a path is
     configured. An archive failure never fails a review.
2. **Make quality measurable (increment B).**
   - Every response carries a `promptVersion` derived from a hash of the static prompt
     templates and schemas, so a change is visible without trusting a human to bump a
     constant.
   - A committed golden set (`benchmark/eval/`) and `scripts/ai_eval.py` measure score
     agreement, severity agreement, invalid rate, repair rate, p50/p95 latency, token
     use and evidence rejection against a live service.
   - The measured run is committed as a baseline with its model and prompt version. A
     later run fails when a metric drops below the baseline. CI validates the corpus and
     the metric maths, but does not call a model: no hosted credential is available to
     pull requests.
3. **Make reviews verifiable (increment C).**
   - The model returns short quotes from the submitted content as evidence. The service
     locates each quote in the source (whitespace-normalized), computes line numbers, and
     rejects quotes it cannot find. The response carries accepted evidence and a rejected
     count; Java's `ReviewValidity` remains the final authority.
   - Evidence is data, not a new policy: post status, approval and comment rendering do
     not change in this increment.

## What deliberately does not change

- The application still owns trigger rules, the lease, attempt counts, `ReviewValidity`,
  status transitions, fail-closed behaviour and reconciliation.
- The service remains stateless across restarts: the dedup cache is in-process and
  bounded, and losing it costs a repeated inference, never correctness.
- The typed contract keeps its shape except for additive response fields
  (`promptVersion`, `evidence`, `evidenceRejected`) and no new request field.
- No frontend change, no database change, no comment-body change.

## Consequences

- The service becomes slightly less stateless: a bounded cache and a breaker hold
  in-memory state. Both are disposable and cannot corrupt a verdict.
- `json_schema` requires provider support. Ollama accepted it on 2026-11-28; the setting
  is configurable so a hosted provider that only supports `json_object` is a config
  change, not a code change.
- Evidence anchoring can lower the valid-response rate on a small local model. The eval
  baseline records both the rejection rate and the resulting invalid rate, which is the
  point: the cost is visible rather than hidden behind a convincing paragraph.
- The eval harness needs a live model, so it cannot be a pull-request gate. CI gates the
  corpus and the metric maths; the regression gate is a documented local/nightly run.
  Pretending otherwise would be a green check that measures nothing.
- Archive files grow. The path is opt-in and the operator owns rotation, matching the
  direct path's existing posture.

## Alternatives considered

- **Keep prompt-only JSON.** No provider dependency, but repair retries and invalid
  outputs cost more than the schema does, and the direct path already proved the stricter
  mode works.
- **Key idempotency on `requestId`.** Simple, but the application derives it from a
  per-dispatch trace id, so a re-dispatch would never hit. Content addressing is the
  property that matters.
- **A Redis-backed cache.** Survives restarts and shares across replicas, at the cost of
  a new datastore for a ten-minute dedup window. Rejected until there is more than one
  replica.
- **A full evaluation platform (LangSmith-style).** More capable, but it would add an
  external service and a vendor to a project whose value is showing the mechanics. A
  committed corpus and a Python script are enough to make the claim measurable.
- **RAG, agents or fine-tuning.** All still require the evaluation baseline this ADR
  introduces before they can be argued for. ADR-0024's retrieval condition is unchanged.

## Relationship to earlier decisions

This ADR operates under ADR-0022's reopened scope and does not supersede ADR-0009.
It preserves ADR-0024's boundary and typed contract, ADR-0004's fail-closed rule,
ADR-0005's lease ownership, ADR-0007's degraded-but-serving semantics and ADR-0012's
seams. Retrieval remains out of scope under ADR-0024 §6.
