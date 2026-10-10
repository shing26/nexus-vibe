# Review Terminal Replay - the evidence a screenshot can carry

Source: this round. The north star is the one `portfolio-showcase-plan` set for direction A:
someone who has never seen this repository can say within five minutes what it is about, and
wants to keep asking. The post-detail page already held the project's strongest artifact - a
finished LLM review with a score, severity, verdict and findings - and rendered it as a flat,
static card. A stranger scrolled past the one thing worth stopping for.

Scope of this round: two visual/interaction surfaces and one navigation change. No backend
work, no new endpoint, no change to the terminal's state contract. The constraint that shapes
all of it is the project's own discipline: a claim without a reproducible path does not go in.
The panel renders a stored review, so it may show that review in any style, and it may not
claim the pipeline is working on it now - the false claim ADR-0010 removed from the landing
card, one surface down.

## R1 - The finished review replays its steps

Status: done on `codex/review-terminal-replay`. Verified in a browser against the built SPA with
the API mocked, at 1440x900 and 390x844, including `prefers-reduced-motion`.

**Scope:** the data state of `AiReviewTerminal` reveals the review it already holds: four steps
light in order, each confirming with a check as its content arrives, and the score counts itself
in at the end. The ritual a screenshot can carry, on a run that actually finished.

- Steps are the three stored sections plus the score: Code Quality, Security Concerns,
  Optimization Suggestions, Score Evaluation. Each of the first three names a field of
  `AiReviewDetail`; the fourth is the writeback. They are the reading order of the stored
  review - not a claim about the order the pipeline ran in, because the three findings arrive
  in one JSON from one model call.
- The header carries `// replay of completed run · <reviewedAt>`, the run's own recorded time.
  The words "recording" and "live" are both avoided: the showcase row is a recording, an
  ordinary post's row is simply a finished run, and neither is happening now.
- Steps advance every 500ms; the score counts in over 12 ticks of 50ms once the last step
  confirms. The whole reveal lands in about 2.6 seconds and plays once per mount - the polling
  has stopped by the time the terminal exists, so a refetch does not restart it.
- Content is in the DOM from the first render; a section's dimness before its step is a reveal
  for the eye, not a fetch, so tests and assistive technology read the finished review either
  way. Under `prefers-reduced-motion` the reveal collapses to the finished state.
- The strip is a shared component, `ui/ReviewStepStrip.tsx`, with the step list in
  `ui/reviewSteps.ts`; both R1 and R2 use it so the two states cannot drift apart.

**Acceptance:**
- Browser pass over the built SPA (`vite preview`, API mocked) at 1440x900 and 390x844: the
  four steps confirm in order and the score lands on the value the header already claimed,
  with no horizontal overflow and no console errors. **The check against the deployed stack
  has not run yet** - the stack was down during this round; re-run it on the public address
  the next time the machine is up.
- `vitest` asserts the order, the reduced-motion collapse, the score scaling, and that the
  caption never reads as a live claim (`not.toHaveTextContent(/live/i)`).
- The pre-existing assertions still hold: `Score: 85/100` from a stored 8.5, `// No findings.`
  for an empty section, the unavailable and error states untouched.
- A 390px viewport shows the strip without horizontal overflow; the page does not scroll
  sideways.

**Files:** `frontend/src/components/AiReviewTerminal.tsx`,
`frontend/src/components/ui/ReviewStepStrip.tsx`, `frontend/src/components/ui/reviewSteps.ts`,
`frontend/src/components/AiReviewTerminal.test.tsx`,
`frontend/src/components/ReducedMotionTerminal.test.tsx`.

**Rejected:**
- **Naming the backend's real stages** (extract code blocks, call the model, repair and
  validate, write back). It sounds more like a working agent, and it is the one framing the
  UI cannot back: whether a given row ever took the repair path is not in `AiReviewDetail`,
  and a step that may not have happened is not a step you may show as complete.
- **Calling the panel a "recording" everywhere.** It is true of the showcase row and false of
  an ordinary post's row; "replay of completed run" is true of both.
- **Animating indefinitely or restarting on refetch.** A review that never stops replaying is
  theatre; one that restarts on poll is a lie about freshness.

## R2 - The reviewing box shows an indeterminate step strip

Status: done on `codex/review-terminal-replay`.

**Scope:** the one state where the pipeline really is in flight - `aiReviewed === 2` - had a
single line of text. It now shows the same four steps with exactly one spinner cycling and
**nothing ever confirmed**, because the UI knows only REVIEWING, not which stage the worker is
standing in. The caption states the mechanism: polling every 5s. The strip is
`aria-hidden` because a live region that re-announces four labels every 1.4s is worse than the
line it would talk over; the steps are read for real once the replay lands.

**Acceptance:** with a post mocked to `aiReviewed: 2`, the box shows one active step and zero
confirmed ones at any moment; `role="status"` and `aria-live="polite"` are on the container.

**Files:** `frontend/src/pages/PostDetailPage.tsx`.

**Rejected:** confirming steps as the strip cycles. There is no evidence any of them finished,
and `AiReviewReconcileTask` exists precisely because a review in flight can stall.

## R3 - Evidence links out of the SPA

Status: done on `codex/review-terminal-replay`.

**Scope:** the repository's own evidence was reachable only by finding the GitHub repo first.
The navbar (`lg` and wider) and the footer (every width) now link out to the ADR index and the
load-test report, and the footer to the observability drill as well. One `ui/ExternalLink.tsx`
carries `target`/`rel` so no surface can add an outbound link and forget them.

- Targets are the `master` tree on GitHub: `tree/master/docs/adr` (fifteen ADRs) and
  `blob/master/docs/research/async-pool-loadtest.md`, plus the drill. The repo is public, and
  these pages exist whatever this machine is doing.
- **Not** `/actuator/health` on the demo. It answers `{"status":"UP"}` over the `servable`
  group with `show-details: never` - one word wide, database only - and when the home machine
  is asleep the URL is an ngrok error page, which reads as a broken project to the exact
  visitor these links are for. Grafana and Prometheus have no public port at all.

**Acceptance:** `NavLinks.test.tsx` asserts the three hrefs, `target="_blank"` and
`rel="noopener noreferrer"` from the navbar and the footer.

**Files:** `frontend/src/links.ts`, `frontend/src/components/ui/ExternalLink.tsx`,
`frontend/src/components/Navbar.tsx`, `frontend/src/components/Footer.tsx`,
`frontend/src/components/NavLinks.test.tsx`.

**Rejected:**
- **A live status link.** See above; the honest version of it 404s on a sleeping machine.
- **Linking the navbar's existing "Agent Active" pill to anything.** The pill is decorative and
  not wired to a health query; this round adds links, it does not launder existing claims.

## Verification

`cd frontend && npm run test` reports **42 tests** (was 33), `npm run lint` is clean, and
`npm run build` succeeds; the README badge and Testing section were updated in the same
commit. The backend is untouched: `mvn -B test` still reports 417 tests. A browser pass over
`vite preview` with the API mocked checked both viewports for horizontal overflow and console
errors (none), and screenshots of the early, mid and final frames plus the running strip.

## Known follow-up

`docs/assets/showcase/recorded-review.png` in the README predates the step strip: it shows the
terminal without it. The stack was down during this round, so the screenshot could not be
re-shot; recapture it from `/post/900000000000000001` the next time the stack is up, and keep
the caption's wording ("a recorded run") exactly as it is.
