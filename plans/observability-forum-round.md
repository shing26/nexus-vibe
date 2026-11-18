# Observability Forum — a quiet, honest community surface

Status: **frozen contract, 2026-11-18.** Base commit `e7dba71`
(`codex/chaos-theater-a2` HEAD). Branch: `codex/observability-forum-round`,
worktree `.worktrees/chaos-theater-a`.

## Goal

The v2 Forge pivot (ADR-0020) made the site professional but invisible: the
project's subject — an LLM-reliability system honestly operating a community —
exists only inside features, not in the visual language. This round makes the
system legible in three small, honest moves, without changing the shell,
palette, or any backend contract:

1. **System pulse** in the sidebar: API liveness plus the real degraded-capability
   list from `GET /api/v1/status/capabilities` (already public, already polled by
   `useCapabilities`). Verdicts come from real data only — no invented queue
   depth or breaker state.
2. **AI credential on topic rows**: a verified state+score chip, replacing the
   bare `AI 85` text; reviewing and failed states also become visible. Only
   fields already on `PostPageVo` (`aiReviewed`, `aiReviewScore`) are encoded.
3. **Human-readable system sentences**: a narrative stats line on the user
   profile (replacing the secondary numbers line), and a per-tab ranking note
   under the home tabs (replacing the hot-only tooltip). Borrowed from the Luzi
   desktop app's best habit: numbers and ranking rules explained in one sentence.

The "My creations" library (Luzi-style Skill/提示词/参考/随手记 with counts) is
**explicitly deferred** to the next round by user decision.

## Non-goals

- No palette change, no new theme, no mascot, no gamification (no levels,
  season progress, badges). Luzi's warm-orange/gamified identity is not borrowed.
- No backend change, no new endpoint, no new npm dependency, no DB/schema work.
- No new animated decoration; v2's "motion communicates state" rule stands.
- The Chaos Theatre stays where it is: `/theatre` is a deep-dive exhibit, not a
  storefront. User verdict: "会的人不想看，不会的人看不懂."

## Allowed paths

- `plans/observability-forum-round.md` (this contract)
- `docs/tickets/observability-forum-round.md`
- `docs/INDEX.md`
- `README.md` (test-count updates only, if any)
- `frontend/src/components/SystemPulse.tsx` (new — carried over from the spike)
- `frontend/src/components/Sidebar.tsx`
- `frontend/src/components/PostCard.tsx`
- `frontend/src/components/PostCard.test.tsx`
- `frontend/src/pages/HomePage.tsx`
- `frontend/src/pages/UserProfilePage.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

New tests for `SystemPulse`, `HomePage`, and `UserProfilePage` may be added only
inside this list's directories (`frontend/src/components/*.test.tsx`,
`frontend/src/pages/*.test.tsx`); no other file may be edited.

## Contract details

### SystemPulse

- Rendered at the bottom of `Sidebar`'s nav, after all other groups; hidden at
  `lg`–`xl` icon-rail widths by the same breakpoint classes as section labels,
  visible in drawer and full-width sidebar.
- Rows: API liveness (from react-query's `isLoading` / `isError` / success of
  `useDegradedCapabilities`) and one amber row per degraded capability, using
  the server's `impact` sentence verbatim (ADR-0007: degraded is not unhealthy;
  never call it an outage). Empty list → one "all capabilities normal" row.
- No queue depth, breaker state, model health, or other values without a real
  source. A comment in the component records why.

### PostCard credential

- `data-testid="ai-credential"`, placed where the `AI 85` text was, inside the
  right-aligned metadata block.
- REVIEWED + score > 0: green check + tabular score + localised "verified"
  caption; `title` carries "score N / 10, verified".
- REVIEWING: amber dot + "reviewing"; FAILED: red dot + "review failed".
  NOT_REVIEWED: renders nothing (as today).
- Existing counts/score assertions are updated to the new structure — the
  narrower `.ml-auto` scope and the credential's own testid — making the test
  stricter, not looser.

### Narrative stats (UserProfilePage)

- Keep the three primary stat cards unchanged. Replace the secondary numbers
  line with one i18n sentence that weaves the same values into prose: posts,
  comments, likes received, forks, versions, average AI score. Numbers use
  `tabular-nums` spans inside the sentence.
- No new data is fetched; the existing `summary.stats` and `avgAiText` feed it.

### Ranking note (HomePage)

- Add one small human-readable sentence directly under the tab row, keyed by
  active tab (`home:tabs.<key>.note` style), stating in plain words what order
  the reader is looking at. The hot note must carry the gravity-decay rule that
  previously lived only in `hotHint`; the tooltip may stay or go, but the note
  is visible without hover.
- No backend ranking change. The note describes existing `changeTab`/query
  parameters (`hot`, `latest`, `sort=ai`, `debug`, `prompts`) truthfully.

## Acceptance criteria

1. The five changes render signed-out where applicable, at 375px and 1280px,
   in both themes, with no console errors.
2. Every displayed state value comes from a real source (capability API,
   react-query status, or existing post/profile fields). No decorative number.
3. i18n keys resolve in both `zh` and `en`; zh is the primary experience.
4. New/updated frontend tests cover the pulse panel (loading, degraded, empty,
   error), the credential states, the narrative stats, and the ranking note.
   No existing assertion is deleted or weakened.
5. `npm run test -- --pool=threads`, `npm run lint`, `npm run build` pass.
   Backend is untouched: no `mvn test` needed for the change itself, but the
   Verifier may run it to confirm zero backend diff.

## Verification commands

```bash
cd frontend && npm run test -- --pool=threads
cd frontend && npm run lint
cd frontend && npm run build
git diff e7dba71..HEAD --stat   # backend/pom/package untouched
```

The independent Verifier re-runs these and binds evidence to HEAD SHA plus
`git status --short`. Implementer logs are not evidence.

## Forbidden

- Editing backend Java, `pom.xml`, `package.json`, or any runtime dependency.
- Inventing operational numbers (queue depth, breaker state, model health)
  that no endpoint provides.
- Changing the Forge palette/tokens, adding gamification, or turning `/theatre`
  into a homepage hero.
- Deleting, skipping, or weakening an existing test or assertion.
- Editing files outside the allowed paths without an amendment.

## Rejected

- **Luzi's warm-orange skin, mascot, season progress and L2 badges.** They are
  the biggest source of its "feel" but would be a borrowed identity, not an
  argument about this project (ADR-0020 chose cool gray + one blue deliberately).
- **A storefront `/theatre` hero.** User verdict: experts skip it, newcomers
  cannot read it; keep it as a deep-dive exhibit.
- **Adding severity/retry chips to topic rows this round.** `PostPageVo` has no
  such fields; inventing colors would break the honesty rule. A backend field
  addition belongs in its own contracted round.
- **"My creations" library this round.** User decision: next round.
