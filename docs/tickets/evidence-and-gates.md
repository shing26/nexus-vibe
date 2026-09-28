# Evidence and gates - the seven dimensions that were still scored 2

Source: `Nexus-Vibe-短板清单-20260921.md` §④, the scoring pass that left seven dimensions at 2
(B1, B2, B4, B6, B8, B9, B10), cross-checked against `docs/research/project-module-audit-2026-09.md`
and the previous round's `framework-reinforcement.md`. The common thread was not missing
capability - it was missing **evidence**: the numbers that made each claim were hand-typed, ran
once on one machine, or lived only in a review that the repository does not keep. The point of this
round is to make each of the seven produce an artifact a reader can regenerate and a command that
regenerates it.

This is a deliberate reopening of the scope ADR-0009 froze. The argument for it is in the plan's
summary and in `docs/adr/0013-config-validation-moves-to-validated-properties.md`: the deliverable
is a portfolio artifact, and "README gives a path from the address to the evidence" is one of the
four things ADR-0009 said the artifact is. A number a reader cannot reproduce is not that path.

Status: implemented in the working tree on 2026-09-21. The full suite is green at
`410 tests / 0 failures / 0 errors / 0 skipped` from a single `mvn test` summary line, and
`jacoco.line.minimum` enforces 0.70 whole-bundle line coverage on top of it. Three of the seven
items (B6, B2, B8) ship a committed summary under `benchmark/`; the other four are covered by
tests or a gate that run in CI (B1, B4, B9) or by an ADR and a test (B10). One of the three
summaries - B2's - is **red on purpose**, because running it found a real defect that this round
chose not to fix. Both of those facts are recorded below rather than smoothed over.

## B6 - The load-test numbers become a script product

Before this round `docs/research/async-pool-loadtest.md` carried 201,880 samples and 121,202 pool
rejections, all typed by hand from a terminal nobody else could inspect, against a build that no
longer exists.

**Scope:** `benchmark/jmeter/run-loadtest.ps1` drives the existing `ai-review-loadtest.jmx`, runs
it against a throwaway compose project, parses the `.jtl` into
`benchmark/jmeter/results/loadtest-<date>.json|md`, and reads the terminal review states back out
of the database. The `.jmx` was parameterised (`BASE_HOST`, `BASE_PORT`, `ADMIN_USER`,
`ADMIN_PASSWORD`, the five ladder timings) and its four `X-Forwarded-For` headers were changed to
`X-Real-IP` on `10.20.*`, because the old rotation was the 2026-09-17 finding and no longer works.
`benchmark/jmeter/docker-compose.loadtest.yml` is the scratch override: it excludes
`RedisAutoConfiguration` and sets `spring.cache.type=simple` so the rate limiter does not cap
`POST /posts` at 10/min/IP, and it joins the live network so the app can reach Ollama.

**Acceptance:** `pwsh -File benchmark/jmeter/run-loadtest.ps1` exits 0 and writes a summary whose
every number is cited by `async-pool-loadtest.md`. Met for the committed run
`benchmark/jmeter/results/loadtest-20260921.{json,md}`: 25,204 samples, 13 errors (all 500s on
`POST /posts`, the pool-rejection path), p50/p90/p99 37/1770/3353 ms, 6,231 posts left FAILED(3).
The 2026-09-14 table is kept but relabelled as an un-reproducible historical record, and a new
"why the two shapes differ" section explains the three variables that changed (the fixed
XFF-rotation bypass, 7b versus 3b, and an order-of-magnitude difference in synchronous POST
latency).

**Not in CI, on purpose:** a faithful run needs a live stack plus a local Ollama and takes about
six minutes. A six-minute green on a shared runner would test the runner, not the pool.

**Files:** `benchmark/jmeter/run-loadtest.ps1`,
`benchmark/jmeter/docker-compose.loadtest.yml`, `benchmark/jmeter/ai-review-loadtest.jmx`,
`benchmark/jmeter/results/loadtest-20260921.{json,md}`,
`docs/research/async-pool-loadtest.md`, `.gitignore`.

**Rejected:** committing the `.jtl` or the JMeter HTML report. They are megabytes of per-sample
rows and a directory of PNGs; the two small summaries carry every number the doc quotes, and
`.gitignore` now keeps the raw forms out.

## B2 - Concurrency correctness measured against the live stack

**Scope:** `benchmark/concurrency/like-concurrency.ps1` builds one probe post and fifty probe
users, releases fifty threads through a shared `System.Threading.Barrier` to like it, then asserts
four views agree - `HTTP 200` count, `SCARD post:like:<id>`, `COUNT(*) FROM vibe_post_like`, and
`vibe_post.like_count` - and repeats the burst as unlikes to assert the baseline returns. It mints
its own HS256 tokens from `JWT_SECRET` and cleans its fixtures up in a `finally`.

**Acceptance:** exit 0 and a summary where the four views agree in both directions. The committed
run `benchmark/concurrency/results/like-concurrency-20260921.{json,md}` **fails the second half**,
and the script exits 1 because of it - it does not adjust the assertion to pass.

**What the failure is.** The like direction converges: 50/50/50, and `like_count` reaches 50 after
35s. The unlike direction does not. HTTP is 200×50, `SCARD` is 0, `vibe_post_like` is 0 - and
`vibe_post.like_count` stays at 50 forever. Two scheduled tasks each decline to correct it, by
design: `LikeSyncTask` logs `[LIKE-SYNC] Refusing to write zero for post <id>: Redis=0, durable=0,
stored=50`, and `DriftReconcileTask` logs `[DRIFT] Post <id> has no recoverable members but
like_count=50; preserving the count for manual recovery`. The API reads the Redis set and serves
0 correctly, so this is a denormalised-column defect, not a read-path one. The guard is not a
mistake - it exists because the membership table was once never written, and "both membership
sources are empty" is the only signal that could tell a lost set from a legitimate empty one. The
defect is that a real unlike-to-zero looks exactly the same.

**Not fixed here, and the two candidate fixes, both recorded so the next reader starts ahead:**

1. Give the two sources a third, durable "the client did this" signal - e.g. have the unlike path
   write a tombstone (or a per-post `last_mutation` marker) that `LikeSyncTask` treats as
   authoritative, so an empty set with a fresh tombstone is a real zero and an empty set with no
   tombstone is a lost set. This is the correct fix; it is a schema change plus a write on the hot
   like path, which is why it is a ticket and not a line in this round.
2. Let `DriftReconcileTask` trust the durable table when the Redis set is missing entirely and the
   table is present-but-empty, and only preserve the count when the table itself is unreachable.
   Cheaper, but it re-opens the exact hole the guard was added for: if the table write silently
   stops, every post reads as "legitimately zero".

Neither was taken because this round is scoped to evidence, and a fix here changes like-count
semantics under a live deployment - the kind of change that wants its own ADR and its own
concurrency evidence, not a drive-by.

**Resolved 2026-09-23** in
[like-count-convergence.md](like-count-convergence.md) (L1): the durable membership table is the
authority, `LikeSyncTask` replays it into Redis and writes its count, and `DriftReconcileTask`
converges both-empty-plus-a-positive-count to zero. Neither candidate fix below is the one that was
taken; the ambiguity they both exist to resolve turned out not to be real, because
`persistMembership` runs after the Redis write on every toggle.

**Not in CI, on purpose:** it needs a running MySQL + Redis + app and about six minutes of cron
waits. What *is* in CI is B1, which covers the same mechanisms without the wall-clock dependency.

**Files:** `benchmark/concurrency/like-concurrency.ps1`,
`benchmark/concurrency/docker-compose.concurrency.yml`,
`benchmark/concurrency/results/like-concurrency-20260921.{json,md}`, `.gitignore`.

## B1 - N-to-1 duplicate writes, in CI

**Scope:** `ConcurrentDuplicateWriteTest` (`@SpringBootTest` + H2) starts two races with a
`CountDownLatch`. 32 threads write the same `(post,user)` like row; 16 threads register the same
username.

**Acceptance:** the like race leaves **exactly one** row and never more than one, with zero
exceptions (`INSERT IGNORE` + `uk_post_user`); the register race yields exactly one success, the
other fifteen a 409, and exactly one `sys_user` row (`DuplicateKeyException` rolls the losers
back). Met, in the full suite.

**Stated limit:** this runs on **H2, not MySQL**. The mechanism under test - a unique constraint
plus an ignore-or-rollback insert - is the same, but the storage engine's exact behaviour under
contention is not, so this is evidence for the code path and not a claim about InnoDB.

**Files:** `src/test/java/com/nexus/campus/service/ConcurrentDuplicateWriteTest.java`.

## B4 - A cross-user access matrix, in CI

**Scope:** `CrossUserAccessTest` has user B's token hit user A's resources: `PUT`/`DELETE` a post,
`DELETE` a comment, `read`/`DELETE` a message, and `DELETE` an uploaded file if such a surface
exists. Each endpoint keeps **its own** existing semantics.

**Acceptance:** every endpoint has at least one case, and the row is asserted untouched where the
semantics are 403. Met - six tests: post `PUT`/`DELETE` 403 (row unchanged), comment `DELETE` 403,
message `read` 404 with the message still unread, a control that the owner's own `PUT` succeeds,
and `DELETE /api/v1/upload/image` returning 405 because no upload-delete surface exists (the test
records the absence instead of inventing a route to test). Admin's vertical surface keeps its
existing assertions in the integration tests; this file does not restate them.

**Files:** `src/test/java/com/nexus/campus/controller/CrossUserAccessTest.java`.

## B9 - One global coverage floor, and the trap that would make it a lie

**Scope:** `jacoco-maven-plugin` in `pom.xml` with `prepare-agent`, `report` and a `check` on the
**BUNDLE** `LINE` `COVEREDRATIO`, with the minimum in `jacoco.line.minimum`. The backend CI job
uploads `target/site/jacoco/` on every run.

**Acceptance:** the first measured run is 2967/3995 = **74.27%** line coverage
(`target/site/jacoco/jacoco.xml`), so the floor is that value floored to the nearest 5%, **0.70**.
`mvn -B test` passes at 0.70, and it was proven to bite: raising the floor to 0.75 against the same
exec data fails with `lines covered ratio is 0.74, but expected minimum is 0.75`. The gate is not
decoration.

**The trap, recorded because it makes the number lie.** `jacoco:prepare-agent` writes the agent
arguments into the `argLine` property and the surefire `<argLine>` reads it back with `${argLine}`.
Passing `-DargLine="..."` on the command line **replaces** that value instead of appending, so the
agent never loads and coverage silently measures 0.00 - which the new floor would then report as a
failure on a number that was never real. The `argLine` property is declared empty in `pom.xml` so
the expression still resolves under `-Djacoco.skip=true`; the memory-limited local run must pair
its `-DargLine` override with `-Djacoco.skip=true`. README and `AGENTS.md` both say so.

**Files:** `pom.xml`, `.github/workflows/maven.yml`, `README.md`, `AGENTS.md`.

**Rejected:** per-package thresholds. The failure this exists to catch is "the suite grew and
nothing exercises the new code", which a per-class number cannot see and which one honest bundle
ratio reports.

## B8 - Migration rollback, rehearsed

**Scope:** three down scripts under `docker/mysql/` and
`benchmark/migrations/rehearse-migrations.ps1`, which builds a throwaway project, runs `init.sql`,
rolls the three migrations back, applies them again, rolls back, and applies again - asserting the
schema shape from `information_schema` at every step.

**Acceptance:** `pwsh -File benchmark/migrations/rehearse-migrations.ps1` exits 0 with an
up→down→up log where each step's assertion passes. Met -
`benchmark/migrations/results/migrations-20260921.{json,md}` shows five PASS steps ending in
`Overall: PASS`.

**Lossy versus lossless, stated in each script's own header:**

| Script | Effect |
|---|---|
| `rollback-0006-drop-ai-sort-index.sql` | lossless - drops an index, which covers data but does not hold it |
| `rollback-0005-drop-user-email.sql` | **lossy** - destroys every stored address; nothing else carries it |
| `rollback-0007-drop-review-lease.sql` | **lossy** - destroys the review attempt budget (4-of-5 becomes a fresh post) |

The two lossy scripts open with a `LOSSY ROLLBACK - DESTROYS DATA` header, and
`docs/runbook/restore.md` §4.1 makes a backup the precondition for running either.

**Stated limits:** the data loss is described, not measured - the rehearsal runs on an empty
schema. It does not prove the application still starts against the rewound schema (no pre-lease
build is booted here), and it is not a migration ledger: there is still no schema-version table,
so DB-1 in `docs/tickets/next-cycle-backlog.md` stays deferred.

**Not in CI, on purpose:** it needs this machine's Docker daemon and a MySQL container.

**Files:** `docker/mysql/rollback-0005-drop-user-email.sql`,
`docker/mysql/rollback-0006-drop-ai-sort-index.sql`,
`docker/mysql/rollback-0007-drop-review-lease.sql`,
`benchmark/migrations/rehearse-migrations.ps1`,
`benchmark/migrations/docker-compose.migrations.yml`,
`benchmark/migrations/results/migrations-20260921.{json,md}`, `docs/runbook/restore.md`,
`.gitignore`.

## B10 - Configuration validation moves to `@Validated`

**Scope:** `CampusAiProperties` (`@ConfigurationProperties("campus.ai")` + `@Validated`, nested
groups, `@NotBlank`/`@Pattern`/`@Positive` and an `@AssertTrue` duration rule), the eight `@Value`
consumers switched to inject the object, `AiConfigValidator` and its test deleted, and a new
`CampusAiPropertiesTest` asserting the equivalent bind failures.

**Acceptance:** `mvn -B compile` passes; `rg "campus\.ai\." src/main/java` returns only
`CampusAiProperties` and javadoc; a bad value (`campus.ai.llm.timeout=0s`) fails startup with the
property name in the message; and the bind-failure test asserts "every violation in a group is
reported at once". Met, with the loss below recorded in `CampusAiPropertiesTest` and ADR-0013.

**The guarantee that shrank, stated so it is not over-read:** the old
`BeanFactoryPostProcessor` collected every violation across the whole tree and reported them in
one message. Spring Boot 3.3 validates each `@Valid` nested group as it finishes binding and
throws on the first group that fails, so "startup names the property" survives but "startup names
*all* the properties" becomes "startup names all the violations in the group that failed".
`CampusAiPropertiesTest` pins both halves. Also, `@DurationMin` does not exist in Boot 3.3.5 (it
arrived in 3.4), so the duration rule is an `@AssertTrue` method that still names the property.

**Files:** `src/main/java/com/nexus/campus/config/CampusAiProperties.java`, the eight consumers,
`src/test/java/com/nexus/campus/config/CampusAiPropertiesTest.java`,
`src/main/java/com/nexus/campus/NexusCampusApplication.java`,
`docs/adr/0013-config-validation-moves-to-validated-properties.md`,
`docs/plans/environment-matrix.md`, `CONTEXT.md`.

## Not necessary, with the reason

| Item | Why it is not a required gap |
|---|---|
| Wrapping `agent/` in an interface | The plan names this explicitly as out of scope. The pipeline is already pluggable at two seams (ADR-0012); a wrapper around the rest would be structure without a caller. |
| Flyway / Liquibase | Still deferred as DB-1. It lands together with a tested restore and a schema-version table; B8 proves the *manual* rollback path, which is what the deployment actually uses. |
| Testcontainers | The H2 suites and the local scripts already cover the mechanisms; a container-per-test suite would trade the six-minute local scripts for a CI job that needs Docker-in-Docker, for the same evidence. |
| Changing the thread-pool behaviour | B6 is measurement, not tuning. The pool's "saturate then drop" shape is a deliberate fail-closed choice (ADR-0004), and the improvements listed in the load-test doc are behaviour changes that want their own round. |
| Per-package coverage thresholds | See B9's rejected section. |

## Rejected

- **Adjusting the B2 assertion so the script exits 0.** The whole point of B2 is to measure the
  live stack; a summary that passes by weakening the check would be exactly the "evidence that is
  not evidence" this round exists to remove. The defect is real and is written up instead.
- **Putting the JMeter or migration scripts in CI.** Both need this machine's Docker daemon and
  minutes of wall clock; scheduled in CI they become a green that proves nothing (the same reason
  the full drill stayed manual).
- **Keeping both `AiConfigValidator` and `CampusAiProperties`.** Two judges that can disagree,
  with no test that compares them - the failure mode ADR-0013 is about.
- **A coverage floor picked to look good.** 0.70 is the first measured value floored to 5%, not a
  target. Raising it without a run that clears the next step with margin would just make the gate
  red on unrelated changes.

## Files

See the per-item **Files** lines above. The two new documents this round added are this file and
`docs/adr/0013-config-validation-moves-to-validated-properties.md`; `docs/plans/environment-matrix.md`
and the `CONTEXT.md` Release-vs-Migration Rollback entries came from the same grilling pass.
