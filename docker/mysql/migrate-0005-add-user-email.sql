-- One-time migration for MySQL deployments created before sys_user.email was
-- introduced (account-recovery anchor). Fresh volumes get the column via
-- init.sql automatically; existing volumes need this applied once:
--   docker compose exec -T db mysql -u root -p"$DB_PASSWORD" nexus_campus < docker/mysql/migrate-0005-add-user-email.sql
--
-- Two things worth knowing before running it, both learned from docs/runbook/restore.md:
--   * The database is nexus_campus (compose sets MYSQL_DATABASE), not the nexus_vibe this line used
--     to name. There is no nexus_vibe on any host that followed the README.
--   * init.sql has created this column since ee09486, so on a volume newer than that the statement
--     fails with ERROR 1060 Duplicate column name 'email'. That failure is the proof you did not
--     need the migration; it is not a reason to keep the volume's backups.
ALTER TABLE `sys_user`
  ADD COLUMN `email` varchar(100) DEFAULT NULL UNIQUE AFTER `username`;
