-- Step 2 of the EXPLAIN case study: add the composite index the production
-- schema ships (migrate-0006), inside the benchmark schema.
USE nexus_bench;

ALTER TABLE vibe_post
  ADD INDEX idx_post_ai_sort (status, ai_reviewed, ai_review_score);

ANALYZE TABLE vibe_post;
