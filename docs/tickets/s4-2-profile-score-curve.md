# S4-2 profile 评分曲线组件

Status: **实现完成，待独立验证**（2026-11-17；分支 `codex/s4-2-profile-score-curve`，
worktree `.claude/worktrees/s4-2`；契约冻结 `b3f1820`，基线 `codex/s4-1-user-review-index` `81b3ef7`）。

## Scope

在 profile 页把 S4-1 的证据索引画成一条评分曲线：数据点 = 该用户每帖最新一次 code review
（`reviewedAt` × `score`），只画真实数据，空就空态。手写 SVG，不引入图表库；溯源跳转留 S4-3。
同时把"存储 0-10 / 展示 0-100"的归一规则抽成单一来源，凭证卡与曲线共用。

## Acceptance（对照契约 7 条）

1. **曲线本身** — `ReviewScoreCurve` 渲染一条 `<polyline>` + 每记录一个 `<circle>`；X 按
   `reviewedAt` 升序（端点返回降序，组件内排序）、Y 映射 0–100；无渐变、无装饰动效。
2. **分数口径单一来源** — 新增 `frontend/src/lib/score.ts` 的 `normalizeReviewScore(score)`
   （`score <= 10 ? Math.round(score * 10) : Math.round(score)`），`AiReviewTerminal` 改为调用它。
   凭证卡展示值零变化由两条新增测试钉住（raw 8 → `Score: 80/100`，raw 85 → `Score: 85/100`）。
3. **空态** — `entries` 为空（或全部无可解析分数）时**不渲染 SVG**、不渲染 `data-testid`，
   只显示 `curve.empty`；加载态是 `Skeleton`、错误态是 `ErrorPanel`，三者互不混淆
   （`UserProfilePage` 里三个分支分开）。
4. **无障碍** — `role="img"` + 词典 `aria-label`（点数与分数区间）；`<table class="sr-only">`
   每行 = 日期 / 分数 / 帖子标题（纯文本，无链接）；每个点 `tabIndex=0` 且带
   `{{title}}/{{date}}/{{score}}` 的 `aria-label`；点旁另有一个透明的大命中区供触摸使用
   （`aria-hidden`，不进无障碍树）。
5. **i18n** — 全部文案走 `profile.curve.*` 词条，zh/en 齐平；组件内无硬编码英文文案
   （日期本地化走 `i18n.resolvedLanguage`）。
6. **门禁** — `npm run test` 130/130、`npm run lint`、`npm run build` 全绿；断言改动逐条留痕；
   `frontend/package.json` 零改动（无新依赖）。
7. **截图** — profile 明暗 × 1280/375 重摄，生产构建、干净配置、四角无外挂元素（见下节数据集说明）。

## 实现记录

- `frontend/src/lib/score.ts`（新增）：`normalizeReviewScore`，注释记录"为什么这里有第二套
  0-100 说法"——模型输出 0-10，少数旧行已是 0-100，展示层统一按 0-100 打印。
- `frontend/src/components/AiReviewTerminal.tsx`：第 179 行三目改为调用共享函数；展示值未变。
- `frontend/src/components/ReviewScoreCurve.tsx`（新增，261 行）：手写 SVG（600×200 视框、
  0/50/100 三条网格线、accent 点 + border 线）；tooltip 由 hover/focus 驱动并夹在视框内；
  图下一行 `curve.caption` 说明数据来源；`sr-only` 表格等价物。
- `frontend/src/pages/UserProfilePage.tsx`：新增独立 `useQuery(['agent-logs','user',id])`；
  section 插在统计与 recent activity 之间（契约指定位置），加载/错误/空/有数据四分支。
  统计卡不受这条 query 影响——它失败时只有曲线那段报错（注释写明了这一点）。
- `frontend/src/i18n/locales/{zh,en}.json`：`profile.curve.*` 11 个词条 + `failedLabels.reviews`。
- `README.md`：前端计数 120 → 130、22 → 24 files，说明文字加入曲线覆盖点。

## 测试改动逐条记录

新增 10 条测试（+2 文件），既有断言一条未删未改：

- `lib/score.test.ts`（2）：0-10 区间缩放（0/8/8.5/10）、已是 0-100 的行原样返回。
- `components/ReviewScoreCurve.test.tsx`（6）：每条记录一个点且有多段线；端点降序进、画面升序出
  （cx 严格递增 + aria-label 区间文案）；空数组不渲染 SVG 且显示空态文案；sr-only 表格
  逐行 date/score/title 且无链接；归一与凭证一致（raw 8 → 80、raw 85 → 85）；每个点
  `tabindex=0` 且 aria-label 形如 `AI score N out of 100`。
- `components/AiReviewTerminal.test.tsx`（+2）：raw 8 仍显示 `Score: 80/100`；raw 85 仍显示
  `Score: 85/100`——钉住抽函数前后凭证卡零变化。

## 数据集说明（截图口径，必须知悉）

本机 Docker Desktop 引擎当前不可用（API 500 + 启动 OOM），线上 MySQL 不在本地可达；
截图因此跑在**开发配置（H2 + `data.sql` 种子）**上，不是线上库。该种子下每个用户最多
**1 条** code review 记录（post 100 → user 1 分数 9；post 101 → user 2 分数 7；post 2 →
user 3 分数 5），所以曲线截图上只有 1 个点——这不是组件缺陷，是种子数据的事实。
仓库自带的 MySQL demo 数据集（`db/mysql/demo-content.sql`）也只有 3 条评审、最多 2 个点。
**结论留档**：当前任何数据集都画不出多段曲线；S4-4 若要展示曲线形态，需要先决定演示
数据是否补评审历史（那是数据变更，不在本票）。

### 截图清单（14 张，生产构建 + 明暗 × 1280/375）

本轮用 `vite preview`（生产构建，:4175）+ `playwright-core` 自带 Chromium，viewport
1280×800 / 375×812、`deviceScaleFactor: 1`、`colorScheme` 明暗、`networkidle` + 2s 静置。
IHDR 逐张校验：1280×800 与 375×812，无缩放。代理只在截图期间临时指向本地后端 :8082，
截图后已还原为 8081（提交里 `vite.config.ts` 无改动）。

| 文件 | 内容 |
| --- | --- |
| `profile-{dark,light}-{1280,375}.png` | profile 首屏（重摄，覆盖本轮资产） |
| `profile-curve-{dark,light}-{1280,375}.png` | 曲线区段滚入视口：1 个数据点、0/50/100 网格、caption |
| `profile-curve-tooltip-{dark,light}-1280.png` | 焦点态 tooltip：标题 / 90/100 / 日期 |
| `profile-empty-{dark,light}-{1280,375}.png` | user 4（bob，无评审记录）：空态文案，页面无 SVG |

已知环境噪声：截图顶部有"部分功能受限 / Search results may be incomplete"横幅——这是
开发配置下 Elasticsearch 未运行的能力降级提示，与本组件无关，也未做任何遮掩。

## Verification（实现方自跑，Verifier 须亲自重跑）

```bash
cd frontend && VITEST_MAX_WORKERS=2 npm run test
→ Test Files 24 passed (24) / Tests 130 passed (130)，Duration 61.99s

cd frontend && npm run lint      → oxlint，无输出（0 warning / 0 error）
cd frontend && npm run build     → ✓ built in 9.58s

grep -rnP '[\x{4e00}-\x{9fa5}]' frontend/src --include='*.tsx' --include='*.ts' | grep -v i18n | grep -v '\.test\.'
→ 3 行，全部是既有注释里引用的中文原文（LanguageToggle.tsx:9-10 的 "切换到英语"、
  ChannelPage.tsx:121 的 "等待回答"），本票新增 0 行（diff 未触碰这两个文件）
grep -rn 'recharts\|chart.js\|"d3' frontend/package.json
→ 0 行
grep -n 'score <= 10' frontend/src/components/AiReviewTerminal.tsx frontend/src/lib/*.ts
→ 仅 lib/score.ts 一处（AiReviewTerminal 改调 normalizeReviewScore）
```

注：本机内存紧张（约 1.1 GB 可用），Vitest 默认并行会以 worker 崩溃告终
（`errno -4094 / UNKNOWN`）；`VITEST_MAX_WORKERS=2` 是这台机器的可用配置，与断言无关。

## Files

- **新增**：`frontend/src/components/ReviewScoreCurve.tsx`、
  `frontend/src/components/ReviewScoreCurve.test.tsx`、`frontend/src/lib/score.ts`、
  `frontend/src/lib/score.test.ts`、`docs/tickets/s4-2-profile-score-curve.md`、
  `docs/assets/design-overhaul/profile-*.png`（重摄）。
- **修改**：`frontend/src/pages/UserProfilePage.tsx`、`frontend/src/components/AiReviewTerminal.tsx`、
  `frontend/src/components/AiReviewTerminal.test.tsx`、`frontend/src/i18n/locales/{zh,en}.json`、
  `README.md`（计数）。

## Rejected

- **引入 recharts / chart.js / d3** — 一个 polyline + 若干 circle 的需求不值得一条运行时依赖，
  契约也明令禁止；手写 SVG 让无障碍表格与点级焦点可控。
- **用 0-100 的假零基线把曲线"补成"完整坐标系** — 契约禁止零基线伪造；空数组就是空态。
- **在组件内再写一份 `score <= 10 ? *10 : 原值`** — 会与凭证卡形成两套口径分叉；抽到
  `lib/score.ts` 并用测试钉住凭证展示值不变。
- **让曲线共用统计卡的"被评审帖子数"** — 统计卡从 `posts` 列表推导，端点数据更权威；
  两者目前数值一致，但语义不同（一个按可见性过滤，一个按评审行），强行合并会把 UI 绑死在
  列表查询是否成功上。
- **点直接做成链接跳到评审详情** — 那是 S4-3 的范围；本票的表格与点标签都是纯文本，
  不提前引入跳转语义。
- **为截图临时改种子数据 / 用 mock 数据** — 会污染证据；宁可截图只有 1 个点，并把数据集
  事实写进本文件。

## 验收与签收记录

（待独立 Verifier 复核后填写）
