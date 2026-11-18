# My Creations — a personal library of what the reader has made

Status: **frozen contract, 2026-11-18.** Base commit `f5171b5`
(merged master, PR #49). Branch: `codex/my-creations`,
worktree `.worktrees/chaos-theater-a`.

## Goal

Luzi's "我的" group is not a link dump: it is a library of the reader's own
assets (Skill / 提示词 / 参考 / 随手记), each with a count. Nexus-Vibe already
has the raw material — posts, prompt templates and their forks, and local
drafts — but scatters it across "我的帖子" and "草稿". This round unifies it
into one honest creation library at `/creations`, with real counts and an
honest boundary between what is published and what only lives on this device.

Deferred item from the Observability Forum round (user decision). This round
makes the community feel like a place where one *makes* things, not just reads.

## Non-goals

- No backend change, no new endpoint, no schema change, no new dependency.
- No gamification (no level/badge/season), no palette/token change.
- No pretending local drafts are server-persisted; no invented counts.
- No change to the public `/user/:id` profile — this is the owner's private
  library.

## Data semantics (all from existing sources)

The page fetches `GET /api/v1/posts?userId=<me>&size=100&page=1` (the existing
owner-view query — the JWT filter grants the owner held rows) and reads local
drafts from the existing `nexus_vibe_drafts` localStorage key (same as
`DraftsPage`). From these, three creation kinds are derived:

| Kind | Derivation | Count source |
|---|---|---|
| `post` | `postType != 'prompt'` | rows in the fetched list |
| `prompt` | `postType == 'prompt' && forkedFromId == null` (original templates) | rows in the fetched list |
| `fork` | `postType == 'prompt' && forkedFromId != null` (from the plaza) | rows in the fetched list |
| `draft` | localStorage drafts | `drafts.length`, labelled "on this device" |

The fetch size 100 is a bounded display window, not a claim of completeness;
if the list is full the page shows a quiet "showing the most recent 100" hint
rather than an invented total. `summary.stats` is NOT fetched here: the
category counts come from the same rows the page lists, so the number on a
tab always equals the number of cards under it.

Each card links to the real destination: posts/prompts → `/post/:id`, forks →
`/post/:id`, drafts → `/post/new?draft=:id` (the existing draft-edit route).

## Allowed paths

- `plans/my-creations-library.md` (this contract)
- `docs/tickets/my-creations-library.md`
- `docs/INDEX.md`
- `README.md` (test-count updates only)
- `frontend/src/App.tsx` (one protected route)
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/SidebarDrawer.tsx` (reuses the same Sidebar; edit only if needed)
- `frontend/src/pages/MyCreationsPage.tsx` (new)
- `frontend/src/pages/MyCreationsPage.test.tsx` (new)
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

New tests may be added only inside these paths. Any other file requires an
amendment before it is edited.

## UI contract

1. `/creations` is an authenticated route (inside the existing `AuthGuard`
   group) with an `ErrorBoundary` like the other protected pages.
2. Page header: title, one-line plain subtitle, and a primary "new post"
   action to `/post/new` (existing editor).
3. A category tab row (全部 / 文章 / 提示词 / Fork / 草稿) with a real count
   chip per tab, derived from the rows above. "全部" shows all server rows plus
   local drafts; drafts carry a visible "仅本机" marker.
4. Cards use the Forge vocabulary: 1px border, no glow, sans text, mono only
   for counts. Each card shows title, a one-line preview, the kind label, and
   one honest metadata line (score if reviewed, or time). Local drafts show a
   "stored on this device" note instead of pretending to have a server score.
5. Empty states are i18n'd prose per category (not ASCII art), with a
   "create the first one" action where meaningful.
6. The sidebar "我的" group gains a "创作物库" entry (with an aggregate count
   chip if cheap and honest) and keeps the existing posts/drafts/settings
   entries. Drawer and full sidebar share this via the existing `Sidebar`.
7. Works at 375px and 1280px, both themes; `prefers-reduced-motion` honoured
   (no new animation beyond existing press feedback). Keyboard-operable tabs.

## Acceptance criteria

1. Signed-in `/creations` renders, lists the derived kinds with counts that
   equal the number of cards under the active tab, and every card links to a
   real destination.
2. Signed-out access is redirected by the existing `AuthGuard` (no new auth
   logic).
3. Drafts are visibly marked as device-local; no count or score is invented.
4. New frontend tests cover: kind derivation from a mixed post list, count
   chips matching cards, tab switching, empty states, draft-local marking, and
   error/loading paths. No existing test is deleted or weakened.
5. i18n keys resolve in both zh and en; zh is primary.
6. `npm run test -- --pool=threads`, `npm run lint`, `npm run build` pass.
   README counts updated in the same change if they change.

## Verification commands

```bash
cd frontend && npm run test -- --pool=threads
cd frontend && npm run lint
cd frontend && npm run build
git diff f5171b5..HEAD --stat   # frontend + docs only
```

The independent Verifier re-runs these and binds evidence to HEAD SHA plus
`git status --short`. Implementer logs are not evidence.

## Forbidden

- Backend Java, `pom.xml`, `package.json`, schema, or any new dependency.
- Inventing counts, scores, or timestamps; presenting localStorage drafts as
  server-persisted.
- Gamification, palette/token changes, or borrowing Luzi's orange/mascot.
- Editing public `/user/:id` profile behavior.
- Deleting, skipping, or weakening existing tests.
- Editing files outside the allowed paths without an amendment.

## Rejected

- **A new `/api/v1/creations` endpoint that aggregates the kinds server-side.**
  It would give exact totals beyond the 100-row window, but it adds a backend
  surface, tests and a release dependency for a portfolio UI. The library's
  claim is "what you made", satisfied by the existing owner-view post list;
  if a reader has >100 posts, the page says so instead of lying.
- **Reusing `/user/:id` profile as the library.** It is public and its stats
  derive differently; mixing owner-private drafts into it would leak state.
- **Local-only simulated data for a richer look.** Every card comes from a
  real post row or a real localStorage draft; no fixtures in the UI.
