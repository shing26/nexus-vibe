# S4-1 按用户聚合的评审记录读取面

Status: **已验收（2026-11-16，签收；含契约追认）**（实现分支 `codex/s4-1-user-review-index`，
worktree `.claude/worktrees/s4-1`；契约冻结提交 `e73c9cf`，基线 master `517f852`）。

## Scope

给 S4 证据档案提供可溯源的读取面：`GET /api/v1/agent-logs/user/{userId}`，公开返回某用户
每个可见帖子的**最新一次 code review**（`logId`、`postId`、`postTitle`、`score`、`severity`、
`isApproved`、`reviewedAt`、`durationMs`），按 `reviewedAt` 降序、上限 50 条。只读、无新 schema、
无新运行时依赖、前端不动。

## Acceptance（对照契约 6 条）

1. **端点与排序/上限** — `AiLogController.getUserReviewIndex` 映射
   `GET /api/v1/agent-logs/user/{userId}`，不带 `AdminGuard`；复用
   `AiReviewLogMapper.selectLatestCodeReviewLogsByUserId`（created_at DESC, id DESC），
   在 Java 侧保留每帖首行；返回前按 `USER_REVIEW_LIMIT = 50` 截断。公开性由
   `JwtAuthFilter.isPublicGet` 放行（见偏差 1）。
2. **字段与溯源** — `UserReviewEntry` 只含契约列出的 8 个字段；`logId` 取
   `ai_review_log.id`；`score`/`severity`/`isApproved`/`reviewedAt`/`durationMs` 经
   `AiReviewDetailService.toUserReviewEntry` 读取，与既有 `toDetail`（凭证/`/post/{postId}`
   同一解析路径）完全一致。不返回 resultJson 原文或 findings 文本。
3. **与 profile 口径一致** — 先按 mapper 顺序取每帖首行（最新），再要求该行
   `resultJson` 非空；这与 `UserProfileSummaryService.calculateAverageAiScore` 的
   “`countedPosts` 先占位、再按 `score(log)` 是否非空计入平均”逐个数据点对齐。测试用一个
   种子用户从端点独立复算“被评审帖子数 + 平均分”，与服务报告值相等。
4. **边界** — 用户不存在返回 `ApiResponse.success([])`（200，空数组，非 404）；帖子被删除
   （join 无行）或不可见（`status != 1`）时跳过，不产生空 title 的孤儿条目；每帖最多一条，
   无重复 `postId`，顺序不重排。
5. **约束** — 未改 schema/迁移/docker/`.env`，未加运行时依赖，未改既有端点响应形状；
   `mvn -B test` 全绿且 JaCoCo 行覆盖达标；后端计数 541 → 546 已同步 README 徽章、
   Testing 段与命令注释（同一提交）。
6. **测试** — 见下节，六个覆盖点逐一钉住。

## 实现记录

- `dto/UserReviewEntry.java`（新增）：8 个字段的只读 DTO，Jackson 依既有 `JacksonConfig`
  把 `Long` 序列化为字符串。
- `service/AiReviewDetailService.java`：新增 `toUserReviewEntry(log, postTitle)`，与
  `toDetail` 共用 `parseResult`/`score`/`severity`/`isApproved`/`durationMs`，避免出现第二套
  评分解析口径。
- `controller/AiLogController.java`：新增端点。组装顺序为
  **每帖最新 → resultJson 非空 → 批量取帖子并过滤 `status == 1` → 截断 50**。标题经
  `VibePostMapper.selectByIdsOrdered(postIds)` 一次批量查询（无 N+1）；帖子行缺失或不可见即跳过。
- `security/JwtAuthFilter.java`：`isPublicGet` 增加 `/api/v1/agent-logs/user/` 前缀（见偏差）。
- `README.md`：徽章与 Testing 段 541 → 546，命令注释同步。

## 口径细节（避免“最新”被悄悄改写）

`selectLatestCodeReviewLogsByUserId` 不按 `result_json` 过滤。若某帖**最新**一行
`resultJson` 为空，本端点不会回退到更早的非空行：该帖整条不出现。这样与
`UserProfileSummaryService` 完全一致——它也是先按帖占位（`countedPosts.add`），发现
`score(log) == null` 时不计入平均，且不会再看该帖更早的行。若先过滤空行再取最新，会
返回 profile 平均分未采用的历史分数，破坏验收第 3 条。

## 偏差与实现决定（必须知悉）

1. **越出 Allowed paths：`src/main/java/com/nexus/campus/security/JwtAuthFilter.java`**
   — 契约要求端点公开，但既有过滤器只放行 `/agent-logs/post/` 与 `/agent-logs/ticker`；
   不加这一行，新端点对无 token 请求直接 401，Acceptance 1 无法满足，且 Allowed paths 中
   没有其他位置能改变过滤链。改动为单行前缀增量（`|| uri.startsWith("/api/v1/agent-logs/user/")`），
   不触碰任何既有路径判定；需维护者追认契约（与 design-overhaul-fixes-b 的越界补链同性质）。
2. **`README.md` 同为 Allowed paths 之外**，但 Acceptance 5 与任务指令明确要求同步计数，
   按契约要求执行，非自主扩张。
3. **上限在“跳过不可见帖子”之后应用** — 保证返回列表不会因中途跳过而少于 50 条可见记录；
   代价是最多一次批量标题查询覆盖全部去重帖（仍是单次 `IN` 查询）。

## 测试改动逐条记录

`AiLogControllerIntegrationTest` 新增 5 个测试，**既有 9 个测试与断言一字未改**：

1. `getUserReviewIndex_shouldReturnLatestReviewPerPostNewestFirst` — user 3：多帖多日志，
   断言每帖只出最新一行、`reviewedAt` 降序、无重复 `postId`、`logId`/标题/分数透传。
2. `getUserReviewIndex_shouldSkipInvisiblePosts` — 给 status=2 的帖子插入 code review，
   断言它不出现在索引中。
3. `getUserReviewIndex_shouldCapAtFiftyEntries` — 55 个帖子各一条评审，断言恰好 50 条、
   首尾 id 正确、第 51 条被截断。
4. `getUserReviewIndex_unknownUser_shouldReturnEmptyArray` — 未知用户 200 + 空数组。
5. `getUserReviewIndex_shouldMatchProfileSummaryAverage` — **口径钉**：user 4 三个可见已评审帖
   （各含新旧两行/一行）+ 一个不可见帖；从端点响应独立复算被评审帖数（3）与平均分
   （(4+9+6)/3），断言等于 `UserProfileSummaryService.getSummary(4).stats.avgAiScore`。
   所有种子数据为测试内 mapper 插入，未改共享 fixture。

## Verification（实现方自跑，Verifier 须亲自重跑）

```bash
mvn -B test
→ Tests run: 546, Failures: 0, Errors: 0, Skipped: 0
→ All coverage checks have been met.
→ BUILD SUCCESS

grep -rn 'AdminGuard' src/main/java/com/nexus/campus/controller/AiLogController.java
→ 仅既有 listLogs(/agent-logs) 与 getStats(/agent-logs/stats) 两处；新方法无调用

grep -rn 'selectLatestCodeReviewLogsByUserId' src/main/java
→ AiReviewLogMapper.java:40（定义）
→ AiLogController.java:125（本端点复用）
→ UserProfileSummaryService.java:75（既有口径来源）

ls target/surefire-reports/*.txt | wc -l → 91（类数未变，新增测试并入既有类）
```

## Files

- **新增**：`src/main/java/com/nexus/campus/dto/UserReviewEntry.java`、
  `docs/tickets/s4-1-user-review-index.md`。
- **修改**：`src/main/java/com/nexus/campus/controller/AiLogController.java`、
  `src/main/java/com/nexus/campus/service/AiReviewDetailService.java`、
  `src/main/java/com/nexus/campus/security/JwtAuthFilter.java`（偏差 1）、
  `src/test/java/com/nexus/campus/controller/AiLogControllerIntegrationTest.java`、
  `README.md`（计数）。

## Rejected

- **先过滤 `resultJson` 非空再取每帖最新** — 会回退到 profile 平均分未采用的历史行，破坏
  验收第 3 条的口径钉；本端点选择“最新行无 payload 则整帖缺席”。
- **在 MyBatis 里新增专用 SQL（含 `LIMIT 50` / 窗口函数）** — 契约明令复用既有 mapper；
  同一 SQL 也是 profile 平均分的唯一来源，分叉会制造第二套口径。
- **返回 `AiReviewDetail` 或 resultJson 原文** — 档案面只需要可追溯元数据；findings 文本
  属于 `/agent-logs/post/{postId}` 的既有读取面，不在公开索引里重复。
- **未知用户返回 404** — 遵循 `/posts/unanswered` 的“空是正常答案”约定；用户存在性不是
  这个读取面的问题。
- **分页** — 契约把档案面定义为曲线而非列表页；超过 50 条截断即可，分页会引入第二套排序
  与游标语义。
- **逐帖 `selectById` 取标题** — 会形成 N+1；改用既有 `selectByIdsOrdered` 一次批量取。
- **在 `UserProfileSummary` 新增“被评审帖子数”字段** — 计数可直接由本端点条目数复算，
  无需改 profile DTO/响应形状（也超出 Allowed paths）。
- **回填/修改共享 fixture（`data.sql`）** — 种子数据在测试内按需插入，避免影响其他测试的
  既有计数断言。

## 验收与签收记录

- **Verifier 独立复核**（2026-11-16，全新会话 verifier subagent）：绑定 HEAD `a29a57a`，6/6 条标准 PASS。
  `mvn -B test` **546/91 全绿** + 覆盖率 floor 达标（亲自重跑，2:28）；surefire 报告 91 个均为本次新写。
  证据要点：端点公开且无 `AdminGuard`；每帖最新 + `reviewedAt` 降序 + 上限 50（断言恰好 50 且第 51 条不存在）；
  DTO 恰为 8 个字段、与凭证卡共用解析路径、`logId` 即日志行 id、无 resultJson/findings；
  未知用户返回空数组；不可见帖跳过；测试 diff 190 行纯新增、零断言删除。
  C3 口径钉测试被判定非循环论证（取错最新→3.0、含隐藏帖→5.0、计数错 都会失败）。
- **越界裁决**：`security/JwtAuthFilter.java` = ACCEPTABLE-DEVIATION（必要性成立：公开性只由该 filter 强制，
  契约内无替代；改动为单条增量前缀，其他路径行为不变）；`README.md` = 契约漏列（验收 5 自身要求同步计数）。
  安全面留档：新前缀下所有 GET 自动公开，当前仅此一路由；子树日后扩增需显式评审。
- **契约修订（用户书面追认）**：两处路径补入 Allowed paths；C3 上限 caveat 写入验收标准 3
  （>50 条评审的用户无法用端点复算全量平均分——契约自证张力，本票接受并留档）。
- **用户书面签收**（2026-11-16）："书面追认 JwtAuthFilter.java 与 README.md 两处路径（并把 C3 上限 caveat
  写进契约），签收 S4-1 并开 PR"。
