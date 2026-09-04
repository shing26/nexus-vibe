-- Applies the PRODUCTION secondary indexes to the benchmark schema, so the
-- late-row-lookup comparison runs against a realistic index set (the seed
-- intentionally creates only the PK).
USE nexus_bench;

ALTER TABLE vibe_post
  ADD INDEX idx_post_user_id (user_id),
  ADD INDEX idx_post_category_id (category_id),
  ADD INDEX idx_post_status (status),
  ADD INDEX idx_post_ai_sort (status, ai_reviewed, ai_review_score);

ANALYZE TABLE vibe_post;
