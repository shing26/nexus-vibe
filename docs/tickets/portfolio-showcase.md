# Portfolio Showcase - A4 and A6

Source: [portfolio-showcase-plan](../plans/portfolio-showcase-plan.md), direction A ("portfolio
artifact"), items A4 (a landing page where a signed-out visitor reads a real AI review) and A6
(a real public deployment carrying it). North star: someone who has never seen this repository
can find what the project is actually about within five minutes, without an account.

Scope of this round: the curated example and the public address that serves it. A5 (three to
five real people completing the loop) and the seven-slot landing-page composition the plan
mentions are **not** in this round; both are recorded under Rejected below rather than left
implicit.

## S1 - The landing page points at one already-reviewed post

Status: done on `codex/showcase-post`. Verified with a signed-out browser against the public
address, not against a local build.

**Scope:** the artifact that carries this project's actual claim - an LLM review that fails
closed, repairs malformed output and writes a score back - was reachable only by registering
and publishing a code block. With `DEMO_SEED_ENABLED=false` in production there was nothing to
look at either, so the strongest evidence this repository has was invisible to the person it
was built for.

- `GET /api/v1/showcase` answers with one post's id, title and score, or `data: null` when the
  deployment has not opted in. It is on the public-GET list in `JwtAuthFilter`, because
  requiring a token would defeat its only purpose.
- `ShowcaseEntry` renders the home-page card and links to whatever id the backend named; it
  renders `null` on absence and on error, so a deployment without the seed is unchanged.
- The gate is `campus.showcase.post-id`. Unset is inert; a typo is inert too, because parsing
  goes through `ShowcasePostId` and returns `null` rather than pointing at post id 0.
- It is deliberately not `DEMO_SEED_ENABLED`, which fills the site with sample users and would
  recreate the ghost-author problem ADR-0008 removed.

**Acceptance:**
- On the deployed stack: home page shows the card, `/post/900000000000000001` renders for an
  unauthenticated visitor, and `/api/v1/showcase` reports score 3.
- The card fits a 390px viewport without horizontal overflow, and the page does not scroll
  sideways.
- Unaffected: every other public page, the `showcase` *channel* (a different thing with a very
  similar name), and the 344-test baseline.

**Files:** `src/main/java/com/nexus/campus/controller/ShowcaseController.java`,
`src/main/java/com/nexus/campus/dto/ShowcasePostVo.java`,
`src/main/java/com/nexus/campus/util/ShowcasePostId.java`,
`src/main/java/com/nexus/campus/security/JwtAuthFilter.java`,
`frontend/src/components/ShowcaseEntry.tsx`, `frontend/src/pages/HomePage.tsx`,
`src/main/resources/application-prod.yml`, `.env.example`,
`src/test/java/com/nexus/campus/controller/ShowcaseControllerTest.java`,
`frontend/src/components/ShowcaseEntry.test.tsx`.

**Rejected:** a second read endpoint that returned the review body inline. The post page already
serves it, and duplicating it would give the landing page its own copy of the pipeline's output
to drift against.

## S2 - The review shown is a recording, and re-recording is one command

Status: done. The first version of this ticket shipped three empty sections; see Rejected.

**Scope:** a visitor is being asked to believe a claim about model output, so the thing on the
page has to be output rather than prose that resembles it.

- The snippet was published once through the ordinary publish path against the deployed stack.
  The comment the pipeline wrote and the `ai_review_log.result_json` row it wrote beside it were
  copied out verbatim into `src/main/resources/showcase/`.
- Score and severity are parsed out of the recorded JSON rather than declared next to it, so the
  card, the comment and the log row cannot drift into disagreeing.
- `benchmark/showcase/record-showcase-review.py` re-runs the whole recording: publish, poll,
  write both resources, print the SQL that clears the three seeded rows so the insert-only
  seeder will write again.
- The seeder writes the post id `campus.showcase.post-id` names rather than the shipped default,
  with the comment and log row derived from it. It used to keep only a boolean and always write
  the default while the read endpoint queried the configured value, so any non-default setting
  seeded one post and advertised another.
- All three inserts share one transaction. Three autocommit statements could leave a post with
  no review, and the already-present check would then treat that half-written state as finished
  on every later start.

**Acceptance:**
- The committed comment blob is byte-identical to the recorded text: 2680 bytes, LF only, no CR,
  pinned by `.gitattributes` so a Windows checkout and a Linux build agree.
- `ShowcasePostSeederTest` fails if any of `codeQuality` / `securityConcerns` /
  `optimizationSuggestions` is blank, if the comment's score and severity disagree with the log
  row, or if the recording stops describing the snippet the post contains.
- A non-default `campus.showcase.post-id` writes that id, and its comment and log row follow it.
- Unaffected: the production seed stays empty, no account is created, and `DEMO_SEED_ENABLED`
  keeps its existing meaning.

**Files:** `src/main/java/com/nexus/campus/config/ShowcasePostSeeder.java`,
`src/main/resources/showcase/recorded-review-comment.md`,
`src/main/resources/showcase/recorded-review-result.json`,
`benchmark/showcase/record-showcase-review.py`, `.gitattributes`,
`src/test/java/com/nexus/campus/config/ShowcasePostSeederTest.java`,
`docs/adr/0010-the-showcase-review-is-a-recording.md`.

**Rejected:** re-running the review at each startup, which makes the most prominent page in the
product fail whenever the LLM is down - the condition everything else is built to survive - and
makes the page different on every deploy. Also rejected: keeping the hand-written fixture and
only fixing its field names. That is what shipped first and it passed, because the only test
compared the fixture against its own author's memory of the schema.

## S3 - Comments render as markdown

Status: done. Found by opening the deployed page as a signed-out visitor, not by a test.

**Scope:** the AI review is displayed twice by design (ADR-0002): the formatted panel and the
comment the agent actually posted. The comment list rendered `{comment.content}` inside a single
`<p>`, so every newline collapsed and the review printed
`## AI Code Review **Overall Score**: 3/10` as literal text in one paragraph.

- Comments go through the same `react-markdown` pipeline the post body already used. This
  applies to every comment, not only the agent's, because the newline collapse was never
  specific to AI output.
- The rendering lives in `CommentBody` so it has a seam a test can hold, rather than being an
  inline expression inside a 600-line page component.

**Acceptance:**
- `CommentBody` renders headings, bold, lists and code, and preserves the separation between
  paragraphs that the previous single `<p>` erased.
- Unaffected: the post body renderer, comment posting and deletion, and the AI review panel
  above the list.

**Files:** `frontend/src/components/CommentBody.tsx`,
`frontend/src/components/CommentBody.test.tsx`, `frontend/src/pages/PostDetailPage.tsx`.

**Rejected:** limiting markdown to AI comments. A user pasting a stack trace hit the same
collapse, and a renderer that behaves differently per author is a second rule to remember for
no gain.

## Rejected for this round

**A5, three to five real people completing the loop.** It is the one item in the plan that
cannot be done by writing code: it needs recruiting, welcoming and waiting, which is roughly
seventy percent non-code work. The honest statement is "no real users", and the plan says so.

**The seven-slot landing-page composition.** The plan's A6 verification sentence describes the
landing page as reusing the 2026-09-15 information-architecture comparison, with
`docs/assets/gui-test-screenshots/` as ready material. What this round actually did is add one
card to the existing home page above the channel grid. That satisfies A4's acceptance clause -
a signed-out visitor reads a real review, with score, severity, verdict and three findings - and
it is what the direction's five-minute test needs, but it is not a rebuilt landing page, and the
plan's status line should not be read as claiming one.

**Moving the funnel meters in front of a visitor.** `funnel_activation_ratio` and
`funnel_active_content_d7_ratio` are the honest measure of whether anyone uses this, but with a
user count of zero they measure nothing. They stay behind the monitoring profile.
