# Nexus-Vibe 项目结构与完成度梳理（2026-09）

评估对象：`D:\Nexus-Campus`，分支 `codex/production-readiness`，HEAD `0284933`（领先 `master` 13 个 commit）。
评估手段：CodeCompass 静态索引（518 files / 1955 symbols，AST 级调用图）+ 本机实测（`mvn test`、`npm run build`、`docker compose config`、演练日志）。
所有数字均为本次实测，不是引用。

---

## 一、总体判断

这是一个**真实可运行的全栈产品，不是演示原型**。判据不是"功能多"，而是三件造假成本很高的事：

1. **284 个 JUnit 用例全绿**（本次实测：`classes=40 tests=284 failures=0 errors=0 skipped=0`）。其中 12 个测试类带 `@SpringBootTest`，会真起 Spring 上下文（H2 + MockMvc 打 HTTP），不是纯 mock；7 个按 `*IntegrationTest` 命名。
2. **626 行故障演练脚本**（`benchmark/observability/drill.ps1`），把 LLM 端点指向死端口跑真实容器栈，16 步断言全过，且报告里明确写了"这 16/16 里哪几步曾因为脚本自己的 bug 而假红"。
3. **有被记录并执行的否定决策**：7 篇研究文档里 3 篇的结论是"回滚 / 不采用"（深分页延迟关联退化 40%、AI 排序索引无过滤时负优化、cgroup exclude 属于误诊）。只写成功案例的项目是宣传，写下"我试错了"的才是工程。

规模基线（实测）：

| 维度 | 数值 |
|---|---|
| Java 主代码 | 8 762 行 / 115 类 / 121 文件 |
| Java 测试代码 | 5 209 行 / 40 类 |
| 前端代码 | 6 211 行 / 49 文件（41 `.tsx` + 7 `.ts` + 1 css） |
| HTTP 端点 | 70 个（12 个 `@RestController`） |
| 方法 / 字段 | 863 / 662 |
| 配置键 | 178 |
| Compose 服务 | 6（默认）/ 9（`monitoring` profile） |
| Git 提交 | 88（2026-07-20 起） |
| 文档 | 8 ADR + 7 研究 + 2 工单集 + 2 设计 + 1 博客 |

一句话定位：**单人或小团队可交付、可自托管、可运维的产品级项目；距离"多人协作的企业级交付物"还差权限体系、契约治理和可观测性第三件套（traces / logs 聚合）。**

---

## 二、模块划分与依赖关系

CodeCompass 约定画像（`get_conventions`，全量 AST 投票，非人工归纳）给出了这套分层的客观证据：

| 轴 | 结论 | 覆盖率 |
|---|---|---|
| 返回包装 | Controller 统一返回 `ApiResponse<T>` | 50 / 56 |
| 服务风格 | 纯类为主，6 个 `*ServiceImpl` 为例外 | 10 / 15 |
| 注入风格 | 字段注入 `@Autowired` | 62 / 69 |
| 基类继承 | 生产类无共享基类 | 115 / 115 |
| 包布局 | `com.nexus.campus.*` | 112 / 115 |

依赖方向单向：`controller → service → mapper`；`agent` 与 `task` 通过事件与状态列和 `service` 解耦。PageRank 前两名是 `ApiResponse.error`（0.0189，入度 22）与 `ApiResponse.success`（0.0177，入度 39）——**全局枢纽是一个 DTO 工厂而非某个 Service**，这本身就是"没有上帝服务"的证据。第三名 `TraceIds.current`（0.0170）是本轮新加的追踪号工具，说明它确实铺进了横切面而不是孤立存在。

```
浏览器 (React SPA)
   |  /api/v1/*, X-Trace-Id
   v
nginx :80  -- /actuator/ 显式 404（仅放行 = /actuator/health）
   |
   v
TraceIdFilter -> XssFilter -> JwtAuthFilter -> RateLimitInterceptor
   |                                                | Redis Lua 滑动窗口
   v
12 x @RestController  --统一-->  ApiResponse<T>
   |
   +-- VibePostServiceImpl --> 22 个 service --> 8 个 MyBatis Mapper --> MySQL
   |                               |                 |
   |                               |                 +--> Redis（点赞/排序/限流/缓存）
   |                               +--> Elasticsearch（检索，不可用时降级 MySQL LIKE）
   |
   +-- ApplicationEventPublisher
          +-- AiReviewEventListener   } agentLlmExecutor 专用池
          +-- AiSafetyCheckListener   } -> LlmClient（熔断 + 重试 + 结构化输出）
                                            |
                                  3 x @Scheduled 对账任务
                                  （租约领取 / 漂移重建 / 脏集合刷写）
```

---

## 三、子模块逐项拆解

复杂度口径：**C3 = 含分布式语义或不可逆决策，C2 = 有真实设计但边界清楚，C1 = 样板与装配。**

### 3.1 接入与传输层

**`filter/`（2 文件 / 275 行）— XSS 前置净化 — C2**

- 职责：在所有业务代码之前中和 HTML 注入。
- 技术栈：`HtmlUtils.htmlEscape`、Servlet Filter、请求体包装器。
- 亮点：`XssConfig` 把过滤器注册为 `HIGHEST_PRECEDENCE`；`XssHttpServletRequestWrapper.sanitize`（第 200 行）是图中第 7 大枢纽（PageRank 0.0072），说明它真的全局生效而不是挂在个别接口上。`XssFilterTest` 9 例 + `XssWrapperTagsArrayTest` 2 例覆盖 `tags` 数组这类嵌套载荷。
- 复杂度来源：包装请求体要保证流可重复读，且不能破坏 multipart 上传。

**`security/`（1 文件 / 86 行）+ `util/JwtUtil`（104 行）— JWT 认证 — C2**

- 职责：令牌签发与解析、身份上下文注入。
- 技术栈：jjwt 0.12.6、BCrypt、`OncePerRequestFilter`（注册顺序 `HIGHEST_PRECEDENCE + 1`，刻意排在 XSS 之后）。
- 亮点：公开 GET 上做尽力而为的令牌解析，解析不到就让 `currentUserId` 属性缺失；`PostController.fillLikedByMe` 因此把"未登录"和"没点过赞"区分成 `null` 与 `false`，而不是粗暴归零。这是个容易被忽略的语义正确性细节。
- 短板：**admin 判定被实现了三套互不复用的私有版本**——`AdminController.checkAdmin`（第 161 行）、`AiLogController.isAdmin`（第 160 行）、以及 `PostController` 里两处直接内联的 `if (!"ADMIN".equals(role))`（第 40、53 行）。集中式 `@RequiresRole` 未做（已明确留下一轮），这是 P1-6 的实际形状。

**`config/RateLimitInterceptor.java`（168 行）— 限流 — C3**

- 职责：按客户端 IP 的 60 秒滑动窗口限流。
- 技术栈：Redis ZSET + Lua（`lua/sliding_window_rate_limit.lua`，9 行）。
- 亮点：判定与写入在一段 Lua 里原子完成，`ZREMRANGEBYSCORE` 先清窗口再 `ZCARD`，避开"读后写"竞态；score 用 `时间戳:随机数` 兜住同毫秒并发。被拒时在拒绝分支（第 114 行）打 `rate_limit_rejected_total{path}`（本轮新增），第 128 行手写 429 状态与响应体。
- 复杂度来源：这是热路径上唯一的外部依赖，Redis 不可用时的放行/拒绝策略必须是显式决策，不能靠异常冒泡。

**`dto/`（24 文件 / 555 行）— 统一契约 — C1**

- 职责：请求/响应外形与视图对象。
- 亮点：`ApiResponse` 的 `success` / `error` / `forbidden` / `notFound` / `successMessage` 五个工厂方法在主源码里被调用 105 次（其中 `controller/` 占 94 次），是图的枢纽顶端。本轮把 `traceId` 做成 `@JsonInclude(NON_NULL)` 且仅 5xx 携带，正常响应形状零变化——加字段的兼容性处理是教科书式的。
- 短板：`code` 目前镜像 HTTP 状态，没有独立的业务错误码目录（P1-2，未做）。

### 3.2 业务服务层

`service/`（15 文件 / 1 429 行）+ `service/impl/`（6 文件 / 982 行）。这一层里混装了三种性质不同的东西（业务命令、基础设施能力、纯 CRUD），是模块划分上最值得改进的一点。

**`impl/VibePostServiceImpl.java`（706 行 / 50 符号）— 帖子主服务 — C3**

- 职责：发帖、编辑、Fork、版本回滚、删除、置顶、审核、多路查询分发、事件投递。
- 技术栈：MyBatis-Plus `LambdaQueryWrapper`、9 处 `@Transactional`、`ApplicationEventPublisher`。
- 亮点一：Prompt Fork 与版本链是产品的差异化能力，`forkPrompt`（第 263 行）/ `getPromptVersions`（307）/ `restorePromptVersion`（329）/ `saveVersionSnapshot`（389）构成完整的模板谱系模型，同类论坛项目里不常见。
- 亮点二：事件投递的背压边界（第 604 行注释）—— `@Async` 提交发生在发布者线程内，因此线程池饱和时 `failClosedAtEnqueue`（第 655 行）必须在事务外写库。这个边界被提前想清楚了，而不是撞上问题再说。
- 客观短板：**全仓唯一的 `oversizedFiles` 热点**，14 个 `@Autowired` 字段，同时承担命令（写）与查询（读）两类职责；查询侧还留下一个 `getActivePosts(int,int)` / `getActivePosts(int,int,String)` / `getActivePostsLegacy()` 的近义方法组（第 403、408、685 行），调用方要靠读签名才知道该用哪个。它不该是一个类。

**`PostRankingService.java`（271 行）— 热度排序 — C3**

- 职责：Gravity Decay 热度榜计算与分页。
- 技术栈：Redis ZSET 全量重算 + MySQL 兜底。
- 亮点：`computeGravityScore`（第 177 行）为 `(赞×10 + 评×20 + 阅×1) / (age+2)^1.5`，权重与指数写在一个 `static` 纯函数里可直接单测，未来时间发帖有 `ageInHours < 0` 钳位。Redis 不可用时 `getFallbackHotRankPage`（第 214 行）走同一条公式，**两条路径不会给出互相矛盾的答案**——这是降级设计里最容易被偷工减料的地方。`onLike`（第 261 行）做增量 `ZINCRBY`，避免每次点赞触发全量重算。

**`SensitiveWordService.java`（232 行）— 敏感词 — C3**

- 职责：文本命中检测与词库热更新。
- 技术栈：双 Trie（DFA），`regularRoot` 与 `criticalRoot` 两棵分级树；Redis Pub/Sub `onMessage`（第 143 行）热重载。
- 亮点：`findLongestMatch`（第 117 行）+ `pickBest`（第 131 行）做最长匹配，避免"敏感"被"敏"提前截断；系统关键级词（第 188 行）与普通词分树存放，**分级靠数据结构本身而不是遍历后过滤**；词库变更无需重启。7 个单测。
- 复杂度来源：进程内可变状态 + 多实例广播一致性。

**`LikeCounterService.java`（197 行）— 点赞计数 — C3**

- 职责：原子点赞切换与计数读取。
- 技术栈：Lua（`lua/like_toggle.lua`，20 行）一次操作同时维护 `post:like:{id}` 集合、`dirty` 集合、`ranking` ZSET，返回 `SCARD`。
- 亮点：`@PostConstruct` 先 ping 探活决定 `redisAvailable`；Lua 不可用时 `likeViaMysql` / `unlikeViaMysql` 镜像同一套"再点即取消"语义（`likePost` 第 75 行注释明写"两种模式必须一致"）。**降级路径保持行为等价**，而不是降级成"只能点赞不能取消"。`isLiked` / `getLikeCount` 在 Redis 异常时各自兜底并告警，不让列表页 500。16 个单测，全仓第三重的测试类。

**`PostSearchService.java`（390 行）— 检索 — C2**

- 职责：ES 索引建立、读写、重建与降级。
- 亮点：`isAvailable()`（第 381 行）作为能力探测点存在，MySQL LIKE 路径是真实的一等公民而非临时补丁；`rebuildIndex` / `bulkIndex` 支持全量重建，`createIndexIfNotExists` 启动时自愈。
- 复杂度来源：索引与主库最终一致，需要任务或人工触发重建。

**其余服务 — C1～C2**

- `MessageNotificationService`（87 行）：Redis 未读计数 + 批量清除，12 个单测。
- `AiReviewDetailService`（136 行）：把评审 JSON 解析成前端可读结构，容错解析独立成 `parseResult`。
- `UserProfileSummaryService`（135 行）：聚合发帖/获赞/Fork 数/平均 AI 分。
- `SysMessageService.sendMessage` 是图中第 5 大枢纽（入度 6），被评审、对账、消息三处复用。
- `ChannelService` / `VibeTagService` / `VibeCommentService` / `SysUserService`：MyBatis-Plus `IService` 常规 CRUD。
- `DraftService`（43 行）：`ConcurrentHashMap` 内存草稿，**进程重启即丢失，未做持久化**。属于功能边界而非 bug，但文档未标注。
- `EmailService`（33 行）：三个方法只打日志，**没有接任何 SMTP**。"注册确认邮件""回帖通知""审核结果邮件"三处能力的实际完成度是：接口在，投递不在。

### 3.3 线程池与定时对账

**`config/AsyncConfig.java` — 双池隔离 — C2**

- `nexus-async-` 通用池与 `agentLlmExecutor` 专用池分开，LLM 排队不吃掉消息通知线程；两个池均挂 `MdcCopyingTaskDecorator`（本轮新增），使追踪号跨线程边界存活。
- 证据：`docs/research/async-pool-loadtest.md` 有 20 万请求饱和实测，12.1 万次背压拒绝，零崩溃。

**`task/`（3 文件 / 383 行）— 对账三件套 — C3**

- 职责：把"事件丢了"从一个事故变成一个会自愈的系统属性。
- `AiReviewReconcileTask`（212 行，cron `0 3/5 * * * ?`）：扫 `REVIEWING` 陈旧 / `FAILED` / `pending-llm` 三类并重投事件，另有预算耗尽退休。**顺序设计有讲究**（第 92-95 行）：backlog gauge 在任何健康门控之前刷新——"LLM 挂了的时候恰恰是这个数字必须被看见的时候"；`sweepBudgetExhaustedReviews` 也无条件跑，因为那是任何重试路径都不会再碰的死状态。
- `ai_review_pending_posts` 是快照 gauge 而非抓取时查库（第 68-72 行注释）：Prometheus 15 秒抓一次，用 `COUNT` 响应抓取等于让监控栈去压数据库。
- `DriftReconcileTask`：抽样比对 Redis 点赞集合与 MySQL `like_count`，**只在"DB 远大于 Redis"这一种丢失形态**才以 `vibe_post_like` 表为真相源重建（`drift-ratio:0.5` / `drift-abs:100` / `sample-size:200`）。Redis 领先 DB 的正常写后滞留被有意忽略——**知道什么不该修，比修得多更值钱。**
- `LikeSyncTask`：脏集合批量刷写，死锁时保留队列（实测日志可见 `1 synced, 1 failed, 1 left in queue`）。
- 三个任务分别 7 / 5 / 7 个单测，其中包含"重试预算耗尽后不再领取"这类难以靠肉眼发现的分支。

**`event/`（2 文件 / 75 行）— 消息解耦 — C1**

- `MessageEvent` + `MessageEventListener`，把站内信写入从请求线程摘出去。

### 3.4 AI Agent 管线（`agent/`，11 文件 / 1 648 行）

**这是整个项目技术含量最高、也最不像演示原型的部分。**

**`LlmClient.java`（414 行）— 供应商客户端 — C3**

- 职责：OpenAI 兼容调用的超时、重试、熔断、健康探针、指标。
- 技术栈：Spring `RestClient`、Micrometer `Timer` / `Counter` / `Gauge`、`AtomicInteger` + `AtomicLong` 手写熔断。
- 亮点一：熔断器手写而非引 Resilience4j（第 45-46、362-377 行），阈值与开路时长走配置（`failure-threshold:3` / `open-seconds:60`）。在这个规模下这是合理的减法，不是不会用库。
- 亮点二：**指标语义被显式区分并写进 Javadoc**（第 52-57 行）：计数器统计逻辑调用（一次 `withRetry` 出口），直方图统计供应商尝试（三次 11 秒超时就是三个 11 秒样本），"这样慢的供应商才会看起来慢，而不是看起来稀少"；健康探针两边都不计入。能想到这个区分并记录理由的人，是真的排障过。
- 亮点三：SLO 桶（第 79-83 行）对齐超时阶梯而非百分位直方图——默认 30s 超时，所以超过 30s 是超时不是慢响应。
- 亮点四：`responseFormat` 三态（`json_schema` / `json_object` / `none`，第 35 行）+ `thinkingDisabled`（第 41 行），为 DeepSeek V4 这类"默认思考导致 `content` 为空、`temperature` 被忽略"的模型留了开关；`Accept: application/json` 兜住 NVIDIA NIM 回 `octet-stream` 的坑（第 94-98 行注释）。这些是针对真实供应商差异的适配，只有实际跑过才会出现。
- 测试：`LlmClientMetricsTest` 3 例锁指标语义。

**`AiReviewService.java`（541 行）— 评审编排 — C3**

- 职责：代码块抽取、提示词构造、结构化评审、结果校验、降级与重试、评论落库。
- 技术栈：JSON Schema、`ObjectMapper`、双温度常量（评审 / 修复）。
- 核心亮点：**两级重试走两条不同路径**（第 305-331 行）。首次输出可解析但语义空洞，换强化提示词 `buildReinforcedSystemPrompt`；首次输出不可解析，换自纠提示词，把模型自己的坏输出加上解析器的**位置错误信息**回喂（`buildRepairUserContent`）。这是当前 LLM 结构化输出工程里相当成熟的做法——不假设"再问一次就好了"，而是按失败模式分型。
- 配套亮点：`isValidReviewResult` + `isSubstantive` + `PLACEHOLDER_PATTERN` + `VALID_SEVERITIES` 组成**语义级**有效性校验，不是"能 parse 就算成功"；二次仍无效则 `markPost(FAILED)` + 通知作者（第 333-347 行），"静默降级"被落实成具体行为：不写脏分数、不假装成功、状态进终态、人拿到消息。
- `supersedePreviousReviewComments`（第 489 行）：重评时隐藏旧 AI 评论，避免同一线程出现两个矛盾评分，历史全量保留在 `ai_review_log`。
- `buildContextExcerpt` + `estimateTokens`（第 245-270 行）：按 `AI_MAX_CONTEXT_TOKENS`（默认 12000）截断上下文，**成本上界是配置项而不是祈祷**。
- 测试：`AiReviewServiceTest` 15 例（按用例数排全仓第四，前三是 `PostControllerIntegrationTest` 29、`SysUserServiceTest` 17、`LikeCounterServiceTest` 16）。

**`AiSafetyCheckListener.java`（278 行）— 语义安全检测 — C3**

- 职责：四分类（safe / prompt_injection / harmful / spam）与分级处置。
- 亮点：**fail-closed 是这个项目最正确的一条安全决策**（ADR-0004）。LLM 不可用或输出不可解析时，帖子进 `PENDING_REVIEW` 人工队列并打 `pending-llm` 标记，由对账任务在 LLM 恢复后自动重跑；监听器自身"never throws"。`CONTEXT.md` 把这条策略与 `Degraded` 状态明确划成两层（进程自述 vs 写入策略），术语边界干净。10 个单测。

**`PromptIsolation.java`（61 行）— 注入面隔离 — C2**

- 职责：LLM 提示词的数据区边界防伪造。
- 亮点：两层防御。每请求用 `SecureRandom` 生成 nonce 定界符（用户内容无法预测），同时用正则把内容里所有形如 `---BEGIN` / `---END` 的行加 `[neutralized]` 后缀（第 50-54 行），**即使 nonce 泄漏也伪造不出边界**。5 个单测。这 61 行是全站安全密度最高的代码，且有一篇配套技术博客。

**`JsonRepairUtil.java`（245 行）— 容错解析 — C3**

- 职责：把 LLM 输出的畸形 JSON 修成可解析。
- 亮点：**纯函数、无 I/O、无重试**（第 14 行注释）。代码块围栏剥离、尾逗号清理、**max-token 截断后用括号感知栈走完补齐未闭合括号与字符串**，使被截断的回答仍能解析成对象。返回 `ParseResult(node, error)` 而非抛异常，因此 `AiReviewService` 的"可解析但空洞"与"不可解析"分型才成为可能。10 个单测。

**`AiReviewEventListener.java`（159 行）— 租约领取 — C3**

- 职责：异步消费评审事件，保证崩溃/并发下不重复处理。
- 技术栈：`@Async` 于 `agentLlmExecutor` 专用池 + 条件 UPDATE 租约（`review_lock_until` / `review_owner` / `review_attempts`）。
- 亮点：领取是一条 SQL：`WHERE (review_lock_until IS NULL OR review_lock_until < NOW()) AND review_attempts < #{maxAttempts}`。**用 DB 时钟判过期，本地时钟漂移的节点无法提前重领**（ADR-0005）。预算耗尽时终态通知"由消耗掉最后一次预算的那次尝试发出"，避开"谁该发通知"这个经典重复投递问题。4 + 6 个单测（含 `ReviewLeaseClaimTest` 走真实 H2）。

**`LlmHealthCache.java`（42 行）— 共享探针缓存 — C2**

- 亮点：5 分钟缓存包住 `isHealthy()`，因为探针本身要付一次完整连接超时；发帖安全闸与对账任务共用这一个缓存，**一次探测同时门控两条路径**。文件很小，判断力密度很高。

### 3.5 持久层

**`mapper/VibePostMapper.java`（311 行，全仓最重的 SQL 载体）— C2**

- 技术栈：MyBatis-Plus 3.5.9 注解式 mapper（实测 41 条 `@Select` / `@Update` / `@Insert` / `@Delete`）+ 4 处 `<script>` 动态 SQL；仅 `VibePostTagMapper.insertBatch` 走 XML。
- 亮点：作者昵称与频道名 JOIN 进同一结果（`u.nickname as authorName, c.name as categoryName`）避免 N+1；计数增减用 `GREATEST(like_count + #{delta}, 0)` / `GREATEST(comment_count - 1, 0)`（第 142-146 行）在**数据库侧**钳住负数，不在 Java 侧兜——并发下这是对的。租约条件更新也落在此处。
- **静态分析边界（如实报告）**：CodeCompass 的 main-flow tour 最后一跳标 `[Static Analysis Break: Dynamic/RPC Dispatch]`，`trace_call_chain` 从接口方法 `createPost` 出发只能拿到 1 跳。这不是代码缺陷，是 MyBatis mapper 动态代理的固有性质——任何静态工具（包括本报告的调用图计数）在 service 到 mapper 这条边上都是断的。**读图时不能把"入度 0"当死代码。**

**`mapper/`（8 文件 / 400 行）+ `entity/`（8 文件 / 211 行）— C1**

- 贫血 DO + Lombok；`MyMetaObjectHandler` 自动填充时间戳；`MybatisPlusConfig` 配分页插件。
- ADR-0001 把 `Bbs*` 全量改名 `Vibe*`，命名统一由 `CONTEXT.md` 的 `_Avoid_` 列表守住。

**`config/RedisConfig.java`（147 行）— C2**

- 亮点：**自定义 `CacheErrorHandler`**，缓存读写异常不冒泡成 500，Redis 挂掉时读穿透、写丢弃。这是很多项目会漏掉的一环。

### 3.6 可观测性（本轮新增）

**日志（T1）— C2**

- `logback-spring.xml`（39 行）+ `logback/prod-file-appender.xml`（43 行）：prod 用 `LogstashEncoder` 写 JSON 到命名卷 `app-logs`，按大小+时间滚动（单件 100MB / 7 天 / `totalSizeCap` 1GB），外层 `AsyncAppender`（`queueSize=512`、`discardingThreshold=0` 即不丢日志）；dev 保留控制台 pattern 并注入 `%X{traceId}`；stdout 同时留精简 pattern 应急。`logstash-logback-encoder` 升至 8.0 以匹配 logback 1.5.11。
- 演练实测：一次事件一行 JSON 落卷，`traceId=e52d0a8884779595` 从响应头走到磁盘。
- 测试：`LogbackStructuredOutputTest` 3 例直接渲染 JSON 行校验字段与 MDC。

**指标（T2 / T3）— C2**

- `micrometer-registry-prometheus` 由 Spring BOM 管版本；prod 暴露 `health,info,prometheus`。
- **删掉了 `SystemMetricsAutoConfiguration` 的 exclude，并在 `temurin:21-jre` 容器里验证成立**：当初那个 exclude 是被 CI 的 `-XX:-UseContainerSupport` 误导出来的，根因在 runner 不在 cgroup V2。
- **指标断言按运行环境分层**：单测只断言 `jvm_*`（不依赖容器支撑），OS 资源指标交给 prod 演练，没有把 runner 崩溃重新引回测试。`ActuatorMetricsTest` 2 例是全仓第一条触达 actuator 的测试。
- 业务埋点 7 个：`llm_chat_completions_total{outcome}`、`llm_chat_completion_duration_seconds`、`llm_circuit_breaker_open`、`rate_limit_rejected_total{path}`、`ai_review_pending_posts`、`ai_review_reconcile_repairs_total{kind}`、`ai_review_lease_attempts_exhausted_total`，全部手写 `MeterRegistry`，未引 `@Timed` AOP。
- 监控栈三容器（prometheus / grafana / alert-bridge）全在 `monitoring` profile 下，**不映射宿主端口**；实测不开 profile 是 6 个服务、开是 9 个，公网面仍只有 nginx。

**告警（T3）— C2**

- 4 条规则（`grafana/provisioning/alerting/rules.yaml` 202 行）：熔断打开 5m=critical、评审积压 >10 持续 15m=warning、5xx 比率 >1% 5m=critical、限流拒绝 5m 速率 >3 倍前 1h 基线=warning。
- `alert_bridge.py`（107 行，83 行非注释）：收 Grafana webhook 转飞书自定义机器人报文（`timestamp` + HMAC-SHA256 `sign`），密钥只进 `.env`。5 个 Python 单测用固定 key + 固定 timestamp 断言签名与请求体；缺 webhook 配置时返回 502 并点名缺失项而不是静默丢弃。
- 边界（报告已如实写明）：规则是拉模式，应用整体消失时 4 条规则进 `no data` 且 `noDataState=OK`，**一声不响**；能发现"进程不见了"的是 Docker healthcheck 与 nginx 两层，不是 Grafana。`for: 5m/15m` 意味着最短 5 分钟延迟，瞬时抖动不吵人是有意的。

**健康语义（T4，ADR-0007）— C3**

- 职责：把"还能不能服务"与"哪个依赖坏了"拆成两层。
- 实现：`management.health.redis.enabled=false` / `elasticsearch.enabled=false` 关掉 starter 自动注册的同名指示器，改由 `config/health/`（4 文件 / 158 行）注册映射到自定义 `Status.DEGRADED` 的 `redis` / `elasticsearch` / `llm` 三个指示器；`db` 保持真实 DOWN。`status.order` 把 `degraded` 排在 `up` 之前但**显式映射 HTTP 200**，`show-details: never`，另开 `group.deps` 给细节。
- 亮点：`DependencyHealth.java`（第 8-14 行）讲清了为什么 Boot 自带的四个状态不够用——**没有一个词能说"依赖没了但站点还能用"，而 `DOWN` 会让 Docker 杀掉容器**。演练用真实故障验证：LLM 开路 60 秒期间 `health=200 status=DEGRADED`、`restarts=0`。
- `LlmHealthIndicator` 复用 `LlmHealthCache`，**健康检查不污染熔断计数**。
- nginx 侧从"恰好安全"改成显式拒绝：`location /actuator/ { return 404; }`（第 92 行），仅保留 `location = /actuator/health`（第 77 行）。公网探测 `prometheus` / `health/deps` / `env` 全 404。
- 测试：`LlmHealthIndicatorTest` 3 例、`HealthSemanticsIntegrationTest` 3 例。
- 这条是本轮含金量最高的一张票：它改变的是系统的自我表述语义，不是加个面板。

**追踪号（T6）— C2**

- `TraceIdFilter`（order = `HIGHEST_PRECEDENCE - 1`，排在 XSS/JWT 之前）生成 16-hex 写 MDC 并回写 `X-Trace-Id`；外部传入值仅在 `campus.security.trust-forwarded-headers=true` 时沿用。
- `MdcCopyingTaskDecorator` 覆盖两个线程池；`TraceIds.runAsJob` 让三个 `@Scheduled` 任务每次执行拿到独立 runId。实测日志里 `[AI-RECONCILE]`、`[DRIFT]`、`[LIKE-SYNC]` 每次运行都带不同追踪号，**这是定时任务排障最缺的东西**。
- 5xx 响应体带 `traceId`；前端 `shortTraceId()` 从响应头或响应体取值，错误 toast 显示前 8 位（`服务暂时不可用（追踪号 a0638811）`）。**用户报障时手里有一个能查的编号**，这条贯通了从浏览器到磁盘的整条链。
- 测试：`TraceIdPropagationTest` 用 `ListAppender` 断言一次含代码块发帖的同步请求与异步监听器共享同一追踪号（2 例），另有 `TraceIdFilterTest` 3 例、`TraceIdsTest` 4 例。

**契约对齐（T7）— C1**

- `frontend/src/api/client.ts` 的 `ApiResponse<T>` 改成后端真实形状 `{ code, message?, data }`，判成功用 `code` 派生；后端不加 `success` 字段（全仓无人读它）。**纯类型修正，不扩契约面**，克制得对。`ResponseContractTest` 3 例锁形状与"traceId 仅出现在 5xx"。

### 3.7 启动与种子治理（ADR-0008）— C2

`config/DataPreloader` + `config/BootstrapAdminInitializer` + `config/DemoContentSeeder`。**看似简单，但这是"能不能交给别人用"的分水岭。**

- `DataPreloader`：prod（`DEMO_SEED_ENABLED=false`）不再插 7 个样例账号，只确保功能性 `AiAgent(999)`；开 seeding 却没给密码维持 fail-fast。
- `BootstrapAdminInitializer`：`BOOTSTRAP_ADMIN_PASSWORD` 非空**且库中无 ADMIN** 时创建 `admin`，成功打一条一次性 WARN；已有 ADMIN 幂等跳过。6 个单测覆盖三种分支。
- `docker/mysql/init.sql` 从"schema + 样例内容"瘦身成 **schema + `vibe_channel` + `vibe_tag` 两张参考表**（171 行），样例 `vibe_post` / `vibe_comment` / `sys_message` / `ai_review_log` 移到 `DemoContentSeeder`（`resources/db/mysql/demo-content.sql` 79 行），同受门控、insert-only。
- 为什么重要：原来 prod 首启会出现**引用不存在作者的孤儿内容**。演练的 `first-install-is-empty-and-administrable` 这步专门断言"0 篇帖、7 个频道、引导管理员可登录为 ADMIN"。**一个真实的空白生产环境该怎么出生，这个项目答了。**
- 测试：`DataPreloaderSeedGateTest` 3 例、`DemoContentSeederTest` 5 例。

### 3.8 部署与演练基础设施

**后端 `Dockerfile`（多阶段）— C1**

- `maven:3.9-eclipse-temurin-21` 构建，`temurin:21-jre` 运行，装 `curl` 供健康检查，`HEALTHCHECK --interval=30s --start-period=60s --retries=3` 打 `/actuator/health`，`JAVA_OPTS` 空默认（GC 参数留给运行时）。
- 客观短板：**两个镜像都没有 `USER` 指令，容器仍以 root 运行**（P1-5，明确留下一轮）。

**`docker-compose.yml`（9 服务）— C1**

- 实测：不开 profile 6 个服务（db / redis / elasticsearch / ollama / app / web），开 `monitoring` 9 个；**唯一宿主端口映射是 `web` 的 `${WEB_PORT:-8080}:80`**；9 个服务全部配 `json-file` 日志上限（防日志写满宿主盘）；8 个命名卷（含 `app-logs`）。
- 短板：DB / Redis / ES 无副本、无备份任务、无滚动升级编排；`nginx.conf` 只 `listen 80`，**栈内无 TLS 终止**。

**`docker/nginx/nginx.conf`（117 行）— C1**

- 安全响应头（含 `Permissions-Policy`）、静态资源长缓存、`client_max_body_size 8m`、隐藏文件拒绝、SPA fallback、actuator 显式 404。

**`docker/mysql/` — C2**

- `init.sql` 171 行 + 3 个编号迁移（0005 用户邮箱、0006 AI 排序索引、0007 评审租约）+ 6 个压测 SQL（`seed_bench_data.sql` 118 行造数、`run_explain*` 前后对照 EXPLAIN、索引 DDL）。
- 亮点：**迁移有编号、可回放、且与 ADR / 研究文档一一对应**（0006 对应 AI 排序索引研究，0007 对应租约 ADR）。这不是随手 `ALTER`，是有 schema 演进纪律。但**没有引入 Flyway / Liquibase**，迁移仍靠人工按序执行，属于可交付但不够自动化的中间态。

**`benchmark/observability/`：`drill.ps1` 626 行 + `mock_llm.py` 53 行 + `docker-compose.drill.yml` 69 行 — C3**

- 职责：用一次性死端口 LLM 桩制造真实故障，验证埋点、降级、自愈与公网面。
- 亮点：**独立 compose project `-p nexus-drill` + 独立卷**，能与开发者正在跑的 `nexus-vibe` 栈并存，结束时 `down -v` 只清自己的卷；`-SkipBuild` / `-Keep` 开关；每步原始回答落 `evidence/drill-<时间戳>.log`（已 gitignore），汇总落 `-summary.md`，退出码即结论。
- 方法论上两条值得单独说：一是**区分"docker CLI 自己崩了"与"断言没过"**，前者退避重试最多 3 次、后者立刻红，否则演练结论取决于宿主机当天运气；二是报告明说"这一节里只有第 1 条是产品 bug"，为拿到可信的 16/16 演练**真跑了 10 次**（3 次全绿、4 次主动中断、2 次脚本自身错、1 次宿主 CLI 崩溃）。**这种自我审计是演练报告最稀缺的品质。**
- 短板：`drill.ps1` 是 PowerShell，**CI 的 ubuntu runner 上用不了，演练进不了 CI**。这是当前验证体系最大的空洞。
- 另有 `benchmark/jmeter/ai-review-loadtest.jmx`，对应异步池压测研究。

**`.github/workflows/maven.yml`（3 job）— C1**

- `backend`（compile + test）、`frontend`（npm ci + lint + build）在 push master 与 PR 到 master 上跑；`docker` 双镜像构建仅在 push master 且前两个 job 全绿时执行。
- 短板：**只有构建与测试，没有部署 job**；`pom.xml` 的 `java.version` 写 18、CI 用 JDK 18、运行镜像是 `temurin:21-jre`，三处版本口径未统一。

### 3.9 前端（`frontend/`，6 211 行 / 49 文件）

**技术栈**：React 19 + Vite + TypeScript + Tailwind + TanStack Query 5 + Zustand + motion + react-markdown + react-syntax-highlighter + lucide-react；lint 用 oxlint。

**`api/`（2 文件 / 128 行）— 传输层 — C2**

- `client.ts`（110 行）承担：Bearer 注入、**单飞令牌刷新**（`refreshPromise` 让并发 401 只打一次 `/auth/refresh`，成功后原请求带 `_retried` 标记重放一次）、登录与刷新类 401 不进循环、5xx 统一 toast 并附追踪号短编号。
- 亮点：31 个 `useQuery` / `useMutation` 调用点分布在 pages 与 components，服务端状态与客户端状态分离干净；`staleTime: 5min`、`refetchOnWindowFocus: false` 是有意识的取值而非默认。
- 客观短板：**没有集中式 API 定义层**。`api/` 只有 `client.ts` 与 `post.ts`，实测 18 个文件直接 import `apiClient`、34 处 `apiClient.*` 散落在页面里，端点 URL 是字符串字面量，改路由靠全文搜索。

**`pages/`（17 文件 / 4 245 行）+ `components/`（22 文件 / 1 854 行）— C2**

- 结构：17 条路由全部 `lazy()` + `Suspense`；`MainLayout` 与 `AdminLayout` 双布局，`AuthGuard`（5 条需登录路由）与 `AdminRouteGuard`（3 条管理路由）分层守；`ErrorBoundary` 包在最内层。
- 实测构建产物：`dist/assets` 共 34 个 JS chunk，其中 **17 个页面各自一个 chunk**（`HomePage` / `DashboardPage` / `AgentLogsPage` / `UserProfilePage` / `CreatePostPage` / `PostDetailPage` 等）——**代码分割真的生效了**，不是配了 `lazy` 结果仍全打进一个包。最重的 `PostDetailPage` 151.6 kB（gzip 46.9 kB，因为吃进了 markdown 渲染与语法高亮），入口 `index` 445.3 kB（gzip 142.5 kB）。
- 亮点组件：`AiReviewTerminal.tsx`（243 行，把 AI 评审结果渲染成终端视图，呼应产品定位）、`PromptVersionPanel.tsx`（157 行，Prompt 版本谱系与回滚）、`CommandPalette.tsx`（278 行）、`PostCard.tsx`（210 行）、`CodeBlock.tsx`（110 行）。
- 主题：`themeStore` 初值读 `prefers-color-scheme`，`documentElement.classList.toggle('dark')`，**尊重系统偏好的暗色切换**。
- 状态：`authStore` / `themeStore` 用 `persist` 中间件落 localStorage 并 `partialize` 只存必要字段；`toastStore` 不持久化。三个 store 共 95 行，很克制。
- 客观短板：CodeCompass 判出的 4 个超长方法**全在前端**（`CreatePostPage` 656 行、`PostDetailPage` 641、`CommandPalette` 278、`EditPostPage` 275）；`PostDetailPage.tsx:143` 有一条 `exhaustive-deps` lint 警告未清（不阻断构建）；**前端零测试文件**（无 vitest / jest / Playwright）。

### 3.10 文档与工程资产 — 载体 C1，价值 C3

- **8 篇 ADR**，每篇都带被否方案：0007 否掉"聚合降级进总状态""只靠指标"；0008 否掉"保留随机密码幽灵账号""首个注册者即管理员""手工 SQL 造管理员"；0004-0006 记录 fail-closed、租约、模型选型。**ADR 的价值在于记录为什么不做什么，这里做到了。** 短板是 0001-0005 与 0007-0008 都只有 3-9 行，是决策便签而非完整 ADR，缺"后果"与"状态"字段。
- **7 篇研究文档**，其中 3 篇结论是负向的：深分页延迟关联退化 40% 已回滚、AI 排序索引无过滤时负优化、cgroup exclude 属误诊。另有 1 篇技术博客（结构化输出与注入防御实战）。**只写成功案例的项目是宣传。**
- `CONTEXT.md` 领域词汇表 **20 个术语**，每个带 `_Avoid_` 反义词列表（`Review Lease` 的 `_Avoid_: Distributed Lock, Mutex`；`Degraded` 的 `_Avoid_: Unhealthy, Partially Down`；`Reconciliation` 的 `_Avoid_: Retry Job, Cleanup Task`）。**通用语言被当成一等工程资产维护**，这在个人项目里极罕见。
- 2 个工单集：`p0-optimization.md` 70 行、`production-readiness.md` 278 行（每票 Scope / Acceptance / Files + 落地状态 + 防回归的"未受影响项"清单）。
- 短板：README 徽章 `tests-284` 与实测一致（正确），但 `Java-18` 徽章与运行时 JDK 21 不一致；`target/surefire-reports/` 残留 2 个已删除诊断测试的旧 XML（`TmpDiagTest` / `TmpAutoConfigReportTest`），会让"数报告得测试数"这类统计得出 286 的错误结论——**本报告差点被它骗过去**。

---

## 四、按复杂度的真实排序

CodeCompass `scan` 的五个桶，加上我的解读：

| 桶 | 实测 | 解读 |
|---|---|---|
| `oversizedFiles` | 仅 1 条：`VibePostServiceImpl` 第 41-706 行 / 50 符号 | 唯一真正的后端结构性债：读写职责混合 + 14 个字段注入 |
| `oversized`（方法 150 行起） | 4 条，全部在前端 | **后端无一超长方法**，方法级健康 |
| `hubs`（PageRank） | 前二 `ApiResponse.error` 0.0189（入度 22）、`ApiResponse.success` 0.0177（入度 39），第三 `TraceIds.current` 0.0170 | 全局枢纽是契约工厂而非某个 Service，**没有上帝服务**；追踪号工具排第三说明它真铺进了横切面 |
| `deepChains` | 66 条，最深 4 跳（`DemoShowcaseController.burstLike` 第 63 行起：`likePost` - `executeLikeToggle` - `likeViaMysql` - `insertPostLike`） | 链路不深，没有出现服务套服务的层间穿透 |
| `orphanedPublic` | 527 条，样本全落在 `mock_llm.py` 的 `MockLlmHandler*`、`alert_bridge.py` 的 `Handler*` / `build_body` | **反射与框架驱动的预期结果，不是死代码**。该桶在本仓库信噪比低，不可作为清理依据 |

`domain_radar` 的外向依赖清单也印证了技术栈广度：持久层 12 个实体/Mapper，`VibePostTagMapper.insertBatch` 是唯一 XML 边；`topApis` 覆盖频道、消息、标签、认证、管理端 10 条主要路由。

---

## 五、完成度：已证明的、未验证的、明确未做的

**已在真实环境证明（非断言，来自 16/16 全绿的演练运行 `20260913-141708`）**

1. 空白 DB 首启：0 篇帖、7 个频道、引导管理员可登录为 ADMIN。
2. LLM 死端口：3 篇帖 parked、`llm_circuit_breaker_open=1`、`/actuator/health` 返回 200 且状态 `DEGRADED`、容器 `restarts=0` 未被判 unhealthy。
3. LLM 恢复：对账重跑，`repairs=6`、熔断归 0。
4. 限流打满：13 个请求拒 3 次，`rate_limit_rejected_total=3`。
5. 公网探测 actuator：除 `/actuator/health` 返回 200 外，`prometheus` / `health/deps` / `env` 全 404。
6. 追踪号贯通：同一 id 出现在响应头、浏览器、磁盘 JSON 日志三处。
7. prod 容器无需 exclude 即有 `system_cpu_usage` / `disk_free_bytes` / `process_uptime`，JVM 不崩。
8. 4 条告警规则被引擎装载、contact point 注册在 `http://alert-bridge:8080/notify`；告警确实到得了桥，桥缺配置时会喊。

**同一份代码的构建验证（本次实测）**

- `mvn test`：`classes=40 tests=284 failures=0 errors=0 skipped=0`。
- `npm run build`：5.17s 通过；`npm run lint`：1 条 `exhaustive-deps` 警告，0 错误。
- `docker compose config --services`：6 / 9（profile 前后）。
- PR #2 状态：`OPEN` / `isDraft: true` / `mergeable: MERGEABLE`，未合并、未 push master。

**演练自己承认未覆盖的（其报告第五节口径）**

- 真实飞书送达未验（缺可用 webhook，需人工确认）。
- 规则真的进 pending/firing 未验（5m/15m 持续条件超出演练窗口）。
- Grafana 面板渲染未看（两个 dashboard JSON 画不画得出图要人打开确认）。
- `ai_review_lease_attempts_exhausted_total` 未演练（要把预算耗到 5 次才出现）。
- 日志滚动的量化上限未验证（appender 图由单测断言，未真写满 1GB）。
- 磁盘满 / OOM / MySQL 主从抖动不在本轮；前端短编号显示属人工验收。

**明确未做（已记账于 `production-readiness.md` 末节）**

错误码目录与 `BusinessException`（P1-2）、配置校验与 dev 默认值（P1-4）、**容器非 root**（P1-5）、集中式角色拦截（P1-6）、前端崩溃上报（P1-7）、cAdvisor / Loki / OpenTelemetry / Alertmanager，以及全部 P2。

**本报告补记的、评估文档未列出的缺口**

1. `EmailService` 是空实现，三处"邮件"能力实际不可用。
2. `DraftService` 内存草稿，重启即丢。
3. 演练脚本不进 CI（PowerShell 与 ubuntu runner 不匹配）。
4. nginx 无 TLS 终止，`listen 80` 明文。
5. 前端零测试。
6. JDK 版本口径三处不一致（18 / 18 / 21）。
7. schema 迁移无工具托管（无 Flyway / Liquibase）。

---

## 六、结论

**代码结构**：分层清晰且由 AST 投票证实（返回包装 50/56、无共享基类 115/115、包布局 112/115），依赖单向，无上帝服务，全局耦合点收敛在响应契约上。唯一的结构性债务是 `VibePostServiceImpl` 706 行与 4 个前端超长组件，**后端方法级别零超长**。

**模块划分**：`agent` / `task` / `config.health` 三个边界划得干净，`TraceIds` 进 `util`、`MdcCopyingTaskDecorator` 进 `config` 都顺着既有布局。**真正该改的是 `service/` 内部**：它把"业务命令"、"基础设施能力（限流/计数/检索/敏感词/排序）"、"纯 CRUD"三类东西混在同一包名下，按能力再切一层会比拆那个 706 行大类收益更大。

**技术实现**：真实、有判断力、有减法意识。含金量最高的五处是：租约式领取（DB 时钟判过期）、fail-closed 安全闸、指标语义上"逻辑调用 vs 供应商尝试"的区分、对账任务"只修会丢的那种"、以及 `DEGRADED` 与 `DOWN` 的语义分离。每处都能说出**为什么不选另一种**，并且落进了 ADR 的被否方案。

**完成度**：核心内容链路（发帖 - AI 评审 - 安全检测 - 人工审核 - 计数/排序/检索 - 前端呈现）的功能与自愈均已完成，并经真实故障验证。P0 可观测性全量 + P1 两项已闭环；P1 余下与 P2 未启动，**但都被显式记账，不是遗漏**。

**定性判断：实打实的项目，不是夸大其词的项目。** 最有力的证据是它自己写了一份把 P0/P1/P2 逐级列出、并承认"这个 16/16 差点因为脚本自己的 bug 而不可信"的评估与演练报告。**想骗人的项目不会花 626 行写一个专门来抓自己假绿的脚本。**

**边界要划清**：它是"一个人或极小团队可长期自托管运维的产品级项目"，不是"可直接进企业采购清单的交付物"。差的那部分是权限体系、契约治理、无外部依赖的日志/链路聚合、以及部署 / 备份 / TLS 运维面。**这个差距是范围问题，不是质量问题。**
