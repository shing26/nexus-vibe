# Round-3 acceptance evidence

Round [frontend-polish-round-3](../tickets/frontend-polish-round-3.md), nine items, five
groups. Base commit `1ce682a`; integration branch `codex/fe3-integration`.

**Status: verified, awaiting the maintainer's four-question review and signoff.** Every row
is bound to a HEAD SHA and was re-run by a verifier that did not write the code.

## Method

Each group got a frozen contract in `docs/plans/` before any code was written: allowed
paths, acceptance lines, verification commands, and what was forbidden. An implementer
worked in its own `git worktree`; a separate `verifier` subagent — Read/Grep/Glob/Bash
only — re-ran every verification command itself. Implementer logs and self-assessment are
not evidence and were not accepted as such. A verdict below is a verifier's, not the
implementers'.

Several contracts could not survive contact with the tree. They were amended with the
reason written into the file (`5853f8a`); each amendment turns an unsatisfiable demand into
a checkable one and none lowers a criterion. They are listed under
[Contract amendments](#contract-amendments).

## Verdicts

| Item | Group | Branch | HEAD | Verdict | Backend | Frontend |
|---|---|---|---|---|---|---|
| 1 | — | `codex/fe3-post-status-visibility` | `d29874d` | **PASS** | 474 / 81 classes | 61 / 15 files |
| 3 | B | `codex/fe3-group-b-degradation` | `0ed9c7c` | **PASS** | 487 | 58 / 14 |
| 5, 7 | C | `codex/fe3-group-c-detail` | `50a7928` | **PASS** | 490 | 68 / 15 |
| 9 | D | `codex/fe3-group-d-palette` | `9a5cc6f` | **PASS** | unchanged | 68 / 14 |
| 2, 4, 6, 8 | A | `codex/fe3-group-a-feed-copy` | `7d64eef` | **PASS** | 482 / 82 | 77 / 17 |

On the merged tree all five branches together read **529 Java tests / 90 classes** and
**115 frontend tests / 21 files**, 0 failures, 0 errors, 0 skipped, with the JaCoCo floor
met — the same four commands, run by the integrator on the merge result rather than on any
one branch. That number is the sum of the branches' deltas (465 + 9 + 22 + 25 + 8), which is
the check that no branch silently displaced another's tests.

Every verdict above rests on the same four commands, re-run by the verifier in the
worktree: `mvn -B test`, and `npm run test && npm run lint && npm run build` in
`frontend/`. The `mvn` summary line is the count; `target/surefire-reports/` accumulates
runs and was not used.

## Item evidence

### Item 1 — the author sees their own held posts

A1 owner listing carries statuses 1/2/3; A2 anonymous and other-user listings return only
status 1 with the same rows, statuses and page metadata; A3 a rejected row carries its own
reason read from the latest safety log; A4 no reason reaches a non-owner or an active row;
A5 the four AI-review codes map distinctly through one module with a visible unknown
fallback; A6 public surfaces unchanged; A7 the card without the owner prop renders the
base commit's markup; A8 pagination totals count held rows.

The A7 proof was audited rather than accepted: the verifier reconstructed
`PostCard.tsx` from `1ce682a` into a scratch directory outside the repo, rendered the same
four fixtures against it, and got the inline `BASE_MARKUP` strings to match 4/4; a
negative control that changed one token made 2/4 fail. The comparison is not vacuous.

### Item 3 — degradation visible in the product

B1 `GET /api/v1/status/capabilities` reports `search` when Elasticsearch is down and an
empty list when everything is up, against the real endpoint; B2 the DTO has exactly
`{key, impact}` and the HTTP test rejects hosts, ports, URLs, stack traces and
dependency-shaped keys; B3 the anonymous GET passes the real `JwtAuthFilter` chain while
the same chain still 401s `/api/v1/users/profile`; B4 the banner renders the server's
`impact` sentence and links ADR-0007; B5 dismissal is per capability in `sessionStorage`;
B6 `/actuator/health` and `/actuator/health/servable` are pinned byte for byte with the
servable group `UP` while the top level is `DEGRADED`; B7 a config-reading test parses
`nginx.conf` and proves `/actuator/health/deps` resolves to `return 404;` and that no other
actuator location exists.

### Items 5 and 7 — worked example, comment sentiment

A5-1..A5-5 the example renders from stored `promptMetadata`, renders nothing when absent,
copies the rendered prompt (asserted on the clipboard payload), is absent on non-prompt
types, and leaves existing metadata parsing unchanged. A5-6 is proven at the two points the
round trip can break: a helper round-trip test, and a source-level assertion that both pages
call those helpers. The verifier deleted `mergeWorkedExample` from `EditPostPage.tsx` and
confirmed the scan goes red at the call site and nowhere else, then restored the blob
byte for byte.

A7-1 the migration pair exists in 0008's style and `init.sql`, `schema.sql` and
`VibeComment` agree on `sentiment tinyint NOT NULL DEFAULT 0`; A7-3 the classifier imports
no framework, network, model or repository and its precedence is pinned by test; A7-4 a
throwing classifier still persists the comment with `sentiment = 0` and `status = 1`;
A7-5 the API returns `sentiment` and the filter is labelled in visible text as a keyword
heuristic; A7-6 `sentiment = 0` rows appear under "All" and are never silently dropped;
A7-7 every pre-existing comment test is byte-identical and the `status` meaning is
unchanged.

### Item 9 — the palette's second destination

D1..D7: post and channel rows declare a secondary "Copy link"; `Ctrl`/`Meta`+Enter runs it
and falls back to the primary action when absent; the copied value is the absolute URL,
asserted on the clipboard payload; copying closes the palette and reports through the
existing toast store; the footer hint tracks the selected row rather than the mere presence
of a secondary row in the list; keyboard navigation, focus trap and restoration are
unchanged.

The D5 test was the round's first real defect: the original action-row case filtered every
secondary-bearing row out of the list, so it could not distinguish "the selected row has
one" from "any row has one", and a mutation that changed the condition survived. The test
was rewritten to hold both kinds of row at once; the same mutation now fails it.

### Items 2, 4, 6 and 8 — empty-state copy, unanswered panel, per-channel best-of, counts

A2-1..A2-4 every preset's title and description are non-empty, iterated from the preset map
(`EmptyState.presetKeys` is `Object.keys(presets)`, so a preset added later cannot escape the
test), the check rejects both a short description and a long restatement of absence,
`noComments` keeps no action and says the reader can be first, and no preset copy contains an
exclamation mark or "Oops". A4-1..A4-3 `/api/v1/posts/unanswered` returns only active,
zero-comment posts newest first, defaults to 5 and caps at 20, is public through the real
filter chain, returns an empty list rather than an error, and a post leaves the list once a
comment is created in the same test context. A4-4 and A6-4 are source-level pins, as
amended; the verifier made each go red independently — changing `768px` to `640px`, and
removing the `isDesktop &&` gate, each failed only the intended test, and the tree was
restored byte for byte. A6-1 the `liked` ordering is a total order (like count, then creation
time, then id) proven across three pages with a held row excluded; A6-2 a `sort=liked`
without a channel or category is a 400 naming both; A6-3 the existing sorts and their tests
are untouched; A8-1 the card's three counts render from the post object and follow a
re-render, with the four item-1 fixtures untouched.

## What this round does not prove

This section is the point of the document. Each line is a claim the evidence does not
support, kept here so a reader does not infer it.

- **No browser pass anywhere in the round.** Every frontend claim is jsdom/Vitest plus
  `tsc` and `vite build`. Responsive layout, the degradation banner, the unanswered panel
  and the sort toggle were not observed in a running browser.
- **Item 1, A3:** "the latest safety log" is `ORDER BY created_at DESC LIMIT 1` in
  `AiReviewLogMapper` — code-inspected, with one safety log per fixture. The
  `Prompt injection` and `Safe` branches are implemented but not exercised on a rejected
  row. A rejected row with no safety log renders `rejectionReason: null`; the contract sets
  no fallback.
- **Item 1, dead prop:** `PostCard` gained an optional `showStatus` prop that no production
  caller passes — the profile page renders `PostStateBadges` directly. It is covered by
  tests but is dead code.
- **Item 3, B1/B2:** the exclusion of `db` from the vocabulary is proven at the mapping
  level, not by an HTTP request against a genuinely degraded database.
- **Item 3, B7:** `NginxEdgeSurfaceTest` deliberately skips regex locations because the
  fixture URIs contain no dot. That is valid for the two regexes in the shipped file; it is
  not a complete nginx matcher, and a future regex location could evade it while nginx
  would apply it.
- **Item 3, B3 and probe cost:** GETs are unthrottled today (`RateLimitInterceptor` returns
  true for non-POST and is not registered on this path) — established from source, not by
  an HTTP assertion. Each anonymous call evaluates the `deps` group, so the endpoint is a
  probe-amplification path. No limiter or cache was added; the contract forbade inventing
  one.
- **Items 5/7, rehearsal:** the `up → down → up` rehearsal **did not run**. PowerShell 5.1
  cannot bind the script's `$PSScriptRoot` default and the Docker daemon returns 500.
  `benchmark/migrations/results/` is byte-identical to base and no result file was
  fabricated — and the script only rehearses `0005/0006/0007`, so it never covered `0008`
  either. A successful run elsewhere would still not exercise `0009`. The 0008 round
  carried the same gap.
- **Items 5/7, classifier behaviour:** the keyword rule produces false positives the tests
  do not cover — `"This is it, the docs are great."` reads as a question (`is it`),
  `"I have you covered."` as a question (`have you`), `"What a great build."` as a question
  (`what`), and `"没有问题"` as a complaint (`问题` inside a negation). The UI labels the
  split as a heuristic; its accuracy is not claimed.
- **Items 5/7, A7-5:** "no second request" is structural — the filter component imports no
  API client and receives rows as props. No test asserts network silence.
- **Items 2/4/6/8, runtime:** the unanswered panel's behaviour below `md` and the sort
  toggle surviving a refresh are source-level pins on the wiring, not observed in a browser.
- **Items 2/4/6/8, A4-1 floor:** `size=0` and a negative `size` clamp to 1. The contract sets
  a default and a maximum, not a minimum, and no committed test covers the floor.
- **Items 2/4/6/8, A6-1:** the id tie-break is exercised by rows 302/303 *within* page 2; no
  tie straddles a page boundary. Pagination's no-repeat property is proven for the fixture
  and follows from the total order, not from a boundary-crossing tie.
- **Items 2/4/6/8, A2-2:** the mechanism-word check is gameable in general — the verifier
  constructed an 80-character sentence naming no mechanism that passes it. The committed
  copy is genuine; the check is a floor, not a proof of the copy's quality.
- **Items 2/4/6/8, A2-4:** asserted for preset copy only; a page-level override was read by
  hand, not tested.

## Contract amendments

Frozen contracts are the acceptance standard, so a defect in one is recorded rather than
worked around silently (`5853f8a`):

- **item 1** forbade committing — an uncommitted tree has no HEAD SHA to bind evidence to,
  and A7's proof had no file it was allowed to live in. Committing is now required;
  `PostCard.test.tsx` is granted for A7; A2's "byte-identical" became the behaviour it
  meant, because A6 permits a new nullable field.
- **item 3** required a public endpoint while the file deciding publicness
  (`JwtAuthFilter.isPublicGet`) was out of scope. One prefix was granted.
- **items 5/7** said "one new component" for a group holding two items, and A5-6 asked for
  a page mount no group could own.
- **items 2/4/6/8** asked A4-4 and A6-4 for runtime assertions on a page with no test
  surface in the round, and allowed the repository interface and its MyBatis implementation
  but not `VibePostMapper.java`, where those implementations' `@Select` SQL actually lives.
  The two new queries and the selects beside them were granted (`21641d1`).

## Four-question review

To be completed by the maintainer before merge; the graded answers are part of the record,
not a formality.

1. **Does it do what was asked?**
2. **Is it correct?**
3. **What could go wrong?**
4. **What did we not do, and why?**

Signoff: pending.
