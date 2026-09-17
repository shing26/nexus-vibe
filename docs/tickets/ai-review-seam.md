# The review pipeline as a replaceable part - AI-1 to AI-4

Source: the reuse assessment of 2026-09-17, which asked whether someone forking this
repository could point it at a different subject. The answer was "the forum in the middle, yes;
the review pipeline, only by editing core files". The decision that follows is recorded in
[ADR-0012](../adr/0012-the-review-pipeline-is-pluggable-at-two-seams.md): two seams are
published, and the pipeline's guarantees are deliberately not among them.

Scope of this round: make the two seams real, give them defaults that reproduce today's
behaviour exactly, and document them well enough that a fork needs the README rather than the
source. Not in this round, and recorded under Rejected below: removing the AI at the data-model
level, an interface around `LlmClient`, and the four-class safety taxonomy.

Order: AI-1 -> AI-2 -> AI-3 -> AI-4. AI-1 and AI-2 are independent of each other; AI-3 needs
both, because it documents them.

## AI-1 - One place decides whether a post is reviewed

Status: open. The rule "posts containing fenced code blocks get reviewed" is currently stated
three times, in three wordings, in two packages:

| Where | How it is worded |
|---|---|
| `AiReviewEventListener.handleAiReviewEvent` | `aiReviewService.detectCodeBlocks(content).isEmpty()` → skip |
| `VibePostServiceImpl.updatePost` | `contentChanged && aiReviewEnabled && status == 1 && !"prompt".equals(postType)` |
| `VibePostServiceImpl.createPost` | `if (aiReviewEnabled)` before publishing either event |

The third row is also the defect worth fixing in the same ticket: the safety event is published
under the *review* flag (`if (aiReviewEnabled && post.getStatus() == 1)`), so
`campus.ai.review.enabled=false` silently switches off the LLM safety check too. Two switches
exist and are not independent.

**Scope:** extract `ReviewPolicy` as a bean, with a default implementation that is exactly
today's rule, and make all three sites ask it instead of restating it.

- `ReviewPolicy` takes the content plus the small amount of post context the rule currently
  reads (`postType`, `status`), and answers a boolean. Deliberately not a decision *record*: a
  fork that wants a reason attached to its own rule can log it, and core has no use for it.
- default bean `CodeBlockReviewPolicy`, `@ConditionalOnMissingBean`, wrapping the existing
  `detectCodeBlocks` so the default behaviour cannot drift from today's.
- the listener stops calling `detectCodeBlocks` and asks the policy; `updatePost` stops
  restating the rule and asks the policy; `createPost` publishes both events, each under its
  own flag.
- the safety check gets its own gate that does not read the review flag. This changes
  behaviour, so it is asserted in both directions rather than assumed.
- `.env.example` gains both flags with their real property names and one line each on what
  turning them off means. Today neither appears on the env surface at all, which is how a
  reader ends up running the stack with no LLM and concluding that posting is broken.

**Acceptance:**
- The trigger rule exists once. A source scan fails when `detectCodeBlocks` is called from
  `AiReviewEventListener` or from `VibePostServiceImpl`, mirroring the approach in
  `NoEntityInControllerTest`.
- A replacement `ReviewPolicy` bean is the one that runs: a policy answering `false`
  unconditionally leaves every post at `ai_reviewed=0` and dispatches no review, with the
  default policy's own tests still green.
- Review and safety are independent in both directions: review off / safety on still runs the
  safety check, and review on / safety off still reviews.
- Unaffected: the lease claim, the reconciliation task, the async pool split, and every
  post-creation behaviour under the default configuration.

**Files:** `src/main/java/com/nexus/campus/agent/**`,
`src/main/java/com/nexus/campus/service/impl/VibePostServiceImpl.java`, `.env.example`.

**Rejected:** making `ReviewPolicy` return a reason or a decision object. Nothing in core reads
it, and a value nothing reads is a promise to keep it stable for no reason.

## AI-2 - One place performs the review

Status: open. `AiReviewService.reviewPost` is one 477-line method-bearing class that calls the
model *and* writes the result: the AI comment, the `ai_review_score` column, and the
`ai_review_log` row. A fork cannot change how a review is produced without editing the part
that records it.

**Scope:** split "produce a verdict" behind `Reviewer`, and keep "record a verdict" in core.

- `ReviewVerdict` carries what the recording half needs: score, severity, the markdown body,
  and the reviewer identity for `ai_review_log.reviewer`.
- `Reviewer` answers with that, or with an explicit unusable outcome carrying the reason.
- default bean `LlmReviewer`, `@ConditionalOnMissingBean`, holding today's path — prompt
  assembly, prompt isolation, the structured call, the JSON repair, the strengthened retry.
- the recording half stays in core and stays authoritative: review validity (severity known,
  body non-placeholder) decides whether a verdict may be written, and the lease, the FAILED
  transition, the retry budget and the terminal notification stay in
  `AiReviewEventListener` where they are now.
- the reviewer identity stops being a literal in five places. `AiReviewLogMapper`'s three
  queries filter on `'code-review-agent'`, and both writers stamp their own name; the default
  reviewer declares its identity, and the queries take it as a parameter. This is the part
  that would silently break a fork otherwise: swap the reviewer, keep the hard-coded name, and
  the score lookup returns nothing while every test still passes.

**Acceptance:**
- A replacement `Reviewer` returning a fixed verdict produces a comment, a score and an
  `ai_review_log` row of the same shape as the LLM path, asserted end to end. The recorded row
  carries the replacement's identity.
- A `Reviewer` that throws takes the existing path unchanged: `ai_reviewed=FAILED`, one
  attempt consumed, and the terminal notification once the budget is exhausted.
- An unusable verdict is not recorded, and takes the retry path rather than writing a score.
- Unaffected: prompt isolation, the review-validity rules and their tests, the
  `idx_post_ai_sort` ordering, and the showcase recording, which reads a stored log row rather
  than calling anything.

**Files:** `src/main/java/com/nexus/campus/agent/**`, `src/main/java/com/nexus/campus/service/**`.

**Rejected:** moving the recording half into the `Reviewer` contract. That would let a
replacement write a score that never passed validation, which is the one guarantee the
pipeline is asked to demonstrate.

## AI-3 - Publish the seam where a fork will find it

Status: open. A seam nobody can find is a private interface. The reuse assessment found the
coupling only by reading the code, and the README has no section that would have answered the
question.

**Scope:** documentation and one worked example, in the place a person forking this actually
looks.

- a README section on adapting the project to another subject: the coupling points that are
  not seams (package and property naming, the `announcements` slug guard, `postType=prompt`
  versioning, core-power rewards), the two that are (`ReviewPolicy`, `Reviewer`), and the
  recipe for running with no review at all.
- one example implementation kept in the repository, small enough to copy: a `Reviewer`
  returning a fixed verdict, or a `ReviewPolicy` reviewing everything. It ships with a test
  proving the bean is picked up, so the example cannot rot into something that no longer
  compiles against the interface.
- the ADR and this ticket linked from the README section, so the "why only two seams" question
  is answerable without searching.

**Acceptance:**
- Following only the README section, a reader can point the pipeline at their own policy by
  adding one class, without editing a file outside it.
- The example is exercised by a test that asserts the replacement is the implementation that
  ran, not merely that it compiles.
- `docs/INDEX.md` lists the ADR and this file.

**Files:** `README.md`, `docs/`, an example package under `src/`.

**Rejected:** naming the example in the ADR instead of keeping it in the tree. Prose about an
interface ages; a compiling implementation with a test beside it does not.

## AI-4 - Prove the seams are not decoration

Status: open, and small on purpose. AI-1 and AI-2 each assert their own seam, which leaves one
question they cannot answer separately: whether replacing *both* is enough to retarget the
pipeline at a different subject.

**Scope:** one test, in the spirit of the mutation checks this repository already runs
(`alert-path-in-ci-2026-09.md`, the frontend interceptor's two mutations). Configure a policy
that reviews everything and a reviewer that returns a canned verdict, publish one post, and
assert the whole path: the event is dispatched, the lease is claimed, the canned verdict is
recorded, and the row carries the replacement's identity.

**Acceptance:**
- With both defaults replaced, a post that the default policy would have skipped is reviewed,
  and the recorded review is the canned one.
- With both defaults restored, the same post is skipped and nothing is recorded. The pair is
  the evidence; either half alone proves nothing.
- Unaffected: the default path's 13 test files that reference agent types.

**Files:** `src/test/java/com/nexus/campus/agent/**`.

**Rejected:** asserting the seams by reflection (bean type is what the context holds). That
proves wiring, not behaviour, and the failure it would catch is not the one worth catching.

## Rejected for the round

**Removing the AI at the data-model level.** Nullable `ai_reviewed` / `ai_review_score`, a
conditional API surface, and the fifteen frontend files that mention a review. The runtime
already supports a forum with no review through the flag; this would change a contract to buy
nothing a fork needs, and `idx_post_ai_sort` is used by the AI-sorted feeds. See ADR-0012.

**An interface around `LlmClient`.** The circuit breaker, the per-address fallback, the JSON
repair and the prompt isolation are the implementation, and they would sit outside any
interface drawn there.

**Making fail-closed or the lease configurable.** They are the failure handling the project
exists to demonstrate; a fork that can switch them off is a fork that can delete the claim.

**The four-class safety taxonomy as a seam, this round.** The classes are persisted in
`ai_review_log` and drive status transitions, so the seam is a data-contract change. Named here
so the next round starts from the right sentence.
