# Frontend polish round 3: what the Luzi observation actually licenses

Source: a 2026-11-09 observation pass over a comparable product (Luzi v0.2.4, a Tauri
"AI asset library + community" desktop app), written up in machine-local
`scratch/luzi-reference-analysis.md`. Nine patterns were filtered as relevant to this
project's subject; this ticket is the arbitration of those nine against the current tree.
It is a **candidate list**, not a plan: [ADR-0009](../adr/0009-scope-is-done-at-deployment.md)
froze the project at deployment, so every item below has to earn its round.

Status: **proposed, 2026-11-09.** Nothing implemented. Each item's "what the tree already
has" line is read from the working tree at `84aff27`, not from the observation notes.

## The filter that produced the list

Luzi's own product is an asset marketplace with an agent-install integration, a
gamification economy (levels, skins, invite tickets) and a multi-end client. None of that
survives contact with this project's subject — "treat the LLM as an unreliable
dependency" — and all of it is excluded before the items below. What was kept is only
the part that is *interface discipline around a state machine*, which is the same
discipline this project's backend already practices.

## Verdicts at a glance

| # | Item | Verdict | Why |
| --- | --- | --- | --- |
| 1 | Show the moderation state to its author | **do first** | The backend has a three-state lifecycle and a four-state AI review; the author can see neither, and `pageByUser` hides their own non-active post outright. A correctness defect wearing polish clothes |
| 2 | Empty-state copy as the first lesson | **do** | `EmptyState` already carries title/desc/action; the cost is copy and per-page override, and the failure it prevents (a blank page that teaches nothing) is already visible on `/tags` and the follow feed |
| 3 | Degradation visible in the product | **do, as a backend slice** | `DependencyHealth.DEGRADED` exists but nothing outside the container reads it: nginx 404s `/actuator/health/deps` on purpose (ADR-0007), so a frontend banner has no source. Needs a small public read model first |
| 4 | "Waiting for an answer" | **do, cheaply** | Derivable from `commentCount = 0` with no schema change; the question is whether a seed-data forum has enough of them to fill a panel |
| 5 | A worked example on prompt posts | **do, if it lands on prompt posts only** | `PromptVersionPanel` and the showcase recording already exist; this is a presentation of data the tree has |
| 6 | Objective "best of channel" | defer | Needs a channel-scoped ordering query; `PostRankingService` is global and time-decayed, not a per-channel all-time list |
| 7 | Three-way comment sentiment | decline | `VibeComment` has no sentiment or type column; this is a schema migration plus a moderation-policy question, for a cosmetic split |
| 8 | Counts on the card | **already done** | `PostCard` renders `viewCount`, `likeCount` and `commentCount` at [PostCard.tsx:210-212](../../frontend/src/components/PostCard.tsx) |
| 9 | Command palette dual-destination | decline | `CommandPalette` has one verb (navigate/execute) and the tree has no second destination — there is no note or asset to "save" to. The pattern needs a product it does not have |

## 1 - Show the moderation state to its author (do first)

**What the tree already has.** `PostStatus` is `ACTIVE(1) / PENDING_REVIEW(2) /
REJECTED(3)` ([PostStatus.java](../../src/main/java/com/nexus/campus/enums/PostStatus.java));
`AiReviewStatus` is `NOT_REVIEWED(0) / REVIEWED(1) / REVIEWING(2) / FAILED(3)`
([AiReviewStatus.java](../../src/main/java/com/nexus/campus/enums/AiReviewStatus.java)).
Both already ride on `PostPageVo` as `status` and `aiReviewed`
([PostPageVo.java:25-26](../../src/main/java/com/nexus/campus/dto/PostPageVo.java)).
`PostCreationService` sets `status` from the publish gate's critical verdict
([PostCreationService.java:65](../../src/main/java/com/nexus/campus/service/impl/PostCreationService.java)).

**The defect.** `MyBatisVibePostRepository.pageByUser` filters `.eq(VibePost::getStatus, 1)`
([MyBatisVibePostRepository.java:75](../../src/main/java/com/nexus/campus/repository/impl/MyBatisVibePostRepository.java)).
A post the gate held for review therefore does not appear anywhere the author can look:
it is absent from the public feed *and* from their own profile, with no statement that it
exists. From the author's side the submit simply did nothing. That is the same failure
mode as a swallowed exception, and it is the one thing on this list that is a bug.

**Scope.** The author sees their own non-active posts, labelled with which state they are
in and, for a rejection, the reason the gate recorded. Nobody else's non-active posts
become visible; the public feed's `status = 1` filter stays exactly as it is.

**Acceptance.**

- A post held by the sensitive-word gate appears on its author's profile with a
  `pending review` label, and `GET /api/v1/posts?userId=<author>` returns it to that
  author only — a second user's identical request returns the same set it returns today.
- A rejected post shows `rejected` plus the gate's reason, and is not rendered as an
  ordinary card with a hidden badge (the label is part of the card, not a tooltip).
- The AI review's four states render distinctly: `REVIEWING` and `FAILED` are not
  collapsed into "not reviewed", because `FAILED` is "awaiting retry" and a reader who
  sees "pending" will wait for something that already gave up.
- An anonymous request to the same endpoint still sees only `ACTIVE` posts, asserted.
- The `status`/`aiReviewed` codes are mapped through one frontend module, not inline
  magic numbers in each page, so a new state cannot render as a blank badge.

**Files.** `src/main/java/com/nexus/campus/repository/VibePostRepository.java` and its
MyBatis implementation, `src/main/java/com/nexus/campus/service/impl/` (the
author-scoped read), `src/main/java/com/nexus/campus/controller/` (the query parameter),
`frontend/src/pages/UserProfilePage.tsx`, `frontend/src/components/PostCard.tsx`,
a new `frontend/src/lib/postStatus.ts`, plus tests on both sides.

**Rejected.** Rendering the status on every card regardless of viewer. It would put an
"active" badge on every post, which is noise, and it would leak the existence of
non-active posts to anyone reading a public list. The label belongs to the author's own
view of their own rows.

## 2 - Empty-state copy as the first lesson (do)

**What the tree already has.** `EmptyState` is a preset map with `title`, `desc`,
`action`, `actionLabel` ([EmptyState.tsx](../../frontend/src/components/EmptyState.tsx)),
and its copy is already terminal-flavoured ("// No drafts yet. Save one from the prompt
studio.").

**Scope.** For each empty surface, the copy states the *mechanism* that would fill it and
names the next action, rather than reporting absence. This is a copy pass with an
override hook, not a component rewrite.

**Acceptance.**

- Every `EmptyState` usage either names the mechanism ("nothing is waiting for an answer
  right now — posts with no replies appear here") or is an action the reader can take now.
- No empty state reads as a dead end: `action: null` survives only where there is
  genuinely nothing to do (`noComments`), and that case says why.
- A preset whose copy would be wrong on a page can override it without forking the
  component.
- Copy stays in the project's voice; no exclamation marks, no "Oops".

**Files.** `frontend/src/components/EmptyState.tsx`, each calling page, and a test that
asserts every preset renders a non-empty `desc`.

**Rejected.** Per-page bespoke empty-state components. The presets are the reason the
copy stays consistent; forking them is how five pages end up with five voices.

## 3 - Degradation visible in the product (do, but it is a backend slice)

**What the tree already has.** `DependencyHealth.DEGRADED` is a real status with the
comment "dependency unavailable, still serving"
([DependencyHealth.java:16](../../src/main/java/com/nexus/campus/config/health/DependencyHealth.java)),
and `HealthSemanticsIntegrationTest` pins the top level reading `DEGRADED` while the
`servable` group reads `UP`. **The frontend has zero references to any health endpoint.**

**The constraint that shapes the scope.** ADR-0007 deliberately split health by audience:
nginx forwards the public `/actuator/health` onto the `servable` group and 404s
`/actuator/health/deps` at the edge. So the honest public surface answers "can users still
use this site", which is exactly the question a banner does *not* need to ask — a banner
that says "servable: UP" is noise. What the banner needs is the *dependency* fact
("search is running on MySQL; results may lag"), and that is deliberately not public
today. This item therefore cannot be a frontend-only round.

**Scope.** A small, public, read-only product status endpoint that exposes the one fact a
visitor can act on — which optional capability is degraded and what it means for them —
plus a dismissible banner that renders it and links to the relevant ADR. Not the raw
`deps` document; a shaped read model with a closed vocabulary.

**Acceptance.**

- With Elasticsearch stopped, the endpoint reports search-degraded and the banner names
  the consequence ("search results may be incomplete"), not the component name.
- With everything up, the endpoint reports nothing to say and the banner does not render.
- The endpoint never exposes `db` internals, dependency hostnames, or any value from
  `deps` that ADR-0007 keeps closed; asserted by a test that fails if a new field is added
  without a decision.
- Dismissal is per-session and does not hide a *new* degradation.
- `/actuator/health` and `/actuator/health/servable` keep their current answers byte for
  byte — this adds a surface, it does not re-point one.

**Files.** `src/main/java/com/nexus/campus/config/health/`, a new controller in
`src/main/java/com/nexus/campus/controller/`, `docker/nginx/nginx.conf` (the new path must
be reachable where the old actuator paths stay closed), `frontend/src/components/`, the
layout that hosts the banner.

**Rejected.** Pointing the banner at the public `/actuator/health`. It answers
servability, which is `UP` during exactly the outage the banner exists to explain — a
banner that says "fine" while search is broken is worse than no banner. Also rejected:
opening `/actuator/health/deps` at the edge, which is the decision ADR-0007 already made
in the other direction.

## 4 - "Waiting for an answer" (do, cheaply)

**What the tree already has.** `commentCount` on every post, and `PostRankingService`
already reads it ([PostRankingService.java:189](../../src/main/java/com/nexus/campus/service/PostRankingService.java)).
No query lists unanswered posts.

**Scope.** A short list of recent active posts with `commentCount = 0`, surfaced where a
reader is already deciding what to open (the channel page or the home sidebar), capped at
a handful so it does not become a second feed.

**Acceptance.**

- The list contains only `status = 1` posts with zero comments, newest first, capped.
- A post leaves the list the moment it receives a comment (no cache longer than the post
  list's own TTL).
- The empty case is handled by item 2's copy, not by hiding the panel.
- The panel is absent on mobile where it would push the feed below the fold.

**Files.** `src/main/java/com/nexus/campus/repository/` + service + controller,
`frontend/src/components/Sidebar.tsx` or the channel page.

**Rejected.** A dedicated `/unanswered` route. At this community's size a whole page for a
handful of rows is a page that is empty most of the time.

## 5 - A worked example on prompt posts (do, if it lands on prompt posts only)

**What the tree already has.** `PromptVersionPanel` for version history,
`ShowcaseEntry` and `DemoShowcaseController` for the recorded showcase, and prompt posts
carry `promptMetadata`.

**Scope.** On a prompt post, a block that shows one input and the output it produced, with
a copy button for the prompt itself. Data the tree already holds; presentation only.

**Acceptance.**

- The example renders from stored data, not a hardcoded string in the component.
- A prompt post with no example renders nothing extra — no empty frame.
- The copy button copies the prompt, not the example output, and its label says which.
- The block does not appear on ordinary discussion posts.

**Files.** `frontend/src/pages/PostDetailPage.tsx`,
`frontend/src/components/PromptVersionPanel.tsx`, the prompt metadata shape.

**Rejected.** A general "usage examples" field on every post type. The showcase recording
is this project's argument; a free-text example box on a discussion post invites the same
content with less structure.

## 6 - Objective "best of channel" (defer)

`PostRankingService` is a global, time-decayed score; a per-channel all-time
"most liked" list is a different query and a different decision about whether likes are
comparable across channels. Deferred until something asks for it. Recorded here so the
next round does not re-derive the distinction.

## 7 - Three-way comment sentiment (decline)

`VibeComment` has `id, postId, userId, parentId, targetId, content, status, createTime`
([VibeComment.java](../../src/main/java/com/nexus/campus/entity/VibeComment.java)) — no
sentiment, no type. A "praise / complaint / question" split is a schema migration plus a
policy question (who classifies, and what happens when the classifier is the LLM that is
allowed to be unavailable). That is not a frontend polish item, and the value is a
cosmetic tab split over a comment list that is currently short. Declined, with the reason
written down rather than left implicit.

## 8 - Counts on the card (already done)

`PostCard` renders `viewCount`, `likeCount` and `commentCount` with icons at
[PostCard.tsx:210-212](../../frontend/src/components/PostCard.tsx). Nothing to do; listed
so the observation note does not read as an outstanding gap.

## 9 - Command palette dual-destination (decline)

`CommandPalette` binds `Enter` to run the selected row
([CommandPalette.tsx:195](../../frontend/src/components/CommandPalette.tsx)). Luzi's
pattern pairs "search" with "save a note" in one box because it has a notes product behind
it. This project has no second destination for the same keystroke, so the modifier would
either do nothing or invent a feature to justify itself. Declined.

## Recommended order

1. **Item 1** — it is a defect, it is small, and it is the only item whose absence a user
   experiences as the product silently failing.
2. **Item 2** — cheap, and it is the prerequisite for item 4's empty case.
3. **Item 4** — small once item 2 has set the copy.
4. **Item 5** — only if the prompt-post metadata can carry the example without a schema
   change; otherwise defer it to the same round as item 6.
5. **Item 3** — last, because it is a backend slice with an nginx edge change and an ADR
   interaction, and because it must not be mistaken for a frontend-only round.

## What this ticket cannot claim

- The observation pass that produced these nine items was one afternoon over one product;
  it establishes that the patterns exist, not that they are right for this project.
- Items 1, 4 and 5 depend on read paths that are asserted here by reading the repository
  code, not by running a query against seeded data.
- No item here has been implemented, so no count in `README.md` moves yet.
