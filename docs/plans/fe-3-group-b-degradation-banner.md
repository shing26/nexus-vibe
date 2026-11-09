# fe-3-group-b — frozen contract: degradation visible in the product

Status: **frozen, 2026-11-09.** Base commit `1ce682a`. Ticket:
[frontend-polish-round-3](../tickets/frontend-polish-round-3.md), item 3.

## The constraint that shapes this

ADR-0007 split health by audience on purpose: nginx forwards the public
`/actuator/health` onto the `servable` group (which contains `db` alone) and keeps
`/actuator/health/deps` closed at the edge
([nginx.conf:103-114](../../docker/nginx/nginx.conf),
[application.yml:239-249](../../src/main/resources/application.yml)). So the public
surface answers "can users still use this site" — which is `UP` during exactly the outage
a banner exists to explain. A banner reading the public health URL would say "fine" while
search is broken.

`DependencyHealth.DEGRADED` exists and is asserted by `HealthSemanticsIntegrationTest`.

## Scope

A small public read-only status endpoint exposing the one fact a visitor can act on —
which optional capability is degraded and what it means for them — plus a dismissible
banner that renders it and links to ADR-0007.

Not the raw `deps` document. A shaped read model with a closed vocabulary.

## Acceptance criteria

- B1: `GET /api/v1/status/capabilities` returns a list of degraded capabilities from a
  **closed** set, each with a stable `key` and a user-facing `impact` sentence. With
  Elasticsearch stopped it reports search-degraded; with everything up it returns an empty
  list.
- B2: The response never contains a dependency hostname, port, URL, stack trace, raw
  exception message, or any field copied wholesale from the `deps` group. A test fails if
  the DTO gains a field that is not on an explicit allow-list.
- B3: The endpoint is public (no token) and rate-limited by the same per-IP POST limiter
  family — it is a GET, so confirm the existing GET behaviour and do not invent a new
  limiter; if GETs are unthrottled today, say so in the report rather than adding one.
  Publicness is decided in `JwtAuthFilter.isPublicGet`, which is why that file is now in
  Allowed paths; the anonymous-GET test belongs in `src/test/**` and must hit the
  contracted path through MockMvc, not a rewritten one.
- B4: The banner renders only when the list is non-empty, names the consequence in the
  user's words ("search results may be incomplete"), and links to ADR-0007.
- B5: Dismissal is per-session (sessionStorage), and a capability that was not in the
  dismissed set still shows the banner.
- B6: `/actuator/health` and `/actuator/health/servable` answers are unchanged, byte for
  byte; a test pins the servable group still reading `UP` while the top level reads
  `DEGRADED` under the same condition `HealthSemanticsIntegrationTest` already uses.
- B7: nginx exposes exactly the new path and no other actuator path; assert by reading the
  config in a test or by a documented manual check, and state which.

## Allowed paths

- `src/main/java/com/nexus/campus/config/health/**`
- `src/main/java/com/nexus/campus/controller/` — one new controller only
- `src/main/java/com/nexus/campus/dto/` — one new DTO only
- `src/main/java/com/nexus/campus/security/JwtAuthFilter.java` — **one entry in
  `isPublicGet` (`:84-103`) for `/api/v1/status`.** Granted 2026-11-09 after the first
  implementer stopped on it: B3 requires an anonymous GET, and the filter is the only
  place that decides which GETs are anonymous. The grant is a single prefix, not a
  rewrite of the filter, and nothing else may change in this file.
- `src/main/resources/application.yml` — only if a health group needs a companion
- `docker/nginx/nginx.conf`
- `frontend/src/components/` — one new banner component + its test
- `frontend/src/components/layout/MainLayout.tsx` — to host the banner. Pin it here;
  `Navbar.tsx` and `Sidebar.tsx` belong to other work, and `MainLayout.tsx` is the one
  shell every route renders under.
- `frontend/src/api/` — one typed call
- `src/test/java/com/nexus/campus/**`
- **`README.md` is not yours.** Four groups run at once and the count badge is one line;
  the integrator updates it once after the merge. Report your counts instead.

## Forbidden

- Opening `/actuator/health/deps` at the edge, or re-pointing the public
  `/actuator/health`. ADR-0007 decided both; this adds a surface, it does not re-point one.
- Returning raw dependency detail of any kind.
- Any schema change.
- Weakening, deleting or skipping an existing assertion, including
  `HealthSemanticsIntegrationTest`; disabling lint or typecheck.
- Pushing, opening a PR, or editing this file. **Do commit** on your own branch: the
  acceptance chain binds evidence to a HEAD SHA, and an uncommitted tree has none.

## Verification commands

```bash
mvn -B test
cd frontend && npm run test && npm run lint && npm run build
```

## Rejected alternatives

- **Pointing the banner at the public `/actuator/health`.** It answers servability, which
  is `UP` during the outage the banner explains. A banner that says "fine" while search is
  broken is worse than no banner.
- **A websocket or SSE status stream.** The state changes on the order of minutes; a
  request on mount plus a refetch interval is enough, and a stream would add a connection
  the deployment has to keep alive through the tunnel.
- **Reading `deps` server-side and forwarding it.** That is the raw document with a
  different URL. The shaped model is the point.
