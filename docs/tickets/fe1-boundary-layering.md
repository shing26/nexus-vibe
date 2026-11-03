# FE-1's second half: ErrorBoundary layering, so one broken page no longer takes the SPA down

Source: `docs/tickets/next-cycle-backlog.md` FE-1, whose closing note records the reporting
half as closed (PR #30) and names this half as still unowned; restated by
[frontend-polish-round-2](frontend-polish-round-2.md). The 2026-10-31 audit's finding was
two-part: the boundary had no `componentDidCatch` (fixed), and there was a single boundary
wrapped around the whole router, so one broken page unmounted navigation, theme, and every
other route.

Status: **merged on the codex branch, 2026-11-03** — frontend **52 tests / 13 files**
(was 50/13), oxlint clean, production build green. Backend untouched, no `mvn` run needed.

## Scope

Today `frontend/src/App.tsx` renders one `<ErrorBoundary>` around everything inside
`BrowserRouter`. A render error anywhere — the post page, the admin audit queue, the nav —
replaces the entire application with the fallback screen. The work: decide the layering
granularity and implement it, keeping FE-1's reporting contract intact (every boundary
error reaches `POST /api/v1/crash-reports` with its component stack).

## The decision

**Per-route section boundaries under a layout that outlives them; the single outer
boundary stays as the last resort.** The fallback is not full-page: an inner boundary's
fallback mounts inside `MainLayout`'s `<Outlet />` (or the admin layout's content column),
so a broken page keeps the navbar, the sidebar, the footer, the theme and the toast
container alive, and its own recovery affordances — "Back to Home" navigates the still-live
router, "Retry" remounts just the failed section.

- **Route-level, not app-region-level.** A boundary per *section* of the page (nav, rail,
  content) sounds finer-grained but buys little: those regions are shared chrome rendered
  once in `MainLayout`, not per-route code paths, and they are exactly what we want to
  survive. The per-route code paths are the sixteen lazy page chunks — each is an
  independently deployable bundle, so each is an independently failing unit. A boundary
  there means a broken `/post/:id` never takes `/search` down.
- **The outer boundary stays, deliberately.** React error boundaries catch children's
  errors, not their own; if `MainLayout` itself throws, the per-route boundaries are
  unmounted children and cannot see it. The outermost boundary is the only thing that can
  render full-screen, and that is the correct degradation for a crash above the layer the
  section boundaries live in.
- **Every boundary reports.** No "only report once per tree" logic: dedupe and the
  five-per-window cap are the crash reporter's job (`crashReporter.ts`), and both a
  section and the outer boundary firing for one crash is two capped reports, not a flood.
  The boundary's `componentDidCatch` contract from PR #30 is unchanged.
- **Route declarations, not a wrapper component.** `<ErrorBoundary>` wrapping each
  route's `element` in `App.tsx` is verbose but keeps the layering readable in one file —
  the alternative, a `withErrorBoundary()` HOC or a layout-level boundary around
  `<Outlet />`, hides which sections have boundaries and invites one shared instance
  reused across routes, which cannot reset independently.

## What landed

- `frontend/src/components/ErrorBoundary.tsx`: optional `section` prop. When set, the
  fallback renders section-shaped — compact, with a note naming the failed section ("a
  failure inside the post, not of the whole site") — instead of full-page. Both recovery
  affordances are kept; "Back to Home" resets the boundary state before navigating, so the
  same route remounts fresh.
- `frontend/src/App.tsx`: the single boundary around `ToastContainer` + `Suspense` +
  `Routes` is replaced by per-route boundaries on the fifteen real pages (the `*` NotFound
  route gets none — it is static and must never fail), plus the unchanged outer boundary
  as the last resort. Routes are now also nested under an explicit `path="/"` layout
  element with index/relative paths, matching react-router v7's preferred form.
- `frontend/src/components/ErrorBoundary.test.tsx`: the pre-existing report test gains a
  section-note case, and a new case renders a minimal sketch of `App.tsx`'s structure
  (outer boundary, layout with `Outlet`, per-route section boundary) at `/broken` and
  asserts the section fallback renders while the layout shell stays mounted, the outer
  boundary never fires, and exactly one crash report is sent.

## Verification

- `cd frontend && npm run test` — **52 passed (52)** across 13 files
  (`npx vitest run --maxWorkers=1`; the default parallel pool aborts worker forks on this
  machine, on unmodified master too — see the note below).
- `npm run lint` clean; `npm run build` green (tsc strict, zero `@ts-ignore`).
- **What the tests cannot prove:** the test's layout sketch is a minimal `Outlet` div,
  not `MainLayout` itself, so "the shell survives" is proven for the router structure, not
  for `MainLayout`'s own render; and no test drives a real navigation from a broken route
  through "Back to Home", because jsdom navigation after a thrown render is not something
  the suite models. Both hold by inspection of `App.tsx`, not by assertion.

## Machine note

`npm run test` without flags: worker forks exit with codes 0xC0000409 / 134 during
startup on this Windows machine — reproducible on unmodified master (5245a7d) before any
change here. `--maxWorkers=1` is the workaround; CI is unaffected.

## Rejected

- **A boundary inside `MainLayout` around `<Outlet />` only.** One instance shared by
  every route: an error on `/post/:id` would leave state on the shared instance when you
  navigate to `/search`, and remounting the boundary per navigation to reset it means
  keying it by route anyway — at which point it is a per-route boundary with extra steps
  and less legibility.
- **`withErrorBoundary()` HOC on each page component.** Same behaviour, but the layering
  moves into seventeen separate import sites and stops being visible in one file; the
  route declaration is where a reader already goes to ask "what renders here and what
  catches it".
- **Fine-grained boundaries inside the busiest pages** (post detail's comment list, the
  AI review terminal). Attractive, but each needs its own fallback design and its own test,
  and the audit's finding was about *page-vs-app* blast radius, not widget-vs-page. A
  widget-level round, if one is ever argued, starts from the review terminal.
- **Sentry-style window.onerror replacement / subtree re-render on error.** Out of scope:
  FE-1's reporting half already covers uncaught errors globally, and auto-retry-on-render
  would loop against a deterministic render bug — the manual Retry is the honest affordance.
