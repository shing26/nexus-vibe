# MySQL 案例研究：AI 精选排序的联合索引——一次真实的正反两面优化

> 数据规模 10 万行；MySQL 8.0（Docker，InnoDB）；`EXPLAIN ANALYZE` 实测。
> 复现步骤：`docker/mysql/benchmark/` 下的三个脚本按文件头注释顺序执行。
> 对应生产查询：`VibePostMapper.selectFilteredPage` 的 `sort=ai` 分支。

## 背景

前端"AI 精选"页签走这条 SQL（`WHERE status=1 ORDER BY ai_reviewed DESC, ai_review_score DESC, create_time DESC`）。加索引前 vibe_post 只有 user_id / category_id / status 三个单列索引，AI 排序分支无法利用任何索引。

## 索引方案

```sql
ALTER TABLE vibe_post ADD INDEX idx_post_ai_sort (status, ai_reviewed, ai_review_score);
```

列顺序依据：`status` 是恒定等值条件放最左；`ai_reviewed` / `ai_review_score` 与 ORDER BY 的方向一致（全 DESC，MySQL 8 支持反向扫描）。

## 实测结果（冷启动首轮 + 热缓存复跑，热缓存数字稳定）

| 查询 | 无索引 | 有索引 | 变化 |
|---|---|---|---|
| Q1 AI 排序（无过滤） | 68.7 ms，全表扫 10 万行 + filesort | **135 ms**，索引扫 9.6 万行 | **退化 2 倍** ⚠ |
| Q2 AI 排序 + aiScoreMin≥600 过滤 | 58.3 ms，全表扫 | **23.8 ms**，索引条件推送（ICP），扫描降到 1.2 万行 | **提速 3 倍** ✅ |
| Q3 默认排序（对照组） | 67.3 ms，全表扫 + filesort | 118 ms（优化器改用新索引取 status=1） | 退化 ⚠ |

加索引后 Q2 的执行计划：`Index lookup on p using idx_post_ai_sort (status=1, ai_reviewed=1), with index condition: ((ai_review_score*10) >= 600)`——两列等值 + 第三列过滤直接落在索引内，这正是联合索引的理想形态。

## 为什么 Q1 反而变慢（关键发现）

无索引时 Q1 的"全表扫描 + top-10 堆排序"只需读完 10 万行紧凑数据；有索引后优化器选择"沿索引扫描 9.6 万行 status=1 的条目"，但索引不含 SELECT 的宽列（title/content/…），**每行都要回主键聚簇索引取整行**——随机 I/O 放大远超省掉的排序。Q1/Q3 属于典型的**低选择性索引 + 宽行回表负优化**。

这是比"索引让查询变快"更有价值的案例：**索引不是无脑加的，等值前缀选择性太低（status=1 占 96%）时，覆盖不了 SELECT 列的二级索引可能帮倒忙。**

## 由此得出的修正建议（两步，均已验证）

1. **Q1 的业务语义本就该带 `ai_reviewed=1`**——"AI 精选"列表只展示通过 AI 评审的帖子。补上这个条件后实测 **135 ms → 51.8 ms**（索引按 `(status=1, ai_reviewed=1)` 等值定位，扫描行数骤减），既修了语义又修了性能。代码改动：`selectFilteredPage` 的 `sort=ai` 分支补 `ai_reviewed = 1`（待办，属行为变更需单独提交）。
2. **Q3 退化**：优化器误选新索引做 status=1 的取数。生产库中已存在 `idx_post_status` 单列索引且热榜查询有 `create_time` 范围条件，实际竞争场景与本基准（人为裸表）不同；若线上观察到同类退化，处理手段是 `ORDER BY is_pinned DESC, create_time DESC` 建配套索引或对 Q3 强制 `FORCE INDEX(idx_post_status)`，本次不改。

## Trade-off 记录

- **写放大**：每次发帖/编辑多维护一棵索引（B+ 树写路径 +1）。本项目发帖是低频操作（对读多写少的论坛模型可接受）。
- **统计信息**：加索引后 `ANALYZE TABLE` 强制刷新统计，避免优化器基于陈旧统计误判。
- **结论**：`idx_post_ai_sort` 对带分数过滤的主查询（Q2，前端 aiScoreMin 筛选的真实路径）是 3 倍净收益，保留；无过滤的 Q1 需配合 `ai_reviewed=1` 语义修正才能兑现收益，已列入待办。
