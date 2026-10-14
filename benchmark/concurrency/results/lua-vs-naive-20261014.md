# Lua atomic toggle vs naive round trips (20261014)

Produced by `python benchmark/concurrency/lua-vs-naive-consistency.py --host localhost --port 6390 --threads 50 --rounds 30` against redis-server 5.0.14.1.

The invariant is `zscore == scard * weight`: every member of the like-set contributes exactly one signed weight to the hot-ranking ZSET. A round that breaks it is a round where the two structures disagree about the same like.

| Path | Distinct users, one like each | Same user, 50 concurrent toggles (30 rounds) |
| --- | --- | --- |
| Lua atomic script | scard=50, zscore=150 (consistent) | 0/30 rounds drifted, max abs drift 0 |
| Naive round trips | scard=50, zscore=150 (consistent) | 29/30 rounds drifted, max abs drift 54 |

## What this run does not show

- It is a Redis-only probe. It does not exercise the HTTP like path, MySQL membership or `LikeSyncTask`; `benchmark/concurrency/like-concurrency.ps1` covers the end-to-end three-view agreement on the full stack.
- A Redis version's execution model is what is being measured, not this repository's latency. Timings are not recorded on purpose.
- The naive path is a faithful re-implementation of the same steps, but it is not a code path the application ships. It is the control, not a regression.
