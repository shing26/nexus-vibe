# Migration rollback rehearsal (20260921)

Produced by `pwsh -File benchmark/migrations/rehearse-migrations.ps1` against the throwaway project `nexus-migrate` (its own volumes, removed at the end).

Sequence: ``init.sql`` -> rollback 0007/0006/0005 -> migrate 0005/0006/0007 -> rollback again -> migrate again.

| Step | Expected objects | Observed | Result |
| --- | --- | --- | --- |
| init.sql (fresh volume) | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | PASS |
| after rollback 0007/0006/0005 | (none) | (none) | PASS |
| after migrate 0005/0006/0007 (up) | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | PASS |
| after rollback 0007/0006/0005 (down) | (none) | (none) | PASS |
| after migrate 0005/0006/0007 (up again) | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | column:sys_user.email<br>column:vibe_post.review_attempts<br>column:vibe_post.review_lock_until<br>column:vibe_post.review_owner<br>index:idx_post_ai_sort | PASS |

Overall: **PASS**

## Lossy versus lossless

- ``rollback-0006-drop-ai-sort-index.sql`` is lossless: an index covers data, it does not hold it.
- ``rollback-0005-drop-user-email.sql`` destroys every stored address, and ``rollback-0007-drop-review-lease.sql`` destroys the review attempt budget (a post at 4 of 5 attempts becomes indistinguishable from a fresh one). Both say so in their own header and both are preceded by a backup in docs/runbook/restore.md.

## What this does not prove

- The data loss is stated, not measured. The rehearsal runs on an empty schema, so nothing is actually lost; the lossy headers describe what a populated database would lose.
- It does not prove the application still works against the rewound schema. A build that predates the lease columns is not started here.
- It is not a migration ledger. There is still no schema-version table (DB-1 in docs/tickets/next-cycle-backlog.md stays deferred); the operator decides which migrations a volume has had, from the dump date and a live probe.

