# Severity chip on topic rows — the feed shows what the reviewer judged

Status: **done — independent Verifier PASS, 2026-11-19.** Contract:
`plans/review-severity-chip.md` (frozen before implementation). Branch:
`codex/review-severity-chip`, based on merged master `41ea470` (PR #50).
Verified HEAD: `1c0c8ef`; the commit recording this verdict touches this
ticket only. The round first received a formal FAIL (the chip read the
wrong i18n namespace, and the unanchored test assertions could not catch
it); the fix commit `1c0c8ef` closed both findings and the re-review passed.

## Scope

The deferred item from the Observability Forum round ("severity/retry chips on
topic rows … a backend field addition belongs in its own contracted round").
The reviewer's severity verdict (`low` / `high` / `critical`) was already
written to `ai_review_log` on every completed code review and shown on the
post detail page, but never reached the feed row. This round carries that
existing judgment onto the topic row.

Changes:

- One batched query (`AiReviewLogMapper.selectLatestCodeReviewByPostIds`, an
  anti-join like the existing pending-recheck queries) loads the latest
  code-review-agent row per post for the whole page — one query per list, not
  one per row. Exposed through `AiReviewLogRepository.findLatestCodeReview`.
- `PostPageVo` gains `reviewSeverity`; the list conversion in
  `VibePostServiceImpl` fills it from the batched map. Single-post conversion
  is untouched, and the audit path's `safetySeverity` fields keep their
  safety-check-agent semantics — a new field rather than an overloaded one.
- `PostStateBadges` renders a `review-severity-badge` pill next to the AI
  credential: danger tone for `critical`, warning for `high`, muted border
  for `low` — palette tokens that already exist on the page. Unknown values
  render nothing; rows whose review did not complete render nothing.
- i18n `post.reviewSeverity` in zh (低 / 较高 / 严重) and en, read through
  the same namespace the badge row already uses for its other labels.

## Acceptance

- Backend **557 tests**, 0 failures, 0 errors, 0 skipped, JaCoCo floor met
  (was 555); frontend **152 tests / 29 files**, 0 failures (was 149);
  oxlint clean; production build clean.
- New H2 tests: the latest code-review row wins when a post has several, and
  a post with no review row carries null — never a placeholder.
- New `PostCard` tests: each of the three tones, an unknown value rendering
  nothing, and reviewing/failed/not-reviewed rows showing no chip.
- The wire-type contract check needed no edit: it asserts every TS field
  exists on the server DTO, and `reviewSeverity` exists on both sides.
- Note on stability: the first full frontend run during independent
  verification hit one `LoginPage.test.tsx` 5s timeout under the 4-worker
  load; the single-file rerun and a second full run both passed (152/152).
  Pre-existing flake outside this round's diff, recorded rather than
  smoothed over.

## Verification (independent)

An independent Verifier ran the round twice. The first verdict was **FAIL**
with one P1 code finding — the chip called `common:reviewSeverity.*` while
the keys live in the `post` namespace, so both locales rendered raw keys —
plus a test-effectiveness finding: the unanchored `toHaveTextContent('critical')`
assertions were satisfied by the raw keys themselves. The second verdict,
against the fix commit, is **PASS**, no P0/P1 findings, bound to HEAD
`1c0c8ef17b2fb12f06e5a977e842dae279319d97` with `git status --short` empty
before and after. Implementer logs were not accepted as evidence.

| Command (Verifier-run) | Result |
|---|---|
| `mvn -B test` | 557 tests, 0 failures, JaCoCo floor met |
| `npm run test -- --pool=threads --maxWorkers=4` | 152 tests / 29 files, 0 failures |
| `npm run lint` / `npm run build` | clean |
| `git diff 41ea470..HEAD --name-only` | 15 paths, all inside Allowed paths |
| i18next probe on the real locale files | zh: 严重/较高/低 · en: critical/high/low |

All ten contract checks passed on re-review, including the N+1 ban (one
batched query per list) and the honesty rules (unknown values and
not-completed reviews render nothing). The `--maxWorkers=4` flag is the one
recorded deviation, for the environment OOM described above. The verdict
binds to `1c0c8ef`; the bookkeeping commit after it changes this ticket only.

## Files

`plans/review-severity-chip.md`, `docs/tickets/review-severity-chip.md`,
`docs/INDEX.md`, `README.md` (counts only),
`src/main/java/com/nexus/campus/agent/AiReviewLogMapper.java`,
`src/main/java/com/nexus/campus/repository/AiReviewLogRepository.java`,
`src/main/java/com/nexus/campus/repository/impl/MyBatisAiReviewLogRepository.java`,
`src/main/java/com/nexus/campus/dto/PostPageVo.java`,
`src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`,
`src/test/java/com/nexus/campus/service/VibePostServiceImplTest.java`,
`frontend/src/types/post.ts`, `frontend/src/components/PostCard.tsx`,
`frontend/src/components/PostCard.test.tsx`,
`frontend/src/i18n/locales/zh.json`, `frontend/src/i18n/locales/en.json`.

## Rejected

- **A retry/attempt chip**: `ai_review_log` stores no attempt counter, and
  "number of rows" would mix rows from different agents. Nothing honest to
  show.
- **Populating severity inside `convertToPageVo`**: every list path funnels
  through it, so the query would run once per row — the N+1 the contract
  forbids.
- **Reusing the `safetySeverity` field**: it is the safety-check-agent's
  semantics, already filled on the audit path; overloading it would make one
  field lie about which agent spoke.
- **A hover-only tooltip**: judgment you have to hover for is judgment
  visitors do not see — the same reasoning that replaced the hot-ranking
  tooltip with a visible note.
