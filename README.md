# Nexus-Vibe

**AI-Powered Vibe Coding & Developer Community**

Nexus-Vibe is a full-stack AI developer community platform — a modern replacement for the traditional campus forum. Built with Spring Boot 3.3 + React, it runs as a Multi-Agent assistant platform: async AI code review, LLM-based content safety checks, explainable review panels on every reviewed post, and per-user activity workspaces, all wrapped in an IDE-station dark UI.

技术博客：[给论坛接入 LLM 代码评审：结构化输出与注入防御实战](docs/blog/llm-code-review-structured-output-injection-defense.md)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Spring Boot 3.3.5, Java 18 |
| **ORM** | MyBatis-Plus 3.5.9 |
| **Frontend** | React 19 + Vite + TypeScript + Tailwind CSS |
| **State** | TanStack Query + Zustand |
| **Animation** | Motion (Framer Motion successor) |
| **Font** | Inter + JetBrains Mono |
| **Database** | H2 (dev) / MySQL 8 (prod) |
| **Cache** | Redis (Lettuce) |
| **Search** | Elasticsearch 7.17 REST API (MySQL fallback) |
| **Auth** | JWT (jjwt 0.12.6) |
| **Security** | XSS Filter + DFA Sensitive Word Filter + LLM semantic check |
| **Build** | Maven 3.9+ (backend) + Vite (frontend) |

## Architecture

### Backend (Spring Boot)

```
Controller (REST API) → Service → Mapper (MyBatis-Plus) → DB
                              ↓
                        Redis Cache
                              ↓
                     Elasticsearch (full-text)
                              ↓
              AI Agent (async) — Code Review + Safety Check
```

- **REST API**: `/api/v1/*` endpoints for all CRUD + auth
- **AI Agent Pipeline**: `AiReviewEvent` → `LlmClient` (OpenAI-compatible) → structured review log → auto-comment + explainable terminal
- **LLM Safety Check**: DFA pass-through → async LLM classification (4 categories)
- **Caching**: Redis-backed like toggle, gravity-decay hot ranking, sliding window rate limiting
- **AI Review Explainability**: `GET /api/v1/agent-logs/post/{postId}/latest` returns the latest structured review (score, severity, verdict, quality, security, suggestions)
- **User Profile Workspace**: `GET /api/v1/users/{id}/summary` aggregates post/comment/like/fork/version stats and a recent activity timeline

### Frontend (React SPA)

```
frontend/
├── src/
│   ├── components/        # UI components (Navbar, Sidebar, PostCard, Avatar...)
│   │   ├── ui/            # Animated components (SpotlightCard, BorderBeam, ShimmerButton...)
│   │   └── layout/        # MainLayout, AdminLayout
│   ├── pages/             # 12+ page components
│   ├── api/               # Axios client + TanStack Query hooks
│   ├── stores/            # Zustand stores (auth, theme)
│   └── types/             # TypeScript interfaces
```

**Design**: 2-column IDE workstation layout — sidebar console + main workspace. Dark cyberpunk theme (`vibe-*` color palette), macOS terminal card patterns, motion animations throughout.

## Features

### Core
- [x] User registration & login (JWT auth)
- [x] Post CRUD with Markdown editor + live preview
- [x] Channel-based browsing with slug routing
- [x] Full-text search (ES + MySQL fallback)
- [x] Comments with thread-style layout
- [x] Like/unlike with Redis atomic toggle
- [x] Prompt template Fork with source attribution
- [x] Prompt template version history, change notes, and rollback
- [x] User profile workspace with stats grid, recent activity timeline, and published posts

### AI
- [x] **AI Code Review Agent**: Asynchronous LLM-powered post review with structured output (score, quality, security, suggestions)
- [x] **Structured Outputs**: JSON Schema-enforced review format via OpenAI API
- [x] **Prompt Injection Guardrails**: Delimiter-based isolation, Chain of Thought analysis
- [x] **LLM Safety Check**: 4-class classification (Prompt injection / Harmful / Spam / Safe) with per-class handling
- [x] **Agent run logs dashboard**: severity stats, filters, and review history
- [x] **AI Review Explainability**: structured review terminal (score, severity, verdict, findings) on post detail pages
- [x] **Prompt Playground**: variable substitution + token estimate

### Design
- [x] Dark cyberpunk theme with `vibe` color palette
- [x] 2-column IDE layout (sidebar + workspace)
- [x] macOS terminal card patterns
- [x] Motion animations (page transitions, stagger lists, hover effects)
- [x] Animated components: SpotlightCard, BorderBeam, DecryptedText, ShimmerButton
- [x] Dark mode toggle with localStorage persistence
- [x] Circular initial avatars with hash colors

### Infrastructure
- [x] DFA sensitive word filtering (two-tier: sensitive + critical)
- [x] Sliding window rate limiting (Redis + Lua)
- [x] Gravity-decay hot ranking (hourly recalculation)
- [x] Write-behind like counter sync (every 5 min)
- [x] Admin audit dashboard

## Quick Start

### Prerequisites

- JDK 18+
- Maven 3.9+
- Node.js 18+
- Redis (optional, can be disabled)

### Run in Development Mode

```bash
# Clone
git clone https://github.com/shing26/nexus-vibe.git
cd nexus-vibe

# Backend (H2 in-memory DB, auto-creates schema + seed data)
mvn clean package -DskipTests
mvn spring-boot:run
# → http://localhost:8081

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
# → http://localhost:5173 (auto-proxies /api to :8081)
```

### Default Accounts

> 仅开发模式（H2 seed data）可用。生产环境 demo 账号默认关闭，详见下方安全说明。

| Username | Password | Role |
|----------|----------|------|
| `admin` | `123456` | ADMIN |
| `shing` | `123456` | USER |
| `alice` | `123456` | USER |
| `bob` | `123456` | USER |

### Run with MySQL (Production)

```bash
# local MySQL + Redis, env-driven config
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://localhost:3306/nexus_campus?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="root"
$env:REDIS_HOST="localhost"
mvn spring-boot:run
```

> The MySQL schema and demo data live in `docker/mysql/init.sql`. The legacy
> `mysql` profile is still available for local MySQL without env overrides.

### Run with Docker Compose (recommended for handoff)

```bash
cp .env.example .env
# fill in DB_PASSWORD / JWT_SECRET
docker compose up --build
```

Then open `http://localhost:8080`. The stack starts:

| Service | Container | Port |
|---------|-----------|------|
| Nginx + React SPA | `nexus-web` | `${WEB_PORT:-8080}` |
| Spring Boot API | `nexus-app` | internal 8080 |
| MySQL 8 | `nexus-db` | internal only |
| Redis 7 | `nexus-redis` | internal only |
| Elasticsearch (optional) | `nexus-es` | internal only |
| Ollama | `nexus-ollama` | internal 11434 |

Ollama 模型需要手动拉取一次：

```bash
docker compose exec ollama ollama pull qwen2.5:7b
```

公开部署建议保持 `WEB_PORT=8080`，由 cloudflared 隧道反代；本机不需要公网 IP 或开放入站端口。若改用独立 VPS 并希望直接访问 80 端口，再把 `WEB_PORT=80` 写入服务器 `.env`。

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `WEB_PORT` | `8080` | 对外暴露的 web 端口 |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile |
| `SERVER_PORT` | `8080` | API port |
| `DB_URL` | local MySQL | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / empty | MySQL credentials (password required) |
| `REDIS_ENABLED` | `true` | Redis features |
| `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` | Redis connection |
| `LLM_API_KEY` | empty | OpenAI-compatible API key (only needed for hosted APIs) |
| `LLM_ENDPOINT` | `http://ollama:11434/v1` | Chat completions base URL (compose-internal Ollama) |
| `LLM_MODEL` | `qwen2.5:7b` | Default model（Windows CPU 建议 `qwen2.5:3b`） |
| `JWT_SECRET` | empty | JWT signing secret (required in prod) |
| `JWT_EXPIRATION` | `86400000` | Access token TTL (ms) |
| `DEMO_SEED_ENABLED` | `false` | Seed demo accounts with `DEMO_PASSWORD` |
| `DEMO_PASSWORD` | empty | Password for demo accounts when seeding is enabled |
| `CORS_ALLOWED_ORIGINS` | online domain | Allowed browser origins; include `http://localhost:8080` for local full-stack verification |
| `UPLOAD_DIR` | `/app/uploads` | Upload storage path |

### Local LLM (Ollama)

The dev profile targets a local Ollama instance by default, so AI Review and
Safety agents work out of the box once Ollama is running:

```bash
ollama pull qwen2.5:7b   # or qwen2.5:3b for a faster, lighter model
ollama serve
```

No API key is needed for local Ollama. To use a hosted OpenAI-compatible API
instead (for example in production), set `LLM_ENDPOINT`, `LLM_MODEL`, and
`LLM_API_KEY` in `.env`. If the endpoint is unreachable, agents log a warning
and skip LLM calls; the rest of the platform still works normally.

### Public Deployment (Docker Compose + Cloudflare Tunnel)

不依赖 Oracle/VPS。需要一台常开的 Windows 机器（Docker Desktop + cloudflared），
本机无需公网 IP，也无需在路由器/防火墙开放端口。

1. 启动 Docker Desktop，在项目根目录写入 `.env`（参考 `.env.example`）：强随机
   `DB_PASSWORD`、`JWT_SECRET`；如需公开 demo 登录，再设置
   `DEMO_SEED_ENABLED=true` 与 `DEMO_PASSWORD`。
2. `docker compose up -d --build`，然后
   `docker compose exec ollama ollama pull qwen2.5:3b`（CPU 机器更稳；算力足够可换
   `qwen2.5:7b`）。
3. 验证 `http://localhost:8080` 与 `GET /actuator/health`；MySQL/Redis/ES 不应监听宿主端口。
4. `cloudflared tunnel login`（免费账号），再 `cloudflared tunnel create nexus-vibe` 记录
   `TUNNEL_ID`。
5. DNS 路由：
   - 若 `nexus-vibe.shing26.is-a.dev` 的 DNS 已在用户自己的 Cloudflare zone，运行
     `cloudflared tunnel route dns nexus-vibe nexus-vibe.shing26.is-a.dev`；
   - 否则 Fork `is-a-dev/register`，为子域添加 CNAME：
     `nexus-vibe -> <TUNNEL_ID>.cfargotunnel.com`，PR 合并后生效。
6. 编写 `%USERPROFILE%\.cloudflared\config.yml`：ingress 指向 `http://localhost:8080`，
   兜底 `service: http_status:404`；前台 `cloudflared tunnel run nexus-vibe` 验证，稳定后
   `cloudflared service install` 设为 Windows 服务。
7. HTTPS 生效后，把 GitHub 仓库 homepage 指向线上域名：
   `gh repo edit shing26/nexus-vibe --homepage "https://nexus-vibe.shing26.is-a.dev"`。

### Privacy & Security Notes

- All demo accounts above are local dev seed data only. In production,
  `DEMO_SEED_ENABLED=false`（默认）会为样例账号写入随机不可恢复密码；只有显式开启
  demo seeding 时才会使用 `DEMO_PASSWORD`。
- Never commit real credentials: `.env` is git-ignored, and `.env.example`
  only ships placeholders. Set `JWT_SECRET` and `DB_PASSWORD`
  via local environment variables or a local `.env` file.
- The JWT secret in `application.yml` is a dev fallback; production must
  override it through `JWT_SECRET`.
- 上传只接受 JPG/PNG/GIF/WebP 魔数文件，扩展名由服务端生成，不信任客户端
  `Content-Type` 与原始文件名。
- CORS、限流、actuator 与 springdoc 均按生产配置收敛，`/api/demo/**` 在 prod 不可达。
- This repository intentionally contains no personal data, API keys, or
  private tokens. If you fork or redeploy, keep it that way.

### Handoff QA Checklist

Run this before handing the project over:

- [ ] `mvn test` passes (188 tests, H2 in-memory)
- [ ] `cd frontend && npm run build` passes
- [ ] `cd frontend && npm run lint` passes
- [ ] Dev: login as `admin/123456` and `shing/123456`
- [ ] Create a prompt template, edit it, verify a new version appears
- [ ] Fork a template and verify the fork badge links back to the source
- [ ] Restore an older template version and verify content rolls back
- [ ] Post a regular post, comment, like, and search
- [ ] Open `/agent-logs` as admin and verify stats + filters
- [ ] Open a reviewed post and verify the explainable AI terminal (score, severity, verdict, findings)
- [ ] Open `/user/2` and verify the profile stats grid and recent activity timeline
- [ ] Open `/admin/audit` and approve/reject a pending post
- [ ] Upload a PNG and verify `/uploads/<uuid>.png` is reachable
- [ ] Upload `x.html` with `Content-Type: image/png` and verify it is rejected
- [ ] `docker compose up --build` and verify the full stack on `http://localhost:8080`

## Project Structure

```
nexus-vibe/
├── frontend/                   # React SPA (Vite + TypeScript + Tailwind)
│   ├── src/
│   │   ├── components/         # Shared UI components
│   │   │   └── ui/             # Animated micro-interaction components
│   │   ├── pages/              # Page components
│   │   ├── api/                # API client + hooks
│   │   ├── stores/             # Zustand stores
│   │   └── types/              # TypeScript types
│   ├── package.json
│   └── vite.config.ts
├── src/main/java/com/nexus/campus/
│   ├── agent/                  # AI Agent (LlmClient, Review, Safety)
│   ├── controller/             # REST controllers
│   ├── service/                # Business logic
│   ├── entity/                 # MyBatis-Plus entities
│   ├── mapper/                 # Data access
│   ├── dto/                    # Request/Response DTOs
│   ├── config/                 # Spring configs
│   ├── security/               # JWT auth filter
│   └── util/                   # DFA filter, JWT util
├── pom.xml
├── CONTEXT.md                  # Domain glossary
└── docs/
    ├── adr/                    # Architecture Decision Records
    ├── product/                # Product plans and prioritization
    ├── design/                 # UI visual system and UX architecture
    ├── research/               # Research documents
    ├── tickets/                # Implementation tickets
    └── ...
```

## API Examples

```bash
# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# Get channels
curl http://localhost:8081/api/v1/channels

# Create post (authenticated)
curl -X POST http://localhost:8081/api/v1/posts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"title":"My Vibe Coding Setup","content":"Using Cursor + Claude...","categoryId":2}'

# Latest structured AI review for a post (public)
curl http://localhost:8081/api/v1/agent-logs/post/100/latest

# User profile stats + recent activity (public)
curl http://localhost:8081/api/v1/users/2/summary
```

## License

MIT
