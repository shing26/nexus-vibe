-- Rollback of migrate-0009-add-comment-sentiment.sql. Lossy in one narrow
-- sense: the stored classifications are derived values and are dropped with
-- the column; no comment text or status is touched, and re-applying the
-- migration leaves old rows at 0 (there is no backfill). Apply after backing
-- up, per docs/runbook/restore.md "迁移回滚程序".
ALTER TABLE `vibe_comment`
  DROP COLUMN `sentiment`;
