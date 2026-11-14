# 设计改造轮：forge 风格视觉改造 + 中英文 i18n

Status: **草案，契约评审中**（契约：[plans/design-overhaul-round.md](../plans/design-overhaul-round.md)。未冻结不得实现。）

## Scope

用户 2026-11-14 提出的改造请求：站点"AI 流水线味重"，参考 GitHub / linux.do（Discourse）一类论坛的设计改造；同时补上从未存在的中英文切换。本轮只动 `frontend/`，后端与数据层不碰。

背景事实（2026-11-14 盘点）：

- 无任何 i18n 库；92 行硬编码中文散布 8 个文件，其余页面纯英文，同一个对象里中英混排（如 `Sidebar.tsx:6-12` 英文标签 + 中文描述）；`HomePage` 与 `Sidebar` 各自维护一份频道定义。
- "流水线味"的主要来源是四个特效组件与配套写法：`BorderBeam`（流光边框）、`DecryptedText`（解码动画）、`ShimmerButton`（渐变微光按钮）、`SpotlightCard`，加上满屏 `font-mono`、霓虹青紫配色、点阵背景、5 处逐字复制的 shimmer 骨架屏、hover 辉光阴影。
- 21 个测试文件钉着现有文案与组件行为；`docs/design/ui-visual-system.md` 与 `ux-architecture.md` 记录的是旧视觉约束（"保留 terminal motif"），本轮以新 ADR 显式取代。

里程碑：

- **M1 i18n**：引入 `i18next` + `react-i18next`，中文为源语言、新增英文全量翻译，切换入口 + 持久化；前端自有文案全部进词典；频道定义合并单一来源。后端 envelope 中文 `message` 原样透传，不做后端 i18n（明确排除）。
- **M2 视觉**：退役四个特效组件；AiReviewTerminal 保留语义重绘（去流光/解码动画，观感对齐 GitHub check run）；token 重定为中性灰阶 + GitHub 蓝（`#0969DA`/`#58A6FF`，2026-11-14 定案）；等宽字体只留给代码；骨架屏/背景/辉光统一清理；明暗双主题保持 AA。默认语言跟随浏览器，手动切换后持久化。

## Acceptance

见契约（Allowed paths / Acceptance criteria / Verification commands / Forbidden 四节以契约为准）。

## Files

`frontend/**`（src、tailwind.config.js、package.json 仅加 i18n 两依赖）、`docs/adr/0020-*`（新）、`docs/design/*`（更新）、`docs/assets/*`（改造后截图）、`README.md`（仅测试计数）。

## Rejected

- **后端 i18n（Accept-Language 协商）**：后端 envelope 文案是运维面而非用户面，改它牵动全部 controller 与错误码测试；本轮明确透传原样中文，留待真正有英文用户诉求时另立 ticket。
- **自研 i18n 方案（Context + 字典对象）**：17 个页面 × 全量文案，自研要重造复数/插值/回退，省两个依赖不值；i18next 是事实标准。
- **换掉 Tailwind / 重构路由与状态层**：改造的是视觉语言与文案组织，不是架构；现有 zustand + TanStack Query + Tailwind 机制全部保留。
- **删除 AiReviewTerminal**：AI 审查是项目主题本身（见 ADR-0002 起），砍掉等于换产品；重绘而非退役。
- **一轮同时做移动端适配深化**：现有响应式断点保留，深化的响应式留给下一轮，避免本轮 diff 不可审。
