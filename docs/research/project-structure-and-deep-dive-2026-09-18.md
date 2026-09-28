# Nexus-Vibe 项目结构与深挖准备

**日期**：2026-09-18
**基线**：`codex/pre-launch-hardening` 工作区（含本轮缺陷修复，未提交）
**用途**：在被追问架构、失败处理、数据一致性与技术选型时，能按代码路径回答，而不是背 README。
**证据约定**：本文中的数量与命令以本机实测为准；生产代码指 `src/main` 与 `frontend/src`，不含测试、依赖和构建产物。

---

## 0. 一句话定位

这不是"论坛 + 接了一个大模型"，而是**把 LLM 当成一个会超时、会返回脏 JSON、会被提示注入、会熔断的不可靠依赖来治理**。

这句话最好的证据不是 `pom.xml` 里的依赖，而是四条不可配置的机制：

1. **fail-closed**：模型不可用时帖子不公开发布，进入人工审核队列并留下可重放的标记（ADR-0004）。
2. **lease claim**：评审任务用数据库条件 UPDATE 领租约，进程重启、重复事件、对账重派都不能双跑（ADR-0005）。
3. **reconciliation**：事件丢了不是数据丢了，MySQL 是真相，每 5 分钟把卡住的工作捞回来。
4. **review validity**：模型输出要通过语义校验才允许落库，不合格先强化提示词重试，再不合格进 FAILED 终态。

技术选型的取舍标准是**"它解决什么问题"**，不是"它是否出现在 JD 里"。

---

## 1. 技术栈与运行时拓扑

| 层 | 选型 | 在本项目中的职责 |
|---|---|---|
| 语言/运行时 | Java 21 toolchain，bytecode target 18 | 与 CI 一致；18 是产物目标，不是本机 JDK |
| Web | Spring Boot 3.3.x + Spring MVC | 单体应用；没有第二个可部署单元 |
| 持久层 | MyBatis-Plus 3.5.x + MySQL 8（测试用 H2） | 帖子、评论、消息、评审日志、租约 |
| 缓存/计数 | Redis 7 + Lua | 点赞集合、热榜 ZSET、限流滑动窗口 |
| 搜索 | Elasticsearch（可选） | 索引失败时降级到 MySQL，不阻塞主链路 |
| 前端 | React 19 + Vite + TypeScript + TanStack Query + Zustand | SPA，与后端镜像成对发布 |
| 容器 | Docker Compose | `web` 是唯一映射宿主端口的入口 |
| 可观测性 | Actuator + Micrometer + Prometheus + Grafana + alert-bridge | `monitoring` profile 下无宿主端口 |
| AI | OpenAI 兼容 chat completions；默认本地 Ollama，可换托管模型 | 代码评审 + 四分类安全审查 |

**关键拓扑约束**：公网只经过 `web`（nginx）。`db`、`redis`、`elasticsearch`、`ollama`、`prometheus`、`grafana`、`alert-bridge` 都不应映射宿主端口。任何新增组件只要映射端口，就是在已收敛的暴露面上重新开口。

---

## 2. 后端包结构与职责

`src/main/java/com/nexus/campus` 共 **137 个 Java 文件**，按职责分包：

| 包 | 文件数 | 职责 | 深挖时优先看 |
|---|---:|---|---|
| `agent/` | 13 | LLM 客户端、JSON 修复、提示隔离、评审服务、安全审查、触发器接口 | `LlmClient`、`AiReviewService`、`AiSafetyCheckListener`、`ReviewPolicy` |
| `config/` | 25 | 过滤器、拦截器、健康指示器、异步池、缓存、启动播种、异常处理 | `AsyncConfig`、`GlobalExceptionHandler`、`RateLimitInterceptor`、health |
| `controller/` | 13 | HTTP 边界；只收发 DTO/VO，不出现实体 | `PostController`、`AdminController`、`UploadController` |
| `dto/` | 29 | 请求/响应模型、视图对象 | `PostCreateRequest`、`PostPageVo` |
| `entity/` | 8 | 持久化实体 | `VibePost`、`VibeComment`、`SysUser` |
| `enums/` | 2 | 状态枚举 | `PostStatus`、`AiReviewStatus` |
| `event/` | 2 | 站内消息等事件与监听器 | `MessageEventListener` |
| `exception/` | 1 | 业务异常 | `BusinessException` |
| `filter/` | 2 | XSS 过滤与包装 | `XssFilter` |
| `mapper/` | 9 | MyBatis-Plus Mapper 与 SQL 契约 | `VibePostMapper` |
| `metrics/` | 2 | 产品漏斗与 Prometheus 指标 | `ProductMetrics` |
| `security/` | 1 | JWT 过滤器 | `JwtAuthFilter` |
| `service/` | 20 | 业务服务与实现 | `VibePostServiceImpl`、`LikeCounterService`、`PostSearchService` |
| `task/` | 4 | 定时任务 | `AiReviewReconcileTask`、`DriftReconcileTask`、`LikeSyncTask`、`FunnelAggregateTask` |
| `util/` | 4 | traceId、JWT、内容清洗等 | `TraceIds` |
| `aspect/` | 1 | 日志切面 | `LogAspect` |

---

## 3. 两条必须能讲清的主链路

### 3.1 同步发帖链路（用户看到的那条）

1. `POST /api/v1/posts` -> `PostController` 校验 `PostCreateRequest`。
2. `VibePostServiceImpl.createPost` 在事务内落 `vibe_post`，同时做 DFA 敏感词审核。
3. 若内容有 critical 词，状态直接进入 `PENDING_REVIEW`；否则 `ACTIVE`。
4. 按 `ReviewPolicy` 判定是否要代码评审。只有判定为"要评审"的帖子才预写 `ai_reviewed=REVIEWING`，这样事件在领租约前丢掉也能被对账捞回。
5. 按 `campus.ai.safety.enabled` 独立派发安全审查事件；**安全审查不再受 review 开关控制**。
6. 异步池饱和时，发布方不抛异常到用户：评审标 `FAILED` 等对账，安全审查直接 fail-closed 到 `PENDING_REVIEW` + `pending-llm`。

**答辩重点**：为什么故障处理发生在发布方而不是消费者？因为 `@Async` 的提交流程发生在发布线程，`RejectedExecutionException` 会同步冒出来；在这里兜比让用户收到 500 更便宜。

### 3.2 异步评审链路（项目真正的主张）

1. `AiReviewEventListener` 收到事件，先问 `ReviewPolicy`——**这是唯一的触发规则**。
2. 不满足条件就清掉可能残留的 `REVIEWING` 标记（只清 `REVIEWING`，不碰已完成的评审）。
3. 满足条件则 `tryClaimReview` 原子领租约；锁被别人持有或预算耗尽直接退出。
4. 领到租约后调 `AiReviewService.reviewPost`：提示隔离 -> LLM 调用 -> JSON 修复 -> 语义校验 -> 不合格强化重试。
5. 通过校验才写评论、评分和 `ai_review_log`；此时重算 `comment_count`。
6. 失败标 `FAILED`；用尽预算发一次终态通知；`finally` 释放自己的租约。
7. `AiReviewReconcileTask` 每 5 分钟扫 `REVIEWING` / `FAILED` / `pending-llm`，LLM 不健康时只刷新积压指标、不做空转重试。

**最容易出事的一环**：触发规则和状态写入如果不一致，就会出现"标了 REVIEWING 但监听器永远跳过"的空转循环。本轮把它收敛到 `ReviewPolicy` 一处，并加了源码扫描测试防止回退。

---

## 4. 核心机制与它们的边界

| 机制 | 解决什么 | 不解决什么 | 关键代码 |
|---|---|---|---|
| `ReviewPolicy` | 触发规则只存在一处，fork 可替换 | 不保证模型质量，也不替代租约/对账 | `agent/ReviewPolicy.java` |
| lease claim | 并发/重放不双跑，预算是有限的 | 不保证模型一定成功；预算是每帖生命周期计数 | `mapper/VibePostMapper.java` |
| reconciliation | 丢事件、进程重启后把工作捞回来 | 不修复模型输出错误；LLM 不健康时不做无意义重试 | `task/AiReviewReconcileTask.java` |
| fail-closed | 模型不可用时宁可不发布 | 不保证通知必达 | `agent/AiSafetyCheckListener.java` |
| JSON 修复 + 有效性校验 | 不让脏输出变成评分和评论 | 不能阻止提示注入；提示隔离负责那部分 | `agent/JsonRepairUtil.java` |
| 点赞漂移对账 | Redis 丢集合时用 `vibe_post_like` 重建 | 不做双向删除；union 语义会保留可疑成员 | `task/DriftReconcileTask.java` |
| 降级健康语义 | 依赖坏了服务仍然 200，只有 db 算不可服务 | 不把 actuator 细节暴露到公网 | `config/health/**`、ADR-0007 |

---

## 5. 数据模型要记住的四件事

1. **持久真相在 MySQL**，Redis 和事件都是加速或唤醒层；这条决定了为什么不需要消息队列。
2. `vibe_post_like` 是点赞的**成员关系表**，`vibe_post.like_count` 是冗余计数；正常路径现在两者都写，漂移任务用 union 收敛。
3. `ai_review_log` 同时承载代码评审与安全审查，靠 `reviewer` 区分（`code-review-agent` / `safety-check-agent`）；`severity='pending-llm'` 是可重放标记。
4. `review_lock_until` / `review_owner` / `review_attempts` 三列是租约与预算的全部状态，不要只看内存里的 Future。

---

## 6. 配置面与开关

- Profile：默认 `prod`；测试用 H2 覆盖数据源。
- 信任边界：`campus.security.trust-forwarded-headers` 只应在可信反向代理后开启；nginx 现在只信 `$remote_addr`，不信任客户端可伪造的 `CF-Connecting-IP`。
- AI 开关：`AI_REVIEW_ENABLED` 与 `AI_SAFETY_ENABLED` **互相独立**。关掉前者不会关闭安全审查；两个都关才不会调用模型。
- 发布不阻塞：`campus.ai.review.enabled=false` 时帖子不进入评审状态，不会留下无人处理的 `REVIEWING`。
- 监控：`monitoring` profile 默认不启动；Grafana 密码为空时 fail-fast，不再回落到字面量。

---

## 7. 验证入口（被追问"你怎么知道它是好的"）

```bash
mvn -B test
cd frontend && npm run test && npm run lint && npm run build
cd docker/observability/alert-bridge && python -m unittest test_alert_bridge
```

本轮实测结果（2026-09-18）：

| 检查 | 结果 |
|---|---|
| 后端 | `Tests run: 375, Failures: 0, Errors: 0, Skipped: 0` / 61 个测试类 |
| 前端 | 33 passed / 8 files；lint 0 error；build 成功 |
| alert-bridge | 28 tests OK |
| 源码扫描契约 | contract 包 11 条检查：无实体进 controller、无陈旧状态断言、无重复触发规则、配置路径未错缩进等 |

**答辩纪律**：不要用 `target/surefire-reports/*.txt` 求和；那是历史运行目录，会重复计数。以 `mvn` 汇总行为准。

---

## 8. 已知风险与可以直接讨论的取舍

| 议题 | 现状 | 可以怎么说 |
|---|---|---|
| 单体 vs 微服务 | 单进程，只有一个可部署单元 | 当前吞吐瓶颈是 LLM 每天 1–12 次，拆服务只会增加一跳失败面；拆 AI 链路是实验，不是补齐 |
| 消息队列 | 没有 broker | 事件会丢，但对账把工作捞回来；这条设计是 ADR-0012 明文写死的 thesis，不是遗漏 |
| 分库分表 | 没有 | 单表数据和 QPS 远未到阈值；现在做是给自己制造跨分片事务 |
| 熔断器 | 手写，无 HALF_OPEN 探针 | 有断流和恢复长窗口；没有真实的间歇性故障样本，先用测量证明再改 |
| Spring Cloud | 没有 | 没有服务发现、配置中心、网关或多实例的真实需求；引入等于先有答案后找问题 |
| 站内信 | 走默认 `@Async` 池，失败只记录，没有对账 | 明确是 best-effort：丢的是通知，不是业务状态；若要强保证，先落 outbox 再谈 broker |

---

## 9. 深挖时最可能被追问的五个问题

1. **"如果 Kafka 挂了怎么办？"** -> 现在没有 Kafka；如果引入，必须先回答它替谁解决了什么，以及为什么数据库对账不够。ADR-0012 把对账列为不可配置，替换它会删掉仓库的论点。
2. **"AI 评审的触发规则写在哪？"** -> `ReviewPolicy` 一处，默认实现 `CodeBlockReviewPolicy`；`AiReviewEventListener` 和 `VibePostServiceImpl` 都不再自己判断。
3. **"编辑帖子会不会绕过审核？"** -> 编辑路径现在复用 DFA，并按策略重跑评审与安全审查；这是本轮修的 P1 缺陷。
4. **"点赞发现 Redis 丢了，会不会清零？"** -> 不会；`vibe_post_like` 是成员真相，漂移任务取 Redis 与 DB 的并集，且在没有可恢复成员时保留原计数等待人工处理。
5. **"限流能不能用请求头绕过？"** -> 不能；nginx 只使用 `$remote_addr`，应用层也不再回退到 `X-Forwarded-For`。

---

## 10. 与"补技术栈"的关系

这个仓库的分量不在依赖清单，而在**它能不能解释每个组件为什么存在、为什么不存在**。

如果为了简历补 Spring Cloud / Kafka / 分库分表，先要跨过三道门：

1. 它解决的是当前真实存在、且已测量的问题吗？
2. 引入后，原来的失败语义（fail-closed、租约、对账、降级健康）是更强还是更弱？
3. 能不能在现有验证入口下证明它没有破坏主链路？

三道门都过了，才值得动。否则更好的做法是把它标注为一次**架构演练**，在独立分支里做，不让它改写主线的交付说明。
