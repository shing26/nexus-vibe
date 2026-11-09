-- One-time migration for MySQL deployments created before the three-way
-- comment split existed (ticket docs/tickets/frontend-polish-round-3.md,
-- item 7; no ADR covers it): vibe_comment gains a nullable-free sentiment
-- tinyint (0 other/unclassified, 1 question, 2 complaint, 3 praise), written
-- by the deterministic keyword classifier at insert time. Existing rows take
-- the default 0 and are not backfilled. Fresh volumes get the column via
-- init.sql automatically; existing volumes need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_campus < docker/mysql/migrate-0009-add-comment-sentiment.sql
ALTER TABLE `vibe_comment`
  ADD COLUMN `sentiment` tinyint NOT NULL DEFAULT 0 AFTER `status`;
