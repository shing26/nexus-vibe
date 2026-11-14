# 设计改造轮：forge 风格改造（GitHub/linux.do）+ 中英文 i18n

Status: **How 层已定（2026-11-15 grill 访谈），契约待用户确认冻结**（契约：[plans/design-overhaul-round.md](../plans/design-overhaul-round.md)；规格：[docs/design/ui-visual-system-v2.md](../design/ui-visual-system-v2.md)；决策记录：ADR-0020）。未冻结不得实现。

## Scope

用户 2026-11-14 提出的改造请求：站点"AI 流水线味重"，参考 GitHub / linux.do（Discourse）论坛设计改造；补上从未存在的中英文切换。本轮只动 `frontend/`。

2026-11-15 grill 访谈定案了 How 层（逐项用户拍板，详见契约"已定案的设计决策"表）：

- **骨架**：GitHub 式顶栏 + linux.do 式左侧频道栏（≥lg 常驻、<lg 汉堡抽屉、底部 tab bar 保留），侧栏含社区/我的两个分组
- **密度**：帖子列表换 Discourse topic-list 式紧凑行；帖子详情单列（Discussions 式）
- **AI 终端**：重绘为折叠式 check-run 卡——解决它的真问题（太长推走评论流），语义全保留
- **主题**：跟随系统不变，亮暗两套色板换 v2 值（GitHub 蓝 + 中性灰阶）
- **i18n**：i18next，中文源、跟随浏览器、手动切换持久化
- 保留：CommandPalette（Ctrl+K）、首页 tabs、Evidence links（footer）、MobileTabBar

背景事实（2026-11-14 盘点）：无 i18n 库，92 行硬编码中文散布 8 文件，其余页面纯英文；"流水线味"来源 = BorderBeam/DecryptedText/ShimmerButton/SpotlightCard 四特效组件 + 满屏 mono + 霓虹青紫 + 点阵背景 + 5 处逐字复制的 shimmer 骨架；21 个测试文件钉着现有文案；HomePage 与 Sidebar 各自维护频道定义。

## Acceptance

见契约 13 条。要点：汉字 grep 归零、特效组件归零引用、shell 三形态（≥lg / 抽屉 / 底部栏）落地、折叠式 AI 终端语义不变、双主题 AA、双语言切换持久化、双端截图入库。

## Files

`frontend/**`（含 tailwind.config、package.json 仅加 i18n 两依赖）、`docs/design/ui-visual-system-v2.md`（新增）、v1 加取代标注、ADR-0020、`docs/assets/*`（新截图）、`README.md`（仅计数）。

## Rejected

- **后端 i18n（Accept-Language 协商）**：envelope 文案是运维面；牵动全部 controller 与错误码测试，与诉求不成比例（ADR-0020）。
- **自研 i18n 方案**：17 页 × 全量文案 + 复数/插值/回退，省两个依赖不值。
- **只换皮不动骨架**：到不了用户点名的 linux.do 参照；频道导航仍埋在顶栏。
- **退役 AiReviewTerminal / 极简纯文本化**：删掉项目主题本身的演示面；折叠模式以更低代价解决长度问题。
- **完整 Discourse 克隆**（分类权限、徽章、反应）：作品集定位不是论坛产品，借骨架与密度足够。
- **改前端渲染兼容已转义存量内容**：渲染层领地在上一轮已定（ADR-0021 出口转义），"显示兼容存储缺陷"是把缺陷固化。
- **一轮同时深化移动端适配**：现有断点行为保留，深化另立轮，避免 diff 不可审。
