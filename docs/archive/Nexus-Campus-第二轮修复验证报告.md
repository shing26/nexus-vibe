# Nexus-Campus 第二轮「修复验证」体验报告

> 验证对象：D:/Nexus-Campus（Nexus-Vibe AI 开发者社区）
> 验证版本：git `8b73775 feat: fix experience report issues and harden pre-deployment`
> 验证时间：2026-08-15 ~ 08-16
> 验证方式：真实浏览器（Playwright）+ 后端 API 实测，测试用隔离实例（后端 8081 重启加载新代码，前端 Vite 5173 热重载）

---

## 一、体验概览

本轮以「最新进化的 agent」对第一轮 15 项问题做逐条复验，并回归正常功能、排查是否引入新问题。

**核心结论：15 项问题中 14 项已修复，1 项（L15 移动端溢出）部分修复（残留约 59px）。新发现 2 项新问题（1 项中、1 项低）。**

| 结论 | 数量 |
|------|------|
| ✅ 已修复 | 14 |
| ⚠️ 部分修复 | 1（L15） |
| 🆕 新引入问题 | 2（N1 中、N2 低） |
| 回归异常 | 0 |

---

## 二、15 项问题逐条复验结果

### 高优先级

**H1 · Prompt 工坊频道永远空 —— ✅ 已修复**
- 根因修复：`VibePostServiceImpl.normalizePostType()` 使 `type=null/blank/"all"` 不再默认 "post"，`applyTypeFilter` 同步修正。
- API 实测：`GET /api/v1/posts?channelSlug=prompts` 返回 `total=3`，3 帖均为 `postType=prompt`（id 100/101/102）；`type=prompt` 同返回 3 帖。
- UI 实测：首页 Prompts tab、频道页 `/channel/prompts` 均显示 3 篇 Prompt 模板（React Component Generator、Tailwind UI Prompt Architect 等）。

**H2 · Agent Logs 页面崩溃 —— ✅ 已修复**
- 根因修复：`AgentLogsPage.tsx` 的 `SeverityBadge` 改为 `(severity || 'unavailable').toLowerCase()`，null/undefined 不再触发 `toLowerCase()` 崩溃；`StatusBadge` 新增 `unavailable` 态。
- 权限加固：`/api/v1/agent-logs` 列表与 `/stats` 现要求 admin（controller 校验 `currentRole`），前端 `/agent-logs` 路由包进 `AdminRouteGuard`。
- 实测：admin 打开 `/agent-logs` 正常渲染（"$ agent_logs — AI Agent Operations Console"，8 条评审记录）；非 admin 访问被重定向到首页，API 返回业务码 403 "Access denied. Admin privileges required."；匿名访问返回 401。

**H3 · 非 admin 可改帖到「社区公告」 —— ✅ 已修复**
- 根因修复：`updatePost` 新增 `if ("announcements".equals(slug) && !isAdmin) throw`；前端 `EditPostPage` 用 `displayChannels` 对非 admin 过滤 announcements（且保留自己当前已在公告频道的帖可选）。
- API 实测：shing 发帖到公告频道 → 400 "只有管理员才能在公告频道发帖"；shing 把已有帖改到公告频道 → 400 同文案；admin 发公告 → 200 成功（回归正常）。

### 中优先级

**M4 · LLM 不可达时 AI 评审降级误导为 FLAGGED/UNKNOWN —— ✅ 已修复**
- 根因修复：`AiReviewService` 在 `resultJson=null`（LLM 不可达）时改为 `saveReviewLog(postId, null, "unavailable", 0)` 并把帖子标记 `aiReviewed=1`；`LlmClient` 仅在 apiKey 非空时注入 Authorization 头；`AiLogController` 新增 `resolveReviewStatus()` 映射 `status="unavailable"`。
- 实测：发含代码块的帖后，`aiReviewed=1`（不再无限 pending），日志 `status="unavailable"`、`severity="unavailable"`（不再误导为 FLAGGED）；前端 `AiReviewTerminal` 优先显示 "AI review data unavailable."；Footer ticker 显示 UNAVAILABLE 而非 FLAGGED。

**M5 · 发帖默认选中 Prompt 工坊 + post_type='post' 错配 —— ✅ 已修复**
- 根因修复：`CreatePostPage` 新增 `generalChannels`（排除 prompts）作为默认频道池，`handlePostTypeChange()` 联动频道与类型。
- UI 实测：shing 打开 `/post/new` 默认频道为「作品展示」（非 Prompt 工坊）、类型为 Post；点「🤖 Prompt Template」→ 频道自动切到「Prompt 工坊」；点回「📝 Post」→ 频道离开 Prompt 工坊。

**M6 · 超长标题 500 —— ✅ 已修复**
- 根因修复：`PostCreateRequest`/`PostUpdateRequest` title 加 `@Size(max=150)`；新增 `GlobalExceptionHandler` 统一返回友好 400。
- API 实测：160 字标题发帖 → HTTP 400 "Validation failed: Title must not exceed 150 characters"（非 500）；前端标题输入框加 `maxLength=150` 与前置校验。

**M7 · 注册密码校验前后端不一致 —— ✅ 已修复**
- 根因修复：`RegisterPage` 前端改为「8-20 位 + 大小写 + 数字」，与后端 `@Size(8,20)` + 大小写数字规则一致，并补充 username/nickname 长度上限。
- 实测：前端输入 `123456` → 被拦截 "Password must be 8-20 characters"；后端 `123456` → 400，`Abc12345` → 注册成功。

**M8 · 首页频道卡片 count 硬编码 —— ✅ 已修复**
- 根因修复：新增 `VibePostMapper.countActivePostsByCategory`、`ChannelStatsVo`、`CategoryController /api/v1/channels/stats`；`HomePage`/`Sidebar` 去掉硬编码数字改读实时统计。
- 实测：`/api/v1/channels/stats` 返回真实计数（announcements=1、prompts=3、agents=2、vibe-coding=1、其余 0），且与 UI 首页卡片显示完全一致（动态随发帖变化，说明已非硬编码）。

**M9 · seed comment_count 与实际不符 —— ✅ 已修复**
- 根因修复：`data.sql` 的 comment_count 对齐真实评论数（post1=2、post2=1、post3/4/5=0）。
- 实测：`/comments/post/{id}` 实际数量与 `commentCount` 逐一相符（2/1/0/0/0）。

**M10 · 敏感词帖发布后跳 404 无审核提示 —— ✅ 已修复**
- 根因修复：`CreatePostPage` 在 `status===2` 时 toast「内容含敏感词，已提交审核」并跳转首页/审核页（不再跳 `/post/{id}`）；`PostController.createPost` 返回 `auditNotice` 字段。
- 实测：发敏感词帖 → 500ms 内捕获 toast「内容含敏感词，已提交审核」，最终落在首页（非 404）；帖进审核队列（`/admin/audit/posts`），AuditPage 中内容脱敏为「这段内容包含[敏感词]和[敏感词]信息」，Approve/Reject 按钮齐全。

### 低优先级

**L11 · Playground 变量替换无效 —— ✅ 已修复**
- 根因修复：`data.sql` 的 prompt 正文改含 `{{componentName}}`/`{{features}}` 等变量占位；`PostDetailPage` 用 `varsKey` 逐变量替换。
- 实测：`/post/100` 填入 componentName=ZZZBtn、features=ZZZloading,ZZZerror 后，渲染区出现替换值、`{{componentName}}` 等占位符消失。

**L12 · 首页缺 announcements/resources 入口 —— ✅ 已修复**
- 根因修复：`HomePage` channelGrid 补齐 7 个频道（社区公告/资源聚合）。
- 实测：首页含 7 个频道卡片入口。

**L13 · 首页 Debug 空状态文案错配 —— ✅ 已修复**
- 根因修复：`HomePage` 新增 `emptyCopy` 按 tab 区分文案。
- 实测：Debug tab 空态显示标题「急诊室空转中」、描述「贴上报错上下文，AI Agent 与社区会一起定位问题。」（`ChannelPage` 同步有独立文案）。

**L14 · Restore note 共享 state —— ✅ 已修复**
- 根因修复：`PromptVersionPanel` 的 `restoreNote` 改为 `Record<version, string>` 按版本独立。
- 实测：打开 `/post/100` 版本历史，两个可恢复版本的 note 输入框分别填 NOTE-A / NOTE-B，互不串扰。

**L15 · 移动视口 375px 横向溢出 —— ⚠️ 部分修复**
- 已修复部分：`Navbar` 右集群（原 scrollWidth=588 的主因）——搜索文案/⌘K 键提示/消息/设置/用户名在小屏 `hidden`，`gap` 压缩，`min-w-0` 收窄。
- 残留问题：375px 下 `document.documentElement.scrollWidth=434`（仍溢出 59px）。定位根因：`PostCard.tsx` 外层 `<div className="relative">`（无 `overflow-hidden`）内的 `BorderBeam`（`size=150`，`absolute` + `offsetPath` 动画）向右越界到 434px；另 PostCard 统计行 `ml-auto flex items-center gap-2` 亦有约 8px 溢出。详见 §四 N1 附近证据与 `ui-shots-r2/L15-375px-overflow.png`。

---

## 三、新发现的问题（按严重度）

### 🆕 N1（中）· Admin Dashboard 新增「待审队列」链接到 404

- **现象**：`/admin/dashboard` 新增的 "Pending Audit Queue" 把待审帖渲染为 `<Link to={'/post/' + post.id}>`，点击后落到「404 — Post Not Found」。
- **复现步骤**：admin 登录 → `/admin/dashboard` → 点待审队列任意帖 → 页面显示 "404 — Post Not Found / This post may have been deleted or never existed."（实测点击 `/post/2088986492863426561`）。
- **根因**：`VibePostServiceImpl.getPostDetail()` 对 `status != 1`（待审 status=2）一律返回 null（454-458 行），而 `DashboardPage.tsx` 新增待审队列直接链接 `/post/{id}`；`PostDetailPage` 对 null post 渲染 404。
- **预期**：待审帖应链接到审核页 `/admin/audit`，或在详情页对作者/管理员支持查看待审内容；至少不应落到 404。
- **证据**：DOM 文本「404 — Post Not Found」；`getPostDetail` 源码 `if (post == null || post.getStatus() != 1) return null;`。

### 🆕 N2（低）· 敏感词帖发布时「Post published!」成功提示误导

- **现象**：发布含敏感词的帖时，顶部同时弹出「Post published!」（success）和「内容含敏感词，已提交审核」两个 toast。
- **复现步骤**：shing 登录 → `/post/new` → 标题+内容含「赌博」「毒品」→ 点 Publish → 观察 toast 区同时出现两条提示。
- **根因**：`CreatePostPage.handleSubmit()` 在状态判断之前先无条件 `addToast("Post published!", "success")`，随后对 `status===2` 再追加审核提示。
- **预期**：敏感词（待审）帖不应提示「Post published!」，应只提示「已提交审核」。
- **证据**：toast DOM 文本序列「Post published! | 内容含敏感词，已提交审核」。

---

## 四、回归测试结果（全部通过）

| 项 | 结果 | 证据 |
|----|------|------|
| 登录 admin/shing | ✅ | 200，返回 token |
| 首页加载（匿名） | ✅ | 7 频道卡片 + 真实计数 |
| Footer AgentTicker | ✅ | 匿名访问 `/agent-logs/ticker` 200，页面无崩溃 |
| 正常发帖 → 详情页 | ✅ | 落在 `/post/{id}`，无 404 |
| 评论提交 | ✅ | 200 "Comment transmitted." |
| 点赞 | ✅ | 200，currentLikes 自增 |
| 搜索 keyword | ✅ | 200，返回命中 |
| 匿名看用户资料/ tags/ 频道列表 | ✅ | 均 public 200 |
| AuditPage 审核流 | ✅ | 4 条待审、内容脱敏、Approve/Reject 齐全 |
| admin 发公告 | ✅ | 200（H3 回归） |
| M6 校验返回 | ✅ | 400 而非 500（GlobalExceptionHandler 生效） |

> 说明：上述回归中，因本人测试期间新增了若干测试帖，频道计数随之动态变化（如 announcements 2、agents 3），恰好反向印证 M8「计数已非硬编码、随真实数据实时变化」。

---

## 五、功能覆盖清单

| 功能/页面 | 覆盖状态 | 备注 |
|-----------|---------|------|
| 首页（频道卡片/计数/5 tab/空态/移动视口） | ✅ 已体验 | M8/L12/L13/L15 |
| 频道页 `/channel/prompts` | ✅ 已体验 | H1 |
| 帖子详情（prompt playground/版本历史/评论/点赞） | ✅ 已体验 | L11/L14 |
| 发帖页（默认频道/类型联动/敏感词/超长标题/草稿） | ✅ 已体验 | M5/M6/M10 |
| 编辑页（公告过滤） | ✅ 已体验（前端代码+后端 API） | H3 |
| 注册/登录 | ✅ 已体验 | M7 |
| Agent Logs 页（admin/非 admin/匿名） | ✅ 已体验 | H2 |
| Admin Dashboard（新增） | ✅ 已体验 | N1 |
| Admin Audit 审核页 | ✅ 已体验 | M10 回归 |
| 移动视口 375px | ✅ 已体验 | L15 残留 |
| 后端 API（posts/channels/stats/comments/like/search/tags/users/agent-logs/audit） | ✅ 已实测 | 见 §二/§四 |

**未覆盖项**：消息（MessagesPage）、设置（SettingsPage）、草稿（DraftsPage）、文件上传（UploadController 88 行改动）本轮未做 UI 实测——改动属后端加固（上传大小限制/路径安全），且不属第一轮 15 项问题范围，故未单独回归（标注为「未覆盖」，如需可下一轮补齐）。

---

## 六、结论与建议

1. **修复质量高**：第一轮 15 项问题中 14 项彻底修复且无副作用，M4/M10 的降级与审核路径修复尤其扎实（脱敏、状态映射、跳转逻辑闭环）。
2. **L15 建议补刀**：给 `PostCard.tsx` 外层容器加 `overflow-hidden`（或让 BorderBeam 容器裁剪），即可消除 375px 下 59px 残留溢出。
3. **N1 建议修复**：`DashboardPage` 待审队列链接应指向 `/admin/audit`（或支持查看待审详情），避免点进 404。
4. **N2 建议修复**：`CreatePostPage` 将「Post published!」toast 移到 `status!==2` 分支内。

> 本报告仅报告问题与验证结论，未对源码做任何修改（遵循默认「只报告、不修复」约定）。
