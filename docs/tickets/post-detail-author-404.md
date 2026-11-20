# 帖子详情页对作者本人返回 404 —— 发布后即“不存在”

Status: **implemented, awaiting independent verification**（2026-11-21）。由 2026-11-20 的
UX 走查（[报告](../research/ux-walkthrough-2026-11-20.md)）发现；契约已冻结于
`plans/post-detail-author-404.md`，实现完成，待独立 Verifier 裁决。

## 缺陷

作者发布一篇会被安全评审 hold 的帖子（含代码块、或敏感词命中）后，前端跳转到
`/post/:id`，详情页显示 **“404 — 帖子不存在”**。帖子实际在库里，`status=2`（待审）。

根因：`VibePostServiceImpl.getPostDetail` 对 `status != 1` 直接 `return null`，
没有“作者本人可见自己的待审帖”这一分支。列表侧（`/posts?userId=` 作者视图）已在
frontend-polish-round-3 修复，详情页是同一缺陷的另一半。

## 影响

- 任何触发 fail-closed 的发布（LLM 不可用 / 输出无法解析 / 敏感词命中）都会让作者
  在发布成功的几秒内看到“帖子不存在”。
- 读者体验上等同于“发布功能坏了”，且与仓库“诚实但不沉默”的口径冲突——帖子存在，
  只是还没公开，应该如实说明。

## 验收（若排期）

- 作者访问自己 `status=2` 的帖子详情：200，页面显示“待审”状态与一句原因说明
  （安全评审进行中 / 等待模型），不是 404。
- 非作者访问同 id：仍是 404（不得泄露存在性）。
- 匿名访问：仍是 404。
- 被拒 `status=3` 不在本轮范围（其语义已由作者列表视图承担）。
- 新增测试：作者本人待审可见、他人/匿名不可见。

## 实现记录（待 Verifier 复核）

- `VibePostService.getPostDetail` 新增 `viewerId` 参数；`status=1` 放行，
  `status=2 && viewerId==post.userId` 放行，其余（含 `status=3`、他人、匿名）返回
  null → 404。viewerId 来自 JWT 过滤器的 `currentUserId`。
- 前端 `PostDetailPage.tsx` 对 `status=2` 显示"待审"警示色状态标签。
- 新增集成测试 `getPostDetail_authorSeesOwnPendingPost`：作者 200 且 `data.id`
  匹配（Jackson 把 Long 序列化为字符串），他人 token 与匿名均 404；`status=3`
  未放开（语义仍由作者列表视图承担）。
- 本机实测（2026-11-21）：`mvn -B test` 562 tests / 93 classes 全绿且 JaCoCo 覆盖率
  达标；`npm run test -- --pool=threads --maxWorkers=4` 152 tests / 29 files 全绿；
  `npm run lint`、`npm run build` 干净。前端测试因本机默认 25 worker OOM 而使用
  `--pool=threads --maxWorkers=4`，属环境偏离，如实记录。

## 相关

- 上游：`docs/tickets/frontend-polish-round-3.md`（列表侧修复）
- 走查：[ux-walkthrough-2026-11-20](../research/ux-walkthrough-2026-11-20.md)
