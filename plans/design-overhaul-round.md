# 契约草案 — 设计改造轮（forge 风格 + 中英文 i18n）

任务 ID：`design-overhaul-round` · 配套 ticket：[docs/tickets/design-overhaul-round.md](../docs/tickets/design-overhaul-round.md)
状态：**草案，待用户评审后冻结。未冻结不得实现。**

## Allowed paths

```
frontend/src/**                 # 全部前端代码（组件、页面、样式、i18n、测试）
frontend/tailwind.config.js
frontend/package.json           # 仅允许新增 i18next + react-i18next（含 lock 文件）
frontend/index.html
docs/adr/0020-*.md              # 新 ADR：视觉语言转向 + i18n 引入，取代旧视觉约束
docs/design/ui-visual-system.md # 更新或标注被 0020 取代
docs/design/ux-architecture.md
docs/tickets/design-overhaul-round.md
docs/assets/**                  # 改造后截图（路径以仓库根为基准）
README.md                       # 仅测试计数徽章与 Testing 段
```

## Acceptance criteria

M1 — i18n（从零）：
1. 引入 `i18next` + `react-i18next`，中文为源语言，新增 `en` 全量翻译；语言切换入口在导航栏/设置页，选择持久化（localStorage），默认跟随 `navigator.language`，回退中文。
2. 前端自有文案（页面标签、按钮、空态、toast、错误提示）全部走 i18n 词典：`frontend/src` 中除 locale 文件与测试外，grep 汉字命中为 0（现状 92 行散布 8 个文件）。
3. 现状的中英混排统一收口：Sidebar 与 HomePage 重复的频道定义合并为单一来源，频道名/描述进词典。
4. 明确不做后端 i18n：API envelope 的中文 `message` 原样透传（`serverErrorMessage.ts` 行为不变，测试不动）。

M2 — 视觉改造（GitHub/linux.do 方向）：
5. 移除特效组件 `BorderBeam`、`DecryptedText`、`ShimmerButton`、`SpotlightCard`：源代码中零引用（grep 可验证），组件文件删除；其测试一并删除（属正常退役，记录于 ticket）。
6. AiReviewTerminal 保留（它是项目主题本身），按新视觉规范重绘：保留数据契约、四种状态与 replay 步骤条语义，移除流光边框与解码动画；`AiReviewTerminal.test.tsx` 的步骤顺序/分数断言保留（文案断言随词典更新，不算弱化）。
7. 视觉 token 重定：中性灰阶表面 + 单一强调色 **GitHub 蓝**（亮 `#0969DA` / 暗 `#58A6FF`；2026-11-14 已定案，翡翠绿、青紫霓虹配对退役），语义色亮 `#1A7F37`/暗 `#3FB950`（成功）、亮 `#CF222E`/暗 `#F85149`（危险）；等宽字体仅用于代码块与代码内容，标题/按钮/toast 回归无衬线；点阵背景移除。
8. 骨架屏统一为单一共享组件（现状 5 处逐字复制的 shimmer 块），纯 pulse 动画。
9. 明暗双主题均可用，对比度达 AA（沿用现有 `.dark` class 机制与 themeStore）。
10. 改造后截图（首页、频道页、帖子详情、个人主页，明暗各一组）提交至 `docs/assets/`，在 ticket 中引用。

通用：
11. `npm run test` / `npm run lint` / `npm run build` 全绿；测试计数变化同步更新 README 徽章与 Testing 段（同一提交内）。
12. 文案变更导致的断言更新必须与词典条目一一对应，禁止改/删/弱化断言换通过。

## Verification commands（Verifier 亲自重跑）

```bash
cd frontend && npm run test
cd frontend && npm run lint
cd frontend && npm run build
grep -rnP '[\x{4e00}-\x{9fa5}]' frontend/src --include='*.tsx' --include='*.ts' \
  | grep -v 'i18n' | grep -v '\.test\.'    # 期望 0 行
grep -rn 'BorderBeam\|DecryptedText\|ShimmerButton\|SpotlightCard' frontend/src   # 期望 0 行
```

证据绑定 HEAD SHA + `git status --short`；Verifier 用全新 subagent 独立执行。

## Forbidden

- 后端 `src/**`、数据库、docker、`.env` 一律不碰。
- 不新增 i18next/react-i18next 以外的运行时依赖。
- 不删除或弱化测试断言换通过；不关 lint/typecheck。
- 不动用户生成内容的存储与展示逻辑（帖子/评论正文不进词典）。
- 不改路由结构与状态管理层（zustand/TanStack Query 用法不变）。
- 不重写已发布历史；实现走 `codex/<slug>` 分支 + PR。
