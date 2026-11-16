# 设计改造修复轮 B（design-overhaul-fixes-b）

Status: **已实现，待独立验证（2026-11-16）**。契约：[plans/design-overhaul-fixes-b.md](../plans/design-overhaul-fixes-b.md)
（冻结提交 b66bdad，基线 master 3f6e8ce）；规范：[docs/design/ui-visual-system-v2.md](../design/ui-visual-system-v2.md)；
ADR 修订：[docs/adr/0020-the-forge-pivot-replaces-the-terminal-aesthetic.md](../adr/0020-the-forge-pivot-replaces-the-terminal-aesthetic.md)
末节 Amendment。实现分支 `codex/design-overhaul-fixes-b`，worktree
`.claude/worktrees/design-fixes-b`。本文件由实现方撰写；验收结论以独立 Verifier 的重跑证据为准。

## Scope

2026-11-16 冻结的 15 项：信息架构去仪表盘化（B1、B2 修订、B3、B4 修订、B7）、签名面
（B8、B9、D7）、v1 能力捡回（D1–D6、B5、B6）、数据策展（B10）。不改路由、不新增运行时依赖、
不改数据库 schema、不碰 docker/.env 与线上数据。

## Acceptance（对照契约 7 条）

1. **首页无宫格；登录态 = 主推 + tabs + 列表** — `HomePage` 删除 `CHANNELS`、`channelLabel`、
   `ChannelStats` 查询与整段 7 列宫格；`ShowcaseEntry` 在 tabs 上方常显（登录/访客都先看到
   真实评审主推），访客态额外保留 Mission Control。1280px 下 `home-latest-*` 截图可见 8 行；
   线上热门榜只有 3 行数据，故默认 `home-*` 截图为 3 行，密度证据由 latest 页补足。
2. **评审凭证默认可见** — `AiReviewTerminal` 头部凭证常显：结论/分数/severity/模型
   (`detail.reviewer`)/调用次数（同帖 review-log 行数）/原始日志链接；findings 与四步
   replay 仍默认折叠。`AiReviewService` 在既有 `result_json` 写入 `durationMs`（无新列），
   DTO/service 读取后前端按存在与否展示；四状态与 replay 语义未动。`mvn -B test` 541 全绿。
3. **envelope 中性化 + 词典展示** — 登录/发帖/点赞后端 message 改为中性中文；前端登录欢迎、
   发帖成功走词典（点赞成功本就没有前端提示，失败提示走词典）。断言钉住后端三条 envelope
   与前端登录成功 toast 的词典来源。
4. **行四件 + 代码预览；profile 三主格** — PostCard 行 meta 只保留频道徽章/作者/相对时间/
   右侧计数，单行不换行；role/模板移至详情页；首块代码可展开（默认折叠）。profile 三项主格
   + 一行次级文字（评论/平均 AI 评分/Fork/版本）。375px 行内计数完整可见（见偏差 2）。
5. **返回/主题/证据链接/按压/折叠态** — 公开面非首页渲染 `BackButton`；顶栏主题按钮登录/访客
   均可见；顶栏“证据”菜单含 ADR 索引/压测报告/观测演练；`.press-feedback` 提供
   `active:scale-[0.98]` 且 `motion-reduce:transform-none`；1024–1279 侧栏为 48px 图标栏，
   tooltip 悬停/聚焦可达，<1024 抽屉，≥1280 240px。
6. **B5/B6/D7 + demo-content.sql** — 语言按钮 aria/title 为当前语言完整句、可见字形不变；
   浅色代码块用 GitHub light token，暗色仍 `oneDark`；规范新增双方言原则；demo SQL 为 10 条
   真人向中文帖（含 2 条真实评审记录），insert-only。
7. **截图/计数/断言/验证** — 31 张截图（18 张同名重摄 + 13 张新增），IHDR 全部匹配视口；
   README 计数 540→541（与新增后端测试同步）；断言改动逐条见下；三套前端验证与
   `mvn -B test` 实现方自跑全绿（Verifier 需重跑）；无新增运行时依赖；`vite.config.ts` 最终 diff 为零。

## 实现记录（15 项）

### 信息架构

- **B1 删除频道宫格** — `HomePage.tsx` 只保留主推、tabs、列表、分页；频道导航唯一入口回到
  `Sidebar`。词典删除 `channelGridAria`。
- **B2(修订) 首屏结构** — 登录态：主推一条（`ShowcaseEntry`，真实评审）→ tabs → 列表；
  访客态额外显示 Mission Control。无任何宫格或仪表盘块。
- **B3 行 meta 减法** — `PostCard` 删除 role span 与模板徽章；meta 行固定为频道徽章、作者、
  相对时间、右侧计数（AI 分/赞/评论/浏览），`overflow-hidden whitespace-nowrap` 保证单行；
  role/模板在 `PostDetailPage` 元数据行显示（`readRole` 复用既有解析）。
- **B4(修订) profile 统计** — 三项主格：帖子数、收到的赞、被评审过；其余数字收进一条
  `stats.secondaryLine`。被评审过由客户端已加载的该用户帖子推导
  （`aiReviewed === AI_REVIEW.REVIEWED`），加载中显示 `--`；不新增统计接口。
- **B7 删除“等待回答”右栏** — `ChannelPage` 移除 unanswered 查询、`useIsDesktop`/
  `DESKTOP_QUERY`、右栏与 `PostPageVo`/`useEffect`/`Link` 依赖；频道页改为单列 feed。
  频道化 API 能力保留，前端不再调用。

### 签名面

- **B8 评审凭证** — `AiReviewTerminal` 新增 `reviewCount` prop 与
  `data-testid="review-voucher"`：首行结论/分数/severity/verdict/展开按钮，次行模型、调用次数、
  可选运行时长、`/agent-logs?postId=<id>` 原始日志链接。findings 与 replay 仍在
  `expanded=false` 的折叠区。`PostDetailPage` 对已评审帖始终拉取
  `/agent-logs/post/{id}` 历史并以行数作为调用次数（与历史面板同源，不会两处不一致）。
  后端 `AiReviewService` 用 `System.nanoTime()` 计时，`withDuration` 把 `durationMs` 合并进
  既有 `resultJson`；null/非 JSON payload 原样保留（空 result_json 仍表示“未完成”）；
  `AiReviewDetail` 增加可空 `durationMs`，`AiReviewDetailService` 仅对数字字段读取。
  `AgentLogsPage` 接收 `?postId=`，查询键与参数同步，并显示可清除的筛选 chip。
  规范 AiReviewTerminal 段改写为“凭证默认可见、findings 折叠”，并在 ticket/ADR 记录对原
  “整卡折叠”决策的反转；ADR-0010 的“不得读作实时”与四状态/回放语义不变。
- **B9 语气统一** — `AuthController` 登录：`登录成功，欢迎回来，<nickname>。`；
  `PostController` 发帖：`帖子发布成功。`；点赞：`点赞成功。`。前端登录/发帖成功文案本就走
  词典（`login.welcome`、`published`），点赞成功无前端提示；新增断言钉住这些入口。
- **D7 双方言原则** — 规范新增第 7 条：公开面 forge，运维面（`/agent-logs`）保留 terminal
  方言；登录/发帖/点赞的俏皮 envelope 属于方言泄漏，后端 envelope 用中性中文。

### v1 遗产

- **D1 列表代码预览** — `getPreview()` 结果接 chevron 按钮（`aria-expanded`、
  `aria-label` 含预览类型），默认折叠；展开后显示首块 fenced code 或前两行纯文本，上限
  `max-h-48`。用普通 `<pre>` 而非 `CodeBlock`：列表不引入高亮体积，详情页仍有完整高亮。
- **D2 全局返回** — `MainLayout` 在 `pathname !== '/'` 时渲染 `BackButton`；按钮文案走
  `common:back` 词典，保留 history idx 判断与 `navigate(-1)`/首页回退。
- **D3 顶栏主题切换** — 顶栏新增 Sun/Moon 按钮，登录/访客均可见；沿用 `themeStore`
  （系统默认 + 手动持久化），用户菜单中的重复项删除。选择：一个控制、一个位置。
- **D4 顶栏证据链接** — 新增“证据”下拉（ADR 索引、压测报告、观测演练），复用 footer 的
  三个链接常量与 `ExternalLink`；`<xl` 只显示图标并由 aria-label 命名，1280 显示文字；
  1024 截图无溢出。
- **D5 按压反馈** — `index.css` 新增共享 `.press-feedback`（`active:scale-[0.98]` +
  `motion-reduce:transform-none`），应用于 Navbar、Sidebar、PostCard、BackButton、
  LanguageToggle、Pagination、MobileTabBar、ShowcaseEntry 等可点元素。编译产物已确认
  `.press-feedback:active` 与 reduced-motion 覆盖均存在。
- **D6 侧栏折叠态** — `Sidebar` 三档：<1024 抽屉全标签；1024–1279 `lg` 48px 图标栏，
  标签 `sr-only` 保留在可访问树，`title` 与 hover/focus tooltip 提供名称；≥1280 `xl`
  恢复 240px。`MainLayout` aside 在 `lg` 用 `overflow-visible` 让 tooltip 越出，`xl`
  恢复自身滚动。
- **B5 语言按钮 aria** — `aria-label`/`title` 用当前语言的完整句命名目标
  （`language.switchToEnglish` / `switchToChinese`）；可见字形仍为 EN/中。
- **B6 浅色代码块** — `CodeBlock` 按 `themeStore.dark` 在 `oneDark` 与自定义 `githubLight`
  间切换；浅色 token 取 GitHub light 值并在代码注释列出对比度（正文 15.0:1、注释 6.3:1、
  关键字 5.9:1、字符串 11.2:1 等，均 ≥4.5:1）；未知语言回退改用 `bg-vibe-bg text-vibe-text`。

### 数据策展

- **B10 demo 数据** — `db/mysql/demo-content.sql` 重写为 10 条中文帖（频道 1–7；2 条 prompt、
  1 条 pending 状态帖），含标签、2 个 prompt 版本、6 条评论、3 条消息。showcase 候选为 post 3，
  配两条 `ai_review_log`（id 9001/9002，`durationMs` 2210/1840）与真实评语；文件仍为
  insert-only、无 DELETE，保留 `DemoContentSeederTest` 所需的 INSERT 语句。仅对
  `DEMO_SEED_ENABLED=true` 且 `vibe_post` 为空的 MySQL 实例生效；线上库未动，H2
  `data.sql` 未改（不在 Allowed paths）。文件头注明可把 `SHOWCASE_POST_ID` 指向 3。

## 断言改动逐条记录

1. **`PostCard.test.tsx`（改）** — 原“held prompt ... and its role”改为
   “without the retired row metadata”：`getByText('reviewer')` 的存在断言改为
   `queryByText('reviewer')`/`queryByText('Template')` 的**不存在**断言。原因：B3 明确把
   role/模板移出列表行；这是契约要求的反向钉，不是弱化为通过。新增 D1 展开测试（默认
   `aria-expanded=false`、点击后代码出现、meta 仍是 `.whitespace-nowrap`）。
2. **`ChannelPage.test.tsx`（删/换）** — 删除 A4-4 的 4 条“等待面板/桌面门/空态/频道作用域”
   源码钉；同一个 describe 改为 B7 的 2 条反向钉（`waiting.ariaLabel`、
   `/posts/unanswered`、`useIsDesktop` 均不得出现）加 1 条空态钉。原因：B7 删除该功能及其
   查询，旧钉失去被测对象；删除已在此逐条留痕。
3. **`AiReviewTerminal.test.tsx`（增/改夹具）** — 全部 render 经 `renderTerminal` 包
   `MemoryRouter`（凭证新增内部 `<Link>`，组件现在需要路由上下文；断言本身未动）；
   `detail` 夹具加 `durationMs: 1234`；新增凭证测试，断言模型、`3 call(s)`、`1.2 s`、
   `/agent-logs?postId=1` 与 `aria-expanded=false`。
4. **`ReducedMotionTerminal.test.tsx`（改夹具）** — render 包 `MemoryRouter`，reduced-motion
   断言一字未动。
5. **`LoginPage.test.tsx`（增）** — 成功登录后断言 toast 文案来自词典
   （`Welcome back!`），钉住“前端走词典而非服务端 envelope”。
6. **`AiReviewServiceTest.java`（增）** — 既有评审成功用例追加断言：写入的 `resultJson`
   含 `"durationMs"`。
7. **`AiLogControllerIntegrationTest.java`（新增 1 个测试）** — 从 result_json 行暴露
   `durationMs`，覆盖 DTO 读取路径；这也是后端计数 540→541 的唯一来源。
8. **`AuthControllerIntegrationTest.java`（增断言）** — 登录成功响应 message 为
   `登录成功，欢迎回来，shing。`。
9. **`PostControllerIntegrationTest.java`（增断言）** — 发帖 message 为 `帖子发布成功。`；
   点赞 message 为 `点赞成功。`。

未被弱化或跳过的既有断言：除上述 B7 失去对象的 4 条外，无删除。

## 截图（验收第 7 条）

`docs/assets/design-overhaul/` 共 **31 张**：18 张同名重摄（home/channel/post/profile ×
明暗 × 1280x800/375x812，加 drawer-dark-375、post-prompt-dark-1280），新增 13 张：
`home-guest-{dark,light}-1280`、`home-latest-{dark,light}-1280`、
`home-preview-{dark,light}-1280`、`post-review-{dark,light}-1280`、
`post-code-light-1280`、`rail-{dark,light}-1024`、`rail-tooltip-{dark,light}-1024`。
全部用 PNG IHDR 校验：`-1280` 精确 1280x800，`-375` 精确 375x812，`-1024` 精确 1024x800。

方法：worktree 内 `npm run build` + `vite preview --port 4175`（生产构建，无 devtools 浮层），
`vite.config.ts` 代理临时 8081→8080，摄完 `git checkout` 还原（最终 diff 为零）；脚本
`scratch/design-overhaul-shots/capture-fixes-b.cjs`（worktree 外）驱动 ms-playwright
缓存的 chromium-1248，每张独立 context，`deviceScaleFactor: 1`、`locale: zh-CN`、
`networkidle` + 2.5s。登录态由真实 `POST /api/v1/auth/login` 取得 token，再以 zustand
persist 键 `nexus-vibe-auth` 注入 `addInitScript`；访客截图不注入。凭证/代码块/预览/rail
tooltip 截图先滚动或悬停再截视口。

截图使用线上库现有语料（契约禁止动线上数据）；B10 的新 SQL 只影响空库 demo 实例。线上
“热门”榜仅 3 行，所以默认首页截图 3 行，`home-latest-*` 用“最新”页展示 8 行以证明首屏密度。

## 偏差与实现决定

1. **越出 Allowed paths 的两处只读补链（必须知悉）** — `src/main/java/com/nexus/campus/dto/AiReviewDetail.java`
   与 `service/AiReviewDetailService.java` 不在契约 Allowed paths，但 Acceptance 2 要求
   “运行时长来自 resultJson 打点”并在前端展示；仅改 `agent/**` 只能写入、无法读到。
   改动为最小可加：DTO 增可空 `durationMs` 字段，service 增一个数字读取 helper；无 schema、
   无接口、无既有字段语义变化。若验收方判定越界，这是本轮唯一的路径偏差。
2. **375px 隐藏 hover 快捷动作** — `PostCard` 的“复制 prompt/复制链接”在 `<sm` 设
   `hidden`。它们是 hover-only，触屏不可达；保留其布局宽度会把单行 meta 的右侧计数挤出
   视口（修前 375 截图实测 meta 仅 231px、计数被裁）。桌面与 ≥640px 行为不变，详情页仍有
   复制入口。此项是 Acceptance 4“375 单行且计数可见”的必要条件。
3. **D1 用普通 `<pre>`** — 不在列表行复用 `CodeBlock`，避免把语法高亮打进 feed chunk；
   展开块仍是单行等宽预览，详情页高亮不变。
4. **调用次数口径** — 复用 `/agent-logs/post/{id}` 的既有语义（仅 code-review-agent、
   `result_json` 非空、LIMIT 20）的行数，不新增 count 查询。超过 20 次时凭证显示 20，
   与历史面板一致；这是既有端点的上限，不是本轮的计数错误。
5. **时长只对新评审可见** — 线上历史行没有 `durationMs`，凭证按“存在才显示”处理；截图
   中的线上 showcase 因此不显示时长，组件测试与 demo SQL（2210/1840ms）覆盖存在时的渲染。
   不回填线上数据、不伪造旧行时长。
6. **B10 只覆盖空库 MySQL** — `DemoContentSeeder` 的既有条件是 `vibe_post` 为空；H2
   profile 使用另一份 `data.sql`，不在 Allowed paths，未同步。文件头句已改为如实描述，
   不再声称两份内容相同。
7. **规范 D6 文字仍写两档宽度** — 规范侧栏段只描述 240px + 抽屉；D6 的实现是三档
   （48px rail）。契约只授权 B1/B8/D5/D7 四处规范同步，故未改该段，在此留痕；若需要
   文档一致，应另开一次规范修订。
8. **ADR-0020 以 Amendment 记录反转** — 不改写原决策正文，只在文末追加 2026-11-16
   Amendment，说明“整卡折叠”被“凭证默认可见、findings 折叠”取代，数据契约/四状态/
   回放/ADR-0010 规则不变。
9. **验证环境的两处瞬态故障（如实记录）** — 本机空闲物理内存一度约 0.4–0.6GB，Vitest
   默认 16 个 fork 在启动阶段两次 `exit code 134`（worker OOM，非断言失败）；同一命令随后
   全绿（22/120）。后端也出现一次只跑到 58 tests 的 `BUILD FAILURE`（无失败用例行，随后
   立即重跑 541 全绿）。最终证据均来自全绿的重跑；Verifier 若遇同类 worker 退出，可用
   `VITEST_MAX_WORKERS=4 npm run test` 约束并发，无需改配置。

## Files

- **前端源码**：`frontend/src/components/{AiReviewTerminal,BackButton,CodeBlock,LanguageToggle,MobileTabBar,Navbar,Pagination,PostCard,ShowcaseEntry,Sidebar}.tsx`、
  `frontend/src/components/layout/MainLayout.tsx`、`frontend/src/pages/{AgentLogsPage,ChannelPage,HomePage,PostDetailPage,UserProfilePage}.tsx`、
  `frontend/src/i18n/locales/{zh,en}.json`、`frontend/src/index.css`、`frontend/src/types/post.ts`。
- **前端测试**：`AiReviewTerminal.test.tsx`、`PostCard.test.tsx`、`ReducedMotionTerminal.test.tsx`、
  `ChannelPage.test.tsx`、`LoginPage.test.tsx`。
- **后端源码**：`agent/AiReviewService.java`、`controller/{AuthController,PostController}.java`、
  `dto/AiReviewDetail.java`、`service/AiReviewDetailService.java`。
- **后端测试**：`agent/AiReviewServiceTest.java`、`controller/{AiLogControllerIntegrationTest,AuthControllerIntegrationTest,PostControllerIntegrationTest}.java`。
- **数据/文档**：`src/main/resources/db/mysql/demo-content.sql`、`README.md`（仅计数）、
  `docs/design/ui-visual-system-v2.md`（B1/B8/D5/D7）、`docs/adr/0020-*.md`（Amendment）、
  `docs/assets/design-overhaul/**`（31 张）、本 ticket。

## Rejected

- **为 durationMs 新增数据库列** — 契约明令 schema 不新增列；resultJson 打点已足够且保留
  原始评审证据。
- **新增“评审次数”统计接口** — 既有 `/agent-logs/post/{id}` 已返回同帖行；再开接口会让
  凭证与历史面板出现两套口径，且 service/mapper 不在 Allowed paths。
- **把 role/模板留在列表行以便旧测试通过** — B3 的定案就是减法；测试改为反向钉，而不是
  保留两处重复信息。
- **375px 保留 hover 快捷按钮并缩写计数** — 会隐藏 Acceptance 4 要求的右侧计数；隐藏
  触屏不可达的 hover 动作更诚实。
- **列表预览复用 CodeBlock 高亮** — 增加 feed chunk 体积，收益只是列表里的装饰性着色。
- **回填线上旧评审行的 durationMs** — 违反“线上数据不动”，且会把未知耗时伪造成测量值。
- **改写 ADR-0020 正文** — ADR 只在新增 ADR 或 Amendment 中改主意；契约允许后者。
- **同步改 H2 `data.sql`** — 不在 Allowed paths；B10 的验收对象是 MySQL demo seed。
- **截图改用 dev server 或 Edge** — 上一轮已证实 devtools 浮层与 Edge 壳层会污染画面；
  继续用生产 build + bundled Chromium。

## 实现方自跑证据（Verifier 需重跑，不采信本段）

```
cd frontend && npm run test   → Test Files 22 passed (22); Tests 120 passed (120)
cd frontend && npm run lint   → oxlint，0 告警
cd frontend && npm run build  → tsc strict + vite build，✓ built
mvn -B test                   → Tests run: 541, Failures: 0, Errors: 0, Skipped: 0
                                All coverage checks have been met.; BUILD SUCCESS
grep -rn 'lg:grid-cols-7\|channelGridAria\|Neural link\|Data injection protocol\|Energy increment' frontend/src src/main/java
                              → 0 行（exit 1）
grep -rn 'active:scale' frontend/src | wc -l → 1（index.css 的 .press-feedback）
编译 CSS                       → .press-feedback:active{...scale .98...}；
                                @media (prefers-reduced-motion:reduce){.press-feedback{transform:none}}
git diff -- frontend/vite.config.ts → 空（代理已还原 8081）
```
