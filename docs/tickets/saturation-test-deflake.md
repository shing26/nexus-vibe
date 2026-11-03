# Saturation test deflake: one test class, three different failures in a day

Source: the `Backend build and tests` failures on PRs #28, #29 and #30 (2026-11-02), all of
them `AgentPoolSaturationIntegrationTest` and none of them a defect in the production path.
Written after the work landed on master; every number below names the run that produced it.

Status: **done on master** (2026-11-02). PR #30 (`d1e168a`) carried the first fix, PR #28
(`ef2edb4`) the same file on a second branch, and PR #29 (`cbcb91c`) the precondition fix.
Backend `mvn -B test` measures **463 tests / 78 classes**, 0 failures, coverage floor met.

## Scope

This class is the only evidence for ADR-0014's recovery path: it saturates the real
`agentLlmExecutor` and asserts that a rejected dispatch still fails closed. It went red three
times in one day, each time for a different reason, none of which was the recovery path:

1. **The poll deadline raced the retry ladder.** `POLL_DEADLINE_SECONDS` sat at
   `HOLD_SECONDS + 10` (later `+ 20`), while an *accepted* dispatch runs the LLM client's two
   retry ladders — three structured attempts, then three `parseFallback` attempts, each
   bounded by the 30 s template timeout — before it can fail closed. Which of the two arrival
   paths a run took therefore decided whether the assertion won or lost. Fixed by mocking
   `LlmClient` so the accepted path resolves deterministically, releasing the hold as soon as
   the dispatch has been attempted (the accept-or-reject decision is already made inside
   `afterCommit`, before `TransactionTemplate.executeWithoutResult` returns, so the remaining
   30 s of hold only delayed the queued listener), and replacing the bare deadline return
   with `fail(...)` that prints the pool's state — a timeout must read as "the write never
   arrived", not as `expected: <2> but was: <1>`.
2. **The precondition assumed one refusal means full.** `occupyAgentPool` broke out of its
   fill loop on the first `RejectedExecutionException` and then probed once. A task finishing
   between the two calls frees a slot, and the probe is accepted by a pool one short of full
   — the assertion then reports "nothing was thrown" about a pool that really was saturated.
   This is the mode that hit PR #29 *after* the first fix had landed, and it reproduces
   locally at **one run in six** (5 of 6 green before the fix). Fixed by confirming the
   refusal, repeating the fill when the probe is accepted, and failing with the pool's own
   state: core/max/poolSize/active/queued, queue class, remaining capacity, gate count.
3. **The gate was read from the field.** `hold()` captured `release` from the field, so a
   task left over from the previous test could wait on the next test's latch. It now takes
   the caller's local latch.

## Acceptance

- The class runs green ten times consecutively, twice (20/20), against a pre-fix rate of 5/6.
  The repeats and their per-run exit codes are machine-local under `scratch/`, which is
  gitignored; the counts here come from those summary lines, not from `target/`.
- `mvn -B test` with the hardened file: BUILD SUCCESS, 463 tests, coverage floor met
  (master content, 2026-11-03).
- CI on the fixed head: run `41430772028` red on the precondition, `41460399218` green.

## Files

- `src/test/java/com/nexus/campus/agent/AgentPoolSaturationIntegrationTest.java`

## Rejected

- **Raising `POLL_DEADLINE_SECONDS` further.** The first attempt on
  `codex/ci-deflake-saturation` did exactly that and still failed. The wait was never the
  only race, and a longer deadline makes a real failure slower to surface without making a
  flaky one rarer.
- **`@RetryTest` or surefire `rerunFailingTestsCount`.** It would have hidden the second
  defect instead of fixing it, and a test that can report green after being wrong is worse
  than a red one — this class is the only place ADR-0014's recovery path is asserted.
- **Mocking the publisher or the listener to force saturation.** The class exists because a
  mocked publisher cannot tell "the catch ran" apart from "the catch was unreachable", which
  is the exact bug ADR-0014 fixed.
- **Disabling the class in CI.** Same argument as the rerun entry, plus it would leave the
  fail-closed guarantee unasserted on the only runner that has ever caught it.
- **Mirroring CI's `-P surefire-without-container-support` locally.** It passes
  `-XX:-UseContainerSupport`, which the Windows JVM rejects outright (`Unrecognized VM
  option`), so the repeat loop cannot carry that flag. Its only effect here is CPU-count
  reporting, and the flake reproduced without it.
