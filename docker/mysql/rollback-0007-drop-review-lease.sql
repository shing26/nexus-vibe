-- LOSSY ROLLBACK - DESTROYS DATA. Read this before running it.
--
-- This undoes migrate-0007-add-review-lease.sql by dropping three columns from vibe_post:
--   review_lock_until  the lease expiry written by tryClaimReview
--   review_owner       the instance holding the lease
--   review_attempts    how many attempts a review has already consumed
--
-- review_attempts is the one that cannot be rebuilt. It is the attempt budget: a post at 4 of 5
-- attempts looks identical to a post at 0 once the column is gone, so the reconciliation sweep
-- will start over on work that had already been retried, and the terminal notification the budget
-- exists to send exactly once may be sent again. review_lock_until and review_owner are transient
-- (a lease is re-claimable by design), so only the budget is genuinely lost - but it is lost
-- silently, which is worse than an error.
--
-- It exists for a rehearsal that has to return a database to the pre-0007 shape, and for a
-- rollback to a build that predates the lease. Take a backup first: docs/runbook/restore.md,
-- "迁移回滚程序".
ALTER TABLE `vibe_post`
  DROP COLUMN `review_lock_until`,
  DROP COLUMN `review_owner`,
  DROP COLUMN `review_attempts`;
