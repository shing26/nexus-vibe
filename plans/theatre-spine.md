# 剧场页的骨架（Slice C4）—— 契约

Status: **frozen contract, 2026-11-23.** Base commit `cadf89f`（C3 已独立 PASS，未合并）。
Branch: `codex/theatre-spine`, worktree `.worktrees/theatre-spine`。
承接 [C3](./theatre-narrative.md) 的叙事修复；C4 只解决“页面骨架与留白”，
不重做 C3 的内容与诚实性。

## 契约修订

- **2026-11-23，AC6 的滚动条件 600px → 200px。** 原条款要求在滚动 600px 时
  落款距视口底 ≤40px。实测页面高 1433px、视口 950px，最大滚动只有 483px；
  滚到 600px 会被浏览器钳制在 483px，此时内容区已结束、站点 footer 进入视口，
  侧栏落款随内容区上移，任何实现都无法同时满足“≤40px”与“不覆盖 footer”。
  条款改为在 `aside` 已 sticky、页面尚未到尾的 200px 处测量；它要证明的
  事情不变：侧栏内容能到视口底部，且 `系统脉动` 与落款之间没有 >300px 的空白。

## 为什么是这一轮

C3 让页面说真话之后，用户连续给出两条反馈：「页面布局会不会有点突兀」「尽量让页面
饱和，不要那么多空白位置」「侧边栏下面还是有空位」。2026-11-23 在 1440×950 的真实
浏览器里量到：

1. **正文两侧各空 152px。** 页面容器 `max-w-4xl`（896px）在 1200px 的主区里居中，
   而「返回」在 x=272、正文在 x=392，**错位 120px**，两条竖线互不相干。
2. **五张卡排 2 列，第 5 张独占一行。** 428×159 的右半格是空的；案例与详情上下
   堆叠，页高 2195px，选一个案例要滚回顶部。
3. **侧栏内容到 540px 就结束。** 滚动到 520px 时，频道栏是 902px 高，而
   `系统脉动` 以下约 410px 是空的；整页截图里栏体还会在视口高度处“断掉”。

这三条都不是审美偏好，是可以用坐标复现的布局缺陷。

## 变更清单

### P0 —— 骨架

1. **一条竖线。** 剧场页容器改用与「返回」相同的 `max-w-[1400px]` 与
   `px-4 sm:px-6 lg:px-8`；≥1280px 时正文左缘与返回按钮左缘对齐（±1px）。
2. **案例索引 + 舞台。** ≥1280px 两栏：304px 竖向索引、剩余宽度给选中案例；
   索引 `sticky`，切案例不用滚回顶部。1024–1279px 仍两栏（索引 260px）；
   <1024px 回到单列卡片网格。五张卡的顺序与三轴内容不变。
3. **消掉半空行。** 评分环与「应用修复」并成一行（左环、右动作），不再各占
   整行；两块的 `data-testid` 与点击行为保留。
4. **表头两栏。** 标题/副标题/论点在左，演示说明在右，说明下补三格真实统计
   （5 案例 / 3 类标签 / 0 写接口）。论点仍排在说明之前。
5. **侧栏通栏。** 频道栏的背景与右边界由 `MainLayout` 的一条绝对定位竖带承担，
   高度覆盖整个内容区；`aside` 仍 `sticky`，只负责内容。整页截图里栏体不再
   在视口高度处截断。
6. **侧栏填满。** 侧栏底部接「最近评审」：5 条来自 `GET /api/v1/agent-logs/ticker`
   的真实记录（时间 / 标题 / 通过|拦截），空数据与请求失败各有文案；60 秒刷新，
   与 footer 复用同一个 query key，不新增请求。栏底再加一行落款
   「只读演示 · 数据可复现」。`<1280px` 时评审块与落款隐藏，48px 图标轨不变。

### P1 —— 密度

7. 区块间距 20→10px、卡片内边距 16→14px、轨迹行 12→9px；页高目标 ≤1600px
   （当前 2195px）。密度只压缩空气，不删内容。

## Allowed paths

- `plans/theatre-spine.md`（本契约）
- `docs/tickets/theatre-spine.md`
- `docs/INDEX.md`
- `docs/design/ui-visual-system-v2.md`（仅 Chaos Theatre 与 Sidebar 两节）
- `README.md`（仅测试计数）
- `frontend/src/components/layout/MainLayout.tsx`
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/SidebarReviews.tsx`（新增）
- `frontend/src/components/SidebarReviews.test.tsx`（新增）
- `frontend/src/pages/TheatrePage.tsx`
- `frontend/src/pages/TheatrePage.test.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

Any file outside this list requires a contract amendment before it is edited.

## Acceptance criteria

1. **一条竖线**：1440px 下 `.theatre-root` 左缘与「返回」左缘差 ≤1px；
   1280px 下差 ≤1px；正文不再有 152px 空槽。
2. **两栏骨架**：≥1280px 为 `304px + 1fr`，索引 `position: sticky`；
   1024–1279px 仍两栏；<1024px 单列。五张案例顺序不变，每张仍有
   `故障`/`防线`/`判定` 三轴非空内容。
3. **评分与动作同行**：评分环与「应用修复」的 `getBoundingClientRect().top`
   差 ≤4px；点击后分数 3/10 → 9/10，模型调用次数仍为 2。
4. **表头**：`theatre-thesis` 在 `theatre-disclosure` 之前；右侧三格数字为
   `5 / 3 / 0`，标签来自 i18n。
5. **侧栏通栏**：1440px、滚动 600px 时，侧栏竖带的
   `getBoundingClientRect().bottom` 等于内容区底部（±2px），不早于 1200px
   视口下缘；栏体不再在 1044px 处结束。
6. **侧栏填满**：1280px 及以上、滚动 200px（`aside` 已 sticky、页面尚未到尾）时，
   侧栏最后一项（落款）底边距视口底部 ≤40px；`系统脉动` 与落款之间不再有
   >300px 的空白。滚动 600px 时页面已接近尾部的站点 footer，落款随内容区一起
   上移，不适用本条的视口距离。
7. **最近评审**：显示 5 条真实 ticker 记录，时间/标题/状态均来自接口；
   接口返回空数组显示 `empty` 文案，请求失败显示 `error` 文案，不渲染假条目；
   不产生额外网络请求（与 footer 共用 `agent-ticker` 缓存）。
8. **既有行为不变**：C3 的 AC1–AC9 全部保持（代码可见、无 `offsetMs`、
   lesson 本地化、375px 不溢出、thesis、三轴、反事实、诚实标签、修复开关）。
9. **响应式与测试**：375/768/1024/1280/1440 无横向溢出；<1280px 隐藏评审块
   与落款；既有断言无删除/跳过/弱化；`npm run test`、`lint`、`build` 通过；
   README 计数同步。

## Verification commands

```bash
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
mvn -B test
git diff --name-only cadf89f..HEAD
```

Verifier 需在真实浏览器中复核第 1、2、3、5、6、7 条，并绑定 HEAD SHA 与
`git status --short`。实现方贴的日志与自评不构成证据。

## Forbidden

- 改 `/api/v1/demo` 契约、评审管线、`ChaosTraceService` 的轨迹内容。
- 删除 C3 的代码面板、三轴、反事实、诚实标签或演示说明。
- 在侧栏写死评审记录，或把 seed 数据说成实时模型调用。
- 为填满空白新增广告位、推荐位或与产品无关的装饰块。
- 改动首页/详情页/管理端；本切片只碰剧场页与公共外壳的侧栏。
- 删除或弱化既有测试；改契约迁就实现。

## Rejected

- **继续用 `max-w-4xl` 单列**：896px 在 1200px 主区里居中，152px 空槽是结构
  决定的，靠调 padding 补不回来。
- **五张卡改 3 列**：5 不能被 3 整除，仍会有孤儿；竖排索引既无孤儿，又能
  让“选案例”成为连续动作。
- **只把侧栏背景拉长、不加内容**：栏体不断了，但 410px 的空白仍在；用真实
  评审流填充，空白变成信息。
- **侧栏改成整页高度、随页面滚走**：会丢掉长页面上的频道导航；保留
  `sticky`，用竖带做连续观感。
- **把 `最近评审` 做成新接口**：footer 已有同一份 ticker 数据，复用 query key
  即可，新增接口只会多一条要维护的路径。
