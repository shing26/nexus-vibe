# R3-1 — the public capabilities endpoint stops re-probing on every read

Status: **implemented, awaiting independent verification.** Contract:
`plans/r3-1-capability-throttle.md` (frozen before implementation). Branch:
`codex/r3-1-capability-throttle`, based on merged master `5b99407` (PR #52).

## Scope

The backlog's R3-1, raised by round 3's degradation banner: the anonymous
`GET /api/v1/status/capabilities` evaluated the whole `deps` health group on
every call — an Elasticsearch HTTP probe with a 2 s timeout, the LLM check, a
Redis ping and a database validation query — and GETs are unthrottled.

Changes:

- `DegradedCapabilityService` caches its shaped result behind a TTL
  (`campus.status.capabilities-ttl-seconds`, default 15; `<= 0` disables the
  cache). A read inside the window performs no probe at all; the first read
  after expiry re-probes once. Double-checked locking, injected clock.
- Nothing else moves: `RateLimitInterceptor`, `WebMvcConfig`, every health
  indicator and every actuator endpoint are untouched, so the pinned
  `/actuator/health` and `/actuator/health/servable` bytes cannot change.
- Honest freshness cost: a capability change can take up to the TTL to reach
  the banner — stated in the contract, not hidden.

## Acceptance

- Backend **561 tests**, 0 failures, JaCoCo floor met (was 557); frontend
  **152 tests / 29 files**, 0 failures; oxlint clean; build clean.
- New unit tests with a mutable clock and a counting mock: two reads inside
  the window probe once and share the cached list; the first read after
  expiry probes again; TTL 0 disables the cache.
- New integration test: two consecutive anonymous GETs answer byte-identically
  through the real filter chain.
- The existing `healthAnswersStayByteForByte` pinned-actuator test passes
  unmodified.

## Files

`plans/r3-1-capability-throttle.md`, `docs/tickets/r3-1-capability-throttle.md`,
`docs/INDEX.md`, `README.md` (counts only),
`src/main/java/com/nexus/campus/config/health/DegradedCapabilityService.java`,
`src/test/java/com/nexus/campus/config/health/DegradedCapabilityServiceTest.java`,
`src/test/java/com/nexus/campus/controller/CapabilityStatusIntegrationTest.java`.

## Rejected

- **Registering `RateLimitInterceptor` on this path for GETs**: it bypasses
  itself when Redis is unavailable — the one scenario where a probe storm is
  dangerous — and would refuse legitimate visitors while still letting the
  first hit probe.
- **Caching inside the health indicators**: it would change the pinned
  actuator bytes and blur ADR-0007's audience split.
- **A per-capability cache**: more moving parts for the same effect; the
  probe cost is the group evaluation as a whole.
