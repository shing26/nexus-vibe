# fe-3-group-c — frozen contract: worked example on prompt posts, three-way comment sentiment

Status: **frozen, 2026-11-09.** Base commit `1ce682a`. Ticket:
[frontend-polish-round-3](../tickets/frontend-polish-round-3.md), items 5 and 7.

Both items live on the post-detail surface and both add a field to something already
stored, so one writer holds them.

## Item 5 — a worked example on prompt posts

`PostDetailPage` already renders a playground from `promptMetadata` (variables, rendered
prompt, token estimate, a copy button —
[PostDetailPage.tsx:230-269](../../frontend/src/pages/PostDetailPage.tsx)). `promptMetadata`
is a free-form JSON string written by `CreatePostPage` as
`{ role, recommendedModel, temperature, variables }`
([CreatePostPage.tsx:325](../../frontend/src/pages/CreatePostPage.tsx)). There is no field
for an example.

**Scope.** Two new optional keys in that JSON — `exampleInput` and `exampleOutput` — filled
from two new fields on the prompt-post form, rendered as one input/output block beside the
playground. No schema change: the column is already `text`.

**Acceptance.**
- A5-1: The example renders from the stored `promptMetadata`, not from a literal in the
  component. A test changes the stored value and asserts the rendered text follows.
- A5-2: A prompt post with neither key renders nothing extra — no empty frame, no
  placeholder box. Asserted.
- A5-3: The copy button in the example block copies the **prompt** (the rendered prompt,
  consistent with the playground's existing button), and its label names that; a separate
  control, if any, copies the input, and its label names that. Asserted by asserting the
  clipboard payload, not the label alone.
- A5-4: The block does not render on a post whose `postType` is not the prompt type.
  Asserted.
- A5-5: Existing `promptMetadata` values without the new keys parse and render exactly as
  today; the JSON is read defensively (the existing `try/catch` shape stays).
- A5-6: The create/edit form round-trips both fields: creating with an example, then
  editing the post, shows the example still there.

## Item 7 — three-way comment sentiment

The ticket declined this because "who classifies" was unanswered, and the obvious answer —
the LLM — is the dependency this project is allowed to lose. This round answers it: **a
deterministic rule classifier in Java, no model call, and the split is labelled as a
heuristic in the UI.**

**Scope.** `vibe_comment` gains a `sentiment` tinyint (`0` unclassified/other, `1` question,
`2` complaint, `3` praise). Classification happens once, in the comment write path, from the
comment text. Comments already in the database stay `0` until they are edited — a backfill
is out of scope and `0` must render sensibly.

**Acceptance.**
- A7-1: The migration pair exists: `docker/mysql/migrate-0009-add-comment-sentiment.sql`
  and `docker/mysql/rollback-0009-drop-comment-sentiment.sql`, in the style of 0008
  (header naming the ADR/ticket and the one-time `docker compose exec` command). `init.sql`
  carries the column for fresh volumes, `schema.sql` carries it for H2, and
  `VibeComment` carries the field.
- A7-2: The up → down → up rehearsal passes via
  `benchmark/migrations/rehearse-migrations.ps1`, and the rehearsal output is committed
  under `benchmark/migrations/results/` the way the 0008 run was. If the rehearsal cannot
  run on this machine, say so in those words and leave the previous results untouched —
  do not fabricate a result file.
- A7-3: The classifier is a pure function with no network, no model, no repository access.
  Unit-tested for: a question (ends with `?` or contains an interrogative), a complaint
  (negative cue words), praise (positive cue words), and the tie/empty case falling to `0`.
  A mixed comment that scores on two cues resolves by a documented precedence, and the
  test pins it.
- A7-4: Classification never blocks or fails the write. If it throws, the comment is still
  saved with `sentiment = 0`. Asserted with a test that injects a throwing classifier.
- A7-5: `GET /api/v1/comments/post/{postId}` returns `sentiment` on each row; the comment
  list can filter by it client-side without a second request, and the filter control is
  labelled as a heuristic ("grouped by keyword", or equivalent) so a reader does not take
  it for a model's judgement.
- A7-6: A comment with `sentiment = 0` — every comment that existed before this change —
  appears under an "all" view and is never silently dropped from a filtered view it
  matches. Asserted.
- A7-7: Existing comment tests pass unmodified; the `status` field's meaning is unchanged.

## Allowed paths

- `frontend/src/pages/PostDetailPage.tsx`
- `frontend/src/pages/CreatePostPage.tsx`, `frontend/src/pages/EditPostPage.tsx` — the two
  example fields only
- `frontend/src/components/PromptVersionPanel.tsx` — only if the example block belongs
  inside it; prefer a sibling block
- `frontend/src/components/` — one new comment-filter or example component + tests
- `frontend/src/types/post.ts`, `frontend/src/types/comment.ts` — the new optional fields
- `src/main/java/com/nexus/campus/entity/VibeComment.java`
- `src/main/java/com/nexus/campus/dto/CommentVo.java`
- `src/main/java/com/nexus/campus/service/` + `service/impl/` — comment write path
- `src/main/java/com/nexus/campus/controller/CommentController.java`
- `src/main/java/com/nexus/campus/enums/` — one new `CommentSentiment` enum
- `src/main/resources/schema.sql`
- `docker/mysql/init.sql`, `docker/mysql/migrate-0009-*.sql`, `docker/mysql/rollback-0009-*.sql`
- `benchmark/migrations/results/**`
- `src/test/java/com/nexus/campus/**`
- **`README.md` is not yours.** Four groups run at once and the count badge is one line;
  the integrator updates it once after the merge. Report your counts instead.

Item 2's empty-state copy on `PostDetailPage.tsx` is also yours, because you hold that
file: `EmptyState` usage on that page gets the same mechanism-naming copy as the rest of
the round (see [group A](fe-3-group-a-feed-copy.md), A2-1 to A2-4). Do not edit
`EmptyState.tsx` itself — group A owns it; just pass a page-local override if the preset's
copy does not fit.

## Forbidden

- Any LLM call, network call or Spring bean dependency inside the sentiment classifier.
- Backfilling existing rows, or any `UPDATE` that touches comments outside the write path.
- Making comment creation fail, or making it depend on the classifier succeeding.
- Changing the comment `status` semantics, or the post-visible-then-checked order ADR-0017
  established.
- Weakening, deleting or skipping an existing assertion; disabling lint or typecheck.
- Pushing, opening a PR, or editing this file. **Do commit** on your own branch: the
  acceptance chain binds evidence to a HEAD SHA, and an uncommitted tree has none.
- Fabricating a rehearsal result. An unrun rehearsal is reported as unrun.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test && npm run lint && npm run build
powershell -File benchmark/migrations/rehearse-migrations.ps1
```

## Rejected alternatives

- **An LLM sentiment classifier.** This project's whole subject is that the LLM may be
  unavailable; a cosmetic tab split must not be the thing that goes dark when it is.
- **Deriving sentiment at read time.** It would re-classify every comment on every request
  and make the value depend on the deployment's code version rather than the data.
- **A free-text `usageExample` on every post type (item 5).** The showcase recording is this
  project's argument; a free-text box on a discussion post invites the same content with
  less structure.
