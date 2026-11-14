# 核心链路修正轮：P0 入口清洗吃代码 + P1 终态通知缺失

Status: **实现完成（2026-11-14，分支 `codex/core-loop-corrections`），待独立 Verifier 重跑验收。** 契约已冻结（2026-11-14，[plans/core-loop-corrections.md](../../plans/core-loop-corrections.md)）。

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

## Implementation notes

实现分支 `codex/core-loop-corrections`（worktree），未推任何远端。ADR：[docs/adr/0021-content-is-stored-raw-and-escaped-at-egress.md](../adr/0021-content-is-stored-raw-and-escaped-at-egress.md)。

### 改动文件

- `src/main/java/com/nexus/campus/filter/XssHttpServletRequestWrapper.java` — JSON 请求体不再做
  jsoup 清洗/转义/重序列化，原文字节透传（`ensureBodyCached`）；非 JSON 体与 query/header 的
  `HtmlUtils.htmlEscape` 保持不变。`sanitizeJsonBody`/`sanitizeJsonNode`/`sanitizeHtml` 删除。
- `src/main/java/com/nexus/campus/filter/XssFilter.java`、`config/XssConfig.java` — 仅 javadoc
  更新为新口径（入口字节保真、出口转义），注册行为零改动。
- `src/main/java/com/nexus/campus/util/ContentSanitizer.java` — **保留**（见下）。
- `src/main/java/com/nexus/campus/agent/AiReviewService.java` — `reviewPost` 新增 `int attempts`
  参数（listener 把 claim 返回的尝试数传入）；两个降级分支在 `attempts >= maxAttempts` 时改发
  终态通知，文案与 `AiReviewReconcileTask.notifyBudgetExhausted` 同一口径（"连续 N 次失败，已停
  止自动重试"）；未达上限维持现状（首败一次、retried 静默）。注入了 `CampusAiProperties`。
- `src/main/java/com/nexus/campus/agent/AiReviewEventListener.java` — 只改 `reviewPost` 调用处：
  把 `attempts` 传入。listener catch 路径与 `AiReviewReconcileTask` 的扫尾/重触发逻辑零改动。
- `pom.xml` 未动（jsoup 依赖保留，仍被 ContentSanitizer 使用）；前端、docker 其余部分、`.env`
  均未动。

### ContentSanitizer 去留结论

**保留。** Wrapper 退出后它仍有三类调用方，且都是服务端直插、绕过请求过滤器的 LLM 产出写入：
`CodeReviewReviewer`/`PromptReviewReviewer`（评审正文→AI 评论）、`AgentMentionListener`（@提及
回复）。模型是不可信依赖，被注入的 verdict 不得把 script 带进评论行；该白名单是这条窄路径上
唯一的防线。已记录的残留局限：safelist 是 HTML 口径，模型 markdown 里的 `<` 比较符仍会被转义
——这是"不全信模型"的代价，只伤 LLM 文案、不伤用户内容（详见 ADR-0021 第 3 条）。

### 出口审查结论摘要

后端不存在把用户内容当 HTML 拼输出的形态：控制器全部返回 JSON（`JwtAuthFilter`、
`RateLimitInterceptor` 的错误响应均为固定 JSON 字面量），无服务端模板引擎（pom 无
thymeleaf/freemarker）。`PostCreationService.summary()` 的 `replaceAll("<[^>]*>", "")` 是为摘要
列派生纯文本摘录，产物仍以 JSON 文本供出、从不拼进 HTML——不构成 HTML 输出面。前端以
react-markdown 渲染正文与评论（`PostDetailPage`/`CommentBody`/`CreatePostPage`/`EditPostPage`），
默认转义裸 HTML，本轮未改。全清单见 ADR-0021 的 egress inventory 表。

### 新增/改写测试（契约验收 4、8、9）

- **`filter/XssWrapperJsonBodyRoundTripTest`**（新增，7 例）：六类冻结输入（`<`、`&`、`&&`、
  `Map<String, Value>`、代码围栏、`<script>alert(1)</script>`）经 wrapper 往返字节一致；`<script>`
  原样存储是明示口径——存储层不做 HTML 改写，其安全性由出口（react-markdown 默认转义）保证。
- `filter/XssFilterTest` — JSON 体三例按新行为**改写断言**（原断言"script 被剥离/属性被清洗"，
  新断言"JSON 体逐字节透传，含 script"；验收 9 的对应记录）：参数/header/非 JSON 体各例未动。
- `filter/XssWrapperTagsArrayTest` — 原版反射调用已删除的 `sanitizeJsonBody`；改写为经
  `getInputStream` 的字节级往返（tags 数组逐字节存活、tag 值中的 HTML 原样存储），同样是验收 9
  项下的断言改写。
- `agent/AiReviewServiceTest` — 新增两例：`null verdict × attempts == max` 恰发一条终态通知；
  `invalid verdict × attempts == max × retried=true` 仍发终态通知（最后一次尝试常经对账重触发
  而来，此时不能因 retried 静默而漏发）。既有四例补 attempts 实参、断言口径不变。
- `agent/AiReviewEventListenerTest` — 全部 verify 补 attempts 实参；新增一例：正常（非抛异常）
  完成于预算上限时 listener 自身不发任何通知，防与 service 的终态通知重复。

Verifier 点名重跑类：`XssWrapperJsonBodyRoundTripTest`、`XssFilterTest`、`XssWrapperTagsArrayTest`、
`AiReviewServiceTest`、`AiReviewEventListenerTest`。

### 存量修复脚本

`benchmark/migrations/fix-escaped-content-artifacts.ps1`（风格对齐 `rehearse-migrations.ps1`：
Invoke-Docker 放宽 `$ErrorActionPreference`、SQL 经 `docker exec -i` stdin 过单边界）。修复集仅
三个无歧义工件：`&amp;&amp;`→`&&`、`&lt;`→`<`、`&gt;`→`>`；独立 `&amp;` **有意不动**（markdown
中渲染等价，而代码围栏内的字面实体会被二次破坏；这也使其严格幂等——作者手打的字面 `&lt;` 存储
为 `&amp;lt;`，不含工件模式，永不触碰）。开头强制备份检查，前置备份
`E:\nexus-backups\20261114-pre-migrate\`（已在本机核实存在）。**线上卷未执行任何修复**；验证在
自建演练容器 `nexus-fixartifacts-db`（本地 mysql:8.0 镜像、独立容器，已销毁）完成：种子 7+3 行
（含工件行、字面实体保护行、干净行）→ dry-run 报告 4+2 行含工件且零写入 → `-Apply` pass 1 改
4+2 行 → pass 2 改 0+0 行（幂等证明）→ 逐行核验修复值（`a &lt; b` → `a < b`、`Map&lt;String,
Value&gt;` 实体部分恢复）与保护行原样。报告：[benchmark/migrations/results/fix-escaped-content-2026-11-14.md](../../benchmark/migrations/results/fix-escaped-content-2026-11-14.md)。
不可恢复项（jsoup 整段删除的 `Map<String, Value>` 尖括号内容）在脚本 header 与报告中书面记录。

### README 计数变化

`530 tests / 90 classes` → `**539 tests / 91 classes**`（徽章、Testing 段、命令注释同一批提交更
新）；前端 115 / 21 files 与 alert-bridge 28 不变。`mvn -B test` 全绿，JaCoCo 0.70 下限照常生效
（未用 `-DargLine` 覆盖，未跳过 jacoco）。
