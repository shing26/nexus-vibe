# UX 走查报告：模拟真实用户的一次完整旅程

日期：2026-11-20。环境：本机 `localhost:5173`（Vite）+ `127.0.0.1:8081`（Spring Boot，H2
内存库，演示数据已灌入，Redis/Elasticsearch 未启动）。方法：Playwright 真实浏览器、
1280×900 与 375×812 两档、zh-CN，每一步截图 + 后端日志与 SQL 逐条比对。

旅程：匿名落地 → 登录（alice/123456）→ `/creations` 创作物库 → 发一篇带代码块的帖 →
读 AI 评审 → 回首页看新行 → `/theatre` → 移动端。

## 结论

**发现 1 个 P1 缺陷**（作者发布后立即看到 404），另有 2 个值得排期的 P2。功能主线
（登录、创作物库、发帖、评审状态、剧场、移动端布局）全部可用；下述问题都在“刚发完帖”
这一最敏感的时刻暴露。

## P1 — 作者刚发布的帖子，点进去是“404 帖子不存在”

- **复现**：登录 → `/post/new` → 填标题 + 含 ```python 代码块的正文 → 提交 → 页面跳转
  `/post/2123503058059694082` → 渲染 **“404 — 帖子不存在 / 这篇帖子可能已被删除或从未存在”**。
- **证据**：后端日志显示该帖**确实入库**（`user_id=3, status=2, post_type=post`，正文含代码块），
  `safety-check-agent` 写下 `pending-llm` 标记（LLM 未启动 → fail-closed，正确行为）。
  即：fail-closed 把帖子 hold 在 `status=2`，而详情页
  `VibePostServiceImpl.getPostDetail` 起写死 `status != 1 → return null`，
  前端把 `null` 渲染成 404。
- **为什么是回归缺口**：`docs/tickets/frontend-polish-round-3.md` 明确修的是**列表侧**
  （“author sees their own non-active posts… on their profile”），详情页不在那一轮的
  Files 里。于是“我的创作物库 / 我的帖子”里能看到这条待审帖，点进去却是 404——作者
  在最短路径上被自己的产品告知“这篇不存在”。
- **影响**：任何带代码块的帖子（触发安全评审）在 LLM 不可用/未通过时都会命中；读者
  视角是“发布功能坏了”。
- **建议**：`getPostDetail` 对作者本人放宽到 `status ∈ {1,2}`（被拒 status=3 仍只走
  作者列表视图），前端把“待审”状态如实渲染，而不是 404。开成了
  `docs/tickets/post-detail-author-404.md`。

## P2-1 — 发布后没有任何“你的帖子正在审核”的过渡反馈

紧接 P1：即便修好详情页，作者点进一篇 `pending-llm` 的帖子，看到的仍是“待审”但**没有
一句话解释“为什么还没发布 / 什么时候会好”**。发布按钮点击后也没有 toast 或跳转说明，
用户只能自己推断。这与仓库一贯的“诚实但不沉默”口径一致，值得补一句状态文案。

## P2-2 — 移动端频道导航只能靠左上角汉堡，底部 tab bar 无频道入口

375×812 下：顶栏有 `Menu` 汉堡（`Navbar.tsx`，`lg:hidden`），点开是 `SidebarDrawer`，
可用；但底部 `MobileTabBar` 只有 首页/搜索/发帖/我的 四个 + 我的菜单，**没有“频道”**。
手机上浏览不同频道必须记住“左上角那个汉堡”，对真实用户是隐藏入口。建议 tab bar
或“我的”菜单里给一个“频道”项，或在首页 feed 里给频道快捷筛选。

## 已确认正常的部分（避免误读）

- 登录、会话保持、`/creations` 四类计数（全部3/文章2/提示词0/Fork1/草稿0）与卡片一致，
  草稿 tab 为空态——符合契约。
- 严重度徽章在 `/creations` 卡片上按真实 `ai_review_log` 渲染（`AI 50 · 1天前` 等）。
- `/theatre` 无需登录、五个场景、REAL/INJECTED/POLICY 徽章、分数盘都正常。
- 移动端布局（feed、tab bar、顶栏）正常渲染，无横向溢出。
- 控制台仅有两条资源 404（favicon 类），无 JS 报错。

## 复现命令

```bash
cd frontend && npm run dev        # 5173
mvn spring-boot:run                # 8081
# 再用 Playwright 走一遍：登录 alice/123456 → 发带代码块的帖 → 打开返回的 /post/:id
```

断言点：发布后跳转的详情页对作者本人应显示待审状态，而不是 404。
