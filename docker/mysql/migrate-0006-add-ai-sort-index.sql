-- One-time migration for MySQL deployments created before the AI-sort
-- composite index existed (covers the AI-curated listing:
-- WHERE status=1 ORDER BY ai_reviewed DESC, ai_review_score DESC).
-- Fresh volumes get the index via init.sql automatically; existing volumes
-- need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_campus < docker/mysql/migrate-0006-add-ai-sort-index.sql
ALTER TABLE `vibe_post`
  ADD INDEX `idx_post_ai_sort` (`status`, `ai_reviewed`, `ai_review_score`);
