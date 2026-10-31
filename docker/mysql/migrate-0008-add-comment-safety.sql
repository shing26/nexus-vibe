-- One-time migration for MySQL deployments created before the comment safety
-- path existed (ADR-0016 / ADR-0017): ai_review_log gains a nullable
-- comment_id so comment-safety rows can be told apart from post rows, and
-- reconcile can find pending-llm comments. Fresh volumes get the column via
-- init.sql automatically; existing volumes need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_campus < docker/mysql/migrate-0008-add-comment-safety.sql
ALTER TABLE `ai_review_log`
  ADD COLUMN `comment_id` bigint DEFAULT NULL AFTER `post_id`,
  ADD INDEX `idx_ai_review_log_comment` (`comment_id`, `reviewer`);
