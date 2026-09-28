# 环境矩阵：dev / prod / CI 三列下的关键配置

> 产出：短板补齐轮 B10。动机是 `CampusAiProperties`（ADR-0013）把散落的 `@Value` 收拢成一个绑定对象之后，
> "某个值在这套环境里到底是多少"这个问题仍然要从 `application.yml`、`application-prod.yml`、
> `.env.example` 和 `.github/workflows/maven.yml` 四处拼出来。这张表把拼图放在一页里。
>
> 读法：**默认值**是该文件里写死的值；**覆盖者**是让它变成别的值的那个环境变量或命令行参数。
> 没有覆盖者的行，说明这个值在三套环境里是同一个数。

## 运行形态

| 配置项 | dev | prod | CI | 覆盖者 |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 无（base 文档） | `prod` | `prod`（仅 smoke / drill 步骤） | `.env` → `SPRING_PROFILES_ACTIVE` |
| `SERVER_PORT` | `8081` | `${SERVER_PORT:8080}` | 容器内 `8080` | compose `app.environment` 固定 `8080` |
| `WEB_PORT`（宿主发布端口） | 不适用 | `8080` | drill 用 `18080` | `.env` → `WEB_PORT` |
| `APP_TAG`（镜像发布标识） | `dev` | 每个 release 一个值 | `${{ github.sha }}` | `.env` → `APP_TAG`；CI 用 workflow 覆盖 |
| 测试 JVM 参数 | 无 | 不适用 | `-XX:-UseContainerSupport` | Maven profile `surefire-without-container-support` |
| `campus.scheduling.enabled` | `true` | `true` | `false` | surefire `systemPropertyVariables`（见 pom.xml） |

## 数据存储

| 配置项 | dev | prod | CI | 覆盖者 |
|---|---|---|---|---|
| 数据源 | `jdbc:h2:mem:nexuscampus`（`MODE=MYSQL`） | `${DB_URL:...mysql://localhost:3306/nexus_campus}` | 单元测试仍是 H2；smoke / drill 是真 MySQL 8.0 | `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` |
| `spring.sql.init.mode` | `always`（跑 `schema.sql` + `data.sql`） | `never` | H2 测试下同 dev | `application-prod.yml` |
| Redis 开关 | `campus.redis.enabled=false` | `${REDIS_ENABLED:true}` | drill 起 `redis:7-alpine` | `REDIS_ENABLED` |
| Redis 地址 | `localhost:6379` | `${REDIS_HOST:localhost}:${REDIS_PORT:6379}` | 容器名 `redis` | `REDIS_HOST`、`REDIS_PORT` |
| `spring.cache.type` | `simple` | `redis` | 测试 `simple`；drill `redis` | `application-prod.yml` |
| Elasticsearch | `http://localhost:9200` | `${ES_URI:http://localhost:9200}` | drill 不启动 ES（ADR-0007：降级而非致命） | `ES_URI` |
| `campus.like.drift-enabled` | `true` | `${LIKE_DRIFT_ENABLED:true}` | 未特别覆盖 | `LIKE_DRIFT_ENABLED` |
| `campus.like.sample-size` | `200` | `${LIKE_SAMPLE_SIZE:200}` | 未特别覆盖 | `LIKE_SAMPLE_SIZE` |

## 账号、来源与信任

| 配置项 | dev | prod | CI | 覆盖者 |
|---|---|---|---|---|
| `jwt.secret` | base 文档里的 dev 回退值 | `${JWT_SECRET}`（必填，空白即启动失败） | workflow 里写入一个测试专用 base64 | `JWT_SECRET` |
| `jwt.expiration` | `86400000` | `${JWT_EXPIRATION:86400000}` | 同 prod 默认 | `JWT_EXPIRATION` |
| `campus.demo.seed-enabled` | `true` | `${DEMO_SEED_ENABLED:false}` | drill `false` | `DEMO_SEED_ENABLED` |
| `campus.demo.password` | `${DEMO_PASSWORD:123456}` | `${DEMO_PASSWORD:}`（开启 seed 时必填） | drill 不开启 seed | `DEMO_PASSWORD` |
| `campus.demo.endpoints-enabled` | `${DEMO_ENDPOINTS_ENABLED:false}` | `${DEMO_ENDPOINTS_ENABLED:false}` | `false` | `DEMO_ENDPOINTS_ENABLED` |
| `campus.bootstrap.admin-password` | 空（dev 靠 demo 账号） | `${BOOTSTRAP_ADMIN_PASSWORD:}` | drill 写入测试值 | `BOOTSTRAP_ADMIN_PASSWORD` |
| `campus.showcase.post-id` | 未设置 | `${SHOWCASE_POST_ID:}` | 未设置 | `SHOWCASE_POST_ID`、`SHOWCASE_AUTHOR_USERNAME` |
| `campus.cors.allowed-origins` | `http://localhost:5173,http://localhost:8080` | `${CORS_ALLOWED_ORIGINS:https://nexus-vibe.shing26.is-a.dev}` | 未特别覆盖 | `CORS_ALLOWED_ORIGINS` |
| `campus.security.trust-forwarded-headers` | `false` | `${TRUST_FORWARDED_HEADERS:true}` | 未特别覆盖 | `TRUST_FORWARDED_HEADERS` |
| `campus.trace.trust-inbound-header` | `${TRUST_INBOUND_TRACE_ID:false}` | 同左（默认不接收） | 未特别覆盖 | `TRUST_INBOUND_TRACE_ID` |

**`trust-forwarded-headers` 这一行值得单独看一眼。** prod 默认 `true`，含义是"客户端地址取自 `X-Real-IP`"，
而 `X-Real-IP` 由 nginx 用 `$remote_addr` 覆盖写入。两个默认值合起来才成立：单看这一行会以为应用信任任何客户端送来的头。
离线环境（直接打 `app:8080`、前面没有 nginx）里，同一个开关会让客户端自报的 `X-Real-IP` 生效——
`benchmark/concurrency/docker-compose.concurrency.yml` 正是靠这一点让 50 个探针用户各自占一个限流桶。

## AI 管线（`campus.ai.*`，全部绑定到 `CampusAiProperties`）

| 配置项 | 绑定默认值 | prod 默认 | CI | 覆盖者 |
|---|---|---|---|---|
| `review.enabled` | `true` | `${AI_REVIEW_ENABLED:true}` | `true` | `AI_REVIEW_ENABLED` |
| `review.lease-seconds` | `30` | `${AI_LEASE_SECONDS:240}` | `240`（无 prod 时用 `application.yml` 的 `240`） | `AI_LEASE_SECONDS` |
| `review.max-attempts` | `5` | `${AI_MAX_ATTEMPTS:5}` | `5` | `AI_MAX_ATTEMPTS` |
| `review.max-context-tokens` | `12000` | `${AI_MAX_CONTEXT_TOKENS:12000}` | `12000` | `AI_MAX_CONTEXT_TOKENS` |
| `safety.enabled` | `true` | `${AI_SAFETY_ENABLED:true}` | `true` | `AI_SAFETY_ENABLED` |
| `reconcile.stale-minutes` | `10` | `${AI_RECONCILE_STALE_MINUTES:10}` | drill 覆盖为 `1` | `AI_RECONCILE_STALE_MINUTES` |
| `archive.enabled` | `false` | `${LLM_ARCHIVE_ENABLED:false}` | `false` | `LLM_ARCHIVE_ENABLED` |
| `archive.path` | `scratch/llm-archive.jsonl` | `${LLM_ARCHIVE_PATH:scratch/llm-archive.jsonl}` | 未启用 | `LLM_ARCHIVE_PATH` |
| `llm.endpoint` | 无默认（`@NotBlank`） | `${LLM_ENDPOINT:https://api.openai.com/v1}` | 未特别覆盖 | `LLM_ENDPOINT` |
| `llm.model` | 无默认（`@NotBlank`） | `${LLM_MODEL:gpt-4o}` | 未特别覆盖 | `LLM_MODEL` |
| `llm.api-key` | `""` | `${LLM_API_KEY:}` | `""` | `LLM_API_KEY` |
| `llm.timeout` | `30s`（`@AssertTrue` 要求 > 0） | `${LLM_TIMEOUT:30s}` | `30s` | `LLM_TIMEOUT` |
| `llm.response-format` | `json_schema` | `${AI_RESPONSE_FORMAT:json_schema}` | `json_schema` | `AI_RESPONSE_FORMAT` |
| `llm.thinking-disabled` | `true` | `${AI_THINKING_DISABLED:true}` | `true` | `AI_THINKING_DISABLED` |
| `llm.breaker.failure-threshold` | `3` | `${AI_BREAKER_FAILURE_THRESHOLD:3}` | `3` | `AI_BREAKER_FAILURE_THRESHOLD` |
| `llm.breaker.open-seconds` | `60` | `${AI_BREAKER_OPEN_SECONDS:60}` | `60` | `AI_BREAKER_OPEN_SECONDS` |

**为什么 `review.lease-seconds` 有两列默认值。** `CampusAiProperties` 里的 `30` 是"这个键完全缺失"时的值，
它照抄自上一轮 `AiReviewEventListener` 的 `@Value("${campus.ai.review.lease-seconds:30}")`；
而 `application.yml` 写的是 `240`，`application-prod.yml` 也是 `${AI_LEASE_SECONDS:240}`。
两者不一致是真实存在的，ADR-0013 把它记在案：**能跑到的两套环境都是 240**，`30` 只在"文件被裁掉"时出现。
本轮没有改这个数，改它属于行为变更。

## 可观测性与告警

| 配置项 | dev | prod | CI | 覆盖者 |
|---|---|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,prometheus,metrics` | `health,info,prometheus` | 同 prod（smoke / drill 走 prod profile） | `application-prod.yml`，由 `ActuatorExposureContractTest` 钉住 |
| `management.metrics.tags.application` | `${spring.application.name}` = `nexus-vibe` | 同左 | 同左 | 不可覆盖（绑定到应用名，避免漂移） |
| `FEISHU_ALERT_WEBHOOK` / `FEISHU_ALERT_SECRET` | 空 | 空 = 告警桥拒绝启动 | drill 提供 in-network sink | `.env`；drill 用 `DRILL_FEISHU_*` |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | 仅 `--profile monitoring` 时读取 | 同左 | drill 用 `DRILL_GRAFANA_PASSWORD` | `.env`；drill 覆盖 |

## 这张表不包含什么

- **不包含密钥的值。** 所有"覆盖者"列里的名字都能在 `.env` 里取到值，而 `.env` 永不入库。
- **不包含 CI 之外的环境。** 没有 staging、没有预发；`benchmark/*/docker-compose.*.yml` 里的一次性项目
  （loadtest、concurrency、migrations、restore-test）都是临时环境，用完 `down -v`，它们继承同一份 `.env`
  和同一份 `docker-compose.yml`，因此不另开一列。
- **不包含前端构建期变量。** `frontend/` 只通过同源 `/api` 访问后端，没有 `VITE_*` 配置项需要按环境区分。
