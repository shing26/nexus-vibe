# Severity chip on topic rows — the feed shows what the reviewer judged

Status: **frozen contract, 2026-11-19.** Base commit `41ea470`
(merged master, PR #50). Branch: `codex/review-severity-chip`,
worktree `.worktrees/next-slice`.

## Goal

The deferred item from the Observability Forum round ("severity/retry chips
on topic rows … a backend field addition belongs in its own contracted
round"). A reader scanning the feed sees the AI credential chip (score,
reviewing, failed) but not *what the reviewer judged*: the severity verdict
that `AbstractLlmReviewer` produces on every completed code review
(`low` / `high` / `critical`) is written to `ai_review_log` and shown on the
post detail page, yet never reaches the row in the list. This round carries
that existing judgment onto the topic row — real data, one batched query, no
new surface.

This sharpens the thesis in the place most visitors actually look: the feed
itself becomes evidence that every post is judged and the judgment is
granular, not a pass/fail stamp.

## Data semantics (existing rows only)

- Source: the post's latest `ai_review_log` row with
  `reviewer = 'code-review-agent'` (the same row family that produces
  `aiReviewed` / `aiReviewScore` on `PostPageVo` today). Severity values are
  exactly what the reviewer writes: `low`, `high`, `critical`.
- Transport: a new `reviewSeverity` field on `PostPageVo`, populated for list
  views in one batched query (the page's post ids → latest row per post, an
  anti-join like the existing pending-recheck queries). Single-post
  conversion (`convertToPageVo`) stays untouched — the detail page already
  shows the full verdict through the review terminal.
- Display rule: the chip renders only when `aiReviewed == REVIEWED` and
  `reviewSeverity` is one of the three known values. An unknown value renders
  nothing rather than guessing a tone.
- No retry chip: nothing in the schema counts review attempts, and inventing
  a number is exactly what the honesty rule forbids. Recorded in Rejected.

## Allowed paths

- `plans/review-severity-chip.md` (this contract)
- `docs/tickets/review-severity-chip.md`
- `docs/INDEX.md`
- `README.md` (test-count updates only)
- `src/main/java/com/nexus/campus/agent/AiReviewLogMapper.java` (batched query)
- `src/main/java/com/nexus/campus/repository/AiReviewLogRepository.java`
- `src/main/java/com/nexus/campus/repository/impl/MyBatisAiReviewLogRepository.java`
- `src/main/java/com/nexus/campus/dto/PostPageVo.java` (one field)
- `src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`
- `src/test/java/com/nexus/campus/service/VibePostServiceImplTest.java`
- `src/test/java/com/nexus/campus/contract/FrontendWireTypeContractTest.java`
  (only if the wire-type scan requires the new field be registered)
- `frontend/src/types/post.ts`
- `frontend/src/components/PostCard.tsx`
- `frontend/src/components/PostCard.test.tsx`
- `frontend/src/i18n/locales/zh.json`
- `frontend/src/i18n/locales/en.json`

Anything else requires an amendment before it is edited.

## UI contract

1. The severity chip sits in `PostStateBadges`, after the existing AI
   credential text: a small rounded pill, `data-testid="review-severity-badge"`.
2. Tones map honestly to the verdict: `critical` uses the existing danger
   tone, `high` the warning tone, `low` the muted border tone — all drawn
   from the palette already on the page (`vibe-danger`, `vibe-warning`,
   `vibe-border`, `vibe-card`), no new colors.
3. Labels are i18n'd (`creations`-style, zh primary): 低 / 较高 / 严重; en:
   low / high / critical. The label alone carries the meaning — color is
   never the only signal.
4. Rows whose review did not complete (`reviewing` / `failed` /
   not-reviewed) or whose severity is null/unknown show no chip at all — no
   placeholder, no skeleton.
5. `FrontendWireTypeContractTest`'s coverage of `PostPageVo` fields, if it
   enumerates them, is extended rather than weakened.

## Acceptance criteria

1. A feed listing (`getPosts` hot/new and the other `convertToPageVos`
   paths) carries `reviewSeverity` for rows with a completed code review,
   and null for rows without one.
2. The batched query is exercised against H2: latest row wins when a post has
   several review logs, posts with no review log come back absent, and the
   query is called once per page, not once per row.
3. `PostCard` renders the chip only under the display rule above; tests
   cover present (each of the three tones), unknown-value-hides, and
   reviewing/failed rows showing no chip.
4. No existing test is deleted, skipped, or weakened.
5. `mvn -B test`, `npm run test -- --pool=threads`, `npm run lint`,
   `npm run build` pass; README counts updated in the same change.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff 41ea470..HEAD --stat
```

The independent Verifier re-runs these and binds evidence to HEAD SHA plus
`git status --short`. Implementer logs are not evidence.

## Forbidden

- Inventing severity, retry counts, or any value with no stored row behind it.
- Rendering severity for rows whose review did not complete.
- New palette tokens, gamification, or borrowing Luzi's skin (ADR-0020).
- Changing the single-post detail conversion, the review pipeline, or the
  safety-check rows' semantics (the audit path's `safetySeverity` fields stay
  exactly as they are).
- N+1 severity queries: one batched query per page load, or the round fails
  review even if tests pass.

## Rejected

- **A retry/attempt chip.** `ai_review_log` stores no attempt counter, and
  "number of rows" would include rows written by different agents. Nothing
  honest to show; the round ships without it.
- **Populating severity inside `convertToPageVo` (single-post conversion).**
  Every list path funnels through it, so the query would run once per row —
  the N+1 the contract forbids — and the detail page does not need it.
- **Reusing the `safetySeverity` field.** It is the safety-check-agent's
  semantics, already filled on the audit path; overloading it with the
  code-review verdict would make one field lie about which agent spoke.
- **A hover-only tooltip.** The same reasoning that replaced the hot-ranking
  tooltip with a visible note in the observability round: judgment you have
  to hover for is judgment visitors do not see.
