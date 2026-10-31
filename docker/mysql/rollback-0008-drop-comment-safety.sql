-- Rollback of migrate-0008-add-comment-safety.sql. Lossless: it only drops the
-- comment_id column and its index, which hold no data the post-side queries
-- read. Apply after backing up, per docs/runbook/restore.md "迁移回滚程序".
ALTER TABLE `ai_review_log`
  DROP INDEX `idx_ai_review_log_comment`,
  DROP COLUMN `comment_id`;
