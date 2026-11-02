# Frontend polish round 2: FE-1 crash reporting, and a tag directory with real counts

Source: the residual items of the 2026-10-31 critique round (PR #27) — FE-1 from
`docs/tickets/next-cycle-backlog.md`, the near-empty `/tags` page, and two items this round
deliberately did not touch. Written after implementation; the verification numbers come from
the runs named at the bottom.

Status: implemented in the working tree on 2026-11-02. Backend `mvn -B test` measures
**463 tests / 78 classes**, 0 failures, coverage floor met; frontend **50 tests / 13 files**
and oxlint clean, production build green.

## FE-1 - Frontend crash reporting (closed)

The backlog ticket's scope, unchanged: report unhandled errors from the browser to the
backend, tagged with the traceId when one exists, with a rate limit so a broken render loop
cannot flood the log, an endpoint that writes one structured log line and no new storage, and
no third-party SDK.

**What landed.**

- `frontend/src/api/crashReporter.ts`: `reportCrash` plus global `error` and
  `unhandledrejection` handlers installed in `main.tsx`. Two gates in front of every send —
  the same kind+message is reported once per 60s window, and a window sends at most five
  reports — so the acceptance line "a hundred throws in a second produce one line" holds by
  construction, not by luck. Fields are truncated client-side to the backend's caps.
  The reporter is a bare `fetch(keepalive)`, deliberately not `apiClient`: it has to keep
  working when the thing that broke is the auth or toast store that `apiClient`'s
  interceptors depend on.
- `frontend/src/api/client.ts` now remembers the last `X-Trace-Id` any response carried;
  the reporter tags crashes with it when one exists.
- `ErrorBoundary.componentDidCatch` reports boundary errors with the component stack — the
  one breadcrumb an error stack cannot give.
- `CrashReportController` (`POST /api/v1/crash-reports`, unauthenticated): every DTO field is
  size-capped, `kind` is a closed three-value set, and the controller flattens control
  characters before logging, so a report can pad the log but cannot forge a second log line
  or smuggle a fake level. The line goes out at WARN through the same MDC traceId field every
  other request uses; no storage, no schema. The path is also on the per-IP POST rate-limit
  list behind the browser's own limiter.

**Accepted deviation from the ticket text:** the endpoint answers with the standard success
envelope (code 200) rather than a 202. `ApiResponse.success` hardcodes 200, and the envelope
was rebuilt specifically so a body that disagrees with its HTTP line is not expressible; a
202 body saying 200 would have reintroduced exactly that. The reporter reads any 2xx as
"recorded".

## /tags - a directory, not a stub

The page listed bare tag names and nothing else, which is why it read as near-empty. The
backend grew `GET /api/v1/tags/usage` (`selectActiveTagsWithUsage`): every active tag with a
count of the **publicly visible** posts carrying it (the join filters `vibe_post.status =
1`, so a post held in the audit queue never inflates its tag), ordered busiest first — the
page does not re-sort. Cached under the same TTL as the tag list, so a just-created post is
counted no sooner than its tag appears. The page renders each chip with its count badge and
uses a real `EmptyState` when no tags exist yet. A separate endpoint rather than a widened
`TagVo` keeps the create-post picker's shape untouched.

## What this round did not do, and why

**Search filter bar progressive disclosure: already converged.** Reading the current
`SearchPage` showed the filters sit behind a labeled bar that only appears once a keyword or
filter is active, with a single clear button — the P2 from the critique was about the
pre-#27 markup, and restyling it again would be churn with no failure it prevents.

**Terminal-vocabulary tooltips: deferred.** The jargon ("pending-llm", "fail-closed",
"REVIEWING") lives mostly in the AI review panel, where the vocabulary *is* the subject —
the terminal aesthetic is the product's argument, and hovering explanations risk sanding
that off. If a future round wants it, it belongs where a first-time reader actually stalls
(the audit queue page), not app-wide.

## Verification

- Backend: `mvn -B test` — `Tests run: 463, Failures: 0, Errors: 0, Skipped: 0`, 78 classes,
  JaCoCo floor met (run of 2026-11-02).
- New coverage: `CrashReportControllerTest` (one WARN line per report, stack flattening,
  absent-field rendering, size caps, closed `kind` set), `TagControllerTest` (pass-through of
  the backend's ordering, empty directory as data), and eight frontend cases (dedupe, window
  cap, truncation, swallowed fetch failure, synthetic unhandled rejection, resource-noise
  skip, boundary report carrying the component stack).
- Frontend: `npm run test` 50/50 across 13 files; `npm run lint` clean; `npm run build` green.
- **What the tests cannot prove:** the join's `status = 1` filter is asserted by SQL
  inspection, not by an H2 test seeding a pending-review post against a tagged one; and the
  crash report's traceId tagging is exercised through the logback MDC pattern only in a live
  run, because the unit test reads the message, not the pattern.
