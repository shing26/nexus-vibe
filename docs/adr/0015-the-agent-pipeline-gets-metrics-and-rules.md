# 0015 — The agent pipeline gets metrics and rules, because the stack watched everything except itself

[ADR-0009](0009-scope-is-done-at-deployment.md) froze the deliverable at a completed deployment and
said further work has to be argued for. This argues for it, and the argument is unusually direct:
the defect recorded in [ADR-0014](0014-agent-dispatch-defers-at-the-publisher-not-the-listener.md)
passed 414 tests, and had the monitoring been able to see the agent pipeline it would not have.

The reasoning goes like this. The bug made pool-saturation rejection unreachable. The consequence was
a post left `ACTIVE` and publicly visible with no safety verdict and no `pending-llm` marker, so no
reconciliation sweep could find it. At the moment it shipped, every signal the stack had read healthy:
the breaker gauge was 0 because the breaker was never consulted, the review backlog was 0 because
the post was never enqueued, the 5xx ratio was 0 because no request failed, every container was
healthy, and `/actuator/health` returned UP. That is a coherent picture, and it was wrong about the
one thing the system exists to do. **The instrumentation was green because it was pointed at
everything except the pipeline.**

## What was actually there

Measured against the running stack before changing anything, not inferred from the config files:

- Six alert rules, all provisioned, all loaded, all `health: ok`. Four of them could not have fired
  in that state for structural reasons rather than because the system was well: the breaker and
  backlog gauges were constant 0, and the 5xx-ratio and SLO rules sit behind a 0.05 rps traffic
  guard that held the query at zero traffic. `rate_limit_rejected_total` did not exist yet, because
  Micrometer creates a counter lazily and nothing had been rate-limited.
- The LLM completion counter read **0**. Every HTTP request the app had served was
  `/actuator/health` or `/actuator/prometheus` — the monitoring stack was the only traffic in the
  system. So "no alerts" and "nothing happened" were indistinguishable, and the rules were being
  evaluated correctly against an idle system.
- No metric existed anywhere for pool rejection, fail-closed holds, or the safety audit backlog.
  The review side had `ai.review.pending.posts`; the safety side, which is the fail-closed half of
  the design, had nothing.

The asymmetry in that last line is the defect. A moderation queue nobody watches is
indistinguishable from a moderation queue that is empty.

## Decision

**Three counters and one gauge on the pipeline, and three rules over them.** The counters are
registered eagerly in `PostAgentEventPublisher`'s constructor rather than created on first use, so
"nothing has been rejected" is a measured `0` and a *missing series* means the process, the
endpoint, or the scrape is gone. The rules can then treat absent data as a fault, which is the only
way an alert about a rarely-firing path can be trusted.

- `agent.pool.rejected.review` / `agent.pool.rejected.safety` — pool saturation, split by producer
  rather than tagged. Both producers are literals at their call sites, so a free-form reason tag
  would be operator-facing prose turned into cardinality.
- `agent.fail.closed` — posts held in the audit queue. Incremented from **both** paths that hold a
  post: the publisher's enqueue-time hold, and `AiSafetyCheckListener`'s unusable-result hold. The
  second is the common case, and an LLM answering with something unparseable never reaches the
  publisher — so counting only the enqueue path would have let the backlog climb with nothing
  incrementing, which is the exact blind spot being closed.
- `safety.pending.posts` — the gauge for the audit backlog itself, from a new
  `countSafetyPendingPosts()` and deliberately without the reconcile select's stale window. It
  answers "how much is moderation not answering right now", not "what is due for another attempt".
  Refreshed before the reconcile task's health gate, because this backlog only exists while
  something is wrong — a gate would skip it at precisely the wrong moment.

Threshold is 0 for both backlog and fail-closed, sustained 15 minutes. Not laziness: a post in this
state is invisible to users by design, so one that lingers is a moderation decision nobody made.
The baseline on the accepted demo dataset is 0, so it does not fire on day one.

**Grafana becomes a scrape target.** Without it, a dead Grafana takes all nine rules with it and the
only symptom is silence — the one failure monitoring cannot report, because the thing that would
report it is what died. Scraping it makes blackout at least distinguishable from idleness.

**A rule over Grafana's own alertmanager.** `nexus-alert-delivery-unprocessed` watches
`grafana_alerting_alertmanager_alerts{state="unprocessed"}`. Alerts that fire and go nowhere are
worse than no alerts, because they read as coverage; every other rule would look healthy while this
was the only honest signal in the stack.

## The rollout trap, and why the fix was in the expressions

Worth recording because it is the kind of thing that produces a monitoring change nobody can later
distinguish from a real one. Provisioning loaded before the app that emits the new series, and two
rules immediately paged: `noDataState: Alerting` plus an old build that publishes no such metric is
an empty result, and empty was indistinguishable from "broken". Alertmanager held two firing
alerts within seconds of the restart. A monitoring change that manufactures a false page gets muted
by the first person it pages, and then it protects nothing.

The fix was not to relax the policy. Both expressions gained a tail that separates "the pipeline is
rejecting work" from "this build does not publish the counter yet":

```promql
max(increase(agent_pool_rejected_review_total{application="nexus-vibe"}[15m]))
  or max(increase(agent_pool_rejected_safety_total{application="nexus-vibe"}[15m]))
  or max(up{job="nexus-vibe"} * 0)
```

`up` is 1 while the app answers, so the tail yields a measured 0 and the rule stays quiet. Every arm
is wrapped in `max()` because `or` unions *distinct* label sets: an unwrapped counter and `up` carry
different labels, so once the counter starts existing the reduce/last stage would be choosing
between two series. Collapsing both to no labels keeps the result a single series.

**What this does not cover, stated plainly because the obvious reading is wrong.** A failed scrape
sets `up` to 0; it does not remove the series. So `max(up * 0)` is still 0 when the app is dead and
this rule stays quiet. That is deliberate rather than an oversight — app death is
`nexus-prometheus-scrape-failed`'s job, and two rules paging for one event is worse than one. The
only way this expression goes empty is the `nexus-vibe` job disappearing from `prometheus.yml`, which
is a configuration fault no other rule in the file would see.

Verified in the running stack: all three new rules evaluate to 0, all nine report `health: ok`, and
alertmanager holds nothing.

One error worth recording, because the drill is what caught it. The first version of the expression
named `agent_pool_rejected_total`, which is not a series that exists — Micrometer emits
`agent_pool_rejected_review_total` and `agent_pool_rejected_safety_total` separately, and there is no
unsuffixed parent. The rule would have been decoration in the exact way
[`alert-rules-select-real-metrics`](../research/alert-path-in-ci-2026-09.md) exists to catch, and the
`up * 0` tail would have concealed it by returning a healthy 0 forever. Naming the producer in the
metric rather than in a tag is what made the mistake possible in the first place; the fix is to sum
both names explicitly.

## Rejected

**Alerts on `/actuator/health/deps`.** It reports db, redis, es and llm, and deliberately reports
`DEGRADED` as HTTP 200 — [ADR-0007](0007-degraded-status-is-not-unhealthy.md) made that choice so a
degraded-but-serving dependency does not take the app out of rotation. An alert on it would fire
continuously and mean nothing, because those dependencies are *designed* to degrade. Watch the
consequences instead, which is what the pipeline counters do.

**A synthetic-posting canary to manufacture the traffic the rules need.** It would make the ratio
and SLO rules evaluable, at the cost of writing into the demo dataset the maintainer has explicitly
accepted, and with a local Ollama taking about a minute per review — far too slow for a 1m probe
interval. The rules are instead written to hold their meaning at zero traffic.

**Wrapping the counter reasons in a `reason` tag.** Free-form operator prose in a tag is unbounded
cardinality by construction, and there are exactly two producers.

## What this still cannot do

Named here rather than left to be discovered:

- **A rule evaluated by Grafana cannot report Grafana's death.** If the process is gone it evaluates
  nothing, including `nexus-alert-delivery-unprocessed`. Closing that needs Prometheus-side
  alerting — Alertmanager rather than Grafana — which is a larger change than this one and is in
  `docs/tickets/next-cycle-backlog.md`.
- **`agent_fail_closed_total` counts events, not posts.** A post that fails closed, is reconciled,
  and fails closed again increments twice. The gauge is the state; the counter is the rate. Neither
  is wrong, and reading them as the same thing would be.
- **Zero real traffic means the ratio and SLO rules remain structurally unproven here.** Their
  expressions are unchanged from before and are exercised by
  [`observability-drill-2026-09`](../research/observability-drill-2026-09.md), not by this
  deployment.
