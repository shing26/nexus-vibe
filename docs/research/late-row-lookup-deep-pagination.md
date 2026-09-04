# 深分页案例研究：延迟关联在 MySQL 8 上是一次负优化（含回滚决策）

> 10 万行 nexus_bench（生产同款索引含 idx_post_ai_sort）；MySQL 8.0 EXPLAIN ANALYZE 实测。
> 复现：`docker/mysql/benchmark/` 下 seed → apply_production_indexes → run_explain_deep_before（旧 SQL）→ run_explain_deep_after（延迟关联）。
> 对应生产查询：`VibePostMapper.selectFilteredPage`（AI 排序分支）。

## 背景与假设

经典优化建议：深分页（`LIMIT 10000, 20`）时宽行回表开销巨大，应改"延迟关联"——内层子查询只走覆盖索引取主键，外层再按主键取宽列。本项目的 `selectFilteredPage` 带多维过滤与三档排序，是该建议的标准适用场景。

## 实测数据（10 万行，ai_reviewed=1 占 3 万）

| 深度 | 旧 SQL（宽行分页） | 延迟关联 | 变化 |
|---|---|---|---|
| LIMIT 20（首页） | 98.3 ms | **138 ms** | **退化 40%** ⚠ |
| LIMIT 2000 | 89.3 ms | **109 ms** | 退化 ⚠ |
| LIMIT 10000 | 110 ms | **160 ms** | 退化 ⚠ |

两个版本的执行计划核心算子相同：`Sort row IDs: ... limit input to N per chunk`——**MySQL 8 优化器在覆盖索引上直接做索引序 top-N 截断，根本不产生宽行扫描**（LEGACY L20 的源算子实际只读索引条目，98ms 里 77ms 是 3 万索引条目的排序读）。延迟关联版额外引入派生表物化 + 回表 JOIN，反而增加 30-50% 开销——且**首页退化最重**（最常用的路径）。

## 为什么经典建议在这里失效

"深分页宽行回表"是 5.6/5.7 时代的真痛点。MySQL 8 的优化器对"覆盖索引 + ORDER BY 索引序 + LIMIT"已经内置了 **`Sort row IDs` top-N 短路**（`limit input to N per chunk`）：排序在索引条目上完成，只有最终 N 行才回表。延迟关联是手动实现了优化器已经内置的东西，还多付了一层派生表代价。

## 决策：回滚重写，保留测量

- mapper 重写已回滚（`git checkout`），生产 SQL 保持旧形态——实测证明它不慢；
- 基准脚本入库（`run_explain_deep_before/after.sql`、`apply_production_indexes.sql`），可随时复现；
- 真正的收益场景留给未来：若查询失去覆盖性（如过滤列变更导致索引失效），或升级到无此优化器的环境，本基准可作为回归工具重跑。

## 方法论备注

- 两条 EXPLAIN 的源算子均只读索引条目（rows=96000 为 `status=1` 的索引条目数，无宽列回表）；
- 每档测量为冷启动首轮 + 复跑稳定值一致；对比基于同一 10 万行数据与同一索引集；
- 与 [mysql-ai-sort-index-explain.md](mysql-ai-sort-index-explain.md) 的正收益案例合并阅读：**同一索引、同一数据，不同查询形态一个提速 3 倍、一个退化 40%——索引与改写策略永远跟着执行计划走，不跟着"最佳实践清单"走。**
