# fe-3-post-status-visibility — frozen contract

Status: **frozen, 2026-11-09.** Base commit `1ce682a`. Ticket:
[frontend-polish-round-3](../tickets/frontend-polish-round-3.md), item 1.

This file is the contract. It is frozen before implementation; changing it requires a new
decision written into this file, not a silent edit during the work.

## The defect being closed

`MyBatisVibePostRepository.pageByUser` filters `.eq(VibePost::getStatus, 1)`
([line 75](../../src/main/java/com/nexus/campus/repository/impl/MyBatisVibePostRepository.java)).
A post the publish gate or the safety check holds in `PENDING_REVIEW(2)` or moves to
`REJECTED(3)` is absent from the public feed **and** from its author's own profile, and
nothing in the product says it exists. The author experiences a silent no-op.

## Scope

The author sees their own non-active posts, labelled with their state, on their own
profile. Nobody else gains visibility. Public feeds are untouched.

## Allowed paths

Implementation may modify only:

- `src/main/java/com/nexus/campus/repository/VibePostRepository.java`
- `src/main/java/com/nexus/campus/repository/impl/MyBatisVibePostRepository.java`
- `src/main/java/com/nexus/campus/service/VibePostService.java`
- `src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`
- `src/main/java/com/nexus/campus/controller/PostController.java`
- `src/main/java/com/nexus/campus/dto/PostPageVo.java`
- `src/test/java/com/nexus/campus/**` (new or extended tests only)
- `frontend/src/lib/postStatus.ts` (new)
- `frontend/src/lib/postStatus.test.ts` (new)
- `frontend/src/components/PostCard.tsx`
- `frontend/src/pages/UserProfilePage.tsx`
- `frontend/src/types/**` or `frontend/src/api/types.ts` — only if `PostPageVo`'s frontend
  type needs the new nullable field
- `README.md` — only the test-count badge and Testing section, and only if counts move

## Forbidden

- Any schema or migration change. `vibe_post` has no reason column; the rejection reason
  is derived from the existing `ai_review_log` row.
- Touching the `status = 1` filter on any other read path (feed, category, search, hot,
  ranking).
- Adding a cache to `getPostsByUserId`, or any caching keyed on `userId` alone. The author
  view and the public view must not be able to share a cached page.
- Returning another user's non-active posts under any request shape, including a
  hand-crafted `userId` on the public list.
- Changing, deleting or weakening an existing assertion to make the suite pass; skipping a
  test without recording it; disabling lint or typecheck.
- Committing, pushing, or opening a pull request.
- Editing this contract file.

## Acceptance criteria

Each line is checkable by a command or a test, not by reading intent.

**A1 — owner sees held posts.** `GET /api/v1/posts?userId=<A>` authenticated as A returns
A's posts with `status` 1, 2 and 3. `PENDING_REVIEW` and `REJECTED` rows are present, not
filtered out.

**A2 — nobody else does.** The same URL, anonymous or authenticated as B, returns only
`status = 1` rows. B's result is byte-identical to today's result for the same
`page`/`size`.

**A3 — the reason travels with the rejection.** A `REJECTED` row in the owner's view
carries a non-null `rejectionReason` derived from the latest `ai_review_log` row for that
post (the existing `classifySafetyResult` vocabulary: `Prompt injection`, `Harmful
content`, `Spam`, `Safe`). A `PENDING_REVIEW` row may carry null; it must not carry a
reason borrowed from a different post.

**A4 — no reason leaks.** `rejectionReason` is null for every row in a non-owner's
response, and null for `status = 1` rows in every response.

**A5 — the four AI review states are distinct.** The frontend maps `aiReviewed` 0/1/2/3 to
four distinguishable labels through one module; `FAILED(3)` does not render as
`NOT_REVIEWED(0)`. The mapping is unit-tested for all four codes and for an unknown code
(falls back to a visible "unknown", never blank).

**A6 — public surfaces unchanged.** Existing tests for feed, category, search and hot
listing pass unchanged. No new field appears in those responses except the nullable
`rejectionReason`, which is null there.

**A7 — the card is unchanged for everyone else.** `PostCard` without the new optional prop
renders exactly as today; its existing tests pass without modification.

**A8 — pagination is honest.** `total` in the owner's view counts the non-active rows it
returns, so page 2 is reachable when the author has more held posts than `size`.

## Verification commands

Run from the repository root, in the implementation worktree:

```bash
mvn -B test
cd frontend && npm run test && npm run lint && npm run build
```

`mvn test`'s summary line is the count; `target/surefire-reports/` accumulates and lies.
If a count moved, the README badge and Testing section move in the same change (AGENTS.md).

The implementer reports what it ran and what it could not prove. The verifier re-runs all
four commands in a fresh session and does not accept the implementer's transcript as
evidence.

## Design notes that are decisions, not suggestions

- **Owner detection is the request attribute.** `JwtAuthFilter` fills `currentUserId` for
  authenticated callers and, on public GETs, best-effort when a valid token is present
  ([JwtAuthFilter.java:51-59](../../src/main/java/com/nexus/campus/security/JwtAuthFilter.java)).
  `PostController` therefore compares `userId` against that attribute. No new auth path.
- **The reason is read, not stored.** One extra lookup per non-active row, via the
  existing `AiReviewLogRepository.findLatestSafety`, and only on the owner path. Storing a
  reason column is a schema change and is out of scope.
- **The label lives on the card, not in a tooltip.** A held post must not render as an
  ordinary card with the state hidden behind a hover; the state is the point of the row.

## Rejected alternatives

- **A separate `/posts/mine` endpoint.** It would need its own auth contract and leave
  `?userId=` inconsistent with it. One parameter with an ownership check keeps one read
  path.
- **Showing the status to everyone.** An "active" badge on every card is noise, and it
  would leak the existence of held posts to public readers.
- **A stored `reject_reason` column.** Correct long-term, out of scope here, and it would
  need a migration rehearsal (`benchmark/migrations/`) to be honest about it.
