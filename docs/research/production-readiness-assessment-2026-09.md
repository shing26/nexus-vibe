# Nexus-Vibe（Nexus-Campus）生产就绪度评估

> 评估日期：2026-09-12
> 评估范围：`D:\Nexus-Campus`（分支 `master`，HEAD `94bfc23`，工作区干净）
> 技术栈：Spring Boot 3.3.5 / Java 18 · MyBatis-Plus 3.5.9 · MySQL 8 / H2 · Redis 7 · ES 7.17 · React 19 + Vite
> 测试基线：`mvn test` 实测 **243** 个用例（README 写 236，已轻微过期）

---

## 一、总体结论

**这不是玩具级 Demo，但也还没到企业级。** 它准确处在两者之间的一个特定位置：

> **「功能完整度与容错设计已接近企业级，可观测性与运维配套仍停留在 Demo 级。」**

判断依据的核心矛盾在于——项目在**业务链路**上做了大量只有认真做过线上的人才会做的设计（租约互斥、fail-closed 降级、熔断、池隔离、对账任务），但**支撑这些链路运行的"眼睛"没有装**：日志不落盘、指标不暴露、告警为零。结果是：系统出问题时**能自愈，但没人知道它自愈了，也没人知道它没自愈成功**。

粗粒度就绪度估算：

| 维度 | 状态 | 就绪度 |
|------|------|--------|
| 1. 容错机制 | 部分具备（偏强） | ~75% |
| 2. 日志体系 | 部分具备（偏弱） | ~45% |
| 3. 配置管理 | 部分具备（偏强） | ~80% |
| 4. 监控与告警 | **缺失** | ~15% |
| 5. 错误处理 | 部分具备（偏强） | ~70% |

**综合约 60%，距可公开上线还差一轮"可观测性补齐"，工作量约 5–8 人日。**

---

## 二、逐项评估

### 1）容错机制 —— 部分具备（偏强）

**已具备的证据**

| 机制 | 代码依据 | 说明 |
|------|----------|------|
| 重试 + 指数退避 | `agent/LlmClient.java:272-305` `withRetry()` | `MAX_ATTEMPTS=3`，退避 `500ms × 2^(n-1)`；**只重试瞬时故障**（429/5xx/超时/IO），永久性 4xx 立即失败不重试 |
| 超时控制 | `LlmClient.java:60-62` | `ClientHttpRequestFactorySettings.DEFAULTS.withConnectTimeout/withReadTimeout`，由 `campus.ai.llm.timeout:30s` 注入 |
| 熔断 | `LlmClient.java:307-324` | 连续失败 3 次打开熔断 60s（`consecutiveFailures` + `circuitOpenUntil`），打开期间 fail-fast 返回 null |
| 健康探针 | `LlmClient.java:336-353` `isHealthy()` | 单次尝试、不走重试阶梯（避免拖死共享调度线程），且**不污染熔断状态**——设计细节到位 |
| 降级（Redis） | `config/RateLimitInterceptor.java:70`, `config/DataPreloader.java:78-90` | Redis 不可用时限流自动旁路、热榜回落 MySQL，均只告警不抛错 |
| 降级（LLM）fail-closed | `service/impl/VibePostServiceImpl.java:638` | LLM 不健康时帖子**保守进入 PENDING_REVIEW**，标记 `pending-llm`，由对账任务在 LLM 恢复后重跑 |
| 线程池隔离 | `config/AsyncConfig.java:33-42` | `agentLlmExecutor` core2/max4/queue50，LLM 长调用不会饿死消息/通知线程 |
| 背压降级 | `VibePostServiceImpl.java:614-645` | 池饱和抛 `RejectedExecutionException` 时，**不失败用户请求**，改把帖子降级为可重试状态（实测 20 万请求饱和：12.1 万次拒绝、零崩溃） |
| 分布式互斥 | `task/AiReviewReconcileTask.java` + ADR-0005 | 原子条件 UPDATE 领取评审租约，多实例/重复事件不会双处理；attempts 耗尽进终态并通知作者 |
| 自愈对账 | `AiReviewReconcileTask.java:67-100`、`task/DriftReconcileTask.java`、`task/LikeSyncTask.java` | 3 个定时任务：每 5 分钟重跑卡住的评审、每小时修 Redis-MySQL 计数漂移 |

**缺口**

1. **重试是"点"而不是"面"**：重试/退避逻辑只内嵌在 `LlmClient` 里。数据库抖动、Redis 超时、ES 写入失败都没有统一的重试抽象（无 `@Retryable`、无 `RetryTemplate`、无 Resilience4j）。业务代码一旦新增外部依赖，得再抄一遍重试。
2. **`agentLlmExecutor` 的拒绝策略靠默认值**：`AsyncConfig.java:33-42` 注释写着 "Small queue + Abort policy fail fast"，但代码里**没有显式 `setRejectedExecutionHandler()`**——当前依赖 `ThreadPoolTaskExecutor` 的默认 Abort。语义正确，但注释与代码是"隐式耦合"，将来有人改默认配置就静默失效。
3. **退避用 `Thread.sleep` 阻塞池线程**（`LlmClient.java:298`）：因为跑在隔离池上，当前可接受；但缺少可中断、可观测的调度式退避。
4. **故障注入未常态化**：README 里那些漂亮的压测结论（20 万请求、GC 分析、索引 EXPLAIN）都是**一次性人工实验**落档，没有变成可重跑的脚本纳入 CI。回归时无人能自动复现。

---

### 2）日志体系 —— 部分具备（偏弱）

**已具备的证据**

- **日志分级到位**：全仓库统一 SLF4J，`GlobalExceptionHandler` 对业务异常用 `warn`、系统异常用 `error`，`LlmClient` 重试过程用 `warn`——分级是**有意为之**的，不是随手打的。
- **分级随环境切换**：`application.yml:166-170`（dev `com.nexus.campus=DEBUG`）vs `application-prod.yml:86-90`（prod `INFO`，`org.mybatis=WARN`）。
- **生产关闭 SQL 全量打印**：`application-prod.yml:92-94` 用 `NoLoggingImpl`，而 dev 用 `StdOutImpl`——这是很实在的性能细节。
- **关键链路有 INFO 记录**：`[AI-RECONCILE]`、`[RATE-LIMIT]`、`[NEXUS-UPLOAD]`、`[PREHEAT]` 等统一前缀，租约领取/对账重跑/降级触发/限流拦截都有留痕。
- **敏感信息已脱敏**：`aspect/LogAspect.java` 只记录 URL/IP/Method 和响应 code，**不打印登录请求体与 JWT 响应体**（`docs/plans/pre-deployment-checklist.md` 有记录这是刻意修复的）。

**缺口（本维度是全项最弱，且有明确"空转"证据）**

1. **结构化日志实际上是空的**。`pom.xml:132-136` 引入了 `logstash-logback-encoder:7.4`，但 `src/main/resources/` 下**根本没有 `logback-spring.xml`**（已逐一枚举资源目录确认：只有 `application*.yml`、`schema.sql`、`data.sql`、2 个 lua、1 个 mapper.xml）。→ 依赖被引入但从未生效，实际输出是 Spring Boot 默认的**非结构化控制台 pattern**。
2. **日志不落盘**。没有 `logging.file.name`、没有 file appender。容器一重建，全部历史日志消失。
3. **容器日志无轮转上限**。`docker-compose.yml` 未配置 `logging: driver/options`，走默认 json-file 且**没有 `max-size` / `max-file`**——长期运行有磁盘被写满的风险。
4. **无 traceId / MDC**。全仓库 grep `MDC|traceId` 零匹配。一次请求从 controller → 异步发布 → LLM 调用 → 对账重跑，日志之间**无法串联**，线上排查只能靠时间戳盲猜。
5. **LLM 链路缺少结构化度量字段**：模型名、耗时、token 数、重试次数没有进日志字段（评审明细入 DB 了，但与应用日志不联动）。
6. **无审计日志**：管理员重置密码、审核通过/拒绝等敏感操作没有独立审计流。

---

### 3）配置管理 —— 部分具备（偏强）

**已具备的证据**

- **三套 profile 齐备且语义清晰**：`application.yml`（dev，H2 内存库 + Redis 默认关闭）/ `application-prod.yml`（prod，MySQL + Redis + 托管 LLM）/ `application-test.yml`。
- **环境变量外部化非常彻底**：prod 配置通篇 `${VAR:default}`，覆盖 `DB_URL/DB_USERNAME/DB_PASSWORD`、`JWT_SECRET`、`REDIS_*`、`LLM_*`、`CORS_ALLOWED_ORIGINS`、`AI_*`、`UPLOAD_DIR`、`TRUST_FORWARDED_HEADERS` 等。
- **`.env.example` 文档质量高**：逐项注释用途，并明确标注"生产建议托管模型或 ≥14B"这类决策约束。`.env` 已被忽略（`.gitignore:37` 实测命中）。
- **敏感配置 fail-fast**：
  - `application-prod.yml:40` → `jwt.secret: ${JWT_SECRET}`，**无默认值**，缺失即启动失败；
  - `DataPreloader.java:115-118` → 开了 `DEMO_SEED_ENABLED` 却没给 `DEMO_PASSWORD` 直接抛 `IllegalStateException`，拒绝种入空密码；
  - `docker-compose.yml:26,114` → `${DB_PASSWORD:?DB_PASSWORD must be set in .env}` 强制必填。
- **魔数已参数化**：租约时长、最大尝试次数、漂移阈值、熔断阈值、池大小、上传目录、CORS 来源全部可配。

**缺口**

1. **dev 里硬编码了一个 JWT 密钥**（`application.yml:92`，带 base64 长串默认值）。prod 会覆盖它，但这是个**弱默认值**：本地直接 `java -jar` 而没设 `SPRING_PROFILES_ACTIVE` 就会用上它。建议 dev 也改为必需注入或启动时打印醒目告警。
2. **`campus.demo.seed-enabled` 的 dev 默认值是 `true`**（`application.yml:105`）。安全性**完全依赖 profile 层覆盖**——一旦某次部署漏设 `SPRING_PROFILES_ACTIVE=prod`，就会种入 demo 账号。属于"一个环境变量决定安全姿态"的脆弱设计。
3. **无配置 schema 校验**。没有 `@ConfigurationProperties + @Validated`。环境变量名拼错（例如 `AI_LEASE_SECONS`）会**静默回落默认值**，不报错——这类问题在生产上极难发现。
4. **无密钥管理**。没有 Vault / K8s Secret / 云 KMS，全靠 `.env` 明文文件 + `env_file` 注入。也没有配置变更审计。
5. **多文档 YAML 的 profile 覆盖关系可读性差**：`application.yml` 用 `---` 拼了三段（默认配置 / management / mysql profile），覆盖关系靠读者心算，容易踩坑（例如默认段里的 `spring.sql.init.mode: always` 仅靠 prod 覆盖为 `never`）。
6. **test profile 存在但 CI 未显式使用**：`.github/workflows/maven.yml:32` 直接 `mvn test`，未指定 profile。

---

### 4）监控与告警 —— 缺失（全项最薄弱）

**已具备的证据（仅有骨架）**

- Actuator 已引入（`pom.xml:36-39`）。
- prod 暴露 `health,info`（`application-prod.yml:102-109`）；dev 额外暴露 `metrics`。
- `Dockerfile:38` 有 `HEALTHCHECK ... /actuator/health`；compose 给 `db/redis/elasticsearch/ollama` 都配了 healthcheck。
- `docker/nginx/nginx.conf:77-84` 反代 `/actuator/health` 且关闭 access log。
- 应用级有 LLM 健康探针（`LlmHealthCache` + `LlmClient.isHealthy()`），供对账任务判活。

**缺口（这是"从跑通到落地"的核心鸿沟）**

1. **没有 Prometheus registry**。`pom.xml` 中**没有 `micrometer-registry-prometheus`** → 不存在 `/actuator/prometheus` 端点，指标**根本无法被抓取**。
2. **prod 主动关掉了系统指标**。`application-prod.yml:5-7` exclude 了 `SystemMetricsAutoConfiguration` —— 为省 768m 容器内存而做的取舍可以理解，但结果是**连 JVM 堆/GC/线程指标都没有**（GC 日志只写到容器内 `/tmp/gc.log`，重启即丢，无人采集）。
3. **零自定义业务指标**。全仓库 grep `MeterRegistry | @Timed | Counter | Micrometer` **零匹配**。意味着这些关键信号**只有日志，没有指标**：
   - LLM 调用成功率 / P95 耗时 / 熔断打开次数
   - AI 评审队列积压量与对账重跑条数
   - 限流拒绝次数（`RateLimitInterceptor.java:114` 只 `log.warn`）
   - 点赞漂移修复条数、池拒绝次数
4. **没有任何告警通道**。compose 里无 Prometheus/Grafana/Alertmanager；无外部告警接入（邮件/IM/Webhook）。**唯一的"通知"是给发帖作者的站内信**（`AiReviewReconcileTask.notifyBudgetExhausted`）——运维侧没有任何人被通知。
5. **健康检查粒度过浅**。只有 Spring 默认 `/actuator/health`，**无自定义 `HealthIndicator`**。因为"Redis 挂了=正常降级"，default health 仍会报 `UP`——**健康状态与"业务是否真的可服务"不等价**，LLM 全挂时健康检查照样绿灯。
6. **actuator 端点无鉴权**：`JwtAuthFilter.java:39` 只拦截 `/api/` 前缀，`/actuator/**` 不在其中。当前靠 prod 只暴露 `health,info` 兜底，`show-details: when-authorized` 在无 Spring Security 时实际不会输出详情——**是"恰好安全"而非"设计安全"**，一旦有人放开 exposure 就会裸奔。
7. **无 tracing**：无 Micrometer Tracing / OpenTelemetry，跨服务（Nginx → App → LLM → Redis → MySQL）链路不可视。
8. **无 SLO/SLI 定义**，无容量基线守门，无崩溃收集（仓库根目录还躺着两个 `hs_err_pid*.log`，2026-09-06）。

---

### 5）错误处理 —— 部分具备（偏强）

**已具备的证据**

- **全局异常处理器规范**（`config/GlobalExceptionHandler.java`，11 类异常）：

  | 异常 | HTTP | 日志级别 | 客户端可见信息 |
  |------|------|----------|----------------|
  | `MethodArgumentNotValidException` | 400 | — | 字段级校验消息（`;` 拼接） |
  | `MissingServletRequestParameterException` | 400 | — | 缺失参数名 |
  | `MethodArgumentTypeMismatchException` | 400 | — | 非法参数名 |
  | `HttpMessageNotReadableException` | 400 | — | 通用"请求体缺失或畸形" |
  | `MaxUploadSizeExceededException` | 413 | — | 超限提示 |
  | `IllegalArgumentException/IllegalStateException` | 400 | **warn** | 原始业务消息 |
  | `RuntimeException` | 500 | **error + 全栈** | 固定 `"Internal server error."` |
  | `Exception` | 500 | **error + 全栈** | 固定通用文案 |
  | `NoResourceFoundException` | 404 | debug | 通用文案 |
  | `HttpRequestMethodNotSupportedException` | 405 | debug | 列出允许的方法 |

- **业务错误与系统错误确实分离**：代码注释明确写着未知异常 "must not leak internal details (messages, stack traces, SQL fragments)"→ 堆栈只进服务端日志，客户端拿到脱敏文案。这是**企业级思维**。
- **统一响应壳**：`dto/ApiResponse.java` 提供 `success/error/unauthorized/forbidden/notFound` 工厂方法，前端有稳定的 `code/message/data` 结构。
- **HTTP 语义基本正确**：400/401/403/404/405/413/429 都有对应映射。
- **前端错误处理有亮点**：`frontend/src/api/client.ts:19-72` 实现了**单飞（single-flight）401 刷新**——并发 401 只触发一次 `/auth/refresh`，成功则重放原请求，失败则统一登出；并区分了"登录接口自身 401"以防死循环。这个实现质量高于同类项目平均水平。全局 `ErrorBoundary` 也在（`components/ErrorBoundary.tsx`）。

**缺口**

1. **没有错误码体系**。`ApiResponse.error(400, "...")` 直接写魔法数字，共 **27 处**调用点（`GlobalExceptionHandler` 11、`UploadController` 5、`AuthController` 5、`AdminController` 4、`UserController` 1、`DemoShowcaseController` 1）。**没有枚举、没有业务码**（如 `NX-1001 内容违规` / `NX-2003 租约冲突`）。前端只能靠匹配 message 文案判断分支——**文案一改，前端逻辑就断**。
2. **没有自定义业务异常类**。全仓库 grep `extends RuntimeException` **零匹配** → 业务语义全靠 JDK 内置的 `IllegalArgumentException` / `IllegalStateException` 承载。粒度太粗，导致：
   - **409 冲突被压成 400**：重复邮箱、并发点赞冲突、重复审核都没有 Conflict 语义；
   - **404 资源不存在被压成 400**：`IllegalStateException("post not found")` 也返回 400，HTTP 语义失真；
   - `IllegalStateException` 一律映射 400，会把**真正的内部状态错误误报为客户端错误**，误导排查方向。
3. **授权检查是分散的**。无 Spring Security 过滤器链（`pom.xml` 只有 `spring-security-crypto`），管理员权限靠每个方法内手写 `checkAdmin(role)`（见 `AdminController.java:54-56`）。**新增接口忘写一次就是越权漏洞**——这是结构性风险，不是笔误风险。
4. **前后端响应契约不一致**。后端 `ApiResponse` 字段是 `code/message/data`，而 `frontend/src/api/client.ts:74-78` 声明的 TS 接口是 `{ success: boolean; data: T; message?: string }`——**`success` 字段后端根本不存在**。当前未爆雷可能是调用方没读该字段，但契约已经漂移。
5. **错误产生点分散**：判定逻辑直接写在 controller 里 `return ApiResponse.error(...)`（`UploadController.java:55-80`、`AdminController`），没有统一错误契约测试；服务层抛错与控制器判空两套风格并存。
6. **文案中英混用**：`"Too many requests. Please try again in 60 seconds."` 与「你的帖子《…》的 AI 评审连续 5 次失败…」并存，无统一语言策略。
7. **前端 `ErrorBoundary` 只 `getDerivedStateFromError`，没有 `componentDidCatch`** → 前端崩溃**不会上报任何地方**，用户看到白屏，你什么都不知道。

---

## 三、优先级差距清单（从"跑通"到"接近落地"）

### 🔴 P0 —— 上线阻断项（不做就不该公开）

| # | 差距 | 现状依据 | 改进方案 | 工作量 |
|---|------|----------|----------|--------|
| P0-1 | **日志不落盘、无结构化、无轮转** | 无 `logback-spring.xml`；`logstash-logback-encoder` 空转；compose 无 logging 配置 | 新增 `logback-spring.xml`：dev 用 pattern 到控制台，prod 启用 `LogstashEncoder` JSON 到滚动文件（保留 7 天 / 单文件 100MB）；compose 全部服务加 `logging.options.max-size: 10m / max-file: "3"` | 0.5 人日 |
| P0-2 | **指标零暴露、告警为零** | pom 无 `micrometer-registry-prometheus`；prod exclude 系统指标；无监控组件 | 加依赖 + 暴露 `/actuator/prometheus`；引入 Prometheus + Grafana + Alertmanager（单机可用轻量栈）；恢复 JVM 指标（如担心内存可只采 `jvm.memory.used` 等低成本项） | 1.5–2 人日 |
| P0-3 | **无业务指标** | grep `MeterRegistry/@Timed/Counter` 零匹配 | 注入 `MeterRegistry`，补 4 组指标：LLM 成功率与耗时直方图、评审队列积压 Gauge、限流拒绝 Counter、对账修复 Counter；在 `RateLimitInterceptor`/`LlmClient`/`AiReviewReconcileTask` 埋点 | 1 人日 |
| P0-4 | **健康检查不代表真实可服务** | 无自定义 `HealthIndicator`；Redis 挂仍 UP | 新增 `LlmHealthIndicator` / `RedisHealthIndicator` / `EsHealthIndicator`，聚合为 `degraded` 语义；`/actuator/health` 的 `show-details` 保持 `when-authorized`，并对 actuator 路径做网络层限制（仅内网/nginx 白名单） | 0.5–1 人日 |
| P0-5 | **生产无首个管理员引导路径** | `docker/mysql/init.sql` 不插入 `sys_user`；`DataPreloader` 在 `DEMO_SEED_ENABLED=false` 时给 `admin` 写入**随机不可恢复密码** | 提供显式的一次性引导：`BOOTSTRAP_ADMIN_PASSWORD` 环境变量（仅当用户表为空时生效）或独立 CLI 命令；同步更新 `.env.example` 与部署文档 | 0.5–1 人日 |
| P0-6 | **关键告警规则缺失** | — | 随 P0-2 一起配：LLM 熔断打开、评审积压 > N 持续 15min、5xx 率 > 1%、限流拒绝突增、容器 OOM/重启 | 0.5 人日（含在 P0-2） |

**P0 小计：约 4.5–6 人日。做完即从"能跑"跨到"可上线"。**

### 🟡 P1 —— 可靠性与可维护性（落地必备，可与 P0 并行）

| # | 差距 | 现状依据 | 改进方案 | 工作量 |
|---|------|----------|----------|--------|
| P1-1 | **无请求链路追踪** | grep `MDC/traceId` 零匹配 | 过滤器生成 `traceId` 写入 MDC，`taskDecorator` 传递到异步池；LLM 日志带上 traceId 与耗时 | 0.5 人日 |
| P1-2 | **无错误码体系、无业务异常类** | 27 处魔法数字；grep `extends RuntimeException` 零匹配 | 建 `ErrorCode` 枚举 + `BusinessException`（携带 code + HTTP 状态）；补 `404/409` 专用 handler；前端改为按 code 分支 | 1–2 人日 |
| P1-3 | **前后端响应契约漂移** | 前端声明 `success`，后端返回 `code` | 对齐 TS 类型定义，或后端补 `success` 字段；加一个契约冒烟测试 | 0.5 人日 |
| P1-4 | **配置无校验、dev 弱默认值** | `application.yml:92` 硬编码 JWT 密钥；`seed-enabled: true` | 改 `@ConfigurationProperties + @Validated`；dev JWT 密钥改为必需注入或启动打印告警；`seed-enabled` dev 默认改 `false` | 0.5–1 人日 |
| P1-5 | **容器以 root 运行、compose 编排细节不足** | `Dockerfile:17-44` 无 `USER`；`app` 服务未显式 healthcheck；`web` 的 `depends_on: app` 非 `service_healthy` | 加非 root 用户；`app` 显式声明 healthcheck；`web` 改 `condition: service_healthy` | 0.5 人日 |
| P1-6 | **授权检查分散** | `AdminController` 每方法手写 `checkAdmin(role)` | 引入 HandlerInterceptor 或 `@RequiresRole` 注解做集中式拦截，`/api/v1/admin/**` 统一门禁 | 0.5–1 人日 |
| P1-7 | **前端崩溃无上报** | `ErrorBoundary` 无 `componentDidCatch` | 补 `componentDidCatch`，上报后端或第三方；错误码体系统一后再补分类 | 0.5 人日 |

**P1 小计：约 4–6.5 人日。**

### 🟢 P2 —— 企业级打磨（可上线后迭代）

| # | 差距 | 改进方案 | 工作量 |
|---|------|----------|--------|
| P2-1 | 无分布式追踪 | Micrometer Tracing + OTel，串联 Nginx→App→LLM→Redis | 1–2 人日 |
| P2-2 | 密钥靠 `.env` 明文 | 接 Vault / 云 KMS / K8s Secret | 1–2 人日 |
| P2-3 | 可靠性结论是一次性人工实验 | 把 `docs/research/` 的压测与故障注入沉淀为**可重跑脚本**（JMeter 已有 `benchmark/jmeter/*.jmx` 可复用），纳入 CI 夜间任务 | 1–2 人日 |
| P2-4 | 无审计日志 | 管理员操作（改密/审核/发布公告）落独立审计表 + 结构化日志 | 1 人日 |
| P2-5 | 数据库迁移靠手工 `migrate-*.sql` | 引入 Flyway/Liquibase，用版本化迁移替代"一次性脚本 for existing volumes" | 1–2 人日 |
| P2-6 | 无发布策略 / 容量守门 | 蓝绿或滚动发布；性能基线纳入 CI 门禁 | 2–3 人日 |
| P2-7 | 无统一重试抽象 | 抽 `@Retryable` 或引入 Resilience4j，覆盖 DB/Redis/ES 调用 | 1–2 人日 |

**P2 小计：约 8–14 人日（不阻断上线）。**

---

## 四、实施顺序建议

**核心原则：先装"眼睛"，再补"契约"，最后做"打磨"。**

```
第 1 步（0.5 人日）  日志落盘 + 结构化 + 轮转          ← 最先做
                    理由：后续所有排障都依赖它；成本最低、收益最高

第 2 步（1.5–2 人日）指标暴露 + Prometheus/Grafana      ← 与第 1 步天然衔接
                    理由：有了日志还需要"趋势与聚合"，这是告警的前置条件

第 3 步（1.5 人日）  业务指标埋点 + 告警规则 + 健康检查
                    理由：让 P0 的监控栈从"有数据"变成"能发现问题"

第 4 步（1 人日）    生产管理员引导路径
                    理由：独立于监控，可并行；但上线前必须堵住

     ── 以上完成 = 可公开上线（约 4.5–6 人日）──

第 5 步（1–1.5 人日）traceId + 前后端契约对齐
                    理由：P0 的日志/指标已能定位"哪个环节"，traceId 解决"哪一次请求"

第 6 步（2.5–4 人日）错误码体系 + 业务异常类 + 集中式授权
                    理由：涉及面广（27 处调用点 + 前端），需要一次完整重构窗口

第 7 步（1–1.5 人日）配置校验 + 容器非 root + compose 编排加固

     ── 以上完成 = 接近企业级（累计约 9–13 人日）──

第 8 步及以后        追踪、密钥管理、混沌常态化、审计、迁移工具
```

**为什么是这个顺序？** 关键判断是：这个项目的**"做对事情"的能力已经很强**（租约、fail-closed、对账、池隔离都是难而正确的事），它缺的是**"知道自己做对了没有"的能力**。所以投入产出比最高的一定是可观测性——它不改变任何业务逻辑，却让已有的大量容错设计**从"隐形"变成"可视、可验证、可告警"**。反过来先做错误码重构，只是改善了代码整洁度，对线上生存能力提升有限。

---

## 五、附：最终裁定

| 问题 | 裁定 |
|------|------|
| 是否仍停留在玩具级 Demo？ | **否**。玩具 Demo 不会有原子租约、fail-closed 降级、熔断、池隔离、漂移对账，更不会在 20 万请求饱和下零崩溃。 |
| 是否已接近可上线/企业级？ | **半只脚**。"可上线"差一轮可观测性补齐（4.5–6 人日）；"企业级"还差契约规范化与运维体系（再 4.5–7 人日）。 |
| 最大的单点风险是什么？ | **无告警**。系统具备不错的自愈能力，但**自愈失败时没有任何人会知道**——这是当前唯一能导致"静默数据损坏/服务退化"而不被察觉的结构性缺口。 |
| 最值得表扬的是什么？ | `LlmClient` 的"只重试瞬时故障 + 熔断不污染健康探针 + 只重试不超预算（总调用 ≤2 次）"三件事同时做对，以及 `VibePostServiceImpl` 在池饱和时选择**降级用户请求而非失败请求**——这两处体现的是真实线上经验。 |
