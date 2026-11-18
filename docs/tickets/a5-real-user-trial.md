# A5 — the real-user trial: three to five humans, one journey each

Status: **in progress — the harness is ready; the participants are not.**
Launched 2026-11-19. Harness commit: this one. Record:
[real-user-trial-2026-11](../research/real-user-trial-2026-11.md). Branch:
`codex/a5-real-user-trial`, based on merged master `efeb004` (PR #53).

## Scope

The last unchecked item of the portfolio plan's definition of done
([portfolio-showcase-plan](../plans/portfolio-showcase-plan.md)). The plan is
explicit about what this is and is not: not community operations, not growth —
just 3–5 real people completing one journey each, so "I built it" becomes
"it was used, and here is the data".

What this round ships (all of it code- and doc-side; the humans are the
maintainer's to invite):

- **The invitation copy** — a short Chinese brief a participant can act on in
  ten minutes, written into the record so the wording every participant saw is
  itself part of the evidence.
- **The journey definition** — 注册 → 发一篇带代码块的帖 → 读到 AI 评审
  (score, severity, verdict visible on the post page), with the completion
  test stated so a participant's account, post and review row can be checked
  against the stack rather than taken on trust.
- **The verbatim-quote log** — a section that stays empty until participants
  speak, with the rule the plan sets: quotes are recorded unedited and
  unpolished; if only two people complete, the record says two.
- **The usage measurement** — one read-only SQL command against the live
  stack that counts registered users, human-written code posts (excluding the
  agent account and the showcase post) and the code reviews the pipeline ran
  for them. Run at kickoff for a baseline and again per participant wave.

## Acceptance

- The record file carries each participant's verbatim feedback, unedited;
  the count matches reality even when it is 0 or 2.
- The usage numbers in the record come from the named SQL command, run on
  this machine, with the date — never estimated.
- The trial is declared done only when 3–5 journeys are complete, or when
  fewer complete and the record says so in those words.

## Files

`docs/tickets/a5-real-user-trial.md`,
`docs/research/real-user-trial-2026-11.md`, `docs/INDEX.md`,
`docs/plans/portfolio-showcase-plan.md` (A5 row only).

## Rejected

- **Building a scripted "participant simulator" that registers and posts
  synthetic journeys.** The round's entire value is that a human who owes the
  product nothing still completed the journey; a script proves the pipeline
  works, which `mvn -B test` already proves for free.
- **Incentives or rewards for participants.** The plan excludes operations;
  paying users turns the evidence into "paid testers completed a task", which
  is a different claim this project does not want to make.
- **Inlining the quotes in the README before the trial completes.** The
  README's discipline is that every claim names its run; advertising an
  empty trial would violate it.
