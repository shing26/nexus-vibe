# Evidence index — every outward claim, and what proves it

The project's discipline is "a claim without a reproducible path does not go in". This
index is the map: each thesis claim a reader might quote, the decision that records it,
and the artifact — a test class or a named command — that proves it. The verification
command for test-backed claims is `mvn -B test "-Dtest=<ClassName>"`; every class name
below is checked against the tree at `5df2eca`.

| Claim | Decision | Reproducible evidence |
|---|---|---|
| An unreachable LLM holds new posts in the audit queue (fail-closed), and reconciliation re-runs them on recovery | [ADR-0004](adr/0004-safety-check-fail-closed.md) | `mvn -B test "-Dtest=AiSafetyCheckListenerTest,AiReviewReconcileTaskTest"` |
| Model output is repair-parsed, semantically validated, retried once, then marked FAILED — never fabricated | the "Review Validity" vocabulary in [CONTEXT.md](../CONTEXT.md) | `mvn -B test "-Dtest=JsonRepairUtilTest,CodeReviewReviewerTest,AiReviewServiceTest"` |
| Reviews are claimed by an atomic lease; two workers cannot process one post; a crashed worker's task is re-dispatched | [ADR-0005](adr/0005-lease-based-review-claim.md) | `mvn -B test "-Dtest=ReviewLeaseClaimTest"` |
| A degraded dependency degrades the service but does not fail the container; the public read model is a closed server-owned vocabulary | [ADR-0007](adr/0007-degraded-status-is-not-unhealthy.md), [ADR-0019](adr/0019-degraded-capabilities-are-a-closed-server-owned-vocabulary.md) | `mvn -B test "-Dtest=HealthSemanticsIntegrationTest,CapabilityStatusIntegrationTest,CapabilityStatusAllUpIntegrationTest,DegradedCapabilityServiceTest"` |
| The review pipeline is pluggable at exactly two seams — the trigger rule and the reviewer | [ADR-0012](adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md) | `mvn -B test "-Dtest=BothSeamsReplacedIntegrationTest,CodeBlockReviewPolicyTest,PromptReviewReviewerTest,ReviewPolicySingleRuleTest"` |
| Comments are safety-checked after they are visible — a deliberate divergence from fail-closed | [ADR-0017](adr/0017-comments-are-checked-after-they-are-visible.md) | `mvn -B test "-Dtest=AiCommentSafetyCheckListenerTest"` |
| A comment mentioning the agent summons it, and silence is not an option: placeholder reply, cooldown, no recursion | [ADR-0018](adr/0018-a-comment-mentioning-the-agent-summons-it.md) | `mvn -B test "-Dtest=AgentMentionListenerTest"` |
| The landing page's showcase review is a recording of one real pipeline run, not prose that resembles one | [ADR-0010](adr/0010-the-showcase-review-is-a-recording.md) | `benchmark/showcase/record-showcase-review.py`; the recorded payload lives in `src/main/resources/showcase/` |
| The pipeline's saturation point is measured, not guessed: 201,880 requests, zero crashes | [async-pool-loadtest](research/async-pool-loadtest.md) | the command inside the report; the in-suite proxy is `mvn -B test "-Dtest=AgentPoolSaturationIntegrationTest"` |
| Migrations can be rolled back, and the rollback is rehearsed, not asserted | [the rehearsal result](../benchmark/migrations/results/migrations-20261114.md) | `benchmark/migrations/rehearse-migrations.ps1` |
| Search may lag after an Elasticsearch outage until an operator rebuilds — decided on measurement | [ADR-0011](adr/0011-search-may-lag-until-an-operator-rebuilds.md) | [es-index-lag-2026-09](research/es-index-lag-2026-09.md), the command inside the report |
| Alerts reach a real Feishu group, and a deliberately wrong secret is reported, not swallowed | [alert-bridge-address-fallback](research/alert-bridge-address-fallback-2026-09.md) | `cd docker/observability/alert-bridge && python -m unittest test_alert_bridge` (28 tests) |
| The public surface cannot reach actuator internals | the nginx contract | `mvn -B test "-Dtest=NginxEdgeSurfaceTest"` |

What this index deliberately does not claim: anything a test cannot check and a command
cannot re-run. The README's [evidence wall](../README.md#evidence-wall) carries the
measured numbers; [ADR-0009](adr/0009-scope-is-done-at-deployment.md) and
[ADR-0022](adr/0022-reopening-scope-for-the-self-demonstrating-community.md) record the
scope decisions behind which claims were worth proving at all.
