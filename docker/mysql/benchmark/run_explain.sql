-- ============================================================================
-- EXPLAIN ANALYZE runs for the AI-curated listing query (selectFilteredPage,
-- sort=ai branch) against nexus_bench.vibe_post (100k rows).
--
-- Run order:
--   1. mysql < run_explain_before.sql   (no ai-sort index yet)
--   2. mysql < apply_ai_sort_index.sql  (add the composite index)
--   3. mysql < run_explain_after.sql    (identical queries, with index)
--
-- Mirrors VibePostMapper.selectFilteredPage:
--   WHERE p.status = 1
--     [AND ai_reviewed = 1 AND ai_review_score * 10 >= :aiScoreMin]
--   ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
--   LIMIT 10 OFFSET 0
-- (the *10 arithmetic is preserved verbatim — it's what production runs)
-- ============================================================================

USE nexus_bench;

-- Q1: AI sort, no extra filter (the "AI 精选" tab default view)
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
LIMIT 10 OFFSET 0;

-- Q2: AI sort + score threshold (frontend aiScoreMin filter)
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1
  AND p.ai_reviewed = 1 AND p.ai_review_score * 10 >= 600
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC
LIMIT 10 OFFSET 0;

-- Q3: default sort (is_pinned/recency) as the control case
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM vibe_post p
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
WHERE p.status = 1
ORDER BY p.is_pinned DESC, p.create_time DESC
LIMIT 10 OFFSET 0;
