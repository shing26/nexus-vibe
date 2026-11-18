# My Creations — the owner's personal library of posts, prompts, forks and drafts

Status: **done, awaiting independent verification.** Contract:
`plans/my-creations-library.md` (frozen before implementation). Branch:
`codex/my-creations`, based on merged master `f5171b5` (PR #49).

## Scope

The deferred item from the Observability Forum round: Luzi's "我的" group is a
library of the reader's own assets, not a link dump. Nexus-Vibe had the raw
material scattered across "我的帖子" and "草稿". This round unifies it into a
single authenticated `/creations` surface with real counts and an honest
boundary between published server rows and device-local drafts.

Changes:

- New protected route `/creations` (`MyCreationsPage.tsx`) behind the existing
  `AuthGuard`, wrapped in an `ErrorBoundary` like the other protected pages.
- Kinds derived from real existing fields only — `postType` and `forkedFromId`
  split the owner's `/posts?userId=X` rows into article / original prompt /
  plaza fork; the existing `nexus_vibe_drafts` localStorage key supplies drafts.
  No new endpoint, no invented count: each tab's chip equals the number of
  cards under it.
- Cards link to real destinations (`/post/:id`, `/post/new?draft=:id`); drafts
  carry a visible "on this device only" marker; a bounded-window hint appears
  at 100 rows rather than a fabricated total.
- Sidebar "我的" group gains a "创作物库" entry; the drawer shares it via the
  existing `Sidebar` reuse. Existing entries stay.
- New i18n `creations` namespace in zh and en; zh is primary.

## Acceptance

- Frontend **149 tests / 29 files**, 0 failures (was 145/28); oxlint clean;
  production build clean; `tsc --noEmit` clean.
- New `MyCreationsPage.test.tsx` covers kind derivation from a mixed list,
  count chips matching cards, tab filtering, draft-to-edit links, device-local
  marking, prose empty states, and the error/retry path.
- No backend change (`git diff f5171b5..HEAD --stat` is frontend + docs only);
  no new dependency, no schema change, no inventing counts/scores/timestamps.
- Note on the verification command: the full suite needs
  `npm run test -- --pool=threads --maxWorkers=4` on this machine; the default
  25 workers intermittently exhausts the Node heap or fails to spawn threads
  workers while the dev server is running (environment fault, not a code
  failure). The narrower run and the single-file run are both green.

## Files

`plans/my-creations-library.md`, `docs/tickets/my-creations-library.md`,
`docs/INDEX.md`, `README.md` (counts only), `frontend/src/App.tsx`,
`frontend/src/components/Sidebar.tsx`,
`frontend/src/pages/MyCreationsPage.tsx` (new),
`frontend/src/pages/MyCreationsPage.test.tsx` (new),
`frontend/src/i18n/locales/zh.json`, `frontend/src/i18n/locales/en.json`.

## Rejected

- **A new `/api/v1/creations` aggregate endpoint**: exact totals beyond the
  100-row window, but a backend surface, tests and release dependency for a
  portfolio UI; the page states the window instead of lying.
- **Reusing the public `/user/:id` profile**: its stats derive differently and
  mixing owner-private drafts into it would leak state.
- **Local-only simulated data for a richer look**: every card comes from a real
  post row or a real localStorage draft.
- **Gamification / Luzi skin**: no level, badge, orange or mascot (ADR-0020).
