# 作者待审帖详情页 404 —— 修复轮契约

Status: **frozen contract, 2026-11-20; amended 2026-11-21.** Base commit `3288f53`
(merged master, PR #54). Branch: `codex/ux-walkthrough-fix`,
worktree `.worktrees/ux-fix`. 由 [走查报告](../docs/research/ux-walkthrough-2026-11-20.md)
发现的 P1 缺陷，[缺陷单](../docs/tickets/post-detail-author-404.md) 记录。

## Amendment（2026-11-21，Verifier 首审 FAIL 后）

- **范围界定**：`docs/research/ux-walkthrough-2026-11-20.md` 由契约冻结**之前**的
  走查提交 `8b08fa0` 引入（先于 `2813ac5` 冻结）。Allowed paths 约束的实现提交是
  `2813ac5` 之后的变更；diff 范围 `3288f53..HEAD` 因此包含该文件，不视为越界。
  修复提交 `b174213` 本身触及其文件均在 Allowed paths 内。
- **测试覆盖补强（首次实现遗漏验收 3）**：新增"作者 GET 自己 `status=3` → 404"
  集成测试；验收 1 的既有测试补断言 `data.title` 与 `data.content`。

## 缺陷

作者发布一篇被安全评审 hold 的帖子（含代码块、或敏感词命中 → `status=2`）后，
前端跳转到 `/post/:id`，详情页显示“404 — 帖子不存在”。帖子在库里，
`VibePostServiceImpl.getPostDetail` 对 `status != 1` 直接返回 `null`。
列表侧已在 frontend-polish-round-3 修复，详情页是漏掉的另一半。

## 修复（最小、诚实、不泄露）

- `getPostDetail` 增加“作者本人可见自己的待审帖”分支：调用方传入当前查看者 id
  （来自 JWT 过滤器的 `currentUserId` 请求属性，匿名时为 null），
  当 `post.userId == viewerId` 且 `post.status ∈ {ACTIVE, PENDING_REVIEW}` 时放行。
- `status=3`（被拒）**不在本轮**：其语义由作者列表视图承担，详情页继续 404，
  避免把“被拒”渲染成一个没有解释的公开页。
- 非作者、匿名访问同 id：仍是 404，不得泄露存在性。
- 详情页顶部渲染 `postStatusLabel(post.status)`（现有函数，已有
  `common:status.pendingReview` 文案），让作者看到“待审”而不是一片空白。
- 不新增提示文案、不改公共 feed 的 `status=1` 过滤、不改评审管线。

## Allowed paths

- `plans/post-detail-author-404.md`（本契约）
- `docs/tickets/post-detail-author-404.md`
- `docs/INDEX.md`
- `README.md`（仅测试计数）
- `src/main/java/com/nexus/campus/service/VibePostService.java`
- `src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`
- `src/main/java/com/nexus/campus/controller/PostController.java`
- `src/test/java/com/nexus/campus/controller/PostControllerIntegrationTest.java`
- `frontend/src/pages/PostDetailPage.tsx`
- `frontend/src/pages/PostDetailPage.test.tsx`（若已有；否则新增）
- `frontend/src/i18n/locales/zh.json`、`frontend/src/i18n/locales/en.json`
  （仅在需要补一条状态说明时）

## Acceptance criteria

1. 作者 GET 自己 `status=2` 的帖子详情：200，`data.status=2`，标题与正文正常。
2. 另一个登录用户 GET 同一 id：404；匿名 GET：404。
3. 作者 GET 自己 `status=3`（被拒）的帖子：404（本轮不放开）。
4. 不存在的 id：404，与今天一致。
5. 详情页对 `status=2` 渲染“待审”状态标签（`postStatusLabel` 已覆盖，不新增硬编码）。
6. 新增后端测试覆盖 1–3；前端测试（若文件存在）断言待审标签渲染。
7. 无既有测试删除/跳过/弱化；`mvn -B test`、`npm run test -- --pool=threads`、
   `npm run lint`、`npm run build` 通过。

## Verification commands

```bash
mvn -B test
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff 3288f53..HEAD --stat
```

独立 Verifier 重跑并绑定 HEAD SHA + `git status --short`。

## Forbidden

- 放开 `status=3`，或在详情页泄露非本人待审/被拒帖的存在。
- 改动公共列表查询的 `status=1` 过滤，或评审/安全管线。
- 把作者判断退化成“登录即可看任意待审帖”（必须是 `post.userId == viewerId`）。
- 新增依赖、schema 变更。

## Rejected

- **只改前端把 404 换成“待审”文案**：后端仍 404，前端拿不到正文，是假修复。
- **详情页对所有登录用户放行待审帖**：泄露存在性，违背 round-3 的隐私边界。
- **本轮一并修 P2-1/P2-2**：P2-1 依赖本修复的落地形态，P2-2（移动端频道入口）
  是独立的信息架构改动，都应各自成轮。
