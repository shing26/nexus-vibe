# 契约草案 — 设计改造修复轮 B（外审 B 档 + v1 遗产捡回 + "课程作业感"治理）

任务 ID：`design-overhaul-fixes-b`
状态：**已冻结（2026-11-16，A 轮 PR #43 合入后；用户两轮访谈逐项拍板共 15 项，签收时授权"绿灯合并并冻结 B 轮"）。**

A/B 分轮原因：两轮重叠 HomePage/PostCard/Navbar/词典/评审组件——两个实现者不得同时碰同一批文件。
来源：锐评 B 档（6 项）+ 对比结论 D 清单（7 项）+ "课程作业感"批评（4 项，含 3 项对已锁决策的修订）。
批评的"半成品感"清单已由 A 轮覆盖；"签名面/去仪表盘化/语气/策展"落在本轮。

## 定案决策表（2026-11-16，用户逐项拍板）

### 信息架构（去仪表盘化）

| # | 决策 | 定案 |
| --- | --- | --- |
| B1 | 频道宫格 | **删除**首页 7 列宫格，回归规范"sidebar 是频道唯一导航面" |
| B2(修订) | 首屏结构 | 登录态首屏 = **一条主推（真实评审 showcase）+ tabs + 列表**；未登录态保持 任务中心 + Showcase + 列表 |
| B3 | 行 meta 减法 | 列表行核心四件：频道徽章 + 作者 + 相对时间 + 右侧计数；role/模板移至详情页 |
| B4(修订) | profile 统计 | **三项主格：帖子 / 收到的赞 / 被评审过**；其余（AI 评分/Fork/版本/评论等）收进一行文字（数据不删） |
| B7(新) | 频道右栏 | **删除**"等待回答"右栏（A 轮频道化 API 能力保留，前端不再展示） |

### 签名面（把真实系统推到台前）

| # | 决策 | 定案 |
| --- | --- | --- |
| B8(新) | 评审凭证 | AiReviewTerminal 改**凭证式默认展开**：结论 + 分数 + 模型（reviewer 字段）+ 调用次数（同帖 log 行数推导）+ ai_review_log 链接默认可见；三段 findings 折叠。**运行时长不新加列**——由 AiReviewService 写入既有 resultJson（打点），前端读取展示；避免 schema 迁移。修订原"折叠式 check-run"决策（ADR-0020 保留数据契约，默认态反转需在 ticket 记录并知会 ADR） |
| B9(新) | 语气统一 | 三处俏皮 envelope 中性化（登录/发帖/点赞，遵循 D7 方言规则：terminal 腔只保留在运维面）：`Neural link established…`、`Data injection protocol acknowledged.`、`Energy increment synchronized.` → 中性中文（与既有 envelope 语言一致）；相关前端测试同步 |
| D7 | 双方言规则 | 入规范一条原则：公开面 forge、运维面（AgentLogsPage）保留 terminal 方言 |

### v1 遗产捡回（能力面）

| # | 决策 | 定案 |
| --- | --- | --- |
| D1 | 列表代码预览 | 行内可展开首块代码/hover 预览（v2 形式，不回卡片）；getPreview() 升级为列表可见 |
| D2 | 全局返回 | 公开面非首页恢复页内返回（BackButton 复活，v2 度重绘） |
| D3 | 访客主题切换 | 顶栏主题切换按钮（登录态访客态均可见） |
| D4 | 顶栏证据链接 | 顶栏恢复 ADRs / Research 入口 |
| D5 | 按压反馈 | 可点元素恢复 active:scale-[0.98] 级克制反馈（规范第 3 条"传达状态"许可；motion-reduce 禁用） |
| D6 | 侧栏折叠态 | 1024–1280 区间折叠为 48px 图标态（tooltip 可达） |
| B5 | 语言按钮 aria | 当前语言完整句（zh 念"切换到英语"）；可见字形不变 |
| B6 | 浅色代码块 | 浅色主题浅色配色，暗色不变，AA 达标 |

### 数据策展（"作业"与"产品"的分界）

| # | 决策 | 定案 |
| --- | --- | --- |
| B10(新) | 演示数据 | `db/mysql/demo-content.sql` 重写为 8–12 条像真人的帖子（含带真实评审记录的 showcase）；**线上数据清理不在本轮**——随发布走运维步骤（涉及线上数据删除，单独确认） |

## Allowed paths（草案，冻结时复核）

```
frontend/src/**
src/main/java/com/nexus/campus/controller/AuthController.java   # B9 登录文案
src/main/java/com/nexus/campus/controller/PostController.java   # B9 发帖/点赞文案
src/main/java/com/nexus/campus/agent/**                         # B8 resultJson 时长打点
src/test/java/**                                                # 后端测试
src/main/resources/db/mysql/demo-content.sql                    # B10
docs/design/ui-visual-system-v2.md                              # B1 首页结构同步、D5 动效澄清、D7 双方言原则、B8 默认态修订
docs/adr/0020-*.md                                              # 如需记录评审卡默认态反转（或新 ADR-0022 片段）
docs/tickets/design-overhaul-fixes-b.md
README.md                                                       # 仅测试计数
docs/assets/design-overhaul/**                                  # 失效截图重摄（干净配置）
```

## Acceptance criteria（草案，冻结时细化）

1. 首页：无宫格；登录态 = 主推一条 + tabs + 列表（1280px 内列表 ≥4 行可见）；未登录态保留任务中心/Showcase；频道页无右栏。
2. 评审卡凭证默认可见（结论/分数/模型/调用次数/日志链接），findings 折叠；运行时长来自 resultJson 打点；mvn -B test 全绿。
3. 登录/发帖/点赞 envelope 文案中性化，全部前端走词典展示，测试钉住。
4. 列表行核心四件 + 可展开代码预览，1280/375 单行；profile 三项主格 + 一行次级文字。
5. 公开面非首页有页内返回；访客可顶栏切主题；顶栏有 ADRs/Research；按压反馈存在且 motion-reduce 禁用；1024–1280 图标折叠态。
6. B5/B6/D7 同前；demo-content.sql 为 8–12 条真人向帖子且含真实评审记录样本。
7. 失效截图重摄（干净配置）；README 计数同步；断言改动逐条留痕；三套验证（test/lint/build + mvn）全绿；无新增运行时依赖。

## Verification commands（草案）

```bash
cd frontend && npm run test && npm run lint && npm run build
mvn -B test
grep -rn 'lg:grid-cols-7\|channelGridAria\|Neural link\|Data injection protocol\|Energy increment' frontend/src src/main/java   # 期望 0 行
grep -rn 'active:scale' frontend/src | wc -l    # 期望 > 0
# 凭证可见性/行单行/折叠态/主推：Verifier 目验截图 + DOM 抽查
```

## Forbidden

数据库 schema 不新增列（B8 用 resultJson 打点规避迁移）；线上数据不动（B10 只改 demo-content.sql）；
docker/.env 不碰；不新增运行时依赖；路由不变；断言不弱化；v2 规范与 ADR-0020 正文改动仅限
B1/B8/D5/D7 四条同步。
