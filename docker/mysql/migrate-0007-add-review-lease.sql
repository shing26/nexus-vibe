-- One-time migration for MySQL deployments created before the review lease
-- columns existed (atomic claim for the AI review pipeline, ADR-0005).
-- Fresh volumes get the columns via init.sql automatically; existing volumes
-- need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_campus < docker/mysql/migrate-0007-add-review-lease.sql
ALTER TABLE `vibe_post`
  ADD COLUMN `review_lock_until` datetime DEFAULT NULL AFTER `forked_from_id`,
  ADD COLUMN `review_owner` varchar(64) DEFAULT NULL AFTER `review_lock_until`,
  ADD COLUMN `review_attempts` int NOT NULL DEFAULT 0 AFTER `review_owner`;
