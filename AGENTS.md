# Working in this repository

This file is the entry point for anyone — human or agent — picking the project up cold. Read it
before running anything. `CONTEXT.md` holds the vocabulary; `docs/INDEX.md` holds the map of
every document.

## What this is, and what "done" means

Nexus-Vibe is a full-stack forum whose subject is not the forum: it is a demonstration of
treating an LLM as an unreliable dependency. The code that matters is the failure handling
around it — fail-closed moderation, lease-based review claims, repair-and-retry on malformed
model output, degraded-but-serving health semantics.

The project reached its stopping point. [ADR-0009](docs/adr/0009-scope-is-done-at-deployment.md)
records the decision: the deliverable is a completed deployment, and the project is frozen
there. Further work is not forbidden, but it has to be argued for rather than assumed, and the
deferred list in `docs/tickets/next-cycle-backlog.md` is a backlog, not a plan.

## Layout

```
src/                      Spring Boot service (Java 21 toolchain, bytecode target 18)
  main/java/com/nexus/campus/
    agent/                  LLM client, repair-parse, review + safety listeners
    task/                   scheduled work: reconciliation, drift, funnels
    config/                 filters, interceptors, health indicators, bootstrap runners
frontend/                 React 19 + Vite SPA, Vitest for tests
docker/                   nginx config, MySQL init, observability stack (Grafana provisioning)
benchmark/
  jmeter/                 the load-test scenario behind docs/research/async-pool-loadtest.md
  observability/          the 23-step failure drill and its panel checks
scripts/                  backup.ps1, tunnel-ngrok.ps1
docs/
  adr/                    numbered decisions, 0001 upward
  research/               measurements, each with how to reproduce it
  tickets/                one file per round: scope, acceptance, files, rejected alternatives
  plans/                  forward-looking plans and the deployment checklist
  design/                 UI and UX architecture
  product/                product thinking: prioritisation and optimisation
  reviews/                walkthrough and black-box review reports
  runbook/                restore procedure, compose override for recovery rehearsals
  archive/                superseded reports, kept for the record
  assets/                 screenshots referenced by the reviews
```

Anything not listed above and not in `git ls-files` is machine-local: `scratch/`, `target/`,
`.env`, `backups/`, and the tool-state directories in `.gitignore`. Do not tidy them into the
repository and do not commit them.

## Conventions

**Documents.** ADRs are numbered sequentially and never rewritten after the fact — a changed
mind means a new ADR that supersedes the old one. Research notes carry the command that
produced the numbers. Ticket files follow `Status / Scope / Acceptance / Files / Rejected`;
the `Rejected` section is not optional, because "what we did not do and why" is the part that
survives a re-read. Write in the language the document is already in; do not translate to match
a preference.

**Links.** There are no root-level asset directories. Screenshots live under `docs/assets/`,
and every document that references one uses a path relative to the repository root.

**Claims.** This is the project's own discipline, and the reason its docs are worth reading: a
claim without a reproducible path does not go in. If a number is measured, name the command or
the file; if something is unproven, say so in those words.
`docs/research/observability-drill-2026-09.md` ends with a section listing what the drill cannot
prove — keep that habit.

## Verification before you claim anything works

```bash
mvn -B test                     # 344 tests / 55 classes; the count is asserted in README.md
cd frontend && npm run test     # 29 tests / 7 files
cd frontend && npm run lint
cd frontend && npm run build
cd docker/observability/alert-bridge && python -m unittest test_alert_bridge   # 17 tests
```

If you change a count, update the README badge and the Testing section in the same commit.
`mvn test`'s summary line is the only trustworthy count; `target/surefire-reports/*.txt`
accumulates runs and will lie to you.

**CI is the authority on the Docker build, not this machine.** `docker compose build` fails
locally during the container-internal `mvn`/`npm` stages because the package mirrors are
unreachable here; that is an environment fault with a long history, not a broken Dockerfile. CI
builds the committed multi-stage files on every pull request that touches them.

## The live deployment

`https://qualifier-discuss-marry.ngrok-free.dev` serves this machine's stack through an ngrok
tunnel started by `scripts/tunnel-ngrok.ps1`.

**The address and the CORS allowlist are a pair.** `CORS_ALLOWED_ORIGINS` in `.env` must contain
the tunnel's origin, or the SPA loads and every API call behind it returns 403 — a failure that
looks like a broken site in the browser and is invisible in the server logs. Change one, change
the other, restart `app`.

`docker compose exec` is how you probe the running stack; `http://localhost:8080` is the same
instance the tunnel serves.

## Rules that exist because something went wrong

- **Never commit secrets or `.env`.** Real values live in `.env` only, which is gitignored.
- **Never push to `master`.** Work on a `codex/<slug>` branch and open a pull request.
- **Never rewrite published history** without explicit instruction; a document's real home is
  its Git history.
- **Do not edit or revert unrelated uncommitted work.** Several agents and the maintainer share
  this working tree.
- **A named volume that already exists keeps its old owner.** After a rebuild, `app` may
  silently fall back to `/tmp` logging with a warning on stderr. The one-time `chown` is in
  `docs/plans/pre-deployment-checklist.md`.
