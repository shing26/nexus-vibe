# R3-1 — the public capabilities endpoint stops re-probing on every read

Status: **frozen contract, 2026-11-19.** Base commit `5b99407`
(merged master, PR #52). Branch: `codex/r3-1-capability-throttle`,
worktree `.worktrees/r3-1`.

## Goal

The backlog's R3-1: `GET /api/v1/status/capabilities` evaluates the whole
`deps` health group on every call — an Elasticsearch HTTP probe with a 2 s
timeout, the LLM check, a Redis ping and a database validation query — and
GETs are unthrottled: `RateLimitInterceptor` returns true for every non-POST
and is not registered on `/api/v1/status/**`. An anonymous caller can
multiply the probes at will. Raised by round 3's degradation banner; the
contract that created the endpoint forbade inventing a limiter then, which
is why this is its own round.

## Approach: a TTL read cache, not a limiter

The acceptance allows either "at most one probe per capability per TTL" or
"refused by a limiter". This round takes the cache branch, because the
projected limiter degrades exactly when the storm matters: the interceptor
bypasses itself when Redis is unavailable, and Redis is one of the
dependencies the endpoint reports on. A short TTL cache on the read model's
probe is simpler, needs no Redis, and gives a strictly stronger guarantee —
within the window the endpoint performs **zero** probes, not one per caller.

- `DegradedCapabilityService` caches its shaped result. A read inside the
  TTL window answers from the cache without touching `HealthEndpoint`; the
  first read after expiry re-probes once. Double-checked locking; the cached
  value is the same immutable list shape as today.
- TTL is `campus.status.capabilities-ttl-seconds`, default **15**; values
  `<= 0` disable caching entirely (every read probes, the pre-round behavior).
- The clock is injected for tests; production uses the system clock. The
  actuator surfaces are untouched: `/actuator/health`,
  `/actuator/health/servable` and `/actuator/health/deps` keep their exact
  bytes and their own probe behavior.
- Honest freshness cost, stated rather than hidden: a capability change can
  take up to the TTL to reach the banner. 15 s is far inside the pace at
  which a dependency dies; the banner already says "may be incomplete", not
  "is down this instant".

## Allowed paths

- `plans/r3-1-capability-throttle.md` (this contract)
- `docs/tickets/r3-1-capability-throttle.md`
- `docs/INDEX.md`
- `README.md` (test-count updates only)
- `src/main/java/com/nexus/campus/config/health/DegradedCapabilityService.java`
- `src/test/java/com/nexus/campus/config/health/DegradedCapabilityServiceTest.java`
- `src/test/java/com/nexus/campus/controller/CapabilityStatusIntegrationTest.java`

Anything else requires an amendment before it is edited.

## Acceptance criteria

1. Two reads inside the TTL window trigger exactly one `healthForPath("deps")`
   call; the next read after expiry triggers one more (asserted with a fake
   clock and a counting mock, deterministic, no Spring context).
2. A TTL of 0 disables the cache: every read probes, the pre-round behavior.
3. Two consecutive anonymous GETs to `/api/v1/status/capabilities` return
   byte-identical bodies inside the window, through the real filter chain.
4. `/actuator/health` and `/actuator/health/servable` stay byte-identical to
   today's pinned bytes (the existing test in
   `CapabilityStatusIntegrationTest` keeps passing unmodified).
5. The vocabulary mapping tests keep passing unchanged in substance; no
   existing test is deleted, skipped, or weakened.
6. `mvn -B test`, `npm run test -- --pool=threads`, `npm run lint`,
   `npm run build` pass; README counts updated in the same change.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test -- --pool=threads --maxWorkers=4
cd frontend && npm run lint
cd frontend && npm run build
git diff 5b99407..HEAD --stat
```

The independent Verifier re-runs these and binds evidence to HEAD SHA plus
`git status --short`. Implementer logs are not evidence.

## Forbidden

- Touching `RateLimitInterceptor`, `WebMvcConfig`, any health indicator, or
  any actuator endpoint: the cache lives in the read model only, and the
  pinned actuator bytes must not move.
- Caching anything on the actuator paths (including `/actuator/health/deps`).
- Leaking component names, probe reasons or URLs through the cached model —
  the closed-vocabulary property of ADR-0019 holds for cached answers too.
- A Redis-dependent limiter on this path.
- `pom.xml`, `package.json`, any new dependency, any schema change.

## Rejected

- **Registering `RateLimitInterceptor` on `/api/v1/status/**` for GETs.** The
  interceptor bypasses itself when Redis is unavailable — the one scenario in
  which a probe storm is actually dangerous — and it would refuse legitimate
  visitors while still allowing the first hit to probe. Wrong tool for an
  expensive-read problem.
- **Caching inside the health indicators.** That would change
  `/actuator/health`'s answers — the bytes the round-3 contract pinned — and
  blur the audience split ADR-0007 exists for.
- **A per-capability cache.** More moving parts for the same effect; the
  read model is one shaped list, and the probe cost is the group evaluation
  as a whole.
