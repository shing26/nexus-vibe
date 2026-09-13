# Evidence Credibility - Observability Follow-up and Ops Tickets

Source: the review of `codex/production-readiness` (PR #2) plus the
SRE / DevOps discussion recorded in
[observability-ops-review-2026-09.md](../research/observability-ops-review-2026-09.md).
North star: **the gate you trust must be trustworthy** — a green build, an alert
that fires, a backup you have actually restored, a rollback target you can point
at. Signal volume is not the problem anymore; signal credibility is.

Scope of this round: the 8 tickets below (~5.5 person-days). Deliberately left for
a later round, with reasons in the discussion doc: error-code catalogue +
`BusinessException` (which also owns the `404`-for-a-parked-post semantics),
centralized `@RequiresRole`, log aggregation (Loki/ELK), OTel tracing,
cAdvisor/resource dashboards, a private registry, and CD through the tunnel.

## E1 - Stop scheduled jobs from poisoning the test context

Status: open. This is why PR #2 is red, and it is not a code regression: the
failing run (`0284933`) changed one markdown file, and the run six minutes
earlier (`a802b73`) was green on the same tests.

Mechanism, verified in the current tree:

- `@EnableScheduling` is declared twice (`NexusCampusApplication:12` and
  `WebMvcConfig:25`), so all four `@Scheduled` crons run inside every
  `@SpringBootTest` context.
- `AiReviewReconcileTask:90` fires on `0 3/5 * * * ?`, i.e. on the wall clock,
  independent of test boundaries.
- `agent/LlmHealthCache` caches the probe verdict in a `volatile boolean` for
  5 minutes. `@MockBean LlmClient` is reset by Spring between test methods, so
  a probe landing outside the stubbing window reads Mockito's default `false`
  for an unstubbed `boolean` and pins "LLM unhealthy" for the next 5 minutes of
  the suite.
- While that verdict stands, `VibePostServiceImpl:637` fails every new post
  closed to `PENDING_REVIEW`, `pinPost:506` rejects it on `status != 1`, and
  `PostController:45` turns that into `code: 404`. That is exactly what CI saw
  at `PostControllerIntegrationTest:456`.

Consequence if unfixed: the only automated gate this repository has becomes
randomly red, and the first red teaches the author to re-run it rather than read
it. Every other ticket here is worth less than that failure mode.

**Scope:**

- One property, `campus.scheduling.enabled`, gating all four job beans
  (`AiReviewReconcileTask`, `DriftReconcileTask`, `LikeSyncTask`,
  `PostRankingService`'s hourly recalculation) — `true` by default, `false` in
  the `test` profile. Prefer `@ConditionalOnProperty` on the job beans over
  conditioning `@EnableScheduling`, because the reconcile task also registers a
  gauge in `@PostConstruct` and that must keep working in tests.
- Remove the duplicate `@EnableScheduling`.
- Make the health cache's staleness visible rather than load-bearing: when
  `LlmHealthCache` serves a cached `false` older than the window it should say
  so in the log line that fails a post closed. Do not change the fail-closed
  policy itself (ADR-0004 stands).

**Acceptance:**
- `PostControllerIntegrationTest` passes on 20 consecutive runs with no stub
  changes, including a run deliberately started just before a `x:03/x+5` cron
  boundary.
- CI on a docs-only commit is green; the run is recorded on this ticket.
- Unaffected: fail-closed behaviour in prod, the `pending-llm` reconcile path,
  and the 284-test baseline (the gate must not delete coverage).

**Files:** `src/main/java/com/nexus/campus/task/`,
`src/main/java/com/nexus/campus/service/PostRankingService.java`,
`src/main/java/com/nexus/campus/NexusCampusApplication.java`,
`src/main/java/com/nexus/campus/config/WebMvcConfig.java`,
`src/main/java/com/nexus/campus/agent/LlmHealthCache.java`,
`src/test/resources/application-test.yml`, `src/main/resources/application.yml`.

**Rejected:** patching the one assertion to tolerate 404, which hides a whole
class of cross-test pollution to save one line; injecting a fake
`LlmHealthCache` bean, which fixes this symptom and leaves three other crons
free to sweep fixtures mid-test.

## E2 - Finish what trace-id started, and stop trusting a public header

Status: open. Follow-up from the previous review (Spec findings 1 and 2).

**Scope:**
- Wrap the fourth cron (`PostRankingService:134`) in `TraceIds.runAsJob`, so
  "each scheduled run gets its own id" is finally true, and keep the CHANGELOG
  wording aligned with the code.
- Strip `X-Trace-Id` at `docker/nginx/nginx.conf` for every proxied location
  (`proxy_set_header X-Trace-Id "";`), and give trace trust its own property
  `campus.trace.trust-inbound-header` defaulting to `false`, instead of reusing
  `campus.security.trust-forwarded-headers`, which exists to decide whether
  client IPs are spoofable (`RateLimitInterceptor:141`).
- Align the vocabulary: `CONTEXT.md` records a Trace ID as 16-hex while
  `TraceIds.INBOUND` accepts up to 64 `[0-9a-zA-Z-]` characters. Either tighten
  the pattern or state both shapes in the glossary; pick one and make the doc and
  the code agree.

**Acceptance:**
- A request arriving with `X-Trace-Id: deadbeefdeadbeef` through nginx is logged
  under a freshly minted id, and the response header does not echo the client's
  value.
- Every one of the four crons emits a run with a non-empty traceId.
- Unaffected: the drill step that proves a user can see the short id.

**Files:** `docker/nginx/nginx.conf`, `src/main/java/com/nexus/campus/util/TraceIds.java`,
`src/main/java/com/nexus/campus/config/TraceIdConfig.java`,
`src/main/java/com/nexus/campus/service/PostRankingService.java`,
`CONTEXT.md`, `docs/tickets/production-readiness.md` (T6 rationale).

## E3 - Make the alerting layer unable to fail silently

Status: open. This is the highest-severity thing the previous round shipped.
All four rules in
`docker/observability/grafana/provisioning/alerting/rules.yaml` set
`noDataState: OK` (lines 52, 99, 147, 194). If the app dies, or Prometheus
dies, or Grafana cannot reach Prometheus, every rule reports "healthy". The
structural gap the assessment called out — nobody knows when self-healing fails —
is reproduced inside its own fix. Compounding it: the whole monitoring stack
sits behind `profiles: ["monitoring"]` (`docker-compose.yml:176-236`), and
`FEISHU_ALERT_WEBHOOK: ${FEISHU_ALERT_WEBHOOK:-}` defaults to empty
(`docker-compose.yml:237`), so on a normal boot there is no scraper and no
listener.

**Scope:**
- `noDataState: ALERTING` on the availability-facing rules (circuit, backlog,
  5xx), keeping the rate-comparison rule honest about what no-data means for it.
- Add a scrapability rule: `up{job="nexus-vibe"} == 0` sustained, severity
  critical. Prometheus already carries that job name (`prometheus.yml:16`).
- Fail fast, not quietly: the bridge must refuse to start (or the compose
  bootstrap must complain loudly) when `FEISHU_ALERT_WEBHOOK` is empty, instead
  of returning a 502 to a caller that has no listener.
- Put `--profile monitoring` in the deploy command and the checklist step, not
  only in the checklist's "known state" line (currently line 60).
- Add the one burn-rate rule the alert set is missing, with a volume guard,
  modelled on the existing 5xx rule at `rules.yaml:120`:
  `sum(rate(http_server_requests_seconds_count{application="nexus-vibe",status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count{application="nexus-vibe"}[5m])) > 0.0144`
  and `sum(rate(...[5m])) > 0.05`, `for: 5m`.
- Re-derive the two thresholds that were picked by eye (5xx ratio, rate-limit
  3x baseline) and record in the rule comment what traffic they assume. The
  backlog threshold of 10 is the only one with a measurement behind it
  (~2-3 posts/min at full speed).

**Acceptance:**
- Stopping the `app` container produces a critical alert within the scrape
  window, end to end, into the Feishu test group; the drill records it.
- No rule in the repo has `noDataState: OK` where missing data is itself the
  incident.
- `docker compose up` without `--profile monitoring` still works, and the
  checklist now says which command the deploy uses.

**Files:** `docker/observability/grafana/provisioning/alerting/rules.yaml`,
`docker/observability/alert-bridge/alert_bridge.py`, `docker-compose.yml`,
`docs/plans/pre-deployment-checklist.md`, `benchmark/observability/drill.ps1`.

## E4 - A release you can point at and a rollback you can perform

Status: open. `app` (`docker-compose.yml:110`) and `web` (`:155`) are declared
with `build:` and no `image:` reference, so every build replaces one anonymous
image and there is physically nothing to roll back to; only `alert-bridge` gets
a tag (`:228`). There is no `backup`, `restore` or `rollback` string anywhere in
the tracked repository (`git grep -i mysqldump\|backup` returns nothing), and no
mention of data anywhere in `docs/plans/pre-deployment-checklist.md`.

**Scope:**
- Tag both application images: `image: nexus-vibe-app:${APP_TAG:?}` and
  `image: nexus-vibe-web:${APP_TAG:?}`; release = bump `APP_TAG` in `.env`,
  rollback = put the old value back and re-up. `.env.example` documents it.
- Keep the last two tags' images on the host (build the old one, don't prune),
  and record the one command that performs a rollback.
- On `master` push, `upload-artifact` the jar and the frontend `dist`, so there
  is an object to point at even before the tag flow is exercised.

**Acceptance:**
- A rollback is demonstrated on the drill host: deploy tag A, deploy tag B, roll
  back to A, `/api/v1/posts` answers on A afterwards. Steps and output land in
  the drill evidence directory.
- `docker compose config` resolves with `APP_TAG` set and fails with a clear
  message when it is missing.

**Files:** `docker-compose.yml`, `.env.example`, `README.md`,
`.github/workflows/maven.yml`, `docs/plans/pre-deployment-checklist.md`.

**Rejected:** a private registry and image promotion — GHCR would be fine but
this deployment pulls and builds on the same machine it runs on, so a registry
adds credentials and a network hop without adding a rollback target.

## E5 - Back up, and prove the backup is restorable

Status: open. Single host, single disk, `db-data`/`app-uploads` volumes, one
human, no copy. A dead disk ends the project, and — unlike every gap in the
previous round — nothing in the system would tell anyone.

**Scope:**
- `scripts/backup.ps1`: `docker compose exec -T db mysqldump --single-transaction`
  to a timestamped file, plus an inventory of the named volumes, written to a
  second disk (or a second machine) on a weekly scheduled task. Retention kept
  small and explicit.
- `docs/runbook/restore.md`: the exact commands to bring the site back from a
  backup into a scratch compose project, including the migrate-*.sql history
  question (see E6 note on migrations).
- The first restore is part of the ticket, not an assumption: restore into a
  throwaway project and assert post counts and one uploaded file come back.

**Acceptance:**
- A restore is executed and its output saved; the runbook is followed literally
  and any step that required improvisation is corrected in the runbook.
- A failed backup writes something the on-call (you) would notice — reuse the
  alert bridge rather than inventing a second channel.

**Files:** `scripts/backup.ps1`, `docs/runbook/restore.md`,
`docker-compose.yml` (db service, if a dump helper mount is needed), `README.md`.

**Rejected:** object-storage backups and encryption-at-rest tooling. A weekly
dump on a second disk with a tested restore is worth more than an untested
pipeline to anywhere.

## E6 - One endpoint, one meaning; and stop losing dev tooling for free

Status: open. `docker/nginx/nginx.conf:77` proxies the top-level
`/actuator/health` to the public internet, while `Dockerfile:38` uses the same
URL for the container healthcheck. Since ADR-0007 maps `DEGRADED` to HTTP 200,
one status code now serves two different questions: "should Docker restart this
container" and "is the site fine for visitors". An external probe reports all-green
while the LLM is fully down. Separately, the base profile's exposure was changed
to `health,info,prometheus` (`application.yml:181`), which removed
`/actuator/metrics` from dev too and pinned that loss in a test
(`ActuatorMetricsTest:61`) — a debug-capability cost with no security benefit,
since the endpoint is not reachable publicly either way.

**Scope:**
- Split the two audiences: the public location serves a **servability** group
  (storage only, per ADR-0007's own definition), the container healthcheck keeps
  the aggregate. Name the groups after what question they answer.
- Restore `metrics` to the dev/base exposure and keep prod an allowlist of
  `health,info,prometheus`; update the test to assert the prod shape rather than
  the dev loss.
- Amend ADR-0007 (or add 0009) so the two-endpoint decision is on record.

**Acceptance:**
- With the LLM endpoint pointed at a dead port (the drill's existing step),
  `/actuator/health` inside the container stays 200, and the public endpoint
  answers the servability question without pretending dependencies are fine.
- `/actuator/metrics` works in dev, 404s in prod and 404s at the public edge.
- Unaffected: `db` DOWN still means 503 and still kills the container.

**Files:** `src/main/resources/application.yml`,
`src/main/resources/application-prod.yml`, `docker/nginx/nginx.conf`,
`Dockerfile`, `src/test/java/com/nexus/campus/controller/ActuatorMetricsTest.java`,
`docs/adr/0007-degraded-status-is-not-unhealthy.md`.

## E7 - Make the CI gate describe the thing that ships

Status: open. Three separate honesty problems in
`.github/workflows/maven.yml` and `pom.xml`:

- The gate tests JDK 18 (`java.version=18` at `pom.xml:22`, `java-version: "18"`
  in the workflow) while the shipped image runs `eclipse-temurin:21-jre`
  (`Dockerfile:17`) built on a 21 toolchain. The green checkmark and the process
  in production are not the same JVM.
- `ci-linux` is not dead — it activates on `<os><family>unix</family>`
  (`pom.xml:190-194`), so the ubuntu runner does pick it up and macOS silently
  does too. The name is wrong, and `<argLine>` at `pom.xml:202` is overwrite
  semantics, which will quietly swallow a future JaCoCo agent string.
- The `docker` job is gated on `github.ref == 'refs/heads/master'` plus push, so
  PRs never build the images — including this round, whose previous PR changed
  `Dockerfile`'s `HEALTHCHECK` with zero CI coverage.

**Scope:**
- Run the backend job on JDK 21 (the runtime that ships), keeping
  `maven.compiler.release=18` for now, and state in the workflow comment which
  JDK is a target and which is a runtime. Local stays free to be anything.
- Either delete `ci-linux` or rename it to what it means and stop auto-
  activating it, passing it explicitly from the workflow instead; if any argLine
  stays, use `@{argLine}` append semantics.
- Build the images on PRs that touch them (`paths:` filter on `Dockerfile`,
  `frontend/Dockerfile`, `src/main/**`, `pom.xml`), and on `master` push follow
  the build with a `docker run` + `/actuator/health` probe, which is the cheapest
  real smoke test this project can afford.

**Acceptance:**
- CI is green on JDK 21 with no new excludes; if `UseContainerSupport` reappears
  as a crash, that is recorded on this ticket rather than papered over with an
  `application-prod.yml` exclude.
- A PR that edits only `Dockerfile` shows a red/green image build, demonstrated
  by one deliberate no-op change.
- Unaffected: the `frontend` job, and the deliberate decision not to add
  coverage tooling this round.

**Files:** `.github/workflows/maven.yml`, `pom.xml`.

## E8 - Correct the assessment documents against the current tree

Status: open. `Nexus-Vibe-项目全量梳理.md` was written at 2026-09-13 02:32, before
T1-T7 landed. Its headline "可观测性与运维配套仍停留在 Demo 级" is now false for
observability, and five of the seven gaps in its M8 table are closed. Its numbers
are stale too: 243 test cases (now 284), 28 test classes (now 40), 6 ADRs (now 8),
7 compose services (now 9), "traceId 占位/改造中" (now shipped), "logback 未提交"
(now committed), and "CI 3 Job 全绿门槛" (currently red on PR #2). Left uncorrected
these are the easiest questions to fail in an interview.

**Scope:**
- Rewrite the 5-dimension table and the headline to what the tree proves, and
  split the sentence in two: observability is at "instrumented, alerted,
  exercised" level for a single instance; delivery operations are still manual.
- Update M8/M10, §4.4, §4.5, §5.1, §5.4 and the interview answers at §7 (the
  "补可观测性" answer is now a completed thing, which is a better story) and add
  the E1 finding as a named example of what a non-hermetic gate costs.
- Record the accepted trade-offs as decisions, not omissions: 1GB/7-day local
  log retention with no search layer, hand-rolled trace id instead of OTel, and
  no private registry.
- Re-measure rather than hand-adjust: `mvn test` count, `git ls-files` counts,
  `docker compose config --services`, `git shortlog -sne`, and line totals.

**Acceptance:**
- Every number in the doc reproduces from a listed command; the document's own
  evidence index (chapter 8) lists those commands.
- No claim in the doc contradicts `git log` on the merge commit of PR #2.

**Files:** `Nexus-Vibe-项目全量梳理.md`, `README.md` (badges/observability
section), `CHANGELOG.md`, `docs/research/observability-ops-review-2026-09.md`.

## Explicitly out of this round

| Item | Why not now |
|---|---|
| Error codes + `BusinessException` (+ `404` → `409` for a parked post) | Owns a contract change; mixing it into E1 would have kept the gate red for a week |
| Centralized `@RequiresRole` | Structural, but nothing has broken because of it yet |
| Loki / log search | 1GB of JSON locally plus a traceId prefix answers most single-instance questions; proposal is to accept it in writing (E8) |
| OTel / Micrometer Tracing | One service, no cross-process hop; the hand-rolled id already carries the user-facing value |
| cAdvisor, resource dashboards | The JVM/OS series that matter came free with the `SystemMetricsAutoConfiguration` un-exclude |
| CD through the tunnel | `cloudflared` runs as a Windows service; a runner holding those credentials is a bigger risk than a manual `compose up -d` |
| Automatic secret rotation | Two secrets, one maintainer; a checklist is more honest than a script |
| Nightly drill in CI | It needs this machine's Docker daemon; scheduled in CI it becomes a daily green that proves nothing |
| Full 16-step drill as a CI job | Keep it manual; promote only the two cheap invariants (nginx denies actuator; degraded does not restart the container) if E7 leaves room |
| Flyway/Liquibase | Half a day to adopt, but the value only lands together with a tested restore (E5); scheduled right after it |
