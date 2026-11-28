# 0026 — Recover review quality before enforcing evidence

Status: **Accepted — 2026-11-28.** Frozen contract: `plans/ai-service-depth-2.md`,
based on `master` `e96c706`.

## Context

ADR-0025 added evidence anchoring as increment C. The mechanism works: quotes are
matched against the submitted text, fabricated quotes are rejected, and the live smoke
observed five rejected quotes in one review. But the same 20-case golden set that
measured increment B also measured C, and the verdict got worse:

| run | prompt | score | severity | evidence | invalid |
|---|---|---|---|---|---|
| B | `4507b4663c31` | 0.85 | 0.40 | 0/20 | 0.00 |
| C | `eb5421e62edc` | 0.70 | 0.25 | 15/20 | 0.05 |

The evidence instruction is not free. It lengthened every prompt, changed the model's
attention, and did not define what `score` and `severity` mean relative to each other.
The model frequently rated a SQL injection or an XSS sink as `medium`/`low`, and its
score often contradicted the severity.

At the same time, the live stack still pays a cold-start tax: with a cold model, the
first 60 s attempt is spent loading, and only the second attempt succeeds. The 200 s
read timeout makes that self-heal, but the first user-visible review still takes about
89 s.

## Decision

Two small increments, in this order:

1. **D0 — warm the provider.** Keep the local Ollama model resident
   (`OLLAMA_KEEP_ALIVE=-1`) and add a small `ollama-warmup` compose sidecar that polls
   `ollama ps` and reloads the model after an Ollama restart. It is local-only, has no
   review-path code, and keeps the retry ladder, breaker, archive and review metrics
   untouched.
2. **D1 — recover the verdict before enforcing evidence.** Add an explicit score
   direction (quality, 10 = best) and a severity rubric to the service prompt and the
   provider-enforced schema. Keep the evidence instruction, but place it after the
   rubric so the model classifies risk first and quotes second. Measure both runs
   against the frozen targets and do not merge a prompt that misses them.

D2 (evidence enforcement, `model_timeout` counting, `approved`/severity coherence) and
D3 (rendering evidence in the product) wait until D1's numbers meet the targets.

## Rejected

- **Revert increment C.** The evidence is the part that makes a verdict checkable; the
  right response is to recover the verdict, not to remove the check.
- **Enforce `AI_EVIDENCE_REQUIRED=true` now.** Only 15/20 cases supply evidence, so this
  would turn 25% of otherwise-valid reviews into invalid verdicts before the prompt has
  learned to supply evidence reliably.
- **Two-pass verdict + evidence now.** It cleanly separates the two tasks but adds a
  second provider call to a path that is already 20–50 s per review; measure a
  single-call prompt first.
- **Switch to a larger model now.** It may improve severity, but it changes the cost,
  latency and deployment assumptions before the current model's prompt has been made
  explicit. The harness can compare a future model against the same baseline.
- **More service endpoints.** The gap is verdict quality and cold-start behavior, not
  missing API surface.

## Consequences

- The service prompt version changes; every baseline, dedup key and archive record is
  tied to the new version.
- The prompt-parity baseline is regenerated. The direct path is deliberately unchanged
  in this round, so the measured drift grows; the research note records the new number
  and the reason.
- The local stack starts the model before the first review instead of during it. A
  hosted provider stack does not start the local-only sidecar and pays no warmup
  tokens.
