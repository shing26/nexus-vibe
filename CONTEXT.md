# Nexus-Vibe

A Vibe Coding & AI 开发者交流论坛，由赛博朋克校园论坛 Nexus-Campus 改造而来。
核心定位是内容沉淀、交互讨论与 AI 辅助编程实践分享。

## Language

**VibePost**:
论坛的核心内容单元，代表一篇包含代码、Prompt 或 AI 实践经验的帖子。
_Avoid_: BbsPost, Post, Article

**Channel**:
帖子的分类归属，每个 Channel 有唯一的 slug 标识和数字 id。Slug 用于前端路由，id 用于后端 API 查询。
包括：社区公告(announcements)、Prompt 工坊(prompts)、作品展示(showcase)、Agent 实战(agents)、Vibe Coding 经验(vibe-coding)、代码急诊室(debug)、资源聚合(resources)。
_Avoid_: BbsCategory, Category, Board

**ReviewEvent**:
用户发布 VibePost 时触发的异步事件，驱动 AI Agent 进行 Code Review 或 Prompt 评估。
_Avoid_: ReviewTask, AuditEvent

**AiAgent**:
系统内置的 AI 机器人角色，有实体账号（role=AI_AGENT），可自动回帖、执行 Code Review、进行语义安全检测。
_Avoid_: Bot, AutoReviewer

**Code Review Agent**:
对带 CodeSnippet 的 VibePost 执行异步 LLM 评审的 Agent，产出结构化评分与建议并自动回帖。评审结果必须通过语义校验才算有效。
_Avoid_: ReviewBot

**Safety Check Agent**:
对已发布 VibePost 做语义安全分类（safe / prompt_injection / harmful / spam）的 Agent。
_Avoid_: ContentFilter, Moderator

**Review Validity（评审结果有效性）**:
Code Review Agent 的结构化结果必须通过语义校验——severity 合法、quality/suggestions 内容充实且非占位词——才写入评分；无效结果用强化提示词重试一次，仍无效则标记 FAILED 静默降级。
_Avoid_: Format Check, Output Validation

**Fail-closed**:
Safety Check Agent 在 LLM 不可用或输出无法解析时的保守策略：帖子进入人工审核队列（PENDING_REVIEW）并记录 pending-llm 标记，LLM 恢复后由对账任务自动重跑。
_Avoid_: Fail-safe, Fallback

**Degraded（降级可服务）**:
依赖（Redis / Elasticsearch / LLM）不可用但主链路仍能服务用户时的健康状态：HTTP 200，容器不被判 unhealthy，解释只在 /actuator/health/deps 里给（ADR-0007）。与 Fail-closed 分属两层——Degraded 是进程对自身的陈述，Fail-closed 是安全路径写入的策略。
_Avoid_: Unhealthy, Partially Down, Degradation

**Servable（可服务性）**:
顶层 /actuator/health 唯一回答的问题：还能不能服务。只由真实存储决定——db DOWN 才算不可服务，其余依赖最多把总状态压到 Degraded。
对外它是一个具名组 `servable`（只含 db）：公网 `/actuator/health` 由 nginx 转到它，容器 HEALTHCHECK 仍问聚合文档，同一个状态码不再同时回答两个问题（见 ADR-0007 修订段）。
_Avoid_: Healthy, Liveness, Readiness

**Reconciliation（对账任务）**:
每 5 分钟扫描卡在 REVIEWING、FAILED 或 pending-llm 状态的帖子并重新触发 Agent 事件的定时任务；重跑前先用轻量探针确认 LLM 健康。
_Avoid_: Retry Job, Cleanup Task

**Review Lease（评审租约）**:
帖子领取评审任务的原子凭据（review_lock_until/owner/attempts 列）。领取即一次条件 UPDATE，锁存续期内其他实例或重复事件不可二次处理；attempts 耗尽进入终态并通知作者。
_Avoid_: Distributed Lock, Mutex

**Drift Reconciliation（点赞漂移对账）**:
比对 Redis 点赞集合与 MySQL like_count 的定时抽样校验。只有"DB 远大于 Redis"的丢失形态才触发以 vibe_post_like 表为真相源的重放重建；正常的写后滞留（Redis 领先 DB）不处理。
_Avoid_: Cache Sync, Count Fix

**CodeSnippet**:
VibePost 中提取出的可执行或可审查的代码片段（以 ` 标记提取），以 JSON 数组形式存储在 ibe_post.code_snippets 字段。
_Avoid_: CodeBlock, Attachment

**VibeComment**:
对 VibePost 的回复，包含普通用户评论和 AI Agent 自动生成的审查评论。
_Avoid_: BbsComment, Reply

**Bootstrap Admin（引导管理员）**:
生产（DEMO_SEED_ENABLED=false）下由 BOOTSTRAP_ADMIN_PASSWORD 一次性创建的 `admin` 账号，仅当库中还没有任何 ADMIN。创建后就是普通账号：重启不重置、不重建，密码需尽快轮换（ADR-0008）。
_Avoid_: Default Admin, Superuser, Root Account

**Trace ID（追踪号）**:
一次请求（或一次定时任务运行）的 16-hex 标识，随 MDC 跨过同步/异步边界，出现在日志、`X-Trace-Id` 响应头与 5xx 响应体里；前端错误提示展示前 8 位，供用户报障时复述。
**生成**形状是 16-hex；**接收**外部值时只接受 `^[0-9a-zA-Z-]{1,64}$`，且默认不接收：要 `campus.trace.trust-inbound-header=true` 才沿用外部值，那是独立于 `trust-forwarded-headers`（客户端 IP 真伪）的另一项信任决定，公网 nginx 还会先把该头清空。四个 `@Scheduled` 任务每次运行都拥有一个。
_Avoid_: Request ID, Correlation Id, Span

**Showcase Post（展示帖）**:
生产部署（DEMO_SEED_ENABLED=false）下首页指向的唯一一篇已带 AI 评审的帖子，让未登录访客先读到真实评审，再决定要不要注册。它写入的评审是一条**录制**：脚本真实走一遍发帖，把管线写回的评论与 ai_review_log 行逐字复制进 `src/main/resources/showcase/`，评分与严重度从那份 JSON 里解析出来，不是手写文案。因此它证明的是"管线当时输出了什么"，不证明模型此刻可达（ADR-0010）。
与 demo seed 是两件事：它只写一篇，作者必须是库里已存在的账号，且没有配置 `campus.showcase.post-id` 时什么都不做。
_Avoid_: Showcase Channel, Demo Post, Sample Content, Fixture Post

## Channels

- **announcements**: 系统公告、更新日志（仅管理员发帖）
- **prompts**: System Prompt 设计、Chain-of-Thought、少样本技巧
- **showcase**: Vibe Coding 成品展示：网页、工具、自动化流程
- **agents**: Multi-Agent、工具调用、OpenClaw/Codex 使用心得
- **vibe-coding**: 上下文控制、幻觉治理、架构设计的纯经验讨论
- **debug**: 贴报错上下文，社区或 AI Agent 协助分析
- **resources**: 工具链推荐、API 评测、教程链接

## Frontend Architecture

**Layouts**:
- MainLayout: Navbar（Logo + 频道导航 + 搜索框 + 用户菜单）+ Outlet + Footer，覆盖用户端所有页面
- AdminLayout: 左侧边栏 + 右侧内容区，不含 Navbar/Footer，用于管理后台

**State**:
- TanStack Query: 服务端状态（帖子、评论、频道列表、缓存/自动重新验证/乐观更新）
- Zustand: 客户端状态（token、当前用户、UI 开关）

**Routes**:
- / → 首页
- /channel/:slug → 频道帖子列表
- /post/:id → 帖子详情
- /post/new → 发帖
- /post/:id/edit → 编辑帖子（预留）
- /search?q=xxx → 搜索结果
- /login, /register → 认证
- /user/:id, /user/settings → 用户
- /admin/audit, /admin/dashboard → 管理

## AI Agent Review

- 触发：仅带代码块（`）的帖子
- 内容：结构化四段——评分、代码质量、安全隐患、优化建议
- 展示：自动回帖（全文）+ 帖子详情页评分徽章

## LLM 语义安全审查

四分类 + 按类处理：

| 分类 | 处理 |
|------|------|
| Prompt 注入 | 帖子可见，标记 PENDING_REVIEW，入审核队列 |
| 不当内容 | 自动隐藏（status=3），系统消息通知作者 |
| 垃圾广告 | 自动隐藏（status=3），不通知 |
| 安全 | 仅写入 ai_review_log |

LLM 不可用或输出无法解析时 fail-closed：帖子入审核队列并标记 pending-llm，由对账任务在 LLM 恢复后自动重跑；重跑结果为安全时帖子恢复可见。
