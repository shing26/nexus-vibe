# Like concurrency summary (20260921)

Produced by `pwsh -File benchmark/concurrency/like-concurrency.ps1` against `http://localhost:18081`, 50 threads released through a shared barrier.

| Check | Expected | Observed |
| --- | --- | --- |
| HTTP 200 on like burst | 50 | 50 |
| Redis SCARD after like burst | 50 | 50 |
| vibe_post_like rows after like burst | 50 | 50 |
| vibe_post.like_count after LikeSyncTask | 50 | 50 |
| HTTP 200 on unlike burst | 50 | 50 |
| Redis SCARD after unlike burst | 0 | 0 |
| vibe_post_like rows after unlike burst | 0 | 0 |
| vibe_post.like_count back at zero | 0 | 50 |

Latency: like p50=1898ms p99=2197ms; unlike p50=1042ms p99=1509ms.

## What this run does not show

- `like_count` is written by LikeSyncTask, not by the request path, so the value above is the state after the five-minute cron, not at response time. This run waited 35s for the like flush and 320s for the unlike flush.
- It runs against a scratch stack (benchmark/concurrency/docker-compose.concurrency.yml), not the live deployment. The limiter is active there; the script sends one X-Real-IP per probe user so 50 simultaneous likes are 50 buckets rather than one.
- It proves the three views agree for one post. It does not sweep for drift; that is DriftReconcileTask's job and is covered by its own tests.

## The unlike leg, and why it is the interesting one

The like direction converges. The unlike direction does not, and the two tasks that could fix it both decline:

- ``LikeSyncTask`` refuses to write zero when the Redis set and the membership table are both empty but ``like_count`` is not: ``[LIKE-SYNC] Refusing to write zero for post <id>: Redis=0, durable=0, stored=N``. It then requeues the post, so the same warning is emitted every five minutes for as long as the post exists.
- Its message says "reconciliation will rebuild membership", and that is wrong. ``DriftReconcileTask`` takes the union of the same two now-empty sources and preserves the count too: ``[DRIFT] Post <id> has no recoverable members but like_count=N; preserving the count for manual recovery``.

So a post that has ever been liked and then fully unliked keeps a stale ``like_count`` in MySQL permanently, and the Redis set - which is what the API actually serves - is correct and reads zero. The guard is not a mistake: it exists because the membership table was once never written, and "both membership sources are empty" is the only signal available to tell a lost set from a legitimate empty one. The defect is that the same condition is also exactly what a real unlike-to-zero looks like.

This round did not change either task. The two candidate fixes and their trade-offs are recorded in docs/tickets/evidence-and-gates.md.

**Resolved 2026-09-23** (docs/tickets/like-count-convergence.md): the durable membership table is
the authority, so LikeSyncTask replays it into Redis and writes its count, and DriftReconcileTask
converges "both sources empty plus a positive count" to zero. Neither of the two candidate fixes
above was needed. The script's own expectation is unchanged - it was right and the code was wrong -
so re-running it against a build that carries the fix should exit 0.

## Failures

- vibe_post.like_count back at zero expected 0, observed 50

