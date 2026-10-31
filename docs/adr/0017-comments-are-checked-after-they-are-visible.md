# Comments are safety-checked after they are visible, not before

The post pipeline fails closed: when the LLM is unavailable a post waits in the human-review queue
rather than publishing unmoderated (ADR-0004). Copying that semantics onto comments would mean every
comment pays an LLM round-trip before it appears, on the site's hottest write path, and an LLM
outage would freeze discussion entirely. The decision: comments publish immediately, a safety event
runs the same four-class check asynchronously, and a flagged comment is hidden after the fact with
the same handling table as posts (harmful auto-hidden with author notice, spam auto-hidden without,
prompt injection marked for human review). This is a deliberate divergence from fail-closed, not an
oversight: the accepted cost is an exposure window of seconds to minutes for a bad comment, traded
against zero added latency for every good one and a comment section that keeps working when the LLM
does not. Reconciliation picks up comments stranded by an LLM outage the same way it picks up posts.
