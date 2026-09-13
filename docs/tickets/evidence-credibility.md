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
A ninth joins that list with this round's evidence: **the `es-data` reindex is never run by a
restore, and until this round it reported a number that meant nothing**. The claim this ticket
originally recorded - that "only per-post `indexPost` exists" - was **wrong when it was written**:
`POST /api/v1/admin/search/reindex` and `PostSearchService.rebuildIndex` have existed since
`9c4b002` (2026-08-14) and had two tests. What is actually true is worse in one specific way:
`rebuildIndex` returned the size of the list it was handed, so the endpoint reported
`reindexed: 32` whether Elasticsearch accepted 32 documents, rejected all 32, or was not running.
Fixed here (see E9), and `docs/runbook/restore.md` section 7 now makes the reindex a restore step.

## E1 - Stop scheduled jobs from poisoning the test context

Status: done, and CI proved it. `ca4cf6d` — one markdown-and-docs commit, the same shape that
made run `34743397875` red — came back green in 59s, as did the code commit before it
(`34783762204`, 1m2s). The failure this ticket describes was never a code regression: the red run
(`0284933`) changed one markdown file, and the run six minutes earlier (`a802b73`) was green on the
same tests.

What is still not proven here, stated so the next person does not over-read two green runs: the
flake was clock-driven, so it could only show itself on a run that happened to cross a `0 3/5 * * * ?`
boundary while a stub window was closed. Two greens are consistent with the gate being fixed and are
not evidence that it is fixed. The real evidence is the gate itself — `SchedulingGateTest` asserts
that no job bean exists in the test context, which is why the mechanism cannot come back without a
test going red. E1's acceptance asked for 3–5 repeats; that is a calendar matter now, not a work item.

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

Status: done. Follow-up from the previous review (Spec findings 1 and 2).

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

Status: done, and re-exercised end to end by the drill — after first teaching a lesson
worth keeping in the open. This ticket is the highest-severity thing the previous round
shipped, and the fix as written here was itself broken:

- This file told the implementer to set `noDataState: ALERTING`. Grafana resolves only
  `Alerting | NoData | OK | KeepState`, and a provisioned file it cannot parse aborts
  startup. With `restart: unless-stopped` that is a crash loop: no dashboard, no
  notifier, no rules — an alerting layer that cannot fail silently, failing by taking
  the whole monitoring profile with it. Only `grafana-provisioning-loaded`, which asks
  `/api/health`, could see it; the step that reads the YAML passed throughout. Fixed in
  `rules.yaml`, guarded in 0.3s by `GrafanaAlertProvisioningTest`, and the drill now
  reads both state knobs back out of the engine API rather than trusting the file.
- Six `errorState:` keys were removed on the way. Provisioning has two state knobs, not
  three: that key parsed, was ignored, and let the file's header argue for a policy the
  engine never had.
- The drill's own bridge step was written against the *old* behavior (start anyway,
  answer 502 when unconfigured). It now asserts the two real halves: the bridge refuses
  to start with no target, and a Grafana-shaped notification travels through
  `benchmark/observability/webhook-sink`, a receiver that recomputes the Feishu
  signature and answers 200-with-an-error-code on a mismatch.

Original finding, kept for the record:
All four rules in
`docker/observability/grafana/provisioning/alerting/rules.yaml` set
`noDataState: OK`. If the app dies, or Prometheus
dies, or Grafana cannot reach Prometheus, every rule reports "healthy". The
structural gap the assessment called out — nobody knows when self-healing fails —
is reproduced inside its own fix. Compounding it: the whole monitoring stack
sits behind `profiles: ["monitoring"]`, and
`FEISHU_ALERT_WEBHOOK: ${FEISHU_ALERT_WEBHOOK:-}` defaults to empty, so on a normal
boot there is no scraper and no listener.

**Scope:**
- `noDataState: Alerting` (that exact spelling; see the status above) on the
  availability-facing rules (circuit, backlog, scrapability), keeping the ratio rules
  honest about what no-data means for them.
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

Status: done, including the acceptance line that was the point of it. The rollback has now
been performed on the drill host (`2026-09-14 05:25` run, step
`rollback-swaps-between-two-real-image-tags`): `prerelease-20260913` → `latest` →
`prerelease-20260913`, and after each move the container's own `Config.Image` is the tag that
was asked for and `/api/v1/posts` answers 200. What that does **not** prove is a rollback away
from a bad version — the two builds differ by this round's observability work, so the sequence
shows the mechanism is real, not that it would have saved us from something.

Original finding, kept for the record — `app`
(`docker-compose.yml:134`) and `web` (`:186`) are declared with `build:` and no
`image:` reference, so every build replaces one anonymous image and there is
physically nothing to roll back to; only `alert-bridge` gets a tag (`:265`). There
is no `backup`, `restore` or `rollback` string anywhere in the tracked repository
(`git grep -i mysqldump\|backup` returns nothing), and no mention of data anywhere in
`docs/plans/pre-deployment-checklist.md`.

Delivered: both application images now carry `nexus-vibe-{app,web}:${APP_TAG:?}`, so
compose refuses to resolve at all without a tag, and `.env.example` documents the
bump-and-re-up release move plus the revert-and-re-up rollback. CI uploads the jar and
`dist` on a `master` push. All six always-on services got a `mem_limit`.

That last paragraph was true when this ticket was written, and it is the reason the drill
got a rollback step: on 2026-09-14 the acceptance sequence ran (`prerelease-20260913` →
`latest` → `prerelease-20260913`), so the tag is no longer a name nobody has pointed at.
Two limits on what that proves: the two images differ by this round's work, so the
sequence shows the mechanism moves the right container and the API still answers — it does
not show that a rollback rescues a real regression, because neither side is broken; and it
was a drill project, not the deployment, so the production host's own tags
(`docs/plans/pre-deployment-checklist.md` says which two must stay unpulled) remain the
thing to keep alive by hand.

What `mem_limit` is actually worth here, stated so nobody oversells it: before this ticket
no service had a per-container memory ceiling, so the only bound was the 7.65 GiB Docker
Desktop VM this machine shares with two other projects' live containers. The values are
sized to measured use (app holds ~400MiB, es ~653MiB), which makes this a fairness and
capacity decision between co-resident stacks — not the removal of a disk-filling risk. An
earlier revision of this paragraph claimed the opposite of both: that "stdout unbounded on
the host disk" was bounded by the VM anyway. That conflated RAM with disk, and it was
wrong; the `json-file` rotation from the previous round is what bounds log size on disk, and
"unbounded" was the correct word for it. Also unstated before: the three
`monitoring`-profile containers still have no `mem_limit` at all.

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

Status: **rehearsed on 2026-09-14** — the acceptance criterion this ticket exists for. A
backup set taken from the running stack was restored into a scratch compose project, and every
assertion passed with the numbers: dump sha256 matched the manifest, the load exited 0, all ten
table row counts matched exactly (32 posts, 10 users, 957 review logs), Chinese titles came back
legible, and the restored application served the restored upload at `200` with a byte-for-byte
matching hash. Evidence and the three blockers the run exposed are in
`docs/runbook/restore.md` section 9.

Two acceptance lines stay open, and neither is a documentation problem:
- **No off-box copy.** The host has one 477 GB NVMe partitioned into `C:`/`D:`/`E:`; "second
  volume" turned out to mean the same physical disk. The backup script now prints and records a
  `same-physical-disk` warning instead of letting the destination look safer than it is, but a dead
  drive still ends the project — which was the original fear, and is unfixed.
- **`es-data` is not restored and the rehearsal did not reindex it**, so a restored site can answer
  from a quietly empty index. The reindex endpoint exists; the restore procedure did not call it.
  `restore.md` section 7 is now that step, and E9 fixed the number it reports.

Also found by running rather than reading: `docker compose ls` rejects Go templates, so
`backup.ps1` died on its own destination check on every run until that moved to `--format json`;
and `container_name:` is not project-scoped, so the scratch project collided with the live
`nexus-db` until `docs/runbook/docker-compose.restore-test.yml` renamed its containers.

What the writing of the runbook measured, three of it contradicting this ticket:
- The migration history is not what the glob implied. `migrate-0001`–`0004` have never
  existed in any commit; numbering starts at `0005`; and `0005`/`0006`/`0007` are all
  already inside `init.sql`, so replaying them onto a fresh volume is a
  `Duplicate column name 'email'` error, not a no-op. `migrate-0005`'s own how-to-run
  line also named a database (`nexus_vibe`) that does not exist — fixed.
- The compose file declares **eight** named volumes, not seven: `ollama-data` was missing
  from the list, and is the largest thing on the disk (model weights, re-downloadable, so
  inventory-only and deliberately not dumped).
- `es-data` is not restored, and no restore step reindexes it. This was first written as "there
  is no full-reindex path, only per-post `indexPost`", which was **false on the day it was
  committed**: `AdminController:150` has exposed `POST /api/v1/admin/search/reindex` since
  `9c4b002` (2026-08-14), backed by `PostSearchService.rebuildIndex` and two tests. The real
  defect, found while correcting the claim, is that `rebuildIndex` returned the row count it read
  from MySQL rather than the document count Elasticsearch confirmed - so the endpoint reported a
  full reindex against a cluster that was down, or that had rejected every item. Fixed as E9;
  the missing restore step is `docs/runbook/restore.md` section 7.
- Backup failure does reach the alert path, but only under an explicit
  `-AlertViaBridge`; the "blocked on E3" note that made it opt-in is obsolete now that the
  bridge and its contact point are proven end to end, so the remaining question is whether
  a weekly task should default to it, which is a decision for whoever runs the first
  rehearsal.

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

Status: done, and now proved on the public side by the drill (rebuilt web image, real
nginx, app stopped and degraded): the public URL answers the `servable` group only, the
container healthcheck keeps the aggregate, and `/actuator/metrics` is back in dev while
prod and the edge both refuse it. `docker/nginx/nginx.conf:89` proxies the top-level
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

Status: done, and CI ran the experiment. Run `34783762204` (head `725daea`) is green on
all three jobs that matter: Backend build and tests in 1m2s on JDK 21 with
`Tests run: 296, Failures: 0`, Docker image build green on a PR that touches
`src/main/**`, Frontend green. That run is also the first proof of E1 from the
non-hermetic side: the previous head (`0284933`, a docs-only commit) was red because the
cron was inside the context, and this one passed with the same docs churn in history.
Two caveats kept visible: the smoke-run step is `if: github.event_name == 'push'`, so it
has never executed — its first real run will be the merge commit on `master`, which is
exactly the moment to be watching; and E1's acceptance asked for the flake to be proved
gone by repeating the run 3–5 times, which so far is one green run, not five.

Three separate honesty problems in
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

## E8 - Correct the in-repo documents against the current tree

Status: done as a dated 复核, not as a rewrite — see the new section at the top of
`docs/research/production-readiness-assessment-2026-09.md`, which is the choice this ticket
should have named up front. The assessment's own section numbers (§4.4, §5.1, §7) do not exist:
that document is structured as 一–五 with `### 1）…5）` subsections and no evidence index, so the
scope list below refers to a write-up that lives outside this repository (`.gitignore` keeps it
out). Corrected: every claim in the 复核 is pinned to a
command or a named drill step, and all eight step names were checked against `drill.ps1`.

Original finding, kept for the record: the assessment this round was planned against was written
at 2026-09-13 02:32, before T1-T7 landed. Its headline "可观测性与运维配套仍停留在
Demo 级" is now false for
observability. Counted against the tables that document actually has: **all six P0 gaps are
closed** (P0-1 日志、P0-2 指标+监控栈、P0-3 业务指标、P0-4 健康语义、P0-5 管理员引导、P0-6 告警规则),
and **two of seven P1** (P1-1 traceId, P1-3 前后端契约). The other five P1 items — error codes,
config validation, non-root app/web containers, `@RequiresRole`, frontend crash reporting — are
still open, four of them by this round's explicit choice. Its numbers
are stale too: 243 test cases (now 296), 28 test classes (now 44), 6 ADRs (now 8),
7 compose services (now 9), "traceId 占位/改造中" (now shipped), "logback 未提交"
(now committed), and "CI 3 Job 全绿门槛" (currently red on PR #2). Left uncorrected, these are
the claims a reader can disprove in one command, which is the fastest way to lose an argument
about everything else in the document.

**Scope:**
- Rewrite the 5-dimension table and the headline to what the tree proves, and
  split the sentence in two: observability is at "instrumented, alerted,
  exercised" level for a single instance; delivery operations are still manual.
- Update M8/M10, §4.4, §4.5, §5.1, §5.4 and the "补可观测性" conclusion at §7 (that work is now a
  completed thing, which is a better story than a plan) and add
  the E1 finding as a named example of what a non-hermetic gate costs.
- Record the accepted trade-offs as decisions, not omissions: 1GB/7-day local
  log retention with no search layer, hand-rolled trace id instead of OTel, and
  no private registry.
- Re-measure rather than hand-adjust: `mvn test` count, `git ls-files` counts,
  `docker compose config --services`, `git shortlog -sne`, and line totals.
- Out of this ticket: a non-tracked write-up that carried the same stale
numbers is deliberately not in this repository (`.gitignore` keeps it out).
Nothing in the tracked tree may name it; the 复核 section above is the
shared source of truth for whoever updates it.
- Not done here, and it should be its own ticket: the assessment's five-dimension percentages
  (日志体系 45%、监控与告警 15%) and its "综合约 60%" are still the 2026-09-12 numbers. They are
  now wrong in the optimistic direction for two of the five, and re-deriving them is a judgement
  call about what "就绪度百分比" is supposed to mean — which is a better conversation than a
  search-and-replace. The two operations facts that keep it from being a straight upgrade are
  in the 复核's closing list: the restore rehearsal was still ahead of us, and the `es-data` reindex
  was not part of any procedure. Both are now closed - the rehearsal ran on 2026-09-14, and
  `restore.md` section 7 makes the reindex a step - but the percentages have still not been
  re-derived, so they remain wrong in the optimistic direction.

**Acceptance:**
- Every number in the doc reproduces from a listed command; the document's own
  evidence index (chapter 8) lists those commands.
- No claim in the doc contradicts `git log` on the merge commit of PR #2.

**Files:** `README.md` (badges/observability section), `CHANGELOG.md`,
`docs/research/observability-ops-review-2026-09.md`,
`docs/research/production-readiness-assessment-2026-09.md`.

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
| Full 21-step drill as a CI job | Keep it manual; promote only the two cheap invariants (nginx denies actuator; degraded does not restart the container) if E7 leaves room |
| Flyway/Liquibase | Half a day to adopt, but the value only lands together with a tested restore (E5); scheduled right after it |
| ~~Bulk reindex path for `es-data`~~ → **done as E9** | The claim as first written ("only per-post `indexPost` exists") was false: a rebuild endpoint had shipped in `9c4b002`. What was real - that no restore ran it, and that it reported MySQL's row count instead of Elasticsearch's confirmed documents - is fixed in E9 and `restore.md` section 7. Index-on-read repair is still open and still wants a design decision |
| E1's flake is proven gone only by repetition | One green CI run on JDK 21 is data, not proof. As of 2026-09-14 the gate has been green on several consecutive pushes including docs-only commits (`ca4cf6d`, `f5bd10f`, `c8f9a3a`, `9b68a0f`), which is corroborating but still not a long sample. If it ever goes red on a markdown-only commit again, this whole round's premise is wrong |

## E9 - Make the reindex report the truth, and put it in the restore procedure

Status: done on `codex/production-readiness`. Found while correcting a false claim this round had
itself committed: `docs/runbook/restore.md` and this file both asserted "there is no bulk reindex
path anywhere in the code". It has existed since `9c4b002` (2026-08-14) as
`POST /api/v1/admin/search/reindex`, with two tests. So the gap was never capability. It was that
the number the capability prints was not a measurement.

**The bug:** `PostSearchService.rebuildIndex` returned `posts.size()` - the count of rows read out
of MySQL - and `AdminController` published it as `reindexed`. Elasticsearch's `_bulk` answers
HTTP 200 while individual items inside the body fail, and the old code looked only at the status
code. So the endpoint reported `reindexed: 32` for a cluster that rejected all 32, and
`reindexed: 32` for a cluster that was not running at all. That is the exact shape of the incident
E5 was written to prevent - a restored site serving empty search with a green checkmark next to it
- reached from the opposite direction: not a missing tool, but a tool that grades its own homework.

Both directions were then executed against a real cluster on 2026-09-14, in a second pass over the
restored scratch project with `elasticsearch` added: `{"requested":32,"esAvailable":true,
"reindexed":32,"failed":0,"complete":true}` with `nexus_posts` docs.count going 0 to 32, a keyword
search returning a post that predated the dump, and `_analyze` on `构建教程` yielding bigrams (so the
CJK mapping really landed rather than an auto-created index). Then, with the cluster stopped mid-run
while the app still believed it was available: `{"requested":32,"reindexed":0,"failed":32,
"complete":false}`. The implementation this replaced answers `reindexed: 32` to that second call.

**Scope:**
- `bulkIndex` returns `BulkResult(submitted, indexed, failed)`; `indexed` comes from counting the
  2xx item statuses in the `_bulk` body, and an unparseable body counts as zero (fail-closed, same
  stance ADR-0004 took for the safety gate).
- `createIndexIfNotExists` returns whether the index is ready, and `rebuildIndex` refuses to bulk
  into an index it could not create with the CJK mapping - a bulk into an auto-created index
  succeeds while making every multi-character Chinese query miss.
- The bulk call now asks for `refresh=true`, so "reindexed" means searchable on return rather than
  eventually, and its timeout went 10s -> 60s because a whole-site reindex is one request and the
  old ceiling was a timeout on the largest thing the endpoint exists for.
- The endpoint response carries `requested` / `reindexed` / `failed` / `complete` / `esAvailable`.
- `restore.md` section 7 makes the reindex a restore step with both commands written out, and says
  plainly that the rehearsal did not run either variant.

**Acceptance:**
- 9 new unit tests in `PostSearchBulkResultTest` pin the item-count parsing (all-2xx, partial
  429/400, all-503, unparseable, missing `items`, and a body that claims more items than were
  submitted), the ES-absent refusal, the empty-batch case, and `complete()`.
- `PostControllerIntegrationTest` now reads the response body instead of asserting
  `notNullValue()`: `requested == reindexed + failed`, `complete` false while anything failed, and
  with `esAvailable: false` the endpoint must report `reindexed: 0`. That assertion fails against
  the previous implementation, which is the point.
- Full suite: `mvn -o test` 305 cases, 0 failures (was 296 before this ticket).
- Unaffected: per-post `indexPost` on publish/edit/delete, the MySQL search fallback, and the
  frontend - no caller reads `reindexed` except the test.

**Files:** `src/main/java/com/nexus/campus/service/PostSearchService.java`,
`src/main/java/com/nexus/campus/controller/AdminController.java`,
`src/test/java/com/nexus/campus/service/PostSearchBulkResultTest.java`,
`src/test/java/com/nexus/campus/controller/PostControllerIntegrationTest.java`,
`docs/runbook/restore.md`.

**Rejected:** making the restore script call the endpoint for you. It needs an admin token, which
means putting `BOOTSTRAP_ADMIN_PASSWORD` into a backup tool's argument list, and a reindex nobody
looked at is not safer than a step in a runbook with a number to check. Index-on-read repair stays
open as a design question.
