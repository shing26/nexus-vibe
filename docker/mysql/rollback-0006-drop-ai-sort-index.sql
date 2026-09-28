-- LOSSLESS ROLLBACK. Drops no rows and no columns.
--
-- This undoes migrate-0006-add-ai-sort-index.sql by dropping idx_post_ai_sort on vibe_post. The
-- index only accelerates "WHERE status=1 ORDER BY ai_reviewed DESC, ai_review_score DESC"; the
-- data it covers stays where it is. The cost of rolling it back is a slower AI-curated listing,
-- and the cost of re-applying it is the time InnoDB needs to build the index again.
--
-- Safe to run against a live database. It is in the rehearsal's sequence because a rollback set
-- that is only exercised on the lossy members is not a rehearsed rollback set.
ALTER TABLE `vibe_post` DROP INDEX `idx_post_ai_sort`;
