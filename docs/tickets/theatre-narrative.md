# Slice C3：剧场页的叙事

Status: **done — independent verification PASS, awaiting sign-off**（2026-11-23）。
契约：[`plans/theatre-narrative.md`](../../plans/theatre-narrative.md)。

## 为什么做

用户对剧场页的判断是「会的人不想看，不会的人看不懂」。2026-11-22 在源码实例
（`:8099`，真实后端、真实浏览器、中文语境）上复核，问题不是观感，而是
**页面的核心承诺在屏幕上不成立**：

1. **代码从未出现。** 接口返回 `sampleCode`、`promptPreview`、`fixedCode`，
   但页面只把 `fixedCode` 当布尔开关。页面写着「点击应用零值守卫」，
   读者看不到任何一行代码，点下去唯一的变化是 3 变成 9。
2. **步进时间是编的。** `offsetMs` 是硬编码常量，与同卡片上的实测
   `durationMs` 并列——`prompt-injection` 的 `+1700 ms` 配「耗时 4 ms」。
3. **`lesson` 是英文。** 中文页面上唯一一句点题的话是英文。
4. **375px 溢出 7px。** 共享导航右侧集群在 375px 需要 206px。

## 改动

### 诚实性

- 每个场景渲染被评审的 `sampleCode`（Java 高亮）；`fix-challenge` 并列渲染
  `fixedCode`，两侧分别标出危险行与守卫行。
- `ChaosStepVo` 与前端 `TheatreStep` 删除 `offsetMs`；页面不再出现 `+N ms`。
  只保留实测耗时，标签改为「本次回放耗时」，`fix-challenge` 注明含两次评审。
- `lesson` 进 i18n（`theatre:lessons.<id>`），服务端字段降为兜底。
- 顶部导航 `Nexus-Vibe` 字样在 `sm` 以下隐藏，logo 链接补 `aria-label`。

### 叙事

- 首屏一句中文论点（`theatre:thesis`），说明代价而不是功能。
- 五张卡改为「故障 / 防线 / 判定」三轴，从功能列表变成可比较的分类学。
- 反事实默认展开，≥640px 与实际轨迹左右并列（「实际发生」/「如果模型正常」）。
- 五个场景共有的 `input`/`prompt` 两步视觉降权，但不删除。

## 实测（2026-11-22，本机）

| 项 | 结果 |
|---|---|
| `mvn -B test` | 563 tests / 0 failures / 0 errors / 0 skipped，覆盖率门槛通过 |
| `npm run test -- --pool=threads --maxWorkers=4` | 171 tests / 33 files 全绿（新增 9 例） |
| `npm run lint` / `npm run build` | 通过 |
| AC1 代码可见（真实浏览器） | `public int divide` 与 `divisor == 0` 均在页面文本中；危险行/守卫行各 1 |
| AC2 无伪造时间 | `step-offset` 节点 0 个，页面文本无 `+N ms` |
| AC4 375px | `/` 与 `/theatre` 均 `clientWidth === scrollWidth === 375` |
| AC6 卡片三轴 | fault/defense/verdict 各 5 个 |
| AC7 反事实并列 | 默认可见，`grid-template-columns: 424px 424px`，标题「实际发生 / 如果模型正常」 |

截图：`scratch/theatre-narrative/c3-fix.png`、`c3-pair.png`、`c3-injection.png`、`c3-375.png`。

## 独立验收（2026-11-23）

Verifier：**独立 subagent `theatre_verifier_v2`**，在 worktree 内亲自重跑命令、
拉起源码实例（后端 `:8098`、Vite `:5198`）并用真实浏览器复核。结论：**PASS**，
无 P0/P1。证据：`scratch/theatre-narrative/verifier-report-v2.md`、`verify-*.png`、
`browser-results.txt`。

- HEAD：`b70ec66ac5fa8ec54cd00473963635b6fa9ffd7d`，`git status --short` 为空。
- AC1–AC9：**全部 PASS**。AC1 五场景代码齐全、危险行/守卫行各 1；AC2 全页
  `step-offset` 0 个；AC4 `/` 与 `/theatre` 在 375px 均 `375 === 375`；
  AC7 640px 两栏同排、375px 堆叠；AC8 既有行为逐项实测保持。
- `mvn -B test`：563 tests / 0 failures / 0 errors / 0 skipped，JaCoCo 79.14%。
- 前端：33 files / 171 tests；`lint`、`build` 退出码 0。
- 路径合规：`git diff --name-only c7bb34a..HEAD` 16 个文件，全部在 Allowed paths 内。

## 未做

- 访客可编辑代码框（需要限流、隔离与滥用方案，独立一轮）。
- 移动端底部 tab bar 在长页面遮挡内容（既有问题，需单独一轮）。
- 剧场页之外的页面视觉调整。

## 已知环境噪声

- 浏览器扩展注入右下角 emoji 浮标，非项目代码。
- `CommentSentimentFilter` 的 React key 警告早于本轮存在。
- 本机 8080 的 Docker 镜像是 2026-11-15 构建，`/api/v1/demo` 仍返回 401；
  复核使用源码实例（8098）。这不是本切片引入的问题，但线上地址需要重建镜像。
