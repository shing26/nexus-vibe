# 契约 — S4-2 profile 评分曲线组件

任务 ID：`s4-2-profile-score-curve` · 父 spec：[docs/plans/s4-evidence-profile.md](../docs/plans/s4-evidence-profile.md)
状态：**已冻结（2026-11-17，用户指令"直接开 S4-2（profile 评分曲线组件，含空态与无障碍表格等价物）"）。**

前置：依赖 S4-1 端点 `GET /api/v1/agent-logs/user/{userId}`（实现于 `codex/s4-1-user-review-index`，PR #46）。
本票基于该分支工作；#46 先合。

## 目标

profile 页展示该用户的 **AI 评分曲线**：数据点 = S4-1 返回的每帖最新评审（时间 × 分数）。
只画真实数据；没有数据就不画（空态），不画零线、不补假点。溯源跳转留给 S4-3。

## 设计（写死，实现不得偏离）

**组件**：新增 `frontend/src/components/ReviewScoreCurve.tsx`，纯手写 SVG（**不新增任何图表库**）。

**数据**：`useQuery` 调 `/agent-logs/user/{userId}`（S4-1）。空数组 → 空态。

**分数口径**：与凭证卡完全一致——`score <= 10 ? score * 10 : score`，纵轴 0–100。
（存储 0-10、展示百分制的归一规则已在 `AiReviewTerminal.tsx:179` 存在，本组件必须复用同一规则，
不得各写一套；如能抽成共享函数则抽，抽走后 `AiReviewTerminal` 的展示值不得变化。）

**呈现**：
- 横轴 = `reviewedAt`（时间升序，左旧右新），纵轴 = 0–100；
- 点用 `vibe-accent`，线用 `vibe-border` 或 accent 的低透明度，不使用渐变；
- 悬停/聚焦一个点显示 tooltip（帖子标题 + 分数 + 日期）；
- 图下方一行 `text-xs text-vibe-muted` 说明数据来源：`t('curve.caption', { n })`（"来自 N 次评审记录"）。

**无障碍（硬要求）**：
- 图形 `role="img"` + `aria-label`（词典文案，含点数与分数区间）；
- 提供 **sr-only 表格等价物**：每行 = 日期 / 分数 / 帖子标题（纯文本，不带链接——跳转是 S4-3）；
- 每个可聚焦点有 `aria-label`；键盘可遍历点（`tabIndex` / 方向键不需要，Tab 遍历即可）。

**空态**：`t('curve.empty')`（"还没有可展示的评审记录"），不渲染 SVG。

**插入位置**：`UserProfilePage.tsx` 统计 section 与 recent activity 之间，独立 `<section aria-label={...}>`
带标题 `t('curve.title')`。

## Allowed paths

```
frontend/src/components/ReviewScoreCurve.tsx      # 新组件
frontend/src/components/ReviewScoreCurve.test.tsx # 组件测试
frontend/src/pages/UserProfilePage.tsx            # 插入 section + 查询
frontend/src/pages/UserProfilePage.test.tsx       # 若存在则补测试
frontend/src/components/AiReviewTerminal.tsx      # 仅当抽出共享的分数归一函数（展示值不得变）
frontend/src/lib/**                               # 共享归一函数（若抽出）
frontend/src/i18n/locales/{zh,en}.json            # curve.* 词条
README.md                                         # 仅测试计数（若变化）
docs/tickets/s4-2-profile-score-curve.md
docs/assets/design-overhaul/**                    # profile 截图重摄（曲线新增）
```

## Acceptance criteria

1. 有评审计录时渲染 SVG 曲线：点数 = S4-1 返回条数，纵轴 0–100，横轴按时间升序；无渐变、无装饰动效。
2. 分数归一与凭证卡一致（同一函数或同一规则，两处展示值对同一原始分数必须相同）；抽共享函数时凭证卡展示值零变化（测试钉住）。
3. 空态：空数组时不渲染 SVG，显示 `curve.empty`；加载/错误态不与空态混淆。
4. 无障碍：`role="img"` + `aria-label`；sr-only 表格含每行日期/分数/帖子标题；点可 Tab 聚焦且有 aria-label。
5. i18n：所有文案走词典（zh/en 齐平，汉字门禁 0 行）；无硬编码英文。
6. `npm run test` / `lint` / `build` 全绿；断言改动逐条留痕；不新增运行时依赖。
7. profile 截图重摄（明暗 × 1280/375），干净配置、四角无外挂元素。

## Verification commands（Verifier 亲自重跑）

```bash
cd frontend && npm run test && npm run lint && npm run build
grep -rnP '[\x{4e00}-\x{9fa5}]' frontend/src --include='*.tsx' --include='*.ts' | grep -v i18n | grep -v '\.test\.'   # 期望 0 行
grep -rn 'recharts\|chart.js\|d3' frontend/package.json                                                              # 期望 0 行
grep -n 'score <= 10' frontend/src/components/AiReviewTerminal.tsx frontend/src/lib/*.ts                             # 归一规则单一来源
```

## Forbidden

- 新增图表库或任何运行时依赖；`frontend/package.json` 零改动（除已有）。
- 后端改动（S4-1 已提供数据）；schema/docker/.env 不碰。
- 假数据、零基线、装饰性动画/渐变。
- 不删/弱化既有断言；`AiReviewTerminal` 既有展示值不得改变。
- 不重写已发布历史；实现走 `codex/s4-2-profile-score-curve` 分支 + PR。
