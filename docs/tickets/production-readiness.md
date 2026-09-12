# Production Readiness - Observability Tickets

Source: [production-readiness-assessment-2026-09.md](../research/production-readiness-assessment-2026-09.md),
gap list P0-1..P0-6 plus P1-1 and P1-3. North star: the system can prove it is
serving traffic, and says so before a user finds out.

Scope of this round: P0 in full, traceId, and the contract type fix. Error
codes, `BusinessException`, centralized `@RequiresRole`, non-root containers
and frontend crash reporting are deliberately left for the next round.

## T1 - Log to disk as structured JSON

Status: done on `codex/production-readiness`.

**Scope:** a rebuilt container must not lose the log lines that explain why it
was rebuilt, and a machine must be able to read them.

- `logback-spring.xml` with a console appender in every profile, so
  `docker logs` keeps working.
- Prod adds `RollingFileAppender` + `LogstashEncoder` inside an
  `AsyncAppender` (`queueSize=512`, `discardingThreshold=0`), driven by an
  included fragment so the graph is testable without the prod Spring profile.
- Rotation caps: 100MB per file, 7 days, 1GB total, on `/app/logs`.
- All compose services cap the daemon json-file driver at 10MB x 3.
- `logstash-logback-encoder` 7.4 -> 8.0 to match logback 1.5.11.
- Console pattern reserves a `traceId` column that T6 fills.

**Acceptance:**
- Prod appender graph is a rolling JSON file behind an async wrapper, and a
  rendered line carries the MDC `traceId` and `stack_trace`.
- Rotation and backpressure caps are pinned by a test.
- Unaffected: `docker logs` output, the 243 baseline tests.

**Files:** `src/main/resources/logback-spring.xml`,
`src/main/resources/logback/prod-file-appender.xml`,
`src/test/resources/logback/prod-appenders-under-test.xml`,
`src/test/java/com/nexus/campus/config/LogbackStructuredOutputTest.java`,
`docker-compose.yml`, `pom.xml`.

## T2 - Expose metrics and ship a monitoring stack

**Scope:** scrapeable metrics without opening a port to the public internet.

- `micrometer-registry-prometheus`, prod exposes `health,info,prometheus`.
- Drop the `SystemMetricsAutoConfiguration` exclude so OS and disk metrics
  come back; verify in a prod-shaped container that cgroup detection does not
  crash the JVM the way it did on the CI runner.
- Metric assertions are layered on purpose: unit tests assert `jvm_*`, because
  the `ci-linux` profile runs surefire with `-XX:-UseContainerSupport`, which
  is exactly the condition that made the exclude necessary. OS and disk
  metrics are verified in the container drill instead.
- `docker/observability/`: Prometheus scrape config plus Grafana provisioning
  (datasource, alert rules, Overview and AI Pipeline dashboards).
- Compose services `prometheus`, `grafana`, `alert-bridge` under a
  `monitoring` profile, on the internal network only, no host port mapping.

**Acceptance:**
- `/actuator/prometheus` returns 200 with `jvm_*` and `http_server_requests_*`.
- `docker compose up` without the profile changes nothing; the public side
  stays nginx only.
- Prod container exposes `system_cpu_usage` (drill, not unit test).
- Unaffected: existing actuator health behaviour, baseline tests.

**Files:** `pom.xml`, `src/main/resources/application-prod.yml`,
`src/main/resources/application.yml`, `docker-compose.yml`,
`docker/observability/`,
`src/test/java/com/nexus/campus/controller/ActuatorMetricsTest.java`.

## T3 - Business metrics and Feishu alerting

Depends on T2.

**Scope:** the AI pipeline has to report its own health, and a human has to
hear about it.

- `llm_chat_completions_total{outcome}`,
  `llm_chat_completion_duration_seconds` and `llm_circuit_breaker_open` from
  `LlmClient`, reusing the existing breaker fields.
- `rate_limit_rejected_total{path}` at the interceptor reject branch.
- `ai_review_pending_posts` gauge plus reconcile repair and
  lease-exhaustion counters on the scheduled task.
- Four Grafana alert rules: breaker open, review backlog, 5xx ratio, rate
  limit spike.
- `alert-bridge`: Grafana webhook to Feishu custom bot, signed with
  HMAC-SHA256. Credentials only via `.env`.

**Acceptance:**
- Each metric name appears on `/actuator/prometheus` after the matching code
  path runs.
- The bridge unit test pins the Feishu body and signature for a fixed key and
  timestamp.
- Unaffected: breaker timings, rate limit decisions, review semantics.

**Files:** `src/main/java/com/nexus/campus/agent/LlmClient.java`,
`src/main/java/com/nexus/campus/config/RateLimitInterceptor.java`,
`src/main/java/com/nexus/campus/task/AiReviewReconcileTask.java`,
`docker/observability/grafana/provisioning/alerting/`,
`docker/observability/alert-bridge/`.

## T4 - Two-level health and actuator lockdown

**Scope:** `unhealthy` must mean "cannot serve", and no dependency outage may
get the container killed while it is still serving traffic.

- Disable the starter-provided redis and elasticsearch indicators before
  registering our own that map to `Status.DEGRADED`, plus an `llm` indicator
  reusing `LlmHealthCache`. `db` stays a real DOWN.
- Status order puts `degraded` above `up`, `degraded` maps to HTTP 200,
  details are hidden by default, and a `deps` health group exposes named
  component detail for operators.
- nginx rejects every `/actuator/` path except the exact `/actuator/health`
  allow, turning "accidentally safe" into an explicit deny.
- Decision recorded in ADR-0007.

**Acceptance:**
- With the LLM probe unavailable, `/actuator/health` is 200 with status
  `DEGRADED`, and the container is not judged unhealthy.
- `/actuator/health/deps` lists db, redis, elasticsearch and llm.
- Public probes of `/actuator/prometheus` and `/actuator/health/deps` 404.
- Unaffected: `db` still goes DOWN when the database is down.

**Files:** `src/main/java/com/nexus/campus/config/health/`,
`src/main/resources/application-prod.yml`, `frontend/nginx.conf`,
`docs/adr/0007-degraded-status-is-not-unhealthy.md`.

## T5 - Bootstrap admin and an empty production seed

**Scope:** a real deployment must be administrable without a ghost account,
and must not start with sample content.

- Prod seeding is off: no sample accounts with random passwords, only the
  functional `AiAgent(999)`.
- `BootstrapAdminInitializer` creates `admin` from `BOOTSTRAP_ADMIN_PASSWORD`
  when no ADMIN exists, and logs one warning telling the operator to rotate
  it. Seeding on without a password keeps failing fast.
- `docker/mysql/init.sql` keeps schema plus reference data; sample posts,
  comments, messages and review logs move to a gated `DemoContentSeeder`, so
  production cannot hold content that points at authors who do not exist.
- `.env.example`, README and the pre-deployment checklist follow.
- Decision recorded in ADR-0008.

**Acceptance:**
- No ADMIN plus a password creates one; no ADMIN and no password creates none
  and warns once; an existing ADMIN is skipped idempotently.
- A prod start leaves `sys_user` empty apart from the bootstrap admin.
- Unaffected: dev demo experience behind `DEMO_SEED_ENABLED=true`.

**Files:** `src/main/java/com/nexus/campus/config/DataPreloader.java`,
`src/main/java/com/nexus/campus/config/BootstrapAdminInitializer.java`,
`src/main/java/com/nexus/campus/config/DemoContentSeeder.java`,
`docker/mysql/init.sql`, `.env.example`, `README.md`,
`docs/plans/pre-deployment-checklist.md`,
`docs/adr/0008-bootstrap-admin-and-empty-production-seed.md`.

## T6 - Trace ID across every boundary

Depends on T1.

**Scope:** one user-facing identifier per request, surviving the async hops
that make this pipeline hard to read.

- `TraceIdFilter` at the outermost order generates a 16-hex id into MDC and
  echoes `X-Trace-Id`. An inbound id is honoured only when
  `campus.security.trust-forwarded-headers` is on, so a public deployment
  cannot be flooded with attacker-chosen cardinality.
- Both async pools copy the MDC; each scheduled run gets its own id.
- 5xx bodies carry `traceId`, matching the header; the error toast shows the
  first 8 characters so a user can report a number.

**Acceptance:**
- A post with a code block logs the same traceId on the request thread and in
  the async review listener.
- Response header and 5xx body agree.
- Unaffected: 2xx response shape, JWT and XSS filter order.

**Files:** `src/main/java/com/nexus/campus/config/TraceIdFilter.java`,
`src/main/java/com/nexus/campus/config/AsyncConfig.java`,
`src/main/java/com/nexus/campus/dto/ApiResponse.java`, scheduled tasks,
`frontend/src/api/client.ts`.

## T7 - Align the frontend response contract

**Scope:** the declared envelope must be the returned envelope.

- `ApiResponse<T>` in the API client becomes `{ code, message?, data }`;
  success is derived from `code`. The backend does not gain a `success`
  field, because nothing reads it.
- Contract smoke test: representative endpoints always carry
  `code/message/data`, and `traceId` only shows up on 5xx.

**Acceptance:**
- `npm run build` type-checks against the real shape.
- The smoke test fails if the envelope changes shape again.
- Unaffected: no runtime behaviour change.

**Files:** `frontend/src/api/client.ts`, contract test under `src/test/java`.

## Drill - Prove it under a real failure

`benchmark/observability/drill.ps1` runs the observability profile and drives
an LLM outage end to end: metric names present, OS metrics present in the
prod container, the post parks in `pending-llm` while the breaker gauge reads
1, health stays 200 `DEGRADED`, the container is not restarted, the rate
limit counter and alert fire, recovery re-runs the reconcile, a test alert
lands in the Feishu group, and public probes of the actuator paths 404.

**Acceptance:** conclusions written up in
`docs/research/observability-drill-2026-09.md`.

## Out of scope this round

Error code catalogue and `BusinessException` (P1-2), config validation and dev
defaults (P1-4), non-root containers (part of P1-5), centralized role
interception (P1-6), frontend crash reporting (P1-7), cAdvisor, Loki,
OpenTelemetry, Alertmanager, and everything under P2.
