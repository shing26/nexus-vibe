# 契约 — S4-1 按用户聚合的评审记录读取面

任务 ID：`s4-1-user-review-index` · 父 spec：[docs/plans/s4-evidence-profile.md](../docs/plans/s4-evidence-profile.md)
状态：**已冻结（2026-11-16，用户指令"开 S4-1（按用户聚合的评审记录读取面）"）。**

## 目标

给 S4 证据档案提供**可溯源的读取面**：某用户所有被评审帖子的评审记录（每帖最新一次），
每条带 `logId` 以便跳转到原始 `ai_review_log` 行。只读、公开、不新增 schema。

## 设计（写死在契约里，实现不得偏离）

**端点**：`GET /api/v1/agent-logs/user/{userId}`（公开，与其他 `/agent-logs/post/*` 同级）

**响应**：`ApiResponse<List<UserReviewEntry>>`，数组按 `reviewedAt` 降序，
**每帖只取最新一次 code review**（与 `UserProfileSummaryService.calculateAverageAiScore` 的口径一致，
保证 profile 的平均分与曲线取自同一批数据点）；上限 50 条（超出截断，不做分页——档案面是曲线，不是列表页）。

**`UserReviewEntry` 字段**：`logId`（原始日志行 id）、`postId`、`postTitle`、`score`、
`severity`、`isApproved`、`reviewedAt`、`durationMs`。**不含** resultJson 原文与 findings 文本。

**语义**：
- 只统计 `reviewer = 'code-review-agent'` 且 `resultJson` 非空的日志（与既有 `/post/{postId}` 一致）。
- 帖子已被删除或不可见（`vibe_post` 无对应行）时跳过该条，不返回空 title 的孤儿记录。
- 用户不存在返回空数组（不是 404，参照 `/posts/unanswered` 的"空是正常答案"约定）。

**复用**：`AiReviewLogMapper.selectLatestCodeReviewLogsByUserId`（已存在，勿重复写 SQL）；
帖子标题经既有 `VibePostMapper` 批量取（避免 N+1）。

## Allowed paths

```
src/main/java/com/nexus/campus/controller/AiLogController.java   # 新端点
src/main/java/com/nexus/campus/dto/UserReviewEntry.java          # 新 DTO
src/main/java/com/nexus/campus/service/AiReviewDetailService.java # 组装逻辑（如需）
src/main/java/com/nexus/campus/security/JwtAuthFilter.java       # 公开前缀（2026-11-16 契约修订，见下）
src/test/java/**                                                  # 控制器/集成测试
README.md                                                         # 仅测试计数（验收 5 本身要求）
docs/tickets/s4-1-user-review-index.md
```

## Acceptance criteria

1. 端点存在且公开（不带 `AdminGuard`）；返回每帖最新一次 code review，按 `reviewedAt` 降序，上限 50。
2. 每条的 `score`/`severity`/`isApproved`/`reviewedAt`/`durationMs` 与对应 `ai_review_log` 行一致，
   `logId` 即该行 `id`（可被 S4-3 用作溯源锚点）。
3. 与 `UserProfileSummaryService` 的平均分口径一致：同一用户的"被评审帖子数"与"平均分"
   可由本端点数据独立复算得到相同结果（用一个测试钉住这一点）。
   **上限 caveat（2026-11-16 Verifier 查出，契约自证）**：本端点上限 50，而 profile 平均分用全量；
   评审帖子数 >50 的用户上两条要求字面不可同时满足。本契约接受此张力：caveat 记录在此，
   复算一致性只保证在 ≤50 的范围内成立；若日后需要全量一致性，把 profile 的统计人群也截到 50，
   或给端点加分页（新契约，不在本票）。
4. 用户不存在 → 空数组；帖子已不可见 → 该条跳过；不乱序、不重复 postId。
5. 不新增 schema、不改既有端点行为、不新增运行时依赖；`mvn -B test` 全绿，
   后端测试计数变化同步 README 徽章与 Testing 段（同一提交）。
6. 测试至少覆盖：多帖多日志取最新、按时间排序、上限截断、空用户、不可见帖跳过、口径一致。

## Verification commands（Verifier 亲自重跑）

```bash
mvn -B test
grep -rn 'AdminGuard' src/main/java/com/nexus/campus/controller/AiLogController.java   # 新端点方法内不得出现
grep -rn 'selectLatestCodeReviewLogsByUserId' src/main/java                             # 复用而非新 SQL
```

## Forbidden

- 数据库 schema / 迁移 / docker / `.env` 不碰。
- 不新增运行时依赖或前端改动（前端在 S4-2/S4-3）。
- 不改既有端点响应形状；不删/弱化测试断言。
- 不返回 resultJson 原文或 findings 文本（只给可溯源元数据）。
- 不重写已发布历史；实现走 `codex/s4-1-user-review-index` 分支 + PR。

## 契约修订记录

- **2026-11-16 · 追认越界（用户书面）**：
  1. `security/JwtAuthFilter.java` 补入 Allowed paths。理由：本项目的公开性只由该 servlet filter
     强制（`SecurityConfig` 仅注册它），不加前缀则公开端点一律 401，验收 1 在契约内无解。
     Verifier 判定 ACCEPTABLE-DEVIATION：单条增量前缀，未重排/放宽既有判断，其他路径行为不变。
     安全面留档：新前缀下所有 GET 自动公开（当前仅此一个路由）；若该子树日后新增端点，必须显式评审。
  2. `README.md` 补入 Allowed paths——验收 5 自身要求同步计数，属契约漏列而非越权。
  3. C3 上限 caveat 写入验收标准 3（Verifier 查出的契约自证张力）。
