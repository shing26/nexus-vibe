# Reading path — how much time do you have?

This project's subject is not the forum: it is what happens when a system treats an
LLM as an unreliable dependency — a model that goes down, emits malformed output, and
gets injection attempts thrown at it. The forum is the stage; the failure handling is
the play. Pick a door below.

## 30 seconds — see it, don't read it

- Open the [live demo](https://qualifier-discuss-marry.ngrok-free.dev) and click the
  reviewed showcase post on the home page. One real stored review — score, severity,
  verdict, findings — rendered without registering. The finished review replays its
  steps in order ([ADR-0010](adr/0010-the-showcase-review-is-a-recording.md) records
  why it is a recording, and the script that produced it lives under `benchmark/showcase/`).
- Or open [`/theatre`](https://qualifier-discuss-marry.ngrok-free.dev/theatre) and press
  a scenario button: five scripted failure modes replayed through the real review
  pipeline, each step badged REAL / INJECTED / POLICY so you can tell what actually
  executed ([the A2 ticket](tickets/chaos-theater-a2.md) records the semantics).

## 10 minutes — read the three decisions the pipeline is built around

1. [Fail-closed moderation](adr/0004-safety-check-fail-closed.md) — an unreachable model
   holds new posts in the audit queue instead of publishing unreviewed, and
   reconciliation re-runs them when the model comes back.
2. [Lease-based review claims](adr/0005-lease-based-review-claim.md) — an atomic
   conditional UPDATE means two workers can never process one post, and a crashed
   worker's task is re-dispatched, not lost.
3. [Degraded is not unhealthy](adr/0007-degraded-status-is-not-unhealthy.md) — a dead
   dependency degrades the service; it does not fail the container, and the status code
   answers exactly one question.

If the shape interests you, continue with [the two seams](adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md)
(the trigger rule and the reviewer are replaceable beans, and only those two) and
[the recording discipline](adr/0010-the-showcase-review-is-a-recording.md). The README's
[evidence wall](../README.md#evidence-wall) carries the measured numbers behind all of it.

## 90 minutes — walk the code path end to end

- The pipeline in source order, under `src/main/java/com/nexus/campus/`: the trigger rule
  (`agent/ReviewPolicy.java` and its two implementations), the listeners that claim a
  lease and call the model (`agent/AiReviewEventListener.java`, `agent/AiSafetyCheckListener.java`),
  the repair-parse and semantic validation (`agent/JsonRepairUtil.java`, `agent/AbstractLlmReviewer.java`),
  and the reconciliation tasks (`task/`).
- The measurements, each naming the command that produced it: the
  [201,880-request saturation run](research/async-pool-loadtest.md), the
  [23-step failure drill](research/observability-drill-2026-09.md) (which ends by
  listing what it cannot prove), the [Elasticsearch lag decision](research/es-index-lag-2026-09.md),
  and the [migration rollback rehearsal result](../benchmark/migrations/results/migrations-20261114.md).
- The public write-up: [how the structured-output review survives injection attempts and
  malformed model output](blog/llm-code-review-structured-output-injection-defense.md).
- Every thesis claim with its ADR, its test class and its re-run command lives in one
  place: the [evidence index](evidence-index.md). If you want to check a claim instead
  of trusting it, start there.

Verified against the tree at `5df2eca` (2026-11-19).
