# Contract Truthfulness and the Product Loop

Source: the product-manager review of 2026-09-15, and the gap list left over
from [production-readiness-assessment-2026-09.md](../research/production-readiness-assessment-2026-09.md).
North star: the API says one thing in two places and they disagree, the repo
has 310 backend tests and zero frontend tests, and a phone cannot reach the
message page. This round fixes what is measurable about those three.

Baseline before R1: **307 tests across 46 test classes**, 0 failures, 0 errors,
0 skipped. R1 ends at 310 with its three new cases. My first read of this number
was 309/48, obtained by summing `target/surefire-reports/*.txt`, and it was
wrong: that directory keeps reports from earlier filtered runs. The run summary
line is the only trustworthy source here, and it agrees with the 307/46 the
previous ticket recorded.

Order is R1 -> R2 -> R3 -> R4 -> R5 -> R6. R1 goes first not because it is
bigger but because it changes the same controller files with **no wire change
at all**, so R2's commits only have to carry one idea each.

Decided before these tickets were written, and not open for re-litigation:
HTTP status is the single source of truth and `code` is derived from it;
no new machine-readable response field; controllers stop building error
objects and throw instead; **no `ErrorCode` catalogue this round**.

## R1 - Responses stop serialising persistence entities

Status: done on `codex/http-contract-and-product-loop`. 310 tests green, and the
reflection scan came back with an empty offender list, so no handler anywhere
mentions an entity type.

**Scope:** make "an entity field reaches the internet" impossible by
structure rather than by memory, without changing a single response body
except one key that was always `null`.

Today nine response points serialise a MyBatis entity. Only one of them can
leak anything: `SysUser.password`, and it is held back by three hand-written
`user.setPassword(null)` calls that a fourth call site can forget. The other
four entity types carry no secret - `VibeComment`, `SysMessage`, `VibeTag`,
`Channel` expose at worst internal lifecycle integers. So the value of this
ticket is the boundary, not the fix, and it is sold as that.

- New `CommentVo`, `MessageVo`, `TagVo`, `ChannelVo`, `ProfileVo`, mapped by
  hand like `PostPageVo`, copying field names and values **verbatim** so the
  JSON is byte-identical. `ProfileVo` omits `password`.
- `SysUser.password` becomes `@JsonProperty(access = WRITE_ONLY)`, and the
  three `setPassword(null)` lines are deleted with it.
- A scan test asserts no controller signature or return generic mentions
  `com.nexus.campus.entity.*`.

The one intentional wire change: `GET /users/profile`, `PUT /users/profile`
and `GET /auth/profile` lose a `password` key whose value was always `null`.
Nothing in `frontend/src` reads a response `password` (checked), and the
request DTOs are untouched.

**Acceptance:**
- The three profile endpoints return the same field names and values as
  before minus `password`; comment, message, tag and channel responses are
  unchanged key for key.
- The scan test is red the moment an entity type name reappears in a
  controller signature, and there is no allowlist.
- Unaffected: `PostPageVo`, `UserPublicVo`, `AiLogVo`, `UserProfileSummary`
  paths, which already returned VOs; the 307 tests that existed before R1; request DTOs;
  the database schema.

**Files:** `src/main/java/com/nexus/campus/dto/{CommentVo,MessageVo,TagVo,ChannelVo,ProfileVo}.java`,
`src/main/java/com/nexus/campus/entity/SysUser.java`,
`src/main/java/com/nexus/campus/controller/{UserController,AuthController,CommentController,MessageController,TagController,CategoryController}.java`,
`src/test/java/com/nexus/campus/contract/NoEntityInControllerTest.java`.

## R2 - HTTP status is the truth, and errors are thrown

Status: done on `codex/http-contract-and-product-loop`. 317 tests green, 0 failures,
0 errors, 0 skipped (from R1's 310: six cases in `ErrorSemanticsTest`, one in
`NoStaleStatusAssertionTest`). `ApiResponse.error(int, String)` and the three
`unauthorized` / `forbidden` / `notFound` shorthands are deleted, so the only way a
controller can answer a failure is to throw something with a status attached.

What the last two commits turned out to be carrying: `handleBusinessError` used to
answer every `IllegalArgumentException` **and** `IllegalStateException` with `400`
and `e.getMessage()` verbatim. That is the leak the assessment under-described - it
is not limited to the register race, any library at any depth that throws an IAE
with an internal string in it was quoting it back to the client. An unmapped IAE
now keeps the 400 and loses the message; an ISE is no longer mapped at all, which
puts it on the 500 side where the stack is logged and the client hears nothing.
`ErrorSemanticsTest` probes both with strings copied from real failures
(`Duplicate entry 'shing' for key 'sys_user.uk_username'`, an upstream LLM error
body) so the assertion is about the leak, not about the class name.

**Scope:** 33 controller sites answer `200 OK` while their envelope says 401,
403, 404 or 400. `POST /api/v1/auth/login` with a wrong password returns
`200 {"code":401}` on the live deployment today; so does a missing post with
`404`. Everything downstream - nginx logs, `http_server_requests_*`, any
client that behaves differently per status, the "5xx ratio" alert rule - is
reading a number that is not the one the envelope holds.

Mechanism first, then migrate by controller, then delete the escape hatch:

- `BusinessException(HttpStatus status, String safeMessage)`.
- `ApiResponse.error` takes `HttpStatus` only; `code` comes from
  `status.value()` and `traceId` from `status.is5xxServerError()`. A code that
  disagrees with its status is no longer expressible, which is why no test
  pairs them endpoint by endpoint.
- `GlobalExceptionHandler` returns `ResponseEntity<ApiResponse<Void>>` so it
  is the only place that sets a status. Controller success paths keep their
  existing `ApiResponse<T>` signatures.
- `handleBusinessError` stops echoing `e.getMessage()`. The service-layer
  `IllegalArgumentException`/`IllegalStateException` throws on a request path
  each become a `BusinessException` with an explicit status.
- Eight commits, one per controller group, then a final commit that deletes
  the `ApiResponse.error(int, String)` overload so anything unmigrated stops
  compiling.

E2: the mechanical mapping is `status = the current code value`. Beyond that, a
closed list of corrections where the current status is not merely mislabelled
but wrong:

- `PostController` pin: "not found" stays 404, "exists but cannot be pinned"
  becomes 409.
- `CommentController` delete-as-stranger and `PostController` edit-as-stranger:
  400 -> 403. It is an authorisation refusal, not a malformed request.
- `VibePostServiceImpl` not-found throws -> 404; the fork and template-state
  throws -> 409.
- `AuthController` login/register and `AdminController` reset-password lose
  their `catch (RuntimeException)`. Register is the one that matters: the
  service throws a bare `RuntimeException("Username already exists.")`, so the
  coarse catch is currently load-bearing for the message, and when `insert`
  wins a race against the pre-check the MySQL constraint name and SQL fragment
  reach an unauthenticated public client inside `"Registration failed: ..."`.
  That is reachable today, not theoretical. The duplicate-key path becomes an
  explicit `BusinessException(CONFLICT)`.
- `UploadController` IO failure -> real 500 with a trace id.
- The two Chinese-language messages become English, matching the other 31.

**Acceptance:**
- Every migrated site answers with its real status; the 11 assertions that
  pair `status().isOk()` with a non-200 `$.code` are updated in the same
  commits, and a scan test keeps that count at zero afterwards.
- Two assertions move from 400 to 403 with the semantics they describe.
- `ResponseContractTest.bodyOf` takes the expected status; 4xx carries no
  `traceId` and 5xx does, asserted both directions.
- A wrong password yields `401` and the login page shows the server's message
  instead of its own fallback.
- Unaffected: the 9 `401` assertions that come from `JwtAuthFilter` and
  already carried a real status; the validation-handler 400s and the 405
  handler, which were already honest; `RateLimitInterceptor`, which already
  writes a real 429; envelope key set; the 310 test count R1 left behind.
- This round does not add `ErrorCode`. `HttpStatus` absorbs the 27 magic
  numbers, and the assessment's other justification - that the frontend
  branches on message text - turned out to be false: all 13 frontend uses of
  `message` are display fallbacks, and nothing reads `code` at all.

**Files:** `src/main/java/com/nexus/campus/exception/BusinessException.java`,
`src/main/java/com/nexus/campus/dto/ApiResponse.java`,
`src/main/java/com/nexus/campus/config/GlobalExceptionHandler.java`,
`src/main/java/com/nexus/campus/controller/*`,
`src/main/java/com/nexus/campus/service/impl/{VibePostServiceImpl,VibeCommentServiceImpl}.java`,
`src/main/java/com/nexus/campus/service/impl/SysUserServiceImpl.java`,
affected tests under `src/test/java/com/nexus/campus/controller/`.

## R3 - A frontend test surface that can fail

Status: done on `codex/http-contract-and-product-loop`. **15 tests in 4 files**,
`npm run test` green, and the step now sits between Lint and Build in the CI
frontend job, which until this commit was lint and `tsc` only.

Two of the acceptance checks were proven by breaking the thing under test rather
than by reading the green run. With `if (!refreshPromise)` replaced by `if (true)`,
exactly one test goes red - `shares one refresh across concurrent 401s` - so the
single-flight is pinned, not merely exercised. With a new
`src/__probe.tsx` holding one bare `await apiClient.get(...)`, the scan reports
`src/__probe.tsx:4 - no enclosing try`. Both probes were reverted; the file is gone.

The interceptor tests drive real axios with a fake `adapter` on both `apiClient`
and the global instance, so the request interceptor, the 401 branch, the refresh,
and the replay all execute, and the rejection is a genuine `AxiosError` carrying
genuine `AxiosHeaders`. One harness detail earned its comment: the first version
kept the config objects it had seen and asserted on their `Authorization` header,
and passed for the wrong reason - the interceptor retries by mutating that same
headers object, so both entries reported the post-refresh token. It records
snapshots now.

R2's fallout is fixed here as planned: `serverErrorMessage(error, fallback)` reads
the envelope and is the only thing the three admin panels use, so
`Request failed with status code 403` cannot reach a user. `PostCard`'s fork reads
`postId` behind a check instead of throwing its way into its own `catch`.
LoginPage's first test is the proof of the whole chain: the sentence
`Invalid username or password.` was not reachable from that page before round six.

**Scope:** 310 backend tests, 17 alert-bridge tests, 0 frontend. The one place
a user can actually be hurt has no gate.

- vitest + jsdom + `@testing-library/react`; `npm run test` joins the CI
  frontend job, which until now was lint and tsc only.
- Four cases: the response interceptor (single-flight refresh and retry on
  401, plain reject on 4xx, toast carrying the trace id on 5xx); `LoginPage`
  surfacing the server message rather than `Login failed`;
  `AiReviewTerminal`'s three states; and a source scan asserting every
  `apiClient.` call sits inside a `try` **whose block has a `catch`**, or a
  `queryFn`/`mutationFn`. A `try`/`finally` without a `catch` does not swallow
  a rejection, it re-throws, so "is it in a try" is the wrong question - that
  was the first version of this census and it had to be redone. Re-scanned the
  right way: 41 call sites, none unguarded. The test therefore guards against a
  future bare call, not against anything R2 introduces.
- R2's own fallout, fixed here because R2 causes it: `AuditPage`,
  `AgentLogsPage` and `DashboardPage` render `(error as Error)?.message`,
  which for an `AxiosError` is `Request failed with status code 403`. Those
  endpoints answer 200 today, so the query never enters its error state and
  nobody has seen that string yet. `PostCard`'s `res.data.data.postId` reaches
  its catch by throwing a TypeError; it becomes an explicit check.

What R2 does *not* do is create an unhandled rejection anywhere: every awaited
call is already inside a `catch`-bearing `try` or a react-query function that
captures rejection as `isError`. What changes is which branch renders. Today a
`200` carrying `code:401` makes `res.data.data` null, the next line throws a
TypeError into the same `catch`, `err.response` is undefined, and the UI shows
its own fallback string; after the switch the `catch` sees a real response and
shows the server's sentence. Better copy, different branch.

**Acceptance:**
- `npm run test` runs in CI and fails on a deliberately broken interceptor.
- The three admin error panels show the server's sentence, not axios's.
- Unaffected: no component's rendered markup on the happy path; the lint and
  build steps; the backend suite.

**Files:** `frontend/package.json`, `frontend/vitest.config.ts`,
`frontend/src/api/client.ts`, `frontend/src/pages/{LoginPage,AuditPage,AgentLogsPage,DashboardPage}.tsx`,
`frontend/src/components/PostCard.tsx`, new tests under `frontend/src/**.test.tsx`.

## R4 - The product loop becomes countable

Status: done on `codex/http-contract-and-product-loop` except for the drill leg, which
runs with R5's. 328 tests green (from R2's 317: 4 in `ProductMetricsTest`, 4 in
`FunnelAggregateTaskTest`, 3 in `FunnelAggregateIntegrationTest`).

Four counters in `metrics/ProductMetrics` (`user_registered_total`,
`post_created_total{status}`, `post_audited_total{action}`,
`comment_created_total`) and two daily gauges from `task/FunnelAggregateTask`
(`funnel_activation_ratio`, `funnel_active_content_d7_ratio`), one
`nexus-product-loop.json` panel set in the existing provider, and a drill step that
registers, publishes, moderates and replies for real and then reads the scrape back.

Three things this ticket turned up that were not on it:

- `SysUserMapper.selectRecentActiveUsers` was `DATEADD('DAY', -7, CURRENT_TIMESTAMP)
  JOIN … SELECT DISTINCT u.* … ORDER BY p.create_time`. Both halves are H2-only: MySQL
  has no `DATEADD`, and its `ONLY_FULL_GROUP_BY` refuses an ordering on a column the
  projection does not carry. `GET /api/v1/stats/active-users` is a public endpoint, so
  the deployment answers 500 there today while all 317 tests stayed green. Fixed by
  binding the cutoff from Java and replacing the join with `EXISTS` plus an aliased
  ranking column.
- `Map.of(...).getOrDefault(null, "other")` throws — immutable maps refuse null probes
  even as keys. That was one line into `recordPostCreated`, on the request path of a
  post that had already been written. `ProductMetricsTest` is what caught it; the mock
  `MeterRegistry` most tests would have used could not have.
- One Chinese validation message survived R2's rule that envelope text is English
  (`RegisterRequest:32`). It is now English, matching its 20 siblings and the copy the
  register page already shows.

Decisions worth keeping: the activation window is a 30-day cohort rather than all-time,
because a lifetime denominator buries this week's registrations under last year's demo
accounts; a ratio whose numerator outsteps the denominator (content whose author was
deleted, since `vibe_post.user_id` is no foreign key) clamps to 1.0 and logs the
disagreement; and the sweep runs once at boot as well as daily, so the gauges cannot
read a zero that means "nobody has looked yet". The boot run is gated on
`campus.scheduling.enabled`, which surefire sets false, so no test context queries a
database it is about to change.

Two dialects, two proofs: `FunnelAggregateIntegrationTest` executes the three reads on
H2 so a typo cannot survive as "the unit test stubs the mapper", and the drill's
`product-loop-drives-the-counters` step executes them on MySQL while asserting the
ratios land inside 0..1.

**Scope:** the seven existing metrics are all operational. Nothing answers
"did the thing we built get used".

- Counters in the service methods that are the events: `user.registered`,
  `post.created{status}`, `post.audited{action}`, `comment.created`.
- `FunnelAggregateTask`, daily, two gauges from MySQL:
  `funnel.activation.ratio` - share of registrations that published a first
  post within 7 days - and `funnel.active_content_d7.ratio`, computed from
  `vibe_post`/`vibe_comment` activity rather than a new column. There is no
  `last_login` or `last_active_at` on `SysUser`, and adding one to write it on
  every authenticated request is a schema change plus hot-path write
  amplification bought for a number that content activity already gives
  better. The cost is stated plainly: a visitor who reads and never posts is
  invisible to this.
- One `nexus-product-loop.json` dashboard in the existing provider.

**Acceptance:**
- The drill registers, posts and approves, then asserts the counters moved and
  that a forced aggregation leaves both ratios inside `0..1`.
- On an empty database the task publishes `0`, not NaN and not absent.
- Unaffected: the 7 operational metrics; the 4 alert rules; the monitoring
  profile's opt-in behaviour.

**Files:** `src/main/java/com/nexus/campus/task/FunnelAggregateTask.java`,
metric lines in `src/main/java/com/nexus/campus/service/impl/*`,
`docker/observability/grafana/provisioning/dashboards/json/nexus-product-loop.json`,
`benchmark/observability/drill.ps1`.

## R5 - Containers: non-root, and nginx that starts without luck

Status: code done on `codex/http-contract-and-product-loop`; the drill steps
(`non-root-app-and-the-root-owned-volume-upgrade` plus the R4 funnel step) run in one
pass at the end of the round, so this line is not yet a verified claim.

`Dockerfile` builds `appuser` at uid/gid 10001 (`--no-create-home`, `nologin`), copies
the jar `--chown`, pre-creates and owns `/app/uploads` and `/app/logs`, and switches
`USER` after the `HEALTHCHECK` line. 10001 rather than 1000 because uid 1000 on a real
host is usually somebody's account, and a container that can write mounted volumes as
that uid can pass as them.

nginx drops the `upstream { server app:8080; }` block for
`resolver 127.0.0.11 valid=10s` plus a variable `proxy_pass`, and the `/actuator/health`
location rewrites to `/actuator/health/servable` before proxying because a variable
backend cannot carry a path. The cost is in the file's own header comment, not hidden:
OSS nginx cannot `keepalive` a per-request-resolved backend, so proxied requests now
open their own connection to Tomcat. `web` keeps `service_started`; the reasoning for
refusing `service_healthy` is recorded there too.

What R5 cannot ship without is the upgrade path, and that is the part a fresh install
never tests: Docker seeds a named volume from the image only while the volume is empty,
so this machine's `nexus-vibe_app-uploads` and `app-logs` are still root:root and a
non-root JVM cannot write them. The one-time `chown` is in
`docs/plans/pre-deployment-checklist.md`, and the drill step performs it for real —
including the negative proof, which throws if a non-root app *can* write a volume that
the step just handed to root, because that would mean the premise was never established.

**Scope:** the JVM runs as root, and nginx currently survives its own startup
race by crashing and being restarted.

- `appuser` uid 10001 in the runtime stage, `chown` on `/app`, `/app/uploads`,
  `/app/logs`.
- Docker seeds a named volume from the image only while the volume is empty.
  Both volumes already exist on this machine and are root-owned, so the upgrade
  path needs a one-time `chown`. It goes in the deployment checklist, and the
  drill gains a step that starts against a root-owned volume on purpose.
  Without that step the change is only proven on a fresh install, which is not
  where it will first break.
- nginx: `resolver 127.0.0.11 valid=10s` with a variable `proxy_pass` replaces
  the `upstream { server app:8080; }` block, so config load no longer needs
  `app` to resolve. `web` keeps `depends_on: service_started`.
  `service_healthy` would also stop the crash loop, and would additionally take
  the static site down whenever the API is down; a 502 on `/api/` with the SPA
  still served is the better failure.
- Not this round: `nginx-unprivileged`. It moves the listen port, the compose
  mapping and the log paths, and wants its own verification pass.

**Acceptance:**
- `id -u` inside the app container is 10001, uploads and JSON logs still
  succeed, and the healthcheck still passes.
- A cold `up --build` reaches a serving nginx without a restart counted by
  `docker inspect`.
- Unaffected: published ports (nginx:80 stays the only one); the monitoring
  profile; mem limits; the health groups.

**Files:** `Dockerfile`, `docker/nginx/nginx.conf`, `docker-compose.yml`,
`docs/plans/pre-deployment-checklist.md`, `benchmark/observability/drill.ps1`.

## R6 - A phone can reach the message page

Status: open.

**Scope:** not cosmetics. There is no hamburger in `Navbar` at all: below
`sm` the message and settings links are hidden, below `md` the username is
gone, so a logged-in phone user sees logo, search, theme, New Post, Logout and
cannot reach messages, settings or their own profile. Navigation exists only
as the home channel grid and the back button, and `Cmd+K` needs a keyboard.

- A `lg:hidden` bottom tab bar: home, channels, post, messages, profile. The
  profile tab carries settings and logout.
- `touch-action: manipulation` on the interactive shell, to drop the 300ms
  double-tap zoom delay.
- The code-block container on `PostDetailPage` goes `overflow-hidden` ->
  `overflow-x-auto`, so a long line on a phone can be read instead of lost.

**Acceptance:**
- At 390px every previously unreachable destination is reachable in one tap
  from the home page, asserted by a rendered-component test.
- The tab bar does not cover content: the last list item clears it at the
  narrowest tested height.
- Unaffected: desktop header and layout at `lg` and above; the existing
  Cmd+K palette.

**Files:** `frontend/src/components/MobileTabBar.tsx`,
`frontend/src/components/layout/MainLayout.tsx`,
`frontend/src/components/CodeBlock.tsx`, `frontend/src/index.css`.

## Out of scope this round

Centralised `@RequiresRole`, Flyway-ised migrations, frontend crash reporting,
`web` running unprivileged, cAdvisor, Loki, OpenTelemetry, Alertmanager, and an
`ErrorCode` catalogue. Session-based retention (a browse-only visit is not
counted) is a stated limitation of R4, not an oversight.
