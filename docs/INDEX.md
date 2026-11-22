# Documentation index

Every document in `docs/`, in one place, with what it is for. If you add a document, add a row
here in the same commit — this file is the only complete list, and a document that is not in it
is a document nobody will find.

For the short version of how to work here, read [AGENTS.md](../AGENTS.md) first. For the
project's vocabulary, read [CONTEXT.md](../CONTEXT.md).

## Entry points — for a reader who just arrived

| File | Purpose |
|---|---|
| [reading-path](reading-path.md) | What to read in 30 seconds, 10 minutes or 90 minutes, with the artifact each tier points at |
| [evidence-index](evidence-index.md) | Every outward thesis claim mapped to the ADR, the test class and the command that proves it |

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
| [0012](adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md) | The review pipeline is pluggable at two seams - the trigger rule and the reviewer - and only two |
| [0013](adr/0013-config-validation-moves-to-validated-properties.md) | The AI settings bind into one `@Validated` object, and the bespoke validator goes |
| [0014](adr/0014-agent-dispatch-defers-at-the-publisher-not-the-listener.md) | Agent dispatch defers at the publisher, not at the listener - `@TransactionalEventListener` broke fail-closed |
| [0015](adr/0015-the-agent-pipeline-gets-metrics-and-rules.md) | The agent pipeline gets metrics and rules - the stack had watched everything except itself |
| [0016](adr/0016-the-scope-reopens-for-one-argued-ai-fusion-round.md) | The scope reopens for one argued AI-fusion round - three items, each reusing the existing guardrails |
| [0017](adr/0017-comments-are-checked-after-they-are-visible.md) | Comments are safety-checked after they are visible, not before - a deliberate divergence from fail-closed |
| [0018](adr/0018-a-comment-mentioning-the-agent-summons-it.md) | A comment mentioning the agent summons it, and silence is not an option - placeholder reply, cooldown, no recursion |
| [0019](adr/0019-degraded-capabilities-are-a-closed-server-owned-vocabulary.md) | A degraded capability is published as a closed, server-owned vocabulary - the banner reads consequence, not health |
| [0020](adr/0020-the-forge-pivot-replaces-the-terminal-aesthetic.md) | The forge pivot replaces the terminal aesthetic |
| [0021](adr/0021-content-is-stored-raw-and-escaped-at-egress.md) | Content is stored raw and escaped at egress |
| [0022](adr/0022-reopening-scope-for-the-self-demonstrating-community.md) | The scope reopens for a self-demonstrating community - ADR-0009's freeze is superseded |
| [0023](adr/0023-the-console-palette-and-a-dark-default.md) | The console palette and a dark default - supersedes ADR-0020's accent clause and system-following theme |

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
| [real-user-trial-2026-11](research/real-user-trial-2026-11.md) | A5's living record: the invitation every participant saw, their verbatim words, and the one SQL command that counts real usage — empty until humans fill it |
| [ux-walkthrough-2026-11-20](research/ux-walkthrough-2026-11-20.md) | A real browser walk of the user journey: one P1 (the author's own held post 404s on the detail page) and two P2 findings, with the reproduction |
| [llm-model-upgrade-options](research/llm-model-upgrade-options.md) | Model selection: local 7B versus hosted APIs, on quality and cost |
| [openai-code-review-prompt-strategies](research/openai-code-review-prompt-strategies.md) | Prompt strategies for structured code review output |
| [production-readiness-assessment-2026-09](research/production-readiness-assessment-2026-09.md) | The readiness assessment that generated the P0/P1 work |
| [project-module-audit-2026-09](research/project-module-audit-2026-09.md) | Module-by-module structure and completion audit |
| [project-structure-and-deep-dive-2026-09-18](research/project-structure-and-deep-dive-2026-09-18.md) | Whole-project walkthrough for deep-dive questions: packages, flows, mechanisms, data model, verification and known trade-offs |

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
| [ai-review-seam](tickets/ai-review-seam.md) | AI-1 to AI-4: making the trigger rule and the reviewer replaceable, and what deliberately stays fixed |
| [framework-reinforcement](tickets/framework-reinforcement.md) | The 2026-09-19 audit's still-real gaps: repository boundary, AI config fail-fast, raw LLM archive, and the gaps that are deliberately not worth closing |
| [evidence-and-gates](tickets/evidence-and-gates.md) | The seven 2/10 dimensions turned into reproducible artifacts: load-test, concurrency, duplicate-write, cross-user, coverage floor, migration rollback, validated config - and the B2 defect that is left open on purpose |
| [like-count-convergence](tickets/like-count-convergence.md) | The B2 defect fixed (the durable table is the authority), a saturated pool no longer aborting the reconcile batch, and two claims the evidence round had to withdraw |
| [review-terminal-replay](tickets/review-terminal-replay.md) | The finished AI review replays its steps on the post page, the in-flight box cycles them without confirming any, and the navbar/footer link out to the ADRs and the load test |
| [readme-open-source](tickets/readme-open-source.md) | The README becomes a front door: the diagnosis of the five stated pain points, the corrections to the circulated draft, the new section order, and the allowed numbers |
| [readme-visual-front-door](tickets/readme-visual-front-door.md) | The README gets a hero capture, a native Mermaid architecture diagram and a measured Lua-vs-naive consistency figure - and where the brief's score-92 label could not be honoured |
| [ai-fusion-round](tickets/ai-fusion-round.md) | ADR-0016's round: comment safety check (post-visible), prompt review on the ADR-0012 seam, and the agent mention with its placeholder-retry semantics |
| [frontend-polish-round-2](tickets/frontend-polish-round-2.md) | FE-1 crash reporting (browser to one capped log line), a /tags directory with visible-post counts, and the two critique items this round declined |
| [fe1-boundary-layering](tickets/fe1-boundary-layering.md) | FE-1's second half: per-route error boundaries under the layout, one broken page no longer unmounts the SPA, and the outer boundary stays as the last resort |
| [saturation-test-deflake](tickets/saturation-test-deflake.md) | One test class, three CI failures in a day: a poll deadline racing the retry ladder, a precondition that read one refusal as proof of a full pool, and a latch read from a field |
| [frontend-polish-round-3](tickets/frontend-polish-round-3.md) | Nine patterns from a comparable product, arbitrated against the tree: one is a defect (the author cannot see their own held post), five are cheap, and three are declined with the reason written down |
| [chaos-theater-a](tickets/chaos-theater-a.md) | `/theatre`: four scripted-failure exhibits replayed through the real review pipeline, public and offline — the thesis playable without an account |
| [chaos-theater-a2](tickets/chaos-theater-a2.md) | A2: the fix-challenge scenario (3→9 on apply-the-fix), four counterfactual runs, the score-dial/stamp payoff, JSON evidence highlighting, and Chinese-first step narration |
| [observability-forum-round](tickets/observability-forum-round.md) | Make the honest system legible in the community UI: sidebar system pulse, AI credential chips, narrative profile stats and per-tab ranking notes — borrowed selectively from Luzi, gamification declined |
| [my-creations-library](tickets/my-creations-library.md) | The owner's `/creations` library: posts, original prompts, plaza forks and device-local drafts as four counted kinds, all derived from real fields with no new endpoint |
| [review-severity-chip](tickets/review-severity-chip.md) | The feed shows the reviewer's severity verdict on the topic row: one batched query, real `ai_review_log` rows, unknown values render nothing |
| [a5-real-user-trial](tickets/a5-real-user-trial.md) | The harness for the real-user trial: invitation copy, journey definition, verbatim-quote log and the usage measurement — the humans are invited, not simulated |
| [r3-1-capability-throttle](tickets/r3-1-capability-throttle.md) | The anonymous capabilities endpoint stops re-probing per read: a TTL cache on the read model, the limiter rejected because it bypasses itself when Redis is down |
| [post-detail-author-404](tickets/post-detail-author-404.md) | Open: the author's just-published, held post answers 404 on the detail page — the list-side fix's missing half |
| [console-dark-slice](tickets/console-dark-slice.md) | Slice C1: the home shell re-skinned as a night-shift console — dark by default, one audit-green accent, stamps instead of decorative color |
| [post-detail-info-arch](tickets/post-detail-info-arch.md) | Slice C2: the detail page becomes one column — identity, body, evidence — and the accent swap's contrast regression is closed |

## Plans — `docs/plans/`

| File | Purpose |
|---|---|
| [pre-deployment-checklist](plans/pre-deployment-checklist.md) | The checklist every deployment runs through, plus the record of what actually happened on 2026-09-16 |
| [deployment-and-blog-plan](plans/deployment-and-blog-plan.md) | The original deployment plan and its local verification log |
| [portfolio-showcase-plan](plans/portfolio-showcase-plan.md) | The chosen direction: what "done" means for a portfolio artifact |
| [environment-matrix](plans/environment-matrix.md) | dev / prod / CI values for the key configuration, with the default and the overrider on each row |
| [fe-3-post-status-visibility](plans/fe-3-post-status-visibility.md) | Frozen contract for round-3 item 1: the author sees their own held posts, nobody else does, and the rejection reason is derived rather than stored |
| [fe-3-group-a-feed-copy](plans/fe-3-group-a-feed-copy.md) | Frozen contract for round-3 items 2, 4, 6, 8: empty-state copy, the unanswered panel, a per-channel liked ordering, and a test that pins the card's counts |
| [fe-3-group-b-degradation-banner](plans/fe-3-group-b-degradation-banner.md) | Frozen contract for round-3 item 3: a shaped public capability read model and a dismissible banner, without reopening the actuator paths ADR-0007 closed |
| [fe-3-group-c-detail-comments](plans/fe-3-group-c-detail-comments.md) | Frozen contract for round-3 items 5 and 7: a worked example on prompt posts, and a rule-based comment sentiment that cannot fail the write or go dark with the LLM |
| [fe-3-group-d-palette](plans/fe-3-group-d-palette.md) | Frozen contract for round-3 item 9: the palette's second destination is the row's link, not a product the tree does not have |
| [chaos-theater-a](plans/chaos-theater-a.md) | Frozen contract for the Chaos Theatre's first slice: allowed paths, the four scenario semantics, the honesty-badge rule, and what the demo may not do |
| [chaos-theater-a2](plans/chaos-theater-a2.md) | Frozen contract for A2: the fifth scenario's double-run semantics, the counterfactual runs (including the injection-obeyed 10/low), the new DTO fields, and the same forbidden list |
| [observability-forum-round](plans/observability-forum-round.md) | Frozen contract for the observability forum round: real-data-only pulse and credential, narrative stats and ranking notes, Luzi's gamification explicitly rejected, creations library deferred |
| [my-creations-library](plans/my-creations-library.md) | Frozen contract for the creations library: real-field kind derivation, counts matching cards, device-local drafts honestly marked, and the rejected aggregate endpoint |
| [review-severity-chip](plans/review-severity-chip.md) | Frozen contract for the severity chip: latest code-review row per page in one query, honest tone mapping, the N+1 and retry-chip rejections |
| [r3-1-capability-throttle](plans/r3-1-capability-throttle.md) | Frozen contract for R3-1: TTL cache branch of the acceptance, actuator bytes pinned, the Redis-dependent limiter rejected |
| [ai4-acceptance](plans/ai4-acceptance.md) | The frozen acceptance contract for AI-4: allowed paths, five criteria, verification commands, forbidden moves |

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
| [reviews/round-3-acceptance-evidence](reviews/round-3-acceptance-evidence.md) | Round 3's verdicts bound to a HEAD SHA each, the commands the verifier re-ran, the contract amendments, and what the round does not prove |
| [reviews/ai4-acceptance-2026-11-09](reviews/ai4-acceptance-2026-11-09.md) | The AI-4 acceptance record: frozen contract, independent verifier's evidence pack, the four human-review questions, written sign-off |
| [archive/Nexus-Campus-体验报告](archive/Nexus-Campus-体验报告.md) | The superseded deep experience report |
| [archive/Nexus-Campus-第二轮修复验证报告](archive/Nexus-Campus-第二轮修复验证报告.md) | Verification pass over the fixes from that report |

## Operations

| File | Purpose |
|---|---|
| [runbook/restore](runbook/restore.md) | Restore procedure, including the Elasticsearch reindex leg |
| [runbook/docker-compose.restore-test.yml](runbook/docker-compose.restore-test.yml) | Compose override that gives rehearsal containers distinct names |
| [blog/llm-code-review-structured-output-injection-defense](blog/llm-code-review-structured-output-injection-defense.md) | The public write-up of the review pipeline |
| [runbook/feishu-alerting](runbook/feishu-alerting.md) | The six-step Feishu alert-bot walkthrough, its error-code table and the contact-point check, moved out of the README in the front-door rewrite |
| [runbook/release-and-live-instance](runbook/release-and-live-instance.md) | Release, rollback, backup, the public-tunnel deployment and the live-instance notes, moved out of the README |

Screenshots referenced by the reviews live in
[`docs/assets/gui-test-screenshots/`](assets/gui-test-screenshots). The README's own hero
capture, community capture and consistency figure live in
[`docs/assets/readme/`](assets/readme); they are described, with the run that produced each
one, in [tickets/readme-visual-front-door.md](tickets/readme-visual-front-door.md).
