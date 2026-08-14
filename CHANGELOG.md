# Changelog

All notable changes to Nexus-Vibe will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
