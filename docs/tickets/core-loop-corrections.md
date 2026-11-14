# 核心链路修正轮：P0 入口清洗吃代码 + P1 终态通知缺失

Status: **草案，契约评审中**（契约：[plans/core-loop-corrections.md](../plans/core-loop-corrections.md)。未冻结不得实现。）

来源：2026-11-14 外部评审。以下两条已由本会话亲手复核，不是转述。

## P0 — 入口清洗把代码内容吃掉（存储层内容丢失）

`XssFilter` 把每个请求包进 `XssHttpServletRequestWrapper`，JSON 体的**每个字符串值**在入口过
jsoup `Safelist.relaxed` 清洗（`XssHttpServletRequestWrapper.sanitizeJsonNode` →
`ContentSanitizer.clean`）。jsoup 把值当 HTML 片段解析：`Map<String, Value>` 的泛型尖括号被当
未知标签整段删除，`a < b && c > d` 存成 `a &lt; b &amp;&amp; c &gt; d`。

铁证：线上库 `vibe_post` id `2096843254191030274`（标题 `angle test`）实存
`a &lt; b and c &gt; d`。该问题在
[readme-visual-front-door.md:76-84](readme-visual-front-door.md) 已被记录，但被降格为"显示缺陷"，
未进 backlog——实际是**存储层内容丢失**，且污染 AI 评审输入、搜索索引与摘要，编辑帖子会沿同
一路径二次破坏。对一个以代码为内容主体的项目，这是 P0。

修复方向（ADR-0021）：入口存原文，出口按面转义。SPA 用 react-markdown 渲染（默认安全），
后端不存在把用户内容当 HTML 拼输出的形态即成立；参数/header 的反射 XSS 防护不动。

## P1 — 正常降级路径永远等不到"已停止自动重试"

三条通知路径只有两条活着：

| 路径 | 触发 | 现状 |
| --- | --- | --- |
| `AiReviewService.notifyReviewFailed` | LLM 不可用/校验失败（`reviewPost` 正常返回标 FAILED） | 只发首败"系统会自动重试"（[AiReviewService.java:106](../../src/main/java/com/nexus/campus/agent/AiReviewService.java)） |
| `AiReviewEventListener` catch | review 过程抛异常且 attempts 达上限 | 到达 |
| `AiReviewReconcileTask.sweepBudgetExhaustedReviews` | 仅捞 `ai_reviewed = 2`（REVIEWING）耗尽租约态 | 只覆盖 worker 崩溃路径 |

正常降级路径的帖子直接标 FAILED(3)：扫尾不查 FAILED，重试捞取只认 `attempts < max`
（[VibePostMapper.selectStaleAiReviewPosts](../../src/main/java/com/nexus/campus/mapper/VibePostMapper.java)），claim 在 `attempts >= max` 时静默拒绝
（[tryClaimReview](../../src/main/java/com/nexus/campus/mapper/VibePostMapper.java)）。结果：最后一次失败的作者信箱里留着的还是"系统会
自动重试"，终态通知永远不来。这条路径恰恰是项目主打的"LLM 不可靠依赖"演示路径。

修复方向：终态判定移进 `reviewPost` 的终态分支（或 `ai_reviewed` 条件更新仲裁），attempts 达
上限时发终态通知；与既有两条路径保证同一终态只发一次。

## Acceptance

见契约。要点：六类输入往返测试；存量 `&lt;` 工件的幂等修复（备份前置）；`null verdict ×
attempts==max` 的终态通知测试；`mvn -B test` 全绿且覆盖率下限不降。

## Files

`filter/`、`util/`、`agent/`、`task/` 及其测试；ADR-0021；README 仅计数。前端不动。

## Rejected

- **入口保留 jsoup、仅对 content 字段加白名单豁免**：豁免清单会随字段增长腐化，且评论、
  prompt_metadata 同样吃代码；出口转义是单一防线，豁免是散弹防线。
- **存量数据不修**：不修则已损坏的帖子永远损坏，且"angle test"这类反例会一直挂在线上；
  修复必须备份前置 + 幂等。
- **本轮顺带修 P2（限流按 id 分桶、授权集中化、Boot 升级）**：P2 各自独立成轮，混进本轮会让
  diff 不可审。Boot 3.5/Java 21 升级涉及生产依赖变更，按 ADR-0009 须单独论证。
- **改前端渲染来兼容已转义的存量内容**：渲染层属于设计改造轮的领地，且"显示兼容存储缺陷"
  是把缺陷固化。
