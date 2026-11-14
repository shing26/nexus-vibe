# 契约草案 — 核心链路修正轮（P0 清洗迁出口 + P1 终态通知）

任务 ID：`core-loop-corrections` · 配套 ticket：[docs/tickets/core-loop-corrections.md](../docs/tickets/core-loop-corrections.md)
状态：**已冻结（2026-11-14，用户书面确认）。** 冻结时的一处澄清：验收第 4 条的"攻击串不存储原样 HTML"读定为"存储层对全部六类输入（含 `<script>`）不做任何 HTML 改写、往返一致；XSS 防线在出口转义，出口审查结论落 ADR-0021"。

来源：2026-11-14 外部评审（P0/P1），关键断言已由本会话亲手复核（代码路径 + 线上库 `angle test` 帖实存值）。

## Allowed paths

```
src/main/java/com/nexus/campus/filter/XssHttpServletRequestWrapper.java
src/main/java/com/nexus/campus/filter/XssFilter.java
src/main/java/com/nexus/campus/util/ContentSanitizer.java
src/main/java/com/nexus/campus/agent/AiReviewService.java
src/main/java/com/nexus/campus/agent/AiReviewEventListener.java
src/main/java/com/nexus/campus/task/AiReviewReconcileTask.java
src/main/java/com/nexus/campus/**            # 出口转义/渲染所需的配套改动与其测试
src/test/java/com/nexus/campus/**            # 新增/更新测试
docker/mysql/init/*.sql                      # 仅当存量数据修复走 schema 路径
docs/adr/0021-*.md                           # 新 ADR：清洗职责从入口移到出口
docs/tickets/core-loop-corrections.md
README.md                                    # 仅测试计数徽章与 Testing 段
```

## Acceptance criteria

P0 — 入口清洗迁到出口：
1. `XssHttpServletRequestWrapper` 不再对 JSON 请求体的字符串值做 jsoup 清洗（content、评论、prompt_metadata 等内容字段按**原文存储**）；query 参数与 header 的 `HtmlUtils.htmlEscape` 防反射路径保持不变。
2. 出口防线成立：前端以 react-markdown 渲染（默认转义，行为不变）；后端如有把内容当 HTML 输出的面（若有）逐点改为按字段转义或移除该输出形态。审查结论写入 ADR-0021：列出所有内容出口及其转义方式。
3. `ContentSanitizer` 保留用于真正需要 HTML 白名单的写入方（如 AI agent 直插评论里的富文本段）——若该场景不复存在则删除，二选一在实现时定案并记录。
4. 往返测试组：`<`、`&`、`&&`、泛型（`Map<String, Value>`）、代码围栏、`<script>` 攻击串，六类输入 → 创建 → 存储 → 读取全文一致（攻击串不存储原样 HTML）。
5. 存量数据修复：以 20261114 备份为前置，幂等脚本修复已入库的 `&lt;`/`&gt;`/`&amp;` 工件（仅匹配内容正文中明显的转义工件，备份 + 修复前后行数报告）；修复脚本与结果进 `benchmark/migrations/` 或同等可复跑位置。若实现判定不可安全自动修复，改为书面结论 + 受影响行数统计，进 ticket。

P1 — 终态通知移到终态分支：
6. LLM 不可用/校验无效的正常降级路径：当本次已是最后一次尝试（attempts 达上限）时，作者收到"已停止自动重试"通知；尚有重试预算时维持现状（首败告知一次，重试静默）。
7. 现有 REVIEWING→FAILED 扫尾通知与 listener `catch` 路径行为不变，不重复通知（同一终态只发一次）。
8. 新增测试：`null verdict × attempts == max` 断言收到终态通知；`null verdict × attempts < max` 断言只发首败通知；终态通知与既有通知路径无重复。
9. 530 基线上的全部既有测试不因行为修复而弱化——因该修复而变更的断言须逐条对应新行为记录。

通用：
10. `mvn -B test` 全绿；计数变化同步 README 徽章与 Testing 段（同一提交）。
11. JaCoCo 覆盖率下限照常生效，不降标准。

## Verification commands（Verifier 亲自重跑）

```bash
mvn -B test
```

外加验收项 4/5/8 对应的具体测试类的点名重跑（实现方在 ticket 中列出测试类名）。
证据绑定 HEAD SHA + `git status --short`；Verifier 用全新 subagent 独立执行。

## Forbidden

- 不改前端 `frontend/**`（渲染层行为本轮不动，留给设计改造轮）。
- 不动 `docker/` 其余部分、`.env`、生产依赖版本。
- 不删除/弱化测试断言换通过；不关 lint/typecheck/覆盖率下限。
- 存量修复必须在备份之后执行，禁止无备份直改线上卷。
- 实现走 `codex/<slug>` 分支 + PR，不推 master。
