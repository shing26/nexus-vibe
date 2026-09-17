# Documentation index

Every document in `docs/`, in one place, with what it is for. If you add a document, add a row
here in the same commit — this file is the only complete list, and a document that is not in it
is a document nobody will find.

For the short version of how to work here, read [AGENTS.md](../AGENTS.md) first. For the
project's vocabulary, read [CONTEXT.md](../CONTEXT.md).

## Decisions — `docs/adr/`

Numbered, append-only. A reversed decision gets a new ADR, not an edit.

| File | Decision |
|---|---|
| [0001](adr/0001-rename-campus-entities-to-vibe-names.md) | Rename campus-domain entities to Vibe names |
| [0002](adr/0002-ai-agent-review-trigger-and-display.md) | AI review triggers only on posts containing code, and renders in two places |
| [0003](adr/0003-llm-safety-multiclass-handling.md) | Safety check returns four classes, each with its own handling |
| [0004](adr/0004-safety-check-fail-closed.md) | When the LLM is unavailable, moderation fails closed |
| [0005](adr/0005-lease-based-review-claim.md) | Reviews are claimed by an atomic conditional UPDATE |
| [0006](adr/0006-deepseek-hosted-model.md) | Hosted DeepSeek is the production review model, with local Ollama as fallback |
| [0007](adr/0007-degraded-status-is-not-unhealthy.md) | A degraded dependency degrades the service; it does not fail the container |
| [0008](adr/0008-bootstrap-admin-and-empty-production-seed.md) | The production seed is empty and the administrator is bootstrapped once from the environment |
| [0009](adr/0009-scope-is-done-at-deployment.md) | The deliverable stops at a completed deployment |
| [0010](adr/0010-the-showcase-review-is-a-recording.md) | The landing page shows a recorded pipeline run, not prose that resembles one |
| [0011](adr/0011-search-may-lag-until-an-operator-rebuilds.md) | Search may lag until an operator rebuilds, and the rebuild is the price |

## Measurements — `docs/research/`

Each of these names the command or script that produced its numbers. Reproduce before quoting.

| File | What was measured |
|---|---|
| [async-pool-loadtest](research/async-pool-loadtest.md) | 201,880 requests against the AI pipeline: 121,202 backpressure rejections, zero crashes, and where it actually saturates |
| [jvm-container-gc-analysis](research/jvm-container-gc-analysis.md) | GC behaviour under a 768 MB container limit, and the basis for `MaxRAMPercentage=75` |
| [mysql-ai-sort-index-explain](research/mysql-ai-sort-index-explain.md) | A composite index for AI-sorted feeds: 3x faster with a filter, slower without |
| [late-row-lookup-deep-pagination](research/late-row-lookup-deep-pagination.md) | A delayed-join optimisation that measured 40% slower and was rolled back |
| [observability-drill-2026-09](research/observability-drill-2026-09.md) | Scripted failure injection against the real stack, including what the drill cannot prove |
| [observability-ops-review-2026-09](research/observability-ops-review-2026-09.md) | An external review of the observability and ops claims, and which of them held up |
| [alert-bridge-values-shape-2026-09](research/alert-bridge-values-shape-2026-09.md) | A payload shape the bridge assumed instead of recording: why a non-zero value stopped every alert, and the three test surfaces that each missed it |
| [alert-bridge-address-fallback-2026-09](research/alert-bridge-address-fallback-2026-09.md) | A CDN edge that accepts TCP and blackholes TLS: why urlopen retried nothing, the live before/after, and the wrong-secret check it had been masking |
| [alert-path-in-ci-2026-09](research/alert-path-in-ci-2026-09.md) | Which three drill steps moved into CI, the precondition they turned out to need, the two mutations that proved the gate goes red, and why the rollback step did not |
| [es-index-lag-2026-09](research/es-index-lag-2026-09.md) | Elasticsearch recovery made search return less: the post that was findable during the outage and gone after it, and the app that never indexes again if it boots without the cluster |
| [llm-model-upgrade-options](research/llm-model-upgrade-options.md) | Model selection: local 7B versus hosted APIs, on quality and cost |
| [openai-code-review-prompt-strategies](research/openai-code-review-prompt-strategies.md) | Prompt strategies for structured code review output |
| [production-readiness-assessment-2026-09](research/production-readiness-assessment-2026-09.md) | The readiness assessment that generated the P0/P1 work |
| [project-module-audit-2026-09](research/project-module-audit-2026-09.md) | Module-by-module structure and completion audit |

## Work items — `docs/tickets/`

One file per round. Each ticket carries scope, acceptance, the files it touches, and the
alternatives that were rejected.

| File | Round |
|---|---|
| [p0-optimization](tickets/p0-optimization.md) | The first P0 list, mostly exhausted |
| [production-readiness](tickets/production-readiness.md) | Observability, health semantics, bootstrap admin, non-root container |
| [evidence-credibility](tickets/evidence-credibility.md) | Making the CI gate and the ops evidence trustworthy |
| [contract-and-product-loop](tickets/contract-and-product-loop.md) | HTTP contract truthfulness, frontend regression tests, product funnels, mobile reach |
| [next-cycle-backlog](tickets/next-cycle-backlog.md) | Deferred work, refiltered by the portfolio direction — a backlog, not a plan |
| [portfolio-showcase](tickets/portfolio-showcase.md) | A4/A6: the landing-page review a signed-out visitor reads, and why it is a recording |

## Plans — `docs/plans/`

| File | Purpose |
|---|---|
| [pre-deployment-checklist](plans/pre-deployment-checklist.md) | The checklist every deployment runs through, plus the record of what actually happened on 2026-09-16 |
| [deployment-and-blog-plan](plans/deployment-and-blog-plan.md) | The original deployment plan and its local verification log |
| [portfolio-showcase-plan](plans/portfolio-showcase-plan.md) | The chosen direction: what "done" means for a portfolio artifact |

## Design and product

| File | Purpose |
|---|---|
| [design/ui-visual-system](design/ui-visual-system.md) | Visual system for the AI review and profile workspace |
| [design/ux-architecture](design/ux-architecture.md) | UX architecture for the same surfaces |
| [product/prioritization](product/prioritization.md) | What was judged worth building, and what was not |
| [product/optimization-plan](product/optimization-plan.md) | The feedback-loop optimisation plan |

## Reviews and archive

| File | Purpose |
|---|---|
| [reviews/three-persona-journey-2026-09-06](reviews/three-persona-journey-2026-09-06.md) | Full-journey walkthrough from three personas, with `file:line` evidence |
| [reviews/black-box-exploration-2026-09-09](reviews/black-box-exploration-2026-09-09.md) | Black-box exploration of the running product |
| [archive/Nexus-Campus-体验报告](archive/Nexus-Campus-体验报告.md) | The superseded deep experience report |
| [archive/Nexus-Campus-第二轮修复验证报告](archive/Nexus-Campus-第二轮修复验证报告.md) | Verification pass over the fixes from that report |

## Operations

| File | Purpose |
|---|---|
| [runbook/restore](runbook/restore.md) | Restore procedure, including the Elasticsearch reindex leg |
| [runbook/docker-compose.restore-test.yml](runbook/docker-compose.restore-test.yml) | Compose override that gives rehearsal containers distinct names |
| [blog/llm-code-review-structured-output-injection-defense](blog/llm-code-review-structured-output-injection-defense.md) | The public write-up of the review pipeline |

Screenshots referenced by the reviews live in
[`docs/assets/gui-test-screenshots/`](assets/gui-test-screenshots).
