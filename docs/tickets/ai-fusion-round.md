# AI Fusion Round — Prompt review, comment safety check, agent mention

Status: **open.** Opened by ADR-0016, which reopens the scope ADR-0009 froze, with the reasoning
and the deferral list. Three items, one branch, one PR; each item lands as its own commit so a
partial merge is a coherent state. Semantic search, streaming output and composer assistance are
**not** in this round — they are recorded in the next-cycle backlog, not dropped.

The connecting discipline across all three items: every new AI surface must answer "what happens
when the LLM is down", not just "what does it do when it works". That is the project's own test for
whether new code belongs to the story or merely decorates it.

## Item 1 — Comment safety check (defect first, not feature)

Comments currently pass no safety check: posts fail closed (ADR-0004) while comments ship unmoderated
into a page that renders them next to AI review output. ADR-0017 sets the semantics: publish
immediately, check asynchronously, flag after the fact, same four-class handling table as posts.

**Scope:**

- A `CommentSafetyCheckEvent` published on comment creation (publisher-side defer, mirroring the
  ADR-0014 lesson — do not defer at the listener) and consumed by a comment safety listener.
- Four-class handling mapped to comments: harmful → hide + system message to author; spam → hide,
  no notice; prompt_injection → mark for the human audit queue; safe → `ai_review_log` only.
- LLM unavailable or unparseable → the comment stays visible with a pending-llm marker; the
  reconcile task rechecks it after recovery (ADR-0017's accepted exposure window).
- The audit page lists flagged comments alongside flagged posts.

**Acceptance:**

- An integration test posts a harmful comment and asserts it is hidden asynchronously with an
  author notice; a spam comment is hidden with no notice.
- A test with the LLM breaker open asserts the comment stays visible, the pending-llm marker is
  set, and a reconcile run after recovery rechecks it.
- No added latency on the comment POST path: the endpoint returns before any LLM call starts.

## Item 2 — Prompt review (the seam ADR-0012 left open)

The forum's flagship channel is Prompt 工坊, and the only production `ReviewPolicy` excludes prompt
posts. Add a `PromptReviewPolicy` that claims `postType=prompt` posts, with its own output schema
(clarity, variable design, reproducibility, expected-output structure) flowing through the same
structured-output → repair-parse → validate → retry path.

**Scope:**

- `PromptReviewPolicy` bean and a prompt-review schema; the reviewer selection follows ADR-0012's
  two-seam rule — trigger rule and reviewer are pluggable, nothing else is.
- The result renders in the existing AiReviewTerminal on prompt post pages and posts an AI comment
  in the same format; the score badge and search's `aiScoreMin` filter work unchanged.
- The agent-logs dashboard distinguishes the two reviewer names.

**Acceptance:**

- Publishing a prompt post triggers exactly one review with the prompt schema; a code post still
  triggers exactly one with the code schema; a post with both (prompt containing a code block) is
  claimed by exactly one policy, and the test names which.
- A semantically-empty or unparseable response degrades through the existing retry ladder — no
  new degradation path is invented.

## Item 3 — Agent mention (the participant turn)

ADR-0018 sets the semantics: mention `@ai-agent` in a comment, the agent replies as a `VibeComment`
in reply to the mentioning comment, with post + comment-chain context, bounded like every other
token budget.

**Scope:**

- Mention parsing on comment creation; a per-user-per-post cooldown; the reply job runs on the
  same async pool whose backpressure already saturates gracefully (the load test's terms).
- On LLM failure: an immediate visible placeholder reply naming the failure, replaced in place by
  the reconciliation task when a real answer is produced (ADR-0018's inversion of silent retry).
- Comment content goes through `PromptIsolation` nonce delimiters — it is untrusted input adjacent
  to instructions, exactly like post content.
- Agent replies never re-trigger safety checks or further agent work.

**Acceptance:**

- A test mentions the agent and asserts exactly one agent reply, threaded under the mention.
- A test mentions twice inside the cooldown and asserts the second mention summons nothing.
- A test with the LLM breaker open asserts a placeholder reply appears and a successful reconcile
  run replaces it in place — the thread never shows both, and never shows neither.
- A test asserts an agent reply containing `@ai-agent` summons nothing.

## Verification

```bash
mvn -B test                     # update the README badge and Testing section in the same commit
cd frontend && npm run test
cd frontend && npm run lint && cd frontend && npm run build
```

## Files

`src/main/java/com/nexus/campus/agent/**` (new comment listener, mention listener, prompt schema),
`src/main/java/com/nexus/campus/service/impl/**` (comment event publisher, mention trigger),
`src/main/java/com/nexus/campus/task/AiReviewReconcileTask.java` (comment rechecks, placeholder
replacement), `src/main/java/com/nexus/campus/controller/CommentController.java`,
`frontend/src/**` (terminal reuse for prompt posts, mention UI affordance),
`docs/adr/0016..0018`, `CONTEXT.md`, this file, `docs/INDEX.md`, README counts if they change.

## Rejected

- **Bundling semantic search / streaming / composer assist into this round.** Each is a round of
  its own (ES mapping + an embedding chain; a real-time channel; a composer surface). Bundling
  thins every acceptance criterion — recorded in ADR-0016 and the backlog, not dropped.
- **Pre-publish comment gating (fail-closed for comments).** Copies the post semantics onto the
  hottest write path and freezes discussion during an LLM outage. ADR-0017 records the trade.
- **Silent retry for unanswered mentions.** The review pipeline's rule, inverted deliberately:
  a mention is a user summon, and silence is the worst outcome. ADR-0018.
- **Daily quotas on mentions.** A cooldown plus existing pool backpressure already bounds cost;
  a quota adds a number nobody can defend yet (per ADR-0018's "keep both layers, invent neither").
- **A third ADR-worthy seam for reviewer selection.** ADR-0012's two seams are the contract;
  prompt review uses the existing reviewer seam rather than widening it.
