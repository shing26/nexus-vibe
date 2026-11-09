# 0019 — A degraded capability is published as a closed vocabulary the server owns

ADR-0007 split the health surface by audience and closed the dependency detail at the edge. The
public answer became `/actuator/health/servable`, which says whether the site is serving and
nothing else; `/actuator/health/deps` keeps the component names, statuses and probe details
inside `nexus-net`. That was the right answer to "should this container be replaced" and "can a
user still use the site", but it left a third question unanswered: a visitor whose search is
running from the SQL fallback cannot tell whether the site is broken, their query is wrong, or a
dependency is down. The banner exists to answer that question, and it is the only reason the
project needs a public statement about degradation.

The banner does not read health. It reads `GET /api/v1/status/capabilities`, which returns a list
of `{key, impact}` and nothing else. The table lives in `DegradedCapabilityService` and is a
closed vocabulary: `elasticsearch` → `search` / "Search results may be incomplete.", `llm` →
`ai-review` / "New posts may take longer to appear while automated review is unavailable.",
`redis` → `cache` / "Like counts and trending posts may lag behind." A component that is
degraded but absent from the table produces nothing, so a new indicator added to the `deps`
group cannot leak its name, status or probe reason through this endpoint. The mapping is built
from the table rather than by iterating the health map for exactly that reason. `db` is not in
the table: it is not an optional capability, and a database incident is an operator incident,
not a "some features are limited" notice. Only `DEGRADED` is reported — a `DOWN` component is a
different incident, owned by the servable group and the top-level document.

The sentence is composed on the server and rendered verbatim by the SPA. A second copy of the
wording in the client is how the two drift, and the client cannot know which optional dependency
is down without being told. Dismissal is the client's business: it is per session and keyed by
capability, so dismissing "search" does not hide a later "ai-review" notice, and the keys are a
client-visible contract — renaming one is a breaking change for anyone holding a dismissed
entry in `sessionStorage`. The banner links ADR-0007 rather than restating it.

Alternatives rejected: forwarding or parsing `/actuator/health/deps` from the browser, which
reopens precisely the surface ADR-0007 closed and publishes probe details as a side effect; a
generic "some features are limited" message, which names no consequence and so answers the
visitor's question with the same words as silence; a client-side map from component to sentence,
which duplicates the vocabulary in a place that cannot observe the components; and gating the
endpoint behind authentication, since the people who need it are anonymous readers.

Consequences worth stating. The endpoint is public and unthrottled, and each call evaluates the
`deps` group, so it is a probe-amplification path — recorded as R3-1 in
`docs/tickets/next-cycle-backlog.md` rather than fixed here. The response is a contract, not a
debug view: adding a capability means adding a key, a sentence and a client that can dismiss it.
And when the endpoint fails the banner stays absent rather than claiming a problem it cannot
name; an outage of the status endpoint itself is invisible to visitors, which is the safe
direction for a notice that is advisory.
