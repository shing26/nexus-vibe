# A comment mentioning the agent summons it, and silence is not an option

Users can address the AiAgent by mentioning it in a comment; the agent replies in the thread as a
`VibeComment` in reply to the mentioning comment. Two semantics here are deliberate deviations from
established behavior. First, when the LLM is unavailable, the agent immediately posts a visible
placeholder reply and the reconciliation task replaces it in place once a real answer is produced —
inverting the review pipeline's "reconciliation retries are silent" rule. A review is a system
action where silence costs nothing, but a mention is an explicit user summon: no reply at all is the
worst outcome, and a thread visitor should be able to see that the summon was heard and is being
retried. Second, a per-user per-post cooldown gates mentions (on top of the existing async pool's
backpressure), because a mention is a switch anyone can flip that costs a real LLM call. Agent
replies never re-trigger safety checks or further agent work — the agent must not be able to
converse with itself.
