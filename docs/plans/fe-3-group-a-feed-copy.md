# fe-3-group-a — frozen contract: empty-state copy, unanswered panel, per-channel best-of, counts

Status: **frozen, 2026-11-09.** Base commit `1ce682a`. Ticket:
[frontend-polish-round-3](../tickets/frontend-polish-round-3.md), items 2, 4, 6 and 8.

These four are one group because they share `ChannelPage.tsx` and `EmptyState.tsx`. Two
writers on those files would conflict; one writer holds all four.

**This group starts only after `fe-3-post-status-visibility` (item 1) has landed.** Item 1
holds `PostController`, `VibePostService(Impl)`, `VibePostRepository`, `MyBatisVibePostRepository`,
`PostCard.tsx` and `UserProfilePage.tsx`; three of those are item 4's read path and two are
item 2's copy surface. Running both at once would put two writers on the same five files.
Item 1 is implemented in `../nexus-fe3`; this contract's base commit becomes that branch's
tip, not `1ce682a`, when the integrator merges it.

## Item 2 — empty-state copy as the first lesson

`EmptyState` is a preset map (`noPosts`, `noResults`, `noComments`, `noActivity`,
`notFound`) with `title`, `desc`, `action`, `actionLabel`
([EmptyState.tsx](../../frontend/src/components/EmptyState.tsx)). Nine call sites across
`AgentLogsPage`, `ChannelPage`, `HomePage`, `PostDetailPage`, `SearchPage`, `TagsPage`,
`UserProfilePage`.

**Scope.** Every preset's `desc` names the mechanism that would fill the surface, not the
absence. `action: null` survives only where there is genuinely nothing to do, and that
case says why. A caller can override any field without forking the component (the
`{...emptyGuide}` spread on `ChannelPage` already does this — keep that shape).

**Acceptance.**
- A2-1: Every preset renders a non-empty `desc` and a non-empty `title`; asserted by a
  test that iterates the exported preset keys, so a preset added later without copy fails.
- A2-2: No `desc` is a bare restatement of absence ("No posts yet." alone fails); each
  names what would appear and how it gets there. Asserted by a copy test that rejects a
  desc shorter than a threshold **and** one that does not contain a mechanism word — keep
  the check honest rather than brittle.
- A2-3: `noComments` keeps `action: null` and its desc explains that the reader can be the
  first, which is the action.
- A2-4: Copy contains no exclamation marks and no "Oops"; asserted.

## Item 4 — "waiting for an answer"

**Scope.** A capped list of recent `status = 1` posts with `commentCount = 0`, surfaced on
`ChannelPage` beside the feed. No new route.

**Acceptance.**
- A4-1: `GET /api/v1/posts/unanswered?size=N` returns only `status = 1` posts with
  `commentCount = 0`, newest first, capped at N (default 5, hard max 20).
- A4-2: The endpoint is public like the feed and returns an empty list rather than an error
  when there are none.
- A4-3: A post leaves the list once it receives a comment; asserted by a test that seeds a
  zero-comment post, adds a comment, and re-queries.
- A4-4: The panel renders the item-2 empty copy when the list is empty, and is not rendered
  below the `md` breakpoint. *Amended 2026-11-09:* mounting the page would need the router
  and query providers this group does not own, so the checkable form is a **source-level
  assertion in `ChannelPage.test.tsx`**: the empty branch renders the `EmptyState` preset
  carrying the item-2 copy, and the panel is mounted only under the `useIsDesktop` gate
  whose media query is `(min-width: 768px)`. Runtime responsive behaviour stays
  observed-by-inspection — say so in the report rather than implying a browser pass.

## Item 6 — objective per-channel "best of"

`PostRankingService` is a global, time-decayed score
([PostRankingService.java:189](../../src/main/java/com/nexus/campus/service/PostRankingService.java)).
The ticket deferred this; the round now takes it on.

**Scope.** A per-channel all-time "most liked" ordering, exposed as an explicit `sort`
value rather than a new endpoint, so it composes with the existing channel filter.

**Acceptance.**
- A6-1: `GET /api/v1/posts?channelSlug=X&sort=liked` returns that channel's `status = 1`
  posts ordered by `likeCount` descending, ties broken by `createTime` descending, then
  `id` descending — a total order, so pagination cannot repeat or skip a row.
- A6-2: `sort=liked` without a channel or category is rejected with a 400 whose message
  names the requirement, not silently treated as `latest`.
- A6-3: Existing `sort` values (`latest`, and whatever `filterPosts` already accepts) keep
  their current behaviour; their tests pass unmodified.
- A6-4: The channel page offers the two orderings as a visible toggle, and the choice is in
  the URL so it survives a refresh and can be linked. *Amended 2026-11-09:* same reason as
  A4-4 — asserted by a source-level check in `ChannelPage.test.tsx` that `sort` is read from
  `useSearchParams`, written back through its setter, and included in the query key, plus
  that both ordering options are rendered by the toggle. A mounted-page test is not
  required; the limit is stated in the report.

## Item 8 — counts on the card (verify, do not rebuild)

`PostCard` already renders `viewCount`, `likeCount`, `commentCount`
([PostCard.tsx:210-212](../../frontend/src/components/PostCard.tsx)).

**Acceptance.**
- A8-1: A test asserts all three counts render from the post object, so a future refactor
  cannot silently drop one. No production code change is expected for this item; if the
  test reveals a real gap, report it rather than widening scope.

## Allowed paths

- `frontend/src/components/EmptyState.tsx`, `frontend/src/components/EmptyState.test.tsx` (new)
- `frontend/src/components/PostCard.test.tsx` (extended — it exists from item 1)
- `frontend/src/pages/ChannelPage.test.tsx` (new) — **granted 2026-11-09 for A4-4 and A6-4
  only.** It carries source-level assertions about `ChannelPage`'s wiring, not a mounted
  page; the page's providers belong to no group in this round. Do not mount the page, and do
  not move another page's tests here.
- `frontend/src/pages/ChannelPage.tsx`, `frontend/src/pages/HomePage.tsx`,
  `frontend/src/pages/AgentLogsPage.tsx`, `frontend/src/pages/SearchPage.tsx`,
  `frontend/src/pages/TagsPage.tsx`, `frontend/src/pages/UserProfilePage.tsx`
  — **copy only** on the last four; no logic edits
- **`PostDetailPage.tsx` is not yours.** Group C holds that file for item 5; its
  empty-state copy is item 2's and group C applies it. Do not edit it, and do not edit
  `CreatePostPage.tsx` or `EditPostPage.tsx` either.
- The unanswered panel goes on **`ChannelPage.tsx`**, not `Sidebar.tsx`. `Sidebar.tsx` is
  not yours; group B's banner host decision is separate and neither group touches it.
- `src/main/java/com/nexus/campus/repository/VibePostRepository.java` + its MyBatis impl
- `src/main/java/com/nexus/campus/service/VibePostService.java` + impl
- `src/main/java/com/nexus/campus/controller/PostController.java`
- `src/test/java/com/nexus/campus/**`
- **`README.md` is not yours.** Four groups run at once and the count badge is one line;
  the integrator updates it once after the merge. Report your counts instead.

## Forbidden

- Any schema or migration change.
- Changing the `status = 1` filter on the feed, search, hot or category read paths.
- Weakening, deleting or skipping an existing assertion; disabling lint or typecheck.
- Pushing, opening a PR, or editing this file. **Do commit** on your own branch: the
  acceptance chain binds evidence to a HEAD SHA, and an uncommitted tree has none.
- Rebuilding `PostCard`'s counts for item 8 — they exist; assert them.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test && npm run lint && npm run build
```

## Rejected alternatives

- **A dedicated `/unanswered` route for item 4.** A whole page that is empty most of the
  time at this community's size.
- **A new `/posts/best` endpoint for item 6.** It would duplicate the channel filter and
  the visibility rule; a `sort` value composes with what exists.
- **Sorting "best" by the time-decayed `PostRankingService` score.** That score answers
  "hot now", which the feed already offers; the point of this item is an all-time,
  objective ordering a reader can verify by eye.
