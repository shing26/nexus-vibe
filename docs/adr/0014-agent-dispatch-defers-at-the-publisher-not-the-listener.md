# 0014 — Agent dispatch defers at the publisher, not at the listener

`PostCreationService.createPost` publishes its agent events from inside its own transaction, so a
plain `@EventListener` can run before the post row is committed. The listener then reads no row,
concludes the post is ineligible, and clears the REVIEWING marker — and when the transaction
commits between that read and that write, the marker is reset on the row that was just created.
Nothing reconciles a post left at `ai_reviewed = 0`, because reconciliation selects `IN (2, 3)`.
That is the defect `3d737e2` set out to fix, by moving both listeners to
`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`.

**That fix was wrong, and it is reverted here.** The phase is correct about when a listener should
see data, but it relocates the one thing the publisher is responsible for.

The `@Async` submission is what a saturated `agentLlmExecutor` rejects, and it happens *inside* the
listener. Under `AFTER_COMMIT` that submission no longer occurs during `publishEvent`; it occurs
later, inside Spring's transaction-completion machinery. `PostAgentEventPublisher` caught the
resulting `TaskRejectedException` in a `try` around `publishEvent` — which, on the transactional
path, simply returned normally and was never going to throw. Verified, not inferred:

- with `AFTER_COMMIT`, `events.publishEvent(...)` inside a transaction throws nothing, and from
  inside an `afterCommit` callback it throws nothing either — the event is re-registered against a
  synchronization that is never triggered, so it is dropped outright;
- with a plain `@EventListener`, the same call on a saturated pool throws
  `TaskRejectedException` in the caller's frame, which is the only place the catch can see it.

The consequence is not a slower review. It is the failure mode [ADR-0004](0004-safety-check-fail-closed.md)
exists to prevent: the safety event is lost, no `pending-llm` marker is written, the post stays
`ACTIVE` and publicly visible with no verdict, and — because `selectPostsPendingSafetyRecheck`
keys on that marker — reconciliation cannot find it either. Permanently unreviewed, and silently
so. The rejection also escapes the transaction, so the caller sees an exception for a post that
was committed: precisely the HTTP-500 storm the fail-closed handling was written to absorb.

## Decision

The listeners stay plain `@EventListener`s. The ordering requirement is met at the publisher
instead: `PostAgentEventPublisher` registers an `afterCommit` synchronization when a transaction is
active, and dispatches inline otherwise (the reconcile task's scheduler path, which has no
transaction and must keep working via `fallbackExecution`).

This is the only arrangement where both properties hold at once. The listener reads committed data,
because the dispatch happens after the commit. The rejection is caught, because the dispatch —
and therefore the `@Async` submission — happens in the frame that holds the `try`.

Two details follow from it:

- the LLM-health gate in `publishSafety` stays **inline**, inside the caller's transaction. Holding
  a post closed has to commit atomically with the insert that published it; deferring it would open
  a window where the post is public before anything holds it.
- the saturation recovery writes run in a `REQUIRES_NEW` transaction. They execute after the outer
  transaction is finished, so on its connection they would never be committed.

## Verification

`AgentPoolSaturationIntegrationTest` saturates the real `agentLlmExecutor`, runs the publish
through a real transaction that really commits, and asserts both fail-closed outcomes. It is not
decorative: it fails against `AFTER_COMMIT`, it fails against a publisher that defers while the
listeners are transactional, and it passes only with the arrangement above. The pre-existing
rejection tests in `PostAgentEventPublisherTest` mock `ApplicationEventPublisher` and therefore
never distinguished "the catch ran" from "the catch was unreachable" — which is why the
regression passed 414 tests.

`dispatchIsDeferredUntilCommit` in the same file asserts the ordering half, without reflection:
inside a transaction, `publishEvent` is not called until the commit fires.

## Rejected

**Keeping `AFTER_COMMIT` and handling rejection inside the listener.** Doable — the listener could
submit to the executor itself instead of relying on `@Async` — but it moves the fail-closed
decision out of the component that owns it and into two listeners, and the recovery writes would
have to be transactional from inside a completion callback. More moving parts for the same
guarantee.

**Reverting to plain inline publishing and accepting the race.** The race is bounded: the post sits
in REVIEWING and `AiReviewReconcileTask` re-triggers it after `campus.ai.reconcile.stale-minutes`
(10 by default). But it is only bounded when the marker survives, and the case where it does not
is the case where the post ends at `ai_reviewed = 0`, which no sweep selects. Deferring the dispatch
removes the race at its source instead of narrowing how often it bites.
