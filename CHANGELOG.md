# Changelog

All notable changes to Nexus-Vibe will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added

- **Contract truthfulness and the product loop (R1–R6 — `docs/tickets/contract-and-product-loop.md`)**: the
  evidence round asked whether the stack's signals can be trusted. This round asked the same question of the
  API itself. 33 controller sites answered `200 OK` while their own envelope said 401, 403, 404 or 400; the
  login page had never once been able to show the server's reason for refusing you; and a phone could not
  reach the message page at all.
  - **One place decides the HTTP status**: `BusinessException(HttpStatus, safeMessage)`, and `ApiResponse.error`
    now accepts *only* an `HttpStatus` — `code` comes from `status.value()`, `traceId` from
    `status.is5xxServerError()`, so a code that disagrees with its status is no longer expressible.
    `GlobalExceptionHandler` returns `ResponseEntity<ApiResponse<Void>>` and is the only status writer. The
    `error(int, String)` overload plus the `unauthorized` / `forbidden` / `notFound` shorthands were deleted in
    the last migration commit, which turns any unmigrated site into a compile error instead of a review finding.
    No `ErrorCode` catalogue: `HttpStatus` absorbs the 27 magic numbers, and the assessment's other argument for
    one — that the frontend branches on message text — measured false (13 uses of `message`, all display
    fallbacks; nothing reads `code`)
  - **Five response VOs** (`CommentVo`, `MessageVo`, `TagVo`, `ChannelVo`, `ProfileVo`), mapped by hand like
    `PostPageVo` with field names and values copied verbatim, replacing entities at nine response points. The
    value is the boundary rather than the fix: four of the five carried no secret. `SysUser.password` is
    `@JsonProperty(access = WRITE_ONLY)` and the three hand-written `setPassword(null)` lines a fourth call site
    could forget are gone. `NoEntityInControllerTest` reads controllers by reflection and fails when an entity
    type reappears in a signature; there is no allowlist
  - **Product-loop metrics**: `metrics/ProductMetrics` counts the four events that *are* the product
    (`user_registered_total`, `post_submitted_total{status}`, `post_audited_total{action}`,
    `comment_submitted_total`); `task/FunnelAggregateTask` publishes two gauges from MySQL,
    `funnel_activation_ratio` (30-day registration cohort, first post inside 7 days) and
    `funnel_active_content_d7_ratio`. Snapshot rather than scrape-time query — same reason as
    `ai_review_pending_posts` — and a boot run before the first daily one, so neither gauge can read a zero that
    means "nobody has looked yet". A numerator that outsteps its denominator (content whose author was deleted,
    since `vibe_post.user_id` is no foreign key) clamps to 1.0 and logs the disagreement. Active means content
    behaviour: a visitor who reads and never posts is invisible, and that is a stated tradeoff, not a gap. New
    panel set `nexus-product-loop` in the existing provider
  - **A frontend test surface that can fail**: vitest + jsdom + Testing Library, `npm run test` sitting between
    lint and build in the CI frontend job, which until then was lint and `tsc` only. 25 tests, 7 of which drive
    real axios against a fake `adapter` so the 401 branch, its single-flight, the replay and the 5xx trace id all
    execute. Two acceptance checks were proven by breaking the thing under test rather than by reading a green
    run: `if (!refreshPromise)` → `if (true)` reddens exactly one test, the concurrent-refresh one, and a
    probe file holding one bare `apiClient.get` makes the call-site scan answer `src/__probe.tsx:4 - no enclosing
    try`. That scan had to be written twice: a `try`/`finally` with no `catch` re-throws, so "is it in a try" was
    the wrong question. Asked correctly: 41 call sites, none unguarded
  - **A bottom tab bar below `lg`** (`MobileTabBar`): Home, Search, Post, Messages, and a fifth that opens a menu
    with profile, Drafts, Settings and logout. `Navbar` has no hamburger, so a logged-in phone user saw a header
    whose message, settings and profile links were `hidden` — navigation existed only as the home channel grid and
    the back button. The plan's second tab was Channels and it became Search: there is no channels index route (the
    grid *is* the home page), and `Cmd+K` is exactly what a phone has no keyboard for

- **A landing-page entry a stranger can actually read (方向 A · A4/A6 — `docs/plans/portfolio-showcase-plan.md`)**:
  the most persuasive artifact here is an LLM review that fails closed, repairs malformed output and writes back a
  score, and it was reachable only by registering and publishing a code block. `GET /api/v1/showcase` now reports
  one already-reviewed post and `ShowcaseEntry` links to it from the home page under Mission Control. Unconfigured
  deployments answer `data: null` and render nothing, so nothing changes for anyone who leaves `SHOWCASE_POST_ID`
  unset.
  - **The review is a recording, not prose that resembles one.** The first cut hand-wrote both the comment and the
    `ai_review_log` row, and shipped a page whose Code Quality, Security and Suggestions sections rendered
    *empty*: the fixture wrote `quality` / `security` / `suggestions`, which parse cleanly and are read by nobody
    (`AiReviewDetailService` reads `codeQuality` / `securityConcerns` / `optimizationSuggestions`). No test
    failed, because the test asserted the fixture's own shape against itself. The snippet was then published once
    through the ordinary publish path against the deployed stack, and the pipeline's comment and log row were
    copied out verbatim into `src/main/resources/showcase/`. Score and severity are derived by parsing that JSON
    rather than declared beside it, so the card, the comment and the log row cannot drift into disagreeing. See
    [ADR-0010](docs/adr/0010-the-showcase-review-is-a-recording.md)
  - **The recording is reproducible, not asserted.** `benchmark/showcase/record-showcase-review.py` publishes the
    snippet, polls for the review, writes both resources and prints the SQL that clears the three seeded rows. It
    is in the repository for the same reason the fixture was not: "copied out verbatim" is a claim, and a claim
    without a reproducible path does not go in
  - **Two defects the review found rather than the tests.** The seeder kept only a boolean from
    `campus.showcase.post-id` and always wrote the shipped default, while the read endpoint queried the configured
    value, so any non-default id seeded one post and advertised another and the entry rendered nothing; the post
    id now drives the write and the comment and log row follow it. And the three inserts were autocommit
    statements, so a failure between the post and its review left a post that the already-present check then
    treated as finished on every later start; they share one transaction now

### Changed

- **Round six, mechanically**: 33 error sites moved to a real status, eight commits by controller group, with the
  semantic corrections kept to a closed list — pinning an existing-but-unpinnable post is 409 not 404 (a missing
  post stays 404), editing or deleting somebody else's content is 403 not 400, `VibePostServiceImpl` splits
  not-found (404) from state conflict (409), and an upload IO failure is a real 500 carrying a trace id.
  `ResponseContractTest`'s `bodyOf()` takes the expected status, and "4xx carries no traceId / 5xx does" is
  asserted in both directions
- `handleBusinessError` no longer quotes `e.getMessage()` back at the client. An unmapped
  `IllegalArgumentException` keeps its 400 and loses its message; an `IllegalStateException` is no longer mapped
  at all, which puts it on the 500 side where the stack is logged and the client hears nothing. The two Chinese
  envelope messages became English, matching their 30 siblings
- The CI frontend job moved to Node 24. It was pinned to 20, where jsdom 30's `engines` refusal surfaced as "6
  errors, no tests" rather than as a skipped suite
- **Connecting the Feishu alert path is documented instead of remembered.** README's Observability section
  gained a 接通飞书告警 walkthrough: create the custom bot, enable signature verification, put the two values in
  `.env`, rebuild with `up -d` (a `restart` never re-reads `.env`), check `forwarding: true`, then fire one
  payload from the `app` container and read Feishu's own `code` rather than the HTTP status. `.env.example`
  now carries the three constraints the bridge cannot work around, and the refusal table names the four codes
  worth recognising (19021 wrong secret or a clock an hour out, 19024 keyword mode, 19022 IP allowlist,
  11232 rate limit). Written because OPS-1 sat blocked on a credential while the procedure lived only in a
  conversation — the code was never the missing part

### Fixed

- **The alert bridge dropped every notification whose value was not zero, and its own log said only
  "502".** Grafana's webhook sends `values` as plain numbers keyed by refId (`{"B": 44.23943...,"C":1}`);
  `render_text` assumed the Alertmanager nesting `{"A": {"value": N}}` and called `.get("value")` on the
  scalar, raising `AttributeError` *before* `send()` was reached. `0` is falsy, so `(0 or {})` fell
  through to "no reading" and only the non-zero half was loud — on 2026-09-17 that meant the scrape
  rule's FIRING (A = 0) was delivered in 462 ms while its three RESOLVED retries (A = 1) each failed in
  under 0.2 ms, every five minutes, with `docker logs nexus-grafana` recording nothing but "webhook
  response status 502". Three surfaces had invented the payload rather than recorded it: the unit
  fixture used the nesting, the test named `test_resolved_state_survives_the_translation` passed
  `alerts: []` and so never reached the line, and the drill's hand-built payload carried no `values`
  field at all. `alert_value()` now accepts both shapes and returns `0` as a reading; the 502 branch
  prints its reason, which the comment above it had always claimed it did; the drill sends
  `values = @{ A = 1 }`. Deployed to the running stack, the next retry of that same RESOLVED
  delivered in 607 ms and the retry loop stopped — the same payload, the same group, 200 instead of
  502. Evidence and the method for the timings:
  [alert-bridge-values-shape-2026-09.md](docs/research/alert-bridge-values-shape-2026-09.md)
- **The Docker smoke gate had never passed once, and its own condition is what hid it.**
  `.github/workflows/maven.yml` passed `-e JWT_SECRET='ci-...0000'`: a redaction-styled placeholder
  committed as a real value. `JwtUtil.init()` base64-decodes that string before `Keys.hmacShaKeyFor`
  sees it, and `-` (0x2d) is not in the base64 alphabet, so the application context died at startup
  (`java.lang.IllegalArgumentException: Illegal base64 character 2d`; reproduced locally with
  `scratch/smoke-repro.ps1`). All six master pushes from `0f7860f` to `70143fc` failed at that step
  and no other, while every PR was green by construction — the step carried
  `if: github.event_name == 'push'`, so the only ref it ran on was already red before anyone looked,
  and four merges inherited it. The secret is now standard base64 decoding to 37 bytes; the step runs
  wherever the docker job runs, which on a PR means the image inputs moved; and
  `.github/workflows/maven.yml` joined the `docker-changes` filter, so a PR that edits only the
  workflow still builds and smokes the image. The two `smokep...word` placeholders beside it were
  harmless — any string is a valid MySQL password — and are gone for the same reason. What this does
  not prove: the smoke covers boot and `/actuator/health` on the `prod` profile with Elasticsearch
  and Ollama absent. It is a liveness check, not a functional test
- **Round six, what was actually on fire**: `POST /api/v1/auth/login` with a wrong password returned
  `200 {"code":401}` on the live deployment, and so did a missing post with `404` — every consumer reading a
  status (nginx logs, `http_server_requests_*`, the 5xx-ratio alert rule, any HTTP client) was reading a number
  that is not the one the envelope holds
- **A reachable information leak on an unauthenticated endpoint**: `AuthController`'s `catch (RuntimeException)`
  wrapped register, so when `insert` wins a race against the pre-check, the MySQL constraint name and SQL
  fragment come back inside `"Registration failed: …"` over the public internet. It is now an explicit
  `BusinessException(CONFLICT)`. The same coarse catch on login and on admin password-reset is gone too
- `SysUserMapper.selectRecentActiveUsers` was H2-only SQL — MySQL has no `DATEADD`, and its `ONLY_FULL_GROUP_BY`
  refuses an ordering on a column the projection does not carry. `GET /api/v1/stats/active-users` is public, so
  the deployment answered 500 there while all 317 tests stayed green. Found while writing R4's integration test,
  which is the only reason it was found
- `Map.of(...).getOrDefault(null, "other")` throws: immutable maps refuse null probes even as keys. That sat one
  line into `recordPostCreated`, on the request path of a post that had already been written
- `AuditPage`, `AgentLogsPage` and `DashboardPage` rendered `(error as Error)?.message`, which after R2 would
  have started printing `Request failed with status code 403` to a user; they read the envelope through
  `serverErrorMessage(error, fallback)` now. `PostCard`'s fork reads `postId` behind a check instead of throwing
  its way into its own `catch`
- Long code blocks were unreadable on a phone: the block sat inside `TerminalWindow`'s `overflow-hidden`, so the
  tail of a line was simply gone. `CodeBlock` carries `overflow-x: auto` (react-syntax-highlighter renders the
  `pre` as a `div` here, which is the node that scrolls), and `touch-action: manipulation` on coarse pointers
  drops the double-tap zoom delay without killing double-tap zoom on text
- **AI review comments rendered as a wall of text.** The comment list used a single `<p>{comment.content}</p>`,
  so every newline collapsed and the one comment this product most wants read printed
  `## AI Code Review **Overall Score**: 3/10 **Severity**: high` as literal text in one paragraph. Comments go
  through the `react-markdown` pipeline the post body already used, behind `CommentBody` so the rendering has a
  seam four tests hold. Found by opening the showcase page as a signed-out visitor, not by a test — which is the
  argument for having built the page

### Security

- **The app container runs as uid 10001**, not root (`appuser`, `--no-create-home`, `nologin`; the jar and
  `/app/uploads` + `/app/logs` are copied/created with the right ownership). 10001 rather than 1000 because uid
  1000 on a real host is usually somebody's account, and a container writing mounted volumes as that uid can pass
  as them. Docker seeds a named volume from the image only while the volume is *empty*, so any machine that ran
  this stack before the change keeps root-owned volumes a non-root JVM cannot write: the one-time `chown` is in
  `docs/plans/pre-deployment-checklist.md`, and the drill's
  `non-root-app-and-the-root-owned-volume-upgrade` step performs it for real, including a negative proof that
  throws if a non-root app *can* write a volume the step just handed to root
- nginx stops depending on luck at startup: `upstream { server app:8080; }` resolved DNS at config load, so a
  cold `up` needed `app` to already exist and nginx recovered by crashing and being restarted. It uses
  `resolver 127.0.0.11 valid=10s` with a variable `proxy_pass` now. The cost is written in the file rather than
  hidden: OSS nginx cannot `keepalive` a per-request-resolved backend, so proxied requests open their own
  connection to Tomcat. `web` deliberately keeps `depends_on: service_started` — `service_healthy` would also
  stop the crash loop, and would additionally take the static site down whenever the API is down
- **An unwritable log directory no longer bricks the container.** The claim this round shipped with was that a
  logback failure to open its file is a warning rather than a crash; measured 2026-09-16, it is a crash — Spring
  Boot escalates any error status recorded while configuring logback into an `IllegalStateException` inside
  `prepareEnvironment`, so the app against a root-owned `app-logs` restart-looped (10 restarts in four minutes,
  `/actuator/health` never answered, whole site down) instead of degrading. The entry point now probes the
  directory before the JVM starts: writable, it uses it; not writable, it falls back to `/tmp/nexus-logs` and
  says so on stderr with the checklist command in the message. Degraded-but-serving, per ADR-0007, with durability
  as the stated cost; the one-time `chown` moved from "recommended" to "run this before you upgrade"

### Testing

- 331 JUnit cases in 53 classes (307/46 when the round started, 310 after R1, 317 after R2, 328 after R4),
  **25 frontend tests in 6 files where the count was zero**, 17 alert-bridge Python tests unchanged. The new
  backend cases are the two contract scans, the error-semantics probes — `ErrorSemanticsTest` uses strings copied
  from real failures, a duplicate-key MySQL message and an upstream LLM error body, so the assertion is about the
  leak rather than the class name — the funnel aggregate run on H2 so a typo cannot survive as "the unit test
  stubs the mapper", and the four product counters rendered through a real `PrometheusMeterRegistry`
- Baseline discipline, written down because it was gotten wrong first: the round's opening number was read as
  309/48 by summing `target/surefire-reports/*.txt`, and that is not a count — the directory keeps reports from
  earlier filtered runs. The `mvn test` summary line is the only trustworthy source
- **A fourth dialect needed its own test.** H2 versus MySQL caught the `DATEADD`, and jsdom caught nothing about
  CSS; this one is the Micrometer-name versus Prometheus-name layer, and the drill — which reads the scrape, not
  the registry — is what found it: `post.created` and `comment.created` published as `post_total` and
  `comment_total`, because the Prometheus naming convention reserves a trailing `created` token. Four green unit
  tests, an absent metric, and a Grafana panel querying a series that never existed. `ProductMetricsTest` cannot
  see this at all: `SimpleMeterRegistry` keeps meter names verbatim. `ProductMetricsPrometheusNamesTest` is the
  missing assertion, and the drill's presence check stopped reading `-not 0` as "absent", so a meter exported at
  zero and a meter that never appeared are now two different failures with two different messages
- jsdom applies no media queries, so R6's layout claims were checked in a real browser at 390x844 instead: bar
  height, the footer clearing the bar at the bottom of the scroll (763.75 against 789), the menu not overlapping,
  and a 400-character line actually scrolling in the code box. Desktop at `lg` and above was not re-measured

### Added

- **Evidence credibility round (E1–E10 — `docs/tickets/evidence-credibility.md`)**: the previous round built
  the observability surface; this round attacked whether its signals can be trusted.
  - **The test gate stops being a coin flip**: all four `@Scheduled` jobs used to run inside every
    `@SpringBootTest` (`@EnableScheduling` was declared twice), and `AiReviewReconcileTask`'s
    `0 3/5 * * * ?` fires on the wall clock. A reconcile tick landing outside a stub's window read
    Mockito's default `false` from `LlmHealthCache`, pinned "LLM unhealthy" for five minutes, and the
    next post failed closed into `PENDING_REVIEW` — which is how PR #2 went red on a markdown-only
    commit. `campus.scheduling.enabled` now gates the job beans (off in surefire's `systemPropertyVariables`),
    the duplicate annotation is gone, and a health-cache flip logs at INFO with its TTL
  - **`traceId` finished**: the hourly hot-ranking recalculation is wrapped like the other three jobs,
    `TRUST_FORWARDED_HEADERS` is split in two (nginx strips the inbound `X-Trace-Id` it does not own,
    a reverse proxy still gets to pass one through), and an absent trace id renders as absent rather
    than the literal string `null`
  - **`rules.yaml` that Grafana cannot parse now fails loudly and early**: `noDataState: ALERTING` was
    an unusable spelling, and a provisioned parse error aborts Grafana's start, so the whole
    monitoring profile was a restart loop; `GrafanaAlertProvisioningTest` checks the four accepted
    values and the routing chain in 0.3s, and the drill's `grafana-provisioning-loaded` step reads
    them back from the engine API. The six `errorState` keys are gone — provisioning has two state
    knobs, and a third parsed, was dropped, and let a comment claim a policy that never existed
  - **Alerts cannot go missing quietly**: `noDataState` is per rule instead of a blanket `OK`
    (blindness on the gauge and scrapability rules, `OK` on the volume-guarded ratios), plus a
    `nexus-prometheus-scrape-failed` rule that pages 3–4 minutes after the app stops being scraped,
    and `alert-bridge` refuses to start when there is nowhere to deliver — its own comment said
    "answers 502 when unconfigured", which is not what it does
  - **Two audiences, two health documents**: nginx's public `/actuator/health` now proxies the
    `servable` group (storage only), the container healthcheck keeps the aggregate, and
    `/actuator/metrics` came back to dev while prod keeps its exact allowlist — that loss had been
    pinned in a test, so `ActuatorExposureContractTest` now asserts the prod shape only
  - **A release with a name**: `app`/`web` carry `image: nexus-vibe-{app,web}:${APP_TAG:?}`,
    `mem_limit` per container sized from measured usage (the compose file set no memory ceiling at
    all on a 7.65 GiB Docker VM shared with two other projects), and CI uploads the jar and `dist`
  - **A backup that has now been restored**: `scripts/backup.ps1` (mysqldump + uploads with
    SHA-256 and end-marker verification, retention, optional alert-bridge notification) and
    `docs/runbook/restore.md`. `es-data` is in no dump, so the runbook now carries the reindex as a
    restore step (E9); a restored site that skips it serves an empty search index with a `200`. The
    rehearsal ran on 2026-09-14
    and passed every assertion (dump sha256 against the manifest, load exit 0, all ten table row
    counts equal, Chinese titles legible, restored app serving the restored upload at `200` with a
    matching hash); it also found two commands that did not work as written — `docker compose ls`
    rejects the Go template `backup.ps1` asked it for, and `-p` does not scope `container_name:`, so
    the scratch project collided with the live `nexus-db` until
    `docs/runbook/docker-compose.restore-test.yml` renamed its containers. What stays unproven: the
    "second volume" is a partition of the same physical NVMe, so there is still no off-box copy
  - **E9 - the reindex stopped grading its own homework**: this round's own docs claimed "there is no
    bulk reindex path anywhere in the code", which was false when written — `POST
    /api/v1/admin/search/reindex` and `PostSearchService.rebuildIndex` shipped in `9c4b002`
    (2026-08-14) with tests. The real defect was the number: `rebuildIndex` returned the size of the
    row list it had read from MySQL and the endpoint published it as `reindexed`, so a cluster that
    rejected every document — or was not running — reported `reindexed: 32`. `bulkIndex` now returns
    `BulkResult(submitted, indexed, failed)` counted from the `_bulk` body's per-item 2xx statuses
    (an unreadable body counts as zero, the same fail-closed stance as ADR-0004), `createIndexIfNotExists`
    reports whether the index is really ready so a rebuild cannot bulk into an auto-created index with
    the wrong analyzer, the call asks for `refresh=true`, and its timeout went 10s → 60s because a
    whole-site reindex is one request. 9 new unit tests plus a controller test that reads the body
    instead of asserting `notNullValue()`; 305 JUnit cases green.
  - **E10 - the dashboard had never been looked at**: every Grafana assertion in the previous two rounds
    went through an API (rules loaded, contact point registered, metric names present in the scrape), and
    not one opened a panel. Doing that found `Latency p50 / p95 / p99` querying
    `http_server_requests_seconds_bucket` — a series Spring Boot does not publish for an auto-configured
    Timer unless `percentiles-histogram` is enabled, and no `distribution` config existed anywhere. So the
    panel had rendered nothing, on every deployment, since it was committed (measured: 12 `_count` series,
    0 `_bucket`). Sweeping all 24 expressions found 9 returning no series: 3 from the missing buckets,
    4 where the honest answer is a plain zero (5xx ratio, rate-limit rejections, lease attempts
    exhausted, reconcile repairs) and are now drawn with `or vector(0)`. On the two ratio panels the
    guard sits on the numerator only, so "there was no traffic at all" still renders `No data` instead
    of a fabricated "0% success" — which is why `LLM success rate` is one of the two panels left empty.
    `application.yml` gains the histogram plus explicit SLO buckets (`50ms` … `30s`) rather than
    Micrometer's ~70-bucket default,
    pinned by `HttpMetricsHistogramContractTest` (2 cases, verified to fail when the flag is flipped);
    `check_panels.py` and `render_panels.py` keep the two layers honest — expression answers a query,
    browser paints a canvas. Post-fix on one scratch prod stack: sweep `15/9` → `22/2 empty`,
    0 → 385 bucket lines, Overview 9/9 canvases with 0 "No data", AI Pipeline 5/5 with the 2 expected,
    `console_errors=0`; and the first thing the working latency panel revealed is that the slowest
    endpoint is `/actuator/health` itself (max `3.16s`), which the container healthcheck calls against a
    10s timeout every 30s. 307 JUnit cases green, and the 21-step drill re-run with the change in the
    image (`drill-20260914-093857`, 21/21) — whose rollback step swapped through the histogram build itself.

- **Observability round (P0-1..P0-6, P1-1, P1-3 — `docs/tickets/production-readiness.md`)**
  - **Structured log to disk**: `logback-spring.xml` gives prod a `LogstashEncoder` file appender on the
    `app-logs` volume (size+time rolling, 100MB/7d/1GB cap) wrapped in an `AsyncAppender` with
    `discardingThreshold=0` so nothing is dropped; dev/console keeps `%X{traceId}` in the pattern;
    `logstash-logback-encoder` moved 7.4 → 8.0 to match logback 1.5.11
  - **Prometheus endpoint + opt-in monitoring stack**: `micrometer-registry-prometheus`, prod exposes
    `health,info,prometheus`; `docker/observability/` ships Prometheus, Grafana (datasource, 6 alert
    rules, 2 dashboards) and a Feishu `alert-bridge`, all under the `monitoring` profile — no host
    port mapping, so the public surface stays nginx-only and the stack runs unchanged without the
    profile
  - **Business metrics instrumented by hand** (no `@Timed` AOP): `llm_chat_completions_total{outcome}`,
    `llm_chat_completion_duration_seconds` (SLO buckets on the timeout ladder),
    `llm_circuit_breaker_open`, `rate_limit_rejected_total{path}`, `ai_review_pending_posts` (a snapshot
    gauge, so a scrape never turns into a `COUNT` on the database), `ai_review_reconcile_repairs_total`,
    `ai_review_lease_attempts_exhausted_total`
  - **Trace ID across every boundary**: `TraceIdFilter` at
    `HIGHEST_PRECEDENCE - 1` mints a 16-hex id and echoes `X-Trace-Id`; `MdcCopyingTaskDecorator`
    carries MDC into both async pools; `TraceIds.runAsJob` gives the AI-review reconcile, like-sync,
    and drift sweeps their own id per run (E2 added the hourly hot-ranking recalculation);
    the same helper wraps the fourth job, and `PostRankingJobTraceTest` keeps it from drifting back;
    5xx bodies repeat the traceId (`@JsonInclude(NON_NULL)`, normal responses unchanged) and the error
    toast shows the first 8 characters so a user can quote it
  - **Bootstrap admin, empty production seed (ADR-0008)**: prod no longer inserts demo accounts —
    `DataPreloader` keeps only the functional `AiAgent(999)`; `BootstrapAdminInitializer` creates
    `admin` from `BOOTSTRAP_ADMIN_PASSWORD` once when no ADMIN exists; demo posts/comments/messages move
    to `DemoContentSeeder` behind `DEMO_SEED_ENABLED`, so a first boot cannot produce content owned by
    nobody

### Changed

- **Two-level health semantics (ADR-0007)**: a degraded dependency is no longer an unhealthy container.
  Boot's `redis`/`elasticsearch` indicators are switched off and replaced with ours that map to
  `DEGRADED` (plus a new `llm` one reusing `LlmHealthCache`, so probing never feeds the breaker);
  `degraded` ranks above `up` but maps to HTTP **200**, only `db` can still go DOWN, details move to
  the `deps` group with `show-details: always`
  (E6 split the two questions by group: the public URL answers `servable`, the container healthcheck
  keeps the aggregate; ADR-0007 carries the amendment, including the fact that its first version
  offered one endpoint to two audiences)
- **`SystemMetricsAutoConfiguration` exclude removed** — the original crash came from the CI profile's
  `-XX:-UseContainerSupport`, not from cgroup v2; OS metrics now verified present in a prod container,
  and unit tests assert only `jvm_*` so no runner crash returns. E7 made that profile explicit rather
  than OS-inferred (`-P surefire-without-container-support` in CI, because an implicit
  `<os><family>unix</family>` activation is invisible in a build log) and moved the gate to JDK 21,
  the LTS the Dockerfile actually runs — it used to compile and test on 18, which nothing else uses
- **Per-container memory ceilings**: `mem_limit` on all six always-on services, sized from what the
  running stack holds (app 400MiB of its 768MiB, es 653MiB); the three `monitoring`-profile
  containers still have none. This is a capacity decision rather than a disk-hygiene one: the only
  bound before it was the 7.65 GiB Docker Desktop VM, shared with two unrelated projects' live
  containers, so the limit is about one stack starving another rather than about filling a disk.
- **Frontend response contract matched to the backend**: `ApiResponse<T>` is now
  `{ code, message?, data }` with success derived from `code`; the never-read `success` field is gone
  from the type rather than bolted onto the API
- **nginx actuator lockdown**: `location /actuator/ { return 404; }` before the SPA fallback, keeping the
  single exact `/actuator/health` forward — "accidentally safe" becomes explicitly denied

### Fixed

- **Compose log rotation on every service**: all services now share a `json-file` anchor with
  `max-size: 10m` / `max-file: 3`; container stdout was previously unbounded on the host disk
- **`migrate-0005` told its reader to load into a database that does not exist** (`nexus_vibe`; the real
  name is `nexus_campus`, which `0006` and `0007` already use). The numbering starting at `0005` is now
  written down too: `0001`–`0004` have never existed in any commit, and all three migrations are already
  inside `init.sql`, so replaying them on a fresh volume is a `Duplicate column name` error — which is
  what a 3am restore would otherwise have turned into

### Documentation

- Failure drill `benchmark/observability/drill.ps1` (21 steps, real container stack, dead-port LLM
  mock, and a signature-verifying webhook sink standing in for Feishu) with results and — equally —
  its own false-red and false-green history in
  `docs/research/observability-drill-2026-09.md`; module/completion audit in
  `docs/research/project-module-audit-2026-09.md`; the readiness assessment archived to
  `docs/research/production-readiness-assessment-2026-09.md`; `CONTEXT.md` gains **Degraded**,
  **Servable**, **Bootstrap Admin**, **Trace ID**

### Testing

- 307 JUnit cases in 46 classes (was 243 before this round, 284 after the observability tickets, 296
  after the evidence round's first eight, 305 after E9): first tests to touch actuator at all, plus health
  semantics, trace propagation across the async hop, log JSON shape, seed gating, bootstrap
  idempotency, the scheduling gate, the Grafana alert file parsed the way the engine parses it, what a
  `_bulk` response actually confirms, and the single YAML key a latency panel silently depends on; the
  alert-bridge ships 17 Python tests pinning the Feishu signature and the body it is computed over
- Not yet proven, stated plainly: an alert arriving in a **real** Feishu group (the drill delivers to a
  signature-verifying stand-in), an alert rule actually reaching `firing` (each one needs 5m/15m of the
  condition holding, longer than the drill window), an A→B→A rollback driven through the real
  public entry point, and a backup copy that survives the death of its host disk — see the drill
  report's honest-list section and `docs/runbook/restore.md` section 9
- Panel rendering was on that list until E10 rendered it (9/9 and 5/5 canvases painted, screenshots in
  `docs/research/observability-drill-2026-09.md` section 9); what that check still does not do is say
  whether the numbers on the panels are *correct*, only that they arrive

### Security

Audit-driven hardening of the AI pipeline (ADR-0003/0004/0005 review + product walkthrough, 2026-09-06):

- **Fail-closed without backdoors**: the safety listener's outer catch now fails closed (was a silent
  fail-open path leaving posts public with no reconciliation marker); the publish path gates on a
  shared cached LLM health verdict, so posts never sit publicly visible during an LLM outage
  (ADR-0004); `failClosed` writes the pending-llm marker before flipping post status
- **Prompt-injection isolation**: per-request nonce delimiters + inline neutralization of
  delimiter-like lines; post title/context excerpt moved inside their own META region
- **Lease correctness**: expiry judged by the DB clock (`NOW()`) instead of the caller's clock;
  lease raised 30s → 240s to exceed the LLM worst case and stop mid-review re-dispatch; the
  reconcile task retires budget-exhausted REVIEWING posts that a worker crash left unclaimable
- **Defense in depth**: AI review comments are sanitized through a shared safelist
  (`ContentSanitizer`, also used by the request XSS filter) since they bypass it

### Fixed

Product walkthrough findings (three-persona full-journey report, `docs/reviews/`):

- **P0**: re-reviewing a post no longer shows the stale score in the review terminal — the post
  poll reaching `aiReviewed=1` invalidates the 5-minute review-detail cache
- **Like state**: posts now carry `likedByMe` (Redis SISMEMBER, best-effort identity on public
  GETs) so the highlight survives reloads; the falsy-count fallback is gone and the server's
  `currentLikes` is the single truth with an in-flight lock against double toggles
- **Unsaved content**: create/edit pages warn on `beforeunload` once content drifts from the
  restored baseline; edit-page Cancel confirms before discarding
- **Smaller UX**: `aiReviewed=3` renders a quiet "review failed, retrying" notice; liking while
  logged out routes to login instead of swallowing the 401; a collapsible Review History panel
  (public `GET /agent-logs/post/{id}`) keeps earlier scores visible after a re-review; the
  create-page Code button inserts triple backticks; validation errors persist until the next submit

### Changed

- **Review lease + attempt budget (ADR-0005)**: AI review attempts claim the post with an atomic
  conditional UPDATE (review_lock_until/owner/attempts) — no double-processing across instances or
  re-published events; after 5 failed attempts the author is notified once and re-dispatch stops
- **Agent pool isolation**: a dedicated agentLlmExecutor (core2/max4/queue50) hosts the two
  LLM-bound listeners; long model calls can no longer starve message/notification work on
  nexus-async
- **Like-count drift reconciliation**: hourly rotating-cursor sweep detects Redis-LOSS-shaped gaps
  (DB far above the Redis set) and rebuilds from vibe_post_like, including the hot-ranking ZSET;
  LikeSyncTask now consumes the dirty set via SPOP batches (100) with requeue-on-failure
- **LLM JSON repair + self-correction**: raw structured output is repair-parsed (fences, trailing
  commas, max-token truncation via bracket-stack completion) before validation; unparseable output
  retries once with the model's own broken output plus the parser error (temperature 0.1)

### Added

- Deep-pagination study: late row lookup measured as a REGRESSION on MySQL 8 with a covering
  index (optimizer already does index-ordered top-N) — rewrite reverted, measurements and
  benchmark scripts kept in docs/research/late-row-lookup-deep-pagination.md

### Changed

- **AI review explainability**
  - The review prompt now anchors scoring with an explicit 0-10 rubric (consistency across reviews)
  - AI review comments end with the score guide; the post-page score badge shows it on hover
- **Re-review supersede**: editing a post hides the previous AI review comment (status=0) so the
  thread never shows contradictory scores; full history remains in ai_review_log / agent logs
- **Pool-saturation degradation**: agent events rejected by a saturated async pool no longer fail
  the post request with a 500 — review posts land in FAILED(3) for the reconciliation task, and
  safety checks fail closed (PENDING_REVIEW + pending-llm marker) exactly like an LLM outage

### Added

- **Account Recovery (P0)**
  - Registration now collects a unique email as the recovery anchor (frontend + backend validation)
  - Admin endpoint `POST /api/v1/admin/users/reset-password` generates a 12-char temporary password for out-of-band handover
  - "Account Recovery" card on the admin dashboard

- **Author Notifications (P0)**
  - Authors are now notified when: their post is held for safety audit (fail-closed or prompt injection), their AI review fails and is queued for retry, and when an admin approves or rejects their post
  - Spam remains silent by design (no abuser feedback, per ADR-0003)

### Changed

- `/api/demo/*` showcase endpoints are now gated behind `campus.demo.endpoints-enabled` and **off by default** (`DEMO_ENDPOINTS_ENABLED`); previously they were anonymous on any non-prod profile

## [1.0.0-CYBERPUNK] - 2026-07-17

### Added

- **Authentication & User Management**
  - User registration and login with JWT token issuance
  - Role-based access (USER / ADMIN)
  - Personal profile page
  - JWT authentication filter with request-scoped user context

- **Posts & Content**
  - Post creation, detail view, and paginated listing
  - Category-based post browsing
  - Post tagging (many-to-many relationship)
  - Post likes with Redis-backed like counter

- **Comments**
  - Comment creation on posts
  - Paginated comment listing

- **Content Moderation**
  - Two-tier sensitive word detection (sensitive / critical)
  - DFA (Deterministic Finite Automaton) algorithm for O(n) word matching
  - Automatic post blocking for critical-level content
  - Flagging for sensitive-level content requiring admin review
  - Admin audit dashboard (approve / reject workflow)
  - XSS input sanitization filter

- **Search**
  - Elasticsearch 8.x integration for full-text search
  - Post document indexing

- **System & Infrastructure**
  - Spring Boot 3.3.5 with Java 18
  - MyBatis-Plus 3.5.9 ORM
  - H2 in-memory database (dev) / MySQL 8 (production)
  - Redis caching with Lettuce connection pool
  - Multi-profile YAML configuration (default H2 / mysql)
  - Global exception handler with structured JSON error responses
  - AOP-based request logging aspect
  - MyBatis-Plus meta-object auto-fill (createTime, updateTime)
  - DTO layer with Jakarta Validation annotations
  - WAR packaging for standalone or container deployment

- **UI / Views**
  - Cyberpunk-styled JSP views
  - Login / Register / Index / Post Detail / Post Create / Profile / Admin Audit pages
  - Responsive layout

### Documentation

- Initial README with tech stack, architecture, setup guide, API examples
- CHANGELOG established
- Swagger / OpenAPI 3 documentation via SpringDoc

### DevOps

- Docker multi-profile build support
- Docker Compose template (app + MySQL + Redis)
- `.gitignore` for Java/Maven projects
- Maven wrapper compatible

### Infrastructure

- Add project documentation (README, CHANGELOG, Swagger)
- Add `.gitignore` for standard Java/Maven project hygiene
- Configure SpringDoc OpenAPI 3 for REST API documentation
