# 帖子详情页信息架构（Slice C2）—— 契约

Status: **frozen contract, 2026-11-22.** Base commit `b07fcf3` (merged master, PR #57).
Branch: `codex/detail-info-arch`, worktree `.worktrees/detail-slice`.
承接 [Slice C1](../docs/tickets/console-dark-slice.md) 的控制台语言，
但**不重复**它的范围（C1 只做首页外壳）。

## 为什么是这一轮

Slice C1 只重做了首页外壳。详情页仍是 v2 forge 时期的结构，在暗色下暴露了三个
真实问题：

1. **对比度缺陷（P1）**：三处主按钮仍是 `bg-vibe-accent text-white`。accent 从
   GitHub 蓝换成审计绿后，白字对 `#4ABF92` 只有约 **2.1:1**，低于 AA 的 4.5:1。
   这是 C1 改 accent 的连带后果，必须在下一轮收口。
2. **头部信息过载**：标题行下方一条 8 个元素的胶囊行（作者、分类、待审、
   Template、role、版本号、分数、时间），再一条统计行，再一条 Fork 来源。
   读者扫不出「这是什么、谁写的、什么时候、值不值得读」。
3. **正文与元信息层级倒置**：正文被包在 `bg-vibe-surface` 卡片里，与
   playground、评审卡、评论框四种同权重卡片连续堆叠，正文没有成为页面主角。

## 方向（沿用 C1，不重新发明）

- **产品**：把「AI 是不可靠依赖」真实演示给开发者的社区。
- **性格**：冷静、可验证、仪器感。
- **世界**：夜间值班的控制台。
- **Moments**：详情页的 AI 评审凭证卡（分数、severity、模型、调用次数）。
- **Signature**：审计印章家族（等宽数字 + 细描边）。

## 变更清单

1. **对比度修复**：三处 `bg-vibe-accent text-white` 改为 `text-vibe-on-accent`
   （Fork、playground 复制、发表评论）。
2. **头部重排为两段**：
   - 第一段（身份）：标题 `text-2xl`，下一行作者 + 分类 + 相对时间，
     全部为 `font-mono text-xs text-vibe-faint`。
   - 第二段（状态）：仅在有值时渲染，作为审计印章行——待审、Template、
     role、版本号、AI 分数。没有值的印章不占位。
3. **正文成为主角**：正文卡从 `bg-vibe-surface` 改为无卡片背景（直接落在
   `vibe-bg` 上），保留 Markdown prose 与代码块；正文与后续区块用
   `border-t border-vibe-border` 分隔，而不是继续堆卡片。
4. **统计与操作合并为一条**：`赞 / 评论 / 浏览` 与 `点赞 / 分享` 合成一行
   等宽计数，去掉重复的两处数字（头部统计行与底部互动栏各显示一次）。
5. **区块标题统一**：评论、playground 等区块标题去掉大写字距，改为
   `text-xs text-vibe-faint`。
6. **可访问性**：统计计数补 `aria-label`（沿用 C1 的 `1 个赞` 形式），
   图标 `aria-hidden`。
7. **评论筛选标签**：`CommentSentimentFilter` 的选中态从蓝底改为中性墨色面
   步进，与「单一色相」一致（该组件只出现在详情页）。

## Allowed paths

- `plans/post-detail-info-arch.md`（本契约）
- `docs/tickets/post-detail-info-arch.md`
- `docs/INDEX.md`
- `docs/design/ui-visual-system-v2.md`（仅 Post detail 一节的措辞）
- `frontend/src/pages/PostDetailPage.tsx`
- `frontend/src/pages/PostDetailPage.test.tsx`（新增）
- `frontend/src/components/CommentSentimentFilter.tsx`（仅选中态与链接色）
- `frontend/src/components/CommentSentimentFilter.test.tsx`（仅新增断言）
- `frontend/src/i18n/locales/zh.json`、`frontend/src/i18n/locales/en.json`
- `README.md`（仅测试计数，见「契约补充」）

## Acceptance criteria

1. 暗色下 Fork、playground 复制、发表评论三个主按钮的文字色为
   `vibe-on-accent`，与 `vibe-accent` 底对比度 ≥ 4.5:1；亮色下同样 ≥ 4.5:1。
2. 头部只有两段：身份行（作者、分类、时间）与状态印章行；没有值的印章不渲染，
   不出现空胶囊。
3. 正文不再有卡片背景，直接落在页面底色上；正文与后续区块之间是发丝分隔线。
4. 赞/评论/浏览在整页只出现一次（合并后的操作行），且各有可访问名称；
   点赞按钮的可访问名必须包含当前计数（如 `1 个赞` / `1 likes`），不能只是
   「点赞」这个动作名。
5. 390px 宽度下详情页无横向溢出，标题不与操作按钮重叠。
6. 既有的待审可见性、AI 评审凭证、playground、版本面板、评论分组行为不变。
7. 评论筛选标签的选中态不再使用 accent 色，与页面其余部分同属中性色阶。
8. 既有测试无删除/跳过/弱化；`npm run test`、`npm run lint`、`npm run build` 通过。

## Verification commands

```bash
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff b07fcf3..HEAD --stat
```

Verifier 需在真实浏览器中复核第 1、4、5 条（对比度、计数唯一性、移动端不溢出）。

## 契约补充（2026-11-22，Verifier 首审后）

首审 FAIL 两项，均为契约自身的遗漏，不是范围变更：

1. **AC4 的点赞计数**：按钮的可访问名只有 `actions.like`（「点赞」），可见计数
   `1` 不在可访问名内。修复为复用已存在的 `post:counts.likesValue`
   （`{{count}} 个赞` / `{{count}} likes`），并把可见数字标为 `aria-hidden`，
   与浏览/评论两个计数的写法一致。
2. **README 测试计数**：仓库根 `AGENTS.md` 要求改动测试计数时同步 README badge
   与 Testing 节，但本契约的 Allowed paths 漏了 `README.md`。补充该路径，
   仅允许改测试计数（badge 与 Testing 节两处数字），不允许改其它内容。

因此 Allowed paths 追加一项：`README.md`（仅测试计数）。

## Forbidden

- 修改后端、接口、评审管线或数据契约。
- 改变待审帖的可见性规则（作者可见、他人/匿名 404、status=3 不放开）。
- 重做首页、剧场页或其它页面。
- 引入新依赖或新图片资源。
- 删除 playground、AI 评审凭证、版本面板或评论分组功能。

## Rejected

- **只修三处对比度**：那是 P1 的最小修复，但头部 8 胶囊与正文卡片化是两个
  独立的层级问题，分轮做会让详情页在两次改动之间显得更乱。
- **把详情页做成双栏（正文 + 右侧目录）**：移动端优先，双栏在 390px 无处可放；
  且本项目没有足够长的正文需要目录。
- **把 AI 评审凭证移到侧栏**：它是本页的 Moment，放在正文下方、评论之前
  才符合阅读顺序。
- **同时改剧场页**：剧场页有独立的叙事问题，各自成轮。
