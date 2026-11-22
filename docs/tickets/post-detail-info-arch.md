# Slice C2：帖子详情页信息架构

Status: **done**（2026-11-22）。独立 Verifier PASS，HEAD `9271400`。
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

## 验证记录

### 首审 FAIL（`8322d3c`）

| 失败项 | 证据 |
|---|---|
| AC4：点赞按钮可访问名只有「点赞」，可见计数没有名字 | `page.get_by_label('1 个赞').count()` → 0；按钮 AX 名为 `点赞` |
| 契约缺口：README 测试计数未同步，且 `README.md` 不在 Allowed paths | README 仍是 152 frontend / 29 files，实际 162 / 32 |

修复提交 `9271400`：点赞按钮改用 `post:counts.likesValue`（复用已有键），可见数字
`aria-hidden`；README badge、Testing 节、命令注释三处计数同步；契约补 `README.md`
路径并写明 AC4 要求可访问名包含计数。

### 复审 PASS（`9271400`）

| 项 | 结果 |
|---|---|
| 全量前端测试 | `162 passed / 32 files`，退出 0 |
| `npm run lint` / `npm run build` | 退出 0，build 1.84s |
| `git diff --name-only b07fcf3..HEAD` | 11 个文件，与契约 Allowed paths 精确相等 |
| AC1 对比度 | 暗色 7.846:1、亮色 5.192:1（Fork / 复制 Prompt / 发表评论） |
| AC2 头部 | 身份行 + 状态印章行，`emptyBordered=0`、`fullPills=0` |
| AC3 正文 | `.prose` 背景透明，分隔线 `1px rgb(46,52,58)` |
| AC4 计数可访问名 | `343 次浏览` / `1 个赞` / `3 条评论`；`get_by_role('button', name='1 个赞')` = 1 |
| AC5 390px | `clientWidth=scrollWidth=390`，`titleButtonOverlap=false` |
| AC6 既有行为 | AI 凭证、playground、版本面板、评论分组均可用；待审可见性经源码差异确认，未构造待审帖实测 |
| AC7 筛选选中态 | `text-vibe-text` + `bg-vibe-text/10`，未用 accent |
| AC8 测试完整性 | 删除行数 0，无 `.skip` / `.only` / `todo` |

AC6 的待审可见性只做了源码差异确认，没有浏览器实测；这是本轮唯一未取到运行时证据的
子项，条件仍是 `post.status === POST_STATUS.PENDING_REVIEW`，与改动前一致。

浏览器证据：`C:\WINDOWS\AppData\Local\Temp\c2-verify\browser-recheck.json`。

## 未做

- 剧场页视觉与叙事（各自成轮）。
- 编辑器、管理端、消息页的 token 迁移。
- 移动端底部 tab bar 在长页面遮挡内容（既有问题，需单独一轮处理）。

## 已知环境噪声

- 浏览器扩展在右下角注入 emoji 浮标（`tsqd-open-btn-container`），非项目代码。
- `CommentSentimentFilter` 的 React key 警告早于本轮存在。
