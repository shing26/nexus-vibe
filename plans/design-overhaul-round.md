# 契约草案 — 设计改造轮（forge 风格 + 中英文 i18n）

任务 ID：`design-overhaul-round` · 配套 ticket：[docs/tickets/design-overhaul-round.md](../docs/tickets/design-overhaul-round.md)
状态：**已冻结（2026-11-15，用户书面确认"冻结 design-overhaul-round.md 契约、走验收链路开工"）。**

规格来源：[docs/design/ui-visual-system-v2.md](../docs/design/ui-visual-system-v2.md)（实现级规范）与
[ADR-0020](../docs/adr/0020-the-forge-pivot-replaces-the-terminal-aesthetic.md)（转向决策）。
实现以 v2 规范为准；契约管边界与验收。

## 已定案的设计决策（2026-11-14/15，用户拍板）

| 决策点 | 定案 |
| --- | --- |
| 强调色 | GitHub 蓝（亮 `#0969DA` / 暗 `#58A6FF`），中性灰阶表面，青紫霓虹退役 |
| 默认主题 | 跟随系统（themeStore 行为不变，只换两套色板） |
| AiReviewTerminal | 重绘保留：折叠式 check-run 卡，四步 replay 与分数语义原样，展开时才播 |
| 布局骨架 | 顶栏收敛（Logo+搜索+语言+用户菜单）+ 左侧频道侧栏（≥lg），<lg 汉堡抽屉 + 底部 tab bar 保留 |
| 侧栏内容 | 社区（7 频道 + 标签）+ 我的（帖子/草稿/消息/设置，未登录只显社区） |
| 帖子详情 | 单列（GitHub Discussions 式），AI 评分徽章在标题元数据行 |
| 列表密度 | Discourse topic-list 式紧凑行 |
| 首页 tabs | 保留（i18n 更名，下划线 tab 样式） |
| CommandPalette | 保留（Ctrl+K），纳入新顶栏 |
| 默认语言 | 跟随浏览器，手动切换持久化，回退中文 |

## Allowed paths

```
frontend/src/**                 # 全部前端代码（shell 重排、i18n、组件、样式、测试）
frontend/tailwind.config.js
frontend/package.json           # 仅允许新增 i18next + react-i18next（含 lock 文件）
frontend/index.html
docs/design/ui-visual-system.md # v1 加被取代标注（正文不改写）
docs/design/ui-visual-system-v2.md
docs/adr/0020-*.md
docs/tickets/design-overhaul-round.md
docs/assets/**                  # 改造后截图（新 shell，路径以仓库根为基准）
README.md                       # 仅测试计数徽章与 Testing 段
```

## Acceptance criteria

M1 — i18n（从零）：
1. 引入 `i18next` + `react-i18next`，中文源语言 + `en` 全量翻译；语言切换入口在用户菜单与设置页，选择持久化（localStorage `nexus-vibe-lang`），默认跟随 `navigator.language`，回退中文。
2. 前端自有文案全部走词典：`frontend/src` 中除 locale 文件与测试外，grep 汉字命中为 0。
3. 频道显示名单一来源：Sidebar / HomePage / 徽章共用一个按 slug 的常量模块，经词典解析；HomePage 与 Sidebar 的重复定义删除。
4. 后端 envelope 中文 `message` 原样透传（`serverErrorMessage.ts` 行为与测试不动）；用户生成内容不进词典。

M2 — 视觉与骨架（按 v2 规范）：
5. 四个特效组件 `BorderBeam`、`DecryptedText`、`ShimmerButton`、`SpotlightCard` 源代码零引用，文件与专属测试删除；5 处复制的 shimmer 骨架屏换成单一共享 `Skeleton`（pulse）。
6. App shell 落地：顶栏（Logo/搜索/语言切换/用户菜单）无频道导航；≥lg 左侧常驻侧栏（社区+我的分组），<lg 汉堡抽屉（路由切换即关、焦点圈闭）+ 底部 MobileTabBar 重皮；CommandPalette Ctrl+K 保留并纳入新顶栏。
7. 帖子列表换紧凑行（topic-row，边框分隔无卡片盒）；首页 tabs 保留为下划线样式；帖子详情单列（`max-w-3xl`），AI 评分徽章入元数据行。
8. AiReviewTerminal 重绘为折叠式 check-run：标题行常显（分数/严重度/裁决），findings 与四步 replay 默认折叠、展开时播一次；数据契约、四状态、"replay of completed run" 文案规则（ADR-0010）不变；既有步骤顺序/分数断言保留。
9. Token 全量切 v2 值（亮暗两套，token 名保留 `vibe-*` 前缀）；`vibe-cyan`/`vibe-neon`/`vibe-purple` 及点阵背景退役；等宽字体仅存于代码块/代码 span/trace id；两套色板关键文本对 AA 达标。
10. 改造后截图（首页、频道页、帖子详情、个人主页，明暗各一组，1280px 与 375px）提交 `docs/assets/`，ticket 引用。
11. `docs/design/ui-visual-system.md`（v1）加被 ADR-0020/v2 取代的标注，正文不改写。

通用：
12. `npm run test` / `npm run lint` / `npm run build` 全绿；测试计数变化同步 README 徽章与 Testing 段（同一提交）。
13. 断言改写只允许"因退役特效/更名文案而收紧或对应新行为"，逐条记录于 ticket；禁止改/删/弱化断言换通过、关 lint/typecheck。

## Verification commands（Verifier 亲自重跑）

```bash
cd frontend && npm run test
cd frontend && npm run lint
cd frontend && npm run build
grep -rnP '[\x{4e00}-\x{9fa5}]' frontend/src --include='*.tsx' --include='*.ts' \
  | grep -v 'i18n' | grep -v '\.test\.'    # 期望 0 行
grep -rn 'BorderBeam\|DecryptedText\|ShimmerButton\|SpotlightCard' frontend/src   # 期望 0 行
grep -rn 'vibe-neon\|vibe-purple' frontend/src --include='*.tsx' --include='*.css'   # 期望 0 行
```

证据绑定 HEAD SHA + `git status --short`；Verifier 用全新 subagent 独立执行。

## Forbidden

- 后端 `src/**`、数据库、docker、`.env` 一律不碰。
- 不新增 i18next/react-i18next 以外的运行时依赖。
- 不动路由路径结构（URL 不变：`/channel/:slug` 等全部保留）、zustand/TanStack Query 用法、authStore/themeStore 行为语义。
- 不删/弱化测试断言换通过。
- 不动用户生成内容的存储与展示逻辑（帖子/评论正文不进词典、不被翻译）。
- 不改写 v1 设计文档正文与既有 docs/assets 历史（旧截图留作历史）。
- 不重写已发布历史；实现走 `codex/<slug>` 分支 + PR。
