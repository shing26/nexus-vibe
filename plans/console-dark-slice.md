# 控制台暗色切片（Slice C1）—— 首页外壳重做契约

Status: **frozen contract, 2026-11-22.** Base commit `a88f437` (merged master, PR #55).
Branch: `codex/console-dark-slice`, worktree `.worktrees/console-slice`.
取代 [ADR-0020](../docs/adr/0020-the-forge-pivot-replaces-the-terminal-aesthetic.md)
决策 1 的 accent 条款与决策 2，由
[ADR-0023](../docs/adr/0023-the-console-palette-and-a-dark-default.md) 记录。

## 为什么是这一轮

维护者的判定：当前页面「与传统的论坛项目/课程作业没区别」，白底刺眼，且说不清
想要什么。一次 unslop 走查（[诊断与方向](../docs/tickets/console-dark-slice.md)）
把病因定位为 **template look**：GitHub 蓝 accent + 白卡 + 默认字，把产品名换掉
页面照样成立。ADR-0020 当初选 GitHub 蓝是为了压住 v1 的「AI 流水线味」，但走到
今天它自己变成了另一套通用模板。

本轮不新增功能、不改信息架构，只把首页外壳的视觉语言换成产品自己的世界：
夜间值班的控制台。

## 方向（五句，来自 unslop 流程）

- **产品**：把「AI 是不可靠依赖」真实演示给开发者的社区，每篇帖子都真过评审管线。
- **性格**：冷静、可验证、仪器感。
- **世界**：控制台与夜间值班——终端等宽、状态灯、日志时间戳、审计印章。
- **Moments**：① 帖子行的「AI 校验」印章是列表里唯一的丰富色；② 底部评审动态条
  是可读的活日志流。
- **Signature**：审计印章家族——AI 校验、severity、REAL/INJECTED、ticker 时间戳
  共用同一套等宽字 + 细描边语言。

## 范围（Slice C1）

只做 **首页 + 顶栏 + 侧栏 + 页脚评审动态条**，以及被它们直接复用的原语
（头像、帖子行、展示卡、系统脉搏、主题 token）。

不在本轮：详情页信息架构、剧场页视觉、编辑器、管理端、移动端导航改造、
新增页面或功能。

## 变更清单

1. **主题**：暗色成为首次访问的默认主题；亮色保留为可切换的次主题，选择持久化。
2. **Token**：暗色阶换成控制台三层面板 + 发丝线；accent 改为审计绿 `#4ABF92`；
   新增 `--vibe-on-accent`（暗色下 accent 上的文字用墨色，白色在绿上不达 4.5:1）。
3. **顶栏**：品牌标与用户头像改中性墨块；主操作（发帖）为实心绿底 + 墨字。
4. **侧栏**：激活态从蓝底改为表面步进 + 左侧细轨；分组标签改小写、去大写字距。
5. **首页**：任务中心四张等宽彩图标卡 → 一条带分隔线的起始条；剧场入口降为
   细边框行；排序说明句去掉句号。
6. **帖子行**：AI 校验改为绿描边印章（等宽数字）；计数改等宽；去掉冗余分类胶囊；
   为三个计数补可访问名称。
7. **头像**：由按名字哈希出的六种饱和色改为单一中性墨块，身份靠首字母。
8. **动态条**：去掉非实时状态的 ping 动画；等宽时间戳 + 细描边状态章；
   `postTitle` 为 null（帖子已删除）时降级显示帖子号，不再渲染空引号。
9. **降级提示**：系统脉搏标题去大写字距；长句允许换行，不再溢出侧栏。

## Allowed paths

- `plans/console-dark-slice.md`（本契约）
- `docs/adr/0023-the-console-palette-and-a-dark-default.md`
- `docs/design/ui-visual-system-v2.md`（token 表、accent 与 mono 两条原则、主按钮一行，
  以及暗色默认的一句说明）
- `docs/tickets/console-dark-slice.md`
- `docs/INDEX.md`
- `frontend/tailwind.config.js`
- `frontend/src/index.css`
- `frontend/src/stores/themeStore.ts`
- `frontend/src/components/Navbar.tsx`
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/SystemPulse.tsx`
- `frontend/src/components/PostCard.tsx`
- `frontend/src/components/ShowcaseEntry.tsx`
- `frontend/src/components/Avatar.tsx`
- `frontend/src/components/Footer.tsx`
- `frontend/src/components/Footer.test.tsx`（新增）
- `frontend/src/components/Navbar.test.tsx`（新增）
- `frontend/src/components/PostCard.test.tsx`（仅新增断言）
- `frontend/src/pages/HomePage.tsx`
- `frontend/src/pages/TheatrePage.tsx`（仅 `TheatreEntry` 的样式类）
- `frontend/src/pages/PostDetailPage.tsx`（仅标题字重一行）
- `frontend/src/pages/LoginPage.tsx`、`frontend/src/pages/RegisterPage.tsx`
  （仅主按钮文字色）
- `frontend/src/i18n/locales/zh.json`、`frontend/src/i18n/locales/en.json`

`README.md` 的测试计数不在本轮（前端测试数变化由下一轮统一更新）。

## Acceptance criteria

1. 首次访问（localStorage 无主题记录）渲染暗色；点顶栏主题按钮可切到亮色，
   刷新后保持亮色；再切回暗色同样保持。
2. 暗色下 `vibe-accent` 为审计绿，且全站主操作按钮上的文字与绿底对比度 ≥ 4.5:1。
3. 暗色界面中，除语义色（success/warning/danger）与审计印章外，不存在第二个
   装饰性色相；头像不再按名字取色。
4. 首页起始条为一条带分隔线的条目（不再是四张等宽卡片）；剧场入口为细边框行。
5. 帖子行的 AI 校验印章使用等宽数字；三个计数各有**可访问名称**（不是仅有
   `title` 提示），形如 `1 个赞` / `1 条评论` / `26 次浏览`（英文 `1 likes` /
   `1 comments` / `26 views`），且可见数字不重复朗读。
6. 动态条在 `postTitle` 为 null 时显示帖子号，不出现 `「」`；有标题时仍显示标题。
7. 390px 宽度下首页不出现横向溢出，起始条与帖子行按阅读顺序堆叠。
8. 亮色主题仍可用且不回归：切到亮色后文字/背景对比度与当前一致。
9. 既有测试无删除/跳过/弱化；`npm run test`、`npm run lint`、`npm run build` 通过。

## Verification commands

```bash
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff a88f437..HEAD --stat
```

Verifier 需在真实浏览器中亲自复核第 1、3、7 条（主题默认值、单一色相、移动端
不溢出），并绑定 HEAD SHA + `git status --short`。

## Forbidden

- 修改后端、数据库、接口或评审管线。
- 改变首页的信息架构（标签页、排序、分页、剧场入口的落点）或删减内容。
- 引入新的 UI 依赖、字体依赖或图片资源。
- 用「再换一套通用模板」的方式收尾（例如换成另一种流行的中性灰 + 圆角卡片）。
- 顺手改 `README.md`、其它页面或其它人未提交的工作。

## Rejected

- **只调亮色对比度**：白底刺眼是维护者明确的否决点，且不解决 template look。
- **保留 GitHub 蓝、只换暗色底**：蓝色 accent 正是「换个产品名照样成立」的来源。
- **同时重做详情页与剧场页**：两页各有独立的信息架构问题（详情页层级、剧场页
  叙事），混在一轮里无法独立验收，各自成轮。
- **引入设计系统库（shadcn 等）**：项目已有 token 体系，换库会重写全部组件，
  与本轮「只改外壳视觉」的范围不符。
- **用图片/插画充当 hero**：这是社区产品，丰富度应来自真实数据（印章、日志流），
  不是装饰性图片。
