# 0009 — The deliverable stops at a completed deployment

Two rounds of hardening had produced 331 backend tests, 25 frontend tests, ten ADRs, six measured research notes and a 23-step failure drill — and the project had never run anywhere a stranger could open it. Every one of those artefacts answered "is this built correctly?" and none of them answered "can I see it?". Meanwhile the budget is under five hours a week, which makes the choice between finishing and continuing a real one rather than a stylistic preference.

The definition of done for this project is therefore: **development through deployment, and then freeze.** Concretely that means the stack runs on a public HTTPS address, an outsider can register, publish a post and receive an AI review, and the README gives a paved path from that address to the evidence behind it. Anything that does not move one of those four things does not get worked on.

What this deliberately declines, and why each was not merely postponed:

- **A small cohort of real users.** The product-loop meters already exist, so the *test* is possible — but recruiting, welcoming and retaining 5–20 people is roughly seventy percent non-code work, and at this budget it would consume the entire remaining runway to answer a question this project does not need answered. The honest statement is "no real users", not "users are coming".
- **Extracting `agent/` and `task/` into a standalone library.** Fourteen files, about 2,200 lines, and the most differentiated code in the repository. It is also the most expensive item on the list: extraction, packaging, examples and backward compatibility are full-time work, and splitting them across five-hour weeks produces neither a library nor a finished product. This is the decision most likely to be revisited, and it should be — from a week with real hours, starting with `LlmClient` and `JsonRepairUtil` rather than the whole package.
- **More platform features, and the eight tickets in `docs/tickets/next-cycle-backlog.md`.** They are written down, ordered, and unfunded. That backlog is the correct place for them; the mistake would be treating a filled backlog as a commitment.

The cost of this decision is stated plainly because it is real: the strongest claim available after it is "it is deployed and the failure handling is documented and measured", not "people use it". A reader who wants the second claim will not find it here, and no README wording should imply otherwise.

Alternatives rejected: archiving without deploying, which throws away the one thing all the evidence cannot substitute for — a working address; and pursuing growth first, which is the path that ends with an unpaid community moderator who never returns to the library work that was actually the differentiator.
