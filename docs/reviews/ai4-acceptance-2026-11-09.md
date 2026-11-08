# Acceptance record: AI-4 — prove the seams are not decoration

Status: **signed off 2026-11-09**, by the maintainer, in writing ("签收"), after the
independent verifier's evidence pack and the four human-review questions below. The
acceptance chain ran in full: contract frozen before judging, implementer and verifier in
separate sessions, verification commands re-run by the verifier itself, evidence bound to
HEAD, sign-off in writing before the record landed.

- Subject under acceptance: PR #34 (merged), head `84aff27`, master merge `a3b6942`.
- Frozen contract: [plans/ai4-acceptance](../plans/ai4-acceptance.md) — written before the
  verification run; Allowed paths, five acceptance criteria, verification commands,
  Forbidden list.
- Verifier: a fresh session restricted to Read / Grep / Glob / Bash. It re-ran both
  verification commands itself; the implementer's pasted logs were not treated as evidence.

## Verification commands, re-run by the verifier

| Command | Exit code | Observed result |
|---|---|---|
| `powershell.exe -File 'D:/Nexus-Campus/scratch/run-ai4.ps1'` (wraps `mvn -B test -Dtest=BothSeamsReplacedIntegrationTest,DefaultsOnlyIntegrationTest -DfailIfNoTests=false -Djacoco.skip=true`) | 0 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS` |
| `powershell.exe -File 'D:/Nexus-Campus/scratch/run-full-final.ps1'` (wraps `mvn -B test`) | 0 | `Tests run: 465, Failures: 0, Errors: 0, Skipped: 0`, 80 class report lines, `BUILD SUCCESS` |

Both scripts were read by the verifier before running: they redirect TMP/TEMP/MAVEN_OPTS to
`D:\tmp` (the C:-near-full machine fault) and carry no `-P surefire-without-container-support`
(the CI-only profile is unrecognized on this Windows JVM). Counts were taken from the mvn
summary line only, per the repository rule; `target/surefire-reports/` was not read.

## Evidence binding

- HEAD `84aff271c5ad50ce794e269ca26e7b621275af6e` at verification time.
- `git status --short`: `?? .claude/`, `?? .impeccable/`, plus the untracked contract file
  itself (frozen before the run; the contract forbids the verifier from committing, so it
  stayed untracked — recorded, not a violation). No tracked file modified.
- `git show --stat 84aff27` touches exactly the Allowed paths: the two test classes (new),
  `README.md`, `docs/tickets/ai-review-seam.md`.

## Acceptance criteria — all five PASS

1. **Both seams replaced → the skipped shape is reviewed.** The verifier read
   `BothSeamsReplacedIntegrationTest.java` line by line: `@Primary` always-true
   `ReviewPolicy`, `canned-review-agent` reviewer claiming everything, a plain-prose post the
   default rule declines; asserts `REVIEWED`, score 5, exactly one `ai_review_log` row stamped
   `canned-review-agent` with severity `medium` and approval, and one comment from user 999
   starting `## AI Code Review` naming AI-4. Green in the targeted run.
2. **Defaults restored → the same shape is skipped.** `DefaultsOnlyIntegrationTest.java` has
   no `TestConfiguration` — the real default wiring, kept out of the sibling class's context
   by Spring TestContext caching; the same prose content asserts `NOT_REVIEWED`, score 0, no
   log row. The pair is the evidence; the two classes hold the two distinct Spring contexts.
3. **The canned verdict passes `ReviewValidity` on merit.** Severity `"medium"` is in the
   known set; quality/suggestions exceed the 20-char floor and are not all-caps; the gate runs
   in core (`AiReviewService.java:68`) before any recording — a replacement cannot write what
   core would refuse.
4. **Default path unaffected.** The full suite, 465 tests green, shows no regression in the
   agent-type tests.
5. **Count consistency in the same commit.** Badge `465 Java + 52 frontend`, Testing section
   `465 tests / 80 classes`, and the tests themselves all land in `84aff27` (stat includes
   `README.md | 6 +-`).

## Forbidden checks — none tripped

Assertions were checked weakened-or-not against the contract (exactly-one row not relaxed to
`>=1`; `startsWith` not swapped for `contains`); no skipped or filtered tests in the full run;
nothing committed, pushed or branch-switched by the verifier; `.claude/` and `.impeccable/`
untouched.

## Human review — the four questions, on the record

1. **Does the change touch business logic / APIs / data deletion / schema?** No — new tests
   and documentation counts only. Nominally the "test adjustments" tier, but AI-4 is the
   ticket's required acceptance evidence, so the chain was run at the higher tier; that
   judgement is recorded here.
2. **Were implementation and judgement separated?** Yes — AI-4 was implemented in one session
   (PR #34, merged); acceptance was judged by a fresh verifier session with
   Read/Grep/Glob/Bash only.
3. **Were the verification commands re-run by the Verifier itself?** Yes — both commands, both
   exit code 0; the implementer's earlier local run was not cited as evidence.
4. **Is the evidence bound to HEAD and working-tree state?** Yes — `84aff27` plus the
   `git status --short` snapshot above.

## Sign-off

The maintainer's written sign-off ("签收", 2026-11-09) is the closing act of this acceptance.
This record is the trace of it: contract → independent verification → evidence pack → four
questions → sign-off → record landed. With it, the `ai-review-seam` round (AI-1 to AI-4) is
implemented, merged, and formally accepted.
