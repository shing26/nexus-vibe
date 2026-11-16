# 0022 — Reopening the scope decision for a self-demonstrating community

ADR-0009 froze the project at a completed deployment and said plainly that further work "has to be
argued for rather than assumed". ADR-0016 reopened it once already, for a single AI-fusion round,
and that round is shipped. This ADR makes the next argument and supersedes the freeze itself,
rather than reopening it for one more round.

## The argument

The deployment was completed (`20261115-01`, PR #40), and the freeze then did what it was meant to
do: it stopped feature drift. What it could not do is answer the question a reader of this
repository actually asks — *why would this forum exist rather than any other forum?* Three
external reviews of the frontend rounds converged on the same answer, independently and in
harsher words: the deliverable had become "a clean forum template with a real system folded up
inside it". Every differentiated mechanism in the codebase — fail-closed moderation, lease-based
claims, repair-and-retry, the review validity gate, degraded-but-serving semantics — was invisible
to anyone visiting the site.

The product proposal ([docs/product/community-proposal-review-in-public.md](../product/community-proposal-review-in-public.md))
turns those mechanisms into public life: the uncertainty is watched, the failures have a stage,
the corrections have a trajectory, the evidence becomes reputation. Its premise is not "add
features"; it is that **the project's subject is only demonstrated if the demonstration is
visible**. That premise fails if the work stops at deployment, because what is deployed is a forum
whose AI behaviour a visitor cannot see.

## Decision

1. **ADR-0009's freeze is superseded.** The project is not "done at deployment" any more; it is
   done when the community surfaces demonstrate the engineering claims. Further work is still
   argued for slice by slice, not assumed — but the default direction is now the proposal, not
   the backlog.
2. **The proposal is adopted as the next scope**, with slices S1-S5 (review-in-public main line)
   and M1-M3 (AI-native content modules) each independently acceptable. The first slice is **S4
   evidence profile**: every number in a profile links to the raw `ai_review_log` row it came
   from. It needs no schema change, and it extends the review voucher already shipped by the
   design rounds.
3. **The tools that produced the freeze's honesty stay in force.** The acceptance chain
   (frozen contract, isolated implementer, independent verifier, written sign-off), the claims
   discipline in AGENTS.md, the migration rehearsal for any schema change, and ADR-0010's
   replay-not-live rule are unchanged. A reopened scope is not a relaxed one.
4. **The old backlog is not the new plan.** `docs/tickets/next-cycle-backlog.md` stays a
   backlog. Slices are chosen for theme demonstrability, not for being next in that list.

## What this reverses, precisely

ADR-0009 declared that anything not moving deployment forward "does not get worked on". That
sentence is what this ADR lifts. The rest of ADR-0009 — no recruited user cohort, no library
extraction, and the refusal to imply usage claims the project cannot support — is **not** lifted;
those remain declined, for the budget reasons it gave.

## Consequences

- The README and the deployment's live surface must eventually show the new community surfaces,
  or the argument above is contradicted by the product itself. Publishing is therefore part of
  the deliverable again, not an optional follow-up.
- S2's consensus loop and S3's AI citizen need schema or pipeline work and go through the
  migration rehearsal; S1's live progress needs instrumentation at the ADR-0014 defer seam; S4,
  S5 and M2 mostly reuse existing tables and endpoints.
- The "finished" claim in the README becomes time-bound: it describes the round that froze at
  deployment, and a later round describes what replaced it. This ADR is that later round's
  record.

## Rejected alternatives

- **Keeping the freeze and shipping the design rounds only.** Rejected because the design rounds
  were explicitly judged, in three independent reviews, as leaving the differentiated system
  invisible — the freeze would preserve a product whose subject is unobservable.
- **Reopening as "more features" (the eight backlog tickets).** Rejected because those tickets are
  capability increments, while the proposal's slices are demonstrations of claims already made;
  conflating them would produce a feature list again, which is what ADR-0009 was right to stop.
- **Reopening without an ADR.** Rejected on this repository's own convention: a changed mind is a
  new ADR, never a rewritten one.
