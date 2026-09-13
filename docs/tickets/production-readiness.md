# Production Readiness - Observability Tickets

Source: [production-readiness-assessment-2026-09.md](../research/production-readiness-assessment-2026-09.md),
gap list P0-1..P0-6 plus P1-1 and P1-3. North star: the system can prove it is
serving traffic, and says so before a user finds out.

Scope of this round: P0 in full, traceId, and the contract type fix. Error
codes, `BusinessException`, centralized `@RequiresRole`, non-root containers
and frontend crash reporting are deliberately left for the next round.

## T1 - Log to disk as structured JSON

Status: done on `codex/production-readiness`. The drill read the file the prod
container actually wrote: one JSON object per line on the `app-logs` volume, with
the same `traceId` the response header carried.

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

Status: done on `codex/production-readiness`. The prod container serves the
scrape with OS and disk metrics and no cgroup crash, so the exclude stayed
deleted and this ticket is not blocked.

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

Status: done on `codex/production-readiness`. Rules and bridge are in the repo and the bridge's
5 unit tests pass. The drill checked every metric name the 4 rules select against a live scrape,
and posted a real alert at the bridge, which answered 502 and named the missing webhook rather
than swallowing it. Delivery into a real Feishu group still needs a webhook and stays manual.

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

Status: done on `codex/production-readiness`. Verified from outside the stack: `/actuator/health`
200 through nginx, `/actuator/prometheus`, `/actuator/health/deps` and `/actuator/env` all 404,
and a `DEGRADED` app stayed `healthy` to Docker with zero restarts.

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

Status: done on `codex/production-readiness`. Verified on a first boot with an empty database:
0 posts, 7 reference channels, and the bootstrap `admin` logs in with role ADMIN. `DemoContentSeeder`
refuses to touch a MySQL that does not already hold demo rows, which is the guard that keeps an
existing deployment's content alone.

**Scope:** a real deployment must be administrable without a ghost account,
and must not start with sample content.

- Prod seeding is off: no sample accounts with random passwords, only the
  functional `AiAgent(999)`.
- `BootstrapAdminInitializer` creates `admin` from `BOOTSTRAP_ADMIN_PASSWORD`
  when no ADMIN exists, and logs one warning telling the operator to rotate
  it. Seeding on without a password keeps failing fast.
- The username `admin` belongs to someone else, or the password is missing: the
  runner says so and leaves the database alone. Booting is not the emergency.
- `docker/mysql/init.sql` keeps schema plus reference data; sample posts,
  comments, messages and review logs move to a gated `DemoContentSeeder`, so
  production cannot hold content that points at authors who do not exist.
  MySQL only, insert only, and only while `vibe_post` is empty; the H2 dev
  profile still reads `data.sql`.
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
`src/main/resources/db/mysql/demo-content.sql`,
`docker/mysql/init.sql`, `.env.example`, `README.md`,
`docs/plans/pre-deployment-checklist.md`,
`docs/adr/0008-bootstrap-admin-and-empty-production-seed.md`.

## T6 - Trace ID across every boundary

Depends on T1.

Status: done on `codex/production-readiness`. A live run of the suite shows one id on the
request thread and on `agent-llm-*` in the same second, so the handoff is proven rather than
assumed.

**Scope:** one user-facing identifier per request, surviving the async hops
that make this pipeline hard to read.

- `TraceIdFilter` at the outermost order generates a 16-hex id into MDC and
  echoes `X-Trace-Id`. An inbound id is honoured only when
  `campus.security.trust-forwarded-headers` is on, so a public deployment
  cannot be flooded with attacker-chosen cardinality.
- Both async pools copy the MDC; each scheduled run gets its own id.
- A sweep invoked from an HTTP request keeps that request's id instead of
  inventing one, so the manual trigger stays attached to what caused it.
- 5xx bodies carry `traceId`, matching the header; the error toast shows the
  first 8 characters so a user can report a number. 2xx envelopes keep their
  exact previous shape.

**Acceptance:**
- A post with a code block logs the same traceId on the request thread and in
  the async review listener.
- Response header and 5xx body agree.
- Unaffected: 2xx response shape, JWT and XSS filter order (the trace filter sits
  one slot ahead of both and changes neither).

**Files:** `src/main/java/com/nexus/campus/config/TraceIdFilter.java`,
`src/main/java/com/nexus/campus/config/TraceIdConfig.java`,
`src/main/java/com/nexus/campus/util/TraceIds.java`,
`src/main/java/com/nexus/campus/config/MdcCopyingTaskDecorator.java`,
`src/main/java/com/nexus/campus/config/AsyncConfig.java`,
`src/main/java/com/nexus/campus/dto/ApiResponse.java`, scheduled tasks,
`frontend/src/api/client.ts`.

## T7 - Align the frontend response contract

Status: done on `codex/production-readiness`. Type-only change on the client: lint and
build pass unchanged, and `ResponseContractTest` now fails if the envelope drifts again.

**Scope:** the declared envelope must be the returned envelope.

- `ApiResponse<T>` in the API client becomes `{ code, message?, data }`;
  success is derived from `code`. The backend does not gain a `success`
  field, because nothing reads it.
- Callers read `res.data.data` on the happy path and `err.response.data.message`
  on the failed one, both of which the real shape already provided; the drift was
  invisible precisely because no code ever touched the phantom field.
- Contract smoke test: representative endpoints always carry
  `code/message/data`, and `traceId` only shows up on 5xx.

**Acceptance:**
- `npm run build` type-checks against the real shape.
- The smoke test fails if the envelope changes shape again, including a `success`
  field appearing unannounced or a fourth key leaking in.
- Unaffected: no runtime behaviour change.

**Files:** `frontend/src/api/client.ts`,
`src/test/java/com/nexus/campus/controller/ResponseContractTest.java`.

## Drill - Prove it under a real failure

Status: done. 16 of 16 steps pass on a real run
(`2026-09-13 14:17:08`, ~11 min with prebuilt images); conclusions in
[observability-drill-2026-09.md](../research/observability-drill-2026-09.md).

`benchmark/observability/drill.ps1` runs the monitoring profile as its own
compose project (own containers, own volumes, published on 18080) so it can
sit next to a running stack, and drives an LLM outage and an exhausted rate
limit for real. Proven on the machine: a first install is empty but
administrable, JSON logs land on the `app-logs` volume with the request's
trace id, every AI metric name is on the scrape with its SLO buckets, OS and
disk metrics come back in the prod container once the cgroup exclude is gone
(no JVM crash, so T2 is not blocked), 3 posts park in `PENDING_REVIEW` +
`FAILED` while the breaker gauge reads 1 and sheds, health stays
`200 DEGRADED` with Docker still calling the container healthy, the limiter
counts its own 429s, the recovery sweep re-dispatches what the outage parked,
the deps group names all four components, and public nginx serves
`/actuator/health` while denying everything else under `/actuator/*`. The Grafana
step asks the alerting engine what it loaded instead of trusting a mounted file,
and the alert is posted at the URL the engine registered for the bridge.

**Acceptance:**
- Each step reports PASS with the raw probe kept in an evidence file, and the
  report lists what the drill deliberately could not prove: delivery into a
  real Feishu group (no webhook configured), Prometheus moving a rule into
  pending/firing (needs a 5m/15m sustained condition), the Grafana UI, and
  the rotation caps.
- Unaffected: the developer's own stack and volumes (the drill only ever
  removes `nexus-drill_*`), and `mvn test` at 284.

## Out of scope this round

Error code catalogue and `BusinessException` (P1-2), config validation and dev
defaults (P1-4), non-root containers (part of P1-5), centralized role
interception (P1-6), frontend crash reporting (P1-7), cAdvisor, Loki,
OpenTelemetry, Alertmanager, and everything under P2.
