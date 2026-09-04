-- ============================================================================
-- Deep-pagination benchmark: legacy wide-row query vs late row lookup.
--
-- Run order (after seed_bench_data.sql, WITHOUT apply_ai_sort_index.sql —
-- apply_production_indexes.sql already includes idx_post_ai_sort):
--   1. mysql < run_explain_deep_before.sql   (wide-row pagination, as shipped)
--   2. mysql < run_explain_deep_after.sql    (late row lookup)
-- Both scripts run the same logical query (AI sort, no extra filter) at three
-- depths: LIMIT 20 / 2000 / 10000.
-- ============================================================================

USE nexus_bench;

-- ---- Legacy: wide-row pagination (old selectFilteredPage shape) ----

SELECT 'LEGACY L20' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1 AND p.ai_reviewed = 1
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
LIMIT 20 OFFSET 0;

SELECT 'LEGACY L2000' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1 AND p.ai_reviewed = 1
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
LIMIT 2000 OFFSET 0;

SELECT 'LEGACY L10000' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1 AND p.ai_reviewed = 1
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
LIMIT 10000 OFFSET 0;
