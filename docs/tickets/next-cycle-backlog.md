# Next Cycle Backlog

Source: the out-of-scope list in
[observability-ops-review-2026-09.md](../research/observability-ops-review-2026-09.md) section 5,
the unchecked boxes in
[pre-deployment-checklist.md](../plans/pre-deployment-checklist.md), and the 2026-09-16
deployment run. Nothing here reopens a decision that the previous two rounds already
settled: HTTP status stays the single source of truth, `ErrorCode` stays out, and the
monitoring stack stays behind the `monitoring` profile.

Written while the first real deployment was being finished, so two entries are not
engineering tickets at all. **OPS-1 and OPS-2 are the only things standing between the
current stack and a public address.** They need a domain holder and a Feishu bot; no
amount of code closes them.

Order: OPS-1 and OPS-2 first (they unblock the public launch), then SEC-1 -> DB-1 ->
SEARCH-1 -> FE-1 -> REL-1 -> OPS-3. SEC-1 goes before DB-1 because rewriting the
authorisation surface touches every controller that a migration would later move.

That sequence was written before the project's direction was settled, and it no longer
matches. The section below supersedes it; the original is left standing rather than
edited away so the change can be read off the file.

## Direction: what survives (2026-09-16)

The project's direction was settled on 2026-09-16: it is **a portfolio artifact**, not a
product being taken to users. Its measure is not user count — it is whether a reader who
has never seen this repository can say within five minutes what the project is, and wants
to keep asking. The working plan is
[portfolio-showcase-plan.md](../plans/portfolio-showcase-plan.md).

That turns this file from a debt list into a filter. One test per ticket: **does it become
something a reader can see, or is it a prerequisite for the landing page, the real-user
evidence, or the public address?**

| Ticket | Verdict | Why |
| --- | --- | --- |
| OPS-1 Feishu delivery | **do** | Prerequisite for the public address, and so for the landing page that needs somewhere to live. It moves the bridge from "the signature is correct" to "a person received it", and its own negative check — a deliberately wrong secret must be *reported*, not swallowed — says more than a successful send does |
| OPS-2 Tunnel and public address | **do** | The other half of the same external-credential blocker |
| OPS-3 Alert and rollback in CI | **do** | An extension of "the gate you trust must be trustworthy", and a demonstrable judgement call. After OPS-1 and OPS-2 |
| FE-1 Frontend crash reporting | **optional** | Independently reached by the 2026-09-15 frontend design audit, which found `ErrorBoundary` has no `componentDidCatch` and sits alone above the router. FE-1 covers *reporting* only; the layering half is still unowned |
| SEARCH-1 ES reconciliation policy | **decision only** | The ticket already says it is a decision before it is code. An ADR is an afternoon, and "we chose A, here is what we rejected" is the most persuasive material an interview can draw on. The implementation waits |
| SEC-1 Centralised role checking | deferred | Internal quality; it produces nothing a reader can see |
| DB-1 Versioned migrations | deferred | Same, and it is bound to the E5 restore rehearsal — worth little on its own |
| REL-1 Non-root web container | deferred | Same; the ticket itself already argues it deserves its own verified round |

Revised order: **OPS-1 -> OPS-2 -> OPS-3 -> SEARCH-1's ADR**, with FE-1 optional.

Deferred is not rejected. If the portfolio direction completes and the project is then
taken toward real users, or extracted as a library, SEC-1, DB-1 and REL-1 come back
first — that is when their cost starts being paid.

## OPS-1 - Real Feishu alert delivery

Status: blocked on an external credential. `alert-bridge` refuses to start without
`FEISHU_ALERT_WEBHOOK`, which is the correct failure: a green dashboard with no
recipient is worse than a restart loop.

**Scope:** put a real webhook (and `FEISHU_ALERT_SECRET` when the bot signs) into
`.env`, then prove one alert reaches a human.

The drill already proves the bridge signs a fixed payload correctly and that a failed
delivery is loud. It cannot prove the group receives anything; only a person can.

The mechanics — creating the bot, the one security mode the bridge implements, the rebuild
command, and the four refusal codes worth recognising — are in README's 接通飞书告警 section.
What stays open here is the part no script can do: an alert arriving in a real group.

**Acceptance:**
- Fire one test alert through the provisioned contact point and confirm the message
  appears in the target Feishu group.
- Repeat with a deliberately wrong secret and confirm the bridge reports the rejection
  rather than swallowing it.
- Record both outcomes in the deployment checklist, including the date.

**Files:** `.env` (not in git), `docs/plans/pre-deployment-checklist.md`.

**Rejected:** proving delivery with the in-network webhook sink. It validates the
payload and the signature, which the drill already covers, and says nothing about
whether a bot exists.

## OPS-2 - Cloudflare Tunnel and the public address

Status: blocked on `cloudflared tunnel login`. This machine has neither `cert.pem` nor
`~/.cloudflared/config.yml`, so no tunnel can be created from here.

**Scope:** create the tunnel, route `nexus-vibe.shing26.is-a.dev` to
`http://localhost:8080`, run it as a service, and confirm the public site.

Deployment steps 6-10 of
[deployment-and-blog-plan.md](../plans/deployment-and-blog-plan.md) are still the
contract to follow.

**Acceptance:**
- `https://nexus-vibe.shing26.is-a.dev` serves the SPA over HTTPS and `POST`ing a
  registration to the same host reaches the API.
- Public probing confirms `/actuator/prometheus` and `/actuator/health/deps` are 404
  from outside, not merely from the compose network.
- MySQL, Redis and Elasticsearch still publish no host ports.
- The GitHub repository homepage points at the live address.

**Files:** `~/.cloudflared/config.yml` (not in git), `docs/plans/pre-deployment-checklist.md`.

**Rejected:** exposing port 8080 through the router. That is what the tunnel exists to
avoid, and it would move the certificate and WAF story onto the home machine.

## SEC-1 - Centralised role checking

Status: open. The JWT filter already publishes `currentUserId` and `currentRole` as
request attributes, but every controller re-implements the decision:
`AdminController` has a private `requireAdmin`, `AiLogController` has a private
`isAdmin`, and `PostController` inlines `!"ADMIN".equals(role)`. Three spellings of one
rule, and the fourth copy is the one that will be forgotten.

**Scope:** one interceptor or aspect driven by an annotation, so a handler declares the
role it needs and no controller body performs the comparison.

- Annotation on the handler method, `ADMIN` for now; the interceptor reads
  `currentRole` and answers 403 through `BusinessException` so the envelope and the
  status stay consistent with R2.
- Delete the three local helpers and every inline comparison.
- A scan test fails when a controller source file mentions `"ADMIN"` again, mirroring
  the `NoEntityInControllerTest` approach.

**Acceptance:**
- Every endpoint that answered 403 for a `USER` before the change still answers 403
  with the same message; the admin endpoints still answer 200 for an `ADMIN`.
- The scan test is red the moment a controller reintroduces a role literal.
- Anonymous callers still get 401 from the filter, not 403 from the interceptor; the
  order matters and must be asserted.

**Files:** `src/main/java/com/nexus/campus/config/**`,
`src/main/java/com/nexus/campus/controller/**`,
`src/test/java/com/nexus/campus/contract/**`.

**Rejected:** Spring Security's method security. The project deliberately keeps a
hand-written JWT filter; introducing the full framework to express one role is a larger
change with its own configuration surface.

## DB-1 - Versioned migrations

Status: open. `docker/mysql/init.sql` only runs against an empty volume, and the
`migrate-0005`/`0006`/`0007` files are applied by whoever remembers. The deployment
checklist already has a line about it; this ticket is the fix.

**Scope:** introduce Flyway, baseline the existing schema at its current state, and make
startup apply pending migrations in order.

- The first migration is a baseline that matches a database built from today's
  `init.sql`; it must not re-create anything.
- `init.sql` shrinks to database creation plus the seed data that still belongs there
  (channels and tags).
- The app fails fast on a checksum mismatch rather than continuing with a half-applied
  history.

**Acceptance:**
- A brand-new database reaches the same schema as a database restored from the current
  backup, verified by comparing table DDL for all ten tables.
- An existing database at the pre-Flyway state baselines cleanly and a second start is a
  no-op.
- `restore.md` gains the step that the history table must be present after an import.

**Files:** `pom.xml`, `src/main/resources/db/migration/**`, `docker/mysql/init.sql`,
`docs/runbook/restore.md`.

**Rejected:** Liquibase. Flyway's plain SQL files are the smaller concept for a schema
this size, and the existing `migrate-000x` files are already written in that shape.

## SEARCH-1 - Decide the Elasticsearch reconciliation policy

Status: open, and it is a decision before it is code. Posts written while ES is down are
only indexed by the next successful write; nothing sweeps the difference.

**Scope:** choose between a scheduled drift sweep and an accepted, documented lag, then
implement whichever is chosen.

The two candidates are a periodic `DriftReconcileTask` that reindexes only rows whose
`update_time` is newer than the indexed document, and a stated policy that search may
lag indefinitely after an outage with the full rebuild as the manual remedy.

**Acceptance:**
- The chosen policy is an ADR with the rejected alternative and the reason.
- If the sweep is chosen, the drill gains a step that stops ES, writes a post, restarts
  ES, and observes the document appear without a full rebuild.
- If lag is accepted, the runbook says who runs the rebuild and when, and the search
  endpoint explains an empty result rather than returning a bare empty list.

**Files:** `docs/adr/`, `src/main/java/com/nexus/campus/task/**`,
`src/main/java/com/nexus/campus/service/PostSearchService.java`,
`benchmark/observability/drill.ps1`.

**Rejected:** running a full rebuild on a timer. It hides the drift signal, costs a full
index pass, and on the current single node it competes with live writes for the same
heap.

## FE-1 - Frontend crash reporting

Status: open. `ErrorBoundary` catches render errors and shows a fallback, but nothing
leaves the browser: the traceId in the toast is the only breadcrumb a user can hand
over, and only for API failures.

**Scope:** report unhandled errors from the browser to the backend, tagged with the
traceId when one exists.

- A small reporter wired into `ErrorBoundary` and `window.onerror`, rate-limited so one
  broken render loop cannot flood the log.
- A backend endpoint that writes the report as a structured log line with the same
  traceId field as everything else; no new storage.
- Explicitly no third-party SDK: the constraint is a single deployment with no vendor
  account.

**Acceptance:**
- Throwing inside a component produces exactly one log line with the component stack
  and the current traceId.
- A rejected promise that never reaches a boundary is also reported exactly once.
- The rate limit is asserted: a hundred throws in a second produce one line.
- The report endpoint is unauthenticated but size-capped and cannot be used to write
  arbitrary log messages.

**Files:** `frontend/src/components/ErrorBoundary.tsx`,
`frontend/src/api/**`, `src/main/java/com/nexus/campus/controller/**`,
`src/main/resources/logback-spring.xml`.

**Rejected:** Sentry or an equivalent hosted product. It adds an account, a DSN and a
network dependency to a system that already has structured logs and a trace id.

## REL-1 - Non-root web container

Status: open. `app` runs as uid 10001; `web` still runs the nginx master as root because
switching to `nginx-unprivileged` moves the listening port from 80 to 8080 and changes
the compose mapping and the log path with it.

**Scope:** move `web` to `nginx-unprivileged`, keep the public port and the CSP intact,
and prove the container still serves after the change.

**Acceptance:**
- `docker compose exec web id -u` is not 0.
- `http://localhost:8080/` returns 200, `/uploads/` still proxies, and the CSP headers
  are byte-identical to the current ones.
- The nginx access log still reaches `docker logs`.
- The drill's public-surface step still passes.

**Files:** `frontend/Dockerfile`, `docker/nginx/nginx.conf`, `docker-compose.yml`,
`benchmark/observability/drill.ps1`.

**Rejected:** running the current image with `user:` in compose. The unprivileged image
already moves the pid file, the temp paths and the cache directory; pinning the uid
without those changes trades one class of failure for another.

## OPS-3 - Prove the alert path and the rollback path in CI

Status: open. The drill runs on a developer machine by hand. Two of its steps are
cheap and deterministic enough to run on every pull request.

**Scope:** pick the two steps that do not need a live LLM or a long wait, and run them in
the GitHub workflow.

- The alert-provisioning step: the rules must still parse and the rules that watch
  application metrics must keep a non-OK `noDataState`, so an unreachable Prometheus
  cannot be reported as healthy again.
- The rollback step between two locally built tags.

The remaining steps stay manual, and the reason is already written down: they depend on
the local Docker daemon and would go green without proving anything.

**Acceptance:**
- A pull request that breaks an alert rule fails CI before merge.
- The CI job finishes inside the current ten-minute budget for the image job.
- The manual drill keeps its existing step numbering; the CI job calls the same script
  with a step filter rather than duplicating the assertions.

**Files:** `.github/workflows/maven.yml`, `benchmark/observability/drill.ps1`.

**Rejected:** moving the whole drill into CI. Most of its steps drive real failure
modes with sleeps; on a shared runner they would either be flaky or be trimmed until
they stopped testing the thing they were written for.
