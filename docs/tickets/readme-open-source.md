# The README becomes a front door - the open-source first run

Source: this round (2026-10-11). The request is a system diagnosis of the repository's content
organisation, followed by a restructuring plan for `README.md` aimed at two audiences: someone
cloning it for the first time, and someone forking it. A draft circulated separately proposing
replacement README content; every factual claim in it was checked against the tree before this
ticket was written, and the corrections are recorded in full below. A README is the one file
where an unverified claim travels furthest, so the draft's errors are not quietly dropped -
they are the reason this ticket exists.

## Diagnosis - the five stated pain points, checked

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 1 | The narrative reads as a developer's private memo; a visitor cannot find "what is this" or "how do I run it in 3 minutes" | **Partly true** | The first runnable command sits at [README.md:168](../../README.md), after 150+ lines of narrative; the runnable Docker path is at line 226. But the "30 seconds / 10 minutes / 90 minutes" ladder at [README.md:44](../../README.md) is already the right shape and the demo-first opening is right. The problem is order and depth, not absence of visitor orientation |
| 2 | The `com.nexus.campus` package name is an unexplained historical burden | **True, minor** | Rename is recorded in [ADR-0001](../adr/0001-rename-campus-entities-to-vibe-names.md); nothing in the README says the package kept the old domain word. Costs one sentence |
| 3 | The anti-forgery discipline (ADR-0010) is presented as a limitation | **True** | [README.md:21](../../README.md) opens the demo with "It is a recording rather than a live call" - accurate, framed as an apology. The replay terminal shipped last round (`frontend/src/components/ui/ReviewStepStrip.tsx`) and gives the positive framing the README never adopts |
| 4 | The extension seams are hidden in `docs/research/` and the source | **True, and worse than the draft knew** | Seam one (`ReviewPolicy`, single method `shouldReview`) is real and wired: `agent/ReviewPolicy.java`, `config/ReviewPolicyConfig.java` (`@ConditionalOnMissingBean`), default `CodeBlockReviewPolicy`. Seam two (`Reviewer`) is *decided* in ADR-0012 and *not built*: no `Reviewer` interface exists in `src/main/java`; [ai-review-seam.md](ai-review-seam.md) AI-2 and AI-3 (the README section itself) are both open. The draft publishes a `ReviewPolicy` example with `supports`/`evaluate`/`ReviewResult` that does not compile against the real interface |
| 5 | Six-plus `docker-compose` variants overload a newcomer | **False** | The root holds exactly one `docker-compose.yml`; the six variants live under `benchmark/` (concurrency, jmeter, migrations, observability drill + render) and `docs/runbook/` (restore test), each driven by its own script. A newcomer meets one compose file. The draft's follow-up ("make `docker compose up -d` reach `localhost:5173`") would introduce the only real error in this area - see the corrections table |

Two further findings the request did not ask for, recorded because they touch the same file:

- **The README contradicts itself on the frontend test count.** [README.md:478](../../README.md) still
  says "前端 33 用例 / 8 个文件" while its own badge and command comment say 42; `AGENTS.md` also
  says 33. The fresh count (below) is **42 tests in 11 files** - the last round shipped tests and
  updated the badge but not the sentence or the contract file.
- **Repository hygiene is fine.** The root's crash logs and scratch artifacts are not tracked
  (`git ls-files` is clean of them); no hygiene work is needed to open-source this tree.

## Scope

Documentation only. No change under `src/`, `frontend/`, `docker/`, `benchmark/`. Rewrite
`README.md` as a front door; move two operations blocks into `docs/runbook/` (moved, linked, not
deleted); fix one stale count line in `AGENTS.md`; add the `docs/INDEX.md` rows and a CHANGELOG
entry in the same commits that touch each area. The ADRs, research notes and tickets are not
edited - this round changes how they are reached, not what they say.

## S1 - The new README, section by section

The rewrite keeps every measured number and every link target that is true today, and reorders
around the two audiences. Target length: about 250 lines, down from 427.

1. **Header** - one-sentence positioning ("a full-stack AI developer community whose subject is
   treating an LLM as an unreliable dependency"), badges unchanged (CI, Java 18, Spring Boot
   3.3.5, React 19, tests 417 + 42, MIT).
2. **What this is** - four lines: async LLM code review, fail-closed safety, lease claims,
   repair-and-retry, degraded-but-serving; "the code that matters is the failure handling".
3. **Live demo** - the URL, the showcase post, the recording statement reframed positively
   ("the review replays its steps on the post page; ADR-0010 records why it is a recording, not
   a live feed"), the screenshot, and the two caveats condensed to two bullet lines.
4. **Run it in three minutes** - three clearly separated paths, each with its own port:
   **A. Docker full stack**: `cp .env.example .env`, fill the four blanks
   (`DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_ADMIN_PASSWORD`;
   `APP_TAG=dev` ships in the example), `docker compose up -d --build`, open
   `http://localhost:8080` (`WEB_PORT`); the AI needs one `docker compose exec ollama ollama
   pull qwen2.5:3b`. **B. Local development**: `mvn spring-boot:run` (no wrapper exists) on
   :8081 plus `npm install && npm run dev` in `frontend/` on :5173, H2 in-memory. **C. No model
   at all**: `AI_REVIEW_ENABLED=false AI_SAFETY_ENABLED=false` - the documented forum that posts
   without calling any model or holding any key. Plus one short "why there is no
   `admin / Admin@123456`" paragraph: dev H2 seeds `admin/123456`; a deployment uses
   `BOOTSTRAP_ADMIN_PASSWORD` once (ADR-0008); `DEMO_SEED_ENABLED` is opt-in.
5. **What you get** - the feature map, condensed (Core / AI / Design / Infrastructure), keeping
   the entries that demonstrate the thesis (lease, fail-closed, repair, drift reconcile, pool
   isolation).
6. **Adapting it - the fork map** - the section the draft asks for, written against the real
   tree: the two seams with their *actual* status (`ReviewPolicy` live today, one class to
   replace; `Reviewer` decided in ADR-0012, tracked open in AI-2 - labelled as such, not
   faked), the recipe for running with no review at all, the model swap as three environment
   variables (`LLM_ENDPOINT` / `LLM_MODEL` / `LLM_API_KEY`, keys under `campus.ai.llm.*`), the
   frontend beat list in `frontend/src/components/ui/reviewSteps.ts` plus
   `ReviewStepStrip.tsx` and `AiReviewTerminal.tsx`, and one line naming what is deliberately
   *not* a seam (lease, reconciliation, fail-closed, review validity - the thesis), each
   linking the ADR and the ticket.
7. **Architecture** - the existing diagram and the resilience chain, kept nearly verbatim; this
   is the project's argument, not decoration.
8. **The evidence wall** - the four research rows with their numbers unchanged, the blog, and
   the fifteen-ADR index link; keeps the habit of pointing at what a measurement cannot prove.
9. **Tech stack** - the table, unchanged.
10. **Configuration quick reference** - the twelve-row subset of `.env.example` that a first run
    actually needs, with the pointer to the full file; the long ops tables move out (S4).
11. **Observability** - three layers condensed, the `--profile monitoring` line, the drill link,
    and one line to the moved Feishu walkthrough.
12. **Testing gates** - the counts (417 / 70, 42 / 11, 28), the coverage floor (0.70, first
    measured 74.27%), and the command block; the `-DargLine` / JaCoCo trap keeps its warning.
13. **Repository structure** - the corrected tree (adds `filter/`, `util/`, `health/`, the
    `benchmark/` variant directories, `frontend/src/{api,stores,types,test}`) and the one
    sentence that names the package/rename gap from pain point 2.
14. **Contributing** - fork, feature branch, the gates; the four source-scanning contract checks
    get one sentence each at most.
15. **License** - MIT, unchanged.

### The allowed numbers

Every number the new README may print, with the file it comes from and how to reproduce it.
A number not in this table does not go in.

| Number | Value | Source | Reproduce |
|---|---|---|---|
| Backend tests | 417 run, 0 failures, 70 classes | fresh 2026-10-11 run (was 2026-10-03) | `mvn -B test` summary line (never `target/surefire-reports/`) |
| Frontend tests | 42 in 11 files | fresh 2026-10-11 run (replaces the stale 33 / 8) | `cd frontend && npm run test` |
| Alert bridge tests | 28 | README Testing section | `cd docker/observability/alert-bridge && python -m unittest test_alert_bridge` |
| Coverage floor | 0.70; first measured 74.27% (2967/3995) | `pom.xml` `jacoco.line.minimum` | `mvn -B test` runs `jacoco:check` |
| ADRs | 15 (0001-0015) | `docs/adr/` | `ls docs/adr` |
| Async pool load test | 25,204 samples, 13 errors (0.1%), p50/p90/p99 37/1,770/3,353 ms, 6,231 posts left FAILED(3), zero crashes | [async-pool-loadtest.md](../research/async-pool-loadtest.md) | `benchmark/jmeter/run-loadtest.ps1` |
| Like-concurrency probe | ~121k rejections under 50 concurrency, zero crashes | README resilience chain + [async-pool-loadtest.md](../research/async-pool-loadtest.md) | `benchmark/concurrency/like-concurrency.ps1` |
| Container GC | 768 MB limit, 0 full GCs | [jvm-container-gc-analysis.md](../research/jvm-container-gc-analysis.md) | the report's own commands |
| Deep pagination | delayed join 40% slower, rolled back | [late-row-lookup-deep-pagination.md](../research/late-row-lookup-deep-pagination.md) | the report's EXPLAIN |
| Drill | 23 steps | [observability-drill-2026-09.md](../research/observability-drill-2026-09.md) | `benchmark/observability/drill.ps1` |
| Ports | web 8080, API dev 8081, vite dev 5173 | `docker-compose.yml` (`WEB_PORT`), `application.yml`, `frontend/vite.config.ts` | the three files |

## S2 - Corrections to the circulated draft

The draft is unusable as printed. Each row is a claim it makes, the tree's answer, and where the
answer lives. The implementation links every URL inside the repository; nothing links to a
search engine.

| Draft claim | Reality |
|---|---|
| `docker compose up -d` then open `localhost:5173` | The web container publishes `${WEB_PORT:-8080}` (`docker-compose.yml:215`); 5173 is the Vite dev server only. First run needs `--build`, and `.env` is mandatory - `APP_TAG:?` fails the compose parse without it (`docker-compose.yml:142,149`) |
| Default demo account `admin / Admin@123456` | No such account exists by default: `DEMO_SEED_ENABLED=false` (`.env.example`). Dev H2 seeds `admin/123456`; a deployment bootstraps from `BOOTSTRAP_ADMIN_PASSWORD` (ADR-0008) |
| "Zero-Key Mock mechanism" | No such mechanism. Compose points at the internal Ollama; the model must be pulled once, and `.env.example` states local 7B often produces unusable reviews that the semantic validator degrades silently. The honest zero-key story is the two AI switches off (S1 path C) |
| `./mvnw spring-boot:run` | The repository has no Maven wrapper; the command is `mvn spring-boot:run` (README:168) |
| `ReviewPolicy` example with `supports` / `evaluate` / `ReviewResult` | Does not compile. The interface is one method, `boolean shouldReview(VibePost post)` (`agent/ReviewPolicy.java`); the reviewer seam is decided in ADR-0012 and open as AI-2, with no `Reviewer` interface in the tree yet |
| `campus.ai.api-base-url` / `timeout-ms` in `application.yml` | Real keys are `campus.ai.llm.endpoint` / `api-key` / `model` / `timeout`, bound to `LLM_*` environment variables (ADR-0013) |
| 15 ADRs, 417 backend tests, Redis Lua atomics, MIT license, MySQL migrations and rollbacks under `docker/mysql/` | All true - keep, with the reproduce paths from the allowed-numbers table |
| `[cite: 2]` residue and `https://www.google.com/search?q=...` hrefs | Replace with repository-relative paths or `tree/master` GitHub links, matching what `frontend/src/links.ts` already does |
| Rename the repository to `nexus-vibe` | Already `shing26/nexus-vibe` (`git remote -v`); ADR-0001's rename was domain entities, not the repository |

## S3 - GitHub repository metadata

- **Description** (EN): a full-stack AI developer community whose subject is treating an LLM as
  an unreliable dependency - fail-closed moderation, lease-based review claims,
  repair-and-retry parsing, degraded-but-serving health, with the measurements attached.
- **Topics**: `spring-boot`, `react`, `vite`, `java`, `ai-agent`, `llm`, `code-review`,
  `observability`, `prometheus`, `grafana`, `redis`, `mysql`, `elasticsearch`, `forum`.
  `boilerplate` and `hackable` are dropped: a topic list is positioning, and neither can be
  demonstrated in one click the way the ADR index can.
- **Website**: the `docs` tree (`https://github.com/shing26/nexus-vibe/tree/master/docs`), not
  the ngrok demo - the demo is a home machine that sleeps, and an About link that 404s at 3am
  costs more than it earns. The demo stays prominent where it is honest: the README's first
  screen.
- The in-app evidence links the request asks for already exist: the navbar and footer link the
  ADR index, the load test and the drill (`frontend/src/links.ts`, `NavLinks.test.tsx`). This
  round does not touch them.

## S4 - What moves out of the README, and where

Moved verbatim (content untouched, links from the README kept), into the runbook the project
already uses:

- The Feishu six-step walkthrough, the error-code table and the panel scripts ->
  `docs/runbook/feishu-alerting.md`.
- The release / rollback / backup table, "Running the live instance", the ngrok and
  CORS-pair warnings, the scheduled-task and log-volume hand-offs ->
  `docs/runbook/release-and-live-instance.md`.

`docs/INDEX.md` gains the rows in the same commit as each new file. Nothing is deleted; the
runbook pages keep their commands and expected outputs exactly as verified.

### Coordination in the same commits

- `AGENTS.md`: the frontend line `# 33 tests / 8 files` becomes `# 42 tests / 11 files`, matching
  the badge the last round shipped. AGENTS.md is the shared contract; leaving it stale re-teaches
  the wrong count to the next agent.
- `README.md:478`: the same stale sentence is corrected as part of the rewrite (S1, item 12).
- `docs/INDEX.md`: one row for this ticket now; two rows for the runbook files when they land.
- `CHANGELOG.md`: one entry under `[Unreleased] / Added` when the README lands.

## Acceptance

- Every number in the new README appears in the allowed-numbers table with a source file and a
  reproduce command; a reviewer can check any one of them in a single command.
- No external link except the demo URL, the Feishu documentation page and the badge shields;
  every internal link is repository-relative or a `tree/master` GitHub URL that resolves.
- Following only the README, a reader can start the full stack and reach a page on :8080; add
  one class implementing `ReviewPolicy` and have it picked up without editing another file; and
  name the two seams plus what is deliberately not one.
- The three gates still pass at their current counts: `mvn -B test` 417, frontend 42/11, bridge
  28 - and `README.md`, `AGENTS.md` and the badge agree on all of them.
- `docs/INDEX.md` lists every new or moved file; `CHANGELOG.md` carries the round entry.

**Files:** `README.md`, `docs/runbook/feishu-alerting.md` (new), `docs/runbook/release-and-live-instance.md` (new),
`AGENTS.md`, `docs/INDEX.md`, `CHANGELOG.md`, this ticket.

## Rejected

- **Keeping the Feishu walkthrough and the release table in the README.** A 427-line front door
  with a Feishu error-code table is a runbook wearing a README's name. The content moves with its
  commands and expected outputs intact; a stranger never needs it, an operator reaches it in one
  click.
- **"Zero-Config / Zero-Key Mock" framing.** No such mechanism exists, and the promise it makes
  is exactly the failure mode this repository keeps measuring: the site looks broken until an
  undisclosed model is pulled. The honest equivalent - two switches, no model, no key - is
  documented as its own path instead.
- **A fabricated `Reviewer` example.** ADR-0012 designed the seam; AI-2 has not built it. The
  README labels it open and links the ticket, and the worked example belongs to that ticket's
  acceptance - prose about an interface ages, a compiling implementation with a test beside it
  does not, which is the argument AI-3 already made.
- **Renaming the package or the repository.** Out of scope; ADR-0001 records why the entities are
  Vibe and the package kept the old word. One sentence explains it.
- **A live health link in the README hero.** `/actuator/health` answers one word, and on a
  sleeping home machine the URL is an ngrok error page that reads as a broken project - the
  reasoning R3 of the terminal round already recorded.

## Verification

Measured 2026-10-11, this machine, before this ticket was written:

```text
mvn -B test                  -> Tests run: 417, Failures: 0, Errors: 0, Skipped: 0;
                                jacoco:check met (bundle 128 classes); BUILD SUCCESS in 01:47
cd frontend && npm run test  -> 42 passed (11 files)   # the stale 33 / 8 is corrected above
git remote -v                -> https://github.com/shing26/nexus-vibe.git
```

Draft claims were checked against `docker-compose.yml`, `.env.example`,
`src/main/resources/application.yml`, `agent/ReviewPolicy.java`, `config/ReviewPolicyConfig.java`,
`docs/tickets/ai-review-seam.md`, `frontend/vite.config.ts`, and `git ls-files`.

## Status

Landed 2026-10-14 on this working tree. The plan itself (this file, the `docs/INDEX.md` rows, the
AGENTS.md count fix) was already in place; **the README rewrite has now landed per S1**: `README.md`
is reordered as a front door, every number was re-verified against a fresh run rather than trusted
from the plan (backend 417 / 70 classes, frontend 42 / 11, bridge 28, JaCoCo 74.83% = 3,018 / 4,033
lines from `target/site/jacoco/jacoco.csv`), and every link is repository-relative. The
implementation reorders, links, and corrects counts; the only number it adds is the 2026-10-14
coverage measurement, because the gates were re-run instead of assumed.

## Known follow-up

- `docs/assets/showcase/recorded-review.png` still predates the step strip; re-shoot from
  `/post/900000000000000001` next time the stack is up (same note as the terminal round).
- When AI-2 lands the `Reviewer` seam, the fork map's status line changes in the same commit -
  the README is not allowed to be ahead of the tree.
