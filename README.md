# Nexus-Vibe

**AI-Powered Vibe Coding & Developer Community**

![CI](https://github.com/shing26/nexus-vibe/actions/workflows/maven.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-18-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)
![Tests](https://img.shields.io/badge/tests-344%20Java%20%2B%2029%20frontend-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

Nexus-Vibe is a full-stack AI developer community platform — a modern replacement for the traditional campus forum. Built with Spring Boot 3.3 + React 19, it runs an AI-governed content pipeline: async LLM code review with semantic validation, structured-output safety checks that fail closed, lease-based task claims that survive crashes, and per-user activity workspaces — all wrapped in an IDE-station dark UI.

## Live demo

**https://qualifier-discuss-marry.ngrok-free.dev**

No account needed to see what this project is actually about. The home page links one
already-reviewed post, and `/post/900000000000000001` renders the AI review the live pipeline
produced for it - score, severity, verdict, findings - to a signed-out visitor. The seed is
display-only: it is a row set written once, not a fixture the app switches to.

New here? [AGENTS.md](AGENTS.md) is the working contract and [docs/INDEX.md](docs/INDEX.md) maps
every document in the repository. Both exist because this project has been picked up by several
different agents, and "where does this go" kept being re-decided.

The whole stack runs on one Windows machine behind an ngrok tunnel: nginx -> Spring Boot ->
MySQL / Redis / Elasticsearch, with the LLM pointed at a hosted OpenAI-compatible endpoint.
Registering an account is enough to try it; publish a post containing a code block and an
AI review appears as a reply within about a minute.

Two things worth knowing before you click:

- ngrok's free tier shows a one-time "Visit Site" warning page the first time a browser
  sees the domain. It is not part of this project; one click dismisses it for seven days.
- It is a single home machine. If it is asleep the address stops answering, and every
  piece of evidence below was measured on the same class of machine rather than in a cloud
  region.

## Where to look, in the time you have

**30 seconds.** Open the live demo and click the showcase entry on the home page. You are
reading a real AI review of a real snippet, produced by the same pipeline that reviews
everything else, without registering. Then read [CONTEXT.md](CONTEXT.md) for the vocabulary.

**10 minutes.** The three decisions the AI pipeline is actually built around:

- [Fail-closed moderation](docs/adr/0004-safety-check-fail-closed.md) - when the LLM is
  unreachable, posts queue for review instead of publishing unreviewed.
- [Lease-based review claims](docs/adr/0005-lease-based-review-claim.md) - an atomic
  conditional UPDATE stops two workers from reviewing one post.
- [Degraded is not unhealthy](docs/adr/0007-degraded-status-is-not-unhealthy.md) - an
  unreachable dependency degrades the service; it does not take the site down.

**90 minutes.** The measured evidence, in the order it was produced:

| Evidence | What it shows |
|---|---|
| [Async pool load test](docs/research/async-pool-loadtest.md) | 201,880 requests, 121,202 backpressure rejections, zero crashes; where the pipeline actually saturates |
| [Container GC analysis](docs/research/jvm-container-gc-analysis.md) | 768 MB container, 0 full GCs, and why `MaxRAMPercentage=75` is the right ceiling |
| [Deep pagination](docs/research/late-row-lookup-deep-pagination.md) | A delayed-join optimisation measured 40% *slower* and was rolled back - with the EXPLAIN output kept |
| [Observability drill](docs/research/observability-drill-2026-09.md) | Scripted failure injections against the real stack, including what the drill cannot prove |
| [Technical blog](docs/blog/llm-code-review-structured-output-injection-defense.md) | How the structured-output review survives injection attempts and malformed model output |

技术博客：[给论坛接入 LLM 代码评审：结构化输出与注入防御实战](docs/blog/llm-code-review-structured-output-injection-defense.md)

**Engineering notes**（设计决策与实测证据）：

- [ADR-0004 · LLM 语义安全审查](docs/adr/0004-safety-check-fail-closed.md) — LLM 不可用时 fail-closed 入人工审核队列
- [ADR-0005 · 租约式评审领取](docs/adr/0005-lease-based-review-claim.md) — 原子条件 UPDATE 互斥多实例
- [研究 · AI 排序索引案例](docs/research/mysql-ai-sort-index-explain.md) — 10 万行 EXPLAIN：带过滤 3 倍提速、无过滤负优化
- [研究 · 异步池压测](docs/research/async-pool-loadtest.md) — 20 万请求饱和实测：12.1 万次背压拒绝，零崩溃
- [研究 · 容器 GC 分析](docs/research/jvm-container-gc-analysis.md) — 768m 容器压测：0 次 Full GC
- [研究 · 深分页负优化](docs/research/late-row-lookup-deep-pagination.md) — 延迟关联实测退化 40%，回滚决策入档

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

**Resilience chain**（每条链路都实测过故障注入）：

- **Lease-based claims**: an AI review starts with an atomic conditional UPDATE — concurrent instances or re-published events cannot double-process a post; after 5 failed attempts the author is notified once and re-dispatch stops
- **Fail-closed moderation**: LLM unreachable → new posts enter the audit queue with a `pending-llm` marker; the reconciliation task re-checks them when the LLM recovers and restores Safe posts
- **Semantic validation + self-correction**: schema-valid garbage (placeholder fields from small local models) retries with a reinforced prompt; *unparseable* output (fences, trailing commas, max-token truncation) is repair-parsed, then retried with the model's own output plus the parser error — total LLM calls per review stay ≤ 2
- **Pool isolation**: LLM-bound listeners run on a dedicated small pool (core2/max4, Abort) so model latency can never starve message/notification work
- **Count drift reconciliation**: hourly rotating-cursor sweep detects Redis-LOSS-shaped gaps and rebuilds from the durable `vibe_post_like` table, including the hot-ranking ZSET
- **Graceful saturation**: agent events rejected by a saturated pool degrade the post to a retryable state instead of failing the request (verified under a 50-concurrent load: 121k rejections, zero crashes)

## Features

### Core
- [x] User registration & login (JWT), unique email as the recovery anchor
- [x] Post CRUD with Markdown editor + live preview
- [x] Channel-based browsing with slug routing
- [x] Full-text search (ES + MySQL fallback)
- [x] Comments with thread-style layout
- [x] Like toggle — Lua-atomic in Redis, MySQL fallback with matching semantics
- [x] Prompt template fork with source attribution
- [x] Prompt template version history, change notes, and rollback
- [x] User profile workspace with stats grid, recent activity timeline, and published posts
- [x] Admin audit dashboard + assisted account recovery (temp-password flow)

### AI
- [x] **AI Code Review Agent**: async LLM review with structured output (score, quality, security, suggestions) and a visible scoring rubric
- [x] **JSON repair + self-correction**: fences/truncation/trailing-comma repair; unparseable output retried with the parser error
- [x] **Prompt Injection Guardrails**: delimiter-based isolation, data-not-instructions framing
- [x] **LLM Safety Check**: 4-class structured classification (safe / prompt_injection / harmful / spam), fail-closed on outage
- [x] **Re-review on edit**: content edits re-trigger the review; the stale AI comment is superseded so scores never contradict
- [x] **Agent run logs dashboard**: severity stats, filters, and full review history
- [x] **Prompt Playground**: variable substitution + token estimate

### Design
- [x] Dark cyberpunk theme with `vibe` color palette
- [x] 2-column IDE layout (sidebar + workspace), macOS terminal card patterns
- [x] Motion animations; SpotlightCard, BorderBeam, DecryptedText, ShimmerButton
- [x] Dark mode toggle with localStorage persistence

### Infrastructure
- [x] DFA sensitive word filtering (two-tier: sensitive + critical) with Redis pub/sub hot reload
- [x] Sliding window rate limiting (Redis + Lua), proxy-trust-aware client IP resolution
- [x] Gravity-decay hot ranking (hourly recalculation + drift-triggered rebuild)
- [x] Write-behind like counter sync (SPOP batches, requeue-on-failure) + drift reconciliation
- [x] Lease-based review claims with attempt budget and terminal notification
- [x] Dedicated agent-llm pool isolation; graceful pool-saturation degradation

## Quick Start

### Prerequisites

- JDK 18+, Maven 3.9+
- Node.js 18+
- Redis (optional — everything degrades gracefully without it)
- Ollama (optional — for local AI features)

### Run in Development Mode

```bash
git clone https://github.com/shing26/nexus-vibe.git
cd nexus-vibe

# Backend (H2 in-memory DB, auto-creates schema + seed data)
mvn spring-boot:run
# → http://localhost:8081

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
# → http://localhost:5173 (auto-proxies /api to :8081)
```

For the AI agents, point the backend at a local Ollama (default) or any
OpenAI-compatible API:

```bash
ollama pull qwen2.5:7b   # or qwen2.5:3b for a faster, lighter model
ollama serve
```

**Model quality bar**: the review agent validates output semantically —
schema-valid but empty/placeholder responses (common from local 7B/3B models)
are retried once, then degrade silently (logged `unknown`, no score, no AI
comment). Local 7B/3B models are fine for exercising the pipeline; for
reviews you can actually use, run a hosted model (e.g. `gpt-4o`) or a local
model of 14B+ in production.

### Default Accounts

> 仅开发模式（H2 seed data + `DEMO_SEED_ENABLED`）可用。生产默认不写任何样例账号或样例内容，详见下方安全说明。

| Username | Password | Role |
|----------|----------|------|
| `admin` | `123456` | ADMIN |
| `shing` | `123456` | USER |
| `alice` | `123456` | USER |

In a deployment with seeding off the only account is the one you bootstrap: set
`BOOTSTRAP_ADMIN_PASSWORD` before the first start and `admin` is created with it,
once, while the database has no `ADMIN`. Rotate the password after logging in.

### Run with Docker Compose (recommended)

```bash
cp .env.example .env
# fill in DB_PASSWORD / JWT_SECRET / BOOTSTRAP_ADMIN_PASSWORD
docker compose up --build
```

Then open `http://localhost:8080`. The stack starts:

| Service | Container | Port |
|---------|-----------|------|
| Nginx + React SPA | `nexus-web` | `${WEB_PORT:-8080}` |
| Spring Boot API | `nexus-app` | internal 8080 (768m mem limit, G1, GC logs) |
| MySQL 8 | `nexus-db` | internal only |
| Redis 7 | `nexus-redis` | internal only |
| Elasticsearch (optional) | `nexus-es` | internal only |
| Ollama | `nexus-ollama` | internal 11434 |

Ollama 模型需要手动拉取一次：

```bash
docker compose exec ollama ollama pull qwen2.5:7b
```

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `WEB_PORT` | `8080` | 对外暴露的 web 端口 |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | compose-internal | MySQL connection (password required) |
| `REDIS_ENABLED` | `true` | Redis features（关闭则点赞/限流/热榜走 MySQL 降级路径） |
| `LLM_ENDPOINT` | `http://ollama:11434/v1` | Chat completions base URL |
| `LLM_MODEL` | `qwen2.5:7b` | Default model（Windows CPU 建议 `qwen2.5:3b`） |
| `LLM_API_KEY` | empty | 仅托管 API 需要 |
| `JWT_SECRET` | empty | JWT signing secret (required in prod, fail-fast) |
| `DEMO_SEED_ENABLED` | `false` | Seed demo accounts *and* sample content from `DEMO_PASSWORD` |
| `BOOTSTRAP_ADMIN_PASSWORD` | empty | Creates `admin` once, only while no `ADMIN` exists and seeding is off |
| `CORS_ALLOWED_ORIGINS` | online domain | Allowed browser origins |
| `AI_REVIEW_ENABLED` / `AI_LEASE_SECONDS` / `AI_MAX_ATTEMPTS` | `true` / `30` / `5` | Agent pipeline tuning |
| `LIKE_DRIFT_ENABLED` / `LIKE_DRIFT_RATIO` / `LIKE_DRIFT_ABS` | `true` / `0.5` / `100` | Drift repair thresholds |
| `DEMO_ENDPOINTS_ENABLED` | `false` | `/api/demo/*` showcase endpoints (keep off in prod) |
| `APP_TAG` | required | 两个应用镜像的标签；发布=改标签后 `--build`，回滚=改回上一个标签再 `up -d` |

完整列表见 [.env.example](.env.example)。

### Public Deployment (Docker Compose + Cloudflare Tunnel)

不依赖公网 VPS：一台常开的机器（Docker Desktop + cloudflared）即可，无需
公网 IP 或入站端口。

1. 根目录写入 `.env`（参考 `.env.example`）：强随机 `DB_PASSWORD`、`JWT_SECRET`，以及一个
   每次发布都变的 `APP_TAG`（`app`/`web` 两个镜像靠它才有可回滚的名字）。
2. `docker compose up -d --build`，拉取模型 `docker compose exec ollama ollama pull qwen2.5:3b`。
3. `cloudflared tunnel login` → `cloudflared tunnel create nexus-vibe` → DNS 路由
   （自有 zone 用 `cloudflared tunnel route dns`，否则 Fork `is-a-dev/register` 加
   CNAME `nexus-vibe -> <TUNNEL_ID>.cfargotunnel.com`）。
4. `cloudflared service install` 注册为 Windows 服务；HTTPS 生效后把仓库
   homepage 指向线上域名。

### Observability

三层：日志、指标、告警。都不新开公网端口。

| 层 | 位置 | 内容 |
|----|------|------|
| 日志 | `docker logs` + 卷 `app-logs` 下的 `/app/logs` | prod 额外把 JSON 落盘（单件 100MB / 保留 7 天 / 总量 1GB，异步写入），重建容器不丢历史；每行带 `traceId` |
| 指标 | `GET /actuator/prometheus`（仅 compose 内网） | JVM / HTTP 之外是 AI 链路自己的数：`llm_chat_completions_total{outcome}`、`llm_chat_completion_duration_seconds`、`llm_circuit_breaker_open`、`rate_limit_rejected_total{path}`、`ai_review_pending_posts`、`ai_review_reconcile_repairs_total{kind}` |
| 产品漏斗 | 同上 | 机器之外还有"东西有没有人用"：`user_registered_total`、`post_submitted_total{status}`、`post_audited_total{action}`、`comment_submitted_total`，加两个由 MySQL 每日聚合的 gauge `funnel_activation_ratio`（注册后 7 日内发出首帖的占比）与 `funnel_active_content_d7_ratio`（近 7 日有内容行为的注册用户占比）；面板 `nexus-product-loop` |
| 告警 | Grafana 规则 → `alert-bridge` → 飞书自定义机器人 | 6 条：熔断打开、评审积压、5xx 比率、限流突增、抓不到 target、99.9% 错误预算快烧；webhook 与加签密钥只进 `.env` |

监控栈挂在 profile 上，默认 `docker compose up` 不启动它，公网面仍然只有 nginx:80：

```bash
docker compose --profile monitoring up -d
```

Prometheus 与 Grafana 都不映射宿主端口，演练时用 `docker compose exec` 访问；dashboard 与告警规则都在
`docker/observability/grafana/provisioning/` 里版本化，重建机器不会把告警丢掉。

健康检查分两层：公网的 `/actuator/health` 被 nginx 指到 `servable` 组，只回答"用户还能不能用"
（只有存储层能把它判 DOWN）；容器自己的 healthcheck 问的是聚合状态——Redis / Elasticsearch / LLM
掉线是 `DEGRADED` + HTTP 200，容器不会被杀；解释在 `/actuator/health/deps`（同样仅内网，nginx
对其余 `/actuator/*` 一律 404）。为什么这样切记录在
[ADR-0007](docs/adr/0007-degraded-status-is-not-unhealthy.md)。

`FEISHU_ALERT_WEBHOOK` 为空时 `alert-bridge` 直接拒绝启动并指名这个变量——`--profile monitoring`
开着却没有任何收件人，比不装监控更容易骗到人。

每个请求一个 16 位十六进制追踪号：日志字段 `traceId`、响应头 `X-Trace-Id`、5xx 响应体里的 `traceId`，
跨线程池和定时任务都跟着走；前端错误提示显示前 8 位，用户报障时只需要给这个数。

产品漏斗的两个 ratio 是**快照**而不是抓取时查询（和 `ai_review_pending_posts` 同一个理由：Prometheus 每
15 秒抓一次，用 `COUNT` 回应抓取等于让监控栈去压数据库），默认每天 03:17 重算一次并在启动时先跑一次，
所以面板上的数最长滞后一天。窗口与 cron 在 `campus.metrics.funnel.*`（`FUNNEL_METRICS_CRON` /
`FUNNEL_METRICS_WINDOW_DAYS`）。活跃口径是**内容行为**：只浏览不发帖的回访测不到，这是写明的取舍。

`benchmark/observability/drill.ps1` 会把上面这套真跑一遍故障（独立 compose project 与独立卷，不会碰正在跑的栈），
结论见 [docs/research/observability-drill-2026-09.md](docs/research/observability-drill-2026-09.md)。

演练证的是抓取、指标名、规则表达式与告警链路，它不看面板；面板由另两个脚本负责：
`benchmark/observability/check_panels.py` 把每个面板的表达式过一遍 datasource 代理（区分"表达式写坏"和
"没人发布这条序列"），`render_panels.py` 用无头浏览器真的把两张 dashboard 打开、数被画出来的 canvas。
它们需要 Grafana 发布到宿主 loopback，所以有独立的 `docker-compose.render.yml`，默认不启用。
之所以补这一步：`Latency p50/p95/p99` 那张图从提交起就没画出来过——Boot 不打开
`percentiles-histogram` 就不发 `_bucket` 序列，而 `histogram_quantile()` 对不存在的序列返回空。
结论在同一份报告的第九节。

### Release, Rollback, Backup

| 事项 | 做法 | 状态 |
|------|------|------|
| 发布 | 改 `.env` 的 `APP_TAG` → `docker compose up -d --build` | 演练里真起过两个标签的镜像，A→B→A 两侧 `/api/v1/posts` 都 200（多阶段构建本身由 CI 的 image job 代证，本机演练用的是宿主机产物+同一套运行时层）；没证的是"这套流程在真实公网入口上跑过一次完整发布" |
| 回滚 | `APP_TAG` 改回上一个值 → `docker compose up -d --no-build` | A→B→A 已在演练里跑通：比对两个 tag 背后的 image id，换完读容器自身的 `Config.Image`，`/api/v1/posts` 每侧间隔 12 秒连探两次。2026-09-16 演练量出一条边界——Tomcat 在 `CommandLineRunner` 之前就开始监听，旧构建的样例账号又会在启动器里撞上本轮的 bootstrap `admin`，于是"探活 200、下一个请求连接被拒"；回滚只在数据兼容窗口内成立，而"滚回去救一个坏版本"仍然没有样本 |
| 备份 | `pwsh scripts/backup.ps1 -Destination <第二块盘>`（周计划任务）：mysqldump + uploads 打包，逐件校验明文 SHA-256、gzip 与 dump 结束标记，留 `-Keep` 份 | 2026-09-14 对运行中的栈真跑过，产出可恢复的备份集；**但该"第二卷"与数据库在同一块物理盘上**（本机单 NVMe 分区为 C/D/E），脚本现在会打印并记录 `same-physical-disk` 告警，异地/异盘副本仍未做 |
| 恢复 | [docs/runbook/restore.md](docs/runbook/restore.md)，另起 `-p nexus-restore-test` 项目（配 `docs/runbook/docker-compose.restore-test.yml` 改名容器），不碰生产卷 | **已演练**（2026-09-14）：dump 校验和与 manifest 一致、导入退出码 0、10 张表行数逐项相等（32 帖 / 10 用户 / 957 评审日志）、中文标题无乱码、恢复出的应用以 `200` 提供还原后的上传文件且哈希一致。仍未证的是异盘副本；`es-data` 不在任何 dump 里，恢复后必须跑 runbook 第 7 节的全量重建索引（`POST /api/v1/admin/search/reindex`），否则搜索静默返回空 |

### Privacy & Security Notes

- Demo 账号与样例内容同属演示数据：`DEMO_SEED_ENABLED=false`（生产默认）时两者都不写入，库里只有功能性的 `AiAgent(999)` 与你引导出的 `admin`。历史做法是写入随机不可恢复密码的幽灵账号，见 ADR-0008。
- Never commit real credentials: `.env` is git-ignored; `.env.example` ships placeholders only.
- 生产 `/api/demo/**` 默认不可达（`DEMO_ENDPOINTS_ENABLED=false`）。
- 上传只接受 JPG/PNG/GIF/WebP 魔数，扩展名由服务端生成。
- 转发头（X-Real-IP / X-Forwarded-For）仅在有可信反代时被信任（`TRUST_FORWARDED_HEADERS`，prod 默认 true 且 nginx 负责设置 X-Real-IP）。
- This repository intentionally contains no personal data, API keys, or private tokens.

## Project Structure

```
nexus-vibe/
├── frontend/                   # React SPA (Vite + TypeScript + Tailwind)
├── src/main/java/com/nexus/campus/
│   ├── agent/                  # AI pipeline: LlmClient, JsonRepairUtil, Review, Safety
│   ├── task/                   # LikeSync, DriftReconcile, AiReviewReconcile
│   ├── controller/ service/    # REST + business logic
│   ├── config/                 # Dual async pools, Redis, security
│   └── security/ util/         # JWT filter, DFA filter
├── docker/mysql/
│   ├── init.sql                # Production schema + reference data (channels, tags)
│   ├── migrate-*.sql           # One-shot migrations for existing volumes
│   └── benchmark/              # 100k-row EXPLAIN/loadtest harness
├── docker/observability/       # Prometheus + Grafana provisioning + the Feishu alert bridge
├── benchmark/jmeter/           # Async-pool load test scenario
├── docs/
│   ├── adr/                    # Architecture Decision Records
│   ├── research/               # Measured case studies (EXPLAIN, load test, GC)
│   ├── blog/                   # Technical write-ups
│   └── archive/                # Legacy QA reports
├── CONTEXT.md                  # Domain glossary
└── CHANGELOG.md
```

## API Examples

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

## Testing

2026-09-16 实测：后端 **331 用例 / 53 个测试类**（`mvn test` 的汇总行，不是把 `surefire-reports/*.txt` 加起来——那个目录里留着之前筛选跑剩的报告），前端 **25 用例 / 6 个文件**，告警桥 **17 条** Python 单测。

后端除了 H2 集成与 Mockito 单测，还有三条"读源码"的契约扫描：controller 签名不许出现 entity、测试不许把 `isOk()` 和非 200 的 `code` 配成一对、每个 `apiClient.` 调用都要落在有 `catch` 的 `try` 或 react-query 里。前端拦截器那 7 条走真实 axios，只把 `adapter` 换成假的，所以 401 刷新、单飞、重放、5xx 追踪号都是真跑；并且用两次变异验证过它们不是摆设：把 `if (!refreshPromise)` 改成 `if (true)` 只红那一条并发刷新的用例，塞一个裸 `apiClient.get` 会让扫描报出文件名与行号。

```bash
mvn test                      # 331 tests: unit + H2 integration + the three source-scanning contract checks
cd frontend && npm run build  # tsc strict, zero @ts-ignore
cd frontend && npm run lint   # oxlint
cd frontend && npm run test   # 25 tests: axios interceptor, login page, AI review panel, call-site scan
cd docker/observability/alert-bridge && python -m unittest -v test_alert_bridge   # 17 tests: Feishu sign + body
```

```bash
pwsh -File benchmark/observability/drill.ps1      # 23 步故障演练，另起 compose project，~15-25 分钟，需 Docker
python benchmark/observability/check_panels.py    # 每个面板表达式查一遍：error / empty / 有序列
python benchmark/observability/render_panels.py   # 无头浏览器真的渲染三张 dashboard，需先起 render 栈
```

CI（`.github/workflows/maven.yml`）只跑 `mvn test`：告警桥的 Python 单测与演练脚本都在本地跑，
演练结论见 [docs/research/observability-drill-2026-09.md](docs/research/observability-drill-2026-09.md)。

## Running the live instance

The public demo is this repository's `docker-compose.yml` plus an ngrok tunnel. Nothing about
the deployed shape is special-cased: `SPRING_PROFILES_ACTIVE=prod`, MySQL/Redis/Elasticsearch
unpublished, and only nginx reachable from outside.

```bash
# 1. the stack (needs .env: DB_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD, APP_TAG, CORS_ALLOWED_ORIGINS)
docker compose up -d

# 2. the tunnel; scripts/tunnel-ngrok.ps1 refuses to start a second copy for the same domain
pwsh scripts/tunnel-ngrok.ps1
```

**The address and CORS are a pair.** The backend only answers requests whose `Origin` is on
`campus.cors.allowed-origins`, so a new tunnel domain must be added to
`CORS_ALLOWED_ORIGINS` in `.env` and the app restarted, or the SPA will load and every API
call behind it will return 403. That failure mode is silent in the browser console and looks
like a broken site, which is why it is written down here rather than discovered later.

Two hand-offs that are not automated yet:

- **Tunnel at logon.** `scripts/tunnel-ngrok.ps1` runs in the foreground. Making it survive a
  reboot needs a scheduled task, which requires an elevated shell:
  `schtasks /create /tn Nexus-Vibe-Tunnel /sc onlogon /rl highest /tr "ngrok http 8080 --url qualifier-discuss-marry.ngrok-free.dev"`.
- **Log-volume ownership.** A host that already ran this stack before uid 10001 has
  root-owned named volumes. The one-time `chown` is in
  [docs/plans/pre-deployment-checklist.md](docs/plans/pre-deployment-checklist.md); the image
  falls back to `/tmp` logging and prints a warning if it is skipped.

The database behind the live demo is the long-lived development database, so it contains the
earlier load-test posts and a handful of demo accounts. Clearing it would delete that corpus;
the decision is recorded in the same checklist rather than made implicitly by a script.

## License

MIT
