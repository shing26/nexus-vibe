# Acceptance contract: AI-4 — prove the seams are not decoration

Task id: `ai4-acceptance`. Frozen 2026-11-09, before the verification run. This contract
governs the independent acceptance of the already-merged AI-4 work; it is written before the
verifier runs, per the acceptance-chain rule that the contract is frozen before judging.

## Subject under acceptance

PR #34 (merged), PR head `84aff27` ("test: prove the seams are not decoration (AI-4)"),
master merge commit `a3b6942`. The local checkout sits on `codex/frontend-polish-2` at
`84aff27`; because the merge into master introduced no conflict resolution, the tree at
`84aff27` is byte-identical to master's tip. The verifier verifies at `84aff27` and records
both SHAs in the evidence pack.

## Allowed paths (files the change may touch)

- `src/test/java/com/nexus/campus/agent/BothSeamsReplacedIntegrationTest.java` (new)
- `src/test/java/com/nexus/campus/agent/DefaultsOnlyIntegrationTest.java` (new)
- `README.md` (badge + Testing section + fork-map Reviewer bullet)
- `docs/tickets/ai-review-seam.md` (AI-4 status, AI-2 partial note)
- `docs/INDEX.md` (row for this plan, when committed)

Any other tracked file changed by `84aff27` is a contract violation.

## Acceptance criteria (from docs/tickets/ai-review-seam.md, AI-4)

1. With both defaults replaced (`@Primary` everything-passing `ReviewPolicy` +
   `canned-review-agent` `Reviewer`), a plain-prose post the default policy would skip is
   reviewed; the recorded review is the canned one — `REVIEWED` status, score 5, severity
   `medium`, approved, exactly one `ai_review_log` row stamped `canned-review-agent`, and one
   visible AI comment (user 999) whose body starts with `## AI Code Review` and names AI-4.
2. With both defaults restored (default context), the same prose shape is skipped:
   `NOT_REVIEWED`, score 0, no `ai_review_log` row. The pair is the evidence; either half
   alone proves nothing.
3. The canned verdict passes `ReviewValidity` by construction (severity in the known set,
   quality/suggestions ≥ 20 chars, not all-caps) — the recording half accepts it on merit.
4. Unaffected: the default path's test files that reference agent types remain green in the
   full suite.
5. The full-suite count moves to 465 tests / 80 classes, and the README badge and Testing
   section carry that count in the same commit as the tests.

## Verification commands (the verifier re-runs these itself; pasted logs from the
implementer are not evidence)

```bash
# environment fault workaround on this machine: C: is near-full, so the run
# must redirect TMP/TEMP/MAVEN_OPTS to D:\tmp — use the machine-local script:
powershell.exe -File 'D:/Nexus-Campus/scratch/run-full-final.ps1'
#   (wraps: mvn -B test  →  expect BUILD SUCCESS, 465 tests / 80 classes)
```

```bash
powershell.exe -File 'D:/Nexus-Campus/scratch/run-ai4.ps1'
#   (wraps: mvn -B test -Dtest=BothSeamsReplacedIntegrationTest,DefaultsOnlyIntegrationTest
#    -DfailIfNoTests=false -Djacoco.skip=true  →  expect 2/2, BUILD SUCCESS)
```

Read-only checks (Read/Grep):

- `git rev-parse HEAD` == `84aff27`; `git status --short` shows only untracked
  `.claude/` and `.impeccable/`.
- The two test classes exist and assert what criteria 1–2 name (Read them).
- `README.md` badge reads `465` Java and `52` frontend; the Testing section states 465/80.
- `git show --stat 84aff27` touches only the allowed paths.

## Forbidden

- Do not weaken, skip, or edit any test or assertion to make a command pass.
- Do not commit anything; do not push; do not switch branches in the shared checkout.
- Do not edit unrelated uncommitted work (`.claude/`, `.impeccable/` stay untouched).
- Do not count tests from `target/surefire-reports/*.txt`; the `mvn test` summary line is the
  only trustworthy count.
- Locally, do not pass `-P surefire-without-container-support` (the CI-only profile's
  `-XX:-UseContainerSupport` is unrecognized on this Windows JVM and kills the forked VM).

## Human review gate

The verifier's evidence pack binds to HEAD SHA `84aff27` + `git status --short`. Merge
sign-off (here: post-merge acceptance sign-off) belongs to the maintainer, in writing; this
session is the implementer's coordinator and does not sign.
