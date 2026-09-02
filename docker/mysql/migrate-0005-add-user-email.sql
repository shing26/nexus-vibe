-- One-time migration for MySQL deployments created before sys_user.email was
-- introduced (account-recovery anchor). Fresh volumes get the column via
-- init.sql automatically; existing volumes need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_vibe < docker/mysql/migrate-0005-add-user-email.sql
ALTER TABLE `sys_user`
  ADD COLUMN `email` varchar(100) DEFAULT NULL UNIQUE AFTER `username`;
