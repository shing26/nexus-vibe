# 契约 — 设计改造修复轮（外审 A 档无争议缺陷）

任务 ID：`design-overhaul-fixes` · 配套 ticket：`docs/tickets/design-overhaul-fixes.md`
状态：**已冻结（2026-11-16，用户书面确认"A 档无争议缺陷先冻结修复轮契约走链路"）。**

背景：2026-11-16 外部锐评（DeepSeek harness）7 组发现经逐条手验，A 档为其中无设计争议、
直接可修的缺陷。B 档（首页宫格去留、首屏结构、行 meta 减法、profile 统计权重、
语言按钮 aria 惯例、浅色代码块）不在本契约内，走单独访谈。

## Allowed paths

```
frontend/src/**                     # 词典、AiReviewTerminal、Navbar、ChannelPage、i18n bootstrap、测试
frontend/index.html                 # 仅 title（如需）
src/main/java/com/nexus/campus/controller/PostController.java      # A3: unanswered 加 channelId
src/main/java/com/nexus/campus/service/VibePostService.java        # A3: 接口签名
src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java  # A3: 实现
src/test/java/**                    # A3 后端测试
docs/assets/design-overhaul/**      # A7: 重摄 18 张 PNG
docs/assets/readme/async-review.png # A9: v1 截图换新 UI 版
docs/assets/readme/community-feed.png  # A9
README.md                           # 仅当图片 alt/说明需要随图更新
docs/tickets/design-overhaul-fixes.md
```

## Acceptance criteria

1. **severity 走词典**：SeverityBadge 渲染 `severityNames` 译名（zh UI 显示 低/中/高/严重/未知/不可用，
   en 显示英文词条）；测试钉住 zh 渲染。
2. **pending 态走词典**：AiReviewTerminal pending 文案来自词典（zh/en 各一条）；
   字面量 `AI Agent reviewing` 在 `frontend/src` grep 为 0；既有测试的钉句改动逐条记入 ticket。
3. **unanswered 频道化**：`/posts/unanswered` 增加可选 `channelId` 参数（不传行为不变）；
   ChannelPage 右栏传入当前频道 id，右栏内容不再跨频道重复。后端测试覆盖过滤行为；
   `mvn -B test` 全绿（计数变化同步 README——同提交）。
4. **注册按钮移动端可见**：<sm 视口下注册入口可见（登录、注册双入口均在）。
5. **palette 提示按平台**：Mac 环境显示 `⌘ K`，其他显示 `Ctrl K`；文案走词典。
6. **document.title 随语言**：切换语言后站点标题更新（zh：Nexus-Vibe | AI 开发者社区；
   en：Nexus-Vibe | AI Developer Community），刷新后保持。
7. **截图重摄（干净配置）**：`docs/assets/design-overhaul/` 18 张 PNG 以无扩展的干净浏览器配置
   重摄（同矩阵同名：4 页 × 明暗 × 1280/375 + 抽屉 + prompt 附加）；ticket 记录方法；
   底部/右下角无外挂悬浮物。
8. **README 两张 v1 截图换新**：`async-review.png`、`community-feed.png` 以新 UI（暗色）
   重摄替换，alt 文案随实相符。
9. **门禁补课**：`grep -rn 'AI Agent reviewing\|agent\.ticker' frontend/src` 为 0；
   前端 `npm run test`/`lint`/`build` 全绿；断言改动逐条记 ticket，禁止为通过而弱化。

## Verification commands（Verifier 亲自重跑）

```bash
cd frontend && npm run test && npm run lint && npm run build
mvn -B test
grep -rn 'AI Agent reviewing\|agent\.ticker' frontend/src          # 期望 0 行
grep -rn 'severityNames' frontend/src/components/AiReviewTerminal.tsx  # 期望命中（词典接线）
# A7/A8 截图：Verifier 目验图像内容本身（含四角），不只读元数据
```

证据绑定 HEAD SHA + `git status --short`；Verifier 用全新 subagent 独立执行。

## Forbidden

- 数据库、docker、`.env` 不碰。
- 不新增运行时依赖。
- 不动路由路径结构、authStore/themeStore 语义、serverErrorMessage.ts。
- 不删/弱化测试断言换通过。
- 不改写 v2 规范与 ADR-0020 正文（B 档访谈如需改规范，另行走轮）。
- 不重写已发布历史；实现走 `codex/design-overhaul-fixes` 分支 + PR。
