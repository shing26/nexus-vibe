# Framework reinforcement - the gaps that were still real

Source: the cross-project framework audit of 2026-09-19 (`全项目框架补强建议.md`, §3.2 and the
cross-project table in §四). Its Nexus-Vibe rows were re-checked against the current tree before
anything was written, because three of the six listed gaps had already been closed by later
rounds. This ticket records what was actually left, what was done about it, and what is
deliberately not worth doing.

Status: implemented in the working tree on 2026-09-19. The full suite is green at
`399 tests / 0 failures / 0 errors / 0 skipped`, and that 399 comes from a **single** `mvn test`
summary line: the default heap could not fit the whole suite in one fork (that run died on a
native `malloc`, not on a test), but constraining surefire's `argLine` to
`-Xmx384m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -Xss1m` lets
the suite finish in one JVM, so the count no longer has to be assembled from batches. The
targeted suites are named under each item below.

> **One caveat on the `Files:` list below.** This round's `AiConfigValidator` /
> `AiConfigValidatorTest` never reached a commit: the evidence-and-gates round replaced them with
> `CampusAiProperties` before the branch was squashed, so `git log -S AiConfigValidator` finds
> nothing and the two paths named under `Files:` do not exist in the tree. Everything else this
> round wrote is in the commit. See `docs/adr/0013-config-validation-moves-to-validated-properties.md`.

## What the audit still had right

### F1 - The post application layer imported MyBatis mappers (N1/N2)

`VibePostServiceImpl` began with `import ...mapper.*`, held ten mappers, and did sensitive-word
filtering, prompt versioning, event publication and persistence in one class. The audit's
`N1`/`N2` were accurate.

**Scope:** put a repository interface between the application services and MyBatis, and split
the two long post methods by responsibility.

- `repository/` holds seven interfaces (`VibePostRepository`, `ChannelRepository`,
  `SysUserRepository`, `VibeTagRepository`, `PromptVersionRepository`, `VibeCommentRepository`,
  `AiReviewLogRepository`); `repository/impl/` holds the MyBatis adapters. The adapters keep
  the same mapper calls as before, so no SQL or transaction semantics changed.
- `PostCreationService` owns `createPost`; `PostEditService` owns `updatePost`;
  `PromptVersionRecorder` owns snapshot writes; `PostAgentEventPublisher` owns review/safety
  dispatch and the fail-closed enqueue path. `VibePostServiceImpl` is now a facade: creation and
  editing delegate, the rest of the queries, version restore, audit and delete use repositories.
- `RepositoryBoundaryTest` is a source scan (same idea as `NoEntityInControllerTest`) that fails
  if the post application services import or hold a mapper, so the boundary cannot quietly
  regress.

**Not in scope, on purpose:** converting the other services. `VibeCommentServiceImpl`,
`SysMessageServiceImpl` and the tasks keep their mappers. The audit's evidence was the post
module; a repository-per-service rewrite of the whole codebase is churn with no measured
benefit.

**Completed in the follow-up round.** The first pass drew the boundary around the post services
and stopped there, which left two halves of the same finding open. Both are now closed:

- `AiReviewService`, named in the audit's own evidence line (`AiReviewService.java:8-10,33-39`),
  still held `VibePostMapper`, `AiReviewLogMapper` and `VibeCommentMapper`. It now takes
  `VibePostRepository`, `AiReviewLogRepository` and `VibeCommentRepository`. The comment
  supersede filter (a MyBatis-Plus `LambdaUpdateWrapper`) moved into
  `MyBatisVibeCommentRepository.hideAiReviewComments`, where the framework type belongs.
- `VibePostRepository` still took and returned MyBatis `Page`, so the service compiled against
  MyBatis no matter what the imports said. The four pagination methods now take `page`/`size`
  and return `PageSlice<VibePost>` (records + total); only `MyBatisVibePostRepository` constructs
  a `Page`. `RepositoryBoundaryTest` was extended to fail if any file under `repository/`
  (excluding `repository/impl`) names a `com.baomidou` type, and `AiReviewService` joined the
  scanned application services.

**The duplicated half.** The same review flagged that `PostCreationService` and
`PostEditService` both repeated the sensitive-word/status/dispatch sequence, so a change to the
gate meant editing two files and hoping they stayed equal. That sequence now lives in
`PostPublishGate`: `applySensitiveWordPolicy` owns the DFA pass and the masking, and
`dispatchForNewPost` / `dispatchForEditedPost` are the two named entry points for the
differences that are real (an edit only resubmits changed text; a review re-runs only on a
content change, while safety also re-runs for a title-only change). The two enable flags moved
with it, so `campus.ai.review.enabled` and `campus.ai.safety.enabled` are now read in one place
instead of two. `VibePostEditAuditTest` drives both entry points through the gate, and
`VibePostServiceImplTest` covers the facade above them.

**Verified:** `RepositoryBoundaryTest`, `VibePostServiceImplTest`, `VibePostEditAuditTest`,
`PostAgentEventPublisherTest` - 22 tests, green.

### F2 - Illegal AI settings were accepted until the runtime path that used them

The audit's cross-project row 2 ("配置无 fail-fast") applied here: every value is bound with
`@Value`, so `campus.ai.llm.timeout=0s` or `campus.ai.review.max-attempts=0` started the
application and only surfaced later.

**Scope:** refuse to start, by property name, on the settings whose illegal value silently
degrades or breaks the pipeline.

- `AiConfigValidator` is a `BeanFactoryPostProcessor`, so it runs before any AI bean is
  constructed and reports every offending property in one message instead of the first one to
  throw. It validates `campus.ai.llm.endpoint` (absolute http(s) URL),
  `campus.ai.llm.model` (non-blank), `campus.ai.llm.timeout` (positive duration), and the
  positive-integer settings `review.lease-seconds`, `review.max-attempts`,
  `review.max-context-tokens`, `reconcile.stale-minutes`, `llm.breaker.failure-threshold`,
  `llm.breaker.open-seconds`.
- Absent properties keep the existing `@Value` defaults; the validator only judges what is
  present, and missing endpoint/model are the two values `LlmClient` already requires.
- `AiConfigValidatorTest` sets one illegal value per case and asserts the context fails with the
  property name in the stack trace: 12 tests including the valid-configuration control and a
  Compose-style underscore host.

**Deliberately not validated:** `campus.ai.llm.response-format`. The configuration comment
documents that an unrecognised value degrades into the strict mode and never breaks a call, so
turning that into a startup failure would contradict the recorded decision.

### F3 - Raw LLM request/response was not archived

The audit's cross-project row 3 ("原始报文归档缺失") applied here: the pipeline logs events and
outcomes, but there was no record of the exact JSON that crossed the wire.

**Scope:** an off-by-default side channel that can answer "what did we actually send" after a
disagreement, without changing the call path.

- `LlmCallRecord` carries timestamp, operation, model, raw request JSON, raw response JSON (null
  when no response arrived), outcome and error.
- `LlmCallArchive` is a functional interface with `NOOP`; `FileLlmCallArchive` appends one JSON
  line per provider attempt to a JSONL file.
- `LlmArchiveConfig` publishes exactly one archive bean: the file archive when
  `campus.ai.archive.enabled=true`, otherwise the no-op. Default is `false`; the default path is
  `scratch/llm-archive.jsonl`, which is already machine-local and gitignored.
- `LlmClient` records every attempt in `postChatCompletion`, including failed attempts with the
  request and the error. The archive call is wrapped twice (implementation and caller), so a
  full disk or a throwing custom archive is logged and never changes what the caller gets back.

**Verified:** `FileLlmCallArchiveTest` (JSONL shape, append, unwritable path swallowed) and
`LlmClientArchiveTest` (raw request == wire body, raw response archived, exploding archive does
not break the answer, failed attempt archived) - 7 tests, green.

**Completed in the follow-up round.** The first pass covered a successful call and a connection
that never arrived, and both archived `responseJson=null` correctly for "nothing came back".
What it did not cover is the case the archive exists for: a provider that *does* answer, with a
4xx/5xx. `restClient` throws `RestClientResponseException` before assigning `response`, so a
rejection was archived as `null` and the body survived only as prose inside `error`.
`postChatCompletion` now recovers it from the exception (`errorBodyOf`), and
`LlmClientArchiveTest#rejectedRequestArchivesTheErrorBody` drives a 400 with a JSON body and
asserts the archived JSON contains it. `null` now means genuinely nothing arrived.

## Housekeeping from the same review

- **CONTEXT drift vocabulary.** `CONTEXT.md` and the `application.yml` comment both still
  described reconciliation as repairing only the "DB far ahead of Redis" shape, which stopped
  being true when the task moved to the union. Both now describe what `reconcilePost` does: union
  membership, count repair in either direction, never remove a member, preserve a non-zero count
  only when both sides are empty.
- **Numbers without a command.** The pre-deployment checklist's "full check" section recorded
  `9 accounts`, `15 CF-Connecting-IP` rotations and `like_count=42/35` with no way to reproduce
  them. `scripts/rate-limit-probe.ps1` is that probe now, committed: rotating `CF-Connecting-IP`
  through nginx gives `401 x 10 / 429 x 5`, and the same rotation pointed straight at the app
  (which trusts `X-Real-IP` from its proxy) gives `15 x 401 / 0 x 429`. That second run is the
  proof the signal can go red, i.e. that it distinguishes "the caller picks the bucket" from "it
  does not". The remaining numbers in that section are now either attributed to the report that
  named them, or labelled as having no script behind them.
- **`deliverables/`.** The external audit drop (gstack reports) sat untracked inside the
  repository with nothing saying what it was. It is named in `AGENTS.md`'s machine-local list and
  ignored in `.gitignore`, the same treatment as the drill's evidence directory: the conclusions
  were rewritten into `docs/`, the bytes stay on this machine.

## Acceptance

- **F1 (N1/N2).** No application-layer class names a MyBatis mapper, and no interface under
  `repository/` exposes a MyBatis type. `RepositoryBoundaryTest` scans for both and covers
  `AiReviewService` plus all seven interfaces, not only the four post services.
- **F2 (fail-fast configuration).** `AiConfigValidatorTest` fails context startup on one illegal
  value per case with the property name in the message, and passes on a valid configuration.
- **F3 (wire archive).** `LlmClientArchiveTest` asserts the archived request equals the bytes
  that went on the wire and that a throwing archive cannot change the caller's result;
  `FileLlmCallArchiveTest` asserts the JSONL shape and that an unwritable path is swallowed.
- **The boundary stops where the evidence stops.** `VibeCommentServiceImpl`,
  `SysMessageServiceImpl` and the tasks keep their mappers; see "Not necessary, with the reason".
- **Suite.** One `mvn test` run: 399 tests, 0 failures, 0 errors, 0 skipped. Frontend 33 tests,
  lint and build clean. Frontend and backend wire types are pinned by
  `FrontendWireTypeContractTest` (see Files).

**Frontend wire types.** The review of this round also found four frontend declarations that
disagreed with what Jackson actually serialises (`Long` leaves the server as a string):
`PostPageVo.isPinned` was a field the server never sent, `ChannelStats.postCount` and the eight
`AiLogStats` counters were typed `number`, and `MessagesPage`'s local `Message.id` was typed
`number`. All four are corrected, and `FrontendWireTypeContractTest` now derives the expected TS
type from each Java field so the next drift fails the build instead of surfacing as a rendering
oddity.

## Already closed before this round, not redone

- **N6 fail-closed safety check.** `AiSafetyCheckListener.classifyResponse` now classifies in a
  fixed order and treats empty/unrecognised responses as pending review rather than safe;
  `AiSafetyCheckListenerTest` asserts empty, unrecognised and negated inputs. Re-implementing
  it would have duplicated the fix.
- **N4 traceId and metrics.** `TraceIds`/`TraceIdFilter`/`MdcCopyingTaskDecorator` carry the
  request id through async work, and the Micrometer metrics (`llm.chat.completions`,
  `llm.chat.completion.duration`, `llm.circuit.breaker.open`, product funnels) are exposed on
  the Prometheus endpoint. The audit's own note was "先补 traceId 更急"; both now exist.
- **N3 "no degradation mechanism".** The claim does not hold against the current tree: ranking
  and search degrade to MySQL, like counting degrades to direct writes, `LlmClient` fails fast
  through a breaker, reviews that cannot run land in `FAILED` for reconciliation, and
  `PostAgentEventPublisher` fails closed to `PENDING_REVIEW` when the queue or the LLM is
  unavailable. Degradation is the project's subject, not a missing layer.
- **N5 "every RuntimeException is a 400 that echoes the message".** `GlobalExceptionHandler` now
  gives `BusinessException` the status its thrower chose, keeps `IllegalArgumentException` at
  400 without echoing it, and routes unmapped `IllegalStateException`/`RuntimeException` to a
  generic 500 with a trace id. `ErrorSemanticsTest` probes all six shapes.

## Not necessary, with the reason

- **An `agent/LlmProvider` interface around `LlmClient`.** The retry ladder, circuit breaker,
  repair-parse fallback and prompt isolation are the implementation; an interface drawn there
  would expose none of the guarantees and would be a second seam with no consumer. ADR-0012
  already fixes the pipeline at two seams (`ReviewPolicy`, `Reviewer`) and says so.
- **A tool system.** The forum has no tool-calling requirement; the audit itself recommended
  against it.
- **More metrics endpoints.** The audit marked metrics as low urgency and traceId as the
  immediate need; the trace id exists and the metrics are already scraped and alerted on.
- **Repository interfaces for every service.** See F1; the audit's evidence was the post
  application layer, and the boundary test now pins exactly that.
- **DTO mapping at the boundary as a separate layer.** `NoEntityInControllerTest` already
  prevents entities from appearing in controller signatures; adding a mapper layer on top
  would be structure without a failing case behind it.

## Files

- `src/main/java/com/nexus/campus/repository/**`
- `src/main/java/com/nexus/campus/service/impl/{VibePostServiceImpl,PostCreationService,PostEditService,PostAgentEventPublisher,PromptVersionRecorder}.java`
- `src/main/java/com/nexus/campus/service/impl/PostPublishGate.java`
- `src/main/java/com/nexus/campus/agent/AiReviewService.java`
- `src/main/java/com/nexus/campus/config/{AiConfigValidator,LlmArchiveConfig}.java`
- `src/main/java/com/nexus/campus/agent/{LlmCallArchive,LlmCallRecord,FileLlmCallArchive,LlmClient}.java`
- `frontend/src/types/post.ts`, `frontend/src/pages/{DashboardPage,MessagesPage}.tsx`
- `src/test/java/com/nexus/campus/{contract/RepositoryBoundaryTest,contract/FrontendWireTypeContractTest,config/AiConfigValidatorTest,config/AiConfigPathsTest,agent/FileLlmCallArchiveTest,agent/LlmClientArchiveTest,service/VibePostEditAuditTest,service/impl/PostAgentEventPublisherTest}.java`
- `src/test/java/com/nexus/campus/agent/AiReviewServiceTest.java`
- `src/main/resources/application.yml`, `src/main/resources/application-prod.yml`, `.env.example`
- `CONTEXT.md`, `docs/plans/pre-deployment-checklist.md`, `.gitignore`
- `scripts/rate-limit-probe.ps1`
- `README.md`, `AGENTS.md` (test counts)

## Rejected

- **Enabling the archive by default.** Raw prompts and responses on disk are an operator
  decision, and the default deployment has no retention story for that file. Off by default,
  path configurable, failure non-blocking.
- **Writing the archive asynchronously.** It would add a queue and a second failure mode to a
  side channel whose whole contract is "never affects the call". A synchronous append behind a
  try/catch and a per-instance lock is honest about the cost and has no backlog to lose.
- **Failing startup on `response-format`.** See F2; the recorded decision is that a wrong value
  degrades rather than breaks.
- **A `@ConfigurationProperties` rewrite of the AI settings.** The values are consumed from
  `@Value` in six classes and pinned by tests that set those fields directly. A binding-object
  refactor would touch every consumer to validate what one early checker already validates, so
  it was left for a round that needs the object for something else.
- **Keeping MyBatis `Page` in `VibePostRepository`.** It type-checked and the original boundary
  test passed, because that test only rejected `com.nexus.campus.mapper` imports. But the point of
  the interface is that the service stops compiling against the framework, and a service that
  builds `new Page<>(page, size)` has not stopped. `PageSlice` is four lines and removes the last
  of it.
- **A blanket `"Mapper;"` substring rule in `RepositoryBoundaryTest`.** It was the first thing
  tried when `AiReviewService` joined the scan, and it immediately flagged Jackson's
  `ObjectMapper`, which is a JSON codec with nothing to do with persistence. Narrowing the rule to
  mapper *lines* minus `ObjectMapper`/`objectMapper` keeps the signal that matters instead of
  teaching the next reader to add an exclusion for a false positive.
- **Recording the rate-limit probe as "unproven" instead of writing it down.** The checklist
  could have satisfied the claims rule by admitting the number had no command behind it, and that
  would have been honest but useless: the next person asking "does the limiter still key on
  something the client chooses?" now has a one-line answer instead of a paragraph of apology.
- **Splitting `PostPublishGate` into an auditor plus a dispatcher.** Two components would each
  have one reason to change, but they change *together* here - the gate is exactly "what happens
  to a post at publish time" - and the callers would have to be handed both. One small component
  with two named entry points is the shape the duplication actually had.
