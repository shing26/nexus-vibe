# Nexus-Campus（Nexus-Vibe）深度体验报告

> 体验对象：`D:\Nexus-Campus` — AI 开发者社区论坛（Spring Boot 3.3.5 + React 19/Vite）
> 体验方式：隔离测试实例（后端 `:8081` H2 内存库、前端 `:5173`），真实浏览器操作 + API/DOM/源码取证
> 体验账号：admin / shing / alice / bob 等（密码均 123456），以及新注册账号
> 体验日期：2026-08-15

---

## 一、体验概览

以真实用户视角对产品全部 17 个路由页面与核心功能进行了无死角体验，覆盖登录/注册、发帖（普通帖 + Prompt 模板）、编辑与版本回滚、Fork、评论、点赞、搜索、Prompt Playground、AI 代码评审、LLM 安全审查、敏感词 DFA 审核、Admin 审核队列/仪表盘/agent-logs、消息、设置、草稿、删除帖子等，并做了边界/异常/权限/移动视口交叉验证。第二轮补充了 **UI 设计视觉层观察**（视觉模型截图分析 + 全站 DOM 样式取证），结论见第六章。

**总体结论**：产品骨架完整、视觉统一（终端风）、核心"发帖-评论-点赞-Fork-版本"主链路可用；但存在 **2 个核心功能错误（Prompt 工坊频道空、Agent Logs 崩溃）+ 1 个权限漏洞（公告频道可被越权编辑）**，以及若干中低级的逻辑错配、数据不一致与体验缺陷。未发现数据损坏/丢失级别的阻塞问题。

---

## 二、全量功能覆盖清单

| # | 功能/页面 | 覆盖状态 | 说明 |
|---|-----------|----------|------|
| 1 | 首页 `/` | ✅ 已体验 | 频道卡片、Mission Control、Hot/Latest 排序、帖子流 |
| 2 | 频道页 `/channel/:slug` | ✅ 已体验 | agents/vibe-coding 正常；**prompts 空**（问题 H1） |
| 3 | 帖子详情 `/post/:id` | ✅ 已体验 | 内容渲染、评论、点赞、分享、Fork、AI 评审、Playground |
| 4 | 发帖 `/post/new` | ✅ 已体验 | 普通帖/Prompt 帖、草稿、敏感词、超长、特殊字符 |
| 5 | 编辑 `/post/:id/edit` | ✅ 已体验 | 编辑、频道修改、版本回滚 |
| 6 | 搜索 `/search` | ✅ 已体验 | RAG 命中、中文、无结果空状态 |
| 7 | 登录 `/login` | ✅ 已体验 | 错误密码提示、成功登录、刷新保持 |
| 8 | 注册 `/register` | ✅ 已体验 | 校验、重复名、密码规则、成功注册 |
| 9 | 用户主页 `/user/:id` | ✅ 已体验 | 统计网格 + 时间线 + 已发帖 |
| 10 | 设置 `/user/settings` | ✅ 已体验 | 资料修改、密码校验 |
| 11 | 消息 `/user/messages` | ✅ 已体验 | 2 条消息、标记已读 |
| 12 | 草稿 `/drafts` | ✅ 已体验 | 保存/编辑/删除草稿闭环 |
| 13 | 标签页 `/tags` | ✅ 已体验 | 正常 |
| 14 | 审核队列 `/admin/audit` | ✅ 已体验 | Approve/Reject 均实测生效；非 admin 被重定向 |
| 15 | 仪表盘 `/admin/dashboard` | ✅ 已体验 | 统计正常（totalPosts=10 与实际一致） |
| 16 | Agent Logs `/agent-logs` | ✅ 已体验 | **页面崩溃**（问题 H2） |
| 17 | 404 页 `*` | ✅ 已体验 | 正常 |
| 18 | Fork 模板 | ✅ 已体验 | 一键副本 + "Forked from post #100" 徽章 |
| 19 | 版本历史/回滚 | ✅ 已体验 | v1→编辑→v2→回滚→v3 内容还原正常 |
| 20 | 点赞/评论 | ✅ 已体验 | 0→1、计数 +1；未登录跳登录页 |
| 21 | AI 评审/安全审查 | ✅ 已体验 | LLM 不可达走降级（问题 M4） |
| 22 | 敏感词 DFA | ✅ 已体验 | 临界词替换为 `[敏感词]` 并转审核队列 |
| 23 | 删除帖子 | ✅ 已体验 | 作者/管理员可删，删除后跳首页 |
| 24 | 移动视口（375px） | ✅ 已体验 | 存在横向溢出（问题 L5） |
| 25 | XSS 注入 | ✅ 已体验 | 服务端 Jsoup 白名单清洗，安全（无弹窗/无脚本执行） |
| 26 | 未登录点赞/评论 | ✅ 已体验 | 正确重定向到 `/login` |

> 未覆盖项：无。全部页面与功能均至少走查一遍。

---

## 三、问题清单（按严重度排序）

### 🔴 高（High）

#### H1. Prompt 工坊频道永远为空，Prompt 模板帖全部不可见

- **严重级别**：高（核心频道不可用）
- **复现步骤**：登录任意用户 → 点击侧边栏「Prompt 工坊」（或访问 `/channel/prompts`）。
- **实际表现**：频道页显示空列表，看不到任何 Prompt 模板；但数据库里实际有 3 个 Prompt 模板帖（id=100/101/102，category_id=2）。
- **预期表现**：应展示 3 个 Prompt 模板（React Component Generator 等）。
- **根因定位**：
  - 前端 `ChannelPage.tsx:70` 请求 `/posts?channelSlug=prompts`（未传 `type`）。
  - 后端 `PostController.java:153-154` 走 `getPostsByCategory(categoryId, page, size, type)`，此时 `type=null`。
  - `VibePostServiceImpl.java:397`：`"all".equals(type) ? null : (type == null || type.isBlank() ? "post" : type)` —— `type` 为 null 时被强制默认成 `"post"`，只查 `post_type='post'` 的帖子；而 Prompt 模板帖全是 `post_type='prompt'`，因此返回 0 条。
- **证据**：`curl "/api/v1/posts?channelSlug=prompts"` 返回 `total=0`；`/posts?page=1&size=100` 可见 id=100/101/102 均为 `post_type=prompt`、`category_id=2`。
- **关联**：与问题 M5（发帖默认频道错配）叠加后，该频道会显示"普通帖"而不是"Prompt 模板"，内容完全错位。

#### H2. Agent Logs 页面崩溃（`Cannot read properties of null`）

- **严重级别**：高（Admin 控制台页面崩溃）
- **复现步骤**：以 admin 登录 → 访问 `/agent-logs`。
- **实际表现**：页面整体报错 "Something went wrong — Cannot read properties of null (reading 'toLowerCase')"。
- **预期表现**：应展示 AI 评审日志列表与统计卡片。
- **根因定位**：`AgentLogsPage.tsx:85`（`SeverityBadge`）`const normalized = severity.toLowerCase();` 未处理 `severity` 为 null 的情况。当 LLM 不可达时（当前环境 Redis/LLM 均降级），`AiReviewService` 写入了 `severity=null` 的评审日志（如 log id `2088509590163640321`），导致该行抛异常。
- **证据**：`GET /api/v1/agent-logs` 返回 `{"severity": null, "reviewer": "code-review-agent", ...}`；页面被 ErrorBoundary 捕获后显示上述崩溃文案。
- **关联**：与问题 M4（LLM 降级写入 null 记录）同源，是本问题被触发的上游。

#### H3. 权限漏洞：非管理员可把帖子改到「社区公告」频道

- **严重级别**：高（权限控制缺失）
- **复现步骤**：以普通用户 shing 登录 → 打开自己的帖子 → 编辑 → 频道下拉选择「社区公告」→ 保存。
- **实际表现**：保存成功，帖子被移入"社区公告"频道（实测帖子 `2088510476097114114` 变成"社区公告"）。
- **预期表现**：公告频道为"管理员只读"，非管理员应无法选择/保存到该频道。
- **根因定位**：
  - 前端 `EditPostPage.tsx:146-151` 直接 `channels?.map(...)` 渲染所有频道，**未像发帖页那样过滤 announcements**（对比 `CreatePostPage.tsx:186` 有 `channels.filter(c => c.slug !== "announcements")`）。
  - 后端 `VibePostServiceImpl.updatePost`（第 172-225 行）只校验"非作者不能编辑"（第 179 行），频道变更分支（第 185-191 行）**没有公告频道的 admin 校验**；而 `createPost`（第 119-126 行）有此校验。
- **证据**：实测 shing 编辑保存后帖子 categoryName 变为"社区公告"；源码对比 createPost 与 updatePost 的校验差异。

---

### 🟠 中（Medium）

#### M4. LLM 不可达时 AI 评审降级误导用户（"FLAGGED / Needs Review / UNKNOWN"）

- **严重级别**：中
- **复现步骤**：在 LLM 服务不可达的环境下发一篇含代码块的帖子，观察帖子详情与底部 ticker。
- **实际表现**：帖子 AI 评审显示 "FLAGGED / Needs Review / Score: --/100 / UNKNOWN"；底部 agent.ticker 显示 `Severity: null · Status: FLAGGED`。
- **预期表现**：LLM 调用失败应显示"评审暂时不可用/失败"之类的明确提示，而不是把帖子标记为"被发现问题"。
- **根因定位**：`AiReviewService.reviewPost`（约 198-202 行）LLM 返回 null 时 `saveReviewLog(postId, null, null, 0)` 写入 `isApproved=0` 记录；前端 `Footer.tsx:42`、`AgentLogsPage.tsx:96` 把 `isApproved=0` 一律映射为 "FLAGGED"，`severity=null` 映射为 "UNKNOWN"。
- **证据**：ticker 文案 `code-review-agent verified post "…" · Severity: null · Status: FLAGGED`；`/api/v1/agent-logs` 中该日志 `isApproved=0, severity=null`。

#### M5. 发帖页默认选中「Prompt 工坊」频道 + 默认 post_type='post'，普通帖默认发错频道

- **严重级别**：中
- **复现步骤**：以非管理员用户直接进入 `/post/new`，不手动改频道与类型，直接发布。
- **实际表现**：新帖落在"Prompt 工坊"频道，类型为普通帖（post）。
- **预期表现**：默认频道应是通用的讨论频道（如"Vibe Coding 经验"），或当默认频道是 Prompt 工坊时默认类型应为 Prompt 模板（prompt）。
- **根因定位**：`CreatePostPage.tsx:154` 默认 `postType="post"`；第 268-269 行当 `categoryId=null` 时回填 `displayChannels[0].id`。因公告频道对非 admin 被过滤，`displayChannels[0]` 即"Prompt 工坊"，造成"普通帖 + Prompt 工坊"的错配。
- **证据**：实测新帖 `2088509577077411841` 等均落入 Prompt 工坊（categoryName="Prompt 工坊"）。

#### M6. 超长标题导致后端 500 错误（无长度校验）

- **严重级别**：中
- **复现步骤**：发帖时标题填入 2000+ 字符（内容正常），点击发布。
- **实际表现**：`POST /api/v1/posts` 返回 `500 Internal Server Error`，页面停留在发帖页，无任何友好提示。
- **预期表现**：前端/后端应在提交前校验标题长度并给出明确提示，或后端返回 400 校验错误。
- **根因定位**：`PostCreateRequest` 对 title 仅有 `@NotBlank`，无 `@Size`；`schema.sql:43` 定义 `title varchar(150)`，超长标题在数据库插入时溢出抛异常。
- **证据**：网络日志 `[HTTP 500] http://localhost:5173/api/v1/posts`；标题 2000 字符触发。

#### M7. 注册密码校验前后端不一致（前端 6+ 位 vs 后端 8-20 位含大小写+数字）

- **严重级别**：中
- **复现步骤**：注册时密码输入 `123456`（6 位）。
- **实际表现**：前端通过（`Password needs 6+ chars` 不触发），提交后被后端 400 拒绝，报错 `Validation failed: 密码必须包含大小写字母和数字，长度8-20位; Password must be 8-20 characters`。
- **预期表现**：前后端规则应一致，且报错信息应清晰单一。
- **根因定位**：前端 `RegisterPage.tsx:27` 只校验 `password.length < 6`；后端 `RegisterRequest.java:20-21` 有 `@Size(min=8,max=20)` 与 `@Pattern`（要求含大小写字母+数字）。
- **证据**：`POST /api/v1/auth/register` 对 `123456` 返回 400；而 demo 账号登录用 `123456` 却可登录，进一步凸显规则不一致。另：`Abc12345` 可成功注册（code 200）。

#### M8. 首页频道卡片计数硬编码，与真实帖子数严重不符

- **严重级别**：中
- **复现步骤**：查看首页频道卡片上的数字（128/64/48/72/36）。
- **实际表现**：显示 128/64/48/72/36 等大数字，但实际各频道只有 0~2 条帖子。
- **预期表现**：计数应来自后端真实统计。
- **根因定位**：`HomePage.tsx:20-25` `channelGrid` 数组里 `count` 为写死的字面量。
- **证据**：首页 body 文本显示 `Prompt 工坊 … 128`、`作品展示 … 64` 等；而 `/api/v1/posts` 全站仅有 10 条帖子。

#### M9. seed 数据 comment_count 与实际评论数不符

- **严重级别**：中（数据一致性）
- **复现步骤**：打开帖子详情（如 post 1、post 100）。
- **实际表现**：标题栏显示 "Comments (24)"（post 1）/ "Comments (8)"（post 100），但下方实际评论列表分别只有 2 条 / 0 条。
- **预期表现**：计数与实际评论一致。
- **根因定位**：`data.sql:36/61` 硬编码 `comment_count=24/8`，而 `vibe_comment` 表实际只有 3 条（post 1 有 2 条）。前端 `PostDetailPage.tsx:258/472` 直接显示 `post.commentCount`。实测新发评论会实时 +1，属 seed 数据质量问题。
- **证据**：post 1 详情头部 "Comments (24)"，评论区仅 2 条；data.sql 注释记录。

#### M10. 发布含敏感词帖子后跳转显示 "404 — Post Not Found"，无审核提示

- **严重级别**：中
- **复现步骤**：发帖内容含临界敏感词（如"色情/赌博"），点击发布。
- **实际表现**：发布后跳转到 `/post/{id}`，页面显示 "404 — Post Not Found"，无任何"帖子正在审核中"的提示，用户以为帖子丢了。
- **预期表现**：应提示"内容含敏感词，已提交审核"。
- **根因定位**：`SensitiveWordService.systemCriticalKeywords()`（硬编码"赌博/色情/暴力…"）触发 `status=2`（待审核），`VibePostServiceImpl.java:110` 置状态；前端详情页对非 `status=1` 帖返回 404（`PostController` 详情只查可见帖）。发帖成功后前端无条件 `navigate('/post/' + id)`。
- **证据**：实测含"色情"的标题被替换为 `正常标题 [敏感词]测试` 并进入 `/admin/audit` 队列；但发布后详情页显示 404。

---

### 🟡 低（Low）

#### L11. Prompt Playground 变量替换对 seed 模板无效

- **严重级别**：低
- **复现步骤**：打开 Prompt 模板帖（如 post 100）→ 在 Playground 的 `componentName`/`features` 输入框填入任意值。
- **实际表现**：Live Preview 完全不变化，填入的值（如 `ZZZMyButton`）不出现在任何预览区；"Copy Prompt" 复制的也是未替换变量的原文。
- **预期表现**：变量值应替换进模板对应位置。
- **根因定位**：`PostDetailPage.tsx:198-205` 只对 `{{变量}}` 形式做正则替换，但 seed 模板正文（`data.sql` 的 prompt 帖 content）是自然语言描述 + 代码示例，**不含任何 `{{变量}}` 占位符**，`prompt_metadata.variables=["componentName","features"]` 形同虚设。
- **证据**：填入 `ZZZMyButton`/`ZZZloading,error` 后，页面所有 `<pre>` 文本中均无这两个值。

#### L12. 首页频道卡片缺少「社区公告」「资源聚合」两个频道入口

- **严重级别**：低
- **复现步骤**：对比首页频道卡片与侧边栏频道列表。
- **实际表现**：首页只有 5 个频道卡片（prompts/showcase/agents/vibe-coding/debug），缺 announcements 与 resources。
- **根因定位**：`HomePage.tsx:20-25` `channelGrid` 只列了 5 项；侧边栏则渲染全部 7 个频道。
- **证据**：首页 body 无"社区公告/资源聚合"卡片，侧边栏有全部 7 频道。

#### L13. 首页 Debug 空状态引导文案错配

- **严重级别**：低
- **复现步骤**：首页 Mission Control 点「Debug」tab，观察空状态引导文案。
- **实际表现**：空状态统一显示"一键填充示例 Prompt"并跳转填充 Prompt，而非针对 Debug 的引导。
- **根因定位**：`HomePage.tsx` 的 EmptyState 复用了 Prompt 引导文案，未按 tab 区分。
- **证据**：Debug tab 下仍显示"一键填充示例 Prompt"。

#### L14. Prompt 版本回滚的 Restore 备注输入框共享 state

- **严重级别**：低
- **复现步骤**：打开版本历史面板，在任一版本的 Restore 备注框输入内容，观察其它版本输入框。
- **实际表现**：多个版本输入框显示同一份输入值（共享同一个 `restoreNote` state）。
- **根因定位**：`PromptVersionPanel` 中多个 Restore 输入框 `value` 绑定同一 state。
- **证据**：源码中 Restore note 输入框 value 共用 `restoreNote`。

#### L15. 移动视口（375px）存在横向溢出

- **严重级别**：低
- **复现步骤**：以 375px 宽度打开首页。
- **实际表现**：`document.documentElement.scrollWidth > clientWidth`（存在横向滚动条）。
- **预期表现**：移动端应无横向溢出。
- **根因定位**：首页频道卡片/网格布局未做窄屏自适应（固定宽度或未换行）。
- **证据**：375px 视口下 `mobile_hscroll: true`。

---

## 四、已验证正常的功能（无问题）

- 登录/登出（错误密码提示、成功欢迎、刷新保持，zustand persist）
- 发帖（空标题/内容前端拦截；含代码块 + 中文 + emoji 正常）
- 评论（发评论成功、计数 +1）、点赞（0→1）
- Fork（一键副本 + "Forked from post #100" 徽章）
- 编辑 + 版本回滚闭环（v1→v2→回滚→v3 内容还原）
- 搜索（RAG 命中、中文命中、无结果空状态）
- 用户主页、标签页、404 页、频道页（agents/vibe-coding）
- Admin：审核队列 Approve/Reject 均生效、Dashboard 统计准确（totalPosts=10 与真实一致）、非 admin 访问 admin 路由被重定向回首页
- 草稿（保存/编辑/删除）、删除帖子、消息（2 条 + 标记已读）、设置（资料保存、密码校验）
- 敏感词 DFA（临界词替换为 `[敏感词]` 并转审核队列）、XSS 防护（服务端 Jsoup 白名单清洗，无脚本执行）

---

## 五、备注与建议

1. 问题 H1 与 M5 相互叠加：Prompt 工坊频道既"看不到模板"（type 默认 post），又"混入了普通帖"（发帖默认落该频道），建议一并修复——频道查询按 `type='prompt'` 或 `'all'`，发帖默认频道改为通用讨论频道或默认类型改为 prompt。
2. 问题 H2 与 M4 同源：LLM 降级路径需要显式区分"调用失败"与"评审为负面"，避免 null severity / FLAGGED 误导，并修复 `SeverityBadge` 的 null 防御。
3. 问题 M6/M7 建议在 DTO 层补 `@Size` 校验，并统一前后端规则与错误文案（当前中英混杂）。
4. 所有测试均在隔离实例（H2 内存库）上完成，未触碰用户真实服务与数据。

---

## 六、UI 设计观察与反馈（视觉层）

> 观察方式：视觉模型（describe_image）对 8 张关键页面截图（首页/帖子详情/发帖/Prompt 模板详情/Admin 仪表盘/移动端等）做视觉分析 + 全站 15 个路由的 DOM/computed-style 取证（`ui-shots/design_dom.json`）。截图证据位于工作区 `ui-shots/` 目录。本章聚焦"设计/视觉/可用性"层面，功能性问题见第三、四章。

### 6.1 设计语言总评（做得好的地方）

- **主题统一度高**：全站暗色终端风（背景 `rgb(10,13,20)` 深蓝黑），主内容用等宽字体（ui-monospace），"代码社区"气质一致，识别度强。
- **语义色清晰**：绿色（`rgb(16,185,129)`）= 主操作/成功/已选中，紫色（`rgb(168,85,247)`）= AI 相关（AI Polish、AI Score），红色 = 危险（Delete/Reject），橙色 = 待审核（Pending）。色相语义全站一致。
- **帖子卡片结构**：`# 标题 → // 描述 → 代码块 → 时间/互动数据` 的信息流直观，代码块有语法高亮，符合开发者阅读习惯。
- **选中态可辨识**：发帖页类型切换（Post/Prompt Template、Editor/Preview）、排序 tab（Hot/Latest）均以绿色填充区分当前项。

### 6.2 设计问题清单（按影响程度）

#### D1. 全局字号偏小，可读性不足（影响面：全站）

- **表现**：正文按钮普遍 10–12px，页面主标题仅 16px，帖子标题 H3 仅 14px 且字重 400；1440px 桌面视口下阅读吃力（"Logout" 10px、"Copy/Fork" 10px、"New Post" 12px、"// Comments (24)" 12px）。
- **证据**：`ui-shots/design_dom.json` —— 首页所有帖子标题 `H3 14px weight 400`；`/` 页按钮 `Logout 10px / New Post 12px / Copy 10px`；`/post/1` 标题 `H1 16px`。
- **影响**：低-中（长时间阅读疲劳；对 1440 宽屏内容更显稀疏）。
- **建议**：正文/按钮至少 13–14px，页面标题 20–24px，帖子标题 16px 且加粗。

#### D2. 首页没有 H1/H2 标题层级，视觉重点缺失

- **表现**：首页 DOM 中 `h1`/`h2` 均为空，页面只有帖子标题 `h3`（14px/400）。"MISSION CONTROL"、"Hot/Latest" 等板块均不是标题元素；整页没有明确的视觉锚点与文档大纲。
- **证据**：首页 DOM 采集 `h1: [], h2: []`，仅 10 个 `h3`。
- **影响**：低（HTML 语义 + 视觉层级问题，无障碍/SEO 不友好）。
- **建议**：为"Mission Control"等板块补 `h1`（页面主标题）与 `h2`（板块标题）。

#### D3. 顶部导航在 375px 移动视口横向溢出（根因已定位）

- **表现**：375px 视口下 `documentElement.scrollWidth=588 > clientWidth=375`，页面可横向滚动；顶栏右侧"New Post / admin / Logout"集群被推出屏幕外（x=294→588）。
- **根因**：顶栏右集群是 `flex items-center gap-3 shrink-0`（`shrink-0` 拒绝收缩），中段搜索框占宽后无换行/折叠，集群整体溢出。
- **证据**：375px 视口 DOM 测量（元素 `left=294 right=588`）；`ui-shots/mob-home.png`。
- **影响**：中（移动端是产品宣传重点场景，横向滚动是明显硬伤；功能问题 L15 的根因所在）。
- **建议**：窄屏下隐藏/折叠搜索框与右侧次要项（如 Logout 收进菜单），或允许搜索框收缩。

#### D4. 按钮与组件样式不统一（圆角/边框/字号混用）

- **表现**：同屏按钮圆角有 `0 / 6px / 8px / 9999px` 四种；边框色混杂 `rgb(35,45,66)`（正常暗色）与 `rgb(229,231,235)`（近白，暗色主题下突兀）；字号 10/11/12/16px 不一；"New Post"（12px 白字无底色）与"Post Comment"（12px 绿底）主次不分。
- **证据**：`design_dom.json` —— `/` 页按钮 `radius: 0/6px/8px/9999px` 混用、`/post/new` 的 `Publish Vibe Post` 与 `AI Polish Prompt` 视觉权重接近。
- **影响**：低-中（暗色终端风下"哪个是主按钮"需要用户猜）。
- **建议**：收敛按钮尺寸/圆角/边框色令牌，主按钮统一绿色填充，次按钮统一描边。

#### D5. 页面标题命名与字号不统一

- **表现**：各页标题风格零散：`$ Messages`、`$ Audit Queue`、`$ Dashboard`（带 `$` 前缀）、`My Drafts`、`Hot Tags`、`Vibe Prompt Studio`、`Search: react`（带冒号）、`System Admin`（用户主页，24px 无衬线，是全站最大标题）。同为 16px mono 的页面标题 vs 24px sans-serif 的用户页标题对比明显。
- **证据**：`design_dom.json` —— `/user/1` H1 `24px` sans-serif；其余页面 H1 多为 `16px` mono。
- **影响**：低（品牌一致性细节）。
- **建议**：统一页面标题组件（前缀符号、字号、字重）。

#### D6. 评论区输入框边框过亮，与暗色主题不符

- **表现**：评论输入框边框 `rgb(229,231,235)`（近白），在深色背景上像"白框"，与页面其它输入框（`rgb(35,45,66)` 暗边框）不一致。
- **证据**：`design_dom.json` —— `/post/1` textarea border `rgb(229,231,235)`，而 `/post/new` 标题输入框 border `rgb(35,45,66)`。
- **影响**：低（细节瑕疵）。
- **建议**：统一输入框边框色。

#### D7. Admin 仪表盘信息密度偏低（视觉上空）

- **表现**：`/admin/dashboard` 仅 3 张统计卡片（Total Posts / Pending Audits / Today's Posts），无任何趋势/图表/最近活动，大块留白；视觉模型评价"信息密度低，概览页更像占位页"。
- **证据**：`ui-shots/desk-admin-dashboard.png`。
- **影响**：低（功能不缺，但设计完成度不足）。
- **建议**：补充帖子增长趋势图、频道分布、最近审核动态等。

#### D8. Prompt 模板详情页信息过载，面板间距紧凑

- **表现**：`/post/100` 一屏内堆叠：作者区、标题+3 个标签、正文、代码块、互动区、AI Score、Playground（2 输入框+预览+Copy）、操作按钮；"3d ago" 等小标签字号过小；Playground 预览区行距较密。
- **证据**：`ui-shots/desk-post-prompt.png`（视觉模型分析）。
- **影响**：低（核心内容可用，但主次不够分明）。
- **建议**：将 Playground/版本历史折叠为可展开面板，或给主内容区更宽松的间距。

#### D9. 首页频道卡片大数字（128/64/48/72/36）视觉上"假"

- **表现**：卡片右侧的大数字与真实帖子数（0~2 条）严重不符，视觉模型直接把它们当"帖子数/成员数"理解；属于功能问题 M8（硬编码计数）在视觉层的放大效应。
- **证据**：`ui-shots/desk-home.png`；功能根因见 M8（`HomePage.tsx:20-25`）。
- **影响**：中（真实用户会被数字误导，产生"社区很热闹"的错误预期，属数据可信度问题）。
- **建议**：修复 M8 后自然消失；若暂不修复，建议先去掉数字或改为后端真实统计。

#### D10. 错误/空状态页缺乏设计区分

- **表现**：Agent Logs 崩溃页（"Something went wrong"）与 404 页（"404 — Page Not Found"）在样式上几乎相同（20px mono 标题 + 相同布局），用户无法区分"系统故障"与"地址不存在"；且崩溃页没有"返回/重试"引导按钮。
- **证据**：`ui-shots/desk-agent-logs.png`、`ui-shots/desk-notfound.png`。
- **影响**：低（错误页可用性问题；崩溃本身是功能问题 H2）。
- **建议**：错误页区分错误类型，提供"返回首页/重试"操作入口。

### 6.3 小结

视觉语言（暗色终端 + 语义色）整体统一且有特色，主要问题集中在：**可读性（字号偏小）、一致性（按钮/标题/边框风格零散）、移动端溢出（顶栏）、数据可信度（频道大数字）**。前两者属于打磨类改进，后两者建议优先处理（D3 移动溢出 + D9 假数字，均会直接损害真实用户体验与信任）。

---

*报告由 PenguinHarness（产品体验 Agent）生成，所有结论均经复现与源码/API/视觉取证，未擅自修改任何代码。*
