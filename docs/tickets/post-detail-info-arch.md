# Slice C2：帖子详情页信息架构

Status: **implemented, awaiting independent verification**（2026-11-22）。
契约：[`plans/post-detail-info-arch.md`](../../plans/post-detail-info-arch.md)。

## 为什么做

Slice C1 只重做首页外壳，详情页仍是 v2 forge 结构，在暗色下暴露三个问题：

1. **P1 对比度回归**：accent 从 GitHub 蓝换成审计绿后，三处
   `bg-vibe-accent text-white` 只有约 **2.1:1**，低于 AA 4.5:1。
2. **头部信息过载**：一条 8 个胶囊的元信息行 + 一条统计行 + 一条 Fork 来源，
   读者扫不出重点。
3. **正文不是主角**：正文与 playground、评审卡、评论框四种同权重卡片连排。

## 改动

- 三处主按钮改用 `text-vibe-on-accent`（Fork、playground 复制、发表评论）。
- 头部拆为身份行（标题 + 作者 · 分类 · 时间，等宽小字）与状态印章行；
  没有值的印章不渲染。
- 正文脱离卡片，直接落在页面底色上。
- 赞/评论/浏览合并进底部操作行，整页只出现一次，各带 `aria-label`。
- AI 评分印章改绿描边等宽，与列表页凭证同语言。
- 区块标题去大写字距。
- 评论筛选标签选中态从 accent 改为中性墨色步进。

## 实测（2026-11-22，本机）

| 项 | 结果 |
|---|---|
| `npm run test -- --pool=threads --maxWorkers=4` | 162 tests / 32 files 全绿（新增详情页 4 例 + 筛选 1 例） |
| `npm run lint` / `npm run build` | 通过 |
| 主按钮对比度（真实浏览器） | 7.846:1 |
| 计数节点数 | 浏览 1、评论 1（整页唯一） |
| 390px | `scrollWidth=clientWidth=390` |

## 未做

- 剧场页视觉与叙事（各自成轮）。
- 编辑器、管理端、消息页的 token 迁移。
- 移动端底部 tab bar 在长页面遮挡内容（既有问题，需单独一轮处理）。

## 已知环境噪声

- 浏览器扩展在右下角注入 emoji 浮标（`tsqd-open-btn-container`），非项目代码。
- `CommentSentimentFilter` 的 React key 警告早于本轮存在。
