# Observability Forum — make the honest system legible in the community UI

Status: **done — Verifier PASS (2026-11-18, HEAD `4677e8c`, clean tree).** Contract:
`plans/observability-forum-round.md` (frozen before implementation). Branch:
`codex/observability-forum-round`, stacked on `codex/chaos-theater-a2`.
An independent Verifier re-ran all commands: 145 frontend tests / 28 files,
oxlint and build clean, `git diff e7dba71..HEAD` touching only allowed
frontend+docs paths, and the live signed-out `GET /api/v1/status/capabilities`
returning the real closed-vocabulary list (`search`, `ai-review`) that the
system pulse consumes. All five criteria and every hard prohibition passed.
Merge still requires written user sign-off.

## Scope

ADR-0020's Forge pivot made the site professional but invisible: the subject —
an LLM-reliability system honestly operating a community — lived only inside
features. This round gives it a quiet visual voice without touching the shell,
palette, or any backend contract. The user's verdict on the alternative — a
theatre-first storefront — was "会的人不想看，不会的人看不懂", so `/theatre`
stays a deep-dive exhibit.

Borrowed selectively from the Luzi desktop app (inspected live, `D:\Luzi`):
the habit of explaining numbers and ranking rules in one human sentence.
Deliberately **not** borrowed: its warm-orange skin, mascot, season-progress
bar and L2 badges — those would be an identity, not an argument (ADR-0020).

Changes:

1. **System pulse** in the sidebar bottom (`SystemPulse.tsx`): API liveness
   plus one amber row per real degraded capability from the existing public
   `GET /api/v1/status/capabilities`, via the existing `useCapabilities` hook.
   No invented queue depth or breaker state; empty list reads "all normal";
   a failed probe reads "cannot reach the server".
2. **AI credential on topic rows** (`PostCard.tsx`): green check + tabular
   score + "verified" for completed reviews; amber "reviewing" and red
   "review failed" states. Only existing `PostPageVo` fields are encoded.
3. **Narrative stats** (`UserProfilePage.tsx`): the secondary numbers line is
   replaced by one i18n sentence weaving posts/comments/likes/reviewed/avg-AI/
   forks/versions as bold numbers, via `Trans`. Same data, no new query.
4. **Ranking note** (`HomePage.tsx`): one plain, visible sentence under the
   tab row, keyed per tab, describing the real query order (gravity-decay hot,
   newest-first, AI-score-sorted, Debug channel, prompt type). The old
   hover-only hot tooltip was not enough.

## Acceptance

- Frontend **145 tests / 28 files**, 0 failures (was 140/25); oxlint clean;
  production build clean; `tsc --noEmit` clean.
- New tests: `SystemPulse` (degraded / all-normal / broken API),
  `HomePage.rankingNote` (visible note follows tab changes),
  `UserProfilePage.narrative` (bold numbers woven into one sentence).
  `PostCard`'s counts/score assertions were narrowed to the credential's own
  testid — stricter, not weakened.
- Backend, `pom.xml`, `package.json` untouched: no new dependency, no schema
  change, no new endpoint. Every displayed value has a real source.

## Files

`plans/observability-forum-round.md`, `docs/tickets/observability-forum-round.md`,
`docs/INDEX.md`, `README.md` (counts only),
`frontend/src/components/SystemPulse.tsx` (new),
`frontend/src/components/SystemPulse.test.tsx` (new),
`frontend/src/components/Sidebar.tsx`, `frontend/src/components/PostCard.tsx`,
`frontend/src/components/PostCard.test.tsx`,
`frontend/src/pages/HomePage.tsx`, `frontend/src/pages/HomePage.rankingNote.test.tsx` (new),
`frontend/src/pages/UserProfilePage.tsx`, `frontend/src/pages/UserProfilePage.narrative.test.tsx` (new),
`frontend/src/i18n/locales/zh.json`, `frontend/src/i18n/locales/en.json`.

## Rejected

- **Luzi's warm-orange skin, mascot, season progress and L2 badges**: the
  biggest source of its feel but a borrowed identity, not an argument about
  this project (ADR-0020 chose cool gray + one blue deliberately).
- **A theatre-first storefront**: user verdict "会的人不想看，不会的人看不懂";
  `/theatre` remains a deep-dive exhibit.
- **Severity/retry chips on topic rows**: `PostPageVo` has no such fields;
  inventing colors would break the honesty rule. A backend field addition
  belongs in its own contracted round.
- **"My creations" library** (Luzi-style Skill/提示词/参考/随手记 with counts):
  user decision — deferred to the next round.
