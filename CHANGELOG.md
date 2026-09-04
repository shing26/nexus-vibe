# Changelog

All notable changes to Nexus-Vibe will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
