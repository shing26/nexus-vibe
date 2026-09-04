# 0005 — Lease-based claim for the AI review pipeline

The AI review pipeline processes posts asynchronously: an event is published on post creation/edit, two listeners run LLM calls on a dedicated pool, and a reconciliation task re-dispatches posts that failed or went stale. Before leases, the reconciliation used staleness windows (create_time + no recent attempt log), which self-heals a single instance but has two gaps: concurrent instances (or a re-published event racing a live review) can double-process the same post, and recovery latency is bounded by the staleness window rather than the actual failure.

We added lease columns on `vibe_post` (`review_lock_until`, `review_owner`, `review_attempts`). Every attempt claims the post with a single atomic conditional UPDATE — wins iff no live lock exists and the attempt budget (5) is not exhausted. The reconciliation task only nominates candidates; the claim arbitrates. When the final attempt fails, the author receives a one-time terminal notification.

Alternatives rejected: an independent lease table (cleaner but needs its own entity/mapper and orphan handling) and Redis SETNX locks (minimal diff, but they inherit Redis availability — the exact failure mode this hardening targets). The columns live next to the state they gate, and existing deployments migrate via a single ALTER script.
