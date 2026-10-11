# Nexus-Vibe

**AI-Powered Vibe Coding & Developer Community**

![CI](https://github.com/shing26/nexus-vibe/actions/workflows/maven.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-18-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)
![Tests](https://img.shields.io/badge/tests-417%20Java%20%2B%2042%20frontend-brightgreen)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

Nexus-Vibe is a full-stack AI developer community platform - a modern replacement for the traditional campus forum. Built with Spring Boot 3.3 + React 19, it runs an AI-governed content pipeline: async LLM code review with semantic validation, structured-output safety checks that fail closed, lease-based task claims that survive crashes, and per-user activity workspaces - all wrapped in an IDE-station dark UI.

**The subject is not the forum.** It is a demonstration of treating an LLM as an unreliable dependency, and the code that matters is the failure handling around it: posts queue for review instead of publishing unreviewed, two workers can never claim one post, malformed model output is repaired and retried, and an unreachable model degrades the service instead of taking the site down. Every number in this README names the run that produced it. It is also built to be forked: one compose file, two pipeline seams, and a review terminal whose steps are a data file.

## Live demo

**https://qualifier-discuss-marry.ngrok-free.dev**

No account needed to see what this project is actually about. The home page links one
already-reviewed post, and `/post/900000000000000001` renders the AI review recorded from a
real pipeline run - score, severity, verdict, findings - to a signed-out visitor. On the post
page the finished review **replays its steps in order**: the finding sections confirm one by
one, then the score counts itself in. [ADR-0010](docs/adr/0010-the-showcase-review-is-a-recording.md)
records why it is a replay of a recorded run rather than a live feed, and `benchmark/showcase/`
holds the script that produced it, so the claim is reproducible rather than asserted. The seed
is display-only: a row set written once, not a fixture the app switches to.

![The recorded showcase review rendered by the live deployment](docs/assets/showcase/recorded-review.png)

The whole stack runs on one Windows machine behind an ngrok tunnel: nginx -> Spring Boot ->
MySQL / Redis / Elasticsearch, with the LLM pointed at the compose-local Ollama service.
Registering an account is enough to try it; publish a post containing a code block and an
AI review appears as a reply once the local model finishes the review.

Two things worth knowing before you click:

- ngrok's free tier shows a one-time "Visit Site" warning page the first time a browser
  sees the domain. It is not part of this project; one click dismisses it for seven days.
- It is a single home machine. If it is asleep the address stops answering, and every
  piece of evidence below was measured on the same class of machine rather than in a cloud
  region.

## Run it

Three paths, each with its own port. Prerequisites: Docker with Compose v2 for path A;
JDK 18+ and Maven 3.9+ (plus Node.js 18+) for path B.

### A. Full stack, one command (recommended)

```bash
cp .env.example .env
# fill the four blanks: DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD
docker compose up -d --build
```

Open **http://localhost:8080** (`WEB_PORT` - the only port the stack publishes). The first run
builds the `app` and `web` images and takes a few minutes; after that, `up -d` is seconds.
`nexus-app`, `nexus-db`, `nexus-redis` and `nexus-ollama` stay inside the compose network;
Elasticsearch is optional and internal as well.

The AI pipeline needs one model pulled into the compose-local Ollama:

```bash
docker compose exec ollama ollama pull qwen2.5:3b   # qwen2.5:7b if you have the compute
```

**Model quality bar.** The review agent validates output semantically: schema-valid but empty
or placeholder responses - common from local 7B/3B models - are retried once, then degrade
silently (logged `unknown`, no score, no AI comment). Until a model is pulled, or while the
endpoint is unreachable, the safety check fails closed and new posts wait in the audit queue
rather than publishing unreviewed. Local models exercise the pipeline; for reviews you can
actually use, point `LLM_ENDPOINT` at a hosted model.

### B. Local development

```bash
git clone https://github.com/shing26/nexus-vibe.git
cd nexus-vibe
mvn spring-boot:run   # backend on :8081, H2 in-memory, schema and seed auto-created
cd frontend && npm install && npm run dev   # SPA on :5173, /api proxied to :8081
```

Dev mode seeds `admin / 123456` (plus `shing` and `alice`) into the in-memory database. That
seed is a development convenience; it is not a deployment feature, and the compose stack never
has it.

### C. A forum with no model at all

No API key, no Ollama, no model download. Add two lines to `.env` before `up -d --build`:

```bash
AI_REVIEW_ENABLED=false
AI_SAFETY_ENABLED=false
```

With both switches off, the forum posts, comments and likes normally and nothing calls a
model - a documented state, not a hack. The switches are independent: review off stops
code-review comments and scores; safety off stops the LLM classification and its fail-closed
queueing.

### About the accounts

There is no `admin / Admin@123456`, and there is no mock login. A deployment with seeding off -
the production default, `DEMO_SEED_ENABLED=false` - holds **no** demo accounts and **no** sample
posts; the one administrator comes from `BOOTSTRAP_ADMIN_PASSWORD`, created once while the
database has no `ADMIN` ([ADR-0008](docs/adr/0008-bootstrap-admin-and-empty-production-seed.md)).
Rotate it after logging in. For a throwaway demo instance, `DEMO_SEED_ENABLED=true` with
`DEMO_PASSWORD` seeds accounts and sample content - never in production.

## What you get

**The forum.** Registration and JWT auth with unique-email recovery, Markdown posts with live
preview, channel browsing with slug routing, threaded comments, prompt-template forking with
version history and rollback, per-user profile workspace with an activity timeline, and an
admin audit dashboard with assisted account recovery.

**The AI pipeline.** Posts containing code blocks are reviewed asynchronously with structured
output (score, quality, security, suggestions) and a visible rubric; fenced output, truncation
and trailing commas are repair-parsed, and unparseable output is retried with the parser
error so a review stays within two model calls; a second listener classifies content into
four safety classes; editing a post re-triggers the review and supersedes the stale comment;
the whole run history is queryable with severity stats and filters.

**The parts that carry the thesis.** Redis Lua like toggle with write-behind flush and hourly
drift reconciliation, sliding-window rate limiting, DFA sensitive-word filtering with hot
reload, gravity-decay hot ranking, lease-based review claims with an attempt budget and a
terminal notification, a dedicated agent pool that turns a saturated dispatch into a
retryable state instead of failing the request, and an IDE-station dark UI whose motion work
collapses under `prefers-reduced-motion`.

## Adapting it - the fork map

[ADR-0012](docs/adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md) publishes exactly
two seams, and everything the seams are not stays fixed.

- **`ReviewPolicy` - should this post be reviewed?** One method,
  `boolean shouldReview(VibePost post)`, resolved as a Spring bean with
  `@ConditionalOnMissingBean`; the default `CodeBlockReviewPolicy` is today's
  fenced-code-block rule. Define your own bean and it is the rule, without editing a core file.
- **`Reviewer` - who produces the verdict?** Decided in ADR-0012 as a verdict object plus a
  default `LlmReviewer` bean, and **not yet in the tree**: today the producing half still sits
  inside `AiReviewService`, and the split is tracked as AI-2 in
  [docs/tickets/ai-review-seam.md](docs/tickets/ai-review-seam.md), status open. This line
  changes in the commit that lands it.

**What is deliberately not a seam:** the lease claim
([ADR-0005](docs/adr/0005-lease-based-review-claim.md)), the reconciliation task, fail-closed
moderation ([ADR-0004](docs/adr/0004-safety-check-fail-closed.md)), the terminal notification
when the attempt budget runs out, and review validity - the semantic check that decides
whether a model's output may be recorded at all. These are the thesis; a pipeline that let a
fork switch them off would delete the reason this repository exists. The couplings that stay
fixed for now (package and property naming, the `announcements` slug guard, `postType=prompt`
versioning, core-power rewards) are listed in the same ticket.

**Swapping the model** is three environment variables - `LLM_ENDPOINT`, `LLM_MODEL`,
`LLM_API_KEY` - bound under `campus.ai.llm.*`
([ADR-0013](docs/adr/0013-config-validation-moves-to-validated-properties.md)). Any
OpenAI-compatible chat-completions endpoint works; a provider that rejects strict JSON
schemas degrades into the plain-completion fallback instead of failing, and
`AI_RESPONSE_FORMAT` picks `json_schema` / `json_object` / `none` per provider.

**The review beats in the UI are data.**
[reviewSteps.ts](frontend/src/components/ui/reviewSteps.ts) holds the four steps the terminal
replays, `ReviewStepStrip.tsx` renders the strip, and `AiReviewTerminal.tsx` owns the replay.
Changing labels, icons or order is a frontend-only edit; a genuinely new assessment dimension
needs a backend field as well, because each of the first three steps names a field of
`AiReviewDetail`.

**Running with no review at all** is a supported configuration - path C above.

## Architecture

```
React SPA (Vite) ── Nginx ── Spring Boot API
                                  │
        ┌─────────────────────────┼──────────────────────────┐
        ▼                         ▼                          ▼
   MySQL 8 (truth)          Redis 7 (hot state)      Elasticsearch (search)
   posts/users/likes        Lua like toggle           CJK analyzer
   audit queue              sliding-window limiter    MySQL fallback
   review lease             hot-ranking ZSET
        ▲                         │
        │    write-behind flush   │
        └─────────────────────────┘
                    +
        AI Agent Pipeline (agent-llm pool)
        AiReviewEvent → LlmClient (OpenAI-compatible)
        → repair-parse → semantic validation
        → review log + auto-comment + score writeback
        AiSafetyCheckEvent → 4-class fail-closed moderation
        Reconciliation task → lease expiry / FAILED / pending-llm retry
```

**Resilience chain**（每条链路都实测过故障注入）:

- **Lease-based claims**: an AI review starts with an atomic conditional UPDATE - concurrent instances or re-published events cannot double-process a post; after 5 failed attempts the author is notified once and re-dispatch stops
- **Fail-closed moderation**: LLM unreachable → new posts enter the audit queue with a `pending-llm` marker; the reconciliation task re-checks them when the LLM recovers and restores Safe posts
- **Semantic validation + self-correction**: schema-valid garbage (placeholder fields from small local models) retries with a reinforced prompt; *unparseable* output (fences, trailing commas, max-token truncation) is repair-parsed, then retried with the model's own output plus the parser error - total LLM calls per review stay ≤ 2
- **Pool isolation**: LLM-bound listeners run on a dedicated small pool (core2/max4, Abort) so model latency can never starve message/notification work
- **Count drift reconciliation**: hourly rotating-cursor sweep unions Redis membership with the durable `vibe_post_like` table, repairs either side without deleting members, and rebuilds the hot-ranking ZSET when anything changed
- **Graceful saturation**: agent events rejected by a saturated pool degrade the post to a retryable state instead of failing the request (verified under a 50-concurrent load: 121k rejections, zero crashes)

## The evidence wall

Every number in this section was measured on this repository's own stack, and each report
names the command that produced it. The drill report ends with a list of what it cannot
prove - that habit is kept on purpose.

| Evidence | What it shows |
|---|---|
| [Async pool load test](docs/research/async-pool-loadtest.md) | 25,204 samples from the reproducible 2026-09-21 run: 13 errors (0.1%), p50/p90/p99 37/1,770/3,353 ms, 6,231 posts left FAILED(3), zero crashes; where the pipeline actually saturates |
| [Container GC analysis](docs/research/jvm-container-gc-analysis.md) | 768 MB container, 0 full GCs, and why `MaxRAMPercentage=75` is the right ceiling |
| [Deep pagination](docs/research/late-row-lookup-deep-pagination.md) | A delayed-join optimisation measured 40% *slower* and was rolled back - with the EXPLAIN output kept |
| [Observability drill](docs/research/observability-drill-2026-09.md) | Scripted failure injections against the real stack, including what the drill cannot prove |
| [Technical blog](docs/blog/llm-code-review-structured-output-injection-defense.md) | How the structured-output review survives injection attempts and malformed model output |

The decisions behind all of it: **[fifteen ADRs, 0001-0015](docs/adr/)** - fail-closed
moderation, lease-based claims, degraded-is-not-unhealthy, the recording discipline, the two
seams, metrics and rules.

Where to look, in the time you have:

- **30 seconds** - the live demo above: one real review of one real snippet, without registering.
- **10 minutes** - the three decisions the pipeline is built around:
  [fail-closed moderation](docs/adr/0004-safety-check-fail-closed.md),
  [lease-based claims](docs/adr/0005-lease-based-review-claim.md),
  [degraded is not unhealthy](docs/adr/0007-degraded-status-is-not-unhealthy.md).
- **90 minutes** - the table above, in the order it was produced.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.3.5, Java 18 |
| **ORM** | MyBatis-Plus 3.5.9 |
| **Frontend** | React 19 + Vite + TypeScript + Tailwind CSS |
| **State** | TanStack Query + Zustand |
| **Database** | H2 (dev) / MySQL 8 (prod) |
| **Cache** | Redis 7 (Lettuce) — atomic Lua like toggle, rate limiting, hot ranking |
| **Search** | Elasticsearch 7.17 (MySQL fallback) |
| **LLM** | OpenAI-compatible API / local Ollama |
| **Auth** | JWT (jjwt 0.12.6) + BCrypt |
| **Build** | Maven 3.9+ + Vite, multi-stage Docker, GitHub Actions CI |

## Configuration quick reference

The full list lives in [.env.example](.env.example), one comment per variable. These are the
ones a first run touches.

| Variable | Default | Purpose |
|----------|---------|---------|
| `WEB_PORT` | `8080` | the only port the stack publishes |
| `APP_TAG` | required | release identity stamped on both images; bump to release, revert to roll back |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | compose-internal | MySQL application connection (use `nexus_app`, not `root`) |
| `MYSQL_ROOT_PASSWORD` | required | MySQL root password, used only for initialization and maintenance |
| `JWT_SECRET` | empty | JWT signing secret (required in prod, fail-fast) |
| `BOOTSTRAP_ADMIN_PASSWORD` | empty | Creates `admin` once, only while no `ADMIN` exists and seeding is off |
| `DEMO_SEED_ENABLED` / `DEMO_PASSWORD` | `false` | Opt-in demo accounts and sample content for a throwaway instance |
| `CORS_ALLOWED_ORIGINS` | localhost + the live domain | Allowed browser origins - a pair with the tunnel address, always changed together |
| `LLM_ENDPOINT` / `LLM_MODEL` / `LLM_API_KEY` | compose-local Ollama, `qwen2.5:7b` | Any OpenAI-compatible endpoint; the key is only for hosted APIs |
| `AI_REVIEW_ENABLED` / `AI_SAFETY_ENABLED` | `true` / `true` | Independent switches; both off is path C |
| `AI_LEASE_SECONDS` / `AI_MAX_ATTEMPTS` | `240` / `5` | Lease window and retry budget |
| `REDIS_ENABLED` | `true` | Off sends likes, rate limiting and ranking down the MySQL fallback path |
| `LIKE_DRIFT_ENABLED` / `LIKE_SAMPLE_SIZE` | `true` / `200` | Drift reconciliation switch and per-cycle sample size |
| `DEMO_ENDPOINTS_ENABLED` | `false` | `/api/demo/*` seed endpoints; keep off outside throwaway instances |

## Observability

Three layers - logs, metrics, alerts - and none of them opens a public port.

| Layer | Where | What |
|----|------|------|
| Logs | `docker logs` + the `app-logs` volume at `/app/logs` | Prod writes structured JSON to disk (100 MB per file, 7 days kept, 1 GB total, async); one 16-hex `traceId` per request crosses thread pools and scheduled tasks, and the frontend shows its first 8 digits |
| Metrics | `GET /actuator/prometheus` (compose-internal only) | JVM / HTTP plus the AI link's own numbers: `llm_chat_completions_total{outcome}`, `llm_chat_completion_duration_seconds`, `llm_circuit_breaker_open`, `rate_limit_rejected_total{path}`, `ai_review_pending_posts`, `ai_review_reconcile_repairs_total{kind}`, and the agent pipeline's own `agent_pool_rejected_{review,safety}_total` / `agent_fail_closed_total` / `safety_pending_posts` ([ADR-0015](docs/adr/0015-the-agent-pipeline-gets-metrics-and-rules.md)) |
| Alerts | Grafana rules → `alert-bridge` → a Feishu bot | Nine rules: circuit breaker open, review backlog, 5xx ratio, rate-limit spike, missing target, error-budget burn, agent-pool rejections, safety backlog, undelivered alerts; webhook and signing secret live in `.env` only |
| Product funnel | same endpoint | `user_registered_total`, `post_submitted_total{status}`, `post_audited_total{action}`, `comment_submitted_total`, plus two daily gauges, `funnel_activation_ratio` and `funnel_active_content_d7_ratio` |

Health is two-layered. The public `/actuator/health` answers through nginx's `servable`
group and only reports what a visitor can still use - only the storage layer can mark it
DOWN. The container's own healthcheck asks the aggregate state: Redis, Elasticsearch or the
LLM dropping out is `DEGRADED` with HTTP 200, never a kill. `/actuator/health/deps`
explains, also internal; nginx answers 404 to everything else under `/actuator/*`. Why it
splits this way is [ADR-0007](docs/adr/0007-degraded-status-is-not-unhealthy.md).

The monitoring stack rides a profile, so a plain `docker compose up` never starts it and the
public surface stays nginx:80:

```bash
docker compose --profile monitoring up -d
```

With `FEISHU_ALERT_WEBHOOK` empty, `alert-bridge` refuses to start and names the variable -
a monitoring profile with no recipient is easier to be fooled by than no monitoring at all.
The six-step Feishu walkthrough, the error-code table and the contact-point check live in
[docs/runbook/feishu-alerting.md](docs/runbook/feishu-alerting.md); dashboards and alert
rules are versioned under `docker/observability/grafana/provisioning/`, so rebuilding a
machine cannot lose the alerting.

The drill proves scraping, metric names, rule expressions and the alert path - it does not
look at the panels. Two scripts cover those: `check_panels.py` replays every panel expression
through the datasource proxy (telling a broken expression from a series nobody emitted), and
`render_panels.py` opens the dashboards in a headless browser and counts painted canvases.
The second exists because `Latency p50/p95/p99` had never drawn since it was committed: Boot
sends no `_bucket` series without `percentiles-histogram`, and `histogram_quantile()` over a
missing series returns empty. The conclusion is section nine of
[the drill report](docs/research/observability-drill-2026-09.md); the 23 steps are
reproduced by `benchmark/observability/drill.ps1` against an isolated compose project.

The two funnel ratios are snapshots, not scrape-time queries - same reason as
`ai_review_pending_posts`. They recompute daily at 03:17 plus once at boot, so the panel is
at most a day behind, and the active definition is deliberately *content behaviour*: a
returning visitor who only browses is not measurable. That trade-off is written down rather
than hidden.

## Testing gates

Measured 2026-10-11: backend **417 tests / 70 classes**, 0 failures, 0 errors, 0 skipped,
counted from one `mvn -B test` summary line - never `target/surefire-reports/`, which
accumulates runs and lies. Frontend **42 tests / 11 files**: the axios interceptor (401
refresh, single-flight, replay, 5xx trace id), the login page, the AI review panel and its
replay, the comment body, the showcase entry, the step strip, the nav evidence links,
reduced motion, the score-zero meter and the call-site scan. The alert bridge carries
**28** Python tests.

Coverage is a gate, not a report: `pom.xml` pins `jacoco.line.minimum` to 0.70 for the whole
bundle (first measured 74.27%), and `mvn test` fails below it. When you squeeze surefire's
`-DargLine` to fit a smaller heap, pass `-Djacoco.skip=true` with it - `-DargLine=` replaces
the JaCoCo agent arguments instead of appending, coverage silently measures 0.00, and the
floor fails the build on a number that was never real.

Beyond unit and H2 integration tests, four contract checks read the source: no entity in a
controller signature, no MyBatis type in a repository interface, frontend `types/` fields
matching the DTOs' Jackson types, and no test pairing `isOk()` with a non-200 code; a fifth
scan requires every frontend `apiClient.` call to sit inside a `try`/`catch` or react-query.

```bash
mvn test                      # 417: unit + H2 integration + the source-scanning contract checks + the JaCoCo floor
cd frontend && npm run build  # tsc strict, zero @ts-ignore
cd frontend && npm run lint   # oxlint
cd frontend && npm run test   # 42
cd docker/observability/alert-bridge && python -m unittest -v test_alert_bridge   # 28
```

CI (`.github/workflows/maven.yml`) runs all of the above plus the `app` and `web` image
builds.

## API examples

```bash
# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# Create post (authenticated, code block → triggers AI review)
curl -X POST http://localhost:8081/api/v1/posts \
  -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" \
  -d '{"title":"My Vibe Coding Setup","content":"Using Cursor + Claude...","categoryId":2}'

# Latest structured AI review for a post (public)
curl http://localhost:8081/api/v1/agent-logs/post/100/latest

# User profile stats + recent activity (public)
curl http://localhost:8081/api/v1/users/2/summary
```

**响应契约**：HTTP 状态码是唯一真相源，信封里的 `code` 由它派生（`{ code, message, data }`，5xx 多带一个
`traceId`），所以"状态 200 但 body 说 401"这类事在类型上证不了假。失败一律走
`BusinessException(HttpStatus, safeMessage)`，controller 不再自己拼错误对象；`GlobalExceptionHandler`
是唯一决定状态码的地方。本轮**没有** `ErrorCode` 枚举：`HttpStatus` 吸收了那 27 个魔法数字，而评估文档里
"前端按 message 分支"的动机查证为假（前端 13 处 `message` 全是展示兜底，`code` 一处都不读）。

## Repository structure

```
nexus-vibe/
frontend/                        # React 19 SPA (Vite + TypeScript + Tailwind)
  src/components/                 # AiReviewTerminal, ui/ReviewStepStrip, Navbar, ...
  src/components/ui/reviewSteps.ts  # the beats the terminal is allowed to name
  src/pages/ src/api/ src/stores/ src/types/ src/test/
src/main/java/com/nexus/campus/  # the package kept the pre-rename domain word (ADR-0001)
  agent/                          # LLM client, repair-parse, review + safety listeners, ReviewPolicy
  task/                           # reconciliation, drift, like-sync, funnels
  filter/ config/                 # XSS wrapper, trace id, dual pools, security, health indicators
  controller/ service/ repository/ entity/ util/
docker/
  mysql/                          # init.sql + one-shot migrations and rollbacks + the 100k-row harness
  observability/                  # Prometheus + Grafana provisioning + the Feishu alert bridge
benchmark/                        # jmeter / concurrency / migrations / observability / showcase
scripts/                          # backup, tunnel, rate-limit probe
docs/                             # ADRs, research, tickets, plans, runbooks, blog
docker-compose.yml                # the only compose file at the root; variants live under benchmark/
CONTEXT.md  CHANGELOG.md  AGENTS.md
```

## Contributing

Fork, branch, keep the gates green, open a pull request - in this repository, on a
`codex/<slug>` branch rather than `master`. Read [AGENTS.md](AGENTS.md) for the working
contract and [CONTEXT.md](CONTEXT.md) for the vocabulary; [docs/INDEX.md](docs/INDEX.md) maps
every document, and a document that is not in it is a document nobody will find. ADRs are
append-only and numbered sequentially: a changed mind means a new ADR, never an edit. One
rule outlives the others - a claim without a reproducible path does not go in.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
