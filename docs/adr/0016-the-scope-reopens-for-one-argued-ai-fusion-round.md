# The scope reopens for one argued AI-fusion round

ADR-0009 froze the deliverable at a completed deployment, and that freeze was correct for what
the project then was: a portfolio artifact whose subject is failure handling around an unreliable
LLM. A full-stack review (2026-10-31) found the engineering at that subject is done, but the AI's
product surface is one-directional — the model only ever reviews, never converses — and carries one
genuine defect: comments pass no safety check at all, while posts fail closed. This ADR reopens the
scope for exactly one round with three items, each reusing the existing guardrails (lease, repair-
retry, breaker, reconciliation, metrics) rather than adding new mechanisms: a second `ReviewPolicy`
implementation so prompt posts are reviewed, post-visible safety checking for comments, and an
agent mention so users can summon the AI in a comment thread. Semantic search, streaming output and
composer assistance were considered for this round and are explicitly deferred to
`docs/tickets/next-cycle-backlog.md` — they are each a round of their own, and bundling them would
thin the acceptance evidence of all five. The trade-off this ADR records: more AI surface risks
diluting the failure-handling story that is the project's core, so every item in this round must be
able to answer "what happens when the LLM is down", not just "what does it do when it works".
