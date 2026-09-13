# Changelog

All notable changes to Nexus-Vibe will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added

- **Evidence credibility round (E1–E8 — `docs/tickets/evidence-credibility.md`)**: the previous round built
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

- 305 JUnit cases in 45 classes (was 243 before this round, 284 after the observability tickets, 296
  after the evidence round's first eight): first tests to touch actuator at all, plus health
  semantics, trace propagation across the async hop, log JSON shape, seed gating, bootstrap
  idempotency, the scheduling gate, the Grafana alert file parsed the way the engine parses it, and
  what a `_bulk` response actually confirms; the alert-bridge ships 17 Python tests pinning the
  Feishu signature and the body it is computed over
- Not yet proven, stated plainly: an alert arriving in a **real** Feishu group (the drill delivers to a
  signature-verifying stand-in), Grafana panel rendering, an A→B→A rollback driven through the real
  public entry point, and a backup copy that survives the death of its host disk — see the drill
  report's honest-list section and `docs/runbook/restore.md` section 9

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

Product walkthrough findings (three-persona full-journey report, `产品体验报告/`):

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
