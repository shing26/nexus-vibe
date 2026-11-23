# 剧场页的叙事（Slice C3）—— 契约

Status: **frozen contract, 2026-11-22.** Base commit `c7bb34a` (merged master, PR #58).
Branch: `codex/theatre-narrative`, worktree `.worktrees/theatre-narrative`.
承接 [Slice A](../docs/tickets/chaos-theater-a.md) 与
[A2](../docs/tickets/chaos-theater-a2.md)，但**不重复**它们的范围（A 做诚实回放，A2 做判定兑现）。

## 为什么是这一轮

用户对剧场页的判断是「会的人不想看，不会的人看不懂」。2026-11-22 在源码实例
（`:8099`，真实后端、真实浏览器、中文语境）上复核，发现这不只是观感问题——
**页面的核心承诺在屏幕上不成立**：

1. **代码从头到尾没有出现。** 接口返回 `sampleCode`、`promptPreview`、`fixedCode`，
   但 `TheatrePage.tsx` 只把 `fixedCode` 当布尔开关用（`selected.fixedCode` 出现在
   `:505`、`:612`、`:658`），`sampleCode` 与 `promptPreview` 一次都没渲染。
   页面写着「同一段有缺陷的 divide」「点击应用零值守卫」，读者看不到任何一行代码；
   点「应用修复」后唯一的变化是数字 3 变成 9。**「声称代码变了、屏幕上什么都没变」
   就是「会的人不想看」的根因。**
2. **步进时间是编的，并与真实耗时自相矛盾。** 每步的 `offsetMs` 是硬编码常量
   `0/400/900/1300/1700/2100/2500/2900`（`ChaosTraceService.java:318-423`），
   而同一张卡片上写着实测 `durationMs`——`prompt-injection` 的 `+1700 ms` 与
   「耗时: 4 ms」并列。一个懂行的读者看到这一幕，会判定整页数字不可信，
   而「这些是真实跑出来的」恰恰是本项目唯一的卖点。
3. **`lesson` 是英文。** 中文页面里唯一一句点题的话是英文
   （`zh.json` 无 `lessons.*` 键，`TheatrePage.tsx:576` 直接渲染服务端字段）。
4. **375px 横向溢出 7px。** 顶部导航右侧集群在 375px 需要 206px，连同 logo 与
   抽屉按钮把行宽推到 382px；`/` 首页同样溢出，说明这是共享导航的问题，
   只是剧场页的移动端验收会卡在它上面。

叙事层面的问题（与上面四条独立）：

- **五张卡是五个功能，不是五个案例。** 没有脊梁：失败是什么 → 代价是什么 →
  防线在哪 → 凭什么信。
- **没有代价。** 3/10 → 9/10 是抽象数字，没说这条帖会带着除零崩溃上线。
- **观众是旁观者。** 卡片叫「动手修复」，但访客没动手。
- **诚实标签变成壁纸。** `INJECTED` 几乎每个场景都有；真正需要被看见的
  「模型服从注入、满分合法通过」反而藏在折叠面板后面。
- **样板步骤重复。** `输入已受理`/`提示词已隔离` 在五个场景各出现一次。

## 方向（沿用 C1/C2，不重新发明）

- **产品**：把「AI 是不可靠依赖」真实演示给开发者的社区。
- **性格**：冷静、可验证、仪器感。
- **世界**：夜间值班的控制台。
- **Signature**：审计印章家族（等宽数字 + 细描边）与真实代码陈列。

这一轮只做一件事：**让页面上的每一句话都能被屏幕上的证据支持**，并把五个
功能卡重排成一条能读下去的案例线。

## 变更清单

### P0 —— 诚实性（不做就是缺陷，不是打磨）

1. **渲染被评审的代码。**
   - 每个场景渲染 `sampleCode`，Java 语法高亮。
   - `fix-challenge` 额外渲染 `fixedCode`，与 `sampleCode` 并排（≥640px）或上下
     （窄屏），并标出新增的守卫行。
   - 有缺陷的行用一条左侧标记线 + `data-testid="code-defect-line"` 标出；
     修复后的守卫行用 `data-testid="code-guard-line"`。
   - 复用 `frontend/src/components/CodeBlock.tsx`，新增**可选**的
     `highlightLines?: number[]` 属性（缺省时渲染结果与今天逐像素一致）。
2. **删掉编造的时间轴。**
   - 从 `ChaosStepVo` 删除 `offsetMs` 字段，`ChaosTraceService` 的全部构造点同步删除。
   - 前端 `TheatreStep` 类型删除 `offsetMs`，删除 `+N ms` 芯片（序号 `01/02/…` 保留）。
   - 每个场景的实测 `durationMs` 保留，标签改为「本次回放耗时」；`fix-challenge`
     额外注明该耗时**包含修复前后两次评审**。
   - 不引入任何新的伪造时间字段。
3. **`lesson` 进 i18n。** 新增 `theatre:lessons.<scenarioId>`（zh 主、en 备），
   服务端 `lesson` 仅作 `defaultValue` 兜底。
4. **修 375px 导航溢出。** 顶部导航的 `Nexus-Vibe` 字样在 `sm` 以下隐藏，
   保留 `N` 方块；同时给 logo 链接补 `aria-label`（否则隐藏文字后链接失去可访问名）。
   这是本切片唯一的共享外壳改动，理由写在上面第 4 条：剧场页的 375px 验收
   无法绕过它。

### P1 —— 叙事

5. **一句中文论点，说代价。** 在演示说明之上加一行 `theatre:thesis`：
   一句话说明「模型可以撒谎、可以死、可以吐坏 JSON，而这条管线仍然不会
   把没被审过的东西发出去」。这是页面的第一句话，也是读者带走的那一句。
6. **五张卡改成案例索引。** 每张卡按三个标签排：`故障` / `防线` / `判定`，
   内容来自 `theatre:cards.<id>.fault|defense|verdict`（zh + en）。
   卡片不再是「功能名 + 副标题」，而是一条可比较的分类学。
7. **反事实从折叠改为并列。** 非 `fix-challenge` 场景的反事实面板默认展开，
   ≥640px 时与实际轨迹左右并列（「实际发生」/「如果模型正常」），
   窄屏上下堆叠；保留收起控件。理由：**对照本身就是这节课**，
   藏在按钮后面等于没讲。
8. **重复的准备步骤收成一次性说明。** 五个场景共有的 `input`/`prompt` 两步，
   在轨迹顶部以一行「准备：输入受理 → 提示词隔离（每个场景相同）」说明，
   列表里保留这两步但视觉降权（更小的字、更淡的色），不再占据与
   分歧步骤相同的权重。不删除任何步骤——它们是证据。

## Allowed paths

- `plans/theatre-narrative.md`（本契约）
- `docs/tickets/theatre-narrative.md`
- `docs/INDEX.md`
- `docs/design/ui-visual-system-v2.md`（仅 Chaos Theatre 一节的措辞）
- `README.md`（仅测试计数）
- `src/main/java/com/nexus/campus/dto/ChaosStepVo.java`
- `src/main/java/com/nexus/campus/agent/ChaosTraceService.java`
- `src/test/java/com/nexus/campus/agent/ChaosTraceServiceTest.java`
- `src/test/java/com/nexus/campus/controller/DemoControllerIntegrationTest.java`
- `frontend/src/pages/TheatrePage.tsx`
- `frontend/src/pages/TheatrePage.test.tsx`
- `frontend/src/components/CodeBlock.tsx`（仅新增可选 `highlightLines`）
- `frontend/src/components/CodeBlock.test.tsx`（新增）
- `frontend/src/components/Navbar.tsx`（仅窄屏 logo 与可访问名）
- `frontend/src/components/Navbar.test.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

Any file outside this list requires a contract amendment before it is edited.

## Acceptance criteria

1. **代码可见**：五个场景都渲染各自的 `sampleCode`；`fix-challenge` 同时渲染
   `fixedCode`，并标出守卫行。页面文本中能找到 `public int divide`，
   `prompt-injection` 能找到注入注释那一行。
2. **时间不再伪造**：`ChaosStepVo` 无 `offsetMs`；`TheatreStep` 无 `offsetMs`；
   页面上不存在 `+<数字> ms` 形态的步进芯片；每场景仍显示实测耗时，
   且 `fix-challenge` 的耗时标注为包含两次评审。
3. **`lesson` 本地化**：`zh` 下五个场景的点题句都是中文；`en` 下为英文；
   切换语言后同一场景的句子随之改变。
4. **375px 不溢出**：`/` 与 `/theatre` 在 375px 下
   `documentElement.scrollWidth === clientWidth`；logo 链接仍有可访问名。
5. **论点在首屏**：`theatre:thesis` 出现在演示说明之前，且是中文（zh 下）。
6. **案例索引**：每张场景卡都渲染 `故障` / `防线` / `判定` 三个标签，
   五个场景各有一组非空内容。
7. **反事实并列**：非 `fix-challenge` 场景的反事实默认可见（无需点击），
   ≥640px 时与实际轨迹在同一行的左右两栏，窄屏堆叠；仍有收起控件。
8. **既有行为不变**：诚实标签（REAL/INJECTED/POLICY）、演示说明、
   fail-closed 印章、应用修复开关、payload 展开、JSON 缺陷标记、
   五场景顺序、公开只读接口全部保持。
9. **不削弱测试**：既有断言无删除/跳过/弱化；`mvn -B test`、
   `npm run test -- --pool=threads --maxWorkers=4`、`npm run lint`、
   `npm run build` 通过；README 计数同步。

## Verification commands

```bash
mvn -B test -Dtest=ChaosTraceServiceTest,DemoControllerIntegrationTest
mvn -B test
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff c7bb34a..HEAD --stat
```

Verifier 需在真实浏览器中复核第 1、4、7 条（代码可见、375px 不溢出、反事实并列），
并绑定 HEAD SHA 与 `git status --short`。实现方贴的日志与自评不构成证据。

## Forbidden

- 修改后端评审管线、`ReviewValidity`、`JsonRepairUtil`、`LlmClient` 或任何
  `/api/v1/demo` 之外的接口行为。
- 手写轨迹或把预生成结果说成实时模型调用。
- 引入真实外部模型、访客可编辑代码框、匿名 POST、新运行时依赖或新图片资源。
- 删除诚实标签、演示说明、payload 展开或反事实面板。
- 删除或弱化既有测试；改动契约范围迁就实现。
- 触碰首页信息架构、详情页、剧场页之外的页面。

## Rejected

- **只渲染代码、不动时间轴**：`+1700 ms` 配「耗时 4 ms」是一个自相矛盾的
  证据链，留着它等于把「诚实」这个卖点亲手拆掉；两件事必须同一轮收口。
- **把 `offsetMs` 保留但显示为序号**：字段还在，下一个人就会再渲染成时间。
  删除比改名彻底。
- **访客可编辑代码框**：需要限流、隔离与滥用方案，是独立一轮。
- **把五张卡合并成一张大表**：表格能比较，但会失去「点进去看轨迹」的
  入口；三标签卡片既可比又可点。
- **反事实继续折叠**：A2 的选择在当时合理（页面已经很长），但用户的实际
  反馈是「看不懂」，而反事实正是把「如果模型正常」摆到眼前的唯一手段。
- **把准备步骤删掉**：它们是轨迹的一部分，删掉会让「真实路径」变成
  只剩结论；降权即可。
