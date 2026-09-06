# Changelog

All notable changes to Nexus-Vibe will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Security

Audit-driven hardening of the AI pipeline (ADR-0003/0004/0005 review + product walkthrough, 2026-09-06):

- **Fail-closed without backdoors**: the safety listener's outer catch now fails closed (was a silent
  fail-open path leaving posts public with no reconciliation marker); the publish path gates on a
  shared cached LLM health verdict, so posts never sit publicly visible during an LLM outage
  (ADR-0004); `failClosed` writes the pending-llm marker before flipping post status
- **Prompt-injection isolation**: per-request nonce delimiters + inline neutralization of
  delimiter-like lines; post title/context excerpt moved inside their own META region
- **Lease correctness**: expiry judged by the DB clock (`NOW()`) instead of the caller's clock;
  lease raised 30s → 240s to exceed the LLM worst case and stop mid-review re-dispatch; the
  reconcile task retires budget-exhausted REVIEWING posts that a worker crash left unclaimable
- **Defense in depth**: AI review comments are sanitized through a shared safelist
  (`ContentSanitizer`, also used by the request XSS filter) since they bypass it

### Fixed

Product walkthrough findings (three-persona full-journey report, `产品体验报告/`):

- **P0**: re-reviewing a post no longer shows the stale score in the review terminal — the post
  poll reaching `aiReviewed=1` invalidates the 5-minute review-detail cache
- **Like state**: posts now carry `likedByMe` (Redis SISMEMBER, best-effort identity on public
  GETs) so the highlight survives reloads; the falsy-count fallback is gone and the server's
  `currentLikes` is the single truth with an in-flight lock against double toggles
- **Unsaved content**: create/edit pages warn on `beforeunload` once content drifts from the
  restored baseline; edit-page Cancel confirms before discarding
- **Smaller UX**: `aiReviewed=3` renders a quiet "review failed, retrying" notice; liking while
  logged out routes to login instead of swallowing the 401; a collapsible Review History panel
  (public `GET /agent-logs/post/{id}`) keeps earlier scores visible after a re-review; the
  create-page Code button inserts triple backticks; validation errors persist until the next submit

### Changed

- **Review lease + attempt budget (ADR-0005)**: AI review attempts claim the post with an atomic
  conditional UPDATE (review_lock_until/owner/attempts) — no double-processing across instances or
  re-published events; after 5 failed attempts the author is notified once and re-dispatch stops
- **Agent pool isolation**: a dedicated agentLlmExecutor (core2/max4/queue50) hosts the two
  LLM-bound listeners; long model calls can no longer starve message/notification work on
  nexus-async
- **Like-count drift reconciliation**: hourly rotating-cursor sweep detects Redis-LOSS-shaped gaps
  (DB far above the Redis set) and rebuilds from vibe_post_like, including the hot-ranking ZSET;
  LikeSyncTask now consumes the dirty set via SPOP batches (100) with requeue-on-failure
- **LLM JSON repair + self-correction**: raw structured output is repair-parsed (fences, trailing
  commas, max-token truncation via bracket-stack completion) before validation; unparseable output
  retries once with the model's own broken output plus the parser error (temperature 0.1)

### Added

- Deep-pagination study: late row lookup measured as a REGRESSION on MySQL 8 with a covering
  index (optimizer already does index-ordered top-N) — rewrite reverted, measurements and
  benchmark scripts kept in docs/research/late-row-lookup-deep-pagination.md

### Changed

- **AI review explainability**
  - The review prompt now anchors scoring with an explicit 0-10 rubric (consistency across reviews)
  - AI review comments end with the score guide; the post-page score badge shows it on hover
- **Re-review supersede**: editing a post hides the previous AI review comment (status=0) so the
  thread never shows contradictory scores; full history remains in ai_review_log / agent logs
- **Pool-saturation degradation**: agent events rejected by a saturated async pool no longer fail
  the post request with a 500 — review posts land in FAILED(3) for the reconciliation task, and
  safety checks fail closed (PENDING_REVIEW + pending-llm marker) exactly like an LLM outage

### Added

- **Account Recovery (P0)**
  - Registration now collects a unique email as the recovery anchor (frontend + backend validation)
  - Admin endpoint `POST /api/v1/admin/users/reset-password` generates a 12-char temporary password for out-of-band handover
  - "Account Recovery" card on the admin dashboard

- **Author Notifications (P0)**
  - Authors are now notified when: their post is held for safety audit (fail-closed or prompt injection), their AI review fails and is queued for retry, and when an admin approves or rejects their post
  - Spam remains silent by design (no abuser feedback, per ADR-0003)

### Changed

- `/api/demo/*` showcase endpoints are now gated behind `campus.demo.endpoints-enabled` and **off by default** (`DEMO_ENDPOINTS_ENABLED`); previously they were anonymous on any non-prod profile

## [1.0.0-CYBERPUNK] - 2026-07-17

### Added

- **Authentication & User Management**
  - User registration and login with JWT token issuance
  - Role-based access (USER / ADMIN)
  - Personal profile page
  - JWT authentication filter with request-scoped user context

- **Posts & Content**
  - Post creation, detail view, and paginated listing
  - Category-based post browsing
  - Post tagging (many-to-many relationship)
  - Post likes with Redis-backed like counter

- **Comments**
  - Comment creation on posts
  - Paginated comment listing

- **Content Moderation**
  - Two-tier sensitive word detection (sensitive / critical)
  - DFA (Deterministic Finite Automaton) algorithm for O(n) word matching
  - Automatic post blocking for critical-level content
  - Flagging for sensitive-level content requiring admin review
  - Admin audit dashboard (approve / reject workflow)
  - XSS input sanitization filter

- **Search**
  - Elasticsearch 8.x integration for full-text search
  - Post document indexing

- **System & Infrastructure**
  - Spring Boot 3.3.5 with Java 18
  - MyBatis-Plus 3.5.9 ORM
  - H2 in-memory database (dev) / MySQL 8 (production)
  - Redis caching with Lettuce connection pool
  - Multi-profile YAML configuration (default H2 / mysql)
  - Global exception handler with structured JSON error responses
  - AOP-based request logging aspect
  - MyBatis-Plus meta-object auto-fill (createTime, updateTime)
  - DTO layer with Jakarta Validation annotations
  - WAR packaging for standalone or container deployment

- **UI / Views**
  - Cyberpunk-styled JSP views
  - Login / Register / Index / Post Detail / Post Create / Profile / Admin Audit pages
  - Responsive layout

### Documentation

- Initial README with tech stack, architecture, setup guide, API examples
- CHANGELOG established
- Swagger / OpenAPI 3 documentation via SpringDoc

### DevOps

- Docker multi-profile build support
- Docker Compose template (app + MySQL + Redis)
- `.gitignore` for Java/Maven projects
- Maven wrapper compatible

### Infrastructure

- Add project documentation (README, CHANGELOG, Swagger)
- Add `.gitignore` for standard Java/Maven project hygiene
- Configure SpringDoc OpenAPI 3 for REST API documentation
