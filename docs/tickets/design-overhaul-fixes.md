# 设计修复轮：外审 A 档无争议缺陷（design-overhaul-fixes）

Status: **已验收（2026-11-16，签收）**。契约：[plans/design-overhaul-fixes.md](../plans/design-overhaul-fixes.md)；
规范：[docs/design/ui-visual-system-v2.md](../design/ui-visual-system-v2.md)。实现分支 `codex/design-overhaul-fixes`
（worktree `.claude/worktrees/design-fixes`，基线 c740aa8，契约冻结提交）。

## Scope

2026-11-16 外部锐评 7 组发现逐条手验后的 A 档 9 条：severity 徽章走词典、pending 态走词典、
unanswered 频道化（前后端）、注册按钮移动端可见、palette 提示按平台、document.title 随语言、
18 张截图干净配置重摄、README 两张 v1 截图换新 UI、门禁补课。B 档（首页宫格去留、首屏结构、
行 meta 减法、profile 统计权重、语言按钮 aria 惯例、浅色代码块）不在本契约，走单独访谈。

## Acceptance（对照契约 9 条的实现记录）

1. **severity 走词典** — `AiReviewTerminal.tsx` 的 `SeverityBadge` 接 `agentLogs:severityNames`
   （与 AgentLogsPage 共用同一词典块，不再逐面复制）；未知 key 回退 `defaultValue: normalized`。
   新增测试钉 zh 渲染（`高`，且不再出现原始 key）。
2. **pending 态走词典** — pending 分支改 `t('post:aiPending')`，复用既有词典键
   （PostDetailPage 的等待横幅同键，两处文案从此一致）。en 词条由 "AI Agent reviewing..."
   改写为 **"AI review in progress..."**（zh 不变："AI Agent 正在评审..."）——原 en 串
   作为字面量会同时命中契约 grep 门禁，且钉句测试随之更新（见断言记录 1）。
3. **unanswered 频道化** — 后端：`PostController.getUnansweredPosts` 增加可选
   `@RequestParam(required=false) Long channelId`；`VibePostService` 接口签名改为
   `getUnansweredPosts(int size, Long channelId)`（唯一调用方是 controller，无其他调用者需要兼容）；
   `VibePostServiceImpl` 在 `channelId == null` 时走原路径逐字节不变；非空时取
   `MAX_UNANSWERED_SIZE`(20) 条按 `categoryId` 过滤后 `limit(size)`。**/repository 与 mapper
   不在 Allowed paths 内**，过滤只能在 service 层内存完成；频道未答帖超过 20 时右栏是子集
   （代码注释与 ticket 双留痕）。前端：`ChannelPage` 的 unanswered query 带上
   `channel?.id`（queryKey 与 params 同步），queryKey 频道感知防缓存串频道。后端测试
   `VibePostServiceImplTest.getUnansweredPosts_channelScopedFilter` 覆盖：全站不含 channelId
   两帖均可见、channelId=2 只留频道 2、channelId=3 镜像。
4. **注册按钮移动端可见** — Navbar 注册入口去掉 `hidden ... sm:inline-flex`，改常显
   icon+文字（UserPlus 图标，px-2 → sm:px-3 随视口放宽），登录 icon 按钮不动。
   重摄的 375px 截图可见双入口。
5. **palette 提示按平台** — `common:search.openPaletteHint`（"Ctrl K"）与新增
   `openPaletteHintMac`（"⌘ K"）双词条双语言；Navbar 按
   `/Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent)` 择一。
   **CommandPalette 检查过**：其 footer 提示是 `palette.footer.modifier` = "ctrl/⌘"
   （平台无关合并词，有测试钉住），非同一字面量，未动。
6. **document.title 随语言** — 新增 `common:site.title`（zh："Nexus-Vibe | AI 开发者社区"；
   en："Nexus-Vibe | AI Developer Community"）；`i18n/index.ts` 在 bootstrap 完成后与
   `setAppLanguage` 的 `changeLanguage` 回调里各自应用（同步 t() 会读到旧语言词典，故走回调）；
   `setAppLanguage` 返回值从 `void` 改为 `Promise<void>`（调用方 LanguageToggle 忽略返回值，
   行为不变），使标题应用可被测试等待。`index.html` 静态 zh 标题保留为首帧默认。
   新增 `frontend/src/i18n/index.test.ts` 钉两种语言的标题。
7. **截图重摄（干净配置）** — 18 张 PNG 全部同矩阵同名重摄（4 页 × 明暗 × 1280x800/375x812
   + drawer-dark-375 + post-prompt-dark-1280）。方法与本轮两个新发现见下"截图方法"。
8. **README 两张 v1 截图换新** — `async-review.png`（帖子详情，v2 折叠 AI 评审卡，
   评分 50/100 · 低 · 通过）、`community-feed.png`（v2 首页 feed），暗色 1280x800，
   生产构建渲染。alt 与图片说明随实更新（评分 70/100 → 50/100；"mission control" →
   任务中心 hero；v1 说明里"五账号点赞"等不可再证的句子移除）。README 其余正文一字未动。
9. **门禁补课** — `grep -rn 'AI Agent reviewing\|agent\.ticker' frontend/src` = **0 行**；
   `grep -rn 'severityNames' frontend/src/components/AiReviewTerminal.tsx` 命中（词典接线）；
   `npm run test`（120/22 全绿）、`npm run lint`（0 告警）、`npm run build`（tsc strict +
   vite build 通过）、`mvn -B test`（**540 tests / 91 classes**，0 失败，JaCoCo 0.70 floor
   通过）——均为实现方自跑，Verifier 需重跑。

## 断言改动逐条记录

1. **AiReviewTerminal.test.tsx** pending 钉句 `'AI Agent reviewing...'` →
   `'AI review in progress...'`：钉句随词典键迁移（en 词条改写原因见 Acceptance 2），
   语义不变（pending 态显示评审进行中文案）。
2. **AiReviewTerminal.test.tsx** `afterEach` 增 `await i18next.changeLanguage('en')`：
   新增 zh 渲染测试切了语言，恢复默认避免同文件后续测试读到 zh。既有断言无一删改。
3. 新增断言（非改动）：zh severity 渲染钉（`高`）、document.title 双语言钉、
   ChannelPage unanswered 查询频道感知源码钉。无任何断言为通过而弱化。

## Files

`frontend/src/components/AiReviewTerminal.tsx`、`frontend/src/components/Navbar.tsx`、
`frontend/src/pages/ChannelPage.tsx`、`frontend/src/i18n/index.ts`、
`frontend/src/i18n/locales/{zh,en}.json`、对应 4 个测试文件、
`src/main/java/com/nexus/campus/controller/PostController.java`、
`src/main/java/com/nexus/campus/service/VibePostService.java`、
`src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`、
`src/test/java/com/nexus/campus/service/VibePostServiceImplTest.java`、
`docs/assets/design-overhaul/**`（18 张）、`docs/assets/readme/{async-review,community-feed}.png`、
`README.md`（徽章 + Testing 段计数 + 两条 alt/说明）、`frontend/vite.config.ts`（截图代理
临时改 8080，**已还原 8081**，最终 diff 为零）、本 ticket。

## 截图方法（2026-11-16，替代上一轮的 dev-server 方法）

`scratch/design-overhaul-shots/capture-fixes.cjs`（worktree 外）以 playwright-core 驱动
**本机 ms-playwright 缓存的 Chromium**（chromium-1248，非 Edge channel）无头渲染：每张独立
context，viewport 1280x800 / 375x812、`colorScheme` dark/light、`deviceScaleFactor: 1`、
locale zh-CN，networkidle + 3s 后截视口。入库前逐张校验 PNG IHDR（桌面精确 1280x800 /
移动精确 375x812），18 张 + README 2 张逐张目验四角干净。

**对上一轮方法的两个修正（外审发现"悬浮物"的真正来源）：**

- 悬浮的热带岛屿头像**不是浏览器扩展**——把 `--disable-extensions` 加上、换 bundled
  Chromium 后仍在。逐元素定位后确认是 **TanStack Query Devtools 的悬浮按钮
  （`tsqd-open-btn`）**，dev server（`npm run dev`）独有。本轮改以 `vite build` +
  `vite preview`（生产模式，:4175）渲染，悬浮物从根上消失。
- Edge channel 即使 `--disable-extensions` 也不可信（另有无扩展相关的壳层浮层风险），
  统一用 playwright 缓存的 Chromium。代理临时指向 8080（vite.config.ts 已还原）。

## 偏差与实现决定

- **`post:aiPending` 的 en 值改写**（"AI review in progress..."）：契约门禁 grep 的是整个
  `frontend/src`，词典文件也在其中；保留原串则门禁物理不可满足。zh 语义不变。
- **后端 channelId 过滤在 service 层内存完成**：repository/mapper 不在 Allowed paths，
  无法在 SQL 层过滤；取 20 条再过滤的近似已在代码注释、本 ticket 与验收记录三处留痕。
- **`setAppLanguage` 返回 `Promise<void>`**：仅签名收窄（调用方不取返回值），为使
  document.title 断言可等待。
- **测试直接经 mapper 插入帖子而非 createPost**：createPost 触发的审核管线在 LLM 不可达
  （本机常态）时异步把帖子 fail-close 成 status=2，读回放与截图都与管线竞速；直接插入
  status=1/comment_count=0 的行让测试确定性地只测 unanswered 过滤本身。
- **README 说明段（async-review 图下）重写**：评分 70/100→50/100（库中实值，截图可证）、
  "五账号点赞"等无法再证实的句子移除，改为只陈述图内可见与 ADR-0010 支持的事实。

## Rejected

- **severityNames 复制一份进 post 词典**：AgentLogsPage 已有同名块，跨命名空间引用
  （`useTranslation('agentLogs')`）即可，复制会造成两处漂移。
- **后端 SQL 层过滤 unanswered（改 mapper/repository）**：两文件不在 Allowed paths；
  越界即验收失败，代价（右栏最多 20 条窗口）对"等待面板"语义可接受并已留痕。
- **CommandPalette 的 "ctrl/⌘" footer 提示按平台拆分**：它是平台无关的合并词且有测试钉住，
  与"Ctrl K 硬编码"不是同一问题；拆分属 B 档打磨，不在本契约。
- **用持久化 user-data-dir 做干净配置**：playwright 非 persistent context 本就是干净 profile，
  问题根源在 devtools 浮层与 Edge 壳层，换渲染通道（生产 build + bundled Chromium）已解。
- **顺手修 B 档条目**（登录/注册对称性重设计等）：契约边界；B 档走单独访谈。

## 验收与签收记录

- **Verifier 独立复核**（2026-11-16，全新会话 verifier subagent）：绑定 HEAD `70717fe`，9/9 标准 PASS。证据：前端 120/22 + lint + build 全绿；后端 540/91 全绿 + JaCoCo 0.70 达标（一次 fork JVM OOM 与一次 jacoco.exec 损坏均为环境故障，`mvn clean` 恢复，无断言/配置改动）；18 张截图 IHDR 精确（9×1280x800 + 9×375x812）且抽验四角干净（"热带岛头像"真身为 dev server 的 TanStack Query Devtools 悬浮钮，生产构建重摄已绝）；README 两图 alt 与图内容吻合；35 文件全在 Allowed paths；断言 3 处改动全部留痕，零弱化零删除。两项知悉项：en pending 文案连带改写（"AI Agent reviewing..." → "AI review in progress..."，grep 门禁覆盖词典文件所致，zh 未动）；右栏子集语义（>20 条 unanswered 显示子集，service 层过滤，三处留痕）。
- **用户书面签收**（2026-11-16）："目验通过，书面签收 design-overhaul-fixes（9/9 PASS，绑定 70717fe），开 PR 走 CI，绿灯合并并冻结 B 轮"——用户已逐张目验 18 张重摄图 + 2 张 README 新图。
