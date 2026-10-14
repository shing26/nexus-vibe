#!/usr/bin/env python3
"""Compare the shipped Lua like-toggle against the same logic split across round trips.

The claim under test is not "Lua is faster". It is narrower and checkable: a like is
three Redis structures that have to agree - the member set, the dirty marker and the
hot-ranking ZSET. ``like_toggle.lua`` mutates them in one atomic script. The naive
path performs the same steps as separate commands, so two callers can both read
``SISMEMBER == 0`` before either writes, both run ``SADD`` (the second is a no-op)
and both run ``ZINCRBY`` - leaving one member on the set and two weights on the
ranking. That is the drift this probe measures.

Two bursts are run against a live Redis, for both paths:

* ``distinct`` - N users each like once. The set is exact by construction, so both
  paths should agree with the invariant ``zscore == scard * weight``.
* ``same-user`` - N concurrent toggles for one user, i.e. a double-submit or a
  retry storm. The Lua path stays consistent by construction; the naive path is
  the one under suspicion.

Run it against any reachable Redis; the probe uses its own keys and deletes them.

    python benchmark/concurrency/lua-vs-naive-consistency.py \
        --host localhost --port 6390 --threads 50 --rounds 30

Requires ``redis`` (redis-py). The real script is read from
``src/main/resources/lua/like_toggle.lua`` rather than copied here, so the probe
cannot drift from the code it is measuring.
"""

from __future__ import annotations

import argparse
import json
import statistics
import threading
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

import redis

REPO_ROOT = Path(__file__).resolve().parents[2]
LUA_SCRIPT = (REPO_ROOT / "src" / "main" / "resources" / "lua" / "like_toggle.lua").read_text(encoding="utf-8")
RESULTS_DIR = Path(__file__).resolve().parent / "results"

POST_ID = "lua-vs-naive-probe"
LIKE_SET = f"post:like:{{probe}}:{POST_ID}"
DIRTY_SET = "post:like:dirty:probe"
RANKING_ZS = "post:ranking:likes:probe"
WEIGHT = 3


def _reset(client: redis.Redis) -> None:
    client.delete(LIKE_SET, DIRTY_SET, RANKING_ZS)


def _lua_toggle(client: redis.Redis, user_id: str, weight: int) -> None:
    client.eval(LUA_SCRIPT, 3, LIKE_SET, DIRTY_SET, RANKING_ZS, user_id, POST_ID, weight)


def _naive_toggle(client: redis.Redis, user_id: str, weight: int) -> None:
    # Deliberately the same logic as the script, without the script's atomicity.
    if client.sismember(LIKE_SET, user_id):
        client.srem(LIKE_SET, user_id)
        client.zincrby(RANKING_ZS, -weight, POST_ID)
    else:
        client.sadd(LIKE_SET, user_id)
        client.zincrby(RANKING_ZS, weight, POST_ID)
    client.sadd(DIRTY_SET, POST_ID)
    client.scard(LIKE_SET)


TOGGLES = {"lua": _lua_toggle, "naive": _naive_toggle}


def _burst(toggle, pool: redis.Redis, users: list[str], weight: int, threads: int) -> None:
    barrier = threading.Barrier(threads)

    def worker(user_id: str) -> None:
        barrier.wait()
        toggle(pool, user_id, weight)

    with ThreadPoolExecutor(max_workers=threads) as executor:
        list(executor.map(worker, users))


def _read_state(client: redis.Redis, weight: int) -> dict:
    scard = int(client.scard(LIKE_SET))
    raw = client.zscore(RANKING_ZS, POST_ID)
    zscore = int(raw) if raw is not None else 0
    expected = scard * weight
    return {
        "scard": scard,
        "zscore": zscore,
        "expectedZscore": expected,
        "drift": zscore - expected,
    }


def run(path: str, host: str, port: int, threads: int, rounds: int, weight: int, db: int, protocol: int) -> dict:
    toggle = TOGGLES[path]

    # One connection per thread is what makes the race real; a shared connection
    # would serialise the naive path through the client instead of through Redis.
    # protocol=2: the Windows redis-server build used for the committed run is 5.0.x
    # and does not answer the RESP3 HELLO handshake redis-py 8 sends by default.
    pool = redis.ConnectionPool(
        host=host, port=port, db=db, protocol=protocol, max_connections=threads + 4
    )
    client = redis.Redis(connection_pool=pool)
    client.ping()

    distinct_users = [f"probe-user-{index}" for index in range(threads)]
    _reset(client)
    _burst(toggle, client, distinct_users, weight, threads)
    distinct = _read_state(client, weight)
    distinct["consistent"] = distinct["drift"] == 0 and distinct["scard"] == threads

    same_user_rounds = []
    for _ in range(rounds):
        _reset(client)
        _burst(toggle, client, ["probe-user-shared"] * threads, weight, threads)
        same_user_rounds.append(_read_state(client, weight))
    _reset(client)

    drifts = [entry["drift"] for entry in same_user_rounds]
    return {
        "path": path,
        "distinctUserBurst": distinct,
        "sameUserBurst": {
            "rounds": rounds,
            "driftedRounds": sum(1 for entry in same_user_rounds if entry["drift"] != 0),
            "consistentRounds": sum(1 for entry in same_user_rounds if entry["drift"] == 0),
            "maxAbsDrift": max((abs(value) for value in drifts), default=0),
            "meanAbsDrift": round(statistics.fmean(abs(value) for value in drifts), 3) if drifts else 0,
            "scardValues": sorted({entry["scard"] for entry in same_user_rounds}),
            "samples": same_user_rounds,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=6390)
    parser.add_argument("--db", type=int, default=0)
    parser.add_argument("--protocol", type=int, default=2, choices=(2, 3))
    parser.add_argument("--threads", type=int, default=50)
    parser.add_argument("--rounds", type=int, default=30)
    parser.add_argument("--weight", type=int, default=WEIGHT)
    parser.add_argument("--label", default="redis")
    parser.add_argument("--out-dir", type=Path, default=RESULTS_DIR)
    args = parser.parse_args()

    probe = redis.Redis(host=args.host, port=args.port, db=args.db, protocol=args.protocol)
    info = probe.info("server")
    version = info.get("redis_version", "unknown")
    probe.close()

    results = {
        "date": datetime.now(timezone.utc).strftime("%Y%m%d"),
        "recordedAtUtc": datetime.now(timezone.utc).isoformat(),
        "label": args.label,
        "target": f"{args.host}:{args.port}/{args.db}",
        "redisVersion": version,
        "threads": args.threads,
        "rounds": args.rounds,
        "weight": args.weight,
        "paths": {},
    }

    for path in TOGGLES:
        results["paths"][path] = run(
            path, args.host, args.port, args.threads, args.rounds, args.weight, args.db, args.protocol
        )
        entry = results["paths"][path]
        print(
            f"[{path:5}] distinct: scard={entry['distinctUserBurst']['scard']} "
            f"zscore={entry['distinctUserBurst']['zscore']} "
            f"consistent={entry['distinctUserBurst']['consistent']} | "
            f"same-user: {entry['sameUserBurst']['driftedRounds']}/{entry['sameUserBurst']['rounds']} rounds drifted, "
            f"max|drift|={entry['sameUserBurst']['maxAbsDrift']}"
        )

    args.out_dir.mkdir(parents=True, exist_ok=True)
    stem = args.out_dir / f"lua-vs-naive-{results['date']}"
    stem.with_suffix(".json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")

    lua = results["paths"]["lua"]["sameUserBurst"]
    naive = results["paths"]["naive"]["sameUserBurst"]
    md = [
        f"# Lua atomic toggle vs naive round trips ({results['date']})",
        "",
        f"Produced by `python benchmark/concurrency/lua-vs-naive-consistency.py "
        f"--host {args.host} --port {args.port} --threads {args.threads} --rounds {args.rounds}` "
        f"against redis-server {version}.",
        "",
        "The invariant is `zscore == scard * weight`: every member of the like-set "
        "contributes exactly one signed weight to the hot-ranking ZSET. A round that "
        "breaks it is a round where the two structures disagree about the same like.",
        "",
        f"| Path | Distinct users, one like each | Same user, {args.threads} concurrent toggles ({args.rounds} rounds) |",
        "| --- | --- | --- |",
        f"| Lua atomic script | scard={results['paths']['lua']['distinctUserBurst']['scard']}, "
        f"zscore={results['paths']['lua']['distinctUserBurst']['zscore']} "
        f"({'consistent' if results['paths']['lua']['distinctUserBurst']['consistent'] else 'drift'}) | "
        f"{lua['driftedRounds']}/{lua['rounds']} rounds drifted, max abs drift {lua['maxAbsDrift']} |",
        f"| Naive round trips | scard={results['paths']['naive']['distinctUserBurst']['scard']}, "
        f"zscore={results['paths']['naive']['distinctUserBurst']['zscore']} "
        f"({'consistent' if results['paths']['naive']['distinctUserBurst']['consistent'] else 'drift'}) | "
        f"{naive['driftedRounds']}/{naive['rounds']} rounds drifted, max abs drift {naive['maxAbsDrift']} |",
        "",
        "## What this run does not show",
        "",
        "- It is a Redis-only probe. It does not exercise the HTTP like path, MySQL "
        "membership or `LikeSyncTask`; `benchmark/concurrency/like-concurrency.ps1` covers "
        "the end-to-end three-view agreement on the full stack.",
        "- A Redis version's execution model is what is being measured, not this "
        "repository's latency. Timings are not recorded on purpose.",
        "- The naive path is a faithful re-implementation of the same steps, but it is "
        "not a code path the application ships. It is the control, not a regression.",
        "",
    ]
    stem.with_suffix(".md").write_text("\n".join(md), encoding="utf-8")
    print(f"wrote {stem.with_suffix('.json')}")
    print(f"wrote {stem.with_suffix('.md')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
