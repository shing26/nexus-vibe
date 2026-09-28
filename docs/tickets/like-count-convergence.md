# Like-count convergence, and the two claims this round had to withdraw

Source: the open defect `docs/tickets/evidence-and-gates.md` recorded under B2, plus what a
2026-09-23 re-run of `benchmark/jmeter/run-loadtest.ps1` against the throwaway `nexus-loadtest`
project turned up. The evidence round was scoped to "make the numbers reproducible and change no
product behaviour"; it found a real defect and deliberately left it open, with two candidate
fixes. This round takes one of them, and corrects two claims the evidence round got wrong.

Status: implemented in the working tree on 2026-09-23. The full suite is green at
`412 tests / 0 failures / 0 errors / 0 skipped`, and the like-count fix is pinned both by the
existing Mockito tests and by a new H2 integration test that drives the real mapper.

## L1 - A fully unliked post kept its old like_count forever

This is the defect B2 found. `benchmark/concurrency/like-concurrency.ps1` liked a post from 50
threads and then unliked it from 50 threads. The like direction converged (50/50/50). The unlike
direction did not: HTTP was 200 x 50, the Redis set was empty, `vibe_post_like` was empty, and
`vibe_post.like_count` stayed at 50. Both tasks that could have fixed it declined, by design —
`LikeSyncTask` refused to write zero while anything still said otherwise, and `DriftReconcileTask`
preserved the count "for manual recovery".

**Why the guard existed, and why it is no longer needed.** The guard was written when the durable
membership table could be empty for a post that really had likes: "both sources are empty" was the
only available signal, and it could not tell a legitimate unlike-to-zero from a set that had been
lost. That ambiguity is gone. `LikeCounterService.persistMembership` mirrors every toggle into
`vibe_post_like`, and it runs *after* the Lua script has already written the Redis set — so a
failed table write leaves the member in Redis, not in the both-empty state the guard was watching.
An empty table now means "nobody likes this" rather than "we do not know".

**The fix.** The durable table is the authority for membership; `like_count` follows it.

- `LikeSyncTask`, on an empty Redis set, reads `countPostLikes`. Rows still present are replayed
  back into Redis and their count is written (a lagging delete, or a set lost between the toggle
  and the flush); an empty table writes zero. Only a toggle puts a post into the dirty set, so an
  empty set seen there is an unlike, not a lost set.
- `DriftReconcileTask`, when both sources are empty and the stored count is positive, writes zero
  instead of preserving. The union logic above it is unchanged: it still never deletes a
  membership row, and it still rebuilds a lost set from the table and a missing table from the set.

**Acceptance:** `pwsh -File benchmark/concurrency/like-concurrency.ps1` converges in both
directions. Locally that script still needs a rebuilt image (`docker compose build` cannot fetch
its mirrors on this machine), so the assertion is covered here by `LikeCountConvergenceTest` — a
`@SpringBootTest` on H2 with the real mapper, asserting the column goes 50 -> 0 and 50 -> 3 — plus
the updated branch tests in `LikeSyncTaskTest` and `DriftReconcileTaskTest`. That the script's own
expectation did not change is the point: it was right and the code was wrong.

**What this trades away, stated plainly.** A post whose likes existed only in Redis *and* whose set
is lost *and* whose table was never populated now converges to zero rather than being preserved.
That requires the table write to have failed for every like on the post and the set to be lost
afterwards; the first is logged at ERROR by `persistMembership`, and the hourly drift pass
bootstraps the table from Redis long before the second can matter. Preserving instead cost a
permanently wrong `like_count` on every unlike-to-zero — the column the hot ranking sorts on — and
that is the worse of the two failures.

## L2 - A saturated pool aborted the whole reconcile batch

Found by the 2026-09-23 re-run, not by a test. With the agent pool at 4/4 threads and 50 queued,
`AiReviewReconcileTask` logged:

```
ERROR [scheduling-2] o.s.s.s.TaskUtils$LoggingErrorHandler : Unexpected error occurred in scheduled task
org.springframework.core.task.TaskRejectedException: ExecutorService in active state did not accept task:
  ...AsyncExecutionInterceptor$$Lambda...
  rejected from ...ThreadPoolTaskExecutor$1@...[Running, pool size = 4, active threads = 4, queued tasks = 50]
```

The re-trigger loop publishes inside the loop with no handling, so one rejection throws out of the
whole cycle and every post after it waits a full five minutes. `AsyncConfig` already claims
"saturation degrades via the rejection handling added at the publish sites"; this is the site that
did not have it.

**Fix:** `publishSafely` wraps both publish calls, logs which post was deferred, and lets the loop
continue. The post is retried next cycle either way, so the failure is swallowed rather than
rethrown. Note the exception *type*: `ThreadPoolTaskExecutor` rethrows the JDK
`RejectedExecutionException` wrapped in `TaskRejectedException`, and on Spring 6.1 the latter
**extends** the former — so a plain `catch (RejectedExecutionException)` does catch it. The earlier
suspicion that the publisher's catch was missing the wrap was wrong, and it is recorded here so
nobody re-opens it: the live run proved the publisher handles it (thousands of rejections, all 200).

**Acceptance:** `AiReviewReconcileTaskTest` still green, and the failure mode is gone from the
re-run's log. A dedicated test is not written because the behaviour under test is "do not rethrow",
which the existing cycle tests already exercise through a mocked publisher.

## L3 - The load-test summary blamed the wrong mechanism for its own 500s

`benchmark/jmeter/results/loadtest-20260921.{json,md}` recorded 13 HTTP 500s on `POST /posts` and
labelled them "the ExecutorService rejection path". That label was an inference, and the
2026-09-23 re-run disproves it: thousands of rejections were logged and **every one answered 200**,
and two shorter runs (2,405 and 17,310 samples) produced 0 x 500. The 13 are unattributed and
unreproduced.

**Fix:** the machine-readable summary's `poolRejects` block became `serverErrors` with a note
recording the withdrawal, the human-readable summary carries the same correction, and
`docs/research/async-pool-loadtest.md` has a dated correction above the table plus a rewritten
key finding. The counts are untouched — only the claim about what they mean.

## L4 - A research note called a shipped change a todo

`docs/research/mysql-ai-sort-index-explain.md` said the `sort=ai` branch still needed
`AND p.ai_reviewed = 1` and marked it "待办，属行为变更需单独提交". `VibePostMapper.selectFilteredPage`
has had that condition since before this round. The note was behind the code, not the code behind
the note. Corrected in place with the date, because a reader who trusts the todo would "fix"
something that is already fixed.

## Acceptance

- `mvn -B test` green at 412 tests; the new `LikeCountConvergenceTest` (2 cases, H2 + real mapper)
  fails against the previous `LikeSyncTask`/`DriftReconcileTask` behaviour.
- `LikeSyncTaskTest` and `DriftReconcileTaskTest` assert the new convergence instead of the old
  refusal, and still assert that no membership row is ever deleted.
- The re-run's scheduled-task error is gone; the re-run itself is described in
  `docs/research/async-pool-loadtest.md`.
- `benchmark/concurrency/like-concurrency.ps1` is unchanged: its expectation was correct all along.

## Files

`src/main/java/com/nexus/campus/task/LikeSyncTask.java`,
`src/main/java/com/nexus/campus/task/DriftReconcileTask.java`,
`src/main/java/com/nexus/campus/task/AiReviewReconcileTask.java`,
`src/test/java/com/nexus/campus/task/LikeSyncTaskTest.java`,
`src/test/java/com/nexus/campus/task/DriftReconcileTaskTest.java`,
`src/test/java/com/nexus/campus/task/LikeCountConvergenceTest.java`,
`benchmark/jmeter/results/loadtest-20260921.json`,
`benchmark/jmeter/results/loadtest-20260921.md`,
`benchmark/jmeter/results/diagnose-20260923.{json,md}`,
`benchmark/jmeter/results/diagnose2-20260923.{json,md}`,
`docs/research/async-pool-loadtest.md`,
`docs/research/mysql-ai-sort-index-explain.md`,
`docs/tickets/evidence-and-gates.md`.

## Rejected

- **A tombstone column on `vibe_post` ("the client did this").** The evidence round called this the
  correct fix; it is the correct fix *if the durable table cannot be trusted*. It can — it is
  written on every toggle, after the Redis write, and a failure is logged — so the marker would add
  a migration, a write on the hot like path, and a second source of truth to resolve an ambiguity
  that no longer exists.
- **Leaving `DriftReconcileTask` preserving while only fixing `LikeSyncTask`.** Tempting as a
  smaller diff, but it leaves two tasks disagreeing about the same state, and a Redis flush between
  an unlike and the flush would still strand the count forever.
- **Rewriting the committed load-test numbers.** They were measured and are not in question; only
  the sentence that explained them was. The correction is dated and sits next to the numbers.
- **Building the app image to re-run B2 end to end.** `docker compose build` cannot reach its
  package mirrors on this machine (AGENTS.md records this); the H2 integration test is the
  strongest evidence available here, and the script is the evidence to re-run where a build works.
