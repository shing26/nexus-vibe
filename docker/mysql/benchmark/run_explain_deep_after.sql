-- ============================================================================
-- Late row lookup variant of the same deep-pagination queries
-- (new selectFilteredPage shape). Run after run_explain_deep_before.sql.
-- ============================================================================

USE nexus_bench;

-- ---- Late row lookup: ids in the inner query, wide rows by PK outside ----

SELECT 'LATE L20' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM (SELECT p2.id FROM vibe_post p2
      WHERE p2.status = 1 AND p2.ai_reviewed = 1
      ORDER BY p2.ai_reviewed DESC, p2.ai_review_score DESC, p2.create_time DESC
      LIMIT 20 OFFSET 0) ids
JOIN vibe_post p ON p.id = ids.id
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC;

SELECT 'LATE L2000' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM (SELECT p2.id FROM vibe_post p2
      WHERE p2.status = 1 AND p2.ai_reviewed = 1
      ORDER BY p2.ai_reviewed DESC, p2.ai_review_score DESC, p2.create_time DESC
      LIMIT 2000 OFFSET 0) ids
JOIN vibe_post p ON p.id = ids.id
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC;

SELECT 'LATE L10000' AS case_label;
EXPLAIN ANALYZE
SELECT p.*, u.nickname AS authorName, c.name AS categoryName
FROM (SELECT p2.id FROM vibe_post p2
      WHERE p2.status = 1 AND p2.ai_reviewed = 1
      ORDER BY p2.ai_reviewed DESC, p2.ai_review_score DESC, p2.create_time DESC
      LIMIT 10000 OFFSET 0) ids
JOIN vibe_post p ON p.id = ids.id
LEFT JOIN sys_user u ON p.user_id = u.id
LEFT JOIN vibe_channel c ON p.category_id = c.id
ORDER BY p.ai_reviewed DESC, p.ai_review_score DESC, p.create_time DESC;
