# Migration rollback rehearsal (20261114)

Produced by `pwsh -File benchmark/migrations/rehearse-migrations.ps1` against the throwaway project `nexus-migrate` (its own volumes, removed at the end).

Migrations rehearsed: 0005-add-user-email, 0006-add-ai-sort-index, 0007-add-review-lease, 0008-add-comment-safety, 0009-add-comment-sentiment.

Sequence: `init.sql` -> rollback 0005/0006/0007/0008/0009 -> migrate 0005/0006/0007/0008/0009 -> rollback again -> migrate again.

| Step | Expected objects | Observed | Result |
| --- | --- | --- | --- |
| init.sql (fresh volume) | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | PASS |
| after rollback 0005/0006/0007/0008/0009 | (none) | (none) | PASS |
| after migrate 0005/0006/0007/0008/0009 (up) | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | PASS |
| after rollback 0005/0006/0007/0008/0009 (down) | (none) | (none) | PASS |
| after migrate 0005/0006/0007/0008/0009 (up again) | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | column:ai_review_log.comment_id<br>column:sys_user.email<br>column:vibe_comment.sentiment<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_ai_review_log_comment<br>index:idx_post_ai_sort | PASS |

Overall: **PASS**

## Lossy versus lossless

- `docker/mysql/rollback-0005-drop-user-email.sql` declares itself **lossy**: `LOSSY ROLLBACK - DESTROYS DATA. Read this before running it.`
- `docker/mysql/rollback-0006-drop-ai-sort-index.sql` declares itself **lossless**: `LOSSLESS ROLLBACK. Drops no rows and no columns.`
- `docker/mysql/rollback-0007-drop-review-lease.sql` declares itself **lossy**: `LOSSY ROLLBACK - DESTROYS DATA. Read this before running it.`
- `docker/mysql/rollback-0008-drop-comment-safety.sql` declares itself **lossless**: `Rollback of migrate-0008-add-comment-safety.sql. Lossless: it only drops the`
- `docker/mysql/rollback-0009-drop-comment-sentiment.sql` declares itself **lossy**: `Rollback of migrate-0009-add-comment-sentiment.sql. Lossy in one narrow`

## What this does not prove

- The data loss is stated, not measured. The rehearsal runs on an empty schema, so nothing is actually lost; the lossy headers describe what a populated database would lose.
- It does not prove the application still works against the rewound schema. A build that predates a migration is not started here.
- It is not a migration ledger. There is still no schema-version table (DB-1 in docs/tickets/next-cycle-backlog.md stays deferred); the operator decides which migrations a volume has had, from the dump date and a live probe.

