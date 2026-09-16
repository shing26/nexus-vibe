# 黑盒探索走查报告 — 2026-09-09

> 走查对象：Nexus-Vibe（http://localhost:8080，Docker 全栈，nexus-app healthy）
> 走查画像：新手用户 / 破坏性用户 / 核心玩家（Persona-QA-Simulator）
> 走查方式：curl API 层实测 + frontend/src 交互推演（本轮无浏览器 MCP，页面级行为标注【实测-页面-受限】）
> 测试账号：nimtest18132（存量帖 2 篇）、qa_probe_30580（本轮新建）、qa_probe 注册即登录验证

---

## 1. 执行摘要

| 级别 | 数量 | 一句话 |
|---|---|---|
| P0-阻断 | 0 | 无阻断性问题 |
| P1-严重 | 2 | ①编辑后重审事件在 240s lease 窗口内被静默丢弃，用户侧"AI 分析中"假死最长 ~14 分钟才被对账任务救回；②ES 搜索结果路径丢失 likeCount/aiReviewScore 等字段，搜索页与首页数据不一致 |
| P2-一般 | 3 | nickname 超 50 字符返回 500；HTTP 方法不支持返回 500 而非 405；幽灵分类帖（categoryId=99999 创建成功） |
| P3-体验优化 | 2 | API 层重复提交无幂等；AI 分数 10 分制/100 分制口径在终端与历史面板不一致 |

**总结论**：主路径（注册→发帖→搜索→AI 评审→编辑重审→Fork）功能闭环完整，上一轮 7 项修复中 6 项回归通过；本轮暴露的核心风险集中在 **AI 重审异步管线的"静默丢弃 + 长假死"** 和 **ES 搜索重建路径的字段缺失** 两处状态同步问题。

---

## 2. 走查全轨迹 (Transcript)（摘要）

1. 【新手】注册 `qa_probe_30580` → 注册接口直接返回 token（注册即登录，顺畅）→ PUT /users/profile 改昵称/简介成功（corePower 0→20）。
2. 【新手】发无代码普通帖（categoryId=2）→ 详情 aiReviewed=0、无 AI 终端数据 → 符合预期。
3. 【新手】搜索 `keyword=qa-nocode-utf8` → 新帖秒级可搜（ES 索引正常）；中文关键词"并发计数器"命中 1 条 → 通过。
4. 【新手】/messages 未登录 401、登录后空列表 `data:[]`；PUT /users/profile 正常（注意正确端点是 /profile，/users/me 是 500 陷阱，见 P2-2）。
5. 【破坏性】未登录 POST /posts、POST like → 均 401 + 统一文案。nimtest PUT/DELETE 别人帖子 → 400 "Only the author can edit/delete this post."（越权被拒，语义见正面清单备注）。
6. 【破坏性】脏输入四连：title 151 字符 → 400 校验文案明确；content 空白 → 400；tags 传字符串 → 400（文案有误导，见 P3）；**categoryId=99999 → 200 创建成功（缺陷，幽灵频道帖）**。
7. 【破坏性】20 连发 GET /posts → 全部 200，无 429 误伤（也无限流迹象，见正面清单备注）。
8. 【破坏性】同 payload 连发两次 → 产生两个 postId（API 层无幂等，前端有防双击）。
9. 【核心玩家】发 `qa-review3-0909` 带 ```java 并发 bug 帖 → **52 秒**收到评审：score=2/severity=critical/isApproved=false + codeQuality/securityConcerns/optimizationSuggestions 三维分析；aiReviewScore=2 写回；AI 助手评论 status=1 出现在评论列表。
10. 【核心玩家】**编辑该帖（追加一行注释）→ 重审事件静默丢失，帖子 aiReviewed=2 假死 14 分钟**（详见 P1-1），对账任务 04:03 恢复后第二轮评审完成（score=2/severity=high），history 出现两轮记录，评论列表只显示最新 AI 评论，aiReviewed 回 1。
11. 【核心玩家】编辑旧"降级帖"2096904523239477249（lease 早已过期）→ **15 秒**完成第三轮评审（score=2/severity=high），history 三轮、newest first。
12. 【核心玩家】Fork prompt 帖 2088686599665295362 → 新帖 2097412423344795650 生成，forkedFromId 正确，GET /posts/{id}/versions 返回 version=1 完整内容 → 通过。

---

## 3. 缺陷与体验问题清单

### P1-1 【AI摩擦/状态机】编辑后 AI 重审事件在 lease 窗口内被静默丢弃，前端"AI 分析中"假死 ~14 分钟

- **复现步骤**（实测-API）：
  1. 发带 ```java 代码块帖（2097411754613354498），首轮评审 52s 完成（03:48:28，"AI review completed (attempt 1)"）；
  2. 在**首轮评审完成后的 240s lease 窗口内**（本例 +67s）PUT /api/v1/posts/{id} 追加内容 → 200 "Post updated."，aiReviewed=2；
  3. 轮询 GET /api/v1/agent-logs/post/{id} → 14 分钟内恒为 1 轮；docker logs 中 agent-llm 线程**零日志、零报错**；
  4. 对照组：lease 过期后（旧帖 2096904523239477249，上次 lease 为前一天）同一编辑操作 → **15 秒**出第二轮。
- **Expected vs Actual**：Expected：编辑触发重审，15-60s 内出现第二轮。Actual：事件发布成功但 `tryClaimReview` 因 `review_lock_until` 未释放（lease-seconds=240，首轮 claim 后未清除）而 claim 失败，监听器仅 `log.debug` 静默跳过，帖子停在 aiReviewed=2；直到 AiReviewReconcileTask（cron `0 3/5 * * * ?` + stale-minutes=10）于 04:02:59 兜底 re-trigger，04:03:07 完成。用户可见假死 **03:49:35 → 04:03:07 ≈ 13.5 分钟**。
- **证据**：
  - `src/main/java/com/nexus/campus/agent/AiReviewEventListener.java:100-107`（claim 失败仅 log.debug skip，不回写 FAILED、不发用户提示）；
  - `src/main/java/com/nexus/campus/mapper/VibePostMapper.java:235-246`（tryClaimReview SQL：`(review_lock_until IS NULL OR review_lock_until < NOW())`，评审成功路径无任何 `review_lock_until=NULL` 释放）；
  - `src/main/resources/application.yml:135`（lease-seconds: 240）+ `:143`（stale-minutes: 10）；
  - 日志：`04:02:59 [AI-RECONCILE] Re-triggering REVIEWING review for post 2097411754613354498`。
- **修复建议**（给 AI-Architect）：① 评审成功回写分数时同步 `review_lock_until=NULL`（或 lease 只保护进行中评审，完成即释放）；② 兜底：监听器 claim 失败时将帖子降级 FAILED(3)（publishReviewEventSafely 已有此模式，ReconcileTask 会再捞 FAILED），让对账恢复窗口从 10 分钟缩短到 ≤5 分钟；③ 至少将 claim 失败日志升级为 log.warn。
- **验收标准**：首轮评审完成后 30s 内编辑帖子，第二轮评审在 ≤90s 内出现在 history（rounds≥2），全程无 aiReviewed=2 停留超过 120s。
- **分级理由**：编辑刚评审完的帖子是核心玩家最高频动作之一，可感知假死 10+ 分钟且无任何反馈。

### P1-2 【状态同步】ES 搜索结果路径丢失 likeCount/aiReviewScore 等字段，搜索页与首页数据不一致

- **复现步骤**（实测-API）：
  1. 点赞帖子 2097411036011638785（likeCount 经 LikeSyncTask 已刷写为 1）；
  2. GET /api/v1/posts?page=1（DB 路径）→ 该帖 `likeCount: 1`；
  3. GET /api/v1/posts?keyword=bad-category-test（ES 路径）→ 同一时刻同一帖 `likeCount: null`，且所有命中帖 likeCount/aiReviewScore/aiReviewed/viewCount/commentCount 均为 null。
- **Expected vs Actual**：Expected：搜索结果与列表页字段口径一致。Actual：`PostSearchService.mapToPostPageVo`（`service/PostSearchService.java:335-356`）只回填 id/title/content/summary/authorName/categoryName/createTime/tags 共 8 个字段，likeCount、viewCount、aiReviewScore、safetySeverity 等全部丢弃。
- **修复建议**（给 Fullstack-Dev）：在 mapToPostPageVo 中补齐 `viewCount/likeCount/commentCount/aiReviewed/aiReviewScore/safetySeverity/safetyClassification` 映射（buildPostDocument 同步确认这些字段已写入 ES _source），或 ES 只返回 id 列表、回 DB 反查完整 VO。
- **验收标准**：keyword 搜索与无 keyword 列表对同一帖返回的 likeCount/aiReviewScore 完全一致。
- **分级理由**：搜索页是核心入口，AI 分数/点赞数是本产品核心信息；缺失直接造成跨页面数据自洽性破坏（核心玩家画像的交叉验证场景必现）。

### P2-1 【功能缺陷】nickname 超 50 字符 PUT /users/profile 返回 500

- **复现步骤**（实测-API）：登录后 `PUT /api/v1/users/profile {"nickname":"A*151","bio":"x"}` → HTTP 500 "Internal server error."
- **Expected vs Actual**：Expected 400 + 校验文案。Actual：`ProfileUpdateRequest`（dto/ProfileUpdateRequest.java）无任何校验注解，DB `nickname varchar(50)`（schema.sql:9）触发 `MysqlDataTruncation: Data too long for column 'nickname'`，被 RuntimeException 兜底为 500；前端 SettingsPage.tsx:148 的 nickname input 也无 maxLength。
- **修复建议**：`@Size(max=50) private String nickname;`（bio 同理按列宽加 @Size）；SettingsPage input 加 maxLength=50。验收：151 字符昵称返回 400 "Validation failed: ..."。
- **分级理由**：正常 GUI 中用户可输入任意长度昵称（无 maxLength 拦截），500 属于约定中的"500 即 bug"，但影响面限于设置页。

### P2-2 【功能缺陷】HTTP 方法不支持返回 500 而非 405

- **复现步骤**（实测-API）：`PUT /api/v1/users/me`（或任意未注册 PUT 的路径）→ HTTP 500 "Internal system error. Contact system administrator."；`GET /api/v1/users/me` → 400 "Invalid value for parameter: id"（被 /users/{id} 捕获）。
- **Expected vs Actual**：Expected 405 Method Not Allowed。Actual：`HttpRequestMethodNotSupportedException` 无专门 handler，落入 `GlobalExceptionHandler.handleException`（config/GlobalExceptionHandler.java:94-98）返回 500。docker logs 佐证：`03:44:59 Unexpected error: HttpRequestMethodNotSupportedException: Request method 'PUT' is not supported`（同轮共 3 次）。
- **修复建议**：增加 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 405；顺带考虑是否提供 GET/PUT `/users/me` 别名（新手/集成方高频约定路径）。验收：任意不支持的方法返回 405，且 5xx 不再出现于无效方法类错误。
- **分级理由**：GUI 正常操作不触发，但污染日志/监控语义，API 消费者会误判为服务端故障。

### P2-3 【功能缺陷】幽灵频道帖：categoryId=99999 创建成功

- **复现步骤**（实测-API）：POST /api/v1/posts `{"title":"bad-category-test","content":"valid","categoryId":99999}` → 200，postId=2097411036011638785；GET 详情 `categoryName: null`，帖子正常出现在列表。
- **Expected vs Actual**：Expected 400（分类不存在）。Actual 直接发布，归属频道为空。
- **修复建议**：PostService.create 校验 categoryId 存在且 status=1，否则 400。验收：99999 创建返回 400，存量幽灵帖（2097411036011638785）下架或修正分类。
- **分级理由**：前端下拉限制了合法值，但 API 层旁路即产生展示层脏数据（首页出现无频道帖）。

### P3-1 【功能缺陷】API 层重复提交无幂等

- **复现步骤**（实测-API）：同 payload 连发两次 POST /posts → 两个不同 postId（2097411658735759361 / 2097411665761218561）。
- **说明**：前端有防双击（如 PostDetailPage likeInFlight、CreatePostPage 提交态），GUI 正常操作难复现；仅 API 层裸调用会产出重复帖。建议服务端加 title+userId+短时间窗去重或 Idempotency-Key。验收：1 秒内同 author+同 title+同 content 二次创建返回首个 postId 或 409。

### P3-2 【AI摩擦】AI 分数口径不一致：终端 2/10 vs Review History 20/100

- **复现步骤**：【实测-API + 代码推演】评审 score=2（10 分制，存 aiReviewScore=2）；详情页终端显示 "Overall Score: 2/10"；PostDetailPage.tsx:549 Review History 面板 `Score {score <= 10 ? Math.round(score*10) : Math.round(score)}/100` 显示 "Score 20/100"。
- **Expected vs Actual**：同一分数同页两种口径。建议统一为一种分制，或终端与历史面板共用同一格式化函数。验收：同帖两处显示同分。

### 备注（不计问题）

- tags 传 `["not-an-id"]` → 400 "Request body is missing or malformed."（HttpMessageNotReadableException 兜底文案对类型错误略有误导，但 GUI 永远传合法 ID，影响极低）。
- 越权返回 400 而非 403：文案明确（"Only the author can edit this post."），可作为语义约定记录，不阻塞。

---

## 4. 已知修复项回归结果（①-⑦）

| # | 项 | 结果 | 证据类型 | 说明 |
|---|---|---|---|---|
| ① | 点赞 likedByMe 刷新保持 | **通过** | 【实测-API】like → 多次 GET 详情 likedByMe=True 稳定保持；unlike → False。前端 fallback 为 controller 层盖章（PostController.java:191-201） |
| ② | 重审后终端新分数 | **通过** | 【实测-API】旧降级帖编辑后 15s 出第三轮（score=2/sev=high，reviewedAt 2026-09-09T03:57:40），latest 指向最新轮，aiReviewScore 写回；**但注意 P1-1：lease 窗口内编辑的帖子要走 14 分钟对账才出新分数** |
| ③ | Review History 折叠面板 | **通过** | 【实测-API】GET /agent-logs/post/{id} 返回多轮、newest first（旧帖 3 轮/新帖 2 轮）；【代码推演】PostDetailPage.tsx:528-556 折叠面板 + "No completed reviews yet." 空态 + enabled: showHistory 懒加载。页面级点击未实测（无浏览器 MCP） |
| ④ | aiReviewed=3 failed 提示 | **通过（受限）** | 【代码推演】PostDetailPage.tsx:560-565 `aiReviewed===3` 渲染 `AiReviewTerminal state="failed"`；【实测-页面-受限】本轮未制造出 FAILED 现场帖（需压满 LLM 池），无法页面截图佐证 |
| ⑤ | 未登录点赞跳登录 | **通过** | 【实测-API】未登录 POST like → 401 + "Authentication required..."；【代码推演】PostDetailPage.tsx:170-179 `!user → addToast('Log in to like posts') + navigate('/login')` |
| ⑥ | create/edit 页 beforeunload | **通过** | 【代码推演】CreatePostPage.tsx:162-171 与 EditPostPage.tsx:36-50 均有 dirty 基线 + beforeunload preventDefault；页面刷新弹窗未实测 |
| ⑦ | CreatePostPage Code 按钮三反引号 | **通过** | 【代码推演】CreatePostPage.tsx:231-250：无选中插入 `"```\n\n```"` 且光标落点正确（start+4），有选中则包裹；未实测点击 |

---

## 5. 正面清单（修复时勿误伤）

1. **注册即登录**：register 直接返回 token，新手零摩擦。
2. **全局异常设计**：业务错误统一 400 + 安全文案（Validation failed 细分字段），500 不泄漏 SQL/堆栈；只缺 405 处理器（见 P2-2）。
3. **越权防护有效**：author-only 编辑/删除双测通过；未登录写操作全部 401。
4. **ES 搜索索引实时性**：新帖创建后秒级可搜，中文关键词命中正常（上一轮修复有效）。
5. **AI 评审管线信息完整**：score/severity 枚举合法（本轮均落 critical/high/unknown 内），codeQuality/securityConcerns/optimizationSuggestions 三维分析质量高，且 "DeepSeek 官方 API + 禁思考" 下 15-60s 内出结果。
6. **lease + reconcile 兜底架构**：虽然存在 P1-1 窗口问题，但对账任务最终自愈（FAILED/REVIEWING 双路径 re-trigger），无需人工干预。
7. **重审评论管理**：重审后评论列表只保留最新 AI 评论（旧轮 status 置 0），与 Review History 面板分工清晰。
8. **likedByMe 匿名语义**：未登录返回 null 而非 false（PostController.java:187-189 注释明确），避免误报"未点赞"。
9. **消息页乐观更新回滚**：markAsRead 失败回滚 isRead（MessagesPage.tsx），状态一致性意识到位。
10. **Redis 点赞 Lua toggle + 定时绝对值刷写**：like 幂等且最终一致（ LikeSyncTask cron 0 0/5），点赞接口响应 currentLikes 实时值。
11. **限流边界安全**：20 连发 GET 无 429，正常操作零误伤（同时备注：未见限流层，生产环境建议补充写操作限流）。
12. **Fork + 版本历史**：fork 即建版本 v1，forkedFromId 溯源完整。

---

## 6. 测试数据说明

- 本轮新增帖：2097410037419159553（无代码·GBK 客户端乱码，系走查工具 curl 编码所致，**非产品缺陷**，服务端 UTF-8 链路已单独验证正常）、2097410245242728449、2097411036011638785（幽灵分类帖）、2097411658735759361、2097411665761218561（重复提交样本）、2097411754613354498（重审链路样本）、2097412423344795650（Fork 样本）；已编辑既有帖 2096904523239477249 与 2097411754613354498 用于回归②/③。未删除任何存量帖子，未改动 .env/代码。
